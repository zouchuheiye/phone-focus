param(
    [string]$DeviceSerial,
    [switch]$Apply,
    [string]$AdbPath,
    [string]$ApkPath = (Join-Path $PSScriptRoot '..\releases\phone-focus-1.2.0.apk')
)
$ErrorActionPreference='Stop'
if(-not $AdbPath){
    $taskAdbCommand=Get-Command adb -ErrorAction SilentlyContinue
    if($taskAdbCommand){$AdbPath=$taskAdbCommand.Source}
    else{$AdbPath=Join-Path $env:USERPROFILE 'Documents\Codex\2026-10-13\codex-v0-1-android-ai-android\work\toolchain\android-sdk\platform-tools\adb.exe'}
}
if(-not(Test-Path -LiteralPath $AdbPath)){throw 'ADB not found. Pass -AdbPath pointing to official Android platform-tools/adb.exe.'}
$taskDevicesText=(& $AdbPath devices -l) -join "`n"
if($LASTEXITCODE -ne 0){throw 'Cannot inspect ADB devices.'}
$taskOnline=@($taskDevicesText -split "`n" | Where-Object {$_ -match '^\S+\s+device(?:\s|$)'})
if(-not $DeviceSerial){
    [ordered]@{mode='read-only';connectedDevices=$taskOnline.Count;devices=$taskOnline;next='Connect the phone, accept USB debugging on it, then pass -DeviceSerial. Without -Apply this script does not install or grant anything.'}|ConvertTo-Json -Depth 3
    exit 0
}
if($DeviceSerial -notmatch '^[A-Za-z0-9][A-Za-z0-9._:-]*$'){throw 'Invalid ADB serial.'}
if(-not @($taskOnline | Where-Object {($_ -split '\s+')[0] -eq $DeviceSerial}).Count){throw 'Selected phone is not connected and authorized. Check adb devices -l; do not proceed on an unidentified phone.'}
function Invoke-SelectedAdb([string[]]$DeviceArgs){
    $taskNativeArgs=@('-s',$DeviceSerial)+$DeviceArgs
    $taskNativeOutput=(& $AdbPath @taskNativeArgs 2>&1 | Out-String).Trim()
    if($LASTEXITCODE -ne 0){throw "ADB operation failed: $taskNativeOutput"}
    return $taskNativeOutput
}
$taskSdk=[int](Invoke-SelectedAdb @('shell','getprop','ro.build.version.sdk'))
$taskUser=(Invoke-SelectedAdb @('shell','am','get-current-user')).Trim()
$taskFeatures=Invoke-SelectedAdb @('shell','pm','list','features')
$taskUsers=Invoke-SelectedAdb @('shell','pm','list','users')
$taskAccounts=Invoke-SelectedAdb @('shell','dumpsys','account')
$taskPolicy=Invoke-SelectedAdb @('shell','dumpsys','device_policy')
$taskOwnerMatch=[regex]::Match($taskPolicy,'(?is)Device Owner:\s*(?:\r?\n)?\s*admin=ComponentInfo\{([^}]+)\}')
$taskOwner=if($taskOwnerMatch.Success){$taskOwnerMatch.Groups[1].Value}else{''}
$taskOurOwner=$taskOwner -eq 'com.phonefocus.guard/com.phonefocus.guard.LockAdminReceiver' -or $taskOwner -eq 'com.phonefocus.guard/.LockAdminReceiver'
$taskUserCount=[regex]::Matches($taskUsers,'UserInfo\{').Count
$taskAccountCount=[regex]::Matches($taskAccounts,'Account\s*\{').Count
$taskIssues=@()
if($taskSdk -lt 28){$taskIssues+='Strict focus requires Android 9 / API 28 or higher.'}
if($taskFeatures -notmatch 'android.software.device_admin'){$taskIssues+='Device administration feature missing.'}
if($taskUser -ne '0'){$taskIssues+='Current user is not the primary user. This setup tool will not switch or remove users.'}
if(-not $taskOurOwner){
    if($taskUserCount -ne 1){$taskIssues+='Other users or work profiles detected. This tool will not remove them.'}
    if($taskAccountCount -gt 0){$taskIssues+='Accounts detected. This tool will not remove accounts or reset the phone.'}
    if($taskOwner -and -not $taskOurOwner){$taskIssues+='Another device owner is present.'}
}
[ordered]@{mode=if($Apply){'apply-requested'}else{'read-only'};serial=$DeviceSerial;androidApi=$taskSdk;currentUser=$taskUser;users=$taskUserCount;accountEntries=$taskAccountCount;deviceOwner=if($taskOwner){$taskOwner}else{'not detected; Android validates authoritatively'};issues=$taskIssues}|ConvertTo-Json -Depth 4
if(-not $Apply){exit 0}
if($taskIssues.Count -gt 0){throw 'Preflight did not pass. No installation or ownership change was performed. Keep the report and investigate; do not reset the phone automatically.'}
if(-not(Test-Path -LiteralPath $ApkPath)){throw 'Release APK missing. Build version 1.2.0 first.'}
$taskResolvedApk=(Resolve-Path -LiteralPath $ApkPath).Path
Invoke-SelectedAdb @('install','-r',$taskResolvedApk)
if(-not $taskOurOwner){Invoke-SelectedAdb @('shell','dpm','set-device-owner','com.phonefocus.guard/.LockAdminReceiver')}
Invoke-SelectedAdb @('shell','am','start','-n','com.phonefocus.guard/.MainActivity')
Write-Output 'Configuration command completed. On the phone open the management page, verify Device Owner is ready, enable exact alarms if requested, and run the 30-second focus test. Actual lock/release behavior remains unverified until that test.'
