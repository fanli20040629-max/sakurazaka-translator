$ErrorActionPreference = 'Stop'
$downloads = 'D:\Android\Downloads'
$zip = Join-Path $downloads 'android-studio-quail4-windows.zip'
$url = 'https://dl.google.com/android/studio/ide-zips/2026.1.4.7/android-studio-quail4-windows.zip'
$hash = 'fabc885015bd67182f4f49da8e3efbb3b60038b2dfda632cf2d29363ffb1e04e'
$downloadStatus = Join-Path $downloads 'download-status.txt'
$postPid = Join-Path $downloads 'postinstall.pid'
$post = Join-Path $downloads 'postinstall.ps1'

try {
    $self = [Diagnostics.Process]::GetCurrentProcess().Id
    $self | Set-Content -LiteralPath $postPid
    'BITS: starting system background transfer' | Set-Content -LiteralPath $downloadStatus
    $job = Start-BitsTransfer -Source $url -Destination $zip -DisplayName 'Android Studio overnight download' -Priority Foreground -Asynchronous
    while ($true) {
        $job = Get-BitsTransfer -JobId $job.JobId -ErrorAction SilentlyContinue
        if (-not $job) { throw 'BITS job disappeared' }
        $done = [math]::Round($job.BytesTransferred / 1MB, 1)
        $total = if ($job.BytesTotal -gt 0) {[math]::Round($job.BytesTotal / 1MB, 1)} else {'?'}
        "BITS: $done MB / $total MB; state=$($job.JobState)" | Set-Content -LiteralPath $downloadStatus
        if ($job.JobState -eq 'Transferred') { Complete-BitsTransfer -BitsJob $job; break }
        if ($job.JobState -eq 'Error') { throw $job.ErrorDescription }
        Start-Sleep -Seconds 30
    }
    $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $hash) { throw "SHA256 mismatch: $actual" }
    'VERIFIED: BITS download complete; SHA-256 matches' | Set-Content -LiteralPath $downloadStatus
    $p = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',$post) -WindowStyle Hidden -PassThru
    $p.Id | Set-Content -LiteralPath $postPid
} catch {
    ('FAILED: ' + $_.Exception.Message) | Set-Content -LiteralPath $downloadStatus
    exit 1
}
