# 核心模块接入包：从这里开始

版本：2026-09-15 / v0.2；状态：**核心自测通过，尚未接入 Android 主工程**。

这是审核0914的增量源码与接入说明，不是第二套 Android 工程、不是 APK，也不能覆盖另一台笔记本的整个项目。它替代本对话中先前未经验证的 core_modules 草稿，不替代你的主工程。

## 给用户的解释

你只需要让另一台笔记本上的 Luna 打开原项目，并读本目录。它应先检查“自己手上的项目版本”，再逐步把这些模块接进去。每做完一小步，都要编译、测试并记录结果。你不需要记住 Java 文件名、安装命令或手动解决依赖冲突。

GitHub 负责传递源码版本；另一台电脑上的 Luna 负责操作那台电脑的文件、SDK 和依赖。本会话没有远程控制那台电脑，也没有自动给它发送任务。

## 固定阅读顺序

1. [另一台电脑接入指南](docs/另一台电脑接入指南.md)：Luna 首轮提示词、版本对齐、依赖和分步迁移。
2. 用户已确认的《长期稳定版开发总方案.md》：当前在本包父目录；上传 GitHub 时可放主工程 docs 下并更新本索引。
3. [模块地图与规则](docs/模块地图与规则.md)：已实现的类、数据流和边界。
4. [当前状态与验收](docs/当前状态与验收.md)：哪些经过测试、哪些需要主工程或真机证明。
5. [开发约束](AGENTS.md)：本包维护规则，不覆盖主工程已有 AGENTS.md。

## 现在能够运行什么

本包仅需可用 JDK 17 或以上版本。Java 源码用 --release 17 编译，无第三方依赖，无 API 请求。

macOS/Linux，在本目录执行：

```bash
bash scripts/verify-core.sh
```

Windows PowerShell，在本目录执行：

```powershell
powershell.exe -NoProfile -File .\scripts\verify-core.ps1
```

脚本默认使用 JAVA_HOME/bin 下的工具；未配置则使用 PATH。脚本不安装 JDK、不修改永久环境变量，不要求使用本电脑 PyCharm 的 JDK 路径。临时测试产物位置会打印；它们不进入仓库。Windows 脚本尚未在 Windows 执行验证，若执行策略限制，应按本机政策处理，不全局关闭安全策略。

## 源码只保留一个正式位置

首次接入可把整个包放在主仓库 handoff/core_modules，先独立运行测试。真正接入时，把 src/com/fanli/sakurazakatranslator/domain **移动**到主工程 app/src/main/java/com/fanli/sakurazakatranslator/domain；如果已有同名类，先比较和合并，不能直接覆盖或移动。

移动后，在主仓库根目录测试真实的生产源码：

```bash
bash handoff/core_modules/scripts/verify-core.sh app/src/main/java/com/fanli/sakurazakatranslator/domain
```

```powershell
powershell.exe -NoProfile -File .\handoff\core_modules\scripts\verify-core.ps1 -SourceDir .\app\src\main\java\com\fanli\sakurazakatranslator\domain
```

不要保留第二份 src 并只测试旧副本。Android 构建还要单独通过 Gradle，核心自测不能替代 APK 构建。
