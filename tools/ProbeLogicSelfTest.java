package com.fanli.sakurazakatranslator.capture;

public final class ProbeLogicSelfTest {
    public static void main(String[] args) {
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
