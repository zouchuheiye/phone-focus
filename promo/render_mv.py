"""32-second original portrait motion-graphics MV for Phone Focus 1.1.0.

The interface is illustrated from local source, explicitly labeled as a demonstration.
No actual handset footage, external artwork, music samples or voices are used.
"""
from pathlib import Path
from functools import lru_cache
import argparse
import json
import math
import subprocess
import sys
import time

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from music import compose

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'output'
QA = ROOT / 'qa'
W, H, FPS, SECONDS = 1080, 1920, 30, 32
CREAM = '#f4f2e9'
PAPER = '#fffcf3'
PINE = '#263f33'
GREEN = '#426653'
SAGE = '#c8d4bf'
MUTED = '#788477'
GOLD = '#d7b77a'
INK = '#20352a'


@lru_cache(maxsize=128)
def font(size, bold=False, mono=False):
    path = 'C:/Windows/Fonts/consolab.ttf' if mono else ('C:/Windows/Fonts/msyhbd.ttc' if bold else 'C:/Windows/Fonts/msyh.ttc')
    return ImageFont.truetype(path, int(size))


def clamp(value):
    return max(0, min(1, value))


def ease(value):
    return 1 - (1 - clamp(value)) ** 3


def smooth(value):
    value = clamp(value)
    return value * value * (3 - 2 * value)


def txt(im, message, x, y, size, color=INK, bold=False, anchor='lt', mono=False):
    ImageDraw.Draw(im).text((int(x), int(y)), message, font=font(size, bold, mono), fill=color, anchor=anchor)


def center(im, message, y, size, color=INK, bold=False, offset=0, mono=False):
    txt(im, message, W / 2 + offset, y, size, color, bold, 'mt', mono)


def rr(im, box, radius, fill, outline=None, width=1):
    ImageDraw.Draw(im).rounded_rectangle(tuple(map(int, box)), radius=int(radius), fill=fill, outline=outline, width=width)


def rgb(color):
    return tuple(int(color[i:i+2], 16) for i in (1, 3, 5))


@lru_cache(maxsize=3)
def background(dark=False):
    top = np.array(rgb(PINE if dark else CREAM), float)
    bottom = np.array(rgb('#14271e' if dark else '#e4e9db'), float)
    y = np.linspace(0, 1, H)[:, None, None]
    a = np.tile(top[None, None, :] * (1 - y) + bottom[None, None, :] * y, (1, W, 1))
    im = Image.fromarray(a.astype(np.uint8), 'RGB')
    d = ImageDraw.Draw(im)
    line = '#385143' if dark else '#d9dfd1'
    for radius in (380, 470, 560):
        d.ellipse((W - radius, 480 - radius, W + radius, 480 + radius), outline=line, width=2)
    for x in range(70, W, 90):
        for yy in range(1480, 1790, 90):
            d.ellipse((x, yy, x+3, yy+3), fill=line)
    return im


@lru_cache(maxsize=16)
def logo(size=120):
    im = Image.new('RGBA', (size, size))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, size-1, size-1), radius=size*.25, fill=GREEN)
    d.rounded_rectangle((size*.32, size*.18, size*.68, size*.82), radius=size*.045, outline=PAPER, width=max(2, round(size*.035)))
    d.line((size*.46, size*.75, size*.54, size*.75), fill=PAPER, width=max(2, round(size*.025)))
    d.line((size*.42, size*.46, size*.49, size*.53, size*.64, size*.37), fill=GOLD, width=max(2, round(size*.045)), joint='curve')
    return im


def paste(im, layer, x, y, opacity=1):
    if opacity < 1:
        layer = layer.copy()
        layer.putalpha(layer.getchannel('A').point(lambda p: round(p * clamp(opacity))))
    im.paste(layer, (int(x), int(y)), layer)


def chrome(im, t, dark=False, demo=False):
    fg = PAPER if dark else PINE
    sub = '#adbdac' if dark else MUTED
    paste(im, logo(48), 72, 75)
    txt(im, '去做正事', 138, 79, 29, fg, True)
    txt(im, 'PHONE FOCUS', 1008, 84, 22, sub, anchor='rt', mono=True)
    if demo:
        rr(im, (72, 1735, 463, 1795), 30, '#385342' if dark else '#dce5d5')
        txt(im, '功能动画演示 · 非实机录屏', 268, 1752, 23, '#d7e2d1' if dark else GREEN, anchor='mt')
    ImageDraw.Draw(im).line((72, 1848, 1008, 1848), fill='#49614e' if dark else '#cdd6c7', width=2)
    ImageDraw.Draw(im).line((72, 1848, 72 + 936 * clamp(t / SECONDS), 1848), fill=GOLD if dark else GREEN, width=4)
    txt(im, '把注意力，留给眼前。', 72, 1803, 24, sub)
    txt(im, f'{int(t):02d} / 32', 1008, 1808, 20, sub, anchor='rt', mono=True)


