"""10-second anime character story with user screenshots and Mandarin female narration."""
from pathlib import Path
from functools import lru_cache
import argparse
import hashlib
import json
import math
import subprocess
import time
import numpy as np
from PIL import Image,ImageDraw,ImageFont,ImageOps

ROOT=Path(__file__).resolve().parent
ASSETS,OUT,QA=ROOT/'assets',ROOT/'output',ROOT/'qa'
W,H,FPS,SECONDS=1080,1920,30,10
CREAM='#f5f3e9';GREEN='#3e6653';INK='#203b2d';GOLD='#e8ca83';MUTED='#738575'

@lru_cache(maxsize=80)
def font(size,bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc' if bold else 'C:/Windows/Fonts/msyh.ttc',size)
def ease(v):
    v=max(0,min(1,v));return v*v*(3-2*v)
def text(im,value,x,y,size,color=INK,bold=False,anchor='lt'):
    ImageDraw.Draw(im).text((round(x),round(y)),value,font=font(size,bold),fill=color,anchor=anchor)
def center(im,value,y,size,color=INK,bold=False):text(im,value,W/2,y,size,color,bold,'mt')
def rr(im,box,radius,color,outline=None,width=1):
    ImageDraw.Draw(im).rounded_rectangle(tuple(round(v) for v in box),radius,fill=color,outline=outline,width=width)

def screen_insert(panel,source,quad):
    """Map an unretouched screenshot into the illustrated handset's blank display."""
    points=[(x*panel.width,y*panel.height) for x,y in quad]
    source=source.convert('RGB')
    # Remove only phone status/navigation edges; preserve app pixels, including inactive labels.
    source=source.crop((0,round(source.height*.053),source.width,round(source.height*.965)))
    target_w=round(max(math.dist(points[0],points[1]),math.dist(points[3],points[2])))
    target_h=round(max(math.dist(points[0],points[3]),math.dist(points[1],points[2])))
    canvas=Image.new('RGB',(max(1,target_w),max(1,target_h)),CREAM)
    fitted=ImageOps.contain(source,canvas.size,Image.Resampling.LANCZOS)
    canvas.paste(fitted,((canvas.width-fitted.width)//2,(canvas.height-fitted.height)//2))
    src=[(0,0),(canvas.width-1,0),(canvas.width-1,canvas.height-1),(0,canvas.height-1)]
    matrix=[];values=[]
    for (x,y),(u,v) in zip(points,src):
        matrix.extend([[x,y,1,0,0,0,-u*x,-u*y],[0,0,0,x,y,1,-v*x,-v*y]])
        values.extend([u,v])
    transform=np.linalg.solve(np.array(matrix,float),np.array(values,float))
    warped=canvas.transform(panel.size,Image.Transform.PERSPECTIVE,transform,Image.Resampling.BICUBIC,fillcolor=CREAM)
    mask=Image.new('L',panel.size);ImageDraw.Draw(mask).polygon(points,fill=255)
    # Keep the illustrated fingers and the phone notch in front of the inserted screenshot.
    original=np.asarray(panel)
    red,green,blue=[original[:,:,i].astype(float) for i in range(3)]
    foreground=((red>170)&(red-green>34)&(red-blue>55))|((red<85)&(green<85)&(blue<85))
    alpha=np.array(mask);alpha[foreground]=0;mask=Image.fromarray(alpha)
    result=panel.copy();result.paste(warped,(0,0),mask);return result

@lru_cache(maxsize=1)
def panels():
    config=json.loads((ASSETS/'layout.json').read_text(encoding='utf-8'))
    image=Image.open(ASSETS/'anime-storyboard.png').convert('RGB')
    result=[]
    for index in range(4):
        x=index%2;y=index//2
        box=(round(x*image.width/2),round(y*image.height/2),round((x+1)*image.width/2),round((y+1)*image.height/2))
        panel=image.crop(box)
        if index<2:
            path=ASSETS/('user-schedule-screen.jpg' if index==0 else 'user-focus-screen.jpg')
            panel=screen_insert(panel,Image.open(path),config['phone_quads'][index])
        result.append(panel)
    return result

SCENES=[(0,2.6,'设置每日时段'),(2.6,4.3,'进入禁用时段'),(4.3,6.3,'放下手机'),(6.3,10,'回到学习')]
CAPTIONS=[(0,1.34,'考研戒手机'),(1.34,2.93,'学习期间戒手机'),(2.93,4.30,'帮孩子少玩手机'),
          (4.30,6.60,'家长和备考党必备小软件'),(6.60,7.50,'关注加群'),(7.50,10,'免费领取')]

def scene_at(t):
    for i,(start,end,title) in enumerate(SCENES):
        if start<=t<end:return i,(t-start)/(end-start)
    return 3,1

def gesture(panel,index,q):
    # Small local hand/pen movement keeps the illustrated action active between cuts.
    if index==0:
        pulse=math.sin(math.pi*max(0,min(1,(q-.25)/.5)))
        cx,cy,rx,ry,dx,dy=.62,.56,.22,.15,-3*pulse,1.5*pulse
    elif index==1:
        pulse=math.sin(math.pi*max(0,min(1,(q-.1)/.55)))
        cx,cy,rx,ry,dx,dy=.69,.58,.25,.16,-2.5*pulse,2*pulse
    elif index==2:
        pulse=ease(q)
        cx,cy,rx,ry,dx,dy=.38,.72,.24,.15,0,-3.5*pulse
    else:
        pulse=math.sin(q*math.pi*12)
        cx,cy,rx,ry,dx,dy=.47,.67,.18,.12,1.8*pulse,.8*math.cos(q*math.pi*12)
    def point(x,y):
        weight=math.exp(-2*((x/panel.width-cx)/rx)**2-2*((y/panel.height-cy)/ry)**2)
        return x+dx*weight,y+dy*weight
    mesh=[]
    for y in range(0,panel.height,40):
        for x in range(0,panel.width,40):
            right=min(x+40,panel.width);bottom=min(y+40,panel.height)
            coords=sum((point(xx,yy) for xx,yy in [(x,y),(x,bottom),(right,bottom),(right,y)]),())
            mesh.append(((x,y,right,bottom),coords))
    return panel.transform(panel.size,Image.Transform.MESH,mesh,Image.Resampling.BICUBIC)

def frame(t):
    index,q=scene_at(t);panel=gesture(panels()[index],index,q)
    im=Image.new('RGB',(W,H),CREAM)
    # The character dominates the frame; short camera moves link four real use actions.
    viewport=(48,310,1032,1530)
    vw,vh=viewport[2]-viewport[0],viewport[3]-viewport[1]
    zoom=1.0+.035*ease(q)
    target=(round(vw*zoom),round(vh*zoom))
    scene=ImageOps.fit(panel,target,Image.Resampling.LANCZOS,centering=(.5,.46))
    dx=round((target[0]-vw)*(.3+.3*q));dy=round((target[1]-vh)*.45)
    scene=scene.crop((dx,dy,dx+vw,dy+vh))
    mask=Image.new('L',scene.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,vw-1,vh-1),36,fill=255)
    im.paste(scene,(viewport[0],viewport[1]),mask)
    rr(im,(62,55,296,111),25,GREEN);text(im,'去做正事',179,67,28,'#fffbed',True,'mt')
    text(im,'10秒动漫剧情版',1018,70,23,MUTED,False,'rt')
    if index==0:
        center(im,'考研戒手机',145,76,GREEN,True)
        center(im,'学习期间，给专注留点时间',244,31,MUTED)
    elif index==1:
        center(im,'少玩手机，多点专注',153,59,GREEN,True)
        center(im,'家长也能陪孩子，安排学习时段',244,30,MUTED)
    elif index==2:
        center(im,'家长和备考党',145,66,GREEN,True)
        center(im,'必备小软件',233,48,INK,True)
    else:
        center(im,'把手机放下，去做正事',153,55,GREEN,True)
        center(im,'学完这一段，再安心休息',244,31,MUTED)
    rr(im,(72,333,410,390),23,'#fffbef')
    text(im,f'0{index+1}  {SCENES[index][2]}',241,348,25,GREEN,True,'mt')
    # A tap ripple is synchronized with the deliberate phone interaction, not a fake UI state.
    if index==1:
        local=t-2.6
        ripple=max(0,1-abs(local-.55)/.45)
        if ripple>0:
            config=json.loads((ASSETS/'layout.json').read_text(encoding='utf-8'))
            px,py=config.get('tap_point',[.72,.59])
            # Convert panel coordinate through the same camera crop.
            scale=max(target[0]/panel.width,target[1]/panel.height)
            excess_x=panel.width*scale-target[0];excess_y=panel.height*scale-target[1]
            x=viewport[0]+px*panel.width*scale-excess_x*.5-dx
            y=viewport[1]+py*panel.height*scale-excess_y*.46-dy
            r=16+42*(1-ripple)
            ImageDraw.Draw(im).ellipse((x-r,y-r,x+r,y+r),outline=GOLD,width=5)
    caption=next(c for start,end,c in CAPTIONS if start<=t<end)
    if t<6.6:
        rr(im,(72,1558,1008,1658),28,GREEN)
        center(im,caption,1589,42,'#fffbed',True)
        center(im,'设置时段 · 每天重复 · 到点恢复',1706,29,GREEN)
    else:
        pulse=1+.012*math.sin((t-6.6)*math.pi*2)
        half=452*pulse
        rr(im,(W/2-half,1555,W/2+half,1735),38,GREEN)
        center(im,'关注加群',1578,48,'#fffbed',True)
        center(im,'免费领取',1647,57,GOLD,True)
    center(im,'AI动漫演绎 · 手机界面为提供截图的合成展示',1794,23,MUTED)
    center(im,'需手机授权并允许后台运行 · 可撤销权限退出',1844,23,MUTED)
    # Very brief cross-cut dip leaves the narration continuous.
    for cut in [2.6,4.3,6.3]:
        distance=abs(t-cut)
        if distance<.055:im=Image.blend(im,Image.new('RGB',(W,H),CREAM),.12*(1-distance/.055))
    return im

def main():
    p=argparse.ArgumentParser();p.add_argument('--previews-only',action='store_true');args=p.parse_args()
    OUT.mkdir(parents=True,exist_ok=True);QA.mkdir(parents=True,exist_ok=True)
    times=[.8,1.9,3.3,4.9,5.9,6.9,8,9.4]
    sheet=Image.new('RGB',(1080,960),CREAM)
    for i,second in enumerate(times):
        shot=frame(second);shot.save(QA/f'shot-{i+1:02d}.jpg',quality=95)
        sheet.paste(shot.resize((270,480),Image.Resampling.LANCZOS),((i%4)*270,(i//4)*480))
    sheet.save(QA/'storyboard.jpg',quality=96);frame(8).save(OUT/'cover.jpg',quality=96)
    def stamp(value):
        return f'00:00:{int(value):02d},{round((value-int(value))*1000):03d}'
    (OUT/'captions.srt').write_text('\n'.join(f'{i+1}\n{stamp(a)} --> {stamp(b)}\n{c}\n' for i,(a,b,c) in enumerate(CAPTIONS)),encoding='utf-8-sig')
    (ROOT/'storyboard.json').write_text(json.dumps({'seconds':10,'fps':FPS,'female_voice':'zh-CN-XiaoxiaoNeural',
        'scenes':[{'start':a,'end':b,'action':c} for a,b,c in SCENES],
        'captions':[{'start':a,'end':b,'text':c} for a,b,c in CAPTIONS],
        'visuals':'Four consistent anime character action keyframes with local hand/pen deformation, animated camera/tap and user screenshot compositing. Not continuous real handset footage.',
        'cta':'关注加群，免费领取','group_number_or_qr':'Not supplied; none invented.'},ensure_ascii=False,indent=2),encoding='utf-8')
    if args.previews_only:print('MV-03 previews ready.');return
    command=['ffmpeg','-y','-hide_banner','-loglevel','warning','-f','rawvideo','-vcodec','rawvideo','-pix_fmt','rgb24',
        '-s',f'{W}x{H}','-r',str(FPS),'-i','-','-i',str(OUT/'soundtrack.wav'),'-map','0:v:0','-map','1:a:0',
        '-c:v','libx264','-preset','fast','-crf','18','-threads','4','-pix_fmt','yuv420p','-c:a','aac','-b:a','192k',
        '-ar','48000','-t','10','-movflags','+faststart',str(OUT/'phone-focus-anime-10s.mp4')]
    start=time.monotonic()
    with (QA/'encode.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen(command,stdin=subprocess.PIPE,stderr=log)
        try:
            for i in range(FPS*SECONDS):
                process.stdin.write(frame(i/FPS).tobytes())
                if i%FPS==0:print(f'Render {i//FPS}/10s',flush=True)
            process.stdin.close();status=process.wait()
            if status:raise RuntimeError(f'ffmpeg exit {status}')
        except BaseException:process.kill();process.wait();raise
    print(f'MV-03 complete; elapsed {time.monotonic()-start:.1f}s',flush=True)

if __name__=='__main__':main()
