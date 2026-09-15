package com.fanli.sakurazakatranslator.domain.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 检查结果与请求绑定、非空及符号。通过不等于中文语义正确，也不等于当前页面仍有效。 */
public final class TranslationValidator {
    private TranslationValidator() { }

    public enum Issue { NO_RESPONSE, REQUEST_MISMATCH, PAGE_MISMATCH, MESSAGE_MISMATCH,
        STYLE_MISMATCH, EMPTY_TEXT, SYMBOL_MISMATCH, PROVIDER_REVIEW }

    public static Validation validate(TranslationRequest request, TranslationResult result) {
        Objects.requireNonNull(request, "request");
        if (result == null) return new Validation(List.of(Issue.NO_RESPONSE));
        List<Issue> issues = new ArrayList<>();
        if (!request.requestId().equals(result.requestId())) issues.add(Issue.REQUEST_MISMATCH);
        if (!request.pageToken().equals(result.pageToken())) issues.add(Issue.PAGE_MISMATCH);
        if (!request.messageId().equals(result.messageId())) issues.add(Issue.MESSAGE_MISMATCH);
        if (!request.style().id().equals(result.styleProfileId())
                || !request.style().version().equals(result.styleProfileVersion())) issues.add(Issue.STYLE_MISMATCH);
        if (result.translatedText().isBlank()) issues.add(Issue.EMPTY_TEXT);
        var protectedText = SymbolProtector.protect(request.sourceText(), request.protectedLiterals());
        if (!SymbolProtector.validate(protectedText, result.translatedText()).isValid()) issues.add(Issue.SYMBOL_MISMATCH);
        if (result.needsReview()) issues.add(Issue.PROVIDER_REVIEW);
        return new Validation(issues);
    }

    public record Validation(List<Issue> issues) {
        public Validation {
            issues = List.copyOf(issues);
        }
        public boolean isValid() { return issues.isEmpty(); }
    }
}
