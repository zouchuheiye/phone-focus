"""Verify the final encoded MV-02 and extract frames for human visual review."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import hashlib
import json
import subprocess
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parent
OUT,QA=ROOT/'output',ROOT/'qa'
MOVIE=OUT/'phone-focus-daily-mv-1080p.mp4'
SAMPLES=[1.7,5.8,10.8,12.6,14.4,15.9,17.8,19.7,23.7,28.8,33.2,35.2]

def run(args):return subprocess.run(args,capture_output=True,check=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    metadata=json.loads(run(['ffprobe','-v','error','-count_frames','-show_streams','-show_format','-of','json',str(MOVIE)]).stdout)
    (QA/'ffprobe.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
    video=next(s for s in metadata['streams'] if s['codec_type']=='video')
    audio=next(s for s in metadata['streams'] if s['codec_type']=='audio')
    assert video['codec_name']=='h264' and video['pix_fmt']=='yuv420p'
    assert (video['width'],video['height'])==(1080,1920)
    assert video['avg_frame_rate']=='30/1' and int(video['nb_read_frames'])==1080
    assert audio['codec_name']=='aac' and audio['channels']==2
    assert 35.95<float(metadata['format']['duration'])<36.05
    assert abs(float(video['duration'])-float(audio['duration']))<.05
    decode=run(['ffmpeg','-v','error','-xerror','-i',str(MOVIE),'-f','null','-'])
    (QA/'decode.txt').write_bytes(decode.stderr or b'PASS: full video/audio decode; exit 0.\n')
    raw=run(['ffmpeg','-v','error','-i',str(MOVIE),'-vn','-f','f32le','-ac','2','-ar','48000','-']).stdout
    samples=np.frombuffer(raw,dtype='<f4').reshape(-1,2)
    assert len(samples)>=48000*35.95 and np.isfinite(samples).all()
    peak=float(np.abs(samples).max());rms=float(np.sqrt(np.mean(samples**2)))
    assert .08<peak<.999 and .015<rms<.5
    segment_rms=[float(np.sqrt(np.mean(part**2))) for part in np.array_split(samples,18)]
    assert min(segment_rms)>.005
    tiles=[]
    for i,second in enumerate(SAMPLES):
        path=QA/f'encoded-{i+1:02d}.jpg'
        run(['ffmpeg','-y','-v','error','-ss',str(second),'-i',str(MOVIE),'-frames:v','1','-q:v','2',str(path)])
        with Image.open(path) as image:
            array=np.asarray(image);assert array.std()>12,'Nonblank encoded frame'
            tiles.append(image.convert('RGB').resize((270,480),Image.Resampling.LANCZOS))
    sheet=Image.new('RGB',(1080,1440))
    for i,tile in enumerate(tiles):sheet.paste(tile,((i%4)*270,(i//4)*480))
    sheet.save(QA/'encoded-contact-sheet.jpg',quality=96)
    # Check actual state changes in the card/timeline regions of the encoded frames.
    arrays=[np.asarray(Image.open(QA/f'encoded-{i+1:02d}.jpg').convert('RGB')).astype(float) for i in range(len(SAMPLES))]
    differences={
        'independent_switch_off':float(np.mean(np.abs(arrays[2][900:1170]-arrays[3][900:1170]))),
        'independent_switch_restored':float(np.mean(np.abs(arrays[3][900:1170]-arrays[4][900:1170]))),
        'focus_to_free_gap':float(np.mean(np.abs(arrays[5]-arrays[6]))),
        'free_gap_to_focus':float(np.mean(np.abs(arrays[6]-arrays[7])))
    }
    assert min(differences.values())>.5,'Encoded demonstration states actually change'
    sources=[ROOT/'render_mv.py',ROOT/'music.py',ROOT/'storyboard.json',Path(__file__),ROOT.parent/'render_mv.py']
    app=ROOT.parents[1]/'app/src/main/java/com/phonefocus/guard'
    sources += [app/'DailySchedule.java',app/'ScheduleSetupActivity.java',app/'TimeRangeEditorDialog.java']
    record={
        'evidence_id':'E-20261022-PROMO-MV-02',
        'verified_at':datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'artifact':str(MOVIE),'bytes':MOVIE.stat().st_size,'sha256':sha(MOVIE),
        'app_version_illustrated':'1.3.1','format':'1080x1920 / 30fps / 36s / H.264 yuv420p + AAC stereo',
        'video_frames':1080,'full_decode_exit':decode.returncode,
        'audio_peak':peak,'audio_rms':rms,'two_second_audio_rms':segment_rms,
        'encoded_demo_changes':differences,'encoded_review_times':SAMPLES,
        'original_score':True,'external_music_images_voices':False,'handset_footage':False,
        'visual_review':'PENDING: inspect encoded-contact-sheet.jpg and full-size editor/end frames.',
        'scope':'Media quality and illustrative feature communication only. No physical-device enforcement, all-model compatibility or permission grant claim.',
        'source_sha256':{str(path.resolve()):sha(path) for path in sources},
        'asset_sha256':{str(path.relative_to(ROOT)):sha(path) for path in [OUT/'cover.jpg',OUT/'original-score.wav',OUT/'captions.srt']},
        'validity':'This exact media hash and source snapshots; app device acceptance remains separate.'
    }
    (QA/'verification.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({key:record[key] for key in ['format','bytes','sha256','full_decode_exit','audio_peak','audio_rms','encoded_demo_changes']},indent=2))

if __name__=='__main__':main()
