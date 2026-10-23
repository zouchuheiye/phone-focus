"""Verify exact 10s output, complete female narration and encoded anime story frames."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import hashlib
import json
import re
import subprocess
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parent
OUT,QA,ASSETS=ROOT/'output',ROOT/'qa',ROOT/'assets'
MOVIE=OUT/'phone-focus-anime-10s.mp4'
TIMES=[.8,1.9,3.3,4.9,5.9,6.9,8,9.4]
def run(args):return subprocess.run(args,capture_output=True,check=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def probe(path):return json.loads(run(['ffprobe','-v','error','-count_frames','-show_streams','-show_format','-of','json',str(path)]).stdout)

def main():
    info=probe(MOVIE);(QA/'ffprobe.json').write_text(json.dumps(info,indent=2),encoding='utf-8')
    video=next(s for s in info['streams'] if s['codec_type']=='video')
    audio=next(s for s in info['streams'] if s['codec_type']=='audio')
    assert (video['width'],video['height'])==(1080,1920)
    assert video['codec_name']=='h264' and video['pix_fmt']=='yuv420p'
    assert video['avg_frame_rate']=='30/1' and int(video['nb_read_frames'])==300
    assert audio['codec_name']=='aac' and audio['channels']==2
    assert abs(float(info['format']['duration'])-10)<.05
    assert abs(float(video['duration'])-float(audio['duration']))<.05
    result=run(['ffmpeg','-v','error','-xerror','-i',str(MOVIE),'-f','null','-'])
    (QA/'decode.txt').write_bytes(result.stderr or b'PASS: full 10-second audio/video decode; exit 0.\n')
    raw=run(['ffmpeg','-v','error','-i',str(MOVIE),'-vn','-f','f32le','-ar','48000','-ac','2','-']).stdout
    samples=np.frombuffer(raw,dtype='<f4').reshape(-1,2)
    assert np.isfinite(samples).all() and len(samples)>=48000*9.95
    peak=float(np.abs(samples).max());rms=float(np.sqrt(np.mean(samples**2)))
    assert .1<peak<.999 and .02<rms<.5
    segment_rms=[float(np.sqrt(np.mean(block**2))) for block in np.array_split(samples,10)]
    assert min(segment_rms)>.001
    voice=json.loads((QA/'voice.json').read_text(encoding='utf-8'))
    boundaries=json.loads((QA/'voice-boundaries.json').read_text(encoding='utf-8'))
    spoken=''.join(item['text'] for item in boundaries)
    assert spoken==re.sub(r'[^\u4e00-\u9fff]','',voice['text']),'Every requested narration phrase synthesized'
    voice_duration=float(probe(OUT/'female-voice-raw.mp3')['format']['duration'])
    last_word_end=max((b['offset']+b['duration'])/1e7 for b in boundaries)+.18
    assert 6<voice_duration<9.8 and last_word_end<9.5
    assert voice['voice']=='zh-CN-XiaoxiaoNeural' and voice['gender']=='Female'
    sheet=Image.new('RGB',(1080,960));frames=[]
    for i,t in enumerate(TIMES):
        path=QA/f'encoded-{i+1:02d}.jpg'
        run(['ffmpeg','-y','-v','error','-ss',str(t),'-i',str(MOVIE),'-frames:v','1','-q:v','2',str(path)])
        im=Image.open(path).convert('RGB');assert np.asarray(im).std()>18
        frames.append(np.asarray(im).astype(float))
        sheet.paste(im.resize((270,480),Image.Resampling.LANCZOS),((i%4)*270,(i//4)*480))
    sheet.save(QA/'encoded-contact-sheet.jpg',quality=96)
    camera_difference=float(np.mean(np.abs(frames[0][310:1530]-frames[1][310:1530])))
    assert camera_difference>.3,'Motion present in actual character footage region'
    artwork=Image.open(ASSETS/'anime-storyboard.png')
    assert artwork.size==(1024,1536)
    sources=[ROOT/'render_mv.py',ROOT/'make_voice.py',ROOT/'mix_audio.py',Path(__file__),ROOT/'storyboard.json',ROOT/'image-prompt.txt',ASSETS/'layout.json']
    assets=[ASSETS/'anime-storyboard.png',ASSETS/'user-focus-screen.jpg',ASSETS/'user-schedule-screen.jpg',OUT/'female-voice-raw.mp3',OUT/'soundtrack.wav',OUT/'captions.srt',OUT/'cover.jpg']
    record={
        'evidence_id':'E-20261022-PROMO-MV-03','verified_at':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'artifact':str(MOVIE),'bytes':MOVIE.stat().st_size,'sha256':sha(MOVIE),
        'format':'1080x1920 / 30fps / 10 seconds / H.264 yuv420p + AAC stereo','frames':300,'full_decode_exit':0,
        'female_narration':{'voice':voice['voice'],'generic_synthetic_voice':True,'script':voice['text'],'all_words_present_in_generation_metadata':True,
            'raw_duration':voice_duration,'speech_last_word_end_with_delay':last_word_end,'mix_delay_seconds':.18,'cut_off':False},
        'audio_peak':peak,'audio_rms':rms,'one_second_audio_rms':segment_rms,
        'character_action':'Same anime student: chooses a time range, taps phone with focus screenshot, puts phone down, writes in notebook.',
        'animation_method':'Four built-in-imagegen keyframes, local hand/pen mesh motion, camera moves, tap ripple and screenshot perspective compositing.',
        'built_in_imagegen':True,'external_image_api_fallback_used':False,
        'user_screenshots':'Both provided images inserted into illustrated phone during opening act (0-4.3s); app status text not rewritten.',
        'camera_motion_difference':camera_difference,'cta':'关注加群，免费领取','invented_qr_or_group_number':False,
        'visual_review':'PENDING: inspect encoded-contact-sheet.jpg and full-size opening, phone and CTA frames.',
        'audio_listening_review':'Not claimed; generated script metadata and encoded signal checked.',
        'scope':'Promotional anime re-enactment with provided screenshots. Not real handset video, end-to-end software acceptance, or a guarantee of quitting phone use.',
        'source_sha256':{str(p.relative_to(ROOT)):sha(p) for p in sources},
        'asset_sha256':{str(p.relative_to(ROOT)):sha(p) for p in assets},
        'validity':'Only these media/source/asset hashes. No APK change or external publication.'
    }
    (QA/'verification.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:record[k] for k in ['format','bytes','sha256','full_decode_exit','audio_peak','audio_rms','female_narration']},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
