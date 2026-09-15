package com.fanli.sakurazakatranslator.domain.style;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 可配置、带版本的个人文风，id 与 authorId 对应；不内置真实人物档案。
 * Blog 是背景材料，MSG 翻译主要用 msgGuidance；不得套用 Blog 的正式语气。
 * 每次确认修改应推进 version，使后续缓存/结果能区分不同版本。
 * 示例应为合成或经允许使用的材料，不把订阅聊天原文放入公共仓库。
 */
public record StyleProfile(String id, String version, String displayName, String chineseSummary,
                           List<String> generalRules, List<String> blogGuidance,
                           List<String> msgGuidance, Map<String, String> glossary, List<Example> examples) {
    public StyleProfile {
        if (id == null || id.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("风格 ID 和版本不能为空");
        }
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(chineseSummary, "chineseSummary");
        generalRules = List.copyOf(generalRules);
        blogGuidance = List.copyOf(blogGuidance);
        msgGuidance = List.copyOf(msgGuidance);
        glossary = Map.copyOf(glossary);
        examples = List.copyOf(examples);
    }

    public record Example(String japanese, String chinese) {
        public Example {
            Objects.requireNonNull(japanese, "japanese");
            Objects.requireNonNull(chinese, "chinese");
        }
    }
}
