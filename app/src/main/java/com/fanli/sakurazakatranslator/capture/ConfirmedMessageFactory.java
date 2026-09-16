package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Bridges selected text fragments into request items; does not infer chat bubble boundaries. */
public final class ConfirmedMessageFactory {
    private ConfirmedMessageFactory() { }

    public static List<ChatMessage> fromFragments(List<ProbeModels.TextFragment> fragments,
                                                  Set<String> selectedIds) {
        if (fragments == null || selectedIds == null || selectedIds.isEmpty()) return List.of();
        List<ChatMessage> messages = new ArrayList<>();
        for (ProbeModels.TextFragment fragment : TextAssembly.screenOrder(fragments)) {
            if (!selectedIds.contains(fragment.id) || !isTranslatable(fragment.role)) continue;
            if (fragment.rawText == null || fragment.rawText.isBlank()) continue;
            int top = fragment.bounds == null ? Integer.MAX_VALUE : fragment.bounds.top;
            int left = fragment.bounds == null ? Integer.MAX_VALUE : fragment.bounds.left;
            messages.add(new ChatMessage(fragment.id, fragment.rawText,
                    fragment.source.name(), top, left));
        }
        return List.copyOf(messages);
    }

    private static boolean isTranslatable(ProbeModels.Role role) {
        return role == ProbeModels.Role.BODY
                || role == ProbeModels.Role.MEDIA_CANDIDATE
                || role == ProbeModels.Role.UNKNOWN;
    }
}
