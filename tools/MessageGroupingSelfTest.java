package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

public final class MessageGroupingSelfTest {
    private static int checks;
    public static void main(String[] args) {
        var nodes = List.of(node("list", null, "RecyclerView", 0, 500, false),
                node("row1", "list", "LinearLayout", 20, 180, false),
                node("a", "row1", "TextView", 30, 50, false),
                node("b", "row1", "TextView", 60, 80, false),
                node("row2", "list", "LinearLayout", 200, 350, false),
                node("c", "row2", "TextView", 210, 230, false));
        var a = message("a", "ありがとう～", 30, 50);
        var b = message("b", "またね💗", 60, 80);
        var c = message("c", "ありがとう～", 210, 230);
        var grouped = MessageGrouper.group(List.of(a, b, c), nodes);
        check(grouped.size() == 2, "repeated direct children of RecyclerView identify separate rows");
        check(grouped.get(0).originalText.equals("ありがとう～\nまたね💗"), "keep exact source text");
        check(grouped.get(1).originalText.equals(a.originalText), "same text in another row survives");
        check(MessageGrouper.group(List.of(a, b), nodes.subList(1, nodes.size())).size() == 2,
                "unidentified container cannot establish a message");
        var oneRow = nodes.subList(0, 4);
        check(MessageGrouper.group(List.of(a, b), oneRow).size() == 2,
                "a single unmarked child is not enough evidence");
        var row = node("row1", null, "LinearLayout", 20, 180, true);
        var time = new NodeRecord("time", "row1", 0, 3, 1, "20:00", null,
                "TextView", null, new Bounds(20, 51, 120, 59), null, true, false, false);
        check(MessageGrouper.group(List.of(a, b), List.of(row, nodes.get(2), nodes.get(3), time)).size() == 2,
                "unselected time between text blocks is still a boundary");
        var differentWindow = new NodeRecord("b", "row1", 0, 3, 2, null, null,
                "TextView", null, new Bounds(20, 60, 120, 80), null, true, false, false);
        check(MessageGrouper.group(List.of(a, b), List.of(row, nodes.get(2), differentWindow)).size() == 2,
                "never group across window identity");
        var groups = MessageGrouper.selectionGroups(TextAssembly.fromNodes(List.of(
                row, textNode("a", 30, "ありがとう～"), textNode("b", 60, "またね💗"))),
                List.of(row, textNode("a", 30, "ありがとう～"), textNode("b", 60, "またね💗")));
        check(groups.size() == 1 && groups.get(0).fragmentIds().equals(List.of("a:text", "b:text")),
                "whole-group selection targets the original fragment ids");
        regressionCases();
        System.out.println("MessageGroupingSelfTest PASS (" + checks + " checks)");
    }

    private static void regressionCases() {
        int failures = 0;
        for (Runnable test : List.<Runnable>of(MessageGroupingSelfTest::unknownAuthorBoundary,
                MessageGroupingSelfTest::mediaBoundaries, MessageGroupingSelfTest::nestedLists,
                MessageGroupingSelfTest::unrelatedBarriers, MessageGroupingSelfTest::deselectedLine,
                MessageGroupingSelfTest::nestedContentBoundary)) {
            try { test.run(); }
            catch (AssertionError error) { failures++; System.err.println(error.getMessage()); }
        }
        if (failures > 0) throw new AssertionError(failures + " grouping regression scenarios failed");
    }

    private static void unknownAuthorBoundary() {
        var nodes = twoLines();
        nodes.add(detail("author", "row1", "TextView", "山﨑天", 20, 52, 120, 58, 1, true));
        assertSeparate(nodes, "unknown author must stop both candidate and request grouping");
    }

    private static void mediaBoundaries() {
        for (String cls : List.of("ImageView", "VideoView", "Button")) {
            for (String text : new String[] {"写真", null}) {
                var nodes = twoLines();
                nodes.add(detail("media", "row1", cls, text, 20, 52, 120, 58, 1, false));
                assertSeparate(nodes, "labelled or unlabelled " + cls + " is a boundary");
            }
        }
    }