@lru_cache(maxsize=12)
def shell(width):
    height = round(width * 1.99)
    margin = 70
    im = Image.new('RGBA', (width + margin * 2, height + margin * 2))
    shadow = Image.new('RGBA', im.size)
    ImageDraw.Draw(shadow).rounded_rectangle((margin+10, margin+24, margin+width+10, margin+height+24), radius=width*.11, fill=(12, 29, 19, 85))
    shadow = shadow.filter(ImageFilter.GaussianBlur(26))
    im.alpha_composite(shadow)
    rr(im, (margin, margin, margin+width, margin+height), width*.115, '#151e19', '#8f9a8b', 3)
    rr(im, (margin+6, margin+6, margin+width-6, margin+height-6), width*.10, '#26362c', '#53604f', 2)
    return im


def phone(mode='scroll', width=510, seconds=0, t=0, lit=True, angle=0):
    im = shell(width).copy()
    height = round(width * 1.99)
    border = 16
    sw, sh = width-2*border, height-2*border
    screen = Image.new('RGB', (sw, sh), CREAM if lit else '#080d0a')
    d = ImageDraw.Draw(screen)
    if lit:
        txt(screen, '9:41', 24, 21, 20, INK, True)
        d.rounded_rectangle((sw-54, 26, sw-23, 40), 4, outline=INK, width=2)
        d.rectangle((sw-50, 30, sw-28, 36), fill=INK)
        if mode == 'scroll':
            txt(screen, '还想再看一眼', 26, 106, 34, INK, True)
            txt(screen, '一滑，又过去了一会儿', 27, 160, 20, MUTED)
            offset = (t * 70) % 182
            for i in range(-1, 6):
                y = 226 + i * 182 - offset
                if y < 196:
                    continue
                rr(screen, (24, y, sw-24, y+158), 20, '#e1e8d8' if i % 2 else '#e6dfcc')
                rr(screen, (44, y+22, 116, y+94), 17, '#8faaa0' if i % 2 else '#c5ae83')
                for j, length in enumerate((sw-176, sw-210, sw-242)):
                    rr(screen, (136, y+25+j*22, 136+length, y+32+j*22), 3, '#b2bfa9')
                rr(screen, (44, y+119, sw-54, y+128), 4, '#c4ccb9')
        else:
            txt(screen, '去做正事', 30, 101, 42, INK, True)
            txt(screen, '手机放下，生活继续。', 30, 162, 22, MUTED)
            rr(screen, (24, 229, sw-24, 684), 30, '#ffffff')
            txt(screen, '● 自动息屏已开启', sw/2, 263, 20, GREEN, anchor='mt')
            box = (sw/2-142, 320, sw/2+142, 604)
            d.ellipse(box, outline='#e6ecdf', width=12)
            d.arc(box, -90, -90+max(2, 360*clamp(seconds/180)), fill=GREEN, width=12)
            value = f'{int(seconds)//60:02d}:{int(seconds)%60:02d}'
            txt(screen, value, sw/2, 421, 55, INK, True, 'mt', True)
            txt(screen, '本次连续亮屏', sw/2, 501, 18, MUTED, anchor='mt')
            rr(screen, (46, 620, sw-46, 663), 12, GREEN)
            txt(screen, '停止自动息屏', sw/2, 631, 19, PAPER, anchor='mt')
            rr(screen, (24, 718, sw-24, 834), 24, '#dce6d5')
            txt(screen, '亮屏满 3 分钟，自动息屏', sw/2, 742, 20, GREEN, True, 'mt')
            txt(screen, '熄屏归零，下次重新计时', sw/2, 782, 18, MUTED, anchor='mt')
    else:
        # A small reflected streak keeps the dark glass visibly physical.
        d.polygon([(0, 80), (sw, 0), (sw, 130), (0, 320)], fill='#101a13')
    mask = Image.new('L', (sw, sh))
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw-1, sh-1), radius=width*.075, fill=255)
    im.paste(screen, (70+border, 70+border), mask)
    rr(im, (70+width/2-49, 70+19, 70+width/2+49, 70+38), 10, '#111a14')
    if lit:
        ImageDraw.Draw(im).line((70+width/2-65, 70+height-25, 70+width/2+65, 70+height-25), fill=INK, width=5)
    if angle:
        im = im.rotate(angle, Image.Resampling.BICUBIC, expand=True)
    return im


