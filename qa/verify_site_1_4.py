"""Check updated download, permissions disclosure and responsive page locally."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
from playwright.sync_api import sync_playwright
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    failures=[];errors=[];widths=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(channel='msedge',headless=True)
        page=browser.new_page(viewport={'width':1440,'height':1050})
        page.on('pageerror',lambda e:errors.append(str(e)))
        page.on('response',lambda r:failures.append({'status':r.status,'url':r.url}) if r.status>=400 else None)
        assert page.goto('http://127.0.0.1:8765/',wait_until='networkidle').status==200
        page.locator('img').evaluate_all('async images=>{images.forEach(i=>i.loading="eager");await Promise.all(images.map(i=>i.decode()));}')
        for width in [320,390,768,1440]:
            page.set_viewport_size({'width':width,'height':1050})
            dims=page.evaluate('({width:innerWidth,content:document.documentElement.scrollWidth})')
            assert dims['content']==dims['width'],dims
            widths.append(dims)
        with page.expect_download() as download:page.locator('.hero-actions a[download]').click()
        assert download.value.suggested_filename=='phone-focus-1.4.0.apk'
        assert sha(Path(download.value.path()))==sha(ROOT/'releases/phone-focus-1.4.0.apk')
        page.locator('summary').filter(has_text='新增：每10分钟').click()
        assert page.get_by_text('Android 11 及以上，在首页另行开启',exact=False).is_visible()
        page.locator('summary').filter(has_text='截图检查会保存').click()
        assert page.get_by_text('画面可能含当前可见的私人信息',exact=False).is_visible()
        page.screenshot(path=str(ROOT/'qa/site-1.4.0-desktop.png'),full_page=True)
        page.set_viewport_size({'width':390,'height':844})
        page.screenshot(path=str(ROOT/'qa/site-1.4.0-mobile.png'),full_page=True)
        assert not failures,failures
        assert not errors,errors
        browser.close()
    result={'evidenceId':'E-20261104-SITE-1.4.0','verifiedAt':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'viewports':widths,'apkDownloadSha256Matches':True,'apkSha256':sha(ROOT/'releases/phone-focus-1.4.0.apk'),
        'entertainmentAndScreenshotDisclosure':'PASS','consoleErrors':errors,'httpFailures':failures,
        'visualReview':'PENDING','scope':'Local website update and download; Android runtime not tested.'}
    (ROOT/'qa/site-verification-1.4.0.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
