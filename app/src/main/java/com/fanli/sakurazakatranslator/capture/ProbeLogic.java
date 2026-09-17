package com.fanli.sakurazakatranslator.capture;

public final class ProbeLogic {
    private ProbeLogic() { }

    public static String positionKey(String text, int left, int top, int right, int bottom) {
        return text + '|' + left + ',' + top + ',' + right + ',' + bottom;
    }

    public static boolean samePage(String expectedPackage, int expectedWindowId, long expectedEpoch,
                                   String actualPackage, int actualWindowId, long actualEpoch) {
        return expectedPackage != null && expectedPackage.equals(actualPackage)
                && expectedWindowId == actualWindowId && expectedEpoch == actualEpoch;
    }

    public static boolean resultAllowed(String expectedPackage, int expectedWindowId,
                                        long expectedEpoch, String actualPackage,
                                        int actualWindowId, long actualEpoch, boolean locked) {
        return !locked && samePage(expectedPackage, expectedWindowId, expectedEpoch,
                actualPackage, actualWindowId, actualEpoch);
    }

    public static boolean isOwnedWindowEvent(boolean samePackage, int eventWindowId,
                                            int previewWindowId, int triggerWindowId) {
        return samePackage && eventWindowId >= 0
                && (eventWindowId == previewWindowId || eventWindowId == triggerWindowId);
    }

    public static final class RequestGate {
        private long sequence;
        private long logicalActive;
        private long physicalActive;
        private boolean busy;
        private boolean logicalValid;

        public synchronized long begin() {
            if (busy) return -1;
            busy = true;
            logicalActive = ++sequence;
            physicalActive = logicalActive;
            logicalValid = true;
            return logicalActive;
        }

        public synchronized boolean isCurrent(long requestId) {
            return logicalValid && requestId == logicalActive;
        }

        public synchronized void finishPhysical(long requestId) {
            if (requestId == physicalActive) {
                physicalActive = 0;
                busy = false;
            }
        }

        public synchronized void invalidate() {
            logicalActive = ++sequence;
            logicalValid = false;
            // Invalidation rejects the result immediately, but the physical
            // screenshot/OCR still owns the single-task slot until it finishes.
        }

        public synchronized boolean isBusy() {
            return busy;
        }
    }

    public static final class ResourceLease {
        private final Runnable releaseAction;
        private boolean previewAttached = true;
        private boolean physicalFinished;
        private boolean released;

        public ResourceLease(Runnable releaseAction) {
            this.releaseAction = releaseAction;
        }

        public synchronized void detachPreview() {
            previewAttached = false;
            releaseIfReady();
        }

        public synchronized void finishPhysical() {
            physicalFinished = true;
            releaseIfReady();
        }

        private void releaseIfReady() {
            if (!released && !previewAttached && physicalFinished) {
                released = true;
                releaseAction.run();
            }
        }
    }
}
