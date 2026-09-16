package com.fanli.sakurazakatranslator.domain;

/** One selected text item. It may be an OCR line rather than a whole chat bubble. */
public final class ChatMessage {
    public final String id;
    public final String originalText;
    public final String source;
    public final int top;
    public final int left;

    public ChatMessage(String id, String originalText, String source, int top, int left) {
        this.id = require(id, "id");
        this.originalText = require(originalText, "originalText");
        this.source = source == null ? "unknown" : source;
        this.top = top;
        this.left = left;
    }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
        return value;
    }
}
