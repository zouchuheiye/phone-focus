"""Verify public repository, release assets, deployed static bytes and hosted playback."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import hashlib
import json
import subprocess
import time
import urllib.request
import urllib.error
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parents[1]
REPO='zouchuheiye/phone-focus'
SITE='https://zouchuheiye.github.io/phone-focus/'
def gh(*args):return json.loads(subprocess.check_output(['gh',*args],cwd=ROOT).decode('utf-8'))
def sha(data):return hashlib.sha256(data).hexdigest()
def get(path):
    print(f'Check hosted asset: {path or "index.html"}',flush=True)
    result=subprocess.run(['curl.exe','--location','--fail','--silent','--show-error','--connect-timeout','8','--max-time','35',
        '--write-out','\n%{http_code}',SITE+path],capture_output=True,timeout=40,check=True)
    body,status=result.stdout.rsplit(b'\n',1)
    return int(status),body

def main():
    repo=gh('api',f'repos/{REPO}')
    assert repo['private'] is False and repo['default_branch']=='main'
    commit=gh('api',f'repos/{REPO}/commits/main')
    tree=gh('api',f'repos/{REPO}/git/trees/main?recursive=1')
    paths=[item['path'] for item in tree['tree']]
    denied=[name for name in paths if any(part in {'.local','.gradle','node_modules','__pycache__'} for part in Path(name).parts)
            or Path(name).suffix in {'.jks','.keystore','.pyc'} or Path(name).name in {'local.properties','signing.json'}]
    assert not denied,denied
    release=gh('release','view','v1.3.1','--repo',REPO,'--json','url,tagName,isDraft,isPrerelease,assets')
    assert not release['isDraft'] and not release['isPrerelease']
    release_assets={asset['name']:asset for asset in release['assets']}
    expected={'phone-focus-1.3.1.apk':ROOT/'releases/phone-focus-1.3.1.apk',
        'phone-focus-anime-10s.mp4':ROOT/'promo/v3/output/phone-focus-anime-10s.mp4'}
    for name,path in expected.items():
        assert release_assets[name]['state']=='uploaded'
        assert release_assets[name]['digest']=='sha256:'+sha(path.read_bytes())
    attempts=[];last_error=None
    for attempt in range(35):
        pages=gh('api',f'repos/{REPO}/pages')
        state=pages.get('status');attempts.append({'attempt':attempt+1,'status':state})
        print(f'Pages status: {state}',flush=True)
        if state=='errored':raise RuntimeError('GitHub Pages reported errored; inspect Pages build logs')
        try:
            status,html=get('')
            if status==200 and sha(html)==sha((ROOT/'docs/index.html').read_bytes()):break
        except (urllib.error.URLError,TimeoutError,subprocess.SubprocessError) as exc:last_error=str(exc)
        time.sleep(8)
    else:raise RuntimeError(f'Pages did not serve matching site within polling window: {last_error}')
    checked={}
    for path in ['styles.css','app.js','assets/hero.jpg','assets/mark.svg','assets/phone-focus-anime-10s.mp4','download/phone-focus-1.3.1.apk']:
        status,data=get(path)
        assert status==200 and sha(data)==sha((ROOT/'docs'/path).read_bytes()),path
        checked[path]={'http':status,'sha256':sha(data),'matches_local':True}
    with sync_playwright() as p:
        browser=p.chromium.launch(channel='msedge',headless=True)
        page=browser.new_page(viewport={'width':1440,'height':1050})
        errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        print('Check live Edge playback',flush=True)
        page.goto(SITE,wait_until='domcontentloaded')
        assert page.title()=='去做正事 · 给正事，留一段时间'
        page.locator('img').evaluate_all('async images=>{images.forEach(i=>i.loading="eager");await Promise.all(images.map(i=>i.decode()));}')
        page.screenshot(path=str(ROOT/'qa/site-live-desktop.png'),full_page=True)
        page.locator('[data-play-film]').click()
        page.wait_for_function('document.querySelector("#promo-film").currentTime>.2')
        playback=page.locator('#promo-film').evaluate('(v)=>({duration:v.duration,width:v.videoWidth,height:v.videoHeight})')
        assert playback=={'duration':10,'width':1080,'height':1920}
        assert not errors,errors
        browser.close()
    record={'evidence_id':'E-20261023-GITHUB-PUBLISHED','verified_at':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'repository':repo['html_url'],'visibility':'PUBLIC','default_branch':'main','verified_commit':commit['sha'],
        'release_url':release['url'],'release_assets':[{key:asset.get(key) for key in ['name','size','url','state','digest']} for asset in release['assets']],
        'pages_url':pages['html_url'],'pages_source':pages['source'],'pages_state':pages.get('status'),
        'pages_html_http':200,'pages_html_sha256':sha((ROOT/'docs/index.html').read_bytes()),'checked_assets':checked,
        'live_edge_video_playback':playback,'live_browser_errors':errors,'remote_tree_forbidden_paths':denied,
        'remote_tree_entries':len(paths),'polls':attempts,
        'scope':'Repository, release digests, public hosted static byte integrity and actual hosted browser playback. Android behavior gate unchanged.'}
    (ROOT/'qa/github-publication.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({key:record[key] for key in ['repository','release_url','pages_url','pages_state','pages_html_http','live_edge_video_playback']},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
