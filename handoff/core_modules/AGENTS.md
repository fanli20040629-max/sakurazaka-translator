# 核心模块维护规则

本文件只规定本接入包的开发约束。用户当前指令和工具权限优先；不要用这里的规则绕过它们。

- 开始前读 README.md、docs/模块地图与规则.md 和 docs/当前状态与验收.md。
- 主工程是另一台笔记本上的实际 Git 仓库；审核快照只能作历史参考。
- 不批量覆盖主工程 AGENTS.md、Gradle 文件、AndroidManifest.xml 或 Service。
- 一项变更包含实现、调用方、相应测试和文档；不要机械地每次只改一个文件，导致接口与调用方不一致。
- 保持 Java 17 语言级别。这里的 domain 仅依赖 Java 标准库，不导入 Android、HTTP SDK、数据库或 UI。
- 使用不可变输入/输出；注释解释职责、单位、异常与限制，不重复显而易见的代码。
- 总生命周期只有一个负责人。以现有 CaptureCoordinator 为迁移起点，不另建并列 RequestGate/TranslationCoordinator。
- 不得将 ProbeModels.Role.BODY 候选直接等同于已确认的聊天正文。
- 不自动猜消息边界、OCR 置信度、作者或日语语义；不把模型自评分当客观准确率。
- 当前 SymbolProtector 是本地校验器，不是图片识别器、全文日语检查器或完整 Unicode 颜文字识别器。
- 不打印模型对象的 toString：其中可能有正文、文风示例等私人内容。生产日志只记录脱敏错误码和耗时。
- 不提交密钥、签名文件、local.properties、真实截图、订阅聊天或私人 Blog 数据。
- 不预设 DeepSeek 模型具有图像能力。真实接入时查厂商最新官方文档和账户可用能力。
- 先写能复现问题的测试，再修复；运行 scripts/verify-core.sh 或 verify-core.ps1。迁移后必须指向生产 domain 目录测试。
- 每项交付分别记录：代码完成、主工程接入、JVM 测试、APK 构建、真机验收。禁止合并成一个“完成”。
- 删除旧实现前搜索所有调用；旧实现未退役期间不得与新实现并行触发 OCR/网络请求。
