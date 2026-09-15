package com.fanli.sakurazakatranslator.domain.assembly;

import com.fanli.sakurazakatranslator.domain.model.ChatMessage;
import com.fanli.sakurazakatranslator.domain.model.TextFragment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 整理一条已确认边界的消息，不从整屏文字猜消息归属。
 * 片段应是完整逻辑行/块，不是单字符坐标；不同片段以换行分隔，原片段不 trim。
 * 相同文字出现在不同消息/位置时保留；仅完全相同的片段 ID 和数据去重。
 */
public final class MessageAssembler {
    private MessageAssembler() { }

    public static AssembledMessage assemble(ChatMessage message) {
        if (!message.boundaryConfirmed()) throw new IllegalStateException("消息边界待确认");
        var unique = new LinkedHashMap<String, TextFragment>();
        for (TextFragment fragment : message.fragments()) {
            TextFragment previous = unique.putIfAbsent(fragment.id(), fragment);
            if (previous != null && !previous.equals(fragment)) {
                throw new IllegalArgumentException("同一片段 ID 对应不同数据");
            }
        }
        List<TextFragment> body = new ArrayList<>();
        for (TextFragment fragment : unique.values()) {
            if (fragment.selected() && fragment.role() == TextFragment.Role.BODY
                    && !fragment.rawText().isBlank() && message.bounds().contains(fragment.bounds())) {
                body.add(fragment);
            }
        }
        body.sort(Comparator.comparingInt((TextFragment f) -> f.bounds().top())
                .thenComparingInt(f -> f.bounds().left())
                .thenComparingInt(TextFragment::traversalIndex));
        String text = String.join("\n", body.stream().map(TextFragment::rawText).toList());
        return new AssembledMessage(message, body, text);
    }

    /** 空 sourceText 表示没有获准翻译的正文，调用方不得因此上传图片或调用 API。 */
    public record AssembledMessage(ChatMessage message, List<TextFragment> body, String sourceText) {
        public AssembledMessage {
            body = List.copyOf(body);
        }
    }
}
