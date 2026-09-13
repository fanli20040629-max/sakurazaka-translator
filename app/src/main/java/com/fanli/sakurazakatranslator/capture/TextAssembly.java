package com.fanli.sakurazakatranslator.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Conservative, deterministic assembly. It never edits raw text or globally de-dupes strings. */
public final class TextAssembly {
    private TextAssembly() { }

    public static List<TextFragment> fromNodes(List<NodeRecord> nodes) {
        List<TextFragment> result = new ArrayList<>();
        Set<String> sameNode = new HashSet<>();
        for (NodeRecord node : nodes) {
            if (!node.visible) continue;
            append(result, sameNode, node.id + ":text", node.rawText, Source.NODE_TEXT,
                    node, "node text");
            append(result, sameNode, node.id + ":desc", node.rawDescription,
                    Source.NODE_DESCRIPTION, node, "node description");
        }
        result.sort(fragmentComparator());
        return result;
    }

    public static List<TextFragment> mergeOcr(List<TextFragment> nodeFragments,
                                               List<TextFragment> ocrFragments) {
        List<TextFragment> result = new ArrayList<>(nodeFragments);
        result.addAll(ocrFragments);
        result.sort(fragmentComparator());
        return result;
    }

    public static TextFragment ocr(String id, String rawText, Bounds bounds, int index) {
        List<String> warnings = new ArrayList<>();
        if (bounds == null || bounds.isEmpty()) warnings.add("OCR_BOUNDARY_MISSING");
        warnings.add("COORDINATE_MAPPING_UNVERIFIED");
        return new TextFragment(id, rawText, rawText, Source.OCR, Role.MEDIA_CANDIDATE,
                null, bounds, Collections.emptyList(), warnings,
                "OCR 独立候选，未自动合并", false);
    }

    private static void append(List<TextFragment> result, Set<String> sameNode,
                               String id, String value, Source source, NodeRecord node,
                               String reason) {
        if (value == null || value.isEmpty() || value.trim().isEmpty()) return;
        String key = node.id + "|" + value + "|" + source;
        if (!sameNode.add(key)) return;
        Role role = classify(node, value, source);
        List<String> warnings = new ArrayList<>();
        if (node.screenBounds == null || node.screenBounds.isEmpty()) warnings.add("BOUNDARY_MISSING");
        if (!node.visible) warnings.add("NOT_VISIBLE");
        result.add(new TextFragment(id, value, value, source, role, null,
                node.screenBounds, Collections.singletonList(node.id), warnings, reason, false));
    }

    private static Role classify(NodeRecord node, String value, Source source) {
        if (source == Source.OCR) return Role.MEDIA_CANDIDATE;
        String cls = node.className == null ? "" : node.className;
        if (cls.endsWith("Button") || cls.endsWith("ImageButton")) return Role.CONTROL;
        if (cls.endsWith("ImageView") || cls.endsWith("VideoView")) return Role.MEDIA_CANDIDATE;
        if (node.clickable && value.length() < 24) return Role.UNKNOWN;
        return Role.BODY;
    }

    private static Comparator<TextFragment> fragmentComparator() {
        return (a, b) -> {
            Bounds x = a.bounds, y = b.bounds;
            int top = Integer.compare(x == null ? Integer.MAX_VALUE : x.top,
                    y == null ? Integer.MAX_VALUE : y.top);
            if (top != 0) return top;
            int left = Integer.compare(x == null ? Integer.MAX_VALUE : x.left,
                    y == null ? Integer.MAX_VALUE : y.left);
            if (left != 0) return left;
            return a.id.compareTo(b.id);
        };
    }
}
