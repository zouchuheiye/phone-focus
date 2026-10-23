param([string]$ToolchainRoot = (Join-Path $env:USERPROFILE 'Documents\Codex\2026-10-13\codex-v0-1-android-ai-android\work\toolchain'))
$ErrorActionPreference = 'Stop'
$jdkBin = Join-Path $ToolchainRoot 'zulu17.68.17-ca-jdk17.0.20-win_x64\bin'
$classes = Join-Path $PSScriptRoot 'qa\classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$taskTestSources = @('app\src\main\java\com\phonefocus\guard\ScreenSession.java', 'app\src\main\java\com\phonefocus\guard\AutoScreenOff.java', 'qa\ScreenSessionTest.java', 'qa\AutoScreenOffTest.java') | ForEach-Object { Join-Path $PSScriptRoot $_ }
$taskTestSources += @('app\src\main\java\com\phonefocus\guard\FocusWindow.java','app\src\main\java\com\phonefocus\guard\RecoveryCode.java','app\src\main\java\com\phonefocus\guard\PolicyRollback.java','qa\OwnerFocusTest.java') | ForEach-Object {Join-Path $PSScriptRoot $_}
$taskTestSources += @('app\src\main\java\com\phonefocus\guard\DailySchedule.java','qa\DailyScheduleTest.java') | ForEach-Object {Join-Path $PSScriptRoot $_}
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $classes @taskTestSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $classes com.phonefocus.guard.ScreenSessionTest
if ($LASTEXITCODE -ne 0) { throw 'Screen session tests failed' }
& (Join-Path $jdkBin 'java.exe') -cp $classes com.phonefocus.guard.AutoScreenOffTest
if ($LASTEXITCODE -ne 0) { throw 'Automatic screen-off tests failed' }
& (Join-Path $jdkBin 'java.exe') -cp $classes com.phonefocus.guard.OwnerFocusTest
if($LASTEXITCODE -ne 0){throw 'Owner-mode focus tests failed'}
& (Join-Path $jdkBin 'java.exe') -cp $classes com.phonefocus.guard.DailyScheduleTest
if($LASTEXITCODE -ne 0){throw 'Daily schedule tests failed'}
