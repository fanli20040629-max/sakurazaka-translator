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
import com.fanli.sakurazakatranslator.domain.TranslationResult;
import com.fanli.sakurazakatranslator.domain.LongMessageDraft;
import com.fanli.sakurazakatranslator.domain.ChatMessage;

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
    private final Runnable retainedMemberCheck = this::checkRetainedMember;
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
    private TranslationReadingOverlay readingOverlay;
    private final LongMessageDraft longMessageDraft = new LongMessageDraft();
    private final MemberSession memberSession = new MemberSession();
    private MemberResolver.Resolution previewMember = new MemberResolver.Resolution(
            MemberResolver.Status.MISSING, null, "", null, java.util.Set.of());
    private Bounds previewWindowBounds;
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
        readingOverlay = new TranslationReadingOverlay(this, windowManager, this::capture,
                visible -> {
                    if (trigger != null) trigger.setVisibility(visible ? View.GONE : View.VISIBLE);
                    updateTriggerLabel();
                });
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
        if (getPackageName().equals(eventPackage) && ownsOverlayWindow(event.getWindowId())) {
            refreshTriggerVisibility();
            return;
        }
        if (isPageEvent(event.getEventType())) {
            WindowSnapshot active = activeApplication();
            updateForegroundIdentity(active);
            boolean sameWindow = active != null && event.getWindowId() == active.windowId;
            boolean windowGeometryChanged = event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    && (event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_BOUNDS) != 0;
            if (active != null && sameWindow && isAllowedPackage(active.packageName)
                    && (event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED || windowGeometryChanged)) {
                markPageChanged();
            } else if (preview != null && event.getWindowId() < 0
                    && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
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
        boolean ownedOverlayActive = windows.stream().anyMatch(window -> window.isActive()
                && window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                && ownsOverlayWindow(window.getId()));
        // A touched overlay can be active while the target application is still focused underneath.
        // Choose the highest application, never an arbitrary older allowed window behind another app.
        AccessibilityWindowInfo highest = windows.stream()
                .filter(window -> window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION)
                .max(java.util.Comparator.comparingInt(AccessibilityWindowInfo::getLayer)).orElse(null);
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            if (!window.isActive() && !(ownedOverlayActive && highest != null
                    && window.getId() == highest.getId() && window.getId() == foregroundWindowId)) continue;
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

    private boolean ownsOverlayWindow(int id) {
        return id >= 0 && (id == overlayWindowId || id == triggerWindowId
                || (readingOverlay != null && readingOverlay.ownsWindow(id)));
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
            clearRetainedContent();
        }
    }

    private void markPageChanged() {
        pageEpoch++;
        captureCoordinator.invalidate();
        removePreview();
        if (readingOverlay != null) readingOverlay.markStale();
        updateTriggerLabel();
        // Coalesce event bursts without postponing the check indefinitely during scrolling.
        if ((!longMessageDraft.isEmpty() || (readingOverlay != null && readingOverlay.hasResult()))
                && !main.hasCallbacks(retainedMemberCheck)) main.postDelayed(retainedMemberCheck, 200);
    }

    private void checkRetainedMember() {
        if (destroyed || isLocked()) return;
        WindowSnapshot active = activeApplication();
        if (active == null || !isAllowedPackage(active.packageName)
                || active.windowId != foregroundWindowId
                || !active.packageName.equals(foregroundPackage)) return;
        // Missing titles during scrolling preserve the draft. Only reliable changes clear it.
        if (memberSession.observe(readCurrentMember(active.windowId))) clearRetainedContent();
    }

    private void showTrigger() {
        if (trigger != null || destroyed || windowManager == null || isLocked()) return;
        Button candidate = new Button(this);
        candidate.setText("翻译当前屏");
        candidate.setTextColor(Color.WHITE);
        candidate.setBackgroundColor(Color.rgb(161, 64, 111));
        WindowManager.LayoutParams params = overlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        params.y = dp(72);
        params.x = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(132));
        enableDrag(candidate, candidate, params, () -> {
            if (readingOverlay != null && readingOverlay.hasResult() && !readingOverlay.isStale()) {
                if (!readingOverlay.reopen()) {
                    readingOverlay.clear();
                    updateTriggerLabel();
                    toast("阅读卡片暂时无法打开，请重新点浮窗取字。");
                }
            } else capture();
        }, null);
        try {
            windowManager.addView(candidate, params);
            trigger = candidate;
            AccessibilityNodeInfo triggerNode = candidate.createAccessibilityNodeInfo();
            triggerWindowId = triggerNode == null ? -1 : triggerNode.getWindowId();
            updateTriggerLabel();
            if (readingOverlay != null && readingOverlay.isShowing()) trigger.setVisibility(View.GONE);
        } catch (RuntimeException ignored) {
            safeRemove(candidate);
            trigger = null;
            triggerWindowId = -1;
        }
    }

    private void updateTriggerLabel() {
        if (trigger == null) return;
        trigger.setText(readingOverlay != null && readingOverlay.hasResult() && !readingOverlay.isStale() ? "打开译文"
                : !longMessageDraft.isEmpty() ? "补取这一屏" : "翻译当前屏");
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
                    || window.getId() != active.windowId) {
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
        main.removeCallbacks(retainedMemberCheck);
        // Hide our reading window before taking a new snapshot; completed translations remain local only.
        if (readingOverlay != null) readingOverlay.clear();
        updateTriggerLabel();
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
            Rect targetRect = new Rect();
            target.getBoundsInScreen(targetRect);
            previewWindowBounds = new Bounds(targetRect.left, targetRect.top, targetRect.right, targetRect.bottom);
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
            MemberResolver.Resolution member = MemberResolver.resolve(
                    nodeReport.nodes, previewWindowBounds, new TranslationSettings(this).profiles(),
                    nodeReport.truncated);
            if (memberSession.observe(member)) {
                readingOverlay.clear();
                longMessageDraft.clear();
                memberSession.clearDraft();
            }
            memberSession.begin(page, member);
            previewMember = member;
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

        List<TextFragment> fragments = report.fragments.stream().map(fragment -> {
            Bounds b = fragment.bounds;
            boolean clipped = b == null || previewWindowBounds == null || b.isEmpty()
                    || b.top <= previewWindowBounds.top + 2 || b.bottom >= previewWindowBounds.bottom - 2;
            return clipped ? fragment.withWarning("POSSIBLY_CLIPPED") : fragment;
        }).toList();
        List<String> memberNames = memberNamesFor(previewMember);
        java.util.Set<String> autoSelected = previewMember.status() == MemberResolver.Status.MATCHED
                ? MessageGrouper.autoSelectedIds(fragments, report.nodes, memberNames, page.windowId())
                : java.util.Set.of();
        candidateSelection.open(page, fragments, autoSelected);
        previewNodes = report.nodes;
        previewPage = page;
        candidatePanel = new CandidatePanel(this, fragments,
                MessageGrouper.selectionGroups(fragments, report.nodes),
                String.format(Locale.ROOT,
                        "版本=%s / %d\n请求=%d · windowId=%d · pageEpoch=%d\n"
                                + "节点=%d · 候选=%d · 节点阶段=%dms\n%s\n"
                                + "节点使用屏幕坐标；分组依据列表结构。OCR 截图坐标不用于贴近显示。",
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, page.requestId(),
                        page.windowId(), page.pageEpoch(), report.visitedNodes, report.textBlocks,
                        SystemClock.elapsedRealtime() - startedAt,
                        report.truncated ? "读取达到上限，原文可能不全" : "尚未确认可见正文完整性"),
                (ids, selected) -> {
                    // A queued click on an old card must not close a newer card.
                    if (!page.equals(previewPage)) return;
                    if (!selectionAllowed(page)) {
                        closePreview();
                        return;
                    }
                    if (candidateSelection.setSelected(page, ids, selected)) {
                        candidatePanel.invalidateTranslation();
                        updateSelection(page);
                    }
                },
                (group, style) -> prepareTranslation(page, group, style),
                (request, model) -> sendTranslation(page, request, model),
                this::cancelTranslationDisplay,
                (request, result) -> showReading(page, request, result),
                new CandidatePanel.LongMessageActions() {
                    public String text() { return longMessageDraft.text(); }
                    public String appendSelection() { return appendLongMessage(page); }
                    public TranslationRequest prepare(StyleProfile style) {
                        if (!selectionAllowed(page) || longMessageDraft.isEmpty()
                                || !memberMatchesCurrentPage(page)
                                || !memberSession.canAppendDraft(page)) return null;
                        return TranslationRequestFactory.confirmed(page, List.of(longMessageDraft.message()), style);
                    }
                    public void clear() { longMessageDraft.clear(); memberSession.clearDraft(); updateTriggerLabel(); }
                    public void continueCapture() { closePreview(); updateTriggerLabel(); }
                }, previewMember, new TranslationSettings(this).profiles(),
                new TranslationSettings(this).selectedProfile(),
                () -> memberSession.style(page), profile -> {
                    StyleProfile previous = memberSession.style(page);
                    memberSession.choose(page, profile);
                    if (previous != null && !previous.id.equals(profile.id)) {
                        memberSession.clearDraft();
                        longMessageDraft.clear();
                        updateTriggerLabel();
                    }
                }, autoSelected.size());
        if (getPackageName().equals(page.packageName()) && ProbePreferences.syntheticMode(this)) {
            candidatePanel.addLocalReadingDemo(() -> {
                if (selectionAllowed(page) && ProbePreferences.syntheticMode(this)) {
                    showReading(page, ReadingDemo.request(page), ReadingDemo.result());
                }
            });
        }
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
            updateSelection(page);
            // One opt-in attempt per explicit capture. Never triggered by OCR completion or page events.
            if (longMessageDraft.isEmpty() && memberSession.canQuickTranslate(page)
                    && candidateSelection.canQuickTranslate()
                    && new TranslationSettings(this).quickTranslation()) candidatePanel.quickTranslate();
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
        candidatePanel.showSelection(candidateSelection.selectedText(), pendingTranslationRequest,
                candidateSelection.selectedIds());
    }

    private static List<String> memberNamesFor(MemberResolver.Resolution resolution) {
        if (resolution == null || resolution.profile() == null) return List.of();
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        names.add(resolution.profile().displayName);
        names.addAll(resolution.profile().aliases);
        if (resolution.name() != null && !resolution.name().isBlank()) names.add(resolution.name());
        return List.copyOf(names);
    }

    private TranslationRequest prepareTranslation(PageToken page, boolean group, StyleProfile style) {
        if (!selectionAllowed(page) || style == null || !memberMatchesCurrentPage(page)) return null;
        TranslationRequest request = candidateSelection.request(page, style).orElse(null);
        if (request == null) return null;
        List<ChatMessage> messages = group ? MessageGrouper.group(request.messages, previewNodes) : request.messages;
        messages = messages.stream().map(message -> {
            if (!BodySelectionPolicy.possiblyClipped(message, previewWindowBounds)
                    || message.warnings.contains("POSSIBLY_CLIPPED")) return message;
            var warnings = new java.util.ArrayList<>(message.warnings);
            warnings.add("POSSIBLY_CLIPPED");
            return new ChatMessage(message.id, message.originalText, message.source,
                    message.left, message.top, message.right, message.bottom, message.coordinateSpace,
                    message.provenance, warnings);
        }).toList();
        return TranslationRequestFactory.confirmed(page, messages, style);
    }

    private String appendLongMessage(PageToken page) {
        TranslationRequest request = prepareTranslation(page, true, memberSession.style(page));
        if (request == null) return "请先选择本屏中该长消息的文字。";
        if (!memberSession.canAppendDraft(page)) {
            return longMessageDraft.isEmpty()
                    ? "当前成员身份未确认，不能开始长文草稿。请先确认本页成员。"
                    : "当前成员身份未确认，不能拼接旧长文草稿。请清除旧草稿后重新收集。";
        }
        final ChatMessage part;
        try {
            // Multiple OCR lines are combined only after the explicit same-message checkbox.
            part = LongMessageDraft.selectedPart(request.messages);
        } catch (IllegalArgumentException error) {
            return "本次选中了多条或边界不明的片段。请只选一条节点消息，或同一条消息按顺序排列的 OCR 行。";
        }
        LongMessageDraft.Outcome outcome = longMessageDraft.append(page, part);
        if (outcome == LongMessageDraft.Outcome.STARTED) memberSession.bindDraft(page);
        updateTriggerLabel();
        return switch (outcome) {
            case STARTED -> "已开始收集。请核对从消息开头开始；可收起并滚动补取下一屏。";
            case APPENDED -> "已合并 " + longMessageDraft.parts() + " 屏，请核对草稿。到结尾后勾选完整性确认，再准备翻译。";
            case DUPLICATE -> "这段内容已经收集，没有重复追加。若节点已提供全文，可直接核对首尾后翻译。";
            case WRONG_CONTEXT -> "窗口已变化，不能合并。请清除草稿后重新开始。";
            case WRONG_SOURCE -> "两次来源不同，不能混合节点与 OCR。请用同一来源重新取字。";
            case LIMIT -> "草稿达到 6000 字符或 12 屏上限，未追加。请分段翻译。";
            case EMPTY -> "没有可加入的文字。";
            case NO_OVERLAP -> "缺少明确且唯一的重叠，未追加。请向回滚一点，保留几行相同文字再取字。";
        };
    }

    /** Only explicit confirmation or an explicit capture with saved quick-send consent can enter here. */
    private void sendTranslation(PageToken page, TranslationRequest request, String model) {
        CandidatePanel owner = candidatePanel;
        if (!selectionAllowed(page) || !page.equals(request.pageToken)) return;
        if (!translationMemberReady(owner, page)) return;
        try {
            String key = new TranslationSettings(this).readKey();
            if (key.isBlank()) {
                owner.showTranslationFailure("KEY_MISSING");
                return;
            }
            boolean started = translationRunner.start(request, new DeepSeekProvider(key, model),
                    result -> {
                        if (translationMemberReady(owner, page)) {
                            owner.showTranslation(request, result);
                            showReading(page, request, result);
                        }
                    }, error -> {
                        if (translationMemberReady(owner, page)) owner.showTranslationFailure(error);
                    });
            if (!started) owner.showTranslationFailure("BUSY");
        } catch (Exception error) {
            owner.showTranslationFailure("CONFIGURATION");
        }
    }

    private void showReading(PageToken page, TranslationRequest request, TranslationResult result) {
        if (!selectionAllowed(page) || !page.equals(request.pageToken) || readingOverlay == null) return;
        if (readingOverlay.show(request, result)) {
            closePreview();
            updateTriggerLabel();
        } else {
            readingOverlay.clear();
            updateTriggerLabel();
            toast("阅读卡片暂时无法显示，译文保留在原文整理卡中。");
        }
    }

    /** Every live card receives a terminal state, even when the title temporarily disappears. */
    private boolean translationMemberReady(CandidatePanel owner, PageToken page) {
        if (owner != candidatePanel || !selectionAllowed(page)) return false;
        if (memberMatchesCurrentPage(page)) return true;
        if (owner == candidatePanel && selectionAllowed(page)) {
            owner.showTranslationFailure("MEMBER_UNCONFIRMED");
        }
        return false;
    }

    private boolean isCurrentPage(String packageName, int windowId, long epoch) {
        if (destroyed || isLocked() || !isAllowedPackage(packageName)) return false;
        WindowSnapshot active = activeApplication();
        return active != null && ProbeLogic.resultAllowed(
                packageName, windowId, epoch,
                active.packageName, active.windowId, pageEpoch, isLocked());
    }

    /** Re-reads the current target title before preparing or displaying a paid result. */
    private boolean memberMatchesCurrentPage(PageToken page) {
        if (!selectionAllowed(page)) return false;
        MemberResolver.Resolution fresh = readCurrentMember(page.windowId());
        if (fresh == null) return false;
        boolean capturedIdentityChanged = memberSession.reliableIdentityChanged(fresh);
        boolean changed = memberSession.observe(fresh) || capturedIdentityChanged;
        if (changed) {
            markPageChanged();
            clearRetainedContent();
            toast("检测到聊天成员变化，请重新取字。");
            return false;
        }
        return memberSession.matches(page, fresh);
    }

    private MemberResolver.Resolution readCurrentMember(int windowId) {
        AccessibilityWindowInfo target = targetWindow();
        if (target == null || target.getId() != windowId) return null;
        AccessibilityNodeInfo root = target.getRoot();
        if (root == null) return null;
        try {
            Rect bounds = new Rect();
            target.getBoundsInScreen(bounds);
            NodeReport report = nodeReader.read(root);
            return MemberResolver.resolve(report.nodes,
                    new Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    new TranslationSettings(this).profiles(), report.truncated);
        } catch (RuntimeException error) {
            return null;
        } finally {
            root.recycle();
        }
    }

    private void cancelTranslationDisplay() {
        translationRunner.cancel();
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
        clearRetainedContent();
        if (removeTrigger) removeTrigger();
        maybeShutdown();
    }

    private void clearRetainedContent() {
        main.removeCallbacks(retainedMemberCheck);
        if (readingOverlay != null) readingOverlay.clear();
        longMessageDraft.clear();
        memberSession.clear();
        updateTriggerLabel();
    }

    private void removePreview() {
        translationRunner.cancel();
        updateTriggerLabel();
        previewWindowBounds = null;
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
        enableDrag(header, card, params, null, null);
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
                            Runnable clickAction, Runnable onDragStart) {
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
                    if (!moved) {
                        if (Math.abs(dx) <= dp(6) && Math.abs(dy) <= dp(6)) return true;
                        moved = true;
                        if (onDragStart != null) onDragStart.run();
                    }
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
