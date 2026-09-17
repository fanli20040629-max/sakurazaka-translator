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
        boolean hasNodes = fragments.stream().anyMatch(f -> selectedIds.contains(f.id)
                && f.source != ProbeModels.Source.OCR);
        boolean hasOcr = fragments.stream().anyMatch(f -> selectedIds.contains(f.id)
                && f.source == ProbeModels.Source.OCR);
        for (ProbeModels.TextFragment fragment : TextAssembly.candidateOrder(fragments)) {
            if (!selectedIds.contains(fragment.id)) continue;
            if (fragment.rawText == null || fragment.rawText.isBlank()) continue;
            int top = fragment.bounds == null ? Integer.MAX_VALUE : fragment.bounds.top;
            int left = fragment.bounds == null ? Integer.MAX_VALUE : fragment.bounds.left;
            int right = fragment.bounds == null ? Integer.MAX_VALUE : fragment.bounds.right;
            int bottom = fragment.bounds == null ? Integer.MAX_VALUE : fragment.bounds.bottom;
            List<String> warnings = new ArrayList<>(fragment.warnings);
            warnings.add("MESSAGE_BOUNDARY_UNVERIFIED");
            if (hasNodes && hasOcr) warnings.add("CROSS_SOURCE_UNVERIFIED");
            if (fragment.role == ProbeModels.Role.METADATA || fragment.role == ProbeModels.Role.CONTROL) {
                warnings.add("MANUAL_ROLE_OVERRIDE");
            }
            if (fragment.role == ProbeModels.Role.UNKNOWN || fragment.role == ProbeModels.Role.MEDIA_CANDIDATE) {
                warnings.add("ROLE_UNVERIFIED");
            }
            List<String> provenance = fragment.provenance.isEmpty()
                    ? List.of(fragment.id) : fragment.provenance;
            messages.add(new ChatMessage(fragment.id, fragment.rawText,
                    fragment.source.name(), left, top, right, bottom,
                    fragment.source == ProbeModels.Source.OCR ? "IMAGE" : "SCREEN",
                    provenance, warnings));
        }
        return List.copyOf(messages);
    }

}
