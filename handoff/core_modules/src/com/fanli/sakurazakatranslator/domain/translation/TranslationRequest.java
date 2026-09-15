package com.fanli.sakurazakatranslator.domain.translation;

import com.fanli.sakurazakatranslator.domain.model.PageToken;
import com.fanli.sakurazakatranslator.domain.style.StyleProfile;
import java.util.List;
import java.util.Objects;

/**
 * 一条消息的文本请求。API Key、图片和整篇 Blog 均不属于这个对象。
 * requestId 每次请求（含重试）唯一；上层把正文作为数据传给模型，不当作开发指令。
 */
public record TranslationRequest(String requestId, PageToken pageToken, String messageId,
                                 String authorId, String sourceText, StyleProfile style,
                                 List<String> protectedLiterals) {
    public TranslationRequest {
        Objects.requireNonNull(pageToken, "pageToken");
        Objects.requireNonNull(style, "style");
        if (requestId == null || requestId.isBlank() || messageId == null || messageId.isBlank()
                || sourceText == null || sourceText.isBlank() || !style.id().equals(authorId)) {
            throw new IllegalArgumentException("请求、消息、正文及人物风格必须有效且一致");
        }
        protectedLiterals = List.copyOf(protectedLiterals);
        SymbolProtector.protect(sourceText, protectedLiterals);
    }

    public String targetLanguage() { return "zh-Hans"; }
}
