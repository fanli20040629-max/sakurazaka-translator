package com.fanli.sakurazakatranslator.domain;

import java.util.List;
import static com.fanli.sakurazakatranslator.domain.LongMessageDraft.Outcome;

public final class LongMessageDraftSelfTest {
    private static int checks;

    public static void main(String[] args) {
        selectedPartCases();
        LongMessageDraft draft = new LongMessageDraft();
        PageToken firstPage = page(1, 1, 1);
        ChatMessage first = message("a", "先頭です。ABCDEFGHIJKLMN\n続きます💗", "NODE_TEXT", List.of("a:text"), List.of("FIRST"));
        check(draft.append(firstPage, first) == Outcome.STARTED, "first part starts draft");
        String secondText = "ABCDEFGHIJKLMN\n続きます💗そして終わりです。";
        check(draft.append(page(2, 1, 2), message("b", secondText, "NODE_TEXT",
                List.of("b:text"), List.of("SECOND"))) == Outcome.APPENDED, "exact overlap appends suffix");
        check(draft.text().equals("先頭です。" + secondText), "merge preserves exact text, emoji, and newline");
        check(draft.parts() == 2, "accepted parts counted");
        ChatMessage merged = draft.message();
        check(merged.id.equals("manual-long-message") && merged.coordinateSpace.equals("NONE")
                && merged.left == 0 && merged.top == 0 && merged.right == 0 && merged.bottom == 0,
                "message has stable id and no screen anchors");
        check(merged.warnings.containsAll(List.of("FIRST", "SECOND", "MANUAL_MULTISCREEN",
                "COMPLETENESS_UNVERIFIED")), "message accumulates required warnings");
        check(merged.provenance.equals(List.of("1:1:a:text", "2:2:b:text")),
                "provenance is prefixed by capture identity");
        try { merged.provenance.add("x"); throw new AssertionError("provenance must be immutable"); }
        catch (UnsupportedOperationException expected) { checks++; }

        snapshotRejected(draft, page(3, 1, 3), message("gap", "完全に別の文章です。", "NODE_TEXT",
                List.of("gap:text"), List.of()), Outcome.NO_OVERLAP, "missing gap");
        snapshotRejected(draft, new PageToken(4, "other", 1, 4), message("ctx", secondText, "NODE_TEXT",
                List.of(), List.of()), Outcome.WRONG_CONTEXT, "package mismatch");
        snapshotRejected(draft, page(5, 2, 5), message("ctx", secondText, "NODE_TEXT",
                List.of(), List.of()), Outcome.WRONG_CONTEXT, "window mismatch");
        snapshotRejected(draft, page(6, 1, 6), message("src", secondText, "OCR",
                List.of(), List.of("SYMBOL_UNVERIFIED")), Outcome.WRONG_SOURCE, "source mismatch");
        check(draft.append(page(2, 1, 2), message("b", secondText, "NODE_TEXT",
                List.of("b:text"), List.of())) == Outcome.DUPLICATE, "same capture is duplicate");
        check(draft.append(page(7, 1, 7), message("equal", draft.text(), "NODE_TEXT",
                List.of(), List.of())) == Outcome.DUPLICATE, "exact full duplicate is duplicate");

        LongMessageDraft ambiguous = new LongMessageDraft();
        ambiguous.append(page(10, 1, 1), message("a", "先頭ABCDEFGHIJKL中間ABCDEFGHIJKL", "NODE_TEXT", List.of(), List.of()));
        snapshotRejected(ambiguous, page(11, 1, 2), message("b", "ABCDEFGHIJKL末尾", "NODE_TEXT",
                List.of(), List.of()), Outcome.NO_OVERLAP, "ambiguous repeated overlap");
        LongMessageDraft multipleSeams = new LongMessageDraft();
        multipleSeams.append(page(12, 1, 1), message("a", "先頭ABCDEFGHIJKLABCDEFGHIJKL",
                "NODE_TEXT", List.of(), List.of()));
        snapshotRejected(multipleSeams, page(13, 1, 2), message("b",
                "ABCDEFGHIJKLABCDEFGHIJKL末尾", "NODE_TEXT", List.of(), List.of()),
                Outcome.NO_OVERLAP, "multiple qualifying suffix-prefix lengths are ambiguous");

        LongMessageDraft sameEpoch = new LongMessageDraft();
        sameEpoch.append(page(14, 1, 7), message("a", "先頭ABCDEFGHIJKL", "NODE_TEXT", List.of(), List.of()));
        check(sameEpoch.append(page(15, 1, 7), message("b", "ABCDEFGHIJKL末尾", "NODE_TEXT",
                List.of(), List.of())) == Outcome.APPENDED, "different captures may retain the same page epoch");

        LongMessageDraft contained = new LongMessageDraft();
        contained.append(page(20, 1, 1), message("a", "これは十分に長い完全な本文です。さらに続きます。", "NODE_TEXT", List.of(), List.of()));
        snapshotRejected(contained, page(21, 1, 2), message("b", "十分に長い完全な本文です", "NODE_TEXT",
                List.of(), List.of()), Outcome.NO_OVERLAP, "contained text from another capture is not inferred duplicate");

        LongMessageDraft tooLarge = new LongMessageDraft();
        check(tooLarge.append(firstPage, message("large", "x".repeat(6001), "NODE_TEXT", List.of(), List.of()))
                == Outcome.LIMIT && tooLarge.isEmpty(), "oversized first part rejected atomically");
        LongMessageDraft boundary = new LongMessageDraft();
        String base = "x".repeat(5987) + "ABCDEFGHIJKL";
        check(boundary.append(firstPage, message("base", base, "NODE_TEXT", List.of(), List.of())) == Outcome.STARTED,
                "6000 character boundary setup accepted");
        snapshotRejected(boundary, page(2, 1, 2), message("extra", "ABCDEFGHIJKL💗", "NODE_TEXT",
                List.of(), List.of()), Outcome.LIMIT, "surrogate pair cannot cross UTF-16 cap");
        snapshotRejected(boundary, page(3, 1, 3), message("huge", "z".repeat(6001),
                "NODE_TEXT", List.of(), List.of()), Outcome.LIMIT,
                "oversized subsequent part rejects before overlap processing");

        LongMessageDraft maxParts = new LongMessageDraft();
        String current = "prefix-ABCDEFGHIJKL";
        maxParts.append(page(100, 1, 1), message("0", current, "NODE_TEXT", List.of(), List.of()));
        for (int i = 1; i < 12; i++) {
            String overlap = current.substring(current.length() - 12);
            current = overlap + (char) ('a' + i);
            check(maxParts.append(page(100 + i, 1, i + 1), message("p" + i, current,
                    "NODE_TEXT", List.of(), List.of())) == Outcome.APPENDED, "part " + (i + 1) + " accepted");
        }
        snapshotRejected(maxParts, page(112, 1, 13), message("limit", current.substring(current.length() - 12) + "z",
                "NODE_TEXT", List.of(), List.of()), Outcome.LIMIT, "thirteenth distinct part rejected");

        LongMessageDraft empty = new LongMessageDraft();
        check(empty.append(null, first) == Outcome.EMPTY, "null page rejected");
        check(empty.append(firstPage, null) == Outcome.EMPTY, "null message rejected");
        try { empty.message(); throw new AssertionError("empty message must reject"); }
        catch (IllegalStateException expected) { checks++; }
        draft.clear();
        check(draft.isEmpty() && draft.parts() == 0 && draft.text().isEmpty(), "clear resets draft");
        check(draft.append(new PageToken(30, "pkg", 1, 99), message("reuse", "再利用できる本文です。", "OCR",
                List.of("ocr:1"), List.of("SYMBOL_UNVERIFIED"))) == Outcome.STARTED,
                "cleared draft is reusable");
        check(draft.message().warnings.contains("SYMBOL_UNVERIFIED"), "OCR source marker is preserved");
        System.out.println("LongMessageDraftSelfTest PASS (" + checks + " checks)");
    }

