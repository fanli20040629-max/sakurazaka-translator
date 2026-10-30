# Windows 接手 Prompt：版本 10 成员切换与服务故障返测

以下内容可直接复制给原 Windows 笔记本上的开发助手。它替代旧版 8 的接手提示词；不要沿用 `codex/chat-bubble-layout` 和 `0.7.0-reading-card-trial`。

---

请接手 sakurazaka-translator，完成“同步本轮源码和文档 → 环境核验 → 构建 → 自动化及真机返测 → 必要修复 → 留证交接”。常规 Git、环境检查、构建、已授权 ADB 操作和本轮故障修复请自主完成，不要把能自动操作的步骤交给我。没有新授权不推送 GitHub、不合并主干。

## 一、必须核对的交付身份

- 仓库：https://github.com/fanli20040629-max/sakurazaka-translator.git
- 本轮分支：`codex/auto-message-bottom-card`，不要使用旧 `master` 或 `codex/chat-bubble-layout`。
- 必须包含的代码提交：`375d3a5b49a3a9280cb1b082853cc1b7de1a3282`。其后的交接文档提交也要同步，最后报告实际 HEAD。
- 预期版本：`versionCode 10`，`versionName 0.8.1-member-service-fix`。版本相同不代表源码相同。
- 目标助手包名：`com.fanli.sakurazakatranslator`。目标聊天 App 的实际包名仍由本机核验。

先找实际工程，优先检查 `D:\Projects\sakurazaka-translator`，不要覆盖或另造工程。核对远程、分支、工作区和提交：

```powershell
git remote -v
git branch --show-current
git status --short
git rev-parse HEAD
git fetch origin
git log -5 --oneline origin/codex/auto-message-bottom-card
```

每条命令检查退出码；fetch 失败不能继续把旧缓存当最新。远程地址不同先核实，不自动重设用户的 origin。

工作区干净、有本地交付分支时切到该分支，确认能快进，再 `git merge --ff-only origin/codex/auto-message-bottom-card`；没有本地分支时 `git switch --track origin/codex/auto-message-bottom-card`。有未提交修改或分叉先检查和保留，能安全解决的相关差异自行处理；不能安全合并时报告具体冲突，并继续独立的环境/日志检查。禁止 reset --hard、强推、自动清空数据、卸载 App 或覆盖签名。

同步后执行并确认退出 0：

```powershell
git merge-base --is-ancestor 375d3a5b49a3a9280cb1b082853cc1b7de1a3282 HEAD
```

保留 `local.properties`、`debug.keystore`、SDK、缓存和本地测试证据。若包含关系不满足或关键代码缺失，明确报告“没有收到本轮完整修正”，不拿旧 APK 冒充新版。

## 二、读什么，以及本轮改了什么

用 UTF-8 阅读（PowerShell 5.1 使用 `Get-Content -Encoding UTF8`）：

1. `docs/本轮优化验收与交接.md` 的 2026-10-30 当前入口。
2. `docs/2026-10-30成员切换与无障碍故障修复.md`。
3. `docs/优化执行记录.md`、`docs/DEVICE_TESTS.md`、`app/build.gradle.kts`。

先核对实际源码与环境，再执行文档。历史记录不能覆盖现有事实；矛盾先记录并核实，不能凭模型判断宣称通过。

本轮修改及原因：

- **切换成员闪退**：CandidatePanel 原先在 Service Context 中创建 Spinner，可能弹出需要 Activity 窗口身份的 Dialog，存在 BadTokenException 风险。现改为卡内 RadioGroup/RadioButton，确认前校验页面有效性。**这只是定位到的风险，本次真实闪退原因必须由设备堆栈确认。**
- **每次都要确认成员**：原规则主要接受标准标题控件，自定义布局可能漏识别；档案若仍叫“偶像一/二”或缺少对应日文全名也不能匹配。现补充“紧凑顶部容器 + 明确返回按钮”的结构识别，并改进未知姓名提示；没有改为凭正文猜人或沿用上一位。
- **无障碍开关开着却要关开**：授权不等于服务仍连接，闪退可能使服务断开，也可能与强制停止或系统限制有关。现显示授权/连接状态、提供已有连接的浮窗刷新，增加连接/断开/销毁日志并清理旧内容。**没有实现也不能伪称实现系统强制重连**；AccessibilityService 没有公开 requestRebind 接口。
- **编译错误**：TranslationPanel 的 final 字段 send 曾在赋值前被构造回调引用，已改为控件初始化后注册监听。
- 新增 Android 无费用冒烟测试 `OverlaySmokeInstrumentation`；版本和两份 Windows 校验脚本默认值已统一为 10。

