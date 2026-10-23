"""Mix by PCM sample index, preserving every speech sample in exactly 480000 frames."""
from pathlib import Path
import subprocess
import wave
import json
import numpy as np

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'output'
SR=48000;COUNT=10*SR
def decode(path):
    raw=subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','f32le','-ar',str(SR),'-ac','2','-'],capture_output=True,check=True).stdout
    return np.frombuffer(raw,dtype='<f4').reshape(-1,2).astype(float)
voice=decode(OUT/'female-voice-raw.mp3');music=decode(ROOT.parent/'v2/output/original-score.wav')[:COUNT]
gain=min(.15/max(.001,float(np.sqrt(np.mean(voice**2)))),.82/max(.001,float(np.abs(voice).max())))
time=np.arange(COUNT)/SR
music*=.12*np.minimum(1,time/.15)[:,None]*np.minimum(1,(10-time)/.6)[:,None]
mix=music.copy();start=round(.18*SR)
assert start+len(voice)<COUNT,'Complete female narration must fit before video ends'
mix[start:start+len(voice)]+=voice*gain
mix*=min(1,.94/max(.001,float(np.abs(mix).max())))
with wave.open(str(OUT/'soundtrack.wav'),'wb') as out:
    out.setnchannels(2);out.setsampwidth(2);out.setframerate(SR);out.writeframes((mix*32767).astype('<i2').tobytes())
with wave.open(str(OUT/'soundtrack.wav'),'rb') as check:
    assert check.getnframes()==COUNT and check.getframerate()==SR
(ROOT/'qa/mix.json').write_text(json.dumps({'method':'PCM sample-index mix','samples_per_channel':COUNT,'sample_rate':SR,'seconds':10,
    'voice_start_sample':start,'voice_sample_count':len(voice),'complete_voice_kept':True,'music_gain':.12,'voice_gain':gain,
    'peak':float(np.abs(mix).max())},indent=2),encoding='utf-8')
print('Mixed complete narration; verified exactly 480000 stereo frames = 10s.')
