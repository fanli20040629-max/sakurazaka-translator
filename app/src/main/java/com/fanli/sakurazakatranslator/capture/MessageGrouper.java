package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Suggests groups only inside a common collection item. No OCR geometry guessing. */
public final class MessageGrouper {
    private MessageGrouper() { }

    public static List<ChatMessage> group(List<ChatMessage> selected, List<NodeRecord> nodes) {
        Map<String, NodeRecord> index = new HashMap<>();
        for (NodeRecord node : nodes) {
            if (index.put(node.id, node) != null) return List.copyOf(selected);
        }
        List<ChatMessage> result = new ArrayList<>();
        for (int start = 0; start < selected.size();) {
            ChatMessage first = selected.get(start);
            String owner = owner(first, index);
            int end = start + 1;
            while (owner != null && end < selected.size()
                    && owner.equals(owner(selected.get(end), index))
                    && first.source.equals(selected.get(end).source)
                    && selected.get(end - 1).bottom <= selected.get(end).top) end++;
            result.add(end - start < 2 ? first : join(selected.subList(start, end)));
            start = end;
        }
        return List.copyOf(result);
    }

    private static String owner(ChatMessage message, Map<String, NodeRecord> nodes) {
        if (!"SCREEN".equals(message.coordinateSpace) || "OCR".equals(message.source)
                || message.right <= message.left || message.bottom <= message.top
                || message.warnings.stream().anyMatch(w -> w.equals("MANUAL_ROLE_OVERRIDE")
                    || w.equals("ROLE_UNVERIFIED") || w.equals("NODE_FIELDS_DIFFER")
                    || w.equals("NODE_TRAVERSAL_TRUNCATED"))) return null;
        String found = null;
        for (String source : message.provenance) {
            String id = source.split(":", 2)[0];
            HashSet<String> visited = new HashSet<>();
            NodeRecord node = nodes.get(id);
            while (node != null && !node.collectionItem && visited.add(node.id)) {
                node = nodes.get(node.parentId);
            }
            if (node == null || !node.collectionItem || !node.visible
                    || node.screenBounds == null || node.screenBounds.isEmpty()) return null;
            Bounds bounds = node.screenBounds;
            if (message.left < bounds.left || message.top < bounds.top
                    || message.right > bounds.right || message.bottom > bounds.bottom) return null;
            if (found != null && !found.equals(node.id)) return null;
            found = node.id;
        }
        return found;
    }

    private static ChatMessage join(List<ChatMessage> parts) {
        ChatMessage first = parts.get(0);
        StringBuilder text = new StringBuilder();
        LinkedHashSet<String> sources = new LinkedHashSet<>(), warnings = new LinkedHashSet<>();
        int left = first.left, top = first.top, right = first.right, bottom = first.bottom;
        for (ChatMessage part : parts) {
            if (text.length() > 0) text.append('\n');
            text.append(part.originalText);
            left = Math.min(left, part.left); top = Math.min(top, part.top);
            right = Math.max(right, part.right); bottom = Math.max(bottom, part.bottom);
            sources.addAll(part.provenance); warnings.addAll(part.warnings);
        }
        warnings.add("GROUPING_SUGGESTED");
        return new ChatMessage("group:" + first.id, text.toString(), first.source,
                left, top, right, bottom, "SCREEN", List.copyOf(sources), List.copyOf(warnings));
    }
}
