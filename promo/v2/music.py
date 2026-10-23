"""36s / 120 BPM original instrumental, synthesized locally without samples or voices."""
from pathlib import Path
import math
import wave
import numpy as np
from scipy.signal import lfilter

SR, SECONDS, BPM = 48000, 36, 120

def compose(path: Path):
    n = SR * SECONDS
    mix = np.zeros((n, 2), dtype=np.float64)
    rng = np.random.default_rng(20261022)
    def time(d): return np.arange(round(d*SR))/SR
    def hz(note): return 440*2**((note-69)/12)
    def add(sound,at,volume=1,pan=0,echo=False):
        pos = round(at*SR); length = min(len(sound),n-pos)
        if pos<0 or length<=0: return
        mix[pos:pos+length] += sound[:length,None]*volume*np.array([math.sqrt((1-pan)/2),math.sqrt((1+pan)/2)])
        if echo:
            for delay,gain in [(.1875,.15),(.375,.075)]: add(sound,at+delay,volume*gain,-pan)
    def keys(note,d=2.2):
        t=time(d); f=hz(note)
        sound=np.sin(2*np.pi*f*t)*np.exp(-t*2.6)
        sound+=.19*np.sin(2*np.pi*f*2.001*t)*np.exp(-t*5)
        sound+=.055*np.sin(2*np.pi*f*4.003*t)*np.exp(-t*8)
        return sound*(1-np.exp(-t*120))*np.minimum(1,(d-t)/.2)
    def pluck(note):
        t=time(.65); f=hz(note)
        return (np.sin(2*np.pi*f*t)+.18*np.sin(2*np.pi*f*3*t))*np.exp(-t*7)*(1-np.exp(-t*200))
    def kick():
        t=time(.36); phase=2*np.pi*np.cumsum(45+92*np.exp(-t*38))/SR
        return np.sin(phase)*np.exp(-t*14)*(1-np.exp(-t*700))
    def hat():
        t=time(.07)
        return lfilter([1,-1],[1],rng.standard_normal(len(t)))*np.exp(-t*85)
    def snare():
        t=time(.16); noise=lfilter([.2,.6,.2],[1],rng.standard_normal(len(t)))
        return noise*np.exp(-t*30)+.18*np.sin(2*np.pi*180*t)*np.exp(-t*40)
    # Cmaj9 - G6 - Am7 - Fmaj9. Bright plucks against a warm, quiet pad.
    chords=[[48,55,59,62,64],[43,50,55,59,64],[45,52,55,60,64],[41,48,52,55,60]]
    for bar in range(18):
        at=bar*2; chord=chords[(bar//2)%4]
        if bar>=16: chord=[48,55,59,62,64]
        for j,note in enumerate(chord): add(keys(note),at+j*.022,.09,(j-2)*.2,True)
        t=time(2.5); pad=sum(np.sin(2*np.pi*hz(note)*t) for note in chord)/len(chord)
        pad*=np.minimum(1,t/.4)*np.minimum(1,(2.5-t)/.7)
        add(pad,at,.06)
        t=time(1.6); bass=np.sin(2*np.pi*hz(chord[0]-12)*t)*(1-np.exp(-t*100))*np.exp(-t*2)
        add(bass,at,.22)
        quiet=27<=at<31
        if not quiet:
            for beat in [0,1.5,2.5]: add(kick(),at+beat*.5,.26 if at<27 else .16)
            for beat in [1,3]: add(snare(),at+beat*.5,.12)
            for beat in range(8): add(hat(),at+beat*.25,.037 if beat%2 else .025,.4 if beat%2 else -.4)
        if at>=4:
            for j in range(4):
                note=chord[(j+bar)%len(chord)]+24
                add(pluck(note),at+.25+j*.5,.075 if quiet else .105,(-.25 if j%2 else .25),True)
    melody=[(4.5,76),(5.25,79),(6,81),(7,79),(8,74),(10,76),(11,74),(12,72),
            (14,79),(15,76),(17,79),(19,81),(21,79),(22,84),(23,83),(24,79),
            (25,76),(27.5,79),(28.5,76),(30,74),(31.5,72),(33,76),(34,79)]
    for i,(at,note) in enumerate(melody): add(keys(note,1.8),at,.13,(-.15 if i%2 else .15),True)
    # Small UI clicks synchronize with wheel selection and independent switches.
    for at in [4.75,5.25,6.75,7.25,10,11,12.5,15,17,19,21]:
        t=time(.045)
        add(np.sin(2*np.pi*1320*t)*np.exp(-t*110),at,.065)
    for at in [4,9,15,21,27,31]:
        t=time(.28); swoosh=lfilter([1,-.8],[1],rng.standard_normal(len(t)))
        swoosh*=np.sin(np.pi*t/.28)**2
        add(swoosh,at-.14,.014,.15)
    for j,note in enumerate([48,55,60,64,67,74]): add(keys(note,3),33+j*.025,.075,(j-2.5)*.13,True)
    t=np.arange(n)/SR
    mix*=np.minimum(1,t/.1)[:,None]*np.minimum(1,(SECONDS-t)/1.2)[:,None]
    mix=np.tanh(mix*1.3); mix*=.86/np.max(np.abs(mix))
    path.parent.mkdir(parents=True,exist_ok=True)
    with wave.open(str(path),'wb') as out:
        out.setnchannels(2);out.setsampwidth(2);out.setframerate(SR)
        out.writeframes((mix*32767).astype('<i2').tobytes())
    return {'bpm':BPM,'seconds':SECONDS,'sample_rate':SR,'peak':float(np.abs(mix).max()),
            'source':'Original local synthesis; electric piano, plucks, bass, pad and percussion. No samples or voices.'}
