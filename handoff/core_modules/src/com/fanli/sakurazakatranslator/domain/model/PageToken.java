package com.fanli.sakurazakatranslator.domain.model;

/**
 * 一次采集的页面身份。pageEpoch 在切页/切人物/滚动导致布局失效时递增；
 * captureTime 使用单调时钟，同毫秒截图须由调用方推进序号保证不重复。
 * 相等仅表示两份数据来自同次采集；是否仍是当前页面由唯一协调器决定。
 */
public record PageToken(String targetPackage, int windowId, long pageEpoch, long captureTime) {
    public PageToken {
        if (targetPackage == null || targetPackage.isBlank() || windowId < 0 || pageEpoch < 0 || captureTime < 0) {
            throw new IllegalArgumentException("页面身份不完整");
        }
    }
}
