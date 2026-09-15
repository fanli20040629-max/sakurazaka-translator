package com.fanli.sakurazakatranslator.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 一次采集中一条消息的不可变快照，ID 只在 pageToken 范围内有意义。
 * 未知作者/时间用空字符串；不得猜测。时间保留原显示格式，未强行解析时区。
 * UI 状态由唯一协调器维护，不在此处复制第二份。图片区域只有坐标，不持有 Bitmap。
 */
public record ChatMessage(PageToken pageToken, String id, String authorId, String authorName,
                          String timestamp, Bounds bounds, MediaType mediaType,
                          List<TextFragment> fragments, List<Bounds> imageRegions,
                          float sourceConfidence, boolean boundaryConfirmed) {
    public enum MediaType { TEXT, IMAGE, VOICE, MIXED, UNKNOWN }

    public ChatMessage {
        Objects.requireNonNull(pageToken, "pageToken");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("消息 ID 不能为空");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(mediaType, "mediaType");
        fragments = List.copyOf(fragments);
        imageRegions = List.copyOf(imageRegions);
        for (Bounds region : imageRegions) {
            if (!bounds.contains(region)) throw new IllegalArgumentException("图片区域不属于该消息");
        }
        if (!Float.isFinite(sourceConfidence)
                || (sourceConfidence != -1 && (sourceConfidence < 0 || sourceConfidence > 1))) {
            throw new IllegalArgumentException("置信度必须为 -1 或 0..1 的有限数");
        }
    }
}
