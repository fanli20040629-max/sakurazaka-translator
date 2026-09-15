package com.fanli.sakurazakatranslator.domain.translation;

import com.fanli.sakurazakatranslator.domain.model.PageToken;
import java.util.Objects;

/**
 * 翻译返回值。绑定字段由客户端保留的请求上下文提供，模型返回 ID 也须单独核对。
 * 不提供伪造的“翻译置信度”：语义正确性不能由模型自报分数证明。
 * needsReview 为提供商/识别链路的待确认标志，不能替代本地校验。
 */
public record TranslationResult(String requestId, PageToken pageToken, String messageId,
                                String translatedText, String providerName, String modelName,
                                String styleProfileId, String styleProfileVersion,
                                boolean needsReview, long createdAtEpochMillis) {
    public TranslationResult {
        Objects.requireNonNull(pageToken, "pageToken");
        Objects.requireNonNull(translatedText, "translatedText");
        for (String value : new String[]{requestId, messageId, providerName, modelName,
                styleProfileId, styleProfileVersion}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("结果身份不完整");
        }
        if (createdAtEpochMillis < 0) throw new IllegalArgumentException("结果时间非法");
    }
}
