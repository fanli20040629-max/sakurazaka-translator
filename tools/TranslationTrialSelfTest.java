package com.fanli.sakurazakatranslator.domain;

import com.fanli.sakurazakatranslator.capture.MessageGrouper;
import com.fanli.sakurazakatranslator.capture.ProbeModels.*;
import com.fanli.sakurazakatranslator.translation.SymbolProtector;
import java.util.List;

public final class TranslationTrialSelfTest {
    private static int checks;

    public static void main(String[] args) {
        String original = "ありがとう～💗\n👩🏽‍💻 ❤️ 🇯🇵 1️⃣ 米9";
        var protectedText = SymbolProtector.protect(original);
        check(protectedText.restore(protectedText.text().replace("ありがとう", "谢谢"))
                .equals(original.replace("ありがとう", "谢谢")), "Unicode round trip");
        reject(() -> protectedText.restore("谢谢"), "missing symbols rejected");
        reject(() -> protectedText.restore(protectedText.text() + protectedText.text()), "duplicates rejected");
        reject(() -> protectedText.restore(protectedText.text() + "💗"), "added emoji rejected");
        var collision = SymbolProtector.protect("__S46_0__～");
        check(collision.restore(collision.text()).equals("__S46_0__～"), "literal token preserved");
        check(SymbolProtector.protect("米9").restore("米9").equals("米9"), "no guessed correction");
        var lines = SymbolProtector.protect("a\r\nb\nc");
        check(lines.restore(lines.text()).equals("a\r\nb\nc"), "line ending identity");
        check(!SymbolProtector.sameSymbols("♡～", "～♡"), "symbol order matters");

        var a = message("n1:text", "こんにちは～", 20, List.of("n1:text"));
        var b = message("n2:text", "ありがとう💗", 50, List.of("n2:text"));
        var parent = node("row", null, 0, true);
        var nodes = List.of(parent, node("n1", "row", 20, false), node("n2", "row", 50, false));
        var grouped = MessageGrouper.group(List.of(a, b), nodes);
        check(grouped.size() == 1, "same collection item proposes one group");
        check(grouped.get(0).originalText.equals("こんにちは～\nありがとう💗"), "group order and line break");
        check(grouped.get(0).warnings.contains("GROUPING_SUGGESTED"), "group not claimed verified");
        check(grouped.get(0).provenance.size() == 2, "all sources retained");
        check(MessageGrouper.group(List.of(a, b), List.of(parent, node("row2", null, 0, true),
                node("n1", "row", 20, false), node("n2", "row2", 50, false))).size() == 2,
                "different message rows not merged");
        check(MessageGrouper.group(List.of(a, b), List.of()).size() == 2, "no evidence no grouping");
        check(MessageGrouper.group(List.of(a, b), List.of(node("n1", "n2", 20, false),
                node("n2", "n1", 50, false))).size() == 2, "cyclic parents safe");
        check(MessageGrouper.group(List.of(a, message("n2:text", "こんにちは～", 20,
                List.of("n2:text"))), nodes).size() == 2, "overlapping nodes not merged");
        var ocr = new ChatMessage("ocr", "ありがとう💗", "OCR", 0, 50, 100, 70,
                "IMAGE", List.of("ocr"), List.of());
        check(MessageGrouper.group(List.of(a, ocr), nodes).size() == 2, "no OCR node fusion");
        var description = new ChatMessage(b.id, b.originalText, "NODE_DESCRIPTION",
                b.left, b.top, b.right, b.bottom, b.coordinateSpace, b.provenance, b.warnings);
        check(MessageGrouper.group(List.of(a, description), nodes).size() == 2,
                "different node field sources not merged");

        var request = new TranslationRequest(new PageToken(1, "test.app", 1, 1),
                List.of(a, b), new StyleProfile("one", "测试", ""), true);
        var reversed = new TranslationResult(List.of(new TranslationResult.Item(b.id, "谢谢💗"),
                new TranslationResult.Item(a.id, "你好～")), List.of(), true);
        var valid = TranslationValidator.validate(request, reversed);
        check(valid.accepted && valid.translations.get(0).id().equals(a.id), "reorder by message id");
        check(!TranslationValidator.validate(request, new TranslationResult(List.of(
                new TranslationResult.Item(a.id, "你好～"), new TranslationResult.Item(a.id, "谢谢💗")),
                List.of(), true)).accepted, "duplicate ids rejected");
        check(!TranslationValidator.validate(request, new TranslationResult(List.of(
                new TranslationResult.Item(a.id, "你好"), new TranslationResult.Item(b.id, "谢谢💗")),
                List.of(), true)).accepted, "missing wave rejected");
        check(!TranslationValidator.validate(request, new TranslationResult(List.of(
                new TranslationResult.Item("wrong", "你好～"), new TranslationResult.Item(b.id, "谢谢💗")),
                List.of(), true)).accepted, "unknown id rejected");
        System.out.println("TranslationTrialSelfTest PASS (" + checks + " checks)");
    }

    private static ChatMessage message(String id, String text, int top, List<String> provenance) {
        return new ChatMessage(id, text, "NODE_TEXT", 0, top, 100, top + 20, "SCREEN",
                provenance, List.of("MESSAGE_BOUNDARY_UNVERIFIED"));
    }
    private static NodeRecord node(String id, String parent, int top, boolean item) {
        return new NodeRecord(id, parent, 0, 0, 1, null, null, "TextView", null,
                new Bounds(0, top, 100, item ? 200 : top + 20),
                new Bounds(0, top, 100, item ? 200 : top + 20), true, false, item);
    }
    private static void reject(Runnable operation, String message) {
        try { operation.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
