package com.fanli.sakurazakatranslator.capture;

import java.util.List;
import java.util.Set;

/** Formats selected candidates in supplied source/reading order, without modifying raw text. */
public final class CandidateTextFormatter {
    private CandidateTextFormatter() { }

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