def put_phone(im, x, y, **kwargs):
    p = phone(**kwargs)
    paste(im, p, x-p.width/2, y-p.height/2)


def floating_card(im, title, subtitle, x, y, opacity=1):
    layer = Image.new('RGBA', (648, 151))
    rr(layer, (0, 0, 647, 150), 26, '#fffcf3')
    rr(layer, (25, 31, 106, 112), 22, '#dce6d4')
    ImageDraw.Draw(layer).ellipse((52, 58, 79, 85), fill=GREEN)
    txt(layer, title, 129, 30, 29, INK, True)
    txt(layer, subtitle, 129, 81, 23, MUTED)
    paste(im, layer, x, y, opacity)


@lru_cache(maxsize=1)
def desk():
    im = Image.new('RGBA', (1000, 920))
    # Warm sunlight, a notebook, an uncapped pen, a cup, a face-down phone.
    rr(im, (30, 100, 970, 880), 46, '#d7c8a8')
    d = ImageDraw.Draw(im)
    for x in range(-800, 1700, 205):
        d.polygon([(x, 100), (x+78, 100), (x+600, 880), (x+522, 880)], fill='#e3d7bd')
    shadow = Image.new('RGBA', im.size)
    ImageDraw.Draw(shadow).rounded_rectangle((147, 253, 681, 788), 30, fill=(58, 64, 39, 45))
    im.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))
    rr(im, (120, 216, 663, 760), 22, '#48684e')
    d = ImageDraw.Draw(im)
    d.polygon([(135, 222), (390, 203), (390, 735), (135, 752)], fill='#fff9e5')
    d.polygon([(394, 203), (647, 225), (647, 752), (394, 735)], fill='#f2e9d1')
    d.line((392, 214, 392, 735), fill='#c7bea4', width=4)
    txt(im, '今日正事', 167, 265, 29, GREEN, True)
    txt(im, '先完成这一页。', 167, 316, 19, MUTED)
    for yy in range(378, 696, 40):
        d.line((169, yy, 354, yy-3), fill='#d4d3bc', width=2)
        d.line((429, yy-3, 610, yy), fill='#d4d3bc', width=2)
    # Pen with a fine brass tip.
    d.line((590, 350, 686, 715), fill='#294b38', width=20)
    d.line((587, 338, 598, 380), fill=GOLD, width=22)
    d.polygon([(683, 710), (694, 710), (698, 744)], fill='#bfa265')
    # Ceramic cup, top down.
    d.ellipse((725, 204, 924, 403), fill='#eee9d6', outline='#a8b398', width=5)
    d.ellipse((744, 223, 905, 384), fill='#d3d6bd')
    d.ellipse((754, 233, 895, 374), fill='#6a5b3a')
    d.ellipse((775, 249, 886, 358), outline='#9b8554', width=4)
    d.arc((893, 259, 956, 348), -80, 85, fill='#f9f3de', width=20)
    # Face-down phone, not an actual app/device screenshot.
    rr(im, (731, 489, 913, 821), 30, '#2d4535', '#526e4c', 3)
    rr(im, (748, 509, 815, 589), 15, '#3e5942')
    for xx, yy in [(764, 527), (790, 550), (763, 568)]:
        d.ellipse((xx-8, yy-8, xx+8, yy+8), fill='#16271c', outline='#809572', width=2)
    paste(im, logo(58), 793, 648)
    return im


