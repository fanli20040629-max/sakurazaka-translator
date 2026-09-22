package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Conservative suggestions only; the user remains responsible for confirming message text. */
public final class BodySelectionPolicy {
    private BodySelectionPolicy() { }

    public static List<String> recommended(List<TextFragment> fragments, List<NodeRecord> nodes) {
        if (fragments == null || nodes == null) return List.of();
        Map<String, NodeRecord> index = new HashMap<>();
        for (NodeRecord node : nodes) {
            if (node == null || node.id == null || index.put(node.id, node) != null) return List.of();
        }
        List<String> result = new ArrayList<>();
        for (TextFragment fragment : fragments) {
            if (recommendable(fragment, index)) result.add(fragment.id);
        }
        return List.copyOf(result);
    }

    private static boolean recommendable(TextFragment fragment, Map<String, NodeRecord> nodes) {
        if (fragment == null || fragment.id == null || fragment.role != Role.BODY
                || fragment.source != Source.NODE_TEXT || fragment.bounds == null
                || fragment.bounds.isEmpty() || !fragment.warnings.isEmpty()
                || !sentenceEvidence(fragment.rawText) || fragment.provenance.isEmpty()) return false;
        NodeRecord sourceNode = null;
        for (String provenance : fragment.provenance) {
            if (provenance == null) return false;
            boolean text = provenance.endsWith(":text");
            boolean description = provenance.endsWith(":description");
            if (!text && !description) return false;
            int suffix = text ? 5 : 12;
            NodeRecord node = nodes.get(provenance.substring(0, provenance.length() - suffix));
            if (node == null || (sourceNode != null && sourceNode != node)) return false;
            sourceNode = node;
            if (text && !fragment.rawText.equals(node.rawText)) return false;
            if (description && (!fragment.rawText.equals(node.rawDescription)
                    || !fragment.rawText.equals(node.rawText))) return false;
        }
        return insideCollectionRow(sourceNode, fragment.bounds, nodes);
    }

    private static boolean sentenceEvidence(String text) {
        if (text == null || text.isBlank()) return false;
        boolean punctuation = text.indexOf('\n') >= 0 || text.codePoints().anyMatch(c ->
                "。！？!?、，,.…〜～".indexOf(c) >= 0);
        long length = text.codePoints().filter(c -> !Character.isWhitespace(c)).count();
        boolean kana = text.codePoints().anyMatch(c -> (c >= 0x3040 && c <= 0x30ff));
        return punctuation || (kana && length > 8);
    }

    private static boolean insideCollectionRow(NodeRecord start, Bounds fragmentBounds,
                                               Map<String, NodeRecord> nodes) {
        if (start == null || !start.visible || start.clickable || start.screenBounds == null
                || start.screenBounds.isEmpty() || !contains(start.screenBounds, fragmentBounds)
                || isControlOrMedia(start)) return false;
        int window = start.windowId;
        NodeRecord row = null;
        Set<String> visited = new HashSet<>();
        NodeRecord node = start;
        while (node != null && node.id != null && visited.add(node.id)) {
            if (node.windowId != window) return false;
            if (isCollection(node)) return row != null && row != node;
            if (node.collectionItem) {
                if (row != null || !node.visible || node.screenBounds == null || node.screenBounds.isEmpty()
                        || !contains(node.screenBounds, start.screenBounds)
                        || !contains(node.screenBounds, fragmentBounds)) return false;
                row = node;
            }
            node = nodes.get(node.parentId);
        }
        return node == null && row != null;
    }

    private static boolean isCollection(NodeRecord node) {
        String cls = node.className == null ? "" : node.className;
        return node.collection || cls.equals("RecyclerView") || cls.endsWith(".RecyclerView")
                || cls.equals("ListView") || cls.equals("android.widget.ListView");
    }

    private static boolean isControlOrMedia(NodeRecord node) {
        String cls = node.className == null ? "" : node.className;
        return cls.endsWith("ImageView") || cls.endsWith("VideoView") || cls.endsWith("Button")
                || cls.endsWith("EditText") || cls.endsWith("Switch") || cls.endsWith("CheckBox")
                || cls.endsWith("RadioButton") || cls.endsWith("SeekBar");
    }

    private static boolean contains(Bounds outer, Bounds inner) {
        return outer != null && inner != null && !outer.isEmpty() && !inner.isEmpty()
                && inner.left >= outer.left && inner.top >= outer.top
                && inner.right <= outer.right && inner.bottom <= outer.bottom;
    }

    /** Geometry warning only; false does not establish that the message is semantically complete. */
    public static boolean possiblyClipped(ChatMessage message, Bounds viewport) {
        if (message == null || viewport == null || viewport.isEmpty()) return true;
        if ("IMAGE".equals(message.source) || "OCR".equals(message.source)
                || !"SCREEN".equals(message.coordinateSpace)
                || message.right <= message.left || message.bottom <= message.top
                || message.warnings.contains("NODE_TRAVERSAL_TRUNCATED")) return true;
        return message.top <= viewport.top + 2 || message.bottom >= viewport.bottom - 2;
    }
}
