package com.fanli.sakurazakatranslator.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import com.fanli.sakurazakatranslator.translation.SymbolProtector;

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
        var byId = new HashMap<String, TranslationResult.Item>();
        for (var item : result.translations) {
            if (byId.put(item.id(), item) != null) warnings.add("DUPLICATE_MESSAGE_ID");
            if (item.text().isBlank()) warnings.add("EMPTY_TRANSLATION");
        }
        List<TranslationResult.Item> ordered = new ArrayList<>();
        for (var message : request.messages) {
            var item = byId.remove(message.id);
            if (item == null) {
                warnings.add("MESSAGE_ID_MISSING");
            } else {
                if (!SymbolProtector.sameSymbols(message.originalText, item.text())) {
                    warnings.add("SYMBOL_MISMATCH");
                }
                ordered.add(item);
            }
        }
        if (!byId.isEmpty()) warnings.add("UNKNOWN_MESSAGE_ID");
        return new TranslationResult(ordered, warnings, warnings.isEmpty() && result.accepted);
    }
}
