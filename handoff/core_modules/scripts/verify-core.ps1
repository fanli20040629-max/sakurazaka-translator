# Windows PowerShell 5.1+；仅使用已安装 JDK，不下载工具、不修改系统设置。
param([string]$SourceDir)
$ErrorActionPreference = 'Stop'
$moduleDir = Split-Path $PSScriptRoot -Parent
if (-not $SourceDir) { $SourceDir = Join-Path $moduleDir 'src' }
if (-not (Test-Path -LiteralPath $SourceDir -PathType Container)) { throw "源码目录不存在：$SourceDir" }
$javacCmd = 'javac'
$javaCmd = 'java'
if ($env:JAVA_HOME) {
    $javacCmd = Join-Path $env:JAVA_HOME 'bin/javac.exe'
    $javaCmd = Join-Path $env:JAVA_HOME 'bin/java.exe'
}
& $javacCmd -version
if ($LASTEXITCODE -ne 0) { throw '需要可用的 JDK 17 或以上版本。' }
$buildDir = Join-Path ([System.IO.Path]::GetTempPath()) ('sakura-core-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $buildDir | Out-Null
$sources = @(Get-ChildItem $SourceDir, (Join-Path $moduleDir 'tools') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
& $javacCmd --release 17 -encoding UTF-8 -Xlint:all -Werror -d $buildDir @sources
if ($LASTEXITCODE -ne 0) { throw '核心模块编译失败。' }
& $javaCmd -cp $buildDir CoreModulesSelfTest
if ($LASTEXITCODE -ne 0) { throw '核心模块测试失败。' }
Write-Output "测试编译产物：$buildDir"
