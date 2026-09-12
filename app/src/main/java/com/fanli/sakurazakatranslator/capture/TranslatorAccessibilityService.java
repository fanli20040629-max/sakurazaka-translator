package com.fanli.sakurazakatranslator.capture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fanli.sakurazakatranslator.ProbePreferences;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TranslatorAccessibilityService extends AccessibilityService {
    private static final int MAX_NODE_DEPTH = 80;
    private static final int MAX_VISITED_NODES = 800;

    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextRecognizer recognizer = TextRecognition.getClient(
            new JapaneseTextRecognizerOptions.Builder().build());

    private WindowManager windowManager;
    private KeyguardManager keyguardManager;
    private Button trigger;
    private LinearLayout preview;
    private TextView ocrLabel;
    private CaptureJob previewJob;
    private final ProbeLogic.RequestGate requestGate = new ProbeLogic.RequestGate();
    private boolean destroyed;
    private boolean receiverRegistered;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) invalidateAndClear();
            else refreshTriggerVisibility();
        }
    };

    @Override protected void onServiceConnected() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        refreshTriggerVisibility();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || destroyed) return;
        refreshTriggerVisibility();
    }

    @Override public void onInterrupt() {
        invalidateAndClear();
    }

    private void refreshTriggerVisibility() {
        if (destroyed || windowManager == null || isLocked()) {
            invalidateAndClear();
            return;
        }
        String foregroundPackage = foregroundApplicationPackage();
        if (isAllowedPackage(foregroundPackage)) showTrigger();
        else {
            removeTrigger();
            invalidateAndClear();
        }
    }

    private boolean isAllowedPackage(String packageName) {
        if (packageName == null || packageName.isBlank()) return false;
        if (getPackageName().equals(packageName)) return true;
        String configured = ProbePreferences.targetPackage(this);
        return !configured.isBlank() && configured.equals(packageName);
    }

    private String foregroundApplicationPackage() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isActive()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.getPackageName() != null) return root.getPackageName().toString();
            }
        }
        return null;
    }

    private void showTrigger() {
        if (trigger != null) return;
        trigger = new Button(this);
        trigger.setText("取字探针");
        trigger.setTextColor(Color.WHITE);
        trigger.setBackgroundColor(Color.rgb(40, 90, 160));
        WindowManager.LayoutParams params = overlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        params.y = dp(72);
        params.x = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(132));
        enableDrag(trigger, params, this::capture);
        windowManager.addView(trigger, params);
    }

    private void removeTrigger() {
        if (trigger == null || windowManager == null) return;
        safeRemove(trigger);
        trigger = null;
    }

    private AccessibilityWindowInfo targetWindow() {
        String foreground = foregroundApplicationPackage();
        if (!isAllowedPackage(foreground)) return null;
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isActive()) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && foreground.equals(String.valueOf(root.getPackageName()))) return window;
        }
        return null;
    }

    private void capture() {
        if (destroyed || isLocked()) {
            toast("设备已锁定，探针已暂停");
            return;
        }
        AccessibilityWindowInfo target = targetWindow();
        if (target == null) {
            toast("当前窗口不是已允许的测试页面");
            return;
        }
        AccessibilityNodeInfo root = target.getRoot();
        if (root == null) {
            toast("目标窗口没有可读取的节点根");
            return;
        }
        final long requestId = requestGate.begin();
        if (requestId < 0) {
            toast("识别正在进行，请等待当前任务完成");
            return;
        }
        removePreview();
        final long startedAt = SystemClock.elapsedRealtime();
        final NodeReport nodeReport = collectNodes(root);
        final int windowId = target.getId();
        final String targetPackage = String.valueOf(root.getPackageName());

        try {
            takeScreenshotOfWindow(windowId, screenshotExecutor, new TakeScreenshotCallback() {
            @Override public void onFailure(int errorCode) {
                main.post(() -> finishScreenshotFailure(requestId, nodeReport, errorCode, startedAt));
            }

            @Override public void onSuccess(ScreenshotResult result) {
                Bitmap software = copyScreenshot(result);
                if (software == null) {
                    main.post(() -> finishScreenshotFailure(requestId, nodeReport, -1, startedAt));
                    return;
                }
                CaptureJob job = new CaptureJob(requestId, software);
                main.post(() -> showPreviewIfCurrent(job, nodeReport, targetPackage, windowId, startedAt));
                try {
                    recognizer.process(InputImage.fromBitmap(software, 0))
                            .addOnSuccessListener(text -> finishOcr(job, text, startedAt))
                            .addOnFailureListener(error -> finishOcrError(job, error, startedAt));
                } catch (RuntimeException error) {
                    finishOcrError(job, error, startedAt);
                }
            }
            });
        } catch (RuntimeException error) {
            finishScreenshotFailure(requestId, nodeReport, -2, startedAt);
        }
    }

    private Bitmap copyScreenshot(ScreenshotResult result) {
        HardwareBuffer buffer = result.getHardwareBuffer();
        Bitmap wrapped = null;
        try {
            wrapped = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            return wrapped == null ? null : wrapped.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException error) {
            return null;
        } finally {
            if (wrapped != null) wrapped.recycle();
            buffer.close();
        }
    }

    private void showPreviewIfCurrent(CaptureJob job, NodeReport report, String packageName,
                                      int windowId, long startedAt) {
        if (!isCurrent(job.requestId) || !isAllowedPackage(foregroundApplicationPackage())) {
            job.detachPreview();
            return;
        }
        previewJob = job;
        preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(12), dp(12), dp(12), dp(12));
        preview.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText(String.format(Locale.ROOT, "截图成功 · %d×%d", job.bitmap.getWidth(), job.bitmap.getHeight()));
        title.setTextColor(Color.BLACK);
        header.addView(title, new LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(v -> removePreview());
        header.addView(close);
        preview.addView(header);

        ImageView image = new ImageView(this);
        image.setImageBitmap(job.bitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.addView(image, new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(260)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        TextView nodeLabel = new TextView(this);
        nodeLabel.setText(String.format(Locale.ROOT, "无障碍节点\n%s", report.displayText()));
        nodeLabel.setTextColor(Color.BLACK);
        results.addView(nodeLabel);
        ocrLabel = new TextView(this);
        ocrLabel.setText("\n日文 OCR\n处理中…");
        ocrLabel.setTextColor(Color.BLACK);
        results.addView(ocrLabel);
        TextView metadata = new TextView(this);
        metadata.setText(String.format(Locale.ROOT,
                "\n诊断：包=%s  window=%d  节点=%d  文本块=%d  截图耗时=%dms",
                packageName, windowId, report.visitedNodes, report.textBlocks,
                SystemClock.elapsedRealtime() - startedAt));
        metadata.setTextColor(Color.DKGRAY);
        results.addView(metadata);
        scroll.addView(results);
        preview.addView(scroll, new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(260)));

        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(420));
        WindowManager.LayoutParams params = overlayParams(width,
                WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        params.x = Math.max(0, (getResources().getDisplayMetrics().widthPixels - width) / 2);
        params.y = dp(36);
        enableDrag(header, params, null);
        windowManager.addView(preview, params);
    }

    private void finishScreenshotFailure(long requestId, NodeReport report, int errorCode, long startedAt) {
        if (!isCurrent(requestId)) return;
        requestGate.complete(requestId);
        toast(String.format(Locale.ROOT, "截图失败 code=%d，节点块=%d，耗时=%dms",
                errorCode, report.textBlocks, SystemClock.elapsedRealtime() - startedAt));
    }

    private void finishOcr(CaptureJob job, Text text, long startedAt) {
        main.post(() -> {
            if (isCurrent(job.requestId)) {
                requestGate.complete(job.requestId);
                if (ocrLabel != null && previewJob == job) {
                    String value = text.getText().isBlank() ? "（空）" : text.getText();
                    ocrLabel.setText(String.format(Locale.ROOT, "\n日文 OCR\n%s\nOCR 总耗时=%dms",
                            value, SystemClock.elapsedRealtime() - startedAt));
                }
            }
            job.finishOcr();
        });
    }

    private void finishOcrError(CaptureJob job, Exception error, long startedAt) {
        main.post(() -> {
            if (isCurrent(job.requestId)) {
                requestGate.complete(job.requestId);
                if (ocrLabel != null && previewJob == job) {
                    ocrLabel.setText(String.format(Locale.ROOT, "\n日文 OCR 失败：%s（%dms）",
                            error.getClass().getSimpleName(), SystemClock.elapsedRealtime() - startedAt));
                }
            }
            job.finishOcr();
        });
    }

    private NodeReport collectNodes(AccessibilityNodeInfo root) {
        NodeReport report = new NodeReport();
        collectNode(root, report, new HashSet<>(), 0);
        return report;
    }

    private void collectNode(AccessibilityNodeInfo node, NodeReport report, Set<String> seenAtPosition, int depth) {
        if (node == null || depth > MAX_NODE_DEPTH || report.visitedNodes >= MAX_VISITED_NODES) return;
        report.visitedNodes++;
        if (!node.isVisibleToUser()) return;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        appendNodeValue(node.getText(), bounds, report, seenAtPosition, "text");
        appendNodeValue(node.getContentDescription(), bounds, report, seenAtPosition, "desc");
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) collectNode(child, report, seenAtPosition, depth + 1);
        }
    }

    private void appendNodeValue(CharSequence value, Rect bounds, NodeReport report,
                                 Set<String> seenAtPosition, String source) {
        if (value == null || value.toString().isBlank()) return;
        String key = ProbeLogic.positionKey(value.toString(), bounds.left, bounds.top, bounds.right, bounds.bottom);
        if (!seenAtPosition.add(key)) return;
        report.textBlocks++;
        report.output.append('[').append(source).append(' ')
                .append(bounds.flattenToString()).append("] ")
                .append(value).append('\n');
    }

    private boolean isCurrent(long requestId) {
        return !destroyed && requestGate.isCurrent(requestId);
    }

    private boolean isLocked() {
        return keyguardManager != null && keyguardManager.isDeviceLocked();
    }

    private void invalidateAndClear() {
        requestGate.invalidate();
        removePreview();
    }

    private void removePreview() {
        if (preview != null && windowManager != null) safeRemove(preview);
        preview = null;
        ocrLabel = null;
        if (previewJob != null) previewJob.detachPreview();
        previewJob = null;
    }

    private WindowManager.LayoutParams overlayParams(int width, int height, int gravity) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = gravity;
        return params;
    }

    private void enableDrag(View handle, WindowManager.LayoutParams params, Runnable clickAction) {
        if (clickAction != null) handle.setOnClickListener(view -> clickAction.run());
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            int startX;
            int startY;
            boolean moved;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    moved = false;
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int dx = Math.round(event.getRawX() - downX);
                    int dy = Math.round(event.getRawY() - downY);
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                    int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(48));
                    int maxY = Math.max(0, getResources().getDisplayMetrics().heightPixels - dp(48));
                    params.x = Math.max(0, Math.min(maxX, startX + dx));
                    params.y = Math.max(0, Math.min(maxY, startY + dy));
                    try { windowManager.updateViewLayout(clickAction == null ? preview : trigger, params); }
                    catch (IllegalArgumentException ignored) { }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!moved && clickAction != null) view.performClick();
                    return true;
                }
                return false;
            }
        });
    }

    private void safeRemove(View view) {
        try { windowManager.removeView(view); } catch (IllegalArgumentException ignored) { }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        main.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override public void onDestroy() {
        destroyed = true;
        invalidateAndClear();
        removeTrigger();
        if (receiverRegistered) unregisterReceiver(screenReceiver);
        receiverRegistered = false;
        recognizer.close();
        screenshotExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class NodeReport {
        final StringBuilder output = new StringBuilder();
        int visitedNodes;
        int textBlocks;

        String displayText() {
            return output.length() == 0 ? "（没有可见文本节点）" : output.toString().trim();
        }
    }

    private static final class CaptureJob {
        final long requestId;
        final Bitmap bitmap;
        private boolean previewAttached = true;
        private boolean ocrFinished;
        private boolean recycled;

        CaptureJob(long requestId, Bitmap bitmap) {
            this.requestId = requestId;
            this.bitmap = bitmap;
        }

        synchronized void detachPreview() {
            previewAttached = false;
            releaseIfUnused();
        }

        synchronized void finishOcr() {
            ocrFinished = true;
            releaseIfUnused();
        }

        private void releaseIfUnused() {
            if (!recycled && !previewAttached && ocrFinished) {
                bitmap.recycle();
                recycled = true;
            }
        }
    }
}
