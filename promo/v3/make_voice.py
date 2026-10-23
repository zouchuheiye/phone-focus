"""Generate a generic synthetic Mandarin female voice; no person imitation."""
from pathlib import Path
import asyncio
import json
import sys
import urllib.request

ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parents[1]/'.local/media-python'))
import edge_tts

TEXT='考研戒手机，学习期间戒手机。帮孩子少玩手机，家长和备考党必备小软件。关注加群，免费领取！'
VOICE='zh-CN-XiaoxiaoNeural'

async def main():
    out=ROOT/'output';out.mkdir(parents=True,exist_ok=True)
    qa=ROOT/'qa';qa.mkdir(parents=True,exist_ok=True)
    proxies=urllib.request.getproxies()
    proxy=proxies.get('https') or proxies.get('http')
    communicate=edge_tts.Communicate(TEXT,VOICE,rate='+25%',pitch='+2Hz',proxy=proxy,
                                     boundary='WordBoundary',connect_timeout=10,receive_timeout=30)
    boundaries=[]
    with (out/'female-voice-raw.mp3').open('wb') as stream:
        async for chunk in communicate.stream():
            if chunk['type']=='audio':stream.write(chunk['data'])
            elif chunk['type']=='WordBoundary':boundaries.append({key:chunk[key] for key in ['offset','duration','text']})
    (qa/'voice-boundaries.json').write_text(json.dumps(boundaries,ensure_ascii=False,indent=2),encoding='utf-8')
    (qa/'voice.json').write_text(json.dumps({'voice':VOICE,'gender':'Female','synthetic':True,'text':TEXT,
        'rate':'+25%','pitch':'+2Hz','engine':'edge-tts','proxy_used':bool(proxy),
        'scope':'Only this public promotional script is sent for speech synthesis. User screenshot images are not sent.'},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Female voice generated; word boundaries saved.',flush=True)

if __name__=='__main__':asyncio.run(main())
