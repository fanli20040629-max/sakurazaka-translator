package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.List;
import java.util.ArrayList;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds;

/** Screen-pixel layout. Android measures text; this class only places complete rectangles. */
public final class BubbleLayout {
    private BubbleLayout() { }
    public record Size(int width, int height) { }
    public record Item(String id, Bounds anchor, List<Size> sizes) {
        public Item { sizes = List.copyOf(sizes); }
        public Item(String id, Bounds anchor, int width, int height) {
            this(id, anchor, List.of(new Size(width, height)));
        }
    }
    public record Placement(String id, Bounds bounds) { }
    public record Result(List<Placement> placements, List<String> fallbackIds) {
        public Result { placements = List.copyOf(placements); fallbackIds = List.copyOf(fallbackIds); }
    }
    public static Bounds anchor(ChatMessage message) {
        if (!"SCREEN".equals(message.coordinateSpace)
                || !("NODE_TEXT".equals(message.source) || "NODE_DESCRIPTION".equals(message.source))
                || message.warnings.stream().anyMatch(w -> switch (w) {
                    case "MANUAL_ROLE_OVERRIDE", "ROLE_UNVERIFIED", "NODE_FIELDS_DIFFER",
                         "NODE_TRAVERSAL_TRUNCATED", "CROSS_SOURCE_UNVERIFIED", "NOT_VISIBLE" -> true;
                    default -> false;
                })) return null;
        Bounds bounds = new Bounds(message.left, message.top, message.right, message.bottom);
        return bounds.isEmpty() ? null : bounds;
    }
    public static Result place(List<Item> items, Bounds viewport, List<Bounds> obstacles, int gap, int limit) {
        if (gap < 0 || limit < 0) throw new IllegalArgumentException("gap/limit");
        List<Bounds> occupied = new ArrayList<>();
        for (Bounds obstacle : obstacles) if (obstacle != null && !obstacle.isEmpty()) occupied.add(obstacle);
        for (Item item : items) if (item.anchor != null && !item.anchor.isEmpty()) occupied.add(item.anchor);
        List<Placement> placed = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        for (Item item : items) {
            Bounds position = null;
            if (viewport != null && !viewport.isEmpty() && item.anchor != null
                    && !item.anchor.isEmpty() && contains(viewport, item.anchor)
                    && placed.size() < limit) {
                for (Size size : item.sizes) {
                    if (size.width <= 0 || size.height <= 0
                            || size.width > viewport.width() || size.height > viewport.height()) continue;
                    for (Bounds candidate : candidates(item, size, gap)) {
                        if (contains(viewport, candidate) && occupied.stream().noneMatch(b -> intersects(b, candidate))) {
                            position = candidate;
                            break;
                        }
                    }
                    if (position != null) break;
                }
            }
            if (position == null) fallback.add(item.id);
            else {
                placed.add(new Placement(item.id, position));
                // Reserve a gap between translations, too.
                occupied.add(new Bounds(position.left - gap, position.top - gap,
                        position.right + gap, position.bottom + gap));
            }
        }
        return new Result(placed, fallback);
    }

    private static List<Bounds> candidates(Item item, Size size, int gap) {
        Bounds a = item.anchor;
        int w = size.width, h = size.height;
        // No searching along the page: each candidate remains attached to its own source edge.
        return List.of(rect(a.left, a.bottom + gap, w, h), rect(a.right - w, a.bottom + gap, w, h),
                rect(a.right + gap, a.top, w, h), rect(a.left - gap - w, a.top, w, h),
                rect(a.left, a.top - gap - h, w, h), rect(a.right - w, a.top - gap - h, w, h));
    }

    private static Bounds rect(int x, int y, int width, int height) {
        return new Bounds(x, y, x + width, y + height);
    }
    private static boolean contains(Bounds outer, Bounds inner) {
        return inner.left >= outer.left && inner.top >= outer.top
                && inner.right <= outer.right && inner.bottom <= outer.bottom;
    }
    private static boolean intersects(Bounds a, Bounds b) {
        return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
    }
}
