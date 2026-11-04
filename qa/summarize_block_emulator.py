"""Summarize an actual private APK run without copying credentials or private APK bytes to public QA."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import json,hashlib,subprocess,os,shutil

ROOT=Path(__file__).resolve().parents[1]
SERIAL='emulator-5580'
ADB=Path(os.environ['USERPROFILE'])/'Documents/Codex/2026-10-13/codex-v0-1-android-ai-android/work/toolchain/android-sdk/platform-tools/adb.exe'
def adb(*args):return subprocess.check_output([str(ADB),'-s',SERIAL,*args],cwd=ROOT)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    local=ROOT/'.local/simulation'
    subprocess.check_call([str(ADB),'-s',SERIAL,'pull','/sdcard/Android/data/com.phonefocus.guard/files/simulation16',str(local/'device16')],stdout=subprocess.DEVNULL)
    folder=local/'device16/simulation16';result=json.loads((folder/'report.json').read_text(encoding='utf-8'))
    assert result.get('passed') is True and result.get('naturalTenMinuteTest') is True,result.get('error')
    cases={row['case']:row for row in result['cases']}
    for name in ['automatic-config','consent-cadence-default','accelerated-game','duration-ui','cross-app-block','wake-and-pause','expiry','study-non-trigger','blank-non-trigger','simulated-voip-protection','edited-duration-applied','natural-ten-minute-game']:
        assert name in cases,name
    natural=cases['natural-ten-minute-game']
    assert natural['noEarlyBlock'] and natural['screenStaysInteractive'] and natural['overlayVisible']
    assert 595_000<=natural['elapsedMs']<650_000 and natural['detectionDeadlineInjection'] is False
    assert 29*60_000<natural['blockRemainingMs']<=30*60_000
    private_apk=ROOT/'.local/personal-release-1.6.0/phone-focus-1.6.0-personal.apk'
    remote=adb('shell','pm','path','com.phonefocus.guard').decode('utf-8').strip().split('package:',1)[1]
    installed=local/'installed-personal-1.6.0.apk'
    subprocess.check_call([str(ADB),'-s',SERIAL,'pull',remote,str(installed)],stdout=subprocess.DEVNULL)
    assert sha(installed)==sha(private_apk),'Installed test APK differs from delivered artifact'
    summary={'evidenceId':'E-20261104-ANDROID11-BLOCK-EMULATOR','verifiedAt':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'environment':{'type':'official Android Emulator','api':30,'systemImage':'system-images;android-30;default;x86_64','acceleration':'WHPX','serial':SERIAL},
        'testedArtifact':{'version':'1.6.0-personal','versionCode':10,'sha256':sha(private_apk),'installedApkHashMatches':True,'privateApkNotPublished':True},
        'realModelEndpoint':'https://apihub.agnes-ai.com/v1/chat/completions','model':'agnes-2.5-flash',
        'cases':result['cases'],'passed':True,
        'method':'Original personal APK unchanged. Same-signer test instrumentation; service enabled by test ADB and cloud consent clicked through real UI. Fast cases inject test deadlines in emulator memory/state; the final detection interval waits the original full600 seconds with no deadline injection. Screens are synthetic fixtures.',
        'scope':'Actual Android11 emulator runtime, Keystore, screenshot, live model and blocking overlay. Not an OEM physical-phone acceptance or measured screenshot accuracy benchmark. Block expiry is accelerated, not a real30-minute wait.',
        'sourceLogsPrivate':'.local/simulation/instrumentation16.txt',
        'testDriverSha256':sha(local/'android-harness/driver/src/main/java/com/phonefocus/simulation/SmokeInstrumentation.java')}
    (ROOT/'qa/emulator-verification-1.6.0.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    shutil.copyfile(folder/'05-persistent-block.png',ROOT/'qa/emulator-block-1.6.0.png')
    personal=ROOT/'.local/personal-release-1.6.0/verification.json';p=json.loads(personal.read_text(encoding='utf-8'));p['emulatorAcceptance']='PASS: qa/emulator-verification-1.6.0.json';personal.write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'passed':True,'actualOriginalIntervalMs':natural['elapsedMs'],'thirtyMinuteRemainingMs':natural['blockRemainingMs'],'caseCount':len(cases),'exactInstalledArtifact':True},indent=2))
if __name__=='__main__':main()
