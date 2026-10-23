"""MV-02: original motion graphics illustrating Phone Focus 1.3.1 daily ranges."""
from pathlib import Path
from functools import lru_cache
import argparse
import importlib.util
import json
import math
import subprocess
import sys
import time
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('phone_focus_art',ROOT.parent/'render_mv.py')
art=importlib.util.module_from_spec(spec);spec.loader.exec_module(art)
from music import compose

OUT,QA=ROOT/'output',ROOT/'qa'
W,H,FPS,SECONDS=1080,1920,30,36
CREAM,PAPER,PINE,GREEN,MUTED,GOLD=art.CREAM,art.PAPER,art.PINE,art.GREEN,art.MUTED,art.GOLD
CORAL='#d59072'
txt,rr,paste,logo=art.txt,art.rr,art.paste,art.logo
ease,clamp=art.ease,art.clamp

def center(im,value,y,size,color=PINE,bold=False,mono=False):
    txt(im,value,W/2,y,size,color,bold,'mt',mono)

def chrome(im,t,dark=False):
    foreground=PAPER if dark else PINE; muted='#c0cfb9' if dark else MUTED
    paste(im,logo(50),72,72);txt(im,'去做正事',140,78,30,foreground,True)
    txt(im,'DAILY FOCUS / 1.3.1',1008,86,20,muted,anchor='rt',mono=True)
    rr(im,(72,1716,600,1772),28,'#385343' if dark else '#dce5d5')
    txt(im,'功能动画示意 · 非实机录屏',336,1732,24,'#e3ebda' if dark else GREEN,anchor='mt')
    txt(im,'给正事，留一段时间。',72,1800,26,muted)
    txt(im,f'{int(t):02d} / 36',1008,1806,21,muted,anchor='rt',mono=True)
    d=ImageDraw.Draw(im);d.line((72,1850,1008,1850),fill='#52694e' if dark else '#ced8c7',width=2)
    d.line((72,1850,72+936*clamp(t/SECONDS),1850),fill=GOLD if dark else GREEN,width=5)

def base(dark=False): return art.background(dark).copy()

def toggle(im,x,y,on=True,pulse=0):
    rr(im,(x,y,x+112,y+58),29,GREEN if on else '#c9d0c3')
    xx=x+83 if on else x+29
    ImageDraw.Draw(im).ellipse((xx-21,y+8,xx+21,y+50),fill=PAPER)
    if pulse>0:
        r=35+24*pulse;ImageDraw.Draw(im).ellipse((xx-r,y+29-r,xx+r,y+29+r),outline=GOLD,width=3)

def range_card(im,x,y,width,start,end,number=1,on=True,pulse=0):
    rr(im,(x,y,x+width,y+248),26,'#fffcf3', '#d8e0d0',2)
    txt(im,f'时段 {number}  ·  每天',x+26,y+22,25,MUTED)
    toggle(im,x+width-140,y+15,on,pulse)
    gap=36; cell=(width-52-gap)/2;ink=PINE if on else MUTED
    for j,(label,value) in enumerate([('开始时间',start),('结束时间 · 次日' if end<start else '结束时间',end)]):
        xx=x+26+j*(cell+gap)
        rr(im,(xx,y+86,xx+cell,y+215),17,'#eef0e6', '#cbd6c4',2)
        txt(im,label,xx+cell/2,y+103,22,ink,anchor='mt')
        txt(im,value,xx+cell/2,y+143,44,ink,True,'mt',True)
    txt(im,'—',x+width/2,y+141,28,MUTED,anchor='mt')

def header(im,first,second,sub,dark=False):
    color=PAPER if dark else PINE
    txt(im,first,80,240,80,color,True)
    txt(im,second,80,348,80,GOLD if dark else GREEN,True)
    txt(im,sub,84,490,31,'#c0cfb9' if dark else MUTED)

@lru_cache(maxsize=16)
def news_card(title):
    im=Image.new('RGBA',(820,185));rr(im,(0,0,819,184),26,PAPER)
    rr(im,(24,28,146,151),23,'#d5dfc8');txt(im,'···',84,57,42,GREEN,True,'mt')
    txt(im,title,177,32,33,PINE,True);txt(im,'再看一下，再往下滑一下。',177,96,25,MUTED)
    return im

