# 渐进式重构设计

## 最新增量：中文翻译试用版

当前 `0.5.0-translation-trial` / 6。复用下方候选结构，增加 MessageGrouper（只建议合并选中节点）、SymbolProtector、TranslationProtocol、DeepSeekProvider、TranslationRunner、TranslationSettings 和 TranslationPanel。结果 Item 带 ID，统一校验/重排；Service 只连接页面身份与面板回调。翻译面板只展示，Runner 管理唯一物理请求和迟到回调，Provider 只负责 HTTPS。

网络入口仅为确认区发送按钮；改选择/分组/页面使旧确认失效。模型/风格在准备时快照，Key 在发送时读取；设置页从取字范围排除。详见 [试用版计划](TRANSLATION_TRIAL_PLAN.md) 和 [交接](本轮优化验收与交接.md)。以下 0.4 章节为上一阶段设计，涉及“未联网/Provider 未接入”的状态已被本节更新。

更新时间：2026-09-17。当前版本为 `0.4.0-probe-candidates` / 5；实际验证见 [本轮优化验收与交接](本轮优化验收与交接.md)。

## 本轮原则

沿用现有工程，只拆出职责清楚且已使用的模块。不新增一套协调器、通用框架或备用生产实现。优先准确性约等于美观性，其次 token 消耗，再次速度。

## 当前实际结构

| 模块 | 唯一职责 | 不负责什么 |
| --- | --- | --- |
| TranslatorAccessibilityService | Android 窗口、前台身份、采集顺序、浮窗和图片资源所有权 | 不在其中整理候选原文、构造请求明细 |
| CaptureCoordinator / ResourceLease | 请求有效性与物理占用 / 图片释放时机 | 不持有 UI 或推断消息边界 |
| NodeTreeReader / OcrProcessor | 有限节点快照 / ML Kit 调用及片段转换 | 不调用翻译、不决定 UI 选择 |
| CandidateSelection | 同一 PageToken 的节点/OCR 候选、勾选、追加及清空 | 不持有 Android View/Bitmap |
| CandidatePanel | 节点/OCR 复选框、折叠信息和摘要 | 不启动采集、不独立管理页面状态 |
| TextAssembly / CandidateTextFormatter | 保守分类、组内位置排序、原文汇总 | 不猜 Emoji、不跨来源自动合并 |
| ConfirmedMessageFactory / TranslationRequestFactory | 把显式勾选转为带来源/风险/页面身份的本地请求 | 不证明消息分组已正确，也不授权联网 |

这些模块仍位于 capture 和 domain 中。当前不为一个面板建立新框架或 overlay 包；需要真正的译文定位时再按职责拆分。

## 数据流与不变量

1. 用户主动取字，生成 PageToken：requestId + 包名 + windowId + pageEpoch。
2. 节点读取后立即建立 CandidateSelection 和 CandidatePanel，默认不选。
3. 同一采集的截图与 OCR 异步完成；仅在 token 仍有效时追加 OCR，不重建节点选择。
4. 用户勾选生成本地 TranslationRequest；每项保留原文、四边界、坐标类别、来源和风险。
5. 切页、关闭、锁屏或配置变化后失效并清空；已启动的 OCR 仍等真实完成后释放其图片引用。

SCREEN（节点屏幕坐标）与 IMAGE（窗口截图坐标）尚无验证映射，所以先节点后 OCR、组内按位置排序。辅助节点单独折叠，人工可补选。不能因为文本相同就删除另一位置的片段，也不能用 OCR 覆盖节点原文。

图片释放顺序：先解除 ImageView 引用，再放弃预览租约；物理 OCR 完成时释放物理租约；两者均完成才回收 Bitmap。关闭不伪装成 ML Kit 已取消。

## 尚未实现

ChatMessage 当前仍表示一个确认片段，虽然保留完整片段矩形，但不等于完整聊天气泡。未知消息边界、节点描述、OCR 符号、来源混选等风险必须随请求传递。

自动正文区域、消息分组、坐标映射、翻译 Provider 的生产调用、两位偶像档案及相邻中文都待后续门槛。domain 中现有同步 Provider 等是未接生产的基础模型，不能直接在主线程联网。
