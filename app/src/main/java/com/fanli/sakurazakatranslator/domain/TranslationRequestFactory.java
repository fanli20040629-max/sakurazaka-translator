package com.fanli.sakurazakatranslator.domain;

import java.util.List;

/** Builds a local request from an explicit selection; callers own consent and page validity. */
public final class TranslationRequestFactory {
    private TranslationRequestFactory() { }

    public static TranslationRequest confirmed(List<ChatMessage> messages, StyleProfile style) {
        return new TranslationRequest(messages, style, true);
    }
}