    private static void selectedPartCases() {
        ChatMessage single = messageAt("one", "一枚", "OCR", "IMAGE", 10, 30,
                List.of("ocr:one"), List.of("SYMBOL_UNVERIFIED"));
        check(LongMessageDraft.selectedPart(List.of(single)) == single, "single selection is returned unchanged");
        rejectSelected(null, "null selection rejects");
        rejectSelected(List.of(), "empty selection rejects");
        rejectSelected(java.util.Collections.singletonList(null), "null singleton rejects");
        ChatMessage second = messageAt("two", "二枚💗", "OCR", "IMAGE", 31, 60,
                List.of("ocr:two"), List.of("SECOND"));
        ChatMessage combined = LongMessageDraft.selectedPart(List.of(single, second));
        check(combined.originalText.equals("一枚\n二枚💗"), "OCR parts preserve selected order and separators");
        check(combined.source.equals("OCR") && combined.coordinateSpace.equals("NONE")
                && combined.left == 0 && combined.top == 0 && combined.right == 0 && combined.bottom == 0,
                "manual OCR part removes screen anchors");
        check(combined.provenance.equals(List.of("ocr:one", "ocr:two")), "OCR provenance is accumulated");
        check(combined.warnings.containsAll(List.of("SYMBOL_UNVERIFIED", "SECOND", "MANUAL_OCR_PART")),
                "OCR warnings and manual marker are accumulated");
        rejectSelected(List.of(single, messageAt("node", "節", "NODE_TEXT", "IMAGE", 31, 60,
                List.of(), List.of())), "mixed or node sources reject");
        rejectSelected(List.of(single, messageAt("screen", "節", "OCR", "SCREEN", 31, 60,
                List.of(), List.of())), "non-image coordinate rejects");
        rejectSelected(List.of(second, single), "non-monotonic order rejects");
        rejectSelected(List.of(single, messageAt("overlap", "節", "OCR", "IMAGE", 29, 60,
                List.of(), List.of())), "overlapping bounds reject");
        rejectSelected(List.of(single, messageAt("unknown", "節", "OCR", "IMAGE", 0, 0,
                List.of(), List.of())), "unknown bounds reject");
        rejectSelected(List.of(single, messageAt("manual", "節", "OCR", "IMAGE", 31, 60,
                List.of(), List.of("MANUAL_ROLE_OVERRIDE"))), "manual role overrides reject");
        rejectSelected(List.of(single, messageAt("cross", "節", "OCR", "IMAGE", 31, 60,
                List.of(), List.of("CROSS_SOURCE_UNVERIFIED"))), "unverified cross-source provenance rejects");
    }

    private static void rejectSelected(List<ChatMessage> selected, String reason) {
        try { LongMessageDraft.selectedPart(selected); throw new AssertionError(reason); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static PageToken page(long request, int window, long epoch) {
        return new PageToken(request, "pkg", window, epoch);
    }

    private static ChatMessage message(String id, String text, String source,
                                       List<String> provenance, List<String> warnings) {
        return new ChatMessage(id, text, source, 1, 2, 3, 4, "SCREEN", provenance, warnings);
    }

    private static ChatMessage messageAt(String id, String text, String source, String coordinates,
                                         int top, int bottom, List<String> provenance, List<String> warnings) {
        return new ChatMessage(id, text, source, 10, top, 100, bottom, coordinates, provenance, warnings);
    }

    private static void snapshotRejected(LongMessageDraft draft, PageToken page, ChatMessage message,
                                         Outcome expected, String reason) {
        String text = draft.text();
        int parts = draft.parts();
        check(draft.append(page, message) == expected, reason + " returns " + expected);
        check(draft.text().equals(text) && draft.parts() == parts, reason + " leaves draft unchanged");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
