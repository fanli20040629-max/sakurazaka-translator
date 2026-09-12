# 探针收尾开发交接指令

适用于接手本项目的开发代理，包括用户指定的 GPT‑5.6。以下正文可直接作为开发指令。质量以实现与验证证据判断，不因模型名称或遵循提示词而自动保证。

## 可直接复制的指令

请在 `D:\Projects\sakurazaka-translator` 中实际完成探针收尾开发，目标是完成方案第 28.7 节 A1～A5，交付可进入手机合成自测的 APK。暂不开发 DeepSeek、API Key、翻译或缓存功能。

1. **阅读与恢复。** 先读取 `project_state.md`，再完整阅读 `sakurazaka_cli_development_plan.md`：第 21～28 节为执行依据，第 28.7 节为当前任务，第 1～20 节保留需求和安全约束。继续读取 `docs/STATUS.md`、`docs/DEVICE_TESTS.md`、相关源码、测试及构建文件；检查适用的 `AGENTS.md` 和 Git 状态。使用 `working-with-files-fl` 技能管理检查点，并先读取其 SKILL.md。保留已有未提交文档改动，不覆盖、不回退用户修改。旧文档中的“已修复”“只差手机”属于历史判断，必须用当前代码核实。

2. **先建立差距清单。** 将 A1～A5 各项对应到代码位置、触发场景、修复方法和验收用例。优先检查 `TranslatorAccessibilityService.java`、`ProbeLogic.java`、`ProbePreferences.java`、`MainActivity.java`、无障碍 XML 配置和 `ProbeLogicSelfTest.java`。已有构建、lint 和简单逻辑测试通过，不代表任务生命周期或正文质量正确。完成差距核对后直接实施，不只输出计划。

3. **A1：取消不等于底层任务结束。** 将页面结果是否有效与物理截图/OCR 是否仍在运行分开。取消、关闭、切页后立即拒绝旧结果，但底层任务实际完成前不得重新开启并发采集。使用请求所有权防止旧完成回调解除新任务占用。不得用超时或清空 busy 冒充底层取消成功；久未完成时提示明确状态。所有成功、失败、取消和同步异常出口均须收敛到正确的资源清理。

4. **A2：资源与服务生命周期。** HardwareBuffer 在所有路径关闭；Bitmap 在 OCR 实际完成且预览不再引用后恰好释放一次。服务销毁后不再启动 OCR，也不得重新添加悬浮窗口；迟到截图回调仍须释放资源。在途 OCR 完成后再关闭识别器，不能提前关闭回调执行器导致清理丢失。统一线程或明确同步所有权，覆盖截图异常、OCR 启动异常、失败及取消。可抽取小型协调器和资源接口供测试，不做无关大重构。

5. **A3：页面身份和锁屏。** 使用 requestId、pageEpoch、目标包和 windowId 标记快照，展示前核对前台、页面身份和锁屏状态。同包滚动、页面切换、两个允许应用之间切换、方向/尺寸变化均不得展示旧结果；锁屏、中断和离开时清除全部悬浮窗口。正确区分助手卡片事件与目标事件，卡片滚动不能误取消任务；核查 XML 是否订阅了实际使用的事件。事件只用于识别窗口和标记过期，不自动遍历正文或后台 OCR；不能确定页面变化时标记结果可能过期。

6. **A4～A5：明确采集范围及可核对结果。** 合成测试必须由用户显式开启，仅允许选中的测试页；目标包基于设备前台证据经用户确认后保存，不猜包名，不回退到任意后台窗口。截图失败也要展示可滚动节点正文和错误码。OCR 提供有序文本块、边界及耗时；节点保留结构与边界，只去除确定的父子重复，保留不同位置的相同句子。达到节点数/深度限制及边缘截断须提示。卡片适配可用屏幕，拖动后关闭入口始终可达，结果可完整滚动。配置、说明及页面布局不应遮挡合成样本验收。

7. **验证必须覆盖真实行为。** 为取消后再点击、旧回调、服务销毁、迟到截图、OCR 成功/失败/启动异常、资源恰好释放一次、不同位置重复文本、同包切页、锁屏和卡片内部事件编写针对性测试。可用受控假回调按不同顺序模拟，但测试应连接生产协调逻辑，不另造一套只供测试的实现。扩展现有测试脚本使新增用例真正运行。测试名称和结果需可追溯；不得把 NO-SOURCE、未执行或仅位置键检查当成生命周期测试通过。

8. **复用工具并完成构建检查。** 保留已验证的 Java 源码、原生 View、单模块和锁定依赖，除非具体错误证明必须调整。复用 JBR `D:\android_stufio\android-studio\jbr`、SDK `D:\Android\Sdk`、项目 Wrapper 和 `.gradle-home`。运行逻辑测试、`:app:lintDebug`、`:app:assembleDebug`；缓存齐全时优先离线。缺失依赖按具体错误从官方来源补齐，不清空缓存、不停用 TLS 校验。修复真实 lint 错误，不以大范围 suppression 或 baseline 掩盖问题。核查最终 APK 签名、包名、版本、日文模型及无 INTERNET/ACCESS_NETWORK_STATE 权限；模型文件存在不等于断网识别已验证。保留现有调试签名，更新版本便于区分返测包。

9. **边界与自主执行。** 自主处理常规编码、构建和修复，不为已授权常规操作反复询问；工具强制权限审批按实际要求申请。不得上传截图/正文、记录设备序列号或密钥，不绕过目标应用安全窗口，不伪装辅助工具。任务较长时简短报告新证据和剩余问题，不能把 CPU 累计值或任务标题当作持续进展证明。遇到缺设备、授权或视觉确认时，先完成所有不依赖该条件的工作，再给出简短手机步骤。

10. **交付与停止条件。** 修改完成后再次按 A1～A5 审查最终 diff，修复残留问题并重跑受影响检查。更新 `project_state.md`、`docs/STATUS.md`、`docs/DEVICE_TESTS.md`、`docs/DECISIONS.md` 及必要的 README；保留历史，将当前结论集中到最新入口。只提交已审查的本次相关文件，建立本地 Git 检查点，不推送远程。输出 A1～A5 完成/未完成及对应证据、实际运行的测试和 lint 结果、APK 绝对路径/版本/大小/SHA-256、剩余限制。分别报告编译、安装、截图、OCR 正文和真机验收状态。无设备时交付“代码与桌面检查完成，手机合成自测待验证”，不要宣称 P0.5 通过；仍有代码缺口时明确列出，不能称为仅差手机。先完成 B 合成自测，再指导 C 四类目标页面；C 通过前不进入翻译开发。

## 本机验证命令参考

先核对文件和路径；以下命令不代替上述验收用例。执行中逐项检查退出码，失败后修复再运行受影响项。

```powershell
$env:JAVA_HOME='D:\android_stufio\android-studio\jbr'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-probe-logic.ps1
.\gradlew.bat :app:lintDebug :app:assembleDebug --offline --console=plain --no-daemon
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'D:\Android\Sdk\build-tools\36.0.0\aapt.exe' dump permissions .\app\build\outputs\apk\debug\app-debug.apk
& 'D:\Android\Sdk\build-tools\36.0.0\apksigner.bat' verify --verbose .\app\build\outputs\apk\debug\app-debug.apk
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

旧产物及历史检查点见 project_state.md。实际新产物哈希和 Git 状态以本次读取/构建证据为准；本文件的写入不代表已实施 A1～A5。
