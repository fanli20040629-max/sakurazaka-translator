package com.fanli.sakurazakatranslator.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Converts explicitly selected candidates into stable, top-to-bottom messages. */
public final class MessageSelection {
    private MessageSelection() { }

    public static List<ChatMessage> confirmed(List<ChatMessage> candidates,
                                              List<String> selectedIds) {
        if (candidates == null || selectedIds == null || selectedIds.isEmpty()) return List.of();
        List<ChatMessage> result = new ArrayList<>();
        var selection = new HashSet<>(selectedIds);
        for (ChatMessage candidate : candidates) {
            if (selection.contains(candidate.id)) result.add(candidate);
        }
        result.sort(Comparator.comparingInt((ChatMessage m) -> m.top)
                .thenComparingInt(m -> m.left));
        return List.copyOf(result);
    }
}
