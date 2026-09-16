$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot ('.verification\logic-' + [Guid]::NewGuid().ToString('N'))
if ($env:JAVA_HOME) {
    $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $javac = (Get-Command javac.exe -ErrorAction Stop).Source
    $java = (Get-Command java.exe -ErrorAction Stop).Source
}
$sources = @(Get-Content -Encoding UTF8 (Join-Path $projectRoot 'tools\logic-sources.txt') |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    ForEach-Object { Join-Path $projectRoot $_ })
$tests = @(Get-Content -Encoding UTF8 (Join-Path $projectRoot 'tools\logic-test-classes.txt') |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
& $javac --release 17 -encoding UTF-8 -Xlint:all -d $outputDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Desktop logic compilation failed.' }
foreach ($testClass in $tests) {
    & $java '-Dfile.encoding=UTF-8' -cp $outputDir $testClass
    if ($LASTEXITCODE -ne 0) { throw "Self-test failed: $testClass" }
}
Write-Output 'All desktop logic tests passed. Android build and device tests are separate.'
