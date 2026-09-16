package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Reproduces problems found in the 2026-09-16 source review. No device is required. */
public final class ReviewRegressionSelfTest {
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        run("node order uses traversal index, not ID spelling", () -> {
            Bounds bounds = new Bounds(0, 0, 100, 30);
            NodeRecord tenth = node("n10", 9, "ten", bounds);
            NodeRecord second = node("n2", 1, "two", bounds);
            check(TextAssembly.fromNodes(List.of(tenth, second)).get(0).rawText.equals("two"));
        });
        run("confirmed fragments keep tie order", () -> {
            Bounds bounds = new Bounds(0, 0, 100, 30);
            var first = TextAssembly.ocr("ocr:2:1", "two", bounds, 0);
            var second = TextAssembly.ocr("ocr:10:1", "ten", bounds, 1);
            var selected = ConfirmedMessageFactory.fromFragments(
                    List.of(first, second), Set.of(first.id, second.id));
            check(selected.get(0).id.equals(first.id));
        });
        run("blank selected OCR cannot crash confirmation", () -> {
            var blank = TextAssembly.ocr("blank", "  ", null, 0);
            check(ConfirmedMessageFactory.fromFragments(List.of(blank), Set.of("blank")).isEmpty());
        });
        run("finished capture cannot restart OCR", () -> {
            CaptureCoordinator coordinator = new CaptureCoordinator();
            long id = coordinator.begin();
            coordinator.finishPhysical(id);
            check(!coordinator.advance(id, CaptureCoordinator.Physical.OCR));
            check(!coordinator.isBusy());
        });
        run("capture cannot skip screenshot phase", () -> {
            CaptureCoordinator coordinator = new CaptureCoordinator();
            long id = coordinator.begin();
            check(!coordinator.advance(id, CaptureCoordinator.Physical.OCR));
        });
        run("blank translation is rejected", () -> {
            var request = TranslationRequestFactory.confirmed(
                    List.of(new ChatMessage("m", "こんにちは", "node", 0, 0)), null);
            check(!TranslationValidator.validate(request,
                    new TranslationResult(List.of("  "), List.of(), true)).accepted);
        });
        run("duplicate message IDs are rejected", () -> {
            var message = new ChatMessage("same", "こんにちは", "node", 0, 0);
            boolean rejected = false;
            try {
                TranslationRequestFactory.confirmed(List.of(message, message), null);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected);
        });
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("ReviewRegressionSelfTest PASS (7 cases)");
    }

    private static NodeRecord node(String id, int index, String text, Bounds bounds) {
        return new NodeRecord(id, null, index, index, 7, text, null,
                "android.widget.TextView", null, bounds, bounds, true, false, false);
    }

    private static void run(String name, Runnable test) {
        try { test.run(); }
        catch (RuntimeException | AssertionError error) {
            failures.add(name + ": " + error.getClass().getSimpleName());
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("unexpected result");
    }
}
