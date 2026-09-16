package com.fanli.sakurazakatranslator.capture;

import java.util.List;
import java.util.Set;

/** Formats explicitly selected OCR candidates without changing their order or text. */
public final class OcrSelectionFormatter {
    private OcrSelectionFormatter() { }

    public static String format(List<ProbeModels.TextFragment> orderedFragments,
                                Set<String> selectedIds) {
        if (orderedFragments == null || selectedIds == null) return "";
        StringBuilder result = new StringBuilder();
        for (ProbeModels.TextFragment fragment : orderedFragments) {
            if (!selectedIds.contains(fragment.id)) continue;
            if (fragment.rawText == null || fragment.rawText.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(fragment.rawText);
        }
        return result.toString();
    }

}
