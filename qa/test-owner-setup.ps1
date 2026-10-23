$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskMockRoot=Join-Path $taskRoot '.local\owner-setup-test'
New-Item -ItemType Directory -Path $taskMockRoot -Force | Out-Null
$taskFakeAdb=Join-Path $taskMockRoot 'fake-adb.cmd'
@'
@echo off
echo %*>>"%~dp0calls.log"
if "%1"=="devices" (
 echo List of devices attached
 echo TEST_SERIAL device product:mock model:mock
 exit /b 0
)
if "%4"=="getprop" echo 35
if "%4"=="am" if "%5"=="get-current-user" echo 0
if "%4"=="pm" if "%6"=="features" echo feature:android.software.device_admin
if "%4"=="pm" if "%6"=="users" echo UserInfo{0:Owner:13} running
if "%4"=="dumpsys" if "%5"=="account" if "%PHONEFOCUS_MOCK_ACCOUNT%"=="yes" echo Account {name=TEST,type=TEST}
if "%3"=="install" echo Success
if "%1"=="install" echo Success
if "%4"=="dpm" echo Success: Device owner set to package com.phonefocus.guard
if "%4"=="am" if "%5"=="start" echo Starting: Intent
exit /b 0
'@ | Set-Content -LiteralPath $taskFakeAdb -Encoding ascii
$taskPwsh=(Get-Process -Id $PID).Path
$taskTool=Join-Path $taskRoot 'tools\manage-device-owner.ps1'
$taskCalls=Join-Path $taskMockRoot 'calls.log'
$taskChecks=0
function Assert-OwnerSetup([bool]$Condition,[string]$Label){if(-not $Condition){throw "Owner setup test failed: $Label"};$script:taskChecks++}
function Run-MockOwnerSetup([string[]]$Extra){
    [IO.File]::WriteAllText($taskCalls,'')
    $taskChildArgs=@('-NoProfile','-File',$taskTool,'-AdbPath',$taskFakeAdb)+$Extra
    $taskOutput=(& $taskPwsh @taskChildArgs 2>&1|Out-String)
    return @{exit=$LASTEXITCODE;output=$taskOutput;calls=[IO.File]::ReadAllText($taskCalls)}
}
try{
    $env:PHONEFOCUS_MOCK_ACCOUNT='no'
    $taskCase=Run-MockOwnerSetup @()
    Assert-OwnerSetup ($taskCase.exit -eq 0) 'default read-only succeeds'
    Assert-OwnerSetup ($taskCase.calls -notmatch 'install|set-device-owner') 'default never mutates'
    $taskCase=Run-MockOwnerSetup @('-DeviceSerial','TEST_SERIAL')
    Assert-OwnerSetup ($taskCase.exit -eq 0) 'selected phone preflight succeeds'
    Assert-OwnerSetup ($taskCase.calls -notmatch 'install|set-device-owner') 'selected preflight never mutates'
    $taskCase=Run-MockOwnerSetup @('-DeviceSerial','WRONG_SERIAL','-Apply')
    Assert-OwnerSetup ($taskCase.exit -ne 0) 'wrong phone refused'
    Assert-OwnerSetup ($taskCase.calls -notmatch 'install|set-device-owner') 'wrong phone not modified'
    $env:PHONEFOCUS_MOCK_ACCOUNT='yes'
    $taskCase=Run-MockOwnerSetup @('-DeviceSerial','TEST_SERIAL','-Apply')
    Assert-OwnerSetup ($taskCase.exit -ne 0) 'account-bearing phone refused'
    Assert-OwnerSetup ($taskCase.calls -notmatch 'install|set-device-owner') 'preflight failure has no mutation'
    $env:PHONEFOCUS_MOCK_ACCOUNT='no'
    $taskCase=Run-MockOwnerSetup @('-DeviceSerial','TEST_SERIAL','-Apply')
    Assert-OwnerSetup ($taskCase.exit -eq 0) 'explicit apply executes against mock only'
    Assert-OwnerSetup ($taskCase.calls -match '-s TEST_SERIAL install -r') 'install targets selected phone'
    Assert-OwnerSetup ($taskCase.calls -match '-s TEST_SERIAL shell dpm set-device-owner com.phonefocus.guard/.LockAdminReceiver') 'ownership uses exact expected component'
    Assert-OwnerSetup ($taskCase.calls -notmatch 'wipe|factory_reset|remove-user|remove-account|reset-password') 'no destructive fallback commands'
    Write-Output "PASS: $taskChecks owner-setup guard checks using fake ADB only. No phone contacted or configured."
}finally{Remove-Item Env:PHONEFOCUS_MOCK_ACCOUNT -ErrorAction SilentlyContinue}
