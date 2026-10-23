"""Browser and asset acceptance for the GitHub Pages display site."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import hashlib
import json
import re
import urllib.request
from playwright.sync_api import sync_playwright
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
BASE='http://127.0.0.1:8765/'
QA=ROOT/'qa';QA.mkdir(exist_ok=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    assert sha(ROOT/'docs/download/phone-focus-1.3.1.apk')==sha(ROOT/'releases/phone-focus-1.3.1.apk')
    assert sha(ROOT/'docs/assets/phone-focus-anime-10s.mp4')==sha(ROOT/'promo/v3/output/phone-focus-anime-10s.mp4')
    assert urllib.request.urlopen(BASE).status==200
    errors=[];failures=[];viewports=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(channel='msedge',headless=True)
        page=browser.new_page(viewport={'width':1440,'height':1050},device_scale_factor=1)
        page.on('pageerror',lambda error:errors.append(str(error)))
        page.on('response',lambda response:failures.append({'status':response.status,'url':response.url}) if response.status>=400 else None)
        response=page.goto(BASE,wait_until='networkidle')
        assert response.status==200
        page.locator('.hero-art').wait_for(state='visible')
        page.locator('img').evaluate_all('async images => { images.forEach(i => i.loading = "eager"); await Promise.all(images.map(i => i.decode())); }')
        page.screenshot(path=str(QA/'site-desktop.png'),full_page=True)
        page.screenshot(path=str(QA/'site-desktop-hero.png'),clip={'x':0,'y':0,'width':1440,'height':920})
        Image.open(QA/'site-desktop-hero.png').convert('RGB').save(ROOT/'docs/assets/github-cover.jpg',quality=93,optimize=True)
        for width in [320,390,768,1440]:
            page.set_viewport_size({'width':width,'height':844 if width<760 else 1050})
            page.goto(BASE,wait_until='networkidle')
            page.locator('img').evaluate_all('async images => { images.forEach(i => i.loading = "eager"); await Promise.all(images.map(i => i.decode())); }')
            dimensions=page.evaluate('({viewport:innerWidth,content:document.documentElement.scrollWidth})')
            assert dimensions['content']<=dimensions['viewport'],f'Horizontal overflow {dimensions}'
            image_failures=page.locator('img').evaluate_all('(images)=>images.filter(i=>!i.complete||!i.naturalWidth).map(i=>i.src)')
            assert not image_failures,image_failures
            viewports.append(dimensions)
            if width==390:page.screenshot(path=str(QA/'site-mobile.png'),full_page=True)
        page.set_viewport_size({'width':1440,'height':1050})
        page.goto(BASE,wait_until='networkidle')
        with page.expect_download() as download:
            page.locator('.hero-actions a[download]').click()
        downloaded=download.value
        assert downloaded.suggested_filename=='phone-focus-1.3.1.apk'
        assert hashlib.sha256(Path(downloaded.path()).read_bytes()).hexdigest()==sha(ROOT/'releases/phone-focus-1.3.1.apk')
        page.locator('[data-play-film]').click()
        page.wait_for_function('document.querySelector("#promo-film").currentTime > 0.2')
        movie=page.locator('#promo-film').evaluate('(v)=>({duration:v.duration,width:v.videoWidth,height:v.videoHeight,paused:v.paused})')
        assert abs(movie['duration']-10)<.05 and movie['width']==1080 and movie['height']==1920
        page.locator('#promo-film').evaluate('(v)=>v.pause()')
        page.locator('summary').nth(1).click()
        assert page.locator('details').nth(1).get_attribute('open') is not None
        assert page.get_by_text('手机授权模式可通过撤销权限',exact=False).is_visible()
        assert not errors,errors
        assert not failures,failures
        browser.close()
    record={'evidence_id':'E-20261023-GITHUB-SITE','verified_at':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'local_http_status':200,'browser':'Microsoft Edge via Playwright, headless','viewports':viewports,
        'console_errors':errors,'http_failures':failures,'download_original_apk_hash_match':True,'video_original_hash_match':True,
        'video_playback':movie,'faq_toggle':'PASS','all_images_loaded':True,'visual_review':'PENDING: view desktop and mobile screenshots.',
        'apk_sha256':sha(ROOT/'releases/phone-focus-1.3.1.apk'),
        'site_files_sha256':{str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in sorted((ROOT/'docs').rglob('*')) if p.is_file()},
        'scope':'Website display, responsive layout, video playback and downloadable artifact integrity. Does not prove native Android enforcement.'}
    (QA/'site-verification.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({key:record[key] for key in ['viewports','console_errors','http_failures','video_playback','download_original_apk_hash_match']},indent=2))

if __name__=='__main__':main()
