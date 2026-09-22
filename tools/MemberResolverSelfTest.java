package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.StyleProfile;
import java.util.ArrayList;
import java.util.List;

public final class MemberResolverSelfTest {
    private static final ProbeModels.Bounds VIEWPORT = new ProbeModels.Bounds(0, 0, 1000, 2000);
    private static int checks;

    public static void main(String[] args) {
        StyleProfile akari = new StyleProfile("akari", "花野あかり", "明るく", List.of("花野 あかり"));
        StyleProfile shiori = new StyleProfile("shiori", "月野しおり", "丁寧に");
        List<StyleProfile> profiles = List.of(akari, shiori);

        assertResolution(resolve(List.of(title("title", "花野あかり")), profiles),
                MemberResolver.Status.MATCHED, "profile:akari", "花野あかり", akari, "title");
        assertResolution(resolve(List.of(title("title", " 花野\u3000あかり ")), profiles),
                MemberResolver.Status.MATCHED, "profile:akari", "花野\u3000あかり", akari, "title");
        assertResolution(resolve(List.of(title("title", "花野 あかり")), profiles),
                MemberResolver.Status.MATCHED, "profile:akari", "花野 あかり", akari, "title");

        check(resolve(List.of(body("body", "花野あかり", 600)), profiles).status()
                == MemberResolver.Status.MISSING, "body mentions are not titles");
        check(resolve(List.of(collectionBody("row", "花野あかり", 100)), profiles).status()
                == MemberResolver.Status.MISSING, "collection row names are not titles");
        ProbeModels.NodeRecord scroll = node("scroll", null, 7, null, null,
                "android.widget.ScrollView", null, new ProbeModels.Bounds(0, 0, 1000, 500), true, false, false, false);
        ProbeModels.NodeRecord nestedTitle = node("nested", "scroll", 7, "花野あかり", null,
                "android.widget.TextView", "pkg:id/title", new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false);
        check(resolve(List.of(scroll, nestedTitle), profiles).status() == MemberResolver.Status.MISSING,
                "titles inside scrolling content are rejected");
        check(resolve(List.of(node("plain", null, 7, "花野あかり", null,
                "android.widget.TextView", null, new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false)), profiles)
                .status() == MemberResolver.Status.MISSING, "top text without title structure is rejected");

        MemberResolver.Resolution conflict = resolve(List.of(title("a", "花野あかり"), title("b", "月野しおり")), profiles);
        check(conflict.status() == MemberResolver.Status.AMBIGUOUS && conflict.profile() == null
                && conflict.identityKey() == null, "distinct header titles are ambiguous");
        MemberResolver.Resolution duplicate = resolve(List.of(title("a", "花野あかり"), title("b", "花野あかり")), profiles);
        assertResolution(duplicate, MemberResolver.Status.MATCHED, "profile:akari", "花野あかり", akari, "a", "b");

        List<StyleProfile> duplicateAlias = List.of(
                new StyleProfile("one", "星野ひかり", "", List.of("花野あかり")),
                new StyleProfile("two", "森野ゆき", "", List.of("花野 あかり")));
        check(resolve(List.of(title("title", "花野あかり")), duplicateAlias).status()
                == MemberResolver.Status.AMBIGUOUS, "duplicate normalized aliases are ambiguous");
        List<StyleProfile> duplicateIds = List.of(
                new StyleProfile("same", "花野あかり", ""), new StyleProfile("same", "月野しおり", ""));
        check(resolve(List.of(title("title", "花野あかり")), duplicateIds).status()
                == MemberResolver.Status.AMBIGUOUS, "duplicate profile IDs fail closed");

        MemberResolver.Resolution unknown = resolve(List.of(title("mystery", "雪野みらい")), profiles);
        assertResolution(unknown, MemberResolver.Status.UNKNOWN, "name:雪野みらい", "雪野みらい", null, "mystery");
        check(resolve(List.of(), profiles).status() == MemberResolver.Status.MISSING, "missing title is missing");
        check(resolve(List.of(withVisibility(title("hidden", "花野あかり"), false)), profiles).status()
                == MemberResolver.Status.MISSING, "invisible title is rejected");
        check(resolve(List.of(withBounds(title("outside", "花野あかり"), new ProbeModels.Bounds(0, -1, 300, 80))), profiles)
                .status() == MemberResolver.Status.MISSING, "off-viewport title is rejected");
        check(MemberResolver.resolve(List.of(title("title", "花野あかり")),
                new ProbeModels.Bounds(0, 0, 0, 0), profiles, false).status()
                == MemberResolver.Status.MISSING, "geometry-free viewport is missing");

        ProbeModels.NodeRecord toolbarOtherWindow = node("toolbar", null, 8, null, null,
                "android.widget.Toolbar", null, new ProbeModels.Bounds(0, 0, 1000, 150), true, false, false, false);
        ProbeModels.NodeRecord crossWindow = node("cross", "toolbar", 7, "花野あかり", null,
                "android.widget.TextView", null, new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false);
        check(resolve(List.of(toolbarOtherWindow, crossWindow), profiles).status() == MemberResolver.Status.MISSING,
                "cross-window ancestor cannot establish title structure");
        ProbeModels.NodeRecord cycleA = node("cycle-a", "cycle-b", 7, "花野あかり", null,
                "android.widget.TextView", null, new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false);
        ProbeModels.NodeRecord cycleB = node("cycle-b", "cycle-a", 7, null, null,
                "android.view.ViewGroup", null, new ProbeModels.Bounds(0, 0, 1000, 180), true, false, false, false);
        check(resolve(List.of(cycleA, cycleB), profiles).status() == MemberResolver.Status.MISSING,
                "cyclic ancestor chain fails closed");

        ProbeModels.NodeRecord conflictingFields = node("fields", null, 7, "花野あかり", "月野しおり",
                "android.widget.TextView", "pkg:id/title", new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false);
        check(resolve(List.of(conflictingFields), profiles).status() == MemberResolver.Status.AMBIGUOUS,
                "conflicting text and description are ambiguous");
        ProbeModels.NodeRecord sameFields = node("same-fields", null, 7, "花野あかり", "花野あかり",
                "android.widget.TextView", "pkg:id/title", new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false);
        assertResolution(resolve(List.of(sameFields), profiles), MemberResolver.Status.MATCHED,
                "profile:akari", "花野あかり", akari, "same-fields");
        check(MemberResolver.resolve(List.of(title("title", "花野あかり")), VIEWPORT, profiles, true).status()
                == MemberResolver.Status.MISSING, "truncated reads fail closed");

        check(resolve(List.of(node("button", null, 7, "花野あかり", null,
                "android.widget.Button", "pkg:id/title", new ProbeModels.Bounds(50, 40, 400, 120), true, true, false, false)), profiles)
                .status() == MemberResolver.Status.MISSING, "button source is rejected");
        check(resolve(List.of(node("substring", null, 7, "花野あかり公式", null,
                "android.widget.TextView", "pkg:id/title_label", new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false)), profiles)
                .status() == MemberResolver.Status.MISSING, "resource entry matching is exact, not substring");
        MemberResolver.Resolution fullOnly = resolve(List.of(title("full", "花野あかり公式")), profiles);
        check(fullOnly.status() == MemberResolver.Status.UNKNOWN, "profile matching is full-name only");

        check(shiori.aliases.isEmpty(), "three-argument constructor supplies empty aliases");
        ArrayList<String> mutableAliases = new ArrayList<>(List.of("  星野ひかり  "));
        StyleProfile immutable = new StyleProfile("immutable", "星野ひかり", "", mutableAliases);
        mutableAliases.clear();
        check(immutable.aliases.equals(List.of("星野ひかり")), "aliases are trimmed snapshots");
        expectFailure(() -> immutable.aliases.add("x"), "alias list is immutable");
        expectFailure(() -> new StyleProfile("bad", "星野ひかり", "", List.of(" ")), "blank aliases fail validation");
        expectFailure(() -> new StyleProfile("bad", "星野ひかり", "", List.of("x".repeat(61))), "long aliases fail validation");
        expectFailure(() -> new StyleProfile("bad", "星野ひかり", "", List.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")), "more than ten aliases fail validation");

        System.out.println("MemberResolverSelfTest PASS (" + checks + " checks)");
    }

    private static MemberResolver.Resolution resolve(List<ProbeModels.NodeRecord> nodes, List<StyleProfile> profiles) {
        return MemberResolver.resolve(nodes, VIEWPORT, profiles, false);
    }

    private static ProbeModels.NodeRecord title(String id, String text) {
        return node(id, null, 7, text, null, "android.widget.TextView", "pkg:id/title",
                new ProbeModels.Bounds(50, 40, 400, 120), true, false, false, false);
    }

    private static ProbeModels.NodeRecord body(String id, String text, int top) {
        return node(id, null, 7, text, null, "android.widget.TextView", null,
                new ProbeModels.Bounds(50, top, 700, top + 100), true, false, false, false);
    }

    private static ProbeModels.NodeRecord collectionBody(String id, String text, int top) {
        return node(id, null, 7, text, null, "android.widget.TextView", "pkg:id/title",
                new ProbeModels.Bounds(50, top, 700, top + 100), true, false, true, false);
    }

    private static ProbeModels.NodeRecord withVisibility(ProbeModels.NodeRecord source, boolean visible) {
        return node(source.id, source.parentId, source.windowId, source.rawText, source.rawDescription,
                source.className, source.viewId, source.screenBounds, visible, source.clickable,
                source.collectionItem, source.collection);
    }

    private static ProbeModels.NodeRecord withBounds(ProbeModels.NodeRecord source, ProbeModels.Bounds bounds) {
        return node(source.id, source.parentId, source.windowId, source.rawText, source.rawDescription,
                source.className, source.viewId, bounds, source.visible, source.clickable,
                source.collectionItem, source.collection);
    }

    private static ProbeModels.NodeRecord node(String id, String parentId, int windowId,
                                                String text, String description, String className,
                                                String viewId, ProbeModels.Bounds bounds, boolean visible,
                                                boolean clickable, boolean collectionItem, boolean collection) {
        return new ProbeModels.NodeRecord(id, parentId, 0, 0, windowId, text, description,
                className, viewId, bounds, null, visible, clickable, collectionItem, collection);
    }

    private static void assertResolution(MemberResolver.Resolution actual, MemberResolver.Status status,
                                         String key, String name, StyleProfile profile, String... nodeIds) {
        check(actual.status() == status, "status: expected " + status + ", got " + actual.status());
        check(java.util.Objects.equals(actual.identityKey(), key), "identity key mismatch");
        check(java.util.Objects.equals(actual.name(), name), "name mismatch");
        check(actual.profile() == profile, "profile mismatch");
        check(actual.nodeIds().equals(java.util.Set.of(nodeIds)), "node IDs mismatch: " + actual.nodeIds());
        expectFailure(() -> actual.nodeIds().add("mutate"), "resolution node IDs are immutable");
    }

    private static void expectFailure(Runnable action, String message) {
        boolean failed = false;
        try {
            action.run();
        } catch (IllegalArgumentException | UnsupportedOperationException expected) {
            failed = true;
        }
        check(failed, message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