def frame(t):
    if t < 4:
        q = t
        im = background().copy()
        e = ease(q/.8)
        txt(im, '本来，只想', 80, 243 + (1-e)*55, 82, PINE, True)
        txt(im, '看一眼。', 80, 357 + (1-e)*55, 120, GREEN, True)
        txt(im, '消息、短视频、再刷新一下……', 84, 538, 32, MUTED)
        put_phone(im, 552+math.sin(q*.9)*20, 1137, mode='scroll', width=445, t=q, angle=-7+math.sin(q)*2)
        if q > .7:
            floating_card(im, '还有一条新消息', '你又拿起了手机', 78-55*(1-ease((q-.7)/.5)), 874, ease((q-.7)/.4))
        if q > 1.7:
            floating_card(im, '下一条，可能更有意思', '手指往上，时间往后', 352, 1396-40*ease((q-1.7)/.5), ease((q-1.7)/.4))
        chrome(im, t, demo=True)
    elif t < 8:
        q = t-4
        im = background(True).copy()
        txt(im, '再刷一下。', 80, 254, 96, PAPER, True)
        txt(im, '又是三分钟。', 80, 381, 96, GOLD, True)
        # A large background clock gives a musical pulse, rather than inventing usage statistics.
        pulse = .5 + .5*math.sin(q*math.pi*4)
        center(im, '03:00', 637, 211, '#385442' if pulse < .6 else '#405b46', True, mono=True)
        put_phone(im, 550, 1240 + math.sin(q*1.2)*16, mode='scroll', width=438, t=t*2.5, angle=7)
        rr(im, (117, 1568, 963, 1644), 38, '#385442')
        center(im, '眼前的正事，还停在原地。', 1587, 32, '#d9e2d1')
        chrome(im, t, True, True)
    elif t < 12:
        q = t-8
        im = background().copy()
        paste(im, logo(106), 80, 236+int(35*(1-ease(q/.5))))
        txt(im, '去做正事', 216, 244, 76, PINE, True)
        txt(im, '给这一次亮屏，设个终点。', 84, 374, 43, GREEN)
        put_phone(im, 540, 1052+60*(1-ease(q/.7)), mode='app', width=480, seconds=min(179, q*44), t=t, angle=-3+q)
        center(im, '亮屏检测 / 后台运行', 1603, 35, GREEN, True)
        chrome(im, t, demo=True)
    elif t < 18:
        q = t-12
        locked = t >= 16
        im = background(locked).copy()
        fg = PAPER if locked else PINE
        center(im, '亮屏 3 分钟', 258, 84, fg, True)
        center(im, '自动息屏。' if locked else '到点，自动息屏。', 378, 68, GOLD if locked else GREEN, True)
        seconds = min(180, 176+q)
        put_phone(im, 540, 1055, mode='app', width=482, seconds=seconds, t=t, lit=not locked)
        if locked:
            rr(im, (248, 1558, 832, 1650), 46, '#3b5644')
            center(im, '手机放下，去做正事。', 1583, 34, PAPER, True)
        else:
            center(im, '计时过程已加速', 1600, 30, MUTED)
        chrome(im, t, locked, True)
    elif t < 24:
        q = t-18
        im = background().copy()
        txt(im, '手机放下。', 80, 248, 96, PINE, True)
        txt(im, '先完成这一页。', 80, 379, 80, GREEN, True)
        txt(im, '读书、写字，做眼前那件事。', 84, 535, 33, MUTED)
        scene = desk()
        paste(im, scene, 40, 687 + 34*(1-ease(q/.8)))
        # Quiet, warm dust glints, not distracting particle noise.
        d = ImageDraw.Draw(im)
        for i in range(12):
            x = 120+(i*151 % 900)
            y = 700+(i*79 % 800) - 20*math.sin(q*.7+i)
            d.ellipse((x, y, x+4, y+4), fill='#f8efcf')
        chrome(im, t)
    elif t < 28:
        q = t-24
        im = background(True).copy()
        txt(im, '规则很简单。', 80, 260, 92, PAPER, True)
        lines = [('01', '后台检测', '开启后，返回桌面也能计时。'),
                 ('02', '熄屏归零', '下一次亮屏，重新开始。'),
                 ('03', '本机运行', '无需联网，不读取使用内容。')]
        for i, (number, title, body) in enumerate(lines):
            enter = ease((q-i*.6)/.6)
            if enter <= 0:
                continue
            yy = 558+i*310+40*(1-enter)
            txt(im, number, 82, yy+7, 50, GOLD, mono=True)
            txt(im, title, 207, yy, 66, PAPER, True)
            txt(im, body, 211, yy+102, 30, '#b5c5b0')
            ImageDraw.Draw(im).line((80, yy+208, 1000, yy+208), fill='#49604b', width=2)
        txt(im, '首次需授权锁屏，并允许后台运行。', 82, 1579, 30, '#cad7c1')
        chrome(im, t, True)
    else:
        q = t-28
        im = background().copy()
        paste(im, logo(230), 425, 344+int(35*(1-ease(q/.7))))
        center(im, '去做正事', 670, 115, PINE, True)
        center(im, '亮屏三分钟，就到这里。', 851, 48, GREEN, True)
        rr(im, (177, 1049, 903, 1173), 62, GREEN)
        center(im, '现在，把手机放下。', 1082, 44, PAPER, True)
        center(im, 'Android 自动息屏助手', 1292, 33, GREEN)
        center(im, '首次需授权锁屏，并放行后台 / 自启动', 1400, 28, MUTED)
        center(im, '系统锁屏后可能需 PIN 或密码解锁', 1451, 25, MUTED)
        chrome(im, t)
    # Short, deliberate dips make hard edits musical while avoiding blank opening/end frames.
    for cut in (4, 8, 12, 18, 24, 28):
        distance = abs(t-cut)
        if distance < .09:
            cover = Image.new('RGB', (W, H), PINE)
            im = Image.blend(im, cover, .22*(1-distance/.09))
    return im


