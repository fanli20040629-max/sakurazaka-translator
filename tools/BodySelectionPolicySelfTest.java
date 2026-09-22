package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.List;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

public final class BodySelectionPolicySelfTest {
    private static int checks;

    public static void main(String[] args) {
        NodeRecord list = node("list", null, "RecyclerView", 0, 500, false, true, false, false);
        NodeRecord row = node("row", "list", "LinearLayout", 20, 180, true, false, false, false);
        NodeRecord body = textNode("body", "row", "今日は楽しかったね", 40, 100);
        TextFragment valid = fragment("body:text", "今日は楽しかったね", Source.NODE_TEXT,
                Role.BODY, "body:text", List.of());
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(list, row, body))
                .equals(List.of("body:text")), "body text structurally inside a list row is recommended");
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(row, body))
                .equals(List.of("body:text")), "explicit synthetic collection item is sufficient without list ancestor");

        NodeRecord virtual = new NodeRecord("virtual", "row", 0, 2, 1, null, null,
                null, null, null, null, false, false, false);
        NodeRecord nestedBody = textNode("body", "virtual", "今日は楽しかったね", 40, 100);
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(row, virtual, nestedBody))
                .equals(List.of("body:text")), "virtual intermediary may lack visibility and geometry");

        check(BodySelectionPolicy.recommended(List.of(
                fragment("title:text", "お知らせ。", Source.NODE_TEXT, Role.BODY, "title:text", List.of())),
                List.of(node("title", null, "TextView", 0, 30, false, false, false, false))).isEmpty(),
                "toolbar or title outside a row is excluded");
        check(BodySelectionPolicy.recommended(List.of(
                fragment("name:text", "山崎天", Source.NODE_TEXT, Role.BODY, "name:text", List.of())),
                List.of(list, row, node("name", "row", "TextView", 40, 60, false, false, false, false))).isEmpty(),
                "bare Japanese or Chinese name is excluded");
        check(BodySelectionPolicy.recommended(List.of(
                fragment("ocr", "今日は楽しかったね", Source.OCR, Role.BODY, "ocr", List.of())),
                List.of(list, row, body)).isEmpty(), "OCR is excluded");
        check(BodySelectionPolicy.recommended(List.of(
                fragment("body:text", "今日は楽しかったね", Source.NODE_TEXT, Role.BODY, "body:text",
                        List.of("NODE_TRAVERSAL_TRUNCATED"))), List.of(list, row, body)).isEmpty(),
                "traversal truncation is excluded");
        check(BodySelectionPolicy.recommended(List.of(
                fragment("body:text", "今日は楽しかったね", Source.NODE_TEXT, Role.BODY, "body:text",
                        List.of("NODE_FIELDS_DIFFER"))), List.of(list, row, body)).isEmpty(),
                "ambiguous fields are excluded");
        check(BodySelectionPolicy.recommended(List.of(
                fragment("body:text", "今日は楽しかったね", Source.NODE_TEXT, Role.BODY, "body:text",
                        List.of("POSSIBLY_CLIPPED"))), List.of(list, row, body)).isEmpty(),
                "possibly clipped text remains a manual selection fallback");
        check(BodySelectionPolicy.recommended(List.of(
                fragment("body:description", "今日は楽しかったね", Source.NODE_DESCRIPTION,
                        Role.BODY, "body:description", List.of())), List.of(list, row, body)).isEmpty(),
                "descriptions remain manual fallback");
        check(BodySelectionPolicy.recommended(List.of(
                fragment("short:text", "またね！", Source.NODE_TEXT, Role.BODY, "short:text", List.of())),
                List.of(list, row, textNode("short", "row", "またね！", 40, 80)))
                .equals(List.of("short:text")), "short Japanese sentence inside row is recommended");
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(list, row,
                textNode("body", "row", "今日は楽しかったね", 40, 100, true, false))).isEmpty(),
                "clickable controls are excluded");
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(list, row,
                textNode("body", "row", "今日は楽しかったね", 40, 100, false, true))).isEmpty(),
                "invisible text is excluded");
        NodeRecord media = new NodeRecord("body", "row", 0, 3, 1, "今日は楽しかったね", null,
                "ImageView", null, new Bounds(20, 40, 160, 100), null, true, false, false);
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(row, media)).isEmpty(),
                "media nodes are excluded even when labelled like body text");
        NodeRecord equalFields = new NodeRecord("equal", "row", 0, 4, 1, "同じ本文です。", "同じ本文です。",
                "TextView", null, new Bounds(20, 40, 160, 80), null, true, false, false);
        TextFragment equalFragment = new TextFragment("equal:text", "同じ本文です。", "同じ本文です。",
                Source.NODE_TEXT, Role.BODY, null, new Bounds(20, 40, 160, 80),
                List.of("equal:text", "equal:description"), List.of(), "test", false);
        check(BodySelectionPolicy.recommended(List.of(equalFragment), List.of(row, equalFields))
                .equals(List.of("equal:text")), "equal text and description provenance is safe evidence");
        TextFragment wrongText = fragment("body:text", "改変された本文です。", Source.NODE_TEXT,
                Role.BODY, "body:text", List.of());
        check(BodySelectionPolicy.recommended(List.of(wrongText), List.of(row, body)).isEmpty(),
                "fragment text must equal actual node text");
        TextFragment outside = new TextFragment("body:text", "今日は楽しかったね", "今日は楽しかったね",
                Source.NODE_TEXT, Role.BODY, null, new Bounds(20, 40, 190, 80),
                List.of("body:text"), List.of(), "test", false);
        check(BodySelectionPolicy.recommended(List.of(outside), List.of(row, body)).isEmpty(),
                "fragment and node geometry must remain inside the row");
        NodeRecord nestedRow = node("nested-row", "row", "LinearLayout", 30, 120, true, false, false, false);
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(row, nestedRow,
                textNode("body", "nested-row", "今日は楽しかったね", 40, 100))).isEmpty(),
                "multiple possible collection rows are ambiguous");
        NodeRecord collectionBoundary = node("inner-list", "row", "RecyclerView", 30, 120,
                false, true, false, false);
        check(BodySelectionPolicy.recommended(List.of(valid), List.of(row, collectionBoundary,
                textNode("body", "inner-list", "今日は楽しかったね", 40, 100))).isEmpty(),
                "collection boundary before a row prevents outer-row ownership");
        NodeRecord cycleA = node("a", "b", "LinearLayout", 20, 180, false, false, false, false);
        NodeRecord cycleB = node("b", "a", "LinearLayout", 20, 180, false, false, false, false);
        check(BodySelectionPolicy.recommended(List.of(
                fragment("a:text", "これは本文です。", Source.NODE_TEXT, Role.BODY, "a:text", List.of())),
                List.of(cycleA, cycleB)).isEmpty(), "ancestor cycles fail closed");
        try {
            BodySelectionPolicy.recommended(List.of(valid), List.of(list, row, body)).add("x");
            throw new AssertionError("recommendations must be immutable");
        } catch (UnsupportedOperationException expected) { checks++; }

        Bounds viewport = new Bounds(0, 0, 200, 400);
        check(BodySelectionPolicy.possiblyClipped(message("IMAGE", 10, 100, List.of()), viewport),
                "images are possibly clipped");
        check(BodySelectionPolicy.possiblyClipped(message("OCR", 10, 100, List.of()), viewport),
                "OCR is possibly clipped");
        check(BodySelectionPolicy.possiblyClipped(new ChatMessage("x", "本文", "NODE_TEXT", 0, 0, 0, 0,
                "NONE", List.of(), List.of()), viewport), "unknown geometry is possibly clipped");
        check(BodySelectionPolicy.possiblyClipped(message("NODE_TEXT", 2, 100, List.of()), viewport),
                "top edge tolerance is possibly clipped");
        check(BodySelectionPolicy.possiblyClipped(message("NODE_TEXT", 20, 398, List.of()), viewport),
                "bottom edge tolerance is possibly clipped");
        check(BodySelectionPolicy.possiblyClipped(message("NODE_TEXT", 20, 100,
                List.of("NODE_TRAVERSAL_TRUNCATED")), viewport), "truncation is possibly clipped");
        check(!BodySelectionPolicy.possiblyClipped(message("NODE_TEXT", 20, 100, List.of()), viewport),
                "well-inside known geometry is not flagged");
        System.out.println("BodySelectionPolicySelfTest PASS (" + checks + " checks)");
    }

    private static NodeRecord node(String id, String parent, String cls, int top, int bottom,
                                   boolean item, boolean collection, boolean clickable, boolean invisible) {
        return new NodeRecord(id, parent, 0, top, 1, null, null, cls, null,
                new Bounds(0, top, 180, bottom), null, !invisible, clickable, item, collection);
    }

    private static NodeRecord textNode(String id, String parent, String text, int top, int bottom) {
        return textNode(id, parent, text, top, bottom, false, false);
    }

    private static NodeRecord textNode(String id, String parent, String text, int top, int bottom,
                                       boolean clickable, boolean invisible) {
        return new NodeRecord(id, parent, 0, top, 1, text, null, "TextView", null,
                new Bounds(20, top, 160, bottom), null, !invisible, clickable, false);
    }

    private static TextFragment fragment(String id, String text, Source source, Role role,
                                         String provenance, List<String> warnings) {
        return new TextFragment(id, text, text, source, role, null, new Bounds(20, 40, 160, 80),
                List.of(provenance), warnings, "test", false);
    }

    private static ChatMessage message(String source, int top, int bottom, List<String> warnings) {
        return new ChatMessage("m", "本文", source, 20, top, 180, bottom, "SCREEN",
                List.of("n:text"), warnings);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
