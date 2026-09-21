param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [int]$ExpectedVersionCode = 7,
    [string]$ExpectedVersionName = '0.6.0-bubble-trial'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    $SdkRoot = $env:ANDROID_HOME
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw 'Set JAVA_HOME or pass -JavaHome.'
}
if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    throw 'Set ANDROID_SDK_ROOT/ANDROID_HOME or pass -SdkRoot.'
}

$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw "Java executable not found: $java"
}

Push-Location $projectRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_SDK_ROOT = $SdkRoot
    $env:ANDROID_HOME = $SdkRoot
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'

    & (Join-Path $PSScriptRoot 'verify-probe-logic.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw 'Desktop logic verification failed.'
    }

    & .\gradlew.bat :app:compileDebugJavaWithJavac :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw 'Android compile, lint, or APK build failed.'
    }

    & (Join-Path $PSScriptRoot 'verify-android-artifact.ps1') -SdkRoot $SdkRoot -ExpectedVersionCode $ExpectedVersionCode -ExpectedVersionName $ExpectedVersionName
    if ($LASTEXITCODE -ne 0) {
        throw 'Android artifact verification failed.'
    }

    & git diff --check
    if ($LASTEXITCODE -ne 0) {
        throw 'git diff --check failed.'
    }

    Write-Output 'Pre-device verification PASS. The APK is ready for installation and device tests.'
} finally {
    Pop-Location
}
