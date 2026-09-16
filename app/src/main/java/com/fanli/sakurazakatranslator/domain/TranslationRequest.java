package com.fanli.sakurazakatranslator.domain;

import java.util.HashSet;
import java.util.List;

public final class TranslationRequest {
    public final List<ChatMessage> messages;
    public final StyleProfile style;
    public final boolean userConfirmed;

    public TranslationRequest(List<ChatMessage> messages, StyleProfile style,
                              boolean userConfirmed) {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("messages");
        this.messages = List.copyOf(messages);
        var ids = new HashSet<String>();
        for (ChatMessage message : this.messages) {
            if (!ids.add(message.id)) throw new IllegalArgumentException("Duplicate message id");
        }
        this.style = style;
        this.userConfirmed = userConfirmed;
    }
}
