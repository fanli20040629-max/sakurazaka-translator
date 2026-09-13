# 项目当前状态

更新时间：2026-09-12

## 最新执行入口：正文整理版探针（优先于下方历史状态）

- 本轮方案：[docs/PROBE_TEXT_OPTIMIZATION_HANDOFF.md](docs/PROBE_TEXT_OPTIMIZATION_HANDOFF.md)，对应主方案第 28.10 节。先完整阅读，按 M0～M5 实施；文末含可复制提示词和工期估算。
- 当前里程碑：方案写入完成，代码优化尚未启动。仍不开发翻译、API Key、缓存或后台自动识别。
- 已有证据：旧 APK 安装/启动成功；用户确认卡片稳定、可滚动和关闭；合成与部分目标页面截图/OCR 可用；目标节点截图保留了小花、爱心和换行。
- 已发现：OCR 漏符号/错字/错序，正文和辅助信息混杂，节点结构未保留，去重简单；生产生命周期测试不足，过宽事件过滤需回归。下方“已完整覆盖三种 OCR/销毁路径”等历史表述不作为当前验收结论。
- P0.5 未通过：目标长文/截断/重复样本、完整合成矩阵、页面切换/锁屏等仍有未验证项。短界面不替代短消息，多行短消息不替代长文。
- 目标包经设备前台证据确认：`jp.co.sonymusic.communication.sakurazaka`；ADB 曾在线后断开，实际连接须重查。历史“没有设备”不能推翻已经发生的安装和截图证据。
- 下一步：M0 核对差距，再 M1 节点保真；生命周期协调器和生产测试同步推进。复用旧签名和缓存，保留未跟踪 `test-result.png`。
- 规划：首个完整可复测 APK 约 6～12 小时有效工作；手机首轮另 30～60 分钟，必要修复另 2～6 小时；闭环按 1～3 天安排，外部等待及缺样本可能延长。这不是模型实测速率或完成承诺。

## 以下为历史环境与里程碑记录

保留用于追溯；其中“当前”“尚未安装”“尚未实施”等描述属于记录当时，以本页最新入口和新交接文档为准。

## 目标

开发 Sakurazaka 日文到简体中文翻译 Android 助手，先完成可安装、可验证的最小版本，再逐步加入取字、翻译卡片和设备测试。

## 当前工作区

- 项目目录：`D:\Projects\sakurazaka-translator`
- 现有方案：`sakurazaka_cli_development_plan.md`；第 21～28 节为唯一执行入口，已完成 2026-09-12 复核修订。
- Android 源码工程及本地 Git 仓库已建立；代码检查点 `30f00b1`，验证记录检查点 `5d07979`。

## 环境配置检查点

- Android Studio：已下载并通过 SHA-256 校验，已解压到 `D:\android_stufio\android-studio`。
- Git：已安装，路径为 `C:\Program Files\Git\cmd\git.exe`。
- Android 命令行工具：已解压并确认 `sdkmanager.bat --version` 可用。
- SDK、ADB、Build Tools：已完成并验证；Android 36、Build Tools 36.0.0、Platform-Tools/ADB 37.0.1 均可用。
- 用户环境变量：已设置 `ANDROID_HOME`、`ANDROID_SDK_ROOT`、`JAVA_HOME`，并将 SDK 工具目录加入用户 PATH。
- 配置过程曾因 Windows 批处理调用和 `JAVA_HOME` 继承问题失败；现已修复，最终状态为 READY。
- 已确认可用：Android Studio、Git 2.55.0、Android Studio 自带 Java 25.0.3。

## 规则与边界

- 不覆盖旧项目目录，不自动删除用户文件。
- 不把 API Key、聊天正文、敏感日志或构建产物提交到项目记录。
- 未经真实设备验证，不宣称目标 App、OCR、无障碍服务或性能已验证。
- 每个阶段保留可恢复检查点，并在继续前检查已完成输出，避免重复外部操作。

## 已完成里程碑

- P0 源码与 Android 工程已建立，Java 源码编译成功。
- P0 首次真实 APK 已打包成功：`app/build/outputs/apk/debug/app-debug.apk`。
- P0 APK SHA-256（已被后续 P0.5 产物 supersede）：`5C0E95237DE2EE28C70BB9C2948A46185865DF728128B20EBD90A830D174AAC4`。
- 已完成静态实现：无障碍服务声明、最小悬浮触发、节点文本遍历、API 34+ 内存截图复制、悬浮 ImageView 预览。
- P0.5 APK 已打包成功：同一路径，当前 SHA-256：`748FA4A1DA0DB638CD748FDC5FDA78CFCCA09DC6B9B15E6EDC67C00CB5A954D4`；静态检查确认包含 ML Kit 日文 OCR native pipeline，并包含内置日文合成样本页。
- 修复版 APK 已重新打包：53,344,138 字节；SHA-256：`32A890CA1EF356070B598C0F6600DA5677196A23D005CF75ED3ECA158697A8A8`；最终权限检查未发现 INTERNET 或 ACCESS_NETWORK_STATE。
- lint 修复后的最终 APK：53,681,339 字节；SHA-256：`FBCE49974E826F80E6EF915C4DFAD9474E1D6120400F3CB6387033784DA847DD`；确认包含 ML Kit 日文 OCR native pipeline，且最终 APK 无 INTERNET/ACCESS_NETWORK_STATE。
- 本地 Git 仓库已初始化；代码与文档恢复点为提交 `30f00b1 feat: add verified accessibility capture probe`，未配置远程、未推送。

