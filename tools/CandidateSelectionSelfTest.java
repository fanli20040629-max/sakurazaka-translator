package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.PageToken;
import java.util.List;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Synthetic data only. Exercises the same selection used by the Android card. */
public final class CandidateSelectionSelfTest {
    private static final PageToken PAGE = new PageToken(1, "test.app", 7, 10);
    private static final PageToken NEXT = new PageToken(2, "test.app", 7, 11);
    private static int checks;

    public static void main(String[] args) {
        unicodeAndEvidence();
        arrivalAndPageIdentity();
        duplicateAndOrdering();
        explicitRoleOverride();
        sourceWarningsAndTruncation();
        rejectMalformedIdentity();
        System.out.println("CandidateSelectionSelfTest PASS (" + checks + " checks)");
    }

    private static void unicodeAndEvidence() {
        String raw = "  ありがとう～💗\n👩🏽‍💻 ❤️ 🇯🇵 1️⃣ 米9  ";
        var node = node("n1", raw, new Bounds(20, 80, 250, 160), Role.BODY);
        var state = new CandidateSelection();
        state.open(PAGE, List.of(node));
        check(state.request(PAGE, null).isEmpty(), "no automatic selection");
        check(state.setSelected(PAGE, "n1", true), "node can be selected");
        var request = state.request(PAGE, null).orElseThrow();
        var item = request.messages.get(0);
        check(item.originalText.equals(raw), "exact Unicode, whitespace and line breaks survive");
        check(item.left == 20 && item.top == 80 && item.right == 250 && item.bottom == 160,
                "all four bounds survive confirmation");
        check(item.coordinateSpace.equals("SCREEN"), "node coordinates retain their space");
        check(item.provenance.equals(List.of("n1:description")), "source evidence survives");
        check(item.warnings.contains("NODE_DESCRIPTION_UNVERIFIED"), "risk survives");
        check(request.pageToken.equals(PAGE), "request binds to capture identity");
        check(state.selectedText().equals(raw), "preview matches request without normalization");
    }

    private static void arrivalAndPageIdentity() {
        var state = new CandidateSelection();
        var node = node("n1", "原文💗", new Bounds(10, 100, 100, 150), Role.BODY);
        var ocr = TextAssembly.ocr("ocr:1", "原文9", new Bounds(0, 0, 90, 50), 0);
        state.open(PAGE, List.of(node));
        state.setSelected(PAGE, "n1", true);
        check(state.appendOcr(PAGE, List.of(ocr)), "first OCR completion accepted");
        check(state.request(PAGE, null).orElseThrow().messages.size() == 1,
                "OCR does not select itself or clear selected nodes");
        check(state.selectedText().equals("原文💗"), "OCR cannot overwrite node symbols");
        check(!state.appendOcr(PAGE, List.of(ocr)), "duplicate OCR completion rejected");
        state.setSelected(PAGE, "ocr:1", true);
        var items = state.request(PAGE, null).orElseThrow().messages;
        check(items.get(1).coordinateSpace.equals("IMAGE"), "OCR stays in image coordinates");
        check(items.get(1).warnings.contains("SYMBOL_UNVERIFIED"), "OCR warns about symbol loss");
        check(items.get(0).warnings.contains("CROSS_SOURCE_UNVERIFIED"),
                "mixed selection carries unmatched-source warning");
        state.open(NEXT, List.of(node));
        check(!state.setSelected(PAGE, "n1", true), "old click cannot select reused ID on new page");
        check(!state.appendOcr(PAGE, List.of(ocr)), "late OCR rejected across pages");
        check(state.request(PAGE, null).isEmpty(), "old token cannot obtain a new request");
        check(state.request(NEXT, null).isEmpty(), "new capture has no inherited selection");
        state.clear();
        check(!state.appendOcr(NEXT, List.of(ocr)), "close cannot be undone by late OCR");
        check(!state.setSelected(NEXT, "n1", true), "closed selection refuses changes");
        check(state.fragments().isEmpty() && state.selectedText().isEmpty(), "close drops text");
    }

