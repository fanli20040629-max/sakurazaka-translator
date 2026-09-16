package com.fanli.sakurazakatranslator.capture;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class OcrSelectionFormatterSelfTest {
    public static void main(String[] args) {
        var fragments = List.of(
                TextAssembly.ocr("ocr:1", "第一行～", null, 0),
                TextAssembly.ocr("ocr:2", "第二行💗", null, 1),
                TextAssembly.ocr("ocr:3", "第三行", null, 2));
        Set<String> selected = new LinkedHashSet<>(List.of("ocr:3", "ocr:1"));
        String result = OcrSelectionFormatter.format(fragments, selected);
        check("第一行～\n第三行".equals(result), "selection must follow OCR screen order");
        check("".equals(OcrSelectionFormatter.format(null, selected)),
                "null inputs must produce empty output");
        System.out.println("OcrSelectionFormatterSelfTest PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
