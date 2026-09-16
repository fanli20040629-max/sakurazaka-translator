# 渐进式重构执行计划

更新时间：2026-09-16。实际验证范围见 [本轮优化验收与交接](本轮优化验收与交接.md)。

- [x] domain 模型与 TranslationRequestFactory 接入请求预览（仍是片段，消息分组未实现）。
- [x] CaptureCoordinator 接入生产并限制阶段流转；ResourceLease 保持单次释放策略。
- [x] NodeTreeReader 与 OcrProcessor 抽离；主服务保留页面有效性和资源所有权。
- [x] OcrSelection 统一候选/勾选状态；按坐标稳定排序；关闭清空；界面明确片段不是消息。
- [x] 删除未调用的旧节点拼接和无用导入；DomainSelfTest 移至 tools。
- [x] Mac/Windows 共用测试清单；Mac 执行七组 PASS；七项新增回归先复现失败再修复。
- [x] 修正构建中的机器专用路径与缺失调试签名文件处理，待 Gradle 验证。
- [ ] 在原电脑运行 PowerShell 自测、Android 编译、lint 和 assembleDebug，记录新 APK。
- [ ] 真机验证浮窗、取字、勾选、关闭、锁屏和迟到回调，不能用纯模型测试代替。
- [ ] 基于真机证据做消息类型/边界、局部 OCR、符号校验、网络和中文显示；具体门槛见审核报告。

当前不继续扩大浮窗重构；先用新包确认此次节点/OCR拆分保持行为，再抽离窗口解析和浮窗控制器。
