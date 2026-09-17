package com.fanli.sakurazakatranslator.domain;

import java.util.List;

/** One selected text item. It may be an OCR line rather than a whole chat bubble. */
public final class ChatMessage {
    public final String id;
    public final String originalText;
    public final String source;
    public final int top;
    public final int left;
    public final int right;
    public final int bottom;
    public final String coordinateSpace;
    public final List<String> provenance;
    public final List<String> warnings;

    public ChatMessage(String id, String originalText, String source,
                       int left, int top, int right, int bottom, String coordinateSpace,
                       List<String> provenance, List<String> warnings) {
        this.id = require(id, "id");
        this.originalText = require(originalText, "originalText");
        this.source = source == null ? "unknown" : source;
        this.top = top;
        this.left = left;
        this.right = right;
        this.bottom = bottom;
        this.coordinateSpace = require(coordinateSpace, "coordinateSpace");
        this.provenance = List.copyOf(provenance);
        this.warnings = List.copyOf(warnings);
    }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
        return value;
    }
}
