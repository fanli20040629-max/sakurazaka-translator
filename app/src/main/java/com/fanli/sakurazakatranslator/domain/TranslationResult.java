package com.fanli.sakurazakatranslator.domain;

import java.util.List;

public final class TranslationResult {
    public final List<String> translations;
    public final List<String> warnings;
    public final boolean accepted;

    public TranslationResult(List<String> translations, List<String> warnings, boolean accepted) {
        this.translations = List.copyOf(translations == null ? List.of() : translations);
        this.warnings = List.copyOf(warnings == null ? List.of() : warnings);
        this.accepted = accepted;
    }
}
