package com.fanli.sakurazakatranslator.domain.model;

import java.util.Objects;

/**
 * 不可变文字片段；rawText 不做自动规范化，Emoji 可与正文共存在一段。
 * confidence=-1 表示来源未提供置信度，不能用 0.9 等常数冒充 OCR 测量结果。
 * BODY/selected 必须由已验证的节点规则或用户选择确定，不因含日文就自动通过。
 */
public record TextFragment(String id, String rawText, Source source, Role role, Bounds bounds,
                           int traversalIndex, float confidence, boolean selected) {
    public enum Source { NODE_TEXT, NODE_DESCRIPTION, OCR, VISION }
    public enum Role { BODY, METADATA, CONTROL, MEDIA_CANDIDATE, UNKNOWN }

    public TextFragment {
        if (id == null || id.isBlank() || traversalIndex < 0) throw new IllegalArgumentException("片段身份非法");
        Objects.requireNonNull(rawText, "rawText");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(bounds, "bounds");
        if (!Float.isFinite(confidence) || (confidence != -1 && (confidence < 0 || confidence > 1))) {
            throw new IllegalArgumentException("置信度必须为 -1 或 0..1 的有限数");
        }
    }
}