STORY = [
    (0, 4, '本来，只想看一眼。', '通知卡片与轻微倾斜的手机，开场钩子'),
    (4, 8, '再刷一下。又是三分钟。', '深绿色反差、节拍与滚动卡片'),
    (8, 12, '去做正事。给这一次亮屏，设个终点。', '应用品牌与界面示意'),
    (12, 18, '亮屏3分钟，到点自动息屏。', '加速计时，16秒屏幕变黑，锁屏音效'),
    (18, 24, '手机放下。先完成这一页。', '暖色桌面、书页、笔与朝下的手机'),
    (24, 28, '后台检测。熄屏归零。本机运行。', '三个功能节拍卡点'),
    (28, 32, '去做正事。亮屏三分钟，就到这里。', '品牌与行动文案、授权说明'),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--previews-only', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    QA.mkdir(parents=True, exist_ok=True)
    sample_times = [1.8, 5.5, 9.5, 14.8, 16.8, 20.0, 26.3, 30.3]
    frames = []
    for i, at in enumerate(sample_times):
        im = frame(at)
        im.save(QA / f'shot-{i+1:02d}.jpg', quality=94)
        tile = im.resize((270, 480), Image.Resampling.LANCZOS)
        frames.append(tile)
    sheet = Image.new('RGB', (270*4, 480*2), CREAM)
    for i, tile in enumerate(frames):
        sheet.paste(tile, ((i%4)*270, (i//4)*480))
    sheet.save(QA / 'storyboard.jpg', quality=95)
    frame(29.5).save(OUT / 'cover.jpg', quality=95)
    (ROOT / 'storyboard.json').write_text(json.dumps({
        'title': '去做正事 · 亮屏三分钟', 'seconds': SECONDS, 'format': '1080x1920, 30fps, H.264 + AAC',
        'app_version': '1.1.0', 'visuals': 'Original procedural animation, not handset footage',
        'score': 'Original 120 BPM instrumental; no external music or voice',
        'scenes': [{'start': a, 'end': b, 'caption': c, 'visual': d} for a, b, c, d in STORY]
    }, ensure_ascii=False, indent=2), encoding='utf-8')
    if args.previews_only:
        print('Storyboard previews ready.', flush=True)
        return
    music_meta = compose(OUT / 'original-score.wav')
    (QA / 'music.json').write_text(json.dumps(music_meta, indent=2), encoding='utf-8')
    video = OUT / 'phone-focus-promo-vertical-1080p.mp4'
    command = ['ffmpeg', '-y', '-hide_banner', '-loglevel', 'warning', '-f', 'rawvideo', '-vcodec', 'rawvideo',
               '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-', '-i', str(OUT/'original-score.wav'),
               '-map', '0:v:0', '-map', '1:a:0', '-c:v', 'libx264', '-preset', 'fast', '-crf', '19',
               '-threads', '4', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-ar', '48000',
               '-t', str(SECONDS), '-movflags', '+faststart', str(video)]
    started = time.monotonic()
    with (QA/'encode.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, stdin=subprocess.PIPE, stderr=log)
        try:
            for i in range(SECONDS*FPS):
                process.stdin.write(frame(i/FPS).tobytes())
                if i % (FPS*2) == 0:
                    print(f'Render {i//FPS:02d}/{SECONDS}s, elapsed {time.monotonic()-started:.1f}s', flush=True)
            process.stdin.close()
            result = process.wait()
            if result:
                raise RuntimeError(f'ffmpeg encoding failed ({result}); see qa/encode.log')
        except BaseException:
            process.kill()
            process.wait()
            raise
    print(f'Render complete: {video}; elapsed {time.monotonic()-started:.1f}s', flush=True)


if __name__ == '__main__':
    main()