def wheel(im,x,y,width,title,hour,minute,progress=1):
    rr(im,(x,y,x+width,y+460),25,PAPER,'#a5b99b',3)
    txt(im,title,x+width/2,y+28,30,GREEN,True,'mt')
    rr(im,(x+18,y+183,x+width-18,y+282),16,'#e3eadb')
    d=ImageDraw.Draw(im)
    for yy in [y+183,y+282]:d.line((x+28,yy,x+width-28,yy),fill='#bdcdb1',width=2)
    offset=65*(1-ease(progress));hx=x+width*.34;mx=x+width*.70
    for delta,yy in [(-1,y+115+offset),(0,y+210+offset),(1,y+317+offset)]:
        selected=delta==0; color=PINE if selected else '#a7b39b';size=67 if selected else 41
        txt(im,f'{(hour+delta)%24:02d}',hx,yy,size,color,selected,'mt',True)
        txt(im,f'{(minute+delta)%60:02d}',mx,yy,size,color,selected,'mt',True)
    txt(im,':',x+width*.52,y+210+offset,59,PINE,True,'mt',True)
    txt(im,'小时          分钟',x+width/2,y+400,22,MUTED,anchor='mt')

def timeline(im,current,dark=False):
    x,y,span=120,665,840
    colors=[GREEN,'#dfc493',GREEN];edges=[0,3/9,5/9,1]
    for i in range(3):
        rr(im,(x+span*edges[i],y,x+span*edges[i+1],y+30),10,colors[i])
    for at,label in zip(edges,['09:00','12:00','14:00','18:00']):
        anchor='lt' if at==0 else 'rt' if at==1 else 'mt'
        txt(im,label,x+span*at,y+66,25,'#dce8d3' if dark else MUTED,anchor=anchor,mono=True)
    px=x+span*(current-9)/9
    ImageDraw.Draw(im).polygon([(px,y-16),(px-14,y-39),(px+14,y-39)],fill=GOLD if dark else PINE)

