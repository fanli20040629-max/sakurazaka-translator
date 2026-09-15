package com.fanli.sakurazakatranslator.domain.model;

/** 屏幕绝对像素矩形，右/下边界不包含在内部。截图局部坐标须由 Android 适配层转换。 */
public record Bounds(int left, int top, int right, int bottom) {
    public Bounds {
        if (left < 0 || top < 0 || right <= left || bottom <= top) {
            throw new IllegalArgumentException("必须提供已裁剪到屏幕内的非空矩形");
        }
    }

    public boolean contains(Bounds other) {
        return other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom;
    }
}
