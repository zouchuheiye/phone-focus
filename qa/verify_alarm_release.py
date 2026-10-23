"""Inspect the signed deliverable, not just source XML. No device mutations."""
from pathlib import Path
import datetime, hashlib, json, os, re, shutil, subprocess, zipfile

root = Path(__file__).resolve().parents[1]
toolchain = Path(os.environ['USERPROFILE']) / 'Documents/Codex/2026-10-13/codex-v0-1-android-ai-android/work/toolchain'
sdk = toolchain / 'android-sdk'
aapt = sdk / 'build-tools/36.0.0/aapt.exe'
java = toolchain / 'zulu17.68.17-ca-jdk17.0.20-win_x64/bin/java.exe'
signer = sdk / 'build-tools/36.0.0/lib/apksigner.jar'
apk = 'releases/phone-focus-1.3.1.apk'
def run(args, log):
    result = subprocess.run([str(a) for a in args], cwd=root, capture_output=True)
    value = (result.stdout + result.stderr).decode('utf-8', 'replace')
    (root / 'qa' / log).write_text(value, encoding='utf-8')
    if result.returncode: raise RuntimeError(f'{log}: exit {result.returncode}')
    return value
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

with zipfile.ZipFile(root/apk) as archive:
    dex = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    assert b'Lcom/phonefocus/guard/TimeRangeEditorDialog;' in dex
    assert b'Landroid/widget/NumberPicker;' in dex

badging = run([aapt,'dump','badging',apk],'apk-badging-1.3.1.txt')
assert "versionName='1.3.1'" in badging and "versionCode='5'" in badging
assert "launchable-activity: name='com.phonefocus.guard.ScheduleSetupActivity'" in badging
assert "uses-feature-not-required: name='android.software.device_admin'" in badging
permissions = run([aapt,'dump','permissions',apk],'apk-permissions-1.3.1.txt')
assert 'android.permission.INTERNET' not in permissions
assert 'android.permission.SYSTEM_ALERT_WINDOW' not in permissions
manifest = run([aapt,'dump','xmltree',apk,'AndroidManifest.xml'],'apk-manifest-1.3.1.txt')
assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
assert 'ScheduleAlarmReceiver' in manifest and 'ScheduleAccessibilityService' in manifest
resources = run([aapt,'dump','--values','resources',apk],'apk-resources-1.3.1.txt')
resource = re.search(r'resource 0x[0-9a-f]+ com\.phonefocus\.guard:xml/schedule_accessibility:.*\n\s*\(string8\) "([^"]+)"', resources)
assert resource, 'Find optimized XML filename'
access = run([aapt,'dump','xmltree',apk,resource[1]],'apk-accessibility-1.3.1.txt')
assert re.search(r'canRetrieveWindowContent[^\n]*0x0\s*$', access, re.M), access
assert re.search(r'accessibilityEventTypes[^\n]*0x20\s*$', access, re.M), access
assert 'canPerformGestures' not in access
new_sign = run([java,'-jar',signer,'verify','--verbose','--print-certs',apk],'signature-1.3.1.txt')
old_sign = run([java,'-jar',signer,'verify','--verbose','--print-certs','releases/phone-focus-1.3.0.apk'],'signature-upgrade-from-1.3.0.txt')
certificate = lambda value: re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',value)[1]
assert certificate(new_sign) == certificate(old_sign), 'Upgrade signer must match'
lint = (root / 'app/build/reports/lint-results-release.txt').read_text(encoding='utf-8')
summary = re.search(r'(\d+) errors, (\d+) warnings',lint)
assert summary and summary[1] == '0'
shutil.copyfile(root/'app/build/reports/lint-results-release.txt',root/'qa/lint-results-1.3.1.txt')
build = (root/'qa/build-1.3.1.txt').read_text(encoding='utf-8-sig',errors='replace')
assert 'BUILD SUCCESSFUL' in build and 'APK ready:' in build
tests = (root/'qa/timer-test-1.3.1.txt').read_text(encoding='utf-8-sig')
assert all(str(count) in tests for count in [129966,382,3363,173284])
devices = run([sdk/'platform-tools/adb.exe','devices','-l'],'devices-1.3.1.txt')
connected = [line for line in devices.splitlines() if re.search(r'\sdevice\s',line)]
paths = sorted((root/'app/src/main').rglob('*')) + [root/'app/build.gradle',root/'test.ps1',root/'qa/DailyScheduleTest.java']
report = {
    'evidenceId':'E-20261022-ALARMUI-1.3.1',
    'verifiedAt':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),
    'version':'1.3.1', 'versionCode':5, 'apk':apk,
    'apkBytes':(root/apk).stat().st_size, 'apkSha256':sha(root/apk),
    'signerSha256':certificate(new_sign), 'sameSignerAs':'1.3.0',
    'tests':{'command':'.\\test.ps1','exitCode':0,'screenSession':129966,'autoScreenOff':382,'ownerFocus':3363,'dailySchedule':173284,'scope':'Pure Java rules; separated ranges, independent switches, legacy migration, daily union boundaries, recurrence, overlap, overnight, DST and serialization. No Android behavior claim.'},
    'build':{'command':'.\\build.ps1','exitCode':0,'lintErrors':int(summary[1]),'lintWarnings':int(summary[2]),'log':'qa/build-1.3.1.txt'},
    'apkAudit':{'launcher':'ScheduleSetupActivity','accessibilitySystemBindProtected':True,'retrieveContent':False,'performGestures':False,'events':'TYPE_WINDOW_STATE_CHANGED only','internetPermission':False,'systemAlertWindowPermission':False,'deviceAdminRequired':False},
    'connectedDeviceCount':len(connected), 'deviceAcceptance':'PENDING: no device installation, permission grant, overlay, call, reboot or background runtime validation performed.',
    'knownLimits':['Phone-only schedule is bypassable by permission removal, settings changes, uninstall or force-stop.','OEM compatibility and background survival require real-device checks.','Unknown foreground on service connection waits for next window-state event.','No exact-alarm permission may delay asleep boundary.','No system credential changes or system unlock automation.'],
    'initialFailure':'qa/build-1.3.1-initial-failure.txt: corrected a missing fourth setPadding argument; final build passes.',
    'uiImplementation':{'editor':'Two rectangular NumberPicker hour/minute wheel panels in a state-preserving DialogFragment; no clock-face picker in daily setup.','list':'Multiple start/end cards with per-window switches and delete confirmation.','compiledEditorPresent':True,'visualAndTouchAcceptance':'PENDING: no emulator or physical device runtime/screenshot.'},
    'sourceSha256':{str(p.relative_to(root)).replace('\\','/'):sha(p) for p in paths if p.is_file()},
    'validity':'Only this APK and source hashes; target phones must complete qa/daily-schedule-device-checklist.md.'
}
value=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
(root/'qa/verification-1.3.1.json').write_text(value,encoding='utf-8')
(root/'qa/verification.json').write_text(value,encoding='utf-8')
print(json.dumps({k:report[k] for k in ['apkBytes','apkSha256','signerSha256','build','connectedDeviceCount']},ensure_ascii=False,indent=2))
