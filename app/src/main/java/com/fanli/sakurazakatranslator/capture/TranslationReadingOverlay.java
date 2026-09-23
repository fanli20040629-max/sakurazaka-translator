package com.fanli.sakurazakatranslator.capture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;
import com.fanli.sakurazakatranslator.domain.TranslationResult;
import java.util.function.Consumer;

/** One touchable reading window; owns presentation only, never sends translation requests. */
final class TranslationReadingOverlay {
    private static final int INK = Color.rgb(49, 43, 49);
    private static final int ACCENT = Color.rgb(150, 48, 99);
    private final Context context;
    private final WindowManager windows;
    private final Runnable recapture;
    private final Consumer<Boolean> visibilityChanged;
    private final ReadingSession session = new ReadingSession();
    private LinearLayout card;
    private ScrollView windowRoot;
    private ScrollView scroll;
    private TextView title, content, status;
    private Button previous, next, original, expand;
    private WindowManager.LayoutParams params;
    private boolean expanded;
    private boolean restoringScroll;
    private long revision;

    TranslationReadingOverlay(Context context, WindowManager windows, Runnable recapture,
                              Consumer<Boolean> visibilityChanged) {
        this.context = context;
        this.windows = windows;
        this.recapture = recapture;
        this.visibilityChanged = visibilityChanged;
    }

    boolean show(TranslationRequest request, TranslationResult result) {
        clear();
        if (!session.open(request, result)) return false;
        Rect safe = safeArea();
        // The result belongs to the bottom reading area; the original chat remains visible above it.
        return reopen();
    }

    boolean hasResult() { return session.hasResult(); }
    boolean isStale() { return session.isStale(); }
    boolean isShowing() { return card != null; }
    // Views do not expose an accessibility window id. The service tracks the
    // ids of its own accessibility overlays separately.
    boolean ownsWindow(int id) { return false; }

    boolean reopen() {
        if (!hasResult()) return false;
        if (isShowing()) return true;
        try {
            buildCard();
            updateBounds();
            windows.addView(windowRoot, params);
            render();
            visibilityChanged.accept(true);
            return true;
        } catch (RuntimeException error) {
            collapse();
            return false;
        }
    }

    void markStale() {
        if (!hasResult()) return;
        session.markStale();
        collapse();
    }

    void collapse() {
        remember();
        revision++;
        if (windowRoot != null) {
            try { windows.removeView(windowRoot); }
            catch (IllegalArgumentException ignored) { /* Window already removed by lifecycle. */ }
        }
        card = null;
        windowRoot = null;
        scroll = null;
        content = null;
        title = null;
        status = null;
        previous = next = original = expand = null;
        params = null;
        visibilityChanged.accept(false);
    }

    void clear() {
        collapse();
        session.clear();
        expanded = false;
    }

    private void buildCard() {
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setContentDescription("translation_reading_card");
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(250, 250, 250));
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Color.rgb(232, 229, 232));
        card.setBackground(background);
        card.setElevation(dp(8));
        windowRoot = new ScrollView(context);
        windowRoot.setFillViewport(true);
        windowRoot.setBackground(background);
        windowRoot.addView(card);
        LinearLayout header = row();
        title = label(14);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        addButton(header, "收起", this::collapse, false);
        card.addView(header);
        status = label(12);
        status.setTextColor(ACCENT);
        card.addView(status);
        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        content = label(17);
        content.setTextIsSelectable(true);
        content.setLineSpacing(dp(3), 1.15f);
        content.setPadding(dp(4), dp(10), dp(4), dp(14));
        scroll.addView(content);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = row();
        previous = addButton(navigation, "上一条", () -> navigate(-1), true);
        next = addButton(navigation, "下一条", () -> navigate(1), true);
        original = addButton(navigation, "查看原文", () -> {
            remember(); session.toggleOriginal(); render();
        }, true);
        card.addView(navigation);
        LinearLayout actions = row();
        expand = addButton(actions, "展开阅读", () -> {
            remember(); expanded = !expanded; relayout();
        }, true);
        addButton(actions, "重新取字", recapture, true);
        card.addView(actions);
        params = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle("Translation reading card");
    }

    private void navigate(int delta) {
        remember(); session.move(delta); render();
    }

    private void render() {
        if (card == null || !session.hasResult()) return;
        long ticket = ++revision;
        title.setText("风格：" + session.profileName() + " · " + (session.index() + 1) + "/" + session.size());
        String source = session.isOriginal() ? "日文原文" : "中文译文";
        boolean uncertain = session.message().warnings.contains("POSSIBLY_CLIPPED")
                || "OCR".equals(session.message().source);
        status.setText(source + (session.isStale() ? " · 上次识别内容" : " · 本次识别内容")
                + (uncertain ? "\n原文可能不完整，请核对首尾" : "")
                + ("OCR".equals(session.message().source) ? " · 表情待核对" : ""));
        content.setText(session.text());
        previous.setEnabled(session.index() > 0);
        next.setEnabled(session.index() + 1 < session.size());
        original.setText(session.isOriginal() ? "返回中文" : "查看原文");
        expand.setText(expanded ? "恢复小卡" : "展开阅读");
        // Posts are tied to this render and view. Old callbacks cannot scroll a newer message/window.
        ScrollView owner = scroll;
        int y = session.scroll();
        restoringScroll = true;
        owner.post(() -> {
            if (ticket == revision && scroll == owner) {
                owner.scrollTo(0, y);
                restoringScroll = false;
            }
        });
    }

    private void relayout() {
        try {
            updateBounds();
            windows.updateViewLayout(windowRoot, params);
            render();
        } catch (RuntimeException error) { collapse(); }
    }

    private void remember() {
        // A rapid next/previous/collapse must not save the previous message's still-pending scroll.
        if (scroll != null && !restoringScroll) session.rememberScroll(scroll.getScrollY());
    }

    private Rect safeArea() {
        WindowMetrics metrics = windows.getCurrentWindowMetrics();
        Rect bounds = new Rect(metrics.getBounds());
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Insets ime = metrics.getWindowInsets().getInsets(WindowInsets.Type.ime());
        bounds.left += Math.max(insets.left, ime.left);
        bounds.top += Math.max(insets.top, ime.top);
        bounds.right -= Math.max(insets.right, ime.right);
        bounds.bottom -= Math.max(insets.bottom, ime.bottom);
        return bounds;
    }

    private void updateBounds() {
        Rect safe = safeArea();
        int available = Math.max(1, safe.height() - dp(16));
        int normalHeight = Math.round(dp(320) * Math.max(1, context.getResources().getConfiguration().fontScale));
        params.height = expanded ? available : Math.min(available, normalHeight);
        params.width = Math.max(1, Math.min(dp(430), safe.width() - dp(16)));
        params.x = safe.left + (safe.width() - params.width) / 2;
        params.y = safe.bottom - params.height - dp(8);
        // Landscape, split-screen and large fonts can leave less space than the controls need.
        // In that case the entire card scrolls, keeping both text and every action reachable.
        boolean compact = available < Math.round(dp(360)
                * Math.max(1, context.getResources().getConfiguration().fontScale));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, compact ? dp(180) : 0, compact ? 0 : 1));
        card.setLayoutParams(new ScrollView.LayoutParams(-1, compact ? -2 : params.height));
    }

    private TextView label(int size) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(INK);
        return view;
    }
    private LinearLayout row() {
        LinearLayout view = new LinearLayout(context);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private Button addButton(LinearLayout row, String text, Runnable action, boolean weighted) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(ACCENT);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(weighted ? 0 : -2, -2, weighted ? 1 : 0));
        return button;
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