Mac 已复跑：17 组桌面逻辑（MemberResolver 80、MemberSession 35 项）、9 项模拟接口测试、Android Java 类型检查。类型检查有 6 条已有 recycle 弃用警告。**完整 Gradle、lint、APK、仪器测试实际运行、真机和真实 API 均未在 Mac 执行**；你必须独立验证，不能复制历史通过状态。

## 三、先取故障证据，再构建安装

如果当前手机还处于闪退或浮窗失效状态，先核对设备安装版本并采集本应用相关崩溃/服务状态，之后才重开服务或覆盖安装。不要先清空日志或 force-stop。

```powershell
adb devices
adb shell dumpsys package com.fanli.sakurazakatranslator
adb shell dumpsys accessibility
adb shell pidof com.fanli.sakurazakatranslator
adb logcat -b crash -d -v threadtime
adb logcat -d -v threadtime -s TranslatorService:I '*:S'
```

完整输出仅本机临时保存；最终报告只保留本应用的版本、启用/绑定/崩溃状态、异常类型、相关调用栈和时间。没有崩溃栈就标“原因待确认”。不要把窗口快速关闭直接等同于进程闪退。

核验 JAVA_HOME、SDK、ADB 和原签名后，在工程根目录执行：

```powershell
.\scripts\verify-pre-device.ps1 -ExpectedVersionCode 10 -ExpectedVersionName '0.8.1-member-service-fix'
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain --no-daemon
```

环境变量缺失时从本机实际安装定位 JDK/SDK，传入 `-JavaHome`、`-SdkRoot`，不要把 Mac 缓存路径抄到 Windows。第一条涵盖桌面逻辑、Java、JUnit、lint、主 APK 和产物检查；第二条构建测试 APK。失败定位并修复，不能删断言或跳过检查；不无故升级依赖。

记录最终源码提交和工作区差异、APK 路径、版本、SHA-256、签名；确认与原安装兼容后 `adb install -r` 覆盖安装，核对设备端版本。签名不匹配先找原签名，不自动卸载。修改代码后重跑相关检查并重新构建、核验、安装，不能用旧 APK 验证新代码。

## 四、哪些测试由你自动完成

具备工具、设备连接与既有操作授权时，自主完成：

- Git、源码与环境核对、构建、桌面/JUnit 测试、APK 身份与签名检查、ADB 安装和本应用状态/日志采集。
- Android 无费用控件冒烟：

```powershell
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.fanli.sakurazakatranslator.test/com.fanli.sakurazakatranslator.capture.OverlaySmokeInstrumentation
```

只有输出 `PASS: member choices and confirmation work without an Activity token; no sends.` 且 `INSTRUMENTATION_CODE: -1` 才记通过。该测试自动切换/确认 20 次并断言不发送；它不证明真实服务浮窗已经通过。仪器测试会启动/替换测试进程，必须在连续性测试前执行，随后重新建立服务已连接的基线。

- 真正的合成页浮窗：切换两位成员并确认 20 次，检查无闪退、无新异常、服务保持绑定、不发送；测试短/长阅读卡、滚动、收起、关闭和旧结果清理。
- 在我已打开并允许测试的聊天页面，通过可靠控件/坐标自主取字、滚动、切换成员。每次核对操作前后画面；ADB 点击返回成功不等于按钮响应或没有穿透。
- 无费用地验证姓名匹配、切人清理、正文提及其他成员不误切、长文草稿不串人；正常返回桌面再进入 10 次、锁屏/解锁 5 次中的可自动部分。
- 缺 Key 门禁和“未发送不联网”优先在合成/隔离测试中核验，不删除我手机现有 Key、不读取明文 Key。

