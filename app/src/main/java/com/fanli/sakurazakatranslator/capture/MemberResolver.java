package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.StyleProfile;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conservatively resolves a member only from reliable app-header evidence. */
public final class MemberResolver {
    private static final Set<String> TITLE_RESOURCE_ENTRIES = Set.of(
            "title", "toolbar_title", "chat_title", "member_name", "idol_name", "artist_name");

    private MemberResolver() { }

    public enum Status { MATCHED, UNKNOWN, AMBIGUOUS, MISSING }

    public record Resolution(Status status, String identityKey, String name,
                             StyleProfile profile, Set<String> nodeIds) {
        public Resolution {
            if (status == null) throw new IllegalArgumentException("status");
            name = name == null ? "" : name;
            nodeIds = Set.copyOf(nodeIds == null ? Set.of() : nodeIds);
        }
    }

    public static Resolution resolve(List<ProbeModels.NodeRecord> nodes,
                                     ProbeModels.Bounds viewport,
                                     List<StyleProfile> profiles,
                                     boolean truncated) {
        ProfileIndex profileIndex = indexProfiles(profiles);
        if (profileIndex.invalid) return ambiguous("", Set.of());
        if (truncated || viewport == null || viewport.isEmpty() || nodes == null) return missing();

        Map<String, ProbeModels.NodeRecord> byId = new HashMap<>();
        Set<String> duplicateNodeIds = new HashSet<>();
        for (ProbeModels.NodeRecord node : nodes) {
            if (node == null || node.id == null) continue;
            if (byId.putIfAbsent(node.id, node) != null) duplicateNodeIds.add(node.id);
        }

        Map<String, Evidence> titles = new LinkedHashMap<>();
        for (ProbeModels.NodeRecord node : nodes) {
            if (!isCandidateNode(node, viewport, byId, duplicateNodeIds)) continue;
            String text = usableText(node.rawText);
            String description = usableText(node.rawDescription);
            if (text != null && description != null && !normalize(text).equals(normalize(description))) {
                LinkedHashSet<String> ids = new LinkedHashSet<>();
                if (node.id != null) ids.add(node.id);
                return ambiguous(text, ids);
            }
            String observed = text != null ? text : description;
            if (observed == null) continue;
            String normalized = normalize(observed);
            Evidence evidence = titles.computeIfAbsent(normalized, unused -> new Evidence(observed));
            if (node.id != null) evidence.nodeIds.add(node.id);
        }

        if (titles.isEmpty()) return missing();
        if (titles.size() != 1) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (Evidence evidence : titles.values()) ids.addAll(evidence.nodeIds);
            return ambiguous(titles.values().iterator().next().name, ids);
        }

