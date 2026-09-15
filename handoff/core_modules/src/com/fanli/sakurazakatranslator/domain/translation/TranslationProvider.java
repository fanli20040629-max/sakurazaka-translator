package com.fanli.sakurazakatranslator.domain.translation;

import java.util.concurrent.CompletionStage;

/**
 * 厂商无关的异步文本翻译边界。实现方不得在调用线程执行阻塞网络操作。
 * 超时/鉴权/限流等传输错误以异常完成 Stage；结果格式和语气符号另由本地校验。
 * 此接口不重试、不读取密钥、不上传图片，也不判断当前屏幕。
 * Stage 的取消不保证底层 HTTP 停止；Android 接入时总协调器还必须持有可取消请求句柄。
 */
public interface TranslationProvider {
    CompletionStage<TranslationResult> translate(TranslationRequest request);
}
