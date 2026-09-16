# 项目当前状态

## 2026-09-16 当前源码入口（以下旧日期内容保留为历史）

- 当前版本标记 `0.3.1-probe-review` / 4，尚未生成并验证本轮 APK。
- 生产协调器为 CaptureCoordinator；节点读取和 OCR 抽出为 NodeTreeReader / OcrProcessor；OcrSelection 统一勾选状态，生成本地 TranslationRequest 预览。
- 七组纯 Java 测试通过（含七个先失败后修复的回归场景）；Android 依赖类未在本机编译。Windows 脚本、Android lint/构建、安装及新包真机测试待验证。
- 当前 OCR 作者/时间仍需用户手动排除；未实现完整消息边界、符号翻译校验、DeepSeek 或中文贴回。
- 下一步按 [本轮优化验收与交接](docs/本轮优化验收与交接.md) 在原电脑合并并构建；查验与规划见 [当前代码审核与改进方案](docs/当前代码审核与改进方案.md)。旧 core_modules 只作参考，不能覆盖主工程。

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
