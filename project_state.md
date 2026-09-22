# 项目当前状态

## 2026-09-22 阅读卡与长文补取（当前开发）

源码为 8 / `0.7.0-reading-card-trial`，分支 `codex/chat-bubble-layout`，基线 `fca8f3c` 上的本地修改尚未提交/推送。已接入单个阅读卡、保守正文推荐、手动多屏补取、默认关闭的快捷翻译、成员姓名匹配风格和本地虚构演示；不增加依赖，不替换签名。16 组逻辑测试、9 项模拟协议/HTTP 测试及 Android API 签名类型检查通过；当前版本的 Gradle/lint/APK/真机/真实 API 未执行。详见 [实施与验收](docs/2026-09-22翻译卡实施与验收.md) 与 [执行记录](docs/优化执行记录.md)。

## 2026-09-21 自动分组与附近显示（历史）

复审已补齐未知/媒体、嵌套列表和无关侧栏时间的分组边界，整组选择改为批量，入口拖动时恢复完整卡片。版本 7 后续已取得 Windows 构建、同签名安装和部分手机操作证据，完整 G/B 验收未完成。详见 [设备记录](docs/DEVICE_TESTS.md)；以下“待验证”为交付当时的状态。

源码 `0.6.0-bubble-trial` / 7，基线 `92fc7cc`，交付分支 `codex/chat-bubble-layout`；交付身份见 [优化执行记录](docs/优化执行记录.md) 顶部。增加重复列表项分组、整组选择和附近显示；可调整组内片段、关闭合并，保持显式确认发送。译文以实际测量尺寸避让已知节点/系统区域，空间不足保留完整卡片，页面/键盘/窗口变化清理，不跟随滚动或再次联网。OCR 仍独立人工选择。新版 Android 构建、APK 和真机均待验证，见 [本轮交接](docs/本轮优化验收与交接.md)。

## 2026-09-19 中文翻译试用版（历史已测）

源码 `0.5.0-translation-trial` / 6；实际构建与真机测试对应 `dfbcded1bfd70b834393d905e9047dd5c1eee459`，测试记录提交 `f408d3e9f3bca79dd0a26282d507605167a489b0` 已推送，`master` 与 `codex/node-candidates` 已快进同步。保守节点分组、符号占位保护、带 ID 的译文校验、DeepSeek 文本适配器、取消/超时、Key 加密、两个可编辑风格档案及原文确认/中文卡片已写入。Windows 九组逻辑测试、JUnit、编译、lint、APK 构建和产物检查通过；Samsung SM-S9060 / Android 16 已完成真实 DeepSeek 主流程。作者/时间仍需人工排除，图片 OCR、原气泡附近显示及完整 C01～C13 边界尚未全部验收。当前交接见 [本轮优化验收与交接](docs/本轮优化验收与交接.md)，逐项证据见 [真机测试记录](docs/DEVICE_TESTS.md)；下方 0.4 与更早条目按历史读取。

## 2026-09-17 当前源码入口

- 源码版本：`0.4.0-probe-candidates` / 5；基线 `6214f15`，工作分支 `codex/node-candidates`，本轮修改尚未推送。
- CandidateSelection 替代 OcrSelection，节点和 OCR 共用选择；CandidatePanel 只负责展示，采集协调器仍唯一。
- 节点原文先显示可选；OCR 只追加，不覆盖节点或重置勾选；截图失败仍保留节点。PageToken、完整片段矩形、坐标类别、来源及风险随请求传递。
- 七组桌面测试通过，新增候选测试 41 项；本机仅完成 Java/XML 语法等静态检查，未构建新版 Android APK。
- Windows 构建与真机矩阵待执行；当前仍无自动消息分组、节点/OCR 坐标映射、翻译 API 或中文贴回。
- 唯一当前交接入口：[本轮优化验收与交接](docs/本轮优化验收与交接.md)。后续先验证新候选界面及页面生命周期，再开发消息与坐标适配。

## 2026-09-16 历史源码入口

- 当前版本标记 `0.3.1-probe-review` / 4；本轮 APK 已生成并通过真机前桌面验收。用户已运行当前样式的目标页面结果卡片，但截图未显示版本/哈希，设备安装身份尚未由 dumpsys 独立确认。
- 生产协调器为 CaptureCoordinator；节点读取和 OCR 抽出为 NodeTreeReader / OcrProcessor；OcrSelection 统一勾选状态，生成本地 TranslationRequest 预览。
- Windows PowerShell 5.1 总入口已通过：七组纯 Java 测试、Android Java 编译、lint 和 assembleDebug 均成功。APK 大小 `53,400,368` 字节，SHA-256 `463CE6B4EAD735B07EF442B5DE09B2BF551A614517F18FA7AA95D9DA22C6E249`；v2 签名有效，无 INTERNET/ACCESS_NETWORK_STATE，包含日文 OCR native pipeline 与 bundled 模型。lint 仅有 Gradle 新版本提示和一个未使用字符串警告。
- 当前 OCR 作者/时间仍需用户手动排除；未实现完整消息边界、符号翻译校验、DeepSeek 或中文贴回。
- 用户已提供本轮目标页面的三份真机视觉证据：截图/OCR/候选链路可运行，`1080×2340`，一个样本总耗时 `283ms`；Emoji 被误识别为 `9`/`米`，作者时间仍混入，长消息仍按 OCR 行拆分。设备端版本未由 dumpsys 独立确认，生命周期和连续使用矩阵仍待测。详见 [2026-09-16 真机测试结果与后续修复建议](docs/2026-09-16真机测试结果与后续修复建议.md)。
- 当前 ADB 已无连接设备。下一步优先实施节点原文与 OCR 的保真对应、不可恢复符号警告、元数据分类和有证据的消息分组，再按 [本轮优化验收与交接](docs/本轮优化验收与交接.md) 执行完整真机矩阵。旧 core_modules 只作参考，不能覆盖主工程。