def frame(t):
    if t<4:
        im=base(True);q=t
        header(im,'想做的事，','又被往后推了。','消息一条接一条，计划还没开始。',True)
        for i,title in enumerate(['再看一条视频','回复这条消息','刷完这页就开始']):
            p=ease((q-i*.5)/.7)
            if p>0:
                layer=news_card(title);paste(im,layer,130+(-1 if i%2 else 1)*80*(1-p),710+i*225, p)
        rr(im,(130,1480,950,1580),45,'#385343')
        center(im,'先替正事，留一个时间段。',1508,36,PAPER,True)
        dark=True
    elif t<9:
        q=t-4;im=base();header(im,'时间，你来定。','开始和结束，分开选。','横排双框 · 数字滚轮 · 24小时制')
        yy=710+45*(1-ease(q/.5))
        wheel(im,120,yy,395,'开始时间',9,0,q/.8)
        wheel(im,565,yy,395,'结束时间',12,0,(q-.5)/.8)
        center(im,'09:00  —  12:00',1260,66,GREEN,True,True)
        rr(im,(260,1410,820,1514),34,GREEN)
        center(im,'保存这个时段',1436,37,PAPER,True)
        center(im,'上下滑动数字，就能选择。',1590,29,MUTED)
        dark=False
    elif t<15:
        q=t-9;im=base();header(im,'一天有好几段？','像闹钟一样，分别设。','多个分离时段，每段都能独立开关。')
        for i,(start,end) in enumerate([('09:00','12:00'),('14:00','18:00'),('22:00','07:00')]):
            enter=ease((q-i*.35)/.5)
            if enter>0:
                on=not(i==1 and 3<=q<4.3)
                pulse=max(clamp(1-abs(q-3)*2),clamp(1-abs(q-4.3)*2)) if i==1 else 0
                range_card(im,112+70*(1-enter),630+i*275,856,start,end,i+1,on,pulse)
        caption='关闭一段，其他时段照常。' if 3<=q<4.3 else '开启后，按设定时段执行。' if q>=4.3 else '上午、下午、夜间，都可单独安排。'
        center(im,caption,1547,32,GREEN,True)
        dark=False
    elif t<21:
        q=t-15;free=1.8<=q<3.8;dark=not free;im=base(dark)
        header(im,'该专注时，专注。','空档，也留给你。','09:00—12:00 / 14:00—18:00',dark)
        value='12:00' if free else '09:00' if q<1.8 else '14:00'
        timeline(im,12 if free else 9 if q<1.8 else 14,dark)
        rr(im,(130,865,950,1435),36,PAPER)
        center(im,'自由使用时段' if free else '当前处于禁用时段',910,30,MUTED)
        center(im,value,1005,112,GREEN,True,True)
        center(im,'空档，自由安排。' if free else '现在，去做正事。',1182,53,PINE,True)
        center(im,'12:00—14:00，没有设置限制。' if free else '普通应用显示专注拦截页。',1296,29,MUTED)
        center(im,'时段切换示意 · 时间已加速',1503,28,'#cad7c2' if dark else MUTED)
        center(im,'电话与紧急呼叫入口保留',1561,27,'#cad7c2' if dark else MUTED)
    elif t<27:
        q=t-21;dark=False;im=base();header(im,'设好一次。','每天，到点执行。','计划开启且授权正常时，按每日时段重复。')
        day=min(6,int(q*7/6));labels=['一','二','三','四','五','六','日']
        for i,label in enumerate(labels):
            x=93+i*130;active=i==day
            rr(im,(x,672,x+112,797),20,GREEN if active else '#e0e7d7')
            txt(im,'周',x+56,687,22,PAPER if active else MUTED,anchor='mt')
            txt(im,label,x+56,726,34,PAPER if active else GREEN,True,'mt')
        range_card(im,112,925,856,'09:00','12:00',1)
        range_card(im,112,1210,856,'14:00','18:00',2)
        center(im,'不必每天，再点一次开始。',1564,36,GREEN,True)
    elif t<31:
        q=t-27;dark=False;im=base();header(im,'这一段时间，','留给想完成的事。','读完这一页，写下第一行，做眼前的事。')
        scene=art.desk();scale=1+.015*ease(q/4);scene=scene.resize((round(scene.width*scale),round(scene.height*scale)),Image.Resampling.LANCZOS)
        paste(im,scene,40-(scene.width-1000)/2,650+30*(1-ease(q/.7)))
        center(im,'手机放下，生活继续。',1583,35,GREEN,True)
    else:
        q=t-31;dark=True;im=base(True)
        paste(im,logo(185),447,254+round(30*(1-ease(q/.6))))
        center(im,'去做正事',518,108,PAPER,True)
        center(im,'给正事，留一段时间。',696,49,GOLD,True)
        for i,label in enumerate(['双框选时','多段独立','每日重复']):
            x=94+i*304;rr(im,(x,888,x+284,1002),24,'#395341')
            txt(im,label,x+142,922,36,PAPER,True,'mt')
        rr(im,(183,1135,897,1251),50,GOLD)
        center(im,'只用手机，设置你的计划。',1170,36,PINE,True)
        center(im,'Android 每日时段助手 · 1.3.1',1332,28,'#d6e2cb')
        center(im,'需授权无障碍、闹钟，并放行后台 / 自启动',1440,26,'#cad7c2')
        center(im,'可关闭权限或卸载退出；兼容性以手机实测为准',1500,24,'#cad7c2')
        center(im,'功能动画示意，非实机运行或全机型效果证明',1551,24,'#cad7c2')
    chrome(im,t,dark)
    for cut in (4,9,15,21,27,31):
        distance=abs(t-cut)
        if distance<.1:im=Image.blend(im,Image.new('RGB',(W,H),PINE),.18*(1-distance/.1))
    return im

