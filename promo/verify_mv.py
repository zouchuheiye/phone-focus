"""Inspect the encoded MP4, fully decode both tracks, and extract real output frames."""
from pathlib import Path
from datetime import datetime, timezone, timedelta
import hashlib
import json
import subprocess

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
MOVIE = ROOT / 'output' / 'phone-focus-promo-vertical-1080p.mp4'
QA = ROOT / 'qa'


def run(command):
    return subprocess.run(command, capture_output=True, check=True)


def main():
    metadata = json.loads(run(['ffprobe', '-v', 'error', '-count_frames', '-show_streams', '-show_format', '-of', 'json', str(MOVIE)]).stdout)
    (QA/'ffprobe.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    video = next(s for s in metadata['streams'] if s['codec_type'] == 'video')
    audio = next(s for s in metadata['streams'] if s['codec_type'] == 'audio')
    assert video['codec_name'] == 'h264'
    assert (video['width'], video['height']) == (1080, 1920)
    assert video['pix_fmt'] == 'yuv420p'
    assert video['avg_frame_rate'] == '30/1'
    assert int(video['nb_read_frames']) == 960
    assert audio['codec_name'] == 'aac' and audio['channels'] == 2
    assert 31.95 < float(metadata['format']['duration']) < 32.05
    assert abs(float(video['duration'])-float(audio['duration'])) < .05
    decode = run(['ffmpeg', '-v', 'error', '-xerror', '-i', str(MOVIE), '-f', 'null', '-'])
    (QA/'decode.txt').write_bytes(decode.stderr or b'PASS: full video/audio decode; exit 0.\n')

    samples = run(['ffmpeg', '-v', 'error', '-i', str(MOVIE), '-vn', '-f', 'f32le', '-ac', '2', '-ar', '48000', '-']).stdout
    samples = np.frombuffer(samples, dtype='<f4').reshape(-1, 2)
    assert len(samples) >= 48000 * 31.95
    assert np.isfinite(samples).all()
    peak = float(np.max(np.abs(samples)))
    rms = float(np.sqrt(np.mean(samples**2)))
    # Require real, unclipped music rather than an empty audio stream.
    assert .08 < peak < .999
    assert .015 < rms < .5
    segment_rms = [float(np.sqrt(np.mean(part**2))) for part in np.array_split(samples, 16)]
    assert min(segment_rms) > .005

    tiles = []
    for i, second in enumerate([1.8, 5.5, 9.5, 14.8, 16.8, 20.0, 26.3, 30.3]):
        path = QA / f'encoded-{i+1:02d}.jpg'
        run(['ffmpeg', '-y', '-v', 'error', '-ss', str(second), '-i', str(MOVIE), '-frames:v', '1', '-q:v', '2', str(path)])
        with Image.open(path) as image:
            tiles.append(image.convert('RGB').resize((270, 480), Image.Resampling.LANCZOS))
    sheet = Image.new('RGB', (1080, 960))
    for i, tile in enumerate(tiles):
        sheet.paste(tile, ((i%4)*270, (i//4)*480))
    sheet.save(QA/'encoded-contact-sheet.jpg', quality=95)

    record = {
        'evidence_id': 'E-20261021-PROMO-MV-01',
        'verified_at': datetime.now(timezone(timedelta(hours=8))).isoformat(),
        'artifact': str(MOVIE),
        'sha256': hashlib.sha256(MOVIE.read_bytes()).hexdigest(),
        'bytes': MOVIE.stat().st_size,
        'format': '1080x1920 / 30fps / 32 seconds / H.264 yuv420p + AAC stereo',
        'video_frames': int(video['nb_read_frames']),
        'full_decode_exit': decode.returncode,
        'audio_peak': peak,
        'audio_rms': rms,
        'two_second_audio_rms': segment_rms,
        'original_score': True,
        'external_images_music_voices': False,
        'handset_footage': False,
        'visual_review': 'pending review of encoded-contact-sheet.jpg',
        'scope': 'Media format, decode, audio signal, and sampled visual review only; not handset feature acceptance',
        'source_sha256': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in [ROOT/'render_mv.py', ROOT/'music.py', ROOT/'storyboard.json', Path(__file__)]}
    }
    (QA/'verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({key: record[key] for key in ['format', 'bytes', 'sha256', 'full_decode_exit', 'audio_peak', 'audio_rms']}, indent=2))


if __name__ == '__main__':
    main()
