"""Audit final signed 1.4.0 bytes and compiled capabilities; never installs or grants permissions."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import hashlib,json,os,re,shutil,subprocess,zipfile

ROOT=Path(__file__).resolve().parents[1]
TOOLS=Path(os.environ['USERPROFILE'])/'Documents/Codex/2026-10-13/codex-v0-1-android-ai-android/work/toolchain'
SDK=TOOLS/'android-sdk';AAPT=SDK/'build-tools/36.0.0/aapt.exe'
JAVA=TOOLS/'zulu17.68.17-ca-jdk17.0.20-win_x64/bin/java.exe'
APK='releases/phone-focus-1.4.0.apk'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def run(args,name):
    result=subprocess.run([str(a) for a in args],cwd=ROOT,capture_output=True,timeout=60)
    value=(result.stdout+result.stderr).decode('utf-8','replace')
    (ROOT/'qa'/name).write_text(value,encoding='utf-8')
    assert result.returncode==0,(name,result.returncode)
    return value
def main():
    badging=run([AAPT,'dump','badging',APK],'apk-badging-1.4.0.txt')
    assert "versionName='1.4.0'" in badging and "versionCode='6'" in badging
    assert "sdkVersion:'26'" in badging
    assert "launchable-activity: name='com.phonefocus.guard.ScheduleSetupActivity'" in badging
    permissions=run([AAPT,'dump','permissions',APK],'apk-permissions-1.4.0.txt')
    for denied in ['INTERNET','ACCESS_NETWORK_STATE','SYSTEM_ALERT_WINDOW','READ_PHONE_STATE','READ_EXTERNAL_STORAGE','QUERY_ALL_PACKAGES']:
        assert 'android.permission.'+denied not in permissions,denied
    manifest=run([AAPT,'dump','xmltree',APK,'AndroidManifest.xml'],'apk-manifest-1.4.0.txt')
    assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
    resources=run([AAPT,'dump','--values','resources',APK],'apk-resources-1.4.0.txt')
    resource=re.search(r'resource 0x[0-9a-f]+ com\.phonefocus\.guard:xml/schedule_accessibility:.*\n\s*\(string8\) "([^"]+)"',resources)
    assert resource
    access=run([AAPT,'dump','xmltree',APK,resource[1]],'apk-accessibility-1.4.0.txt')
    assert re.search(r'canRetrieveWindowContent[^\n]*0x0\s*$',access,re.M)
    assert re.search(r'canTakeScreenshot[^\n]*(?:0xffffffff|0x1)\s*$',access,re.M)
    assert re.search(r'accessibilityEventTypes[^\n]*0x20\s*$',access,re.M)
    assert 'canPerformGestures' not in access
    with zipfile.ZipFile(ROOT/APK) as archive:
        names=archive.namelist();dex=b''.join(archive.read(n) for n in names if n.endswith('.dex'))
        for symbol in [b'Lcom/phonefocus/guard/EntertainmentMonitor;',b'Lcom/phonefocus/guard/EntertainmentPolicy;',b'Lcom/phonefocus/guard/TimeRangeEditorDialog;',b'takeScreenshot',b'performGlobalAction',b'ChineseTextRecognizerOptions']:
            assert symbol in dex,symbol
        models=[n for n in names if n.startswith('assets/') and ('ocr' in n.lower() or 'tflite' in n.lower())]
        assert models,'Bundled OCR model must be inside delivered APK'
        assert any(n.endswith('.so') and 'ocr' in n.lower() for n in names),'Native OCR library bundled'
    certs=[]
    for version in ['1.4.0','1.3.1']:
        text=run([JAVA,'-jar',SDK/'build-tools/36.0.0/lib/apksigner.jar','verify','--verbose','--print-certs',f'releases/phone-focus-{version}.apk'],f'signature-entertainment-{version}.txt')
        certs.append(re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',text)[1])
    assert certs[0]==certs[1]
    lint=(ROOT/'app/build/reports/lint-results-release.txt').read_text(encoding='utf-8');summary=re.search(r'(\d+) errors, (\d+) warnings',lint)
    assert summary and summary[1]=='0'
    shutil.copyfile(ROOT/'app/build/reports/lint-results-release.txt',ROOT/'qa/lint-results-1.4.0.txt')
    assert 'BUILD SUCCESSFUL' in (ROOT/'qa/build-1.4.0.txt').read_text(encoding='utf-8-sig')
    tests=(ROOT/'qa/timer-test-1.4.0.txt').read_text(encoding='utf-8-sig')
    assert all(n in tests for n in ['129966','382','3363','173284','35 focused'])
    devices=run([SDK/'platform-tools/adb.exe','devices','-l'],'devices-1.4.0.txt')
    device_count=sum(bool(re.search(r'\sdevice\s',line)) for line in devices.splitlines())
    record={'evidenceId':'E-20261104-ENTERTAINMENT-1.4.0','verifiedAt':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'version':'1.4.0','versionCode':6,'apk':APK,'apkBytes':(ROOT/APK).stat().st_size,'apkSha256':sha(ROOT/APK),
        'signerSha256':certs[0],'sameSignerAs':'1.3.1','build':{'exitCode':0,'lintErrors':0,'lintWarnings':int(summary[2])},
        'tests':{'entertainmentRulesCadenceAndStaleResult':35,'screenSession':129966,'autoScreenOff':382,'ownerFocus':3363,'dailySchedule':173284,
            'scope':'Pure Java decisions and text fixtures, not model inference or OCR accuracy or Android behavior.'},
        'apkAudit':{'canTakeScreenshot':True,'canRetrieveWindowContent':False,'canPerformGestures':False,'internetPermission':False,
            'accessNetworkStatePermission':False,'contentPersistence':'No bitmap or recognized text storage; fixed result/time/settings only.',
            'bundledOcrModels':models,'screenshotMinSdk':30,'originalDailyScheduleMinSdk':26},
        'connectedDeviceCount':device_count,'deviceAcceptance':'PENDING: no installation, permission grant, screenshot OCR, physical lock, call, reboot or background execution acceptance.',
        'knownLimits':['OCR rules are not universal visual-semantic classification; misses text-free content and may misclassify.',
            'Study words override entertainment signals; no measured real screenshot accuracy.',
            'One lock request per ten-minute sample; user can still unlock with original credentials.',
            'Permission removal, uninstall, force-stop or OEM cleanup can pause checks; no all-model guarantee.',
            'No device wakeup; service reconnect restarts a full ten-minute wait; timer may be delayed in sleep.',
            'Protected/calling/locked/unknown screens and failed/uncertain/stale results do not trigger lock.'],
        'initialFailure':'AndroidX flag false blocked dependencies; enabled required AndroidX and rebuilt successfully. Initial null-package rule fixture fixed before final tests.',
        'sourceSha256':{str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in sorted((ROOT/'app/src/main').rglob('*')) if p.is_file()},
        'references':['https://developer.android.com/reference/android/accessibilityservice/AccessibilityService',
            'https://developers.google.com/ml-kit/vision/text-recognition/v2/android'],
        'validity':'Only these APK/source hashes; actual phone behavior must pass qa/entertainment-device-checklist.md.'}
    (ROOT/'qa/verification-1.4.0.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:record[k] for k in ['version','apkBytes','apkSha256','sameSignerAs','build','connectedDeviceCount']},indent=2))
if __name__=='__main__':main()