STORY=[
    (0,4,'想做的事，又被往后推了。','消息卡片按音乐节拍进入，建立使用场景'),
    (4,9,'时间，你来定。开始和结束，分开选。','两个矩形数字滚轮，选择09:00—12:00'),
    (9,15,'一天有好几段？像闹钟一样，分别设。','三段独立时段卡片，关闭其中一段后恢复，其余保留'),
    (15,21,'该专注时，专注。空档，也留给你。','加速示意09点拦截、12点空档、14点再次拦截'),
    (21,27,'设好一次。每天，到点执行。','一周日期轮换，相同每日计划重复，无需每日开始'),
    (27,31,'这一段时间，留给想完成的事。','温暖桌面、书页与放下的手机，音乐减法'),
    (31,36,'去做正事。给正事，留一段时间。','品牌、三个功能、手机设置入口文案与授权边界')]
SAMPLES=[1.7,5.8,10.8,13.6,15.9,17.8,19.7,23.7,28.8,33.2,35.2,8.2]

def main():
    p=argparse.ArgumentParser();p.add_argument('--previews-only',action='store_true');args=p.parse_args()
    OUT.mkdir(parents=True,exist_ok=True);QA.mkdir(parents=True,exist_ok=True)
    for i,t in enumerate(SAMPLES): frame(t).save(QA/f'shot-{i+1:02d}.jpg',quality=95)
    tiles=[frame(t).resize((270,480),Image.Resampling.LANCZOS) for t in SAMPLES]
    sheet=Image.new('RGB',(1080,1440),CREAM)
    for i,tile in enumerate(tiles):sheet.paste(tile,((i%4)*270,(i//4)*480))
    sheet.save(QA/'storyboard.jpg',quality=95)
    frame(33).save(OUT/'cover.jpg',quality=96)
    (ROOT/'storyboard.json').write_text(json.dumps({'title':'去做正事 · 给正事留一段时间','app_version':'1.3.1','seconds':SECONDS,'fps':FPS,
        'visuals':'Original procedural motion graphics illustrating local source, not actual handset footage.',
        'score':'Original 120 BPM instrumental, no vocals or external samples.',
        'scenes':[{'start':a,'end':b,'caption':c,'visual':v} for a,b,c,v in STORY]},ensure_ascii=False,indent=2),encoding='utf-8')
    captions=[]
    def stamp(t):return f'00:00:{int(t):02d},000'
    for i,(a,b,c,v) in enumerate(STORY):captions.append(f'{i+1}\n{stamp(a)} --> {stamp(b)}\n{c}\n')
    (OUT/'captions.srt').write_text('\n'.join(captions),encoding='utf-8-sig')
    if args.previews_only:print('MV-02 previews ready.',flush=True);return
    music=compose(OUT/'original-score.wav');(QA/'music.json').write_text(json.dumps(music,indent=2),encoding='utf-8')
    command=['ffmpeg','-y','-hide_banner','-loglevel','warning','-f','rawvideo','-vcodec','rawvideo','-pix_fmt','rgb24',
        '-s',f'{W}x{H}','-r',str(FPS),'-i','-','-i',str(OUT/'original-score.wav'),'-map','0:v:0','-map','1:a:0',
        '-c:v','libx264','-preset','fast','-crf','18','-threads','4','-pix_fmt','yuv420p','-c:a','aac','-b:a','192k',
        '-ar','48000','-t',str(SECONDS),'-movflags','+faststart',str(OUT/'phone-focus-daily-mv-1080p.mp4')]
    started=time.monotonic()
    with (QA/'encode.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen(command,stdin=subprocess.PIPE,stderr=log)
        try:
            for i in range(SECONDS*FPS):
                process.stdin.write(frame(i/FPS).tobytes())
                if i%(2*FPS)==0:print(f'MV-02 render {i//FPS:02d}/{SECONDS}s; elapsed {time.monotonic()-started:.1f}s',flush=True)
            process.stdin.close();status=process.wait()
            if status:raise RuntimeError(f'ffmpeg failed: {status}')
        except BaseException:process.kill();process.wait();raise
    print(f'MV-02 rendered in {time.monotonic()-started:.1f}s',flush=True)

if __name__=='__main__':main()
