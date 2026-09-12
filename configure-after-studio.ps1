$ErrorActionPreference = 'Stop'
$root = 'D:\Android'
$downloads = Join-Path $root 'Downloads'
$studioRoot = 'D:\android_stufio\android-studio'
$sdkRoot = Join-Path $root 'Sdk'
$status = Join-Path $downloads 'configure-status.txt'
$postStatus = Join-Path $downloads 'postinstall-status.txt'
$postPidPath = Join-Path $downloads 'postinstall.pid'

function Write-Status([string]$message) {
    $message | Set-Content -LiteralPath $status -Encoding UTF8
}

try {
    Write-Status 'WAITING: Android Studio download and extraction'
    while ($true) {
        $postAlive = $false
        if (Test-Path -LiteralPath $postPidPath) {
            $postId = [int](Get-Content -LiteralPath $postPidPath -Raw)
            $postAlive = [bool](Get-Process -Id $postId -ErrorAction SilentlyContinue)
        }
        if (Test-Path -LiteralPath $postStatus) {
            $s = (Get-Content -LiteralPath $postStatus -Raw).Trim()
            if ($s -like 'EXTRACTED:*') { break }
            if ((-not $postAlive) -and $s -like 'FAILED:*') { throw $s }
        }
        Start-Sleep -Seconds 30
    }

    Write-Status 'CONFIGURING: Git'
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) {
        if (Get-Command winget.exe -ErrorAction SilentlyContinue) {
            winget.exe install --id Git.Git --exact --silent --accept-source-agreements --accept-package-agreements
        } else {
            throw 'Git is not installed and winget.exe is unavailable'
        }
    }

    New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null
    $sdkManagerItem = Get-ChildItem -LiteralPath $sdkRoot -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    $sdkManager = if ($sdkManagerItem) { $sdkManagerItem.FullName } else { $null }
    if (-not $sdkManager) {
        Write-Status 'CONFIGURING: Android command-line tools download'
        $toolsZip = Join-Path $downloads 'commandlinetools-win-latest.zip'
        Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile $toolsZip
        $temp = Join-Path $downloads 'cmdline-tools-extract'
        if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
        Expand-Archive -LiteralPath $toolsZip -DestinationPath $temp -Force
        $cmdRoot = Join-Path $sdkRoot 'cmdline-tools\latest'
        New-Item -ItemType Directory -Force -Path $cmdRoot | Out-Null
        Copy-Item -Path (Join-Path $temp 'cmdline-tools\*') -Destination $cmdRoot -Recurse -Force
        $sdkManager = Join-Path $cmdRoot 'bin\sdkmanager.bat'
    }

    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $jbr = Join-Path $studioRoot 'jbr'
    if (Test-Path (Join-Path $jbr 'bin\java.exe')) { $env:JAVA_HOME = $jbr }
    [Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdkRoot, 'User')
    [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $sdkRoot, 'User')
    if ($env:JAVA_HOME) { [Environment]::SetEnvironmentVariable('JAVA_HOME', $env:JAVA_HOME, 'User') }

    Write-Status 'CONFIGURING: accepting Android SDK licenses'
    $log = Join-Path $downloads 'sdkmanager-install.log'
    $sdkManagerForCmd = $sdkManager -replace '/', '\\'
    cmd.exe /c "(echo y&echo y&echo y&echo y&echo y&echo y&echo y&echo y&echo y&echo y) | $sdkManagerForCmd --sdk_root=$sdkRoot --licenses" 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager license step failed with exit code $LASTEXITCODE" }
    Write-Status 'CONFIGURING: Android 16 SDK, Build Tools, Platform Tools (verbose)'
    & $sdkManager --sdk_root=$sdkRoot --verbose 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' 2>&1 | Tee-Object -FilePath $log -Append
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed with exit code $LASTEXITCODE" }
    Write-Status 'READY: Android Studio extracted; Git and Android 16 SDK components installed'
} catch {
    Write-Status ('FAILED: ' + $_.Exception.Message)
    exit 1
}