优先执行新文档 F01～F07，不沿用旧提示词要求“下拉框默认值”或已退出默认方案的显示行为。冲突档案、未知/缺失标题等破坏性配置场景优先用桌面或合成测试，若临时修改真实风格配置，先可靠保留且最后恢复，不改 Key。

## 五、必须达到的结果与人工边界

1. **成员切换**：合成页连续 20 次稳定；真实页 A→B→A 各 3 次无崩溃，旧草稿/译文不串人，迟到结果不落到新成员。
2. **自动识别**：两位已配置成员各连续取字 5 次，可靠标题下无需每次手动确认；正文提到他人不改风格。仍失败就区分 UNKNOWN（档案/别名不匹配）和 MISSING（结构未识别），取得本地顶部节点证据后再适配，不能放松身份门禁掩盖问题。
3. **无障碍连续性**：正常退出/返回与锁屏/解锁后无需反复关开开关，浮窗恢复且旧内容不残留。失败先看 Enabled/Bound/Crashed 状态与异常栈，再考虑系统后台限制。
4. **翻译回归**：前三项稳定后，且已有明确付费授权、手机已配 Key，才做少量指定短消息真实翻译；核对中文、符号和换行、切人后迟到结果处理。没有条件则完成其他所有测试，真实 API 标未执行，不连续重复收费请求。

只有以下情况需我参与：USB/系统授权、解锁、登录目标 App、在手机输入 Key、选择允许测试的私人聊天、工具确实无法完成的操作，以及视觉遮挡、真实触摸手感和中文语气评价。不要索要密码或让我把 Key 发到聊天/终端。

手机重启先征得同意，首次解锁由我完成。正常返回、划掉最近任务、系统强制停止、Instrumentation、覆盖安装和重启要分开记录；不要用 am force-stop 冒充普通退出。无法复现或需故障注入的场景标未执行/待验证，不以普通成功场景替代。

## 六、回传什么

将自动检查/修复/构建追加到 `docs/优化执行记录.md`；逐项手机结果追加到 `docs/DEVICE_TESTS.md`。若继续改代码，同步当前交接入口和版本核验依据，不改写历史结果。

每项“通过”必须附源码身份、命令/操作、退出码或实际现象、本地脱敏证据位置。分别写清代码实现、桌面通过、Android 构建通过、仪器测试通过、真机通过、真实 API 通过。

最后返回一个简明结果表，至少包含：

| 项目 | 必须回传 |
| --- | --- |
| 代码身份 | 同步前/后分支和完整 HEAD，是否包含 375d3a5，最终工作区差异/新增修复 |
| 安装身份 | APK 文件路径、版本、SHA-256、签名一致性、设备实际安装版本 |
| 自动验证 | 各脚本/Gradle 命令退出码、失败摘要与修复、仪器测试 stream 和结束码 |
| 三项 Bug | 每项通过/部分通过/未通过/未执行，操作次数、实际现象、相关证据；原因确认到哪一步 |
| 服务异常 | 故障前后的授权/绑定/崩溃状态、时间和本应用异常栈；没有异常栈明确写没有 |
| 真实页面 | UNKNOWN/MISSING/MATCHED 的实际结果、脱敏标题结构结论；不回传私人正文/完整 XML |
| 真实 API | 是否执行、次数、成功/失败/不确定、是否可能重复请求；不输出 Key |
| 遗留事项 | 未执行原因、下一步修复建议、只需要我手动做的最小清单 |

私人聊天、截图、原始 XML、Key、设备序列号和其他应用日志只按必要性留在本地，不提交 GitHub或写进交接。没有证据不说完成；没有新指令不推送、不合并主干。
