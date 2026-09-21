package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Groups selected lines by list structure; proximity alone never establishes message ownership. */
public final class MessageGrouper {
    private MessageGrouper() { }

    public record SelectionGroup(List<String> fragmentIds, String text) {
        public SelectionGroup { fragmentIds = List.copyOf(fragmentIds); }
    }

    public static List<SelectionGroup> selectionGroups(List<TextFragment> fragments, List<NodeRecord> nodes) {
        Set<String> ids = new HashSet<>();
        for (TextFragment fragment : fragments) if (fragment.role == Role.BODY) ids.add(fragment.id);
        List<ChatMessage> candidates = ConfirmedMessageFactory.fromFragments(fragments, ids);
        List<SelectionGroup> result = new ArrayList<>();
        for (ChatMessage message : group(candidates, nodes)) {
            if (!message.warnings.contains("GROUPING_SUGGESTED")) continue;
            Set<String> sources = new HashSet<>(message.provenance);
            List<String> members = candidates.stream()
                    .filter(part -> !part.provenance.isEmpty() && sources.containsAll(part.provenance))
                    .map(part -> part.id).toList();
            if (members.size() > 1) result.add(new SelectionGroup(members, message.originalText));
        }
        return List.copyOf(result);
    }

    public static List<ChatMessage> group(List<ChatMessage> selected, List<NodeRecord> nodes) {
        Map<String, NodeRecord> index = new HashMap<>();
        for (NodeRecord node : nodes) {
            if (index.put(node.id, node) != null) return List.copyOf(selected);
        }
        Set<String> rows = rowIds(nodes, index);
        Map<String, List<Bounds>> barriers = barriersByRow(selected, nodes, index, rows);
        List<ChatMessage> result = new ArrayList<>();
        for (int start = 0; start < selected.size();) {
            ChatMessage first = selected.get(start);
            String owner = owner(first, index, rows);
            int end = start + 1;
            while (owner != null && end < selected.size()
                    && owner.equals(owner(selected.get(end), index, rows))
                    && first.source.equals(selected.get(end).source)
                    && selected.get(end - 1).bottom <= selected.get(end).top
                    && !hasBarrier(selected.get(end - 1), selected.get(end),
                            barriers.getOrDefault(owner, List.of()))) end++;
            result.add(end - start < 2 ? first : join(selected.subList(start, end)));
            start = end;
        }
        return List.copyOf(result);
    }

    private static String owner(ChatMessage message, Map<String, NodeRecord> nodes, Set<String> rows) {
        if (!"SCREEN".equals(message.coordinateSpace) || "OCR".equals(message.source)
                || message.right <= message.left || message.bottom <= message.top
                || message.warnings.stream().anyMatch(w -> w.equals("MANUAL_ROLE_OVERRIDE")
                    || w.equals("ROLE_UNVERIFIED") || w.equals("NODE_FIELDS_DIFFER")
                    || w.equals("NODE_TRAVERSAL_TRUNCATED"))) return null;
        String found = null;
        for (String source : message.provenance) {
            String id = source.split(":", 2)[0];
            NodeRecord node = closestRow(nodes.get(id), nodes, rows);
            if (node == null || node.screenBounds == null || node.screenBounds.isEmpty()) return null;
            Bounds bounds = node.screenBounds;
            if (message.left < bounds.left || message.top < bounds.top
                    || message.right > bounds.right || message.bottom > bounds.bottom) return null;
            if (found != null && !found.equals(node.id)) return null;
            found = node.id;
        }
        return found;
    }

    /** A collection boundary cannot be crossed just because an outer section looks like a row. */
    private static NodeRecord closestRow(NodeRecord node, Map<String, NodeRecord> nodes, Set<String> rows) {
        int window = node == null ? -1 : node.windowId;
        Set<String> visited = new HashSet<>();
        while (node != null && visited.add(node.id)) {
            if (!node.visible || node.windowId != window || isList(node)) return null;
            if (rows.contains(node.id)) return node;
            node = nodes.get(node.parentId);
        }
        return null;
    }

