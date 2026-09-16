# 技术决策

## 2026-09-16：本轮重构与验证边界

- 生产协调器统一为 CaptureCoordinator；旧 RequestGate 仅保留在历史自测，不并行执行任务。
- 节点读取、ML Kit适配和候选状态分别放入 NodeTreeReader、OcrProcessor、OcrSelection；页面校验和资源生命周期仍由 Service 负责。
- 所选 OCR 是片段，不冒充完整消息；时间/作者仍需手动排除；只建立本地请求预览。
- 七组纯 Java 测试通过，Android 编译/lint/APK/真机尚未验证。详细证据与后续规划见 [当前代码审核与改进方案](当前代码审核与改进方案.md)。下方决策保留为历史。

## 2026-09-13：正文整理版方案 2.0（待实施）

- 执行入口统一为 [PROBE_TEXT_OPTIMIZATION_HANDOFF.md](PROBE_TEXT_OPTIMIZATION_HANDOFF.md)。先完成协调器和结构节点，再分类、OCR 对照、UI 与完整验证；测试从 M1 同步推进。
- Tasks 取消不触发失败监听，采用非 Activity 绑定的唯一完成入口处理成功/失败/取消，另处理同步启动异常；这是根据 [Task 官方文档](https://developers.google.com/android/reference/com/google/android/gms/tasks/Task) 对旧方案的修订，尚未改代码。
- Android 33 起节点 recycle 无实际作用。按 [节点官方文档](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo) 管理短期引用，保存屏幕/窗口坐标类别；不再以补 recycle 次数作为修复目标。
- SDK 36 没有公开 View.getAccessibilityWindowId()。候选方案为 attach 后从自有 View 的节点获取窗口 ID 并核对 overlay 类型，真机行为待验证；不调用隐藏 API。
- 无结构证据使用可见 UNKNOWN 和手选；无坐标/归属证据保留独立 OCR 候选并关闭自动融合。手动回退与自动正文验收分开。
- 复用 Java 17、单模块、原生 View、锁定依赖、现有调试签名；不开发翻译/API Key/缓存，不新增后台正文采集。

下方为历史决策及当时证据；“真机证据尚不存在”等旧描述不覆盖当前证据汇总。

## 2026-09-12：先做取字探针

目标 App 的正文节点、自绘方式、Android 16 敏感数据保护和安全截图状态尚未实测。探针先提供最小悬浮触发、节点读取、指定窗口截图和日文 OCR，避免在核心路径未确认前投入翻译和复杂 UI。

## 2026-09-12：本地内存处理截图

截图只用于当前 OCR，代码将硬件缓冲转换为软件 Bitmap 后释放原始资源，不落盘、不上传。取消和异步完成路径仍需在真机阶段检查资源生命周期。

## 2026-09-12：探针 A1～A5 收尾实现

- 采用生产 `RequestGate` 分离逻辑有效性和物理占用；采用生产 `ResourceLease` 约束预览引用与 OCR 完成的 Bitmap 所有权，避免用超时或清空 busy 冒充取消。
- 使用 requestId、pageEpoch、目标包名和 windowId 组成页面身份；锁屏、前台变化和目标页面事件使旧结果失效，卡片滚动通过来源边界单独分流。
- 合成模式默认关闭，必须由用户在本应用内显式开启；目标包名只保存用户确认的前台证据，不猜测包名。
- 诊断结果保留节点来源/边界、OCR 文本块边界、耗时和错误码；截图失败时仍展示节点正文，节点遍历达到限制时明确提示截断。
- 桌面证据已完成，真机证据尚不存在；不得将当前 APK、lint 或逻辑测试等同于 OCR 正文和目标 App 验收通过。
