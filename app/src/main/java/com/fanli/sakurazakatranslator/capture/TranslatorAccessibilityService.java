package com.fanli.sakurazakatranslator.capture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fanli.sakurazakatranslator.ProbePreferences;
import com.fanli.sakurazakatranslator.BuildConfig;
import com.fanli.sakurazakatranslator.TranslationSettingsActivity;
import com.fanli.sakurazakatranslator.translation.DeepSeekProvider;
import com.fanli.sakurazakatranslator.translation.TranslationSettings;
import com.fanli.sakurazakatranslator.translation.TranslationRunner;
import com.fanli.sakurazakatranslator.domain.TranslationRequestFactory;
import com.fanli.sakurazakatranslator.domain.PageToken;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.text.Text;
import com.fanli.sakurazakatranslator.capture.NodeTreeReader.NodeReport;
import com.fanli.sakurazakatranslator.domain.StyleProfile;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

public final class TranslatorAccessibilityService extends AccessibilityService {
    private static final StyleProfile DEFAULT_STYLE =
            new StyleProfile("default", "默认风格", "保留原文语气、符号和换行");

    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TranslationRunner translationRunner = new TranslationRunner(command -> main.post(command));
    private final Object lifecycleLock = new Object();
    private final OcrProcessor ocrProcessor = new OcrProcessor();
    private final NodeTreeReader nodeReader = new NodeTreeReader();
    // One coordinator owns both logical validity and physical screenshot/OCR occupancy.
    private final CaptureCoordinator captureCoordinator = new CaptureCoordinator();

    private WindowManager windowManager;
    private KeyguardManager keyguardManager;
    private Button trigger;
    private LinearLayout preview;
    private CandidatePanel candidatePanel;
    private PageToken previewPage;
    private List<NodeRecord> previewNodes = List.of();
    private final CandidateSelection candidateSelection = new CandidateSelection();
    // Preview only; never submitted. Clear on uncheck, close, or page invalidation.
    private TranslationRequest pendingTranslationRequest;
    private CaptureJob previewJob;
    private CaptureJob inFlightJob;
    private volatile boolean destroyed;
    private boolean receiverRegistered;
    private boolean recognizerClosed;
    private long pageEpoch;
    private String foregroundPackage;
    private int foregroundWindowId = -1;
    private int overlayWindowId = -1;
    private int triggerWindowId = -1;

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
        // Our synthetic page shares the package, but not the registered overlay window IDs.
        if (ProbeLogic.isOwnedWindowEvent(getPackageName().equals(eventPackage),
                event.getWindowId(), overlayWindowId, triggerWindowId)) {
            refreshTriggerVisibility();
            return;
        }
        if (isPageEvent(event.getEventType())) {
            WindowSnapshot active = activeApplication();
            updateForegroundIdentity(active);
            boolean sameWindow = active != null && event.getWindowId() == active.windowId;
            if (active != null && sameWindow && isAllowedPackage(active.packageName)) {
                markPageChanged();
            } else if (preview != null && event.getWindowId() < 0) {
                // Unknown origin is not proof that an event belongs to the card.
                markPageChanged();
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
            return ProbePreferences.syntheticMode(this) && !TranslationSettingsActivity.isVisible();
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
            captureCoordinator.invalidate();
            removePreview();
        }
    }