以下历史记录更新时间：2026-09-14

## 2026-09-14 执行入口：正文整理版探针（历史）

- 本轮唯一详细方案：[docs/PROBE_TEXT_OPTIMIZATION_HANDOFF.md](docs/PROBE_TEXT_OPTIMIZATION_HANDOFF.md) 版本 2.0，对应主方案第 28.11 节；包含目标/边界、D01～D13 差距、M0～M5、T01～T25、回退与交付标准。
- 当前里程碑：M0、M1 第一批已完成；2026-09-14 已根据首批新版目标页真机视觉证据实施 M2/M3 第一批。节点正文改为按正文、作者/时间、其他可见文字及控件/媒体分区；坐标退出默认阅读区；OCR 改为默认不加入正文的复选候选，并显示用户已选择内容。M2/M3 仍需真机复测和规则补全，M4～M5 尚未完成；仍不开发翻译、API Key、缓存或后台自动识别。
- 恢复点：方案提交 `ee80f90`，当前代码与文档检查点 `aed21f2`。`test-result.png` 保留，不提交、不删除。
- 本次验证：2026-09-14 M2/M3 第一批后的 `ProbeLogicSelfTest`、Java 编译、lint 和 assembleDebug PASS；同节点 text/description 合并保留双来源、日期时间/时长保守分区、阅读区不显示坐标的断言已通过。新 APK 为 `0.3.0-probe-select`/3，大小 `53,383,422` 字节，SHA-256 `443F2341C476E4180C7E39CCA336B4E6F84C0C99C12C2C79E3E3308F456D9D63`；v2 签名、无 INTERNET/ACCESS_NETWORK_STATE、日文 OCR native/model 检查通过。已通过 ADB `install -r` 覆盖安装，设备端 dumpsys 确认 versionCode 3、versionName 一致，无障碍服务仍启用，主界面已启动；新分区和候选交互尚待用户操作确认。此前两张用户截图显示指定窗口截图成功（`1080×2340`）、节点长文可读、日文 OCR 可运行且一次样本耗时 `836ms`，但截图未独立证明 APK 哈希。
- 当前剩余缺口：生产节点模型尚未覆盖所有 Android 异常出口，TextAssembly 尚未覆盖完整分类/父子压缩规则，OCR 结果尚未进入候选选择 UI，内容变化事件和 overlay 身份仍需真机回归。API 33+ 节点 recycle 已无实际作用，验收应关注引用生命周期，不以补调用次数宣称修复。
- 已有证据：应用可安装/启动；卡片可滚动和关闭；合成与目标页面截图/OCR 可用。2026-09-14 节点路径读取到可见长消息段落并保留换行；另一目标页 OCR 输出 13 个块，能识别正文、作者/时间和语音时长等可见信息。
- 已发现：当前卡片仍直接展示来源标签和坐标；作者、时间、正文、导航及语音时长尚未完成分类。OCR 存在符号变成多余字符、正文和辅助信息混杂的问题；这正是 M2/M3 要解决的范围。生产生命周期测试和内容变化事件仍需继续回归。
- P0.5 未通过：已有一份长消息正向样本，但截断、同句重复、完整合成矩阵、页面切换、锁屏和服务重启等仍未验证；单张截图也不能证明可见正文零遗漏。
- 目标包经设备前台证据确认：`jp.co.sonymusic.communication.sakurazaka`；ADB 曾在线后断开，实际连接须重查。历史“没有设备”不能推翻已经发生的安装和截图证据。
- 下一步：安装 `0.3.0-probe-select`，先确认节点分区、坐标隐藏、OCR 勾选及选择汇总不会卡片跳动或丢失滚动位置；随后复测长文、重复、截断、切页和锁屏矩阵。根据结果补全 M2/M3，再进入 M4。保守回退允许待复测 APK 交付，不代表 P0.5 通过。
- 新估算：剩余开发与桌面交付约 11.5～19 小时有效工作，手机首轮另 0.5～1 小时，适配返修及等待另计；M1/M2 后重估。取代旧 6～12 小时粗估，不是模型测速或期限承诺。

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
