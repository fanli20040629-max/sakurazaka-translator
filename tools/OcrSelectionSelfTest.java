package com.fanli.sakurazakatranslator.capture;

import java.util.List;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

public final class OcrSelectionSelfTest {
    public static void main(String[] args) {
        OcrSelection selection = new OcrSelection();
        var bottom = TextAssembly.ocr("bottom", "二行目💗", new Bounds(0, 100, 50, 120), 0);
        var top = TextAssembly.ocr("top", "一行目～\n続き", new Bounds(0, 10, 50, 30), 1);
        selection.replace(List.of(bottom, top));
        check(selection.fragments().get(0).id.equals("top"), "candidates use screen order");
        selection.setSelected("bottom", true);
        selection.setSelected("top", true);
        check(selection.selectedText().equals("一行目～\n続き\n二行目💗"), "symbols and order preserved");
        check(selection.request(null).orElseThrow().messages.get(0).id.equals("top"),
                "request order matches displayed text");
        check(!selection.setSelected("old-page", true), "unknown/stale IDs ignored");
        selection.setSelected("top", false);
        check(selection.request(null).orElseThrow().messages.size() == 1, "uncheck updates request");
        selection.clear();
        check(selection.fragments().isEmpty() && selection.selectedText().isEmpty()
                && selection.request(null).isEmpty(), "close clears text and request");
        selection.replace(List.of(top));
        check(selection.request(null).isEmpty(), "new page has no inherited selections");
        var timestamp = TextAssembly.ocr("time", "20:00", null, 0);
        selection.replace(List.of(timestamp));
        selection.setSelected("time", true);
        check(selection.request(null).isPresent(),
                "OCR is manual: do not pretend timestamps are already automatically classified");
        System.out.println("OcrSelectionSelfTest PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
