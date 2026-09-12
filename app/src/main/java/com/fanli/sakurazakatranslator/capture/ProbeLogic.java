package com.fanli.sakurazakatranslator.capture;

public final class ProbeLogic {
    private ProbeLogic() { }

    public static String positionKey(String text, int left, int top, int right, int bottom) {
        return text + '|' + left + ',' + top + ',' + right + ',' + bottom;
    }

    public static final class RequestGate {
        private long sequence;
        private long active;
        private boolean busy;

        public synchronized long begin() {
            if (busy) return -1;
            busy = true;
            active = ++sequence;
            return active;
        }

        public synchronized boolean isCurrent(long requestId) {
            return busy && requestId == active;
        }

        public synchronized void complete(long requestId) {
            if (requestId == active) busy = false;
        }

        public synchronized void invalidate() {
            active = ++sequence;
            busy = false;
        }
    }
}
