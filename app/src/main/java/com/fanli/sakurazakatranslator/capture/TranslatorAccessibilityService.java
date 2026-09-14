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
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fanli.sakurazakatranslator.ProbePreferences;
import com.fanli.sakurazakatranslator.R;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

public final class TranslatorAccessibilityService extends AccessibilityService {
    private static final int MAX_NODE_DEPTH = 80;
    private static final int MAX_VISITED_NODES = 800;

    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lifecycleLock = new Object();
    private final TextRecognizer recognizer = TextRecognition.getClient(
            new JapaneseTextRecognizerOptions.Builder().build());
    private final ProbeLogic.RequestGate requestGate = new ProbeLogic.RequestGate();

    private WindowManager windowManager;
    private KeyguardManager keyguardManager;
    private Button trigger;
    private LinearLayout preview;
    private ImageView previewImage;
    private TextView ocrLabel;
    private LinearLayout ocrCandidates;
    private TextView selectedOcrLabel;
    private final Set<String> selectedOcrIds = new LinkedHashSet<>();
    private final Map<String, String> ocrTextById = new LinkedHashMap<>();
    private CaptureJob previewJob;
    private CaptureJob inFlightJob;
    private boolean destroyed;
    private boolean receiverRegistered;
    private boolean recognizerClosed;
    private long pageEpoch;
    private String foregroundPackage;
    private int foregroundWindowId = -1;
    private int overlayWindowId = -1;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                invalidateAndClear(true);
            } else {
                refreshTriggerVisibility();
            }
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
        if (isPreviewScrollEvent(event)) {
            refreshTriggerVisibility();
            return;
        }
        String eventPackage = event.getPackageName() == null
                ? null : event.getPackageName().toString();
        // Showing or refocusing this app's own synthetic page is not a target
        // page transition. The foreground window identity check below still
        // handles an actual application/window change.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && getPackageName().equals(eventPackage)) {
            refreshTriggerVisibility();
            return;
        }
        if (isPageEvent(event.getEventType())) {
            WindowSnapshot active = activeApplication();
            updateForegroundIdentity(active);
            boolean sameWindow = active != null && event.getWindowId() == active.windowId;
            if (active != null && sameWindow && eventPackage != null
                    && eventPackage.equals(active.packageName)
                    && isAllowedPackage(eventPackage)) {
                markPageChanged();
            } else if (event.getWindowId() == overlayWindowId) {
                refreshTriggerVisibility();
                return;
            }
        }
        refreshTriggerVisibility();
    }

    @Override public void onInterrupt() {
        invalidateAndClear(true);
    }

    private void refreshTriggerVisibility() {
        if (destroyed || windowManager == null) {
            invalidateAndClear(true);
            return;
        }
        if (isLocked()) {
            invalidateAndClear(true);
            return;
        }
        WindowSnapshot active = activeApplication();
        updateForegroundIdentity(active);
        if (active != null && isAllowedPackage(active.packageName)) {
            showTrigger();
        } else {
            removeTrigger();
            invalidateAndClear(false);
        }
    }

    private boolean isAllowedPackage(String packageName) {
        if (packageName == null || packageName.isBlank()) return false;
        if (getPackageName().equals(packageName)) {
            return ProbePreferences.syntheticMode(this);
        }
        String configured = ProbePreferences.targetPackage(this);
        return !configured.isBlank() && configured.equals(packageName);
    }

    private WindowSnapshot activeApplication() {
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException error) {
            return null;
        }
        if (windows == null) return null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isActive()) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            try {
                CharSequence packageName = root.getPackageName();
                if (packageName != null) {
                    return new WindowSnapshot(packageName.toString(), window.getId());
                }
            } finally {
                root.recycle();
            }
        }
        return null;
    }

    private void updateForegroundIdentity(WindowSnapshot active) {
        String packageName = active == null ? null : active.packageName;
        int windowId = active == null ? -1 : active.windowId;
        boolean hadIdentity = foregroundPackage != null || foregroundWindowId != -1;
        boolean changed = foregroundWindowId != windowId
                || (foregroundPackage == null ? packageName != null
                : !foregroundPackage.equals(packageName));
        foregroundPackage = packageName;
        foregroundWindowId = windowId;
        if (changed && (hadIdentity || packageName != null)) {
            pageEpoch++;
            requestGate.invalidate();
            removePreview();
        }
    }

    private void markPageChanged() {
        pageEpoch++;
        requestGate.invalidate();
        removePreview();
    }

    private void showTrigger() {
        if (trigger != null || destroyed || windowManager == null || isLocked()) return;
        Button candidate = new Button(this);
        candidate.setText("取字探针");
        candidate.setTextColor(Color.WHITE);
        candidate.setBackgroundColor(Color.rgb(40, 90, 160));
        WindowManager.LayoutParams params = overlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        params.y = dp(72);
        params.x = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(132));
        enableDrag(candidate, candidate, params, this::capture);
        try {
            windowManager.addView(candidate, params);
            trigger = candidate;
        } catch (RuntimeException ignored) {
            trigger = null;
        }
    }

    private void removeTrigger() {
        if (trigger != null && windowManager != null) safeRemove(trigger);
        trigger = null;
    }

    private AccessibilityWindowInfo targetWindow() {
        WindowSnapshot active = activeApplication();
        if (active == null || !isAllowedPackage(active.packageName)) return null;
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException error) {
            return null;
        }
        if (windows == null) return null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                    || !window.isActive() || window.getId() != active.windowId) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            try {
                if (active.packageName.equals(String.valueOf(root.getPackageName()))) return window;
            } finally {
                root.recycle();
            }
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
            toast("目标窗口没有可读取的节点树");
            return;
        }

        final long requestId = requestGate.begin();
        if (requestId < 0) {
            root.recycle();
            toast("识别正在进行，请等待当前任务完成");
            return;
        }

        removePreview();
        final long startedAt = SystemClock.elapsedRealtime();
        final int windowId = target.getId();
        final String targetPackage = String.valueOf(root.getPackageName());
        final long capturedEpoch = pageEpoch;
        final NodeReport nodeReport;
        try {
            nodeReport = collectNodes(root);
        } catch (RuntimeException error) {
            requestGate.finishPhysical(requestId);
            maybeShutdown();
            toast("节点读取失败：" + error.getClass().getSimpleName());
            return;
        } finally {
            root.recycle();
        }

        try {
            takeScreenshotOfWindow(windowId, screenshotExecutor, new TakeScreenshotCallback() {
                @Override public void onFailure(int errorCode) {
                    main.post(() -> finishScreenshotFailure(
                            requestId, nodeReport, targetPackage, windowId, capturedEpoch,
                            errorCode, startedAt));
                }

                @Override public void onSuccess(ScreenshotResult result) {
                    Bitmap software = copyScreenshot(result);
                    if (software == null) {
                        main.post(() -> finishScreenshotFailure(
                                requestId, nodeReport, targetPackage, windowId, capturedEpoch,
                                -1, startedAt));
                        return;
                    }

                    CaptureJob job = new CaptureJob(
                            requestId, targetPackage, windowId, capturedEpoch, software);
                    synchronized (lifecycleLock) {
                        if (destroyed) {
                            discardJob(job);
                            return;
                        }
                        inFlightJob = job;
                    }
                    main.post(() -> showPreviewIfCurrent(job, nodeReport, startedAt));
                    startOcr(job, startedAt);
                }
            });
        } catch (RuntimeException error) {
            main.post(() -> finishScreenshotFailure(
                    requestId, nodeReport, targetPackage, windowId, capturedEpoch,
                    -2, startedAt));
        }
    }

    private void startOcr(CaptureJob job, long startedAt) {
        synchronized (lifecycleLock) {
            if (destroyed || recognizerClosed) {
                discardJob(job);
                return;
            }
            try {
                recognizer.process(InputImage.fromBitmap(job.bitmap, 0))
                        .addOnCompleteListener(task -> {
                            if (task.isCanceled()) {
                                finishOcrCanceled(job, startedAt);
                            } else if (task.isSuccessful()) {
                                finishOcr(job, task.getResult(), startedAt);
                            } else {
                                Exception error = task.getException();
                                finishOcrError(job, error == null
                                        ? new IllegalStateException("OCR 任务失败") : error, startedAt);
                            }
                        });
            } catch (RuntimeException error) {
                finishOcrError(job, error, startedAt);
            }
        }
    }

    private Bitmap copyScreenshot(ScreenshotResult result) {
        if (result == null || result.getHardwareBuffer() == null) return null;
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

    private void showPreviewIfCurrent(CaptureJob job, NodeReport report, long startedAt) {
        if (!isCurrentPage(job) || !requestGate.isCurrent(job.requestId)) {
            job.detachPreview();
            return;
        }
        removePreview();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(Color.WHITE);
        card.setContentDescription("probe_result_card");

        WindowManager.LayoutParams params = overlayParams(cardWidth(),
                WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        params.x = Math.max(0, (getResources().getDisplayMetrics().widthPixels - params.width) / 2);
        params.y = dp(36);
        addHeader(card, "截图成功 · " + job.bitmap.getWidth() + "×" + job.bitmap.getHeight(),
                this::closePreview, params);

        ImageView image = new ImageView(this);
        image.setImageBitmap(job.bitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        previewImage = image;
        card.addView(image, new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, previewImageHeight()));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        TextView nodeLabel = new TextView(this);
        nodeLabel.setText(getString(R.string.probe_nodes_result, report.displayText()));
        nodeLabel.setTextColor(Color.BLACK);
        results.addView(nodeLabel);
        ocrLabel = new TextView(this);
        ocrLabel.setText(R.string.probe_ocr_processing);
        ocrLabel.setTextColor(Color.BLACK);
        results.addView(ocrLabel);
        ocrCandidates = new LinearLayout(this);
        ocrCandidates.setOrientation(LinearLayout.VERTICAL);
        results.addView(ocrCandidates);
        selectedOcrLabel = new TextView(this);
        selectedOcrLabel.setText(R.string.probe_ocr_selected_empty);
        selectedOcrLabel.setTextColor(Color.DKGRAY);
        results.addView(selectedOcrLabel);
        selectedOcrIds.clear();
        ocrTextById.clear();
        TextView metadata = new TextView(this);
        metadata.setText(metadataText("截图+节点", job.packageName, job.windowId,
                report, startedAt, null));
        metadata.setTextColor(Color.DKGRAY);
        results.addView(metadata);
        scroll.addView(results);
        card.addView(scroll, new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, resultHeight()));

        preview = card;
        previewJob = job;
        try {
            windowManager.addView(card, params);
            AccessibilityNodeInfo cardNode = card.createAccessibilityNodeInfo();
            overlayWindowId = cardNode == null ? -1 : cardNode.getWindowId();
        } catch (RuntimeException error) {
            preview = null;
            previewJob = null;
            if (previewImage != null) {
                previewImage.setImageDrawable(null);
                previewImage = null;
            }
            ocrLabel = null;
            ocrCandidates = null;
            selectedOcrLabel = null;
            job.detachPreview();
        }
    }

    private void finishScreenshotFailure(long requestId, NodeReport report, String packageName,
                                         int windowId, long epoch, int errorCode, long startedAt) {
        boolean valid = !destroyed && requestGate.isCurrent(requestId)
                && isCurrentPage(packageName, windowId, epoch);
        requestGate.finishPhysical(requestId);
        maybeShutdown();
        if (!valid) return;
        showFailurePreview(report, packageName, windowId, epoch, errorCode, startedAt);
    }

    private void showFailurePreview(NodeReport report, String packageName, int windowId,
                                    long epoch, int errorCode, long startedAt) {
        if (destroyed || !isCurrentPage(packageName, windowId, epoch)) return;
        removePreview();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(Color.WHITE);
        card.setContentDescription("probe_result_card");

        WindowManager.LayoutParams params = overlayParams(cardWidth(),
                WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        params.x = Math.max(0, (getResources().getDisplayMetrics().widthPixels - params.width) / 2);
        params.y = dp(36);
        addHeader(card, "截图失败 · code=" + errorCode, this::closePreview, params);

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        TextView nodeLabel = new TextView(this);
        nodeLabel.setText(getString(R.string.probe_nodes_failure, report.displayText()));
        nodeLabel.setTextColor(Color.BLACK);
        results.addView(nodeLabel);
        TextView metadata = new TextView(this);
        metadata.setText(metadataText("仅无障碍节点", packageName, windowId,
                report, startedAt, "截图错误码=" + errorCode));
        metadata.setTextColor(Color.DKGRAY);
        results.addView(metadata);
        scroll.addView(results);
        card.addView(scroll, new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, resultHeight()));

        preview = card;
        previewJob = null;
        ocrLabel = null;
        ocrCandidates = null;
        selectedOcrLabel = null;
        try {
            windowManager.addView(card, params);
            AccessibilityNodeInfo cardNode = card.createAccessibilityNodeInfo();
            overlayWindowId = cardNode == null ? -1 : cardNode.getWindowId();
        } catch (RuntimeException error) {
            preview = null;
        }
    }

    private void finishOcr(CaptureJob job, Text text, long startedAt) {
        job.finishPhysical();
        requestGate.finishPhysical(job.requestId);
        maybeShutdown();
        main.post(() -> {
            if (destroyed || previewJob != job || !isCurrentPage(job)) return;
            renderOcrCandidates(text, SystemClock.elapsedRealtime() - startedAt);
        });
    }

    private void finishOcrError(CaptureJob job, Exception error, long startedAt) {
        job.finishPhysical();
        requestGate.finishPhysical(job.requestId);
        maybeShutdown();
        main.post(() -> {
            if (destroyed || previewJob != job || !isCurrentPage(job)) return;
            ocrLabel.setText(getString(R.string.probe_ocr_failure,
                    error.getClass().getSimpleName(),
                    SystemClock.elapsedRealtime() - startedAt));
            if (ocrCandidates != null) ocrCandidates.removeAllViews();
        });
    }

    private void finishOcrCanceled(CaptureJob job, long startedAt) {
        job.finishPhysical();
        requestGate.finishPhysical(job.requestId);
        maybeShutdown();
        main.post(() -> {
            if (destroyed || previewJob != job || !isCurrentPage(job)) return;
            ocrLabel.setText(getString(R.string.probe_ocr_failure,
                    "已取消", SystemClock.elapsedRealtime() - startedAt));
            if (ocrCandidates != null) ocrCandidates.removeAllViews();
        });
    }

    private List<TextFragment> toOcrFragments(Text text) {
        List<TextFragment> fragments = new ArrayList<>();
        if (text == null || text.getText() == null || text.getText().isBlank()) return fragments;
        int blockIndex = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            blockIndex++;
            List<Text.Line> lines = block.getLines();
            if (lines == null || lines.isEmpty()) {
                fragments.add(TextAssembly.ocr("ocr:" + blockIndex + ":0",
                        block.getText(), toBounds(block.getBoundingBox()), fragments.size()));
                continue;
            }
            int lineIndex = 0;
            for (Text.Line line : lines) {
                lineIndex++;
                fragments.add(TextAssembly.ocr("ocr:" + blockIndex + ":" + lineIndex,
                        line.getText(), toBounds(line.getBoundingBox()), fragments.size()));
            }
        }
        return fragments;
    }

    private void renderOcrCandidates(Text text, long elapsedMs) {
        if (ocrLabel == null || ocrCandidates == null) return;
        List<TextFragment> fragments = toOcrFragments(text);
        ocrCandidates.removeAllViews();
        selectedOcrIds.clear();
        ocrTextById.clear();
        if (fragments.isEmpty()) {
            ocrLabel.setText(getString(R.string.probe_ocr_empty, elapsedMs));
            updateSelectedOcr();
            return;
        }
        ocrLabel.setText(getString(R.string.probe_ocr_candidates, fragments.size(), elapsedMs));
        for (TextFragment fragment : fragments) {
            ocrTextById.put(fragment.id, fragment.rawText);
            CheckBox candidate = new CheckBox(this);
            candidate.setText(fragment.rawText);
            candidate.setTextColor(Color.BLACK);
            candidate.setContentDescription("OCR 候选；边界="
                    + (fragment.bounds == null ? "无" : fragment.bounds));
            candidate.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedOcrIds.add(fragment.id);
                else selectedOcrIds.remove(fragment.id);
                updateSelectedOcr();
            });
            ocrCandidates.addView(candidate);
        }
        updateSelectedOcr();
    }

    private void updateSelectedOcr() {
        if (selectedOcrLabel == null) return;
        if (selectedOcrIds.isEmpty()) {
            selectedOcrLabel.setText(R.string.probe_ocr_selected_empty);
            return;
        }
        StringBuilder selected = new StringBuilder();
        for (String id : selectedOcrIds) {
            String value = ocrTextById.get(id);
            if (value == null) continue;
            if (selected.length() > 0) selected.append('\n');
            selected.append(value);
        }
        selectedOcrLabel.setText(getString(R.string.probe_ocr_selected, selected));
    }

    private String metadataText(String source, String packageName, int windowId,
                                NodeReport report, long startedAt, String error) {
        String suffix = error == null ? "" : " · " + error;
        return String.format(Locale.ROOT,
                "\n诊断：来源=%s · 包=%s · windowId=%d · pageEpoch=%d · 节点=%d · 文本块=%d"
                        + " · 总耗时=%dms%s",
                source, packageName, windowId, pageEpoch, report.visitedNodes,
                report.textBlocks, SystemClock.elapsedRealtime() - startedAt, suffix);
    }

    private NodeReport collectNodes(AccessibilityNodeInfo root) {
        NodeReport report = new NodeReport();
        collectNode(root, report, new HashSet<>(), 0, null, 0);
        report.fragments.addAll(TextAssembly.fromNodes(report.nodes));
        report.textBlocks = report.fragments.size();
        return report;
    }

    private void collectNode(AccessibilityNodeInfo node, NodeReport report,
                              Set<String> seenAtPosition, int depth,
                              String parentId, int childIndex) {
        if (node == null) return;
        if (depth > MAX_NODE_DEPTH || report.visitedNodes >= MAX_VISITED_NODES) {
            report.truncated = true;
            return;
        }
        report.visitedNodes++;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        Rect windowBounds = new Rect();
        node.getBoundsInWindow(windowBounds);
        String id = "n" + report.visitedNodes;
        report.nodes.add(new NodeRecord(id, parentId, childIndex, report.visitedNodes - 1,
                node.getWindowId(), toRaw(node.getText()), toRaw(node.getContentDescription()),
                toRaw(node.getClassName()), node.getViewIdResourceName(),
                toBounds(bounds), toBounds(windowBounds), node.isVisibleToUser(),
                node.isClickable(), node.getCollectionItemInfo() != null));
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) collectNode(child, report, seenAtPosition, depth + 1, id, index);
            if (report.truncated) return;
        }
    }

    private String toRaw(CharSequence value) {
        return value == null ? null : value.toString();
    }

    private Bounds toBounds(Rect value) {
        return value == null ? null : new Bounds(value.left, value.top, value.right, value.bottom);
    }

    private void appendNodeValue(CharSequence value, Rect bounds, NodeReport report,
                                 Set<String> seenAtPosition, String source) {
        if (value == null || value.toString().isBlank()) return;
        String key = ProbeLogic.positionKey(value.toString(),
                bounds.left, bounds.top, bounds.right, bounds.bottom);
        if (!seenAtPosition.add(key)) return;
        report.textBlocks++;
        report.output.append('[').append(source).append(' ')
                .append(bounds.flattenToString()).append("] ")
                .append(value).append('\n');
    }

    private boolean isCurrentPage(CaptureJob job) {
        return isCurrentPage(job.packageName, job.windowId, job.pageEpoch);
    }

    private boolean isCurrentPage(String packageName, int windowId, long epoch) {
        if (destroyed || isLocked() || !isAllowedPackage(packageName)) return false;
        WindowSnapshot active = activeApplication();
        return active != null && ProbeLogic.resultAllowed(
                packageName, windowId, epoch,
                active.packageName, active.windowId, pageEpoch, isLocked());
    }

    private boolean isPreviewScrollEvent(AccessibilityEvent event) {
        if (preview == null || !getPackageName().equals(String.valueOf(event.getPackageName()))
                || event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return false;
        }
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return false;
        try {
            if (overlayWindowId != -1 && source.getWindowId() == overlayWindowId) return true;
            Rect sourceBounds = new Rect();
            source.getBoundsInScreen(sourceBounds);
            Rect cardBounds = new Rect();
            preview.getGlobalVisibleRect(cardBounds);
            return ProbeLogic.isOverlayScrollEvent(true, true,
                    Rect.intersects(sourceBounds, cardBounds));
        } finally {
            source.recycle();
        }
    }

    private boolean isPageEvent(int eventType) {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED;
    }

    private void invalidateAndClear(boolean removeTrigger) {
        requestGate.invalidate();
        pageEpoch++;
        removePreview();
        if (removeTrigger) removeTrigger();
        maybeShutdown();
    }

    private void removePreview() {
        if (previewImage != null) {
            previewImage.setImageDrawable(null);
            previewImage = null;
        }
        if (preview != null && windowManager != null) safeRemove(preview);
        preview = null;
        overlayWindowId = -1;
        ocrLabel = null;
        ocrCandidates = null;
        selectedOcrLabel = null;
        selectedOcrIds.clear();
        ocrTextById.clear();
        if (previewJob != null) previewJob.detachPreview();
        previewJob = null;
    }

    private void closePreview() {
        requestGate.invalidate();
        removePreview();
        maybeShutdown();
    }

    private void addHeader(LinearLayout card, String title, Runnable closeAction,
                           WindowManager.LayoutParams params) {
        LinearLayout header = new LinearLayout(this);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.BLACK);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, WindowManager.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(v -> closeAction.run());
        header.addView(close);
        card.addView(header);
        enableDrag(header, card, params, null);
    }

    private WindowManager.LayoutParams overlayParams(int width, int height, int gravity) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = gravity;
        return params;
    }

    private void enableDrag(View handle, View owner, WindowManager.LayoutParams params,
                            Runnable clickAction) {
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
                    try {
                        windowManager.updateViewLayout(owner, params);
                    } catch (IllegalArgumentException ignored) {
                        // The close/screen lifecycle may remove the owner concurrently.
                    }
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
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException ignored) {
            // It was already removed by the window lifecycle.
        }
    }

    private void maybeShutdown() {
        synchronized (lifecycleLock) {
            if (destroyed && !requestGate.isBusy() && !recognizerClosed) {
                recognizer.close();
                recognizerClosed = true;
                screenshotExecutor.shutdown();
            }
        }
    }

    private void discardJob(CaptureJob job) {
        job.detachPreview();
        job.finishPhysical();
        requestGate.finishPhysical(job.requestId);
        maybeShutdown();
    }

    private int cardWidth() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(260), Math.min(screenWidth - dp(24), dp(420)));
    }

    private int previewImageHeight() {
        int available = getResources().getDisplayMetrics().heightPixels;
        return Math.max(dp(120), Math.min(dp(240), Math.round(available * 0.28f)));
    }

    private int resultHeight() {
        int available = getResources().getDisplayMetrics().heightPixels;
        return Math.max(dp(180), Math.min(dp(460), Math.round(available * 0.42f)));
    }

    private String formatBounds(Rect bounds) {
        return bounds == null ? "无边界" : bounds.flattenToString();
    }

    private boolean isLocked() {
        return keyguardManager != null && keyguardManager.isDeviceLocked();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        main.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override public void onDestroy() {
        synchronized (lifecycleLock) {
            destroyed = true;
            pageEpoch++;
            requestGate.invalidate();
        }
        invalidateAndClear(true);
        synchronized (lifecycleLock) {
            if (inFlightJob != null) {
                inFlightJob.detachPreview();
                inFlightJob = null;
            }
        }
        if (receiverRegistered) unregisterReceiver(screenReceiver);
        receiverRegistered = false;
        maybeShutdown();
        super.onDestroy();
    }

    private static final class WindowSnapshot {
        final String packageName;
        final int windowId;

        WindowSnapshot(String packageName, int windowId) {
            this.packageName = packageName;
            this.windowId = windowId;
        }
    }

    private static final class NodeReport {
        final StringBuilder output = new StringBuilder();
        final List<NodeRecord> nodes = new java.util.ArrayList<>();
        final List<TextFragment> fragments = new java.util.ArrayList<>();
        int visitedNodes;
        int textBlocks;
        boolean truncated;

        String displayText() {
            String value = TextAssembly.formatNodeSections(fragments);
            return truncated ? value + "\n[节点遍历达到深度或数量上限，结果可能截断]" : value;
        }
    }

    private static final class CaptureJob {
        final long requestId;
        final String packageName;
        final int windowId;
        final long pageEpoch;
        final Bitmap bitmap;
        private final ProbeLogic.ResourceLease resourceLease;

        CaptureJob(long requestId, String packageName, int windowId,
                   long pageEpoch, Bitmap bitmap) {
            this.requestId = requestId;
            this.packageName = packageName;
            this.windowId = windowId;
            this.pageEpoch = pageEpoch;
            this.bitmap = bitmap;
            this.resourceLease = new ProbeLogic.ResourceLease(() -> {
                if (!bitmap.isRecycled()) bitmap.recycle();
            });
        }

        void detachPreview() {
            resourceLease.detachPreview();
        }

        void finishPhysical() {
            resourceLease.finishPhysical();
        }
    }
}
