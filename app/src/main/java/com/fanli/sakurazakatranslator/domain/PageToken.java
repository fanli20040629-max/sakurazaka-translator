package com.fanli.sakurazakatranslator.domain;

/** Identity of one user-triggered capture, never an authorization to read a later page. */
public record PageToken(long requestId, String packageName, int windowId, long pageEpoch) {
    public PageToken {
        if (requestId <= 0 || packageName == null || packageName.isBlank()
                || windowId < 0 || pageEpoch < 0) throw new IllegalArgumentException("page identity");
    }
}