        Map.Entry<String, Evidence> title = titles.entrySet().iterator().next();
        Set<StyleProfile> matches = profileIndex.byName.getOrDefault(title.getKey(), Set.of());
        if (matches.size() > 1) return ambiguous(title.getValue().name, title.getValue().nodeIds);
        if (matches.isEmpty()) {
            return new Resolution(Status.UNKNOWN, "name:" + title.getKey(), title.getValue().name,
                    null, title.getValue().nodeIds);
        }
        StyleProfile profile = matches.iterator().next();
        return new Resolution(Status.MATCHED, "profile:" + profile.id, title.getValue().name,
                profile, title.getValue().nodeIds);
    }

    private static boolean isCandidateNode(ProbeModels.NodeRecord node, ProbeModels.Bounds viewport,
                                           Map<String, ProbeModels.NodeRecord> byId,
                                           Set<String> duplicateNodeIds) {
        if (node == null || !node.visible || node.collection || node.collectionItem) return false;
        if (isInteractiveClass(node.className)) return false;
        ProbeModels.Bounds bounds = node.screenBounds;
        if (bounds == null || bounds.isEmpty() || bounds.left < viewport.left || bounds.top < viewport.top
                || bounds.right > viewport.right || bounds.bottom > viewport.bottom) return false;
        long viewportHeight = viewport.height();
        if ((long) bounds.height() * 100 > viewportHeight * 12
                || (long) (bounds.bottom - viewport.top) * 100 > viewportHeight * 22) return false;

        boolean semanticTitle = TITLE_RESOURCE_ENTRIES.contains(resourceEntry(node.viewId));
        boolean toolbarAncestor = false;
        Set<String> visited = new HashSet<>();
        if (node.id != null) visited.add(node.id);
        String parentId = node.parentId;
        while (parentId != null) {
            if (!visited.add(parentId) || duplicateNodeIds.contains(parentId)) return false;
            ProbeModels.NodeRecord parent = byId.get(parentId);
            if (parent == null || parent.windowId != node.windowId) return false;
            if (parent.collection || parent.collectionItem || isScrollingClass(parent.className)) return false;
            if (isToolbarClass(parent.className)) toolbarAncestor = true;
            parentId = parent.parentId;
        }
        if (!semanticTitle && !toolbarAncestor) return false;
        return !node.clickable || semanticTitle || toolbarAncestor;
    }

    private static String usableText(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        if (stripped.isEmpty() || stripped.length() > 60
                || stripped.indexOf('\n') >= 0 || stripped.indexOf('\r') >= 0) return null;
        return stripped;
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder compact = new StringBuilder(normalized.length());
        normalized.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)
                && !Character.isSpaceChar(codePoint)).forEach(compact::appendCodePoint);
        return compact.toString();
    }

    private static String resourceEntry(String viewId) {
        if (viewId == null) return "";
        int marker = viewId.lastIndexOf(":id/");
        if (marker < 0 || marker + 4 >= viewId.length()) return "";
        return viewId.substring(marker + 4).toLowerCase(Locale.ROOT);
    }

    private static boolean isInteractiveClass(String className) {
        String value = simpleClass(className);
        return value.contains("edittext") || value.contains("button") || value.contains("checkbox")
                || value.contains("radiobutton") || value.contains("switch");
    }

    private static boolean isScrollingClass(String className) {
        String value = simpleClass(className);
        return value.contains("scrollview") || value.contains("recyclerview")
                || value.equals("listview") || value.contains("abslistview");
    }

    private static boolean isToolbarClass(String className) {
        String value = simpleClass(className);
        return value.equals("toolbar") || value.equals("actionbar")
                || value.endsWith("toolbar") || value.endsWith("actionbar");
    }

    private static String simpleClass(String className) {
        if (className == null) return "";
        int separator = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return className.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static ProfileIndex indexProfiles(List<StyleProfile> profiles) {
        if (profiles == null) return new ProfileIndex(Map.of(), false);
        Map<String, Set<StyleProfile>> byName = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (StyleProfile profile : profiles) {
            if (profile == null || !ids.add(profile.id)) return new ProfileIndex(Map.of(), true);
            List<String> names = new ArrayList<>(profile.aliases.size() + 1);
            names.add(profile.displayName);
            names.addAll(profile.aliases);
            for (String name : names) {
                byName.computeIfAbsent(normalize(name), unused -> new LinkedHashSet<>()).add(profile);
            }
        }
        return new ProfileIndex(byName, false);
    }

    private static Resolution missing() {
        return new Resolution(Status.MISSING, null, "", null, Set.of());
    }

    private static Resolution ambiguous(String name, Set<String> nodeIds) {
        return new Resolution(Status.AMBIGUOUS, null, name, null, nodeIds);
    }

    private static final class Evidence {
        final String name;
        final Set<String> nodeIds = new LinkedHashSet<>();
        Evidence(String name) { this.name = name; }
    }

    private static final class ProfileIndex {
        final Map<String, Set<StyleProfile>> byName;
        final boolean invalid;
        ProfileIndex(Map<String, Set<StyleProfile>> byName, boolean invalid) {
            this.byName = byName;
            this.invalid = invalid;
        }
    }
}
