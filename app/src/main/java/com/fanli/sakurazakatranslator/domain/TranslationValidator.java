package com.fanli.sakurazakatranslator.domain;

import java.util.ArrayList;
import java.util.List;

/** Basic safety checks run after a provider returns and before UI display. */
public final class TranslationValidator {
    private TranslationValidator() { }

    public static TranslationResult validate(TranslationRequest request, TranslationResult result) {
        List<String> warnings = new ArrayList<>();
        if (result != null && result.warnings != null) warnings.addAll(result.warnings);
        if (request == null) {
            warnings.add("REQUEST_MISSING");
            return new TranslationResult(List.of(), warnings, false);
        }
        if (!request.userConfirmed) warnings.add("INPUT_NOT_CONFIRMED");
        if (result == null) {
            warnings.add("RESULT_MISSING");
            return new TranslationResult(List.of(), warnings, false);
        }
        if (result.translations.size() != request.messages.size()) warnings.add("MESSAGE_COUNT_MISMATCH");
        if (result.translations.stream().anyMatch(String::isBlank)) warnings.add("EMPTY_TRANSLATION");
        return new TranslationResult(result.translations, warnings, warnings.isEmpty() && result.accepted);
    }
}
