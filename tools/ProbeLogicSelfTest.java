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
        long second = gate.begin();
        gate.complete(first);
        check(gate.isCurrent(second), "old completion must not finish new request");
        gate.complete(second);
        check(!gate.isCurrent(second), "completed request must no longer be current");

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
}
