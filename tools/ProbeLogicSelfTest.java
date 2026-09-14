package com.fanli.sakurazakatranslator.capture;

public final class ProbeLogicSelfTest {
    public static void main(String[] args) {
        checkAssembly();
        checkCoordinator();
        ProbeLogic.RequestGate gate = new ProbeLogic.RequestGate();
        long first = gate.begin();
        check(first > 0, "first request must start");
        check(gate.begin() == -1, "parallel request must be rejected");
        check(gate.isCurrent(first), "first request must be current");
        gate.invalidate();
        check(!gate.isCurrent(first), "invalidated callback must be stale");
        check(gate.isBusy(), "physical task must keep the gate occupied after invalidation");
        check(gate.begin() == -1, "new capture must wait for the physical task");
        gate.finishPhysical(first);
        long second = gate.begin();
        check(second > first, "second request must start after physical completion");
        gate.finishPhysical(first);
        check(gate.isCurrent(second), "old completion must not finish new request");
        gate.finishPhysical(second);
        check(gate.isCurrent(second), "physical completion must not invalidate a valid result");
        gate.invalidate();
        check(!gate.isCurrent(second), "explicit invalidation must reject the result");

        check(ProbeLogic.samePage("a", 7, 3, "a", 7, 3), "same page token must match");
        check(!ProbeLogic.samePage("a", 7, 3, "a", 8, 3), "window change must invalidate page");
        check(!ProbeLogic.samePage("a", 7, 3, "a", 7, 4), "epoch change must invalidate page");
        check(!ProbeLogic.resultAllowed("a", 7, 3, "a", 7, 3, true),
                "locked device must reject a result");
        check(ProbeLogic.isOverlayScrollEvent(true, true, true),
                "card scroll must be recognized as an overlay event");
        check(!ProbeLogic.isOverlayScrollEvent(true, true, false),
                "target scroll must not be treated as a card event");

        checkLease("OCR success", 1);
        checkLease("OCR failure", 1);
        checkLease("OCR startup exception", 1);
        System.out.println("Lifecycle cases: cancel/reclick, stale callback, destroy/late screenshot,");
        System.out.println("OCR success/failure/start exception, lock/page identity, card scroll, release-once");

        String sameA = ProbeLogic.positionKey("重复", 0, 0, 20, 20);
        String sameB = ProbeLogic.positionKey("重复", 0, 0, 20, 20);
        String otherPosition = ProbeLogic.positionKey("重复", 0, 30, 20, 50);
        check(sameA.equals(sameB), "same text at same bounds must deduplicate");
        check(!sameA.equals(otherPosition), "same text at different bounds must remain");
        System.out.println("ProbeLogicSelfTest PASS");
    }

    private static void checkAssembly() {
        java.util.List<com.fanli.sakurazakatranslator.capture.ProbeModels.NodeRecord> nodes =
                new java.util.ArrayList<>();
        com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds first =
                new com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds(0, 10, 100, 40);
        nodes.add(new com.fanli.sakurazakatranslator.capture.ProbeModels.NodeRecord(
                "n1", null, 0, 0, 7, "花♡(笑)\n二行目", "花♡(笑)\n二行目",
                "android.widget.TextView", null, first, first, true, false, false));
        nodes.add(new com.fanli.sakurazakatranslator.capture.ProbeModels.NodeRecord(
                "n2", null, 1, 1, 7, "同じ文", null, "android.widget.TextView", null,
                new com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds(0, 50, 100, 80),
                null, true, false, false));
        nodes.add(new com.fanli.sakurazakatranslator.capture.ProbeModels.NodeRecord(
                "n3", null, 2, 2, 7, "同じ文", null, "android.widget.TextView", null,
                new com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds(0, 90, 100, 120),
                null, true, false, false));
        nodes.add(new com.fanli.sakurazakatranslator.capture.ProbeModels.NodeRecord(
                "n4", null, 3, 3, 7, null, "小田倉 麗奈 9/13 19:34",
                "android.widget.TextView", null,
                new com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds(0, 130, 100, 150),
                null, true, false, false));
        nodes.add(new com.fanli.sakurazakatranslator.capture.ProbeModels.NodeRecord(
                "n5", null, 4, 4, 7, "00:25", null, "android.widget.TextView", null,
                new com.fanli.sakurazakatranslator.capture.ProbeModels.Bounds(0, 160, 100, 180),
                null, true, false, false));
        java.util.List<com.fanli.sakurazakatranslator.capture.ProbeModels.TextFragment> fragments =
                com.fanli.sakurazakatranslator.capture.TextAssembly.fromNodes(nodes);
        check(fragments.size() == 5,
                "assembly merges identical same-node fields and keeps positional repeats");
        check(fragments.get(0).rawText.equals("花♡(笑)\n二行目"), "assembly preserves raw symbols and newline");
        check(fragments.get(0).provenance.size() == 2,
                "same text and description keep both provenance entries");
        check(fragments.get(0).role == com.fanli.sakurazakatranslator.capture.ProbeModels.Role.BODY,
                "ordinary text node is a body candidate");
        check(fragments.get(3).role == com.fanli.sakurazakatranslator.capture.ProbeModels.Role.METADATA,
                "single-line author/date/time is separated as metadata");
        check(fragments.get(4).role == com.fanli.sakurazakatranslator.capture.ProbeModels.Role.METADATA,
                "voice duration is separated as metadata");
        String reading = com.fanli.sakurazakatranslator.capture.TextAssembly
                .formatNodeSections(fragments);
        check(reading.contains("正文候选\n花♡(笑)\n二行目"),
                "reading view keeps body symbols and line breaks");
        check(reading.contains("作者/时间等信息（待核对）"),
                "reading view labels metadata conservatively");
        check(!reading.contains("0,10,100,40") && !reading.contains("NODE_TEXT"),
                "reading view keeps coordinates and source IDs out of the body area");
    }

    private static void checkCoordinator() {
        com.fanli.sakurazakatranslator.capture.CaptureCoordinator coordinator =
                new com.fanli.sakurazakatranslator.capture.CaptureCoordinator();
        long id = coordinator.begin();
        check(id > 0 && coordinator.isBusy(), "coordinator starts node task");
        check(coordinator.advance(id, com.fanli.sakurazakatranslator.capture.CaptureCoordinator.Physical.OCR),
                "coordinator advances physical task");
        coordinator.invalidate();
        check(!coordinator.isCurrent(id) && coordinator.isBusy(), "invalid result keeps physical task busy");
        check(coordinator.begin() == -1, "coordinator rejects overlap after invalidation");
        coordinator.finishPhysical(id);
        check(!coordinator.isBusy(), "coordinator releases after physical completion");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkLease(String name, int expectedReleases) {
        final int[] releases = {0};
        ProbeLogic.ResourceLease lease = new ProbeLogic.ResourceLease(() -> releases[0]++);
        lease.detachPreview();
        lease.detachPreview();
        check(releases[0] == 0, name + ": preview detach alone must not release");
        lease.finishPhysical();
        lease.finishPhysical();
        check(releases[0] == expectedReleases,
                name + ": resource must release exactly once");
    }
}
