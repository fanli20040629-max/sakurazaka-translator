$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot '.verification\probe-logic'
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\android_stufio\android-studio\jbr' }
$javac = Join-Path $javaHome 'bin\javac.exe'
$java = Join-Path $javaHome 'bin\java.exe'
$logic = Join-Path $projectRoot 'app\src\main\java\com\fanli\sakurazakatranslator\capture\ProbeLogic.java'
$models = Join-Path $projectRoot 'app\src\main\java\com\fanli\sakurazakatranslator\capture\ProbeModels.java'
$assembly = Join-Path $projectRoot 'app\src\main\java\com\fanli\sakurazakatranslator\capture\TextAssembly.java'
$coordinator = Join-Path $projectRoot 'app\src\main\java\com\fanli\sakurazakatranslator\capture\CaptureCoordinator.java'
$test = Join-Path $projectRoot 'tools\ProbeLogicSelfTest.java'

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
& $javac --release 17 -encoding UTF-8 -d $outputDir $logic $models $assembly $coordinator $test
if ($LASTEXITCODE -ne 0) { throw 'Probe logic compilation failed.' }
& $java -cp $outputDir com.fanli.sakurazakatranslator.capture.ProbeLogicSelfTest
if ($LASTEXITCODE -ne 0) { throw 'Probe logic self-test failed.' }