## 当前未完成与下一步

开发代理交接指令：`docs/DEVELOPMENT_HANDOFF.md`。先按该文档读取和核实当前代码，再实施第 28.7 节 A1～A5；交接文档新增不代表修复已完成。

1. 当前为已构建且通过静态检查的探针，P0.5 未通过；最新执行入口为方案第 28.7 节。此前“R1～R5 已全部实现、只差手机”的描述过强，须先完成 A 阶段收尾。
2. 已通过 `ProbeLogicSelfTest`、Wrapper `assembleDebug`、`lintDebug`（0 error、1 个锁定版本提示）和 APK 最终权限/OCR 组件检查；APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
3. A 阶段待实施：取消与物理任务占用分离、销毁/迟到回调清理、页面身份和锁屏清理、显式合成模式、截图失败节点展示及完整诊断 UI。当前逻辑自测未覆盖这些全部行为。
4. 上次 ADB 无设备；A 完成后生成新包，B 完成安装及合成自测，C 核对目标 App 四类页面。运行截图、OCR 字级质量和正文验收均待验证。
5. C 门槛通过后实施 D（P1/P2/P3 正文整理和翻译），E（P4）再做扩展验收。具体预期结果和失败处理见第 28.7 节。

## 2026-09-12 A1～A5 实际实施检查点

- A1 已实现：逻辑失效与物理截图/OCR 占用分离；失效后立即拒绝旧结果，物理完成前拒绝新采集，迟到回调不能解除新任务占用。
- A2 已实现：HardwareBuffer 统一关闭；Bitmap 由生产 `ResourceLease` 管理，OCR 完成且预览解绑后恰好释放一次；服务销毁后不再启动 OCR 或添加悬浮窗，识别器延迟到在途任务完成后关闭。
- A3 已实现：结果使用 requestId、pageEpoch、包名和 windowId 校验；前台切换、同包页面事件、锁屏、中断会使旧结果失效；卡片内部滚动事件单独识别。
- A4 已实现：合成页必须由用户勾选“启用本页合成测试”后才允许显示探针；目标包只接受用户保存的前台包名，不回退到任意窗口。
- A5 已实现：截图失败展示可滚动节点正文和错误码；节点/OCR 均显示来源、边界、耗时；节点遍历上限会提示截断；卡片可滚动、拖动和关闭。
- 已运行：`verify-probe-logic.ps1`（PASS，覆盖取消重点击、旧回调、销毁/迟到截图模型、OCR 三种结束路径、锁屏/页面身份、卡片滚动和资源单次释放）；`:app:lintDebug`（0 error、1 个 Gradle 版本提示）；`:app:assembleDebug`（成功）；`git diff --check`（无差异错误）。
- 最新 APK：`D:\Projects\sakurazaka-translator\app\build\outputs\apk\debug\app-debug.apk`，versionName `0.1.0-probe`，versionCode `1`，大小 `53,683,519` 字节，SHA-256 `1B8C9B66EFE45189B05E382DFE54E2A7839D3C250B39E81062034B4E1A676799`。本次复测进一步过滤探针自身 `TYPE_WINDOW_STATE_CHANGED`，避免结果卡片被自身窗口事件立即清除；同时保留普通内容变化、悬浮窗窗口集合过滤和 Bitmap 引用先解除修复。

### 合成页真机复测反馈

- 已确认结果卡片稳定，可滑动并自主关闭；截图显示当前合成页，尺寸为 `1080×2340`。
- OCR 已识别图片内两句日文：`画像内だけの日本語です。`、`OCR で二行目も確認します。`。
- 中日混排页面中，日文模型对中文控件文本存在误识别；因此当前结论是截图通过、图片日文 OCR 通过、混排正文质量未完全通过，P0.5 尚不能整体宣称通过。
- 静态 APK 检查：包名 `com.fanli.sakurazakatranslator`；仅有应用自身动态接收器权限；无 `INTERNET`、无 `ACCESS_NETWORK_STATE`；签名 v2 verified；包含 `libmlkit_google_ocr_pipeline.so` 和 `Jpan_ctc` 模型资源。
- 尚未验证：ADB 当前无设备；安装、无障碍授权、真实截图、OCR 正文质量、合成真机验收和目标 App 四类页面均未完成。P0.5 仍不可宣称通过。