    private void markPageChanged() {
        pageEpoch++;
        captureCoordinator.invalidate();
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
            AccessibilityNodeInfo triggerNode = candidate.createAccessibilityNodeInfo();
            triggerWindowId = triggerNode == null ? -1 : triggerNode.getWindowId();
        } catch (RuntimeException ignored) {
            safeRemove(candidate);
            trigger = null;
            triggerWindowId = -1;
        }
    }

    private void removeTrigger() {
        if (trigger != null && windowManager != null) safeRemove(trigger);
        trigger = null;
        triggerWindowId = -1;
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

        final long requestId = captureCoordinator.begin();
        if (requestId < 0) {
            root.recycle();
            toast("识别正在进行，请等待当前任务完成");
            return;
        }

        removePreview();
        final long startedAt = SystemClock.elapsedRealtime();
        final PageToken page;
        final NodeReport nodeReport;
        try {
            CharSequence packageName = root.getPackageName();
            page = new PageToken(requestId, packageName == null ? null : packageName.toString(),
                    target.getId(), pageEpoch);
            nodeReport = nodeReader.read(root);
        } catch (RuntimeException error) {
            captureCoordinator.finishPhysical(requestId);
            maybeShutdown();
            toast("节点读取失败：" + error.getClass().getSimpleName());
            return;
        } finally {
            root.recycle();
        }

        try {
            if (!showNodePreview(page, nodeReport, startedAt)) {
                captureCoordinator.finishPhysical(requestId);
                maybeShutdown();
                return;
            }
            if (!captureCoordinator.advance(requestId, CaptureCoordinator.Physical.SCREENSHOT)) {
                captureCoordinator.finishPhysical(requestId);
                toast("截图任务无法启动");
                return;
            }
            takeScreenshotOfWindow(page.windowId(), screenshotExecutor, new TakeScreenshotCallback() {
                @Override public void onFailure(int errorCode) {
                    main.post(() -> finishScreenshotFailure(
                            page, errorCode));
                }

                @Override public void onSuccess(ScreenshotResult result) {
                    Bitmap software = copyScreenshot(result);
                    if (software == null) {
                        main.post(() -> finishScreenshotFailure(
                                page, -1));
                        return;
                    }

                    CaptureJob job = new CaptureJob(page, software);
                    synchronized (lifecycleLock) {
                        if (destroyed) {
                            discardJob(job);
                            return;
                        }
                        inFlightJob = job;
                    }
                    main.post(() -> {
                        if (!selectionAllowed(page)) {
                            if (page.equals(previewPage)) closePreview();
                            discardJob(job);
                            return;
                        }
                        previewJob = job;
                        try {
                            candidatePanel.attachImage(job.bitmap);
                        } catch (RuntimeException error) {
                            closePreview();
                            completeJob(job);
                            return;
                        }
                        startOcr(job, startedAt);
                    });
                }
            });
        } catch (RuntimeException error) {
            if (preview == null) closePreview();
            main.post(() -> finishScreenshotFailure(
                    page, -2));
        }
    }

    /** Runs on the main thread, after the existing node card owns the preview image. */
    private void startOcr(CaptureJob job, long startedAt) {
        if (!selectionAllowed(job.page) || recognizerClosed
                || !captureCoordinator.advance(job.page.requestId(), CaptureCoordinator.Physical.OCR)) {
            if (previewJob == job) closePreview();
            discardJob(job);
            return;
        }
        final Task<Text> task;
        try {
            task = ocrProcessor.recognize(job.bitmap);
        } catch (RuntimeException error) {
            try {
                showOcrError(job, error.getClass().getSimpleName());
            } finally {
                completeJob(job);
            }
            return;
        }
        // A started Task owns the bitmap until it actually completes. Closing the UI only
        // detaches its preview lease; it does not claim to cancel ML Kit's physical work.
        task.addOnCompleteListener(getMainExecutor(), completed -> {
            try {
                if (!selectionAllowed(job.page)) {
                    if (job.page.equals(previewPage)) closePreview();
                    return;
                }
                if (completed.isCanceled()) {
                    showOcrError(job, "已取消");
                } else if (!completed.isSuccessful()) {
                    showOcrError(job, "识别未完成");
                } else {
                    List<TextFragment> fragments = OcrProcessor.fragments(completed.getResult());
                    if (candidateSelection.appendOcr(job.page, fragments)) {
                        candidatePanel.showOcr(candidateSelection.fragments().stream()
                                .filter(f -> f.source == Source.OCR)
                                .collect(java.util.stream.Collectors.toList()),
                                SystemClock.elapsedRealtime() - startedAt);
                        updateSelection(job.page);
                    }
                }
            } catch (RuntimeException error) {
                showOcrError(job, "结果整理失败");
            } finally {
                completeJob(job);
            }
        });
    }

    private void showOcrError(CaptureJob job, String reason) {
        if (selectionAllowed(job.page)) candidatePanel.showFailure("图片识别：" + reason);
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

    private boolean showNodePreview(PageToken page, NodeReport report, long startedAt) {
        if (!captureCoordinator.isCurrent(page.requestId())
                || !isCurrentPage(page.packageName(), page.windowId(), page.pageEpoch())) return false;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(Color.WHITE);
        card.setContentDescription("probe_result_card");

        WindowManager.LayoutParams params = overlayParams(cardWidth(),
                WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        params.x = Math.max(0, (getResources().getDisplayMetrics().widthPixels - params.width) / 2);
        params.y = dp(36);
        addHeader(card, "原文整理 · " + BuildConfig.VERSION_NAME, this::closePreview, params);

        candidateSelection.open(page, report.fragments);
        previewNodes = report.nodes;
        previewPage = page;
        candidatePanel = new CandidatePanel(this, report.fragments,
                String.format(Locale.ROOT,
                        "版本=%s / %d\n请求=%d · windowId=%d · pageEpoch=%d\n"
                                + "节点=%d · 候选=%d · 节点阶段=%dms\n%s\n"
                                + "节点位置为屏幕坐标，OCR 位置为截图坐标；映射和消息边界均未验证。",
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, page.requestId(),
                        page.windowId(), page.pageEpoch(), report.visitedNodes, report.textBlocks,
                        SystemClock.elapsedRealtime() - startedAt,
                        report.truncated ? "读取达到上限，原文可能不全" : "尚未确认可见正文完整性"),
                (id, selected) -> {
                    // A queued click on an old card must not close a newer card.
                    if (!page.equals(previewPage)) return;
                    if (!selectionAllowed(page)) {
                        closePreview();
                        return;
                    }
                    if (candidateSelection.setSelected(page, id, selected)) {
                        candidatePanel.invalidateTranslation();
                        updateSelection(page);
                    }
                },
                (group, style) -> prepareTranslation(page, group, style),
                (request, model) -> sendTranslation(page, request, model),
                translationRunner::cancel);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(candidatePanel);
        card.addView(scroll, new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, resultHeight()));

        preview = card;
        try {
            windowManager.addView(card, params);
            AccessibilityNodeInfo cardNode = card.createAccessibilityNodeInfo();
            overlayWindowId = cardNode == null ? -1 : cardNode.getWindowId();
            captureCoordinator.show(page.requestId());
            return true;
        } catch (RuntimeException error) {
            closePreview();
            return false;
        }
    }

    private void finishScreenshotFailure(PageToken page, int errorCode) {
        captureCoordinator.finishPhysical(page.requestId());
        maybeShutdown();
        if (selectionAllowed(page)) {
            candidatePanel.showFailure("此窗口截图不可用（" + errorCode + "）");
        }
    }

    private boolean selectionAllowed(PageToken page) {
        return page != null && candidatePanel != null && page.equals(previewPage)
                && candidateSelection.isCurrent(page) && captureCoordinator.isCurrent(page.requestId())
                && isCurrentPage(page.packageName(), page.windowId(), page.pageEpoch());
    }

    private void updateSelection(PageToken page) {
        if (!selectionAllowed(page)) return;
        pendingTranslationRequest = candidateSelection.request(page, DEFAULT_STYLE).orElse(null);
        candidatePanel.showSelection(candidateSelection.selectedText(), pendingTranslationRequest);
    }

    private TranslationRequest prepareTranslation(PageToken page, boolean group, StyleProfile style) {
        if (!selectionAllowed(page)) return null;
        TranslationRequest request = candidateSelection.request(page, style).orElse(null);
        if (request == null || !group) return request;
        return TranslationRequestFactory.confirmed(page,
                MessageGrouper.group(request.messages, previewNodes), style);
    }

    /** A button press on the visible confirmation is the only network entry point. */
    private void sendTranslation(PageToken page, TranslationRequest request, String model) {
        if (!selectionAllowed(page) || !page.equals(request.pageToken)) return;
        CandidatePanel owner = candidatePanel;
        try {
            String key = new TranslationSettings(this).readKey();
            if (key.isBlank()) {
                owner.showTranslationFailure("KEY_MISSING");
                return;
            }
            boolean started = translationRunner.start(request, new DeepSeekProvider(key, model),
                    result -> {
                        if (owner == candidatePanel && selectionAllowed(page)) owner.showTranslation(request, result);
                    }, error -> {
                        if (owner == candidatePanel && selectionAllowed(page)) owner.showTranslationFailure(error);
                    });
            if (!started) owner.showTranslationFailure("BUSY");
        } catch (Exception error) {
            owner.showTranslationFailure("CONFIGURATION");
        }
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
            // Overlapping rectangles do not establish ownership, especially on the synthetic page.
            return ProbeLogic.isOwnedWindowEvent(true, source.getWindowId(), overlayWindowId, -1);
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
        captureCoordinator.invalidate();
        pageEpoch++;
        removePreview();
        if (removeTrigger) removeTrigger();
        maybeShutdown();
    }

    private void removePreview() {
        translationRunner.cancel();
        if (candidatePanel != null) candidatePanel.releaseImage();
        if (preview != null && windowManager != null) safeRemove(preview);
        preview = null;
        overlayWindowId = -1;
        candidatePanel = null;
        previewPage = null;
        previewNodes = List.of();
        candidateSelection.clear();
        pendingTranslationRequest = null;
        if (previewJob != null) previewJob.detachPreview();
        previewJob = null;
    }

    private void closePreview() {
        captureCoordinator.closeResult();
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
                    int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - owner.getWidth());
                    int maxY = Math.max(0, getResources().getDisplayMetrics().heightPixels - owner.getHeight());
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
            if (destroyed && !captureCoordinator.isBusy() && !recognizerClosed) {
                ocrProcessor.close();
                recognizerClosed = true;
                screenshotExecutor.shutdown();
            }
        }
    }

    private void discardJob(CaptureJob job) {
        job.detachPreview();
        completeJob(job);
    }

    /** All OCR terminal paths release physical occupancy without invalidating a valid preview. */
    private void completeJob(CaptureJob job) {
        job.finishPhysical();
        captureCoordinator.finishPhysical(job.page.requestId());
        synchronized (lifecycleLock) {
            if (inFlightJob == job) inFlightJob = null;
        }
        maybeShutdown();
    }

    private int cardWidth() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.max(1, Math.min(screenWidth - dp(24), dp(420)));
    }

    private int resultHeight() {
        int available = getResources().getDisplayMetrics().heightPixels;
        return Math.max(1, Math.min(dp(520), Math.round(available * 0.60f)));
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

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        invalidateAndClear(true);
        refreshTriggerVisibility();
    }

    @Override public void onDestroy() {
        translationRunner.close();
        synchronized (lifecycleLock) {
            destroyed = true;
            pageEpoch++;
            captureCoordinator.invalidate();
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

    private static final class CaptureJob {
        final PageToken page;
        final Bitmap bitmap;
        private final ProbeLogic.ResourceLease resourceLease;

        CaptureJob(PageToken page, Bitmap bitmap) {
            this.page = page;
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