    private static void duplicateAndOrdering() {
        var state = new CandidateSelection();
        var top = node("n1", "同じ💗", new Bounds(0, 10, 80, 40), Role.BODY);
        var bottom = node("n2", "同じ💗", new Bounds(0, 90, 80, 120), Role.BODY);
        state.open(PAGE, List.of(bottom, top));
        state.setSelected(PAGE, "n2", true);
        state.setSelected(PAGE, "n1", true);
        var request = state.request(PAGE, null).orElseThrow();
        check(request.messages.size() == 2, "same text at different locations remains distinct");
        check(request.messages.get(0).id.equals("n1"), "selection order is reading order");
        var collision = TextAssembly.ocr("n1", "collision", null, 0);
        check(!state.appendOcr(PAGE, List.of(collision)), "ID collision is rejected atomically");
        check(state.selectedText().equals("同じ💗\n同じ💗"), "rejection leaves selections intact");
        check(state.appendOcr(PAGE, List.of(TextAssembly.ocr("ocr:1", "  ", null, 0))),
                "empty OCR completion is valid");
        check(state.fragments().size() == 2, "blank OCR never reaches confirmation");
        state.setSelected(PAGE, "n1", false);
        state.setSelected(PAGE, "n2", false);
        check(state.request(PAGE, null).isEmpty(), "unselect all clears request");
    }

    private static void explicitRoleOverride() {
        var state = new CandidateSelection();
        var time = node("time", "20:00", null, Role.METADATA);
        state.open(PAGE, List.of(time));
        check(state.request(PAGE, null).isEmpty(), "metadata excluded by default");
        state.setSelected(PAGE, "time", true);
        var item = state.request(PAGE, null).orElseThrow().messages.get(0);
        check(item.originalText.equals("20:00"), "user can recover a misclassified real message");
        check(item.warnings.contains("MANUAL_ROLE_OVERRIDE"), "override remains visible downstream");
    }

    private static void sourceWarningsAndTruncation() {
        Bounds bounds = new Bounds(0, 0, 100, 40);
        var node = new NodeRecord("n", null, 0, 0, 7, "ありがとう💗", "読み上げ説明",
                "android.widget.TextView", null, bounds, bounds, true, false, false);
        var candidates = TextAssembly.fromNodes(List.of(node));
        check(candidates.size() == 2, "conflicting node fields remain separate");
        check(candidates.stream().allMatch(f -> f.warnings.contains("NODE_FIELDS_DIFFER")),
                "conflict is attached to both sources");
        var limited = candidates.get(0).withWarning("NODE_TRAVERSAL_TRUNCATED");
        var state = new CandidateSelection();
        state.open(PAGE, List.of(limited));
        state.setSelected(PAGE, limited.id, true);
        check(state.request(PAGE, null).orElseThrow().messages.get(0).warnings
                .contains("NODE_TRAVERSAL_TRUNCATED"), "truncation follows the request");
        check(limited.rawText.equals(candidates.get(0).rawText), "warning does not alter raw text");
        check(!candidates.get(0).warnings.contains("NODE_TRAVERSAL_TRUNCATED"),
                "annotating a snapshot never mutates the original");
    }

    private static void rejectMalformedIdentity() {
        boolean rejected = false;
        try { new PageToken(0, "test.app", 7, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "missing capture identity rejected");
        var state = new CandidateSelection();
        state.open(PAGE, List.of(node("n", "本文", null, Role.BODY)));
        check(!state.setSelected(new PageToken(1, "other.app", 7, 10), "n", true),
                "same numeric IDs cannot authorize another package");
        check(!state.setSelected(new PageToken(1, "test.app", 8, 10), "n", true),
                "same package cannot authorize another window");
    }

    private static TextFragment node(String id, String raw, Bounds bounds, Role role) {
        return new TextFragment(id, raw, raw, Source.NODE_DESCRIPTION, role, null, bounds,
                List.of(id + ":description"), List.of("NODE_DESCRIPTION_UNVERIFIED"), "test", false);
    }

    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
}
