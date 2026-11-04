"""Audit final signed 1.5.0 bytes and compiled capabilities; never installs or grants permissions."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import hashlib,json,os,re,shutil,subprocess,zipfile

ROOT=Path(__file__).resolve().parents[1]
TOOLS=Path(os.environ['USERPROFILE'])/'Documents/Codex/2026-10-13/codex-v0-1-android-ai-android/work/toolchain'
SDK=TOOLS/'android-sdk';AAPT=SDK/'build-tools/36.0.0/aapt.exe'
JAVA=TOOLS/'zulu17.68.17-ca-jdk17.0.20-win_x64/bin/java.exe'
APK='releases/phone-focus-1.5.0.apk'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def run(args,name):
    result=subprocess.run([str(a) for a in args],cwd=ROOT,capture_output=True,timeout=60)
    value=(result.stdout+result.stderr).decode('utf-8','replace')
    (ROOT/'qa'/name).write_text(value,encoding='utf-8')
    assert result.returncode==0,(name,result.returncode)
    return value
def main():
    badging=run([AAPT,'dump','badging',APK],'apk-badging-1.5.0.txt')
    assert "versionName='1.5.0'" in badging and "versionCode='7'" in badging
    assert "sdkVersion:'26'" in badging
    assert "launchable-activity: name='com.phonefocus.guard.ScheduleSetupActivity'" in badging
    permissions=run([AAPT,'dump','permissions',APK],'apk-permissions-1.5.0.txt')
    for denied in ['ACCESS_NETWORK_STATE','SYSTEM_ALERT_WINDOW','READ_PHONE_STATE','READ_EXTERNAL_STORAGE','QUERY_ALL_PACKAGES']:
        assert 'android.permission.'+denied not in permissions,denied
    assert 'android.permission.INTERNET' in permissions
    manifest=run([AAPT,'dump','xmltree',APK,'AndroidManifest.xml'],'apk-manifest-1.5.0.txt')
    assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
    resources=run([AAPT,'dump','--values','resources',APK],'apk-resources-1.5.0.txt')
    resource=re.search(r'resource 0x[0-9a-f]+ com\.phonefocus\.guard:xml/schedule_accessibility:.*\n\s*\(string8\) "([^"]+)"',resources)
    assert resource
    access=run([AAPT,'dump','xmltree',APK,resource[1]],'apk-accessibility-1.5.0.txt')
    assert re.search(r'canRetrieveWindowContent[^\n]*0x0\s*$',access,re.M)
    assert re.search(r'canTakeScreenshot[^\n]*(?:0xffffffff|0x1)\s*$',access,re.M)
    assert re.search(r'accessibilityEventTypes[^\n]*0x20\s*$',access,re.M)
    assert 'canPerformGestures' not in access
    with zipfile.ZipFile(ROOT/APK) as archive:
        names=archive.namelist();dex=b''.join(archive.read(n) for n in names if n.endswith('.dex'))
        for symbol in [b'Lcom/phonefocus/guard/EntertainmentMonitor;',b'Lcom/phonefocus/guard/AgnesClient;',b'Lcom/phonefocus/guard/AgnesConfig;',b'Lcom/phonefocus/guard/AgnesSetupActivity;',b'AndroidKeyStore',b'AES/GCM/NoPadding',b'Lcom/phonefocus/guard/TimeRangeEditorDialog;',b'takeScreenshot',b'performGlobalAction']:
            assert symbol in dex,symbol
        models=[n for n in names if n.startswith('assets/') and ('ocr' in n.lower() or 'tflite' in n.lower())]
        assert not models,'Cloud version must not bundle unused OCR models'
        assert b'Lcom/phonefocus/guard/EntertainmentPolicy;' not in dex
        assert b'Lcom/google/mlkit/' not in dex
        private=json.loads((ROOT/'.local/agnes-phone-config.json').read_text(encoding='utf-8'))['api_key'].encode()
        for name in names:
            assert private not in archive.read(name),'Private API key must not be in public APK'
    certs=[]
    for version in ['1.5.0','1.4.0']:
        text=run([JAVA,'-jar',SDK/'build-tools/36.0.0/lib/apksigner.jar','verify','--verbose','--print-certs',f'releases/phone-focus-{version}.apk'],f'signature-entertainment-{version}.txt')
        certs.append(re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',text)[1])
    assert certs[0]==certs[1]
    lint=(ROOT/'app/build/reports/lint-results-release.txt').read_text(encoding='utf-8');summary=re.search(r'(\d+) errors, (\d+) warnings',lint)
    assert summary and summary[1]=='0'
    shutil.copyfile(ROOT/'app/build/reports/lint-results-release.txt',ROOT/'qa/lint-results-1.5.0.txt')
    assert 'BUILD SUCCESSFUL' in (ROOT/'qa/build-1.5.0.txt').read_text(encoding='utf-8-sig')
    tests=(ROOT/'qa/timer-test-1.5.0.txt').read_text(encoding='utf-8-sig')
    assert all(n in tests for n in ['129966','382','3363','173284','35 legacy'])
    devices=run([SDK/'platform-tools/adb.exe','devices','-l'],'devices-1.5.0.txt')
    device_count=sum(bool(re.search(r'\sdevice\s',line)) for line in devices.splitlines())
    import xml.etree.ElementTree as ET
    unit=ET.parse(ROOT/'qa/agnes-unit-tests.xml').getroot()
    assert unit.get('tests')=='8' and unit.get('failures')=='0' and unit.get('errors')=='0'
    live=json.loads((ROOT/'qa/agnes-live-verification.json').read_text(encoding='utf-8'))
    assert len(live['checks'])==3 and live['syntheticOnly'] is True
    assert live['checks'][0]['wouldLock'] is True and all(row['wouldLock'] is False for row in live['checks'][1:])
    files=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).split(b'\0')
    for raw in files:
        if not raw:continue
        path=ROOT/raw.decode('utf-8')
        if path.is_file():assert private not in path.read_bytes(),'Private API key found outside ignored config'
    record={'evidenceId' :'E-20261104-AGNES-1.5.0','verifiedAt':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'version':'1.5.0','versionCode':7,'apk':APK,'apkBytes':(ROOT/APK).stat().st_size,'apkSha256':sha(ROOT/APK),
        'signerSha256':certs[0],'sameSignerAs':'1.4.0','build':{'exitCode':0,'lintErrors':0,'lintWarnings':int(summary[2])},
        'liveSyntheticVision':live,'accountKeyInPublishableFiles':False,
        'tests':{'legacyPolicyFixturesAndCurrentCadence':35,'protocolAndHttpTests':8,'screenSession':129966,'autoScreenOff':382,'ownerFocus':3363,'dailySchedule':173284,
            'scope':'Legacy Java fixtures, current cadence/old-result guards and production protocol/HTTP mock tests. Live synthetic image tests are separate; no Android runtime claim.'},
        'apkAudit':{'canTakeScreenshot':True,'canRetrieveWindowContent':False,'canPerformGestures':False,'internetPermission':True,
            'accessNetworkStatePermission':False,'contentPersistence':'No bitmap/model text storage; key encrypted using device Android Keystore; fixed result/time/settings only.',
            'bundledOcrModels':models,'accountKeyEmbeddedInApk':False,'cloudConsentFreshDefaultOff':True,'model':'agnes-2.5-flash','endpoint':'https://apihub.agnes-ai.com/v1/chat/completions','screenshotMinSdk':30,'originalDailyScheduleMinSdk':26},
        'connectedDeviceCount':device_count,'deviceAcceptance':'PENDING: no installation, permission grant, screenshot OCR, physical lock, call, reboot or background execution acceptance.',
        'knownLimits':['Visual model can misclassify; self-reported confidence >=0.90 is a gate, not measured accuracy.',
            'Cloud screenshot+package transfer requires explicit fresh opt-in and personal credential import.',
            'One lock request per sample; user can still unlock; no all-model or irreversible enforcement claim.',
            'Network/error/invalid/uncertain/stale/changed-window responses skip without OCR fallback.',
            'Provider handles screenshots under its policy; application does not persist images or model answers.',
            'API call timeout 30s; total result-age cap 45s; no wakeup, ten-minute cadence restarts on reconnect.'],
        'initialFailure':'Gradle unit worker could not load classes from a non-ASCII path. Scoped ASCII drive alias in build.ps1 fixes this; Android test stubs required readAllBytes/write instead of readString/writeString.',
        'sourceSha256':{str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in sorted((ROOT/'app/src/main').rglob('*')) if p.is_file()},
        'references':['https://developer.android.com/reference/android/accessibilityservice/AccessibilityService',
            'https://agnes-ai.com/zh-Hans/docs/agnes-25-flash'],
        'validity':'Only these APK/source hashes; actual phone behavior must pass qa/agnes-device-checklist.md.'}
    (ROOT/'qa/verification-1.5.0.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:record[k] for k in ['version','apkBytes','apkSha256','sameSignerAs','build','connectedDeviceCount']},indent=2))
if __name__=='__main__':main()