    private static Map<String, List<Bounds>> barriersByRow(List<ChatMessage> selected, List<NodeRecord> nodes,
                                                          Map<String, NodeRecord> index, Set<String> rows) {
        Set<String> selectedSources = new HashSet<>();
        for (ChatMessage message : selected) selectedSources.addAll(message.provenance);
        Set<String> barrierIds = new HashSet<>();
        for (TextFragment fragment : TextAssembly.fromNodes(nodes)) {
            // Keep skipped body lines as boundaries too: a partial selection is not a continuous message.
            if (fragment.role != Role.BODY || !selectedSources.containsAll(fragment.provenance)) {
                for (String source : fragment.provenance) barrierIds.add(source.split(":", 2)[0]);
            }
        }
        Set<String> parents = new HashSet<>();
        for (NodeRecord node : nodes) parents.add(node.parentId);
        Map<String, List<Bounds>> result = new HashMap<>();
        for (NodeRecord node : nodes) {
            if (!node.visible) continue;
            String cls = node.className == null ? "" : node.className;
            // Images and controls can have no text/description, hence no TextFragment at all.
            boolean nestedList = isList(node);
            boolean nonTextContent = nestedList || cls.endsWith("ImageView") || cls.endsWith("VideoView")
                    || cls.endsWith("Button") || (node.clickable && !parents.contains(node.id));
            if (!barrierIds.contains(node.id) && !nonTextContent) continue;
            // Its children cannot belong to an outer row, but the list's rectangle still separates outer text.
            NodeRecord row = closestRow(nestedList ? index.get(node.parentId) : node, index, rows);
            if (row != null && row.windowId == node.windowId) {
                result.computeIfAbsent(row.id, key -> new ArrayList<>()).add(node.screenBounds);
            }
        }
        return result;
    }

    private static Set<String> rowIds(List<NodeRecord> nodes, Map<String, NodeRecord> index) {
        Set<String> rows = new HashSet<>();
        Set<String> parents = new HashSet<>();
        for (NodeRecord node : nodes) if (node.parentId != null) parents.add(node.parentId);
        Map<String, List<NodeRecord>> children = new HashMap<>();
        for (NodeRecord node : nodes) {
            if (!node.visible || node.screenBounds == null || node.screenBounds.isEmpty()) continue;
            if (node.collectionItem) rows.add(node.id);
            NodeRecord parent = index.get(node.parentId);
            if (parent != null && isList(parent) && parent.visible && parent.windowId == node.windowId
                    && contains(parent.screenBounds, node.screenBounds)) {
                children.computeIfAbsent(parent.id, key -> new ArrayList<>()).add(node);
            }
        }
        for (List<NodeRecord> siblings : children.values()) {
            for (NodeRecord child : siblings) {
                // Text leaves and controls directly in a list are not evidence of multi-line bubbles.
                boolean container = parents.contains(child.id);
                if (!container || child.className == null || isList(child)) continue;
                boolean repeated = siblings.stream().anyMatch(other -> other != child
                        && child.className.equals(other.className)
                        && (child.screenBounds.bottom <= other.screenBounds.top
                            || other.screenBounds.bottom <= child.screenBounds.top));
                boolean overlap = siblings.stream().anyMatch(other -> other != child
                        && child.screenBounds.top < other.screenBounds.bottom
                        && other.screenBounds.top < child.screenBounds.bottom);
                if (repeated && !overlap) rows.add(child.id);
            }
        }
        return rows;
    }

    private static boolean isList(NodeRecord node) {
        String cls = node.className == null ? "" : node.className;
        return node.collection || cls.equals("RecyclerView") || cls.endsWith(".RecyclerView")
                || cls.equals("ListView") || cls.equals("android.widget.ListView");
    }

    private static boolean contains(Bounds outer, Bounds inner) {
        return outer != null && !outer.isEmpty() && inner.left >= outer.left && inner.top >= outer.top
                && inner.right <= outer.right && inner.bottom <= outer.bottom;
    }

    private static boolean hasBarrier(ChatMessage before, ChatMessage after, List<Bounds> barriers) {
        int left = Math.min(before.left, after.left), right = Math.max(before.right, after.right);
        // Unknown bounds inside this row are not evidence of a clear gap. Other rows/windows were excluded above.
        return barriers.stream().anyMatch(b -> b == null || b.isEmpty()
                || (b.left < right && b.right > left && b.top < after.top && b.bottom > before.bottom));
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
