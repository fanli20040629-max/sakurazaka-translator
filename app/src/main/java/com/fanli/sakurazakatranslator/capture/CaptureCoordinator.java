package com.fanli.sakurazakatranslator.capture;

/** Pure state coordinator for one physical capture/OCR task. */
public final class CaptureCoordinator {
    public enum Physical { IDLE, NODES, SCREENSHOT, OCR }
    public enum ResultState { CLOSED, CURRENT, STALE, INVALID }

    private long sequence;
    private long requestId;
    private boolean logicalValid;
    private boolean closed;
    private Physical physical = Physical.IDLE;
    private ResultState resultState = ResultState.CLOSED;

    public synchronized long begin() {
        if (physical != Physical.IDLE) return -1;
        requestId = ++sequence;
        logicalValid = true;
        closed = false;
        resultState = ResultState.CLOSED;
        physical = Physical.NODES;
        return requestId;
    }

    public synchronized boolean isCurrent(long id) {
        return id == requestId && logicalValid && !closed;
    }

    public synchronized boolean advance(long id, Physical next) {
        if (!isCurrent(id) || next == Physical.IDLE) return false;
        physical = next;
        return true;
    }

    public synchronized void invalidate() {
        logicalValid = false;
        if (resultState != ResultState.INVALID) resultState = ResultState.STALE;
    }

    public synchronized void closeResult() {
        closed = true;
        logicalValid = false;
        resultState = ResultState.CLOSED;
    }

    public synchronized void finishPhysical(long id) {
        if (id == requestId) physical = Physical.IDLE;
    }

    public synchronized void show(long id) {
        if (isCurrent(id)) resultState = ResultState.CURRENT;
    }

    public synchronized Physical physical() { return physical; }
    public synchronized ResultState resultState() { return resultState; }
    public synchronized boolean isBusy() { return physical != Physical.IDLE; }
}
