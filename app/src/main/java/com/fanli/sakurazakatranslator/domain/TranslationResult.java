package com.fanli.sakurazakatranslator.domain;

import java.util.List;

public final class TranslationResult {
    public record Item(String id, String text) {
        public Item {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(text);
        }
    }

    public final List<Item> translations;
    public final List<String> warnings;
    public final boolean accepted;

    public TranslationResult(List<Item> translations, List<String> warnings, boolean accepted) {
        this.translations = List.copyOf(translations == null ? List.of() : translations);
        this.warnings = List.copyOf(warnings == null ? List.of() : warnings);
        this.accepted = accepted;
    }
}
