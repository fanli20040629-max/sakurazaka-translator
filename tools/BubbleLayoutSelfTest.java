package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds;

public final class BubbleLayoutSelfTest {
    private static int checks;
    public static void main(String[] args) {
        Bounds screen = new Bounds(0, 24, 400, 760);
        Bounds original = new Bounds(20, 100, 180, 180);
        var item = new BubbleLayout.Item("one", original, 170, 60);
        var placed = BubbleLayout.place(List.of(item), screen, List.of(original), 6, 4);
        check(placed.placements().size() == 1, "nearby rectangle is placed");
        check(!intersects(placed.placements().get(0).bounds(), original), "never cover the original");
        check(inside(screen, placed.placements().get(0).bounds()), "stay in safe screen area");
        check(BubbleLayout.place(List.of(item), screen, List.of(screen), 6, 4).fallbackIds().equals(List.of("one")),
                "dense page falls back without hiding text");
        check(BubbleLayout.place(List.of(new BubbleLayout.Item("long", original, 170, 900)), screen,
                List.of(), 6, 4).placements().isEmpty(), "long translation is not truncated");
        check(BubbleLayout.place(List.of(new BubbleLayout.Item("clipped", new Bounds(0, 0, 90, 60), 100, 40)),
                screen, List.of(), 6, 4).placements().isEmpty(), "partly offscreen source cannot anchor");
        check(BubbleLayout.place(List.of(new BubbleLayout.Item("ocr", null, 100, 40)), screen,
                List.of(), 6, 4).fallbackIds().equals(List.of("ocr")), "unknown mapping falls back");
        var second = new BubbleLayout.Item("two", new Bounds(20, 250, 180, 290), 170, 60);
        check(BubbleLayout.place(List.of(item, second), screen, List.of(), 6, 1).fallbackIds().equals(List.of("two")),
                "bounded number of overlays, all remaining ids retained");
        ChatMessage node = message("SCREEN", List.of());
        check(BubbleLayout.anchor(node) != null, "valid node uses screen coordinates");
        check(BubbleLayout.anchor(message("IMAGE", List.of())) == null, "image coordinates never used as screen");
        check(BubbleLayout.anchor(message("SCREEN", List.of("MANUAL_ROLE_OVERRIDE"))) == null,
                "manually selected metadata remains in full card");
        check(BubbleLayout.anchor(message("SCREEN", List.of("NODE_TRAVERSAL_TRUNCATED"))) == null,
                "truncated tree cannot support reliable overlay");
        var narrow = new BubbleLayout.Item("short", new Bounds(20, 200, 190, 250),
                List.of(new BubbleLayout.Size(260, 60), new BubbleLayout.Size(140, 90)));
        var narrowResult = BubbleLayout.place(List.of(narrow), new Bounds(0, 24, 360, 760),
                List.of(new Bounds(0, 24, 190, 195), new Bounds(0, 255, 190, 760)), 6, 4);
        check(narrowResult.placements().size() == 1, "narrow measured alternative fits an empty side gutter");
        check(narrowResult.placements().get(0).bounds().width() == 140, "use alternative's measured width");
        // Varied density and text sizes: every shown card remains intact and collision free.
        for (int height = 24; height <= 160; height += 17) {
            var items = new ArrayList<BubbleLayout.Item>();
            for (int i = 0; i < 5; i++) items.add(new BubbleLayout.Item("m" + i,
                    new Bounds(20, 40 + i * 125, 180, 90 + i * 125), 130, height));
            var result = BubbleLayout.place(items, screen, List.of(), 6, 4);
            check(result.placements().size() + result.fallbackIds().size() == items.size(), "no lost result");
            for (var p : result.placements()) {
                check(inside(screen, p.bounds()), "varied layouts stay inside safe bounds");
                check(p.bounds().width() == 130 && p.bounds().height() == height, "never resize measured text");
                for (var source : items) check(!intersects(source.anchor(), p.bounds()), "avoid all source messages");
                for (var other : result.placements()) if (p != other)
                    check(!intersects(p.bounds(), other.bounds()), "translations do not collide");
            }
        }
        System.out.println("BubbleLayoutSelfTest PASS (" + checks + " checks)");
    }
    private static ChatMessage message(String space, List<String> warnings) {
        return new ChatMessage("node", "原文", "NODE_TEXT", 20, 100, 180, 180, space,
                List.of("n:text"), warnings);
    }
    private static boolean inside(Bounds outer, Bounds b) {
        return b.left >= outer.left && b.top >= outer.top && b.right <= outer.right && b.bottom <= outer.bottom;
    }
    private static boolean intersects(Bounds a, Bounds b) {
        return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