    private static void nestedLists() {
        var nodes = List.of(node("outer", null, "RecyclerView", 0, 500, false),
                node("section1", "outer", "LinearLayout", 0, 180, false),
                node("inner", "section1", "RecyclerView", 20, 100, false),
                detail("a", "inner", "TextView", "独立 A", 20, 30, 120, 50, 1, false),
                detail("b", "inner", "TextView", "独立 B", 20, 60, 120, 80, 1, false),
                node("section2", "outer", "LinearLayout", 200, 350, false),
                detail("c", "section2", "TextView", "独立 C", 20, 210, 120, 230, 1, false));
        assertSeparate(nodes, "inner list leaves must not fall back to outer sections");
        var explicit = new ArrayList<>(nodes);
        explicit.set(2, node("inner", "section1", "RecyclerView", 20, 100, true));
        assertSeparate(explicit, "a list that is also an outer collection item is still a boundary");
        var known = List.of(node("outer", null, "RecyclerView", 0, 500, false),
                node("section1", "outer", "LinearLayout", 0, 180, true),
                node("inner", "section1", "RecyclerView", 20, 100, false),
                node("row1", "inner", "LinearLayout", 20, 100, true),
                textNode("a", 30, "ありがとう～"), textNode("b", 60, "またね💗"));
        check(MessageGrouper.selectionGroups(TextAssembly.fromNodes(known), known).size() == 1,
                "a proven inner row can still group its own lines");
    }

    private static void unrelatedBarriers() {
        for (NodeRecord time : List.of(
                detail("time", null, "TextView", "12:34", 400, 52, 480, 58, 1, false),
                detail("time", "row1", "TextView", "12:34", 20, 52, 120, 58, 2, false),
                detail("time", "row1", "TextView", "12:34", 140, 52, 170, 58, 1, false))) {
            var nodes = twoLines();
            nodes.add(time);
            var fragments = TextAssembly.fromNodes(nodes);
            check(MessageGrouper.selectionGroups(fragments, nodes).size() == 1,
                    "unrelated column or window timestamp must not split a message");
            var selected = ConfirmedMessageFactory.fromFragments(fragments, java.util.Set.of("a:text", "b:text"));
            check(MessageGrouper.group(selected, nodes).size() == 1, "request uses the same scoped barriers");
        }
    }

    private static void nestedContentBoundary() {
        var nodes = twoLines();
        nodes.add(node("inner", "row1", "RecyclerView", 51, 59, false));
        nodes.add(detail("photo", "inner", "ImageView", null, 20, 52, 120, 58, 1, false));
        assertSeparate(nodes, "outer text must not skip media hidden inside a nested list");
        nodes.remove(nodes.size() - 1);
        assertSeparate(nodes, "an opaque inner list itself marks an outer message boundary");
    }

    private static void deselectedLine() {
        var nodes = twoLines();
        nodes.add(detail("middle", "row1", "TextView", "省いた行", 20, 52, 120, 58, 1, false));
        var fragments = TextAssembly.fromNodes(nodes);
        check(MessageGrouper.selectionGroups(fragments, nodes).get(0).fragmentIds()
                .equals(List.of("a:text", "middle:text", "b:text")), "whole group preserves full reading order");
        var selected = ConfirmedMessageFactory.fromFragments(fragments, java.util.Set.of("a:text", "b:text"));
        check(MessageGrouper.group(selected, nodes).size() == 2, "deselected middle text leaves a group boundary");
    }

    private static ArrayList<NodeRecord> twoLines() {
        return new ArrayList<>(List.of(node("row1", null, "LinearLayout", 20, 180, true),
                textNode("a", 30, "ありがとう～"), textNode("b", 60, "またね💗")));
    }

    private static void assertSeparate(List<NodeRecord> nodes, String reason) {
        var fragments = TextAssembly.fromNodes(nodes);
        var ids = new HashSet<String>();
        for (var fragment : fragments) if (fragment.role == Role.BODY) ids.add(fragment.id);
        check(MessageGrouper.selectionGroups(fragments, nodes).isEmpty(), reason);
        var selected = ConfirmedMessageFactory.fromFragments(fragments, ids);
        check(MessageGrouper.group(selected, nodes).equals(selected), reason + " (request)");
    }

    private static NodeRecord detail(String id, String parent, String cls, String text,
                                     int left, int top, int right, int bottom, int window, boolean clickable) {
        return new NodeRecord(id, parent, 0, top, window, text, null, cls, null,
                new Bounds(left, top, right, bottom), null, true, clickable, false);
    }
    private static NodeRecord textNode(String id, int top, String text) {
        return new NodeRecord(id, "row1", 0, top, 1, text, null, "TextView", null,
                new Bounds(20, top, 120, top + 20), null, true, false, false);
    }
    private static NodeRecord node(String id, String parent, String cls, int top, int bottom, boolean item) {
        return new NodeRecord(id, parent, 0, top, 1, null, null, cls, null,
                new Bounds(0, top, 180, bottom), null, true, false, item);
    }
    private static ChatMessage message(String id, String text, int top, int bottom) {
        return new ChatMessage(id + ":text", text, "NODE_TEXT", 20, top, 120, bottom,
                "SCREEN", List.of(id + ":text"), List.of("MESSAGE_BOUNDARY_UNVERIFIED"));
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
