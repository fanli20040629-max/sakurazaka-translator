# 项目当前状态

更新时间：2026-09-12

## 目标

开发 Sakurazaka 日文到简体中文翻译 Android 助手，先完成可安装、可验证的最小版本，再逐步加入取字、翻译卡片和设备测试。

## 当前工作区

- 项目目录：`D:\Projects\sakurazaka-translator`
- 现有方案：`sakurazaka_cli_development_plan.md`；第 21～28 节为唯一执行入口，已完成 2026-09-12 复核修订。
- Android 源码工程骨架已建立；Git 仓库尚未初始化。

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

1. 当前为 R1～R5 已实现的修复版探针，P0.5 仍未通过真机验收；最新恢复入口为方案第 28.5 节。
2. 已通过 `ProbeLogicSelfTest`、Wrapper `assembleDebug`、`lintDebug`（0 error、1 个锁定版本提示）和 APK 最终权限/OCR 组件检查；APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
3. ADB 当前无设备；安装、无障碍授权、合成自测、实际截图、OCR 字级质量和正文验收均待验证。
4. 连接设备后先完成合成自测，再测试目标 App 四类页面；至少一条正文路径满足门槛后，按 P1/P2/P3 推进翻译，P4 再做扩展验收。
