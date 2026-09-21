package com.fanli.sakurazakatranslator.capture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;
import com.fanli.sakurazakatranslator.domain.TranslationResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Owns just the non-touchable translation window. All coordinates are from one capture. */
final class BubbleTranslationOverlay {
    private final Context context;
    private final WindowManager windows;
    private FrameLayout layer;
    private int windowId = -1;
    private int retiredWindowId = -1;
    private Bounds initialFrame;
    private Insets initialInsets;
    private boolean populated;

    BubbleTranslationOverlay(Context context, WindowManager windows) {
        this.context = context;
        this.windows = windows;
    }

    boolean isShowing() { return layer != null; }

    boolean ownsWindow(int id) {
        // A removal event can arrive after close(). Keep the last proven ID to drain it safely.
        return id >= 0 && (id == windowId || id == retiredWindowId);
    }

    void show(TranslationRequest request, TranslationResult result, List<NodeRecord> nodes,
              Bounds targetBounds, Bounds triggerBounds, BooleanSupplier isCurrent,
              Runnable onGeometryChanged, IntConsumer onPlaced) {
        close();
        FrameLayout root = new FrameLayout(context);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        layer = root;
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (layer == root && populated && !safeInsets(insets).equals(initialInsets)) {
                close();
                onGeometryChanged.run();
            }
            return insets;
        });
        // A plain View.post may run before the first frame. Wait for real dimensions, once.
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int l, int t, int r, int b,
                                                 int oldL, int oldT, int oldR, int oldB) {
                if (r <= l || b <= t) return;
                if (layer != root) return;
                if (populated) {
                    if (!sameFrame(initialFrame, frame(root))) {
                        close();
                        onGeometryChanged.run();
                    }
                    return;
                }
                if (!isCurrent.getAsBoolean()) { close(); return; }
                try {
                    rememberWindow(root);
                    int count = populate(root, request, result, nodes, targetBounds, triggerBounds);
                    populated = count > 0;
                    if (count == 0) close();
                    onPlaced.accept(count);
                } catch (RuntimeException error) {
                    close();
                    onPlaced.accept(0);
                }
            }
        });
        try {
            windows.addView(root, params);
            rememberWindow(root);
        } catch (RuntimeException error) {
            close();
            onPlaced.accept(0);
        }
    }

    private int populate(FrameLayout root, TranslationRequest request, TranslationResult result,
                         List<NodeRecord> nodes, Bounds targetBounds, Bounds triggerBounds) {
        WindowInsets insets = root.getRootWindowInsets();
        if (insets == null || targetBounds == null || root.getWidth() <= 0 || root.getHeight() <= 0) return 0;
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        initialFrame = frame(root);
        initialInsets = safeInsets(insets);
        int margin = dp(8);
        Bounds viewport = new Bounds(
                Math.max(targetBounds.left, origin[0] + initialInsets.left) + margin,
                Math.max(targetBounds.top, origin[1] + initialInsets.top) + margin,
                Math.min(targetBounds.right, origin[0] + root.getWidth() - initialInsets.right) - margin,
                Math.min(targetBounds.bottom, origin[1] + root.getHeight() - initialInsets.bottom) - margin);
        if (viewport.isEmpty()) return 0;
        List<Bounds> obstacles = new ArrayList<>();
        for (NodeRecord node : nodes) {
            if (node.visible && node.windowId == request.pageToken.windowId()
                    && (hasText(node.rawText) || hasText(node.rawDescription) || node.clickable
                    || (node.className != null && (node.className.endsWith("ImageView")
                        || node.className.endsWith("VideoView"))))) obstacles.add(node.screenBounds);
        }
        obstacles.add(triggerBounds);
        Map<String, TextView> labels = new LinkedHashMap<>();
        Map<String, String> translated = new LinkedHashMap<>();
        for (var item : result.translations) translated.put(item.id(), item.text());
        List<BubbleLayout.Item> items = new ArrayList<>();
        for (int i = 0; i < request.messages.size(); i++) {
            var message = request.messages.get(i);
            Bounds anchor = BubbleLayout.anchor(message);
            String text = translated.get(message.id);
            if (text == null) continue;
            if (anchor == null) continue;
            TextView label = label("中文 " + (i + 1) + "\n" + text);
            int width = Math.min(dp(260), viewport.width());
            List<BubbleLayout.Size> sizes = new ArrayList<>();
            measure(label, width, viewport.height(), sizes);
            int sideWidth = Math.max(viewport.right - anchor.right, anchor.left - viewport.left) - dp(6);
            if (sideWidth >= dp(140) && sideWidth < width) measure(label, sideWidth, viewport.height(), sizes);
            labels.put(message.id, label);
            items.add(new BubbleLayout.Item(message.id, anchor, sizes));
        }
        var layout = BubbleLayout.place(items, viewport, obstacles, dp(6), 4);
        for (var placement : layout.placements()) {
            Bounds b = placement.bounds();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(b.width(), b.height(), Gravity.TOP | Gravity.LEFT);
            params.leftMargin = b.left - origin[0];
            params.topMargin = b.top - origin[1];
            root.addView(labels.get(placement.id()), params);
        }
        return layout.placements().size();
    }

    private void measure(TextView label, int width, int availableHeight, List<BubbleLayout.Size> sizes) {
        label.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        // Both width choices preserve full text; long results remain in the full card.
        if (label.getMeasuredHeight() <= Math.min(dp(200), availableHeight / 2)) {
            sizes.add(new BubbleLayout.Size(width, label.getMeasuredHeight()));
        }
    }

    private TextView label(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.rgb(48, 53, 66));
        view.setPadding(dp(10), dp(7), dp(10), dp(7));
        view.setLineSpacing(dp(2), 1.05f);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(248, 245, 252));
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), Color.rgb(194, 179, 211));
        view.setBackground(background);
        return view;
    }

    private void rememberWindow(View root) {
        AccessibilityNodeInfo node = root.createAccessibilityNodeInfo();
        if (node != null && node.getWindowId() >= 0) windowId = node.getWindowId();
    }

    void close() {
        FrameLayout old = layer;
        layer = null;
        populated = false;
        initialFrame = null;
        initialInsets = null;
        if (windowId >= 0) retiredWindowId = windowId;
        windowId = -1;
        if (old != null) {
            try { windows.removeView(old); }
            catch (IllegalArgumentException ignored) { /* Already removed by Android. */ }
        }
    }

    private static Insets safeInsets(WindowInsets insets) {
        return Insets.max(insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout()), insets.getInsets(WindowInsets.Type.ime()));
    }

    private static Bounds frame(View view) {
        int[] origin = new int[2];
        view.getLocationOnScreen(origin);
        return new Bounds(origin[0], origin[1], origin[0] + view.getWidth(), origin[1] + view.getHeight());
    }

    private static boolean sameFrame(Bounds a, Bounds b) {
        return a != null && a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
