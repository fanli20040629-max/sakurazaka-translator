# Windows 文档编码说明

本项目的 Markdown 和源码统一使用 UTF-8 编码，文件使用 LF 换行。这样可以同时兼容 macOS、Windows、GitHub、VS Code 和 Android Studio。

## 为什么 Windows 可能显示乱码

文件本身没有问题。旧版 Windows PowerShell 5.1 的 `Get-Content` 默认按系统 ANSI 编码读取文件，遇到中文 UTF-8 文档就可能显示乱码。

## 推荐打开方式

- 用 VS Code、Android Studio 或新版记事本直接打开。
- 在 PowerShell 5.1 中读取时明确指定编码：

```powershell
Get-Content -Encoding UTF8 .\docs\当前代码审核与改进方案.md
```

- PowerShell 7 默认对 UTF-8 支持更好，建议后续使用 PowerShell 7。

## 不要做的事情

不要把这些文件批量转换成 ANSI、GBK 或 Big5。这样会让 GitHub、macOS 和后续模型读取出现新的兼容问题。

## 提交前检查

在 Windows 上可以用 VS Code 状态栏确认文件编码为 `UTF-8`。如果显示 `GBK` 或 `Windows 1252`，请使用“另存为 UTF-8”，不要选择 ANSI。
