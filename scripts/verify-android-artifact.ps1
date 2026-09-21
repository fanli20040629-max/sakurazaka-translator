param(
    [string]$ApkPath,
    [string]$SdkRoot,
    [string]$BuildToolsVersion = '36.0.0',
    [string]$ExpectedPackage = 'com.fanli.sakurazakatranslator',
    [int]$ExpectedVersionCode = 7,
    [string]$ExpectedVersionName = '0.6.0-bubble-trial'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
}
$ApkPath = [System.IO.Path]::GetFullPath($ApkPath)

if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    $SdkRoot = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    $SdkRoot = $env:ANDROID_HOME
}
if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    throw 'Set ANDROID_SDK_ROOT/ANDROID_HOME or pass -SdkRoot.'
}

$buildTools = Join-Path $SdkRoot (Join-Path 'build-tools' $BuildToolsVersion)
$aapt = Join-Path $buildTools 'aapt.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
foreach ($requiredPath in @($ApkPath, $aapt, $apksigner)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required file not found: $requiredPath"
    }
}

$badging = @(& $aapt dump badging $ApkPath 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'aapt failed to read APK badging.'
}
$packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
if (-not $packageLine) {
    throw 'APK package metadata is missing.'
}
if ($packageLine -notmatch "name='$([regex]::Escape($ExpectedPackage))'") {
    throw "Unexpected package metadata: $packageLine"
}
if ($packageLine -notmatch "versionCode='$ExpectedVersionCode'") {
    throw "Unexpected versionCode: $packageLine"
}
if ($packageLine -notmatch "versionName='$([regex]::Escape($ExpectedVersionName))'") {
    throw "Unexpected versionName: $packageLine"
}

$permissions = @(& $aapt dump permissions $ApkPath 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'aapt failed to read APK permissions.'
}
if (-not ($permissions -match 'android\.permission\.INTERNET')) {
    throw 'Text translation requires INTERNET permission.'
}
foreach ($forbiddenPermission in @('android.permission.ACCESS_NETWORK_STATE')) {
    if ($permissions -match [regex]::Escape($forbiddenPermission)) {
        throw "Forbidden permission found: $forbiddenPermission"
    }
}

$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $signature = @(& $apksigner verify --verbose $ApkPath 2>&1 | ForEach-Object { $_.ToString() })
    $signatureExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
if ($signatureExitCode -ne 0 -or -not ($signature -match '^Verifies$')) {
    throw 'APK signature verification failed.'
}
if (-not ($signature -match '^Verified using v2 scheme \(APK Signature Scheme v2\): true$')) {
    throw 'APK is not verified with the v2 signature scheme.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName })
} finally {
    $archive.Dispose()
}
if (-not ($entries -match '^lib/.+/libmlkit_google_ocr_pipeline\.so$')) {
    throw 'ML Kit OCR native pipeline is missing from the APK.'
}
if (-not ($entries -match '^assets/mlkit-google-ocr-models/.+/Jpan')) {
    throw 'Bundled Japanese OCR model is missing from the APK.'
}

$apk = Get-Item -LiteralPath $ApkPath
$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $ApkPath
Write-Output 'Android artifact verification PASS'
Write-Output "APK: $($apk.FullName)"
Write-Output "Package: $ExpectedPackage"
Write-Output "Version: $ExpectedVersionName ($ExpectedVersionCode)"
Write-Output "Size: $($apk.Length) bytes"
Write-Output "SHA-256: $($hash.Hash)"
Write-Output 'Signature: v2 verified'
Write-Output 'Permissions: INTERNET present for explicit text translation; ACCESS_NETWORK_STATE absent'
Write-Output 'OCR: native pipeline and bundled Japanese model present'
