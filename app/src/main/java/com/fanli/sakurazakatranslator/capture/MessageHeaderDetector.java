package com.fanli.sakurazakatranslator.capture;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Detects message boundaries from the member/time row visible in the chat UI. */
public final class MessageHeaderDetector {
    private static final Pattern TIME = Pattern.compile(
            "(?:\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}[./-]\\d{1,2})?\\s*\\d{1,2}:\\d{2}");
    private static final Set<String> MEDIA_CLASSES = Set.of("imageview", "videoView".toLowerCase(Locale.ROOT));

    private MessageHeaderDetector() { }

    public enum Confidence { HIGH, LOW }

    public record MessageBlock(List<String> headerNodeIds, List<String> bodyNodeIds,
                               String memberName, String timeText, Confidence confidence) {
        public MessageBlock {
            headerNodeIds = List.copyOf(headerNodeIds);
            bodyNodeIds = List.copyOf(bodyNodeIds);
            memberName = memberName == null ? "" : memberName;
            timeText = timeText == null ? "" : timeText;
            confidence = confidence == null ? Confidence.LOW : confidence;
        }
    }

    private record Header(NodeRecord member, NodeRecord time, String memberName, String timeText,
                          int top, int bottom, String container) { }

    public static List<MessageBlock> detect(List<NodeRecord> nodes, List<String> memberNames, int windowId) {
        if (nodes == null || memberNames == null || memberNames.isEmpty()) return List.of();
        Map<String, NodeRecord> byId = new HashMap<>();
        for (NodeRecord node : nodes) if (node != null && node.id != null) byId.putIfAbsent(node.id, node);
        List<NodeRecord> visible = nodes.stream()
                .filter(node -> usable(node, windowId))
                .sorted(Comparator.comparingInt(MessageHeaderDetector::top)
                        .thenComparingInt(MessageHeaderDetector::left)
                        .thenComparingInt(node -> node.traversalIndex))
                .toList();
        List<Header> headers = new ArrayList<>();
        Set<String> usedTimes = new HashSet<>();
        for (NodeRecord member : visible) {
            String memberText = text(member);
            if (!isMemberName(memberText, memberNames)) continue;
            NodeRecord best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (NodeRecord candidate : visible) {
                if (candidate == member || usedTimes.contains(candidate.id)) continue;
                String timeText = text(candidate);
                if (candidate.clickable || !isTime(timeText)
                        || !sameHeaderContainer(member, candidate, byId)) continue;
                int distance = Math.abs(top(candidate) - top(member));
                if (distance <= 90 && distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
            if (best == null) continue;
            usedTimes.add(best.id);
            headers.add(new Header(member, best, memberText.strip(), text(best).strip(),
                    Math.min(top(member), top(best)), Math.max(bottom(member), bottom(best)),
                    container(member, byId)));
        }
        headers.sort(Comparator.comparingInt(Header::top));
        List<MessageBlock> result = new ArrayList<>();
        for (int index = 0; index < headers.size(); index++) {
            Header header = headers.get(index);
            int nextTop = index + 1 < headers.size() ? headers.get(index + 1).top : Integer.MAX_VALUE;
            List<String> bodies = new ArrayList<>();
            boolean sameContainer = true;
            for (NodeRecord node : visible) {
                if (node.id.equals(header.member.id) || node.id.equals(header.time.id)
                        || top(node) < header.bottom || top(node) >= nextTop || !bodyCandidate(node)) continue;
                if (!sameBodyContainer(header, node, byId)) {
                    sameContainer = false;
                    continue;
                }
                bodies.add(node.id);
            }
            Confidence confidence = !bodies.isEmpty() && sameContainer
                    ? Confidence.HIGH : Confidence.LOW;
            result.add(new MessageBlock(List.of(header.member.id, header.time.id), bodies,
                    header.memberName, header.timeText, confidence));
        }
        return List.copyOf(result);
    }

    public static boolean isMemberName(String value, List<String> memberNames) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return false;
        return memberNames.stream().anyMatch(name -> !normalize(name).isEmpty()
                && normalize(name).equals(normalized));
    }

    public static boolean isTime(String value) {
        String normalized = value == null ? "" : value.strip();
        return !normalized.isEmpty() && TIME.matcher(normalized).matches();
    }

    private static boolean usable(NodeRecord node, int windowId) {
        return node != null && node.visible && node.windowId == windowId
                && node.screenBounds != null && !node.screenBounds.isEmpty();
    }

    private static boolean bodyCandidate(NodeRecord node) {
        String value = text(node);
        if (value.isEmpty() || isTime(value) || node.clickable) return false;
        String simple = simpleClass(node.className);
        return !MEDIA_CLASSES.contains(simple) && !simple.contains("button")
                && !simple.contains("image") && !simple.contains("video")
                && !simple.contains("checkbox") && !simple.contains("switch");
    }

    private static boolean sameHeaderContainer(NodeRecord first, NodeRecord second,
                                                Map<String, NodeRecord> byId) {
        return container(first, byId).equals(container(second, byId))
                || first.parentId != null && first.parentId.equals(second.parentId);
    }

    private static boolean sameBodyContainer(Header header, NodeRecord body,
                                              Map<String, NodeRecord> byId) {
        return header.container.equals(container(body, byId))
                || header.member.parentId != null && header.member.parentId.equals(body.parentId);
    }

    private static String container(NodeRecord node, Map<String, NodeRecord> byId) {
        String current = node.id;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            NodeRecord candidate = byId.get(current);
            if (candidate == null) break;
            if (candidate.collectionItem) return "row:" + candidate.id;
            current = candidate.parentId;
        }
        return "parent:" + (node.parentId == null ? node.id : node.parentId);
    }

    private static String text(NodeRecord node) {
        String value = node.rawText == null || node.rawText.isBlank() ? node.rawDescription : node.rawText;
        return value == null ? "" : value.strip();
    }

    private static int top(NodeRecord node) { return node.screenBounds == null ? Integer.MAX_VALUE : node.screenBounds.top; }
    private static int bottom(NodeRecord node) { return node.screenBounds == null ? Integer.MIN_VALUE : node.screenBounds.bottom; }
    private static int left(NodeRecord node) { return node.screenBounds == null ? Integer.MAX_VALUE : node.screenBounds.left; }

    private static String simpleClass(String className) {
        if (className == null) return "";
        int separator = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return className.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.replaceAll("\\s+", "").strip();
    }
}
