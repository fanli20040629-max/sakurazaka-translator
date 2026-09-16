package com.fanli.sakurazakatranslator.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Conservative, deterministic assembly. It never edits raw text or globally de-dupes strings. */
public final class TextAssembly {
    private static final Pattern DURATION = Pattern.compile("^\\s*\\d{1,2}:\\d{2}\\s*$");
    private static final Pattern DATE_AND_TIME = Pattern.compile(
            "^.{1,48}\\b\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2}\\s*$");

    private TextAssembly() { }

    public static List<TextFragment> fromNodes(List<NodeRecord> nodes) {
        List<TextFragment> result = new ArrayList<>();
        Set<String> sameNode = new HashSet<>();
        // Preserve numeric traversal order at identical screen coordinates.
        List<NodeRecord> orderedNodes = new ArrayList<>(nodes);
        orderedNodes.sort(Comparator.comparingInt(node -> node.traversalIndex));
        for (NodeRecord node : orderedNodes) {
            if (!node.visible) continue;
            if (hasText(node.rawText) && node.rawText.equals(node.rawDescription)) {
                append(result, sameNode, node.id + ":text+desc", node.rawText, Source.NODE_TEXT,
                        node, List.of(node.id + ":text", node.id + ":description"),
                        "同一节点的 text 与 description 完全相同，合并显示并保留双来源");
            } else {
                append(result, sameNode, node.id + ":text", node.rawText, Source.NODE_TEXT,
                        node, Collections.singletonList(node.id + ":text"), "节点 text");
                append(result, sameNode, node.id + ":desc", node.rawDescription,
                        Source.NODE_DESCRIPTION, node,
                        Collections.singletonList(node.id + ":description"), "节点 description");
            }
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

    /** Horizontal chat layout: top, then left; ties preserve provider/traversal order. */
    public static List<TextFragment> screenOrder(List<TextFragment> fragments) {
        List<TextFragment> result = new ArrayList<>(fragments);
        result.sort(fragmentComparator());
        return List.copyOf(result);
    }

    public static TextFragment ocr(String id, String rawText, Bounds bounds, int index) {
        List<String> warnings = new ArrayList<>();
        if (bounds == null || bounds.isEmpty()) warnings.add("OCR_BOUNDARY_MISSING");
        warnings.add("COORDINATE_MAPPING_UNVERIFIED");
        return new TextFragment(id, rawText, rawText, Source.OCR, Role.MEDIA_CANDIDATE,
                null, bounds, Collections.emptyList(), warnings,
                "OCR 独立候选，未自动合并", false);
    }

    /** Reading-oriented node output. Coordinates and source IDs stay in diagnostics. */
    public static String formatNodeSections(List<TextFragment> fragments) {
        StringBuilder output = new StringBuilder();
        appendSection(output, "正文候选", fragments, Role.BODY);
        appendSection(output, "作者/时间等信息（待核对）", fragments, Role.METADATA);
        appendSection(output, "其他可见文字（待核对）", fragments, Role.UNKNOWN);
        appendSection(output, "控件/媒体（未加入正文）", fragments,
                Role.CONTROL, Role.MEDIA_CANDIDATE);
        if (output.length() == 0) return "（没有可见文字节点）";
        return output.toString();
    }

    private static void append(List<TextFragment> result, Set<String> sameNode,
                               String id, String value, Source source, NodeRecord node,
                               List<String> provenance, String reason) {
        if (!hasText(value)) return;
        String key = node.id + "|" + value + "|" + source;
        if (!sameNode.add(key)) return;
        Role role = classify(node, value, source);
        List<String> warnings = new ArrayList<>();
        if (node.screenBounds == null || node.screenBounds.isEmpty()) warnings.add("BOUNDARY_MISSING");
        if (!node.visible) warnings.add("NOT_VISIBLE");
        result.add(new TextFragment(id, value, value, source, role, null,
                node.screenBounds, provenance, warnings,
                reason + "；" + roleReason(role), false));
    }

    private static Role classify(NodeRecord node, String value, Source source) {
        if (source == Source.OCR) return Role.MEDIA_CANDIDATE;
        String cls = node.className == null ? "" : node.className;
        if (cls.endsWith("Button") || cls.endsWith("ImageButton")) return Role.CONTROL;
        if (cls.endsWith("ImageView") || cls.endsWith("VideoView")) return Role.MEDIA_CANDIDATE;
        if (!value.contains("\n") && (DURATION.matcher(value).matches()
                || DATE_AND_TIME.matcher(value).matches())) return Role.METADATA;
        if (node.clickable && value.length() < 24) return Role.UNKNOWN;
        return Role.BODY;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty() && !value.trim().isEmpty();
    }

    private static String roleReason(Role role) {
        return switch (role) {
            case BODY -> "未发现明确辅助语义，保守保留为正文候选";
            case METADATA -> "单行日期时间或时长格式，单独展示但不删除";
            case CONTROL -> "控件类节点";
            case MEDIA_CANDIDATE -> "媒体类节点";
            case UNKNOWN -> "短可点击文字，语义不足，等待核对";
        };
    }

    private static void appendSection(StringBuilder output, String title,
                                      List<TextFragment> fragments, Role... roles) {
        StringBuilder section = new StringBuilder();
        for (TextFragment fragment : fragments) {
            if (!contains(roles, fragment.role)) continue;
            if (section.length() > 0) section.append('\n');
            section.append(fragment.displayText);
        }
        if (section.length() == 0) return;
        if (output.length() > 0) output.append("\n\n");
        output.append(title).append("\n").append(section);
    }

    private static boolean contains(Role[] roles, Role value) {
        for (Role role : roles) if (role == value) return true;
        return false;
    }

    private static Comparator<TextFragment> fragmentComparator() {
        return (a, b) -> {
            Bounds x = a.bounds, y = b.bounds;
            int top = Integer.compare(x == null ? Integer.MAX_VALUE : x.top,
                    y == null ? Integer.MAX_VALUE : y.top);
            if (top != 0) return top;
            int left = Integer.compare(x == null ? Integer.MAX_VALUE : x.left,
                    y == null ? Integer.MAX_VALUE : y.left);
            // Java's stable sort keeps input order when coordinates are identical.
            return left;
        };
    }
}
