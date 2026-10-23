"""Original 120 BPM instrumental and synchronized UI sound design. No external samples."""
from pathlib import Path
import math
import wave
import numpy as np
from scipy.signal import lfilter

SR = 48000
DURATION = 32


def compose(path: Path):
    count = SR * DURATION
    mix = np.zeros((count, 2), dtype=np.float64)
    rng = np.random.default_rng(20261021)

    def add(signal, at, gain=1, pan=0, space=False):
        start = round(at * SR)
        length = min(len(signal), count - start)
        if start < 0 or length <= 0:
            return
        levels = np.array([math.sqrt((1 - pan) / 2), math.sqrt((1 + pan) / 2)])
        mix[start:start + length] += signal[:length, None] * gain * levels
        if space:
            for delay, level, p in [(.127, .14, -pan), (.251, .085, .45), (.379, .045, -.45)]:
                add(signal, at + delay, gain * level, p)

    def t(duration):
        return np.arange(round(duration * SR)) / SR

    def hz(note):
        return 440 * 2 ** ((note - 69) / 12)

    def keys(note, duration=2.0):
        time = t(duration)
        f = hz(note)
        signal = np.sin(2 * np.pi * f * time) * np.exp(-time * 2.1)
        signal += .22 * np.sin(2 * np.pi * 2.002 * f * time) * np.exp(-time * 4.2)
        signal += .055 * np.sin(2 * np.pi * 3.008 * f * time) * np.exp(-time * 6)
        return signal * (1 - np.exp(-time * 95)) * np.minimum(1, (duration - time) / .2)

    def kick():
        time = t(.33)
        phase = 2 * np.pi * np.cumsum(46 + 83 * np.exp(-time * 40)) / SR
        return np.sin(phase) * np.exp(-time * 15) * (1 - np.exp(-time * 500))

    def hat():
        time = t(.075)
        noise = rng.standard_normal(len(time))
        return lfilter([1, -1], [1], noise) * np.exp(-time * 75) * .25

    def clap():
        time = t(.15)
        noise = lfilter([.25, .5, .25], [1], rng.standard_normal(len(time)))
        return noise * (np.exp(-time * 35) + .4 * np.exp(-np.maximum(0, time - .02) * 70))

    # D minor / B-flat maj7 / F add9 / C major. Two bars per harmony.
    chords = [[50, 57, 60, 65], [46, 53, 57, 62], [48, 53, 57, 67], [48, 55, 60, 64]]
    for bar in range(16):
        at = bar * 2
        chord = chords[(bar // 2) % 4]
        after_lock = at >= 16
        for j, note in enumerate(chord):
            add(keys(note + (12 if after_lock else 0), 3), at + j * .025,
                .10 if after_lock else .085, (j - 1.5) * .25, True)
        time = t(2.5)
        pad = sum(np.sin(2 * np.pi * hz(n) * time) for n in chord) / len(chord)
        pad *= np.minimum(1, time / .35) * np.minimum(1, (2.5 - time) / .75)
        add(pad, at, .055, -.1)
        time = t(1.6)
        bass = np.sin(2 * np.pi * hz(chord[0] - 12) * time)
        bass *= (1 - np.exp(-time * 100)) * np.exp(-time * 2.9)
        add(bass, at, .27 if not after_lock else .17)
        if bar < 8 or bar >= 12:
            for beat in [0, 1, 2.5]:
                add(kick(), at + beat * .5, .28 if bar < 8 else .20)
            for beat in [1, 3]:
                add(clap(), at + beat * .5, .11)
            for beat in range(8):
                add(hat(), at + beat * .25, .12 if beat % 2 else .08, .35 if beat % 2 else -.35)
        elif bar < 15:
            add(kick(), at, .12)
            add(hat(), at + 1, .07, -.35)

    melody = [(8.5, 77), (9.25, 76), (10, 72), (10.75, 69), (12.5, 74), (13.25, 72),
              (14, 69), (18.5, 77), (19.25, 79), (20, 81), (21.0, 77), (22, 76),
              (22.75, 72), (24.5, 77), (25.25, 76), (26, 74), (27, 72), (28.5, 77),
              (29.25, 76), (30, 77)]
    for i, (at, note) in enumerate(melody):
        add(keys(note, 2.3), at, .135, .18 if i % 2 else -.18, True)

    # Early notification-like taps, three countdown ticks, then a single tactile lock click.
    for at in [1.0, 2.0, 4.0, 5.0, 6.0]:
        add(keys(84, .18), at, .055, .15)
    for at in [14.5, 15.0, 15.5]:
        time = t(.055)
        add(np.sin(2 * np.pi * 1320 * time) * np.exp(-time * 105), at, .10)
    time = t(.11)
    click = lfilter([.3, .5, .2], [1], rng.standard_normal(len(time))) * np.exp(-time * 80)
    add(click, 16, .23)
    # Warm resolution, allowed to breathe beneath the end card.
    for j, note in enumerate([50, 57, 62, 65, 69]):
        add(keys(note, 3), 30 + j * .025, .075, (j - 2) * .15, True)

    times = np.arange(count) / SR
    envelope = np.minimum(1, times / .12) * np.minimum(1, (DURATION - times) / .9)
    mix *= envelope[:, None]
    mix = np.tanh(mix * 1.2)
    mix *= .86 / max(.01, np.max(np.abs(mix)))
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as stream:
        stream.setnchannels(2)
        stream.setsampwidth(2)
        stream.setframerate(SR)
        stream.writeframes((mix * 32767).astype('<i2').tobytes())
    return {"sample_rate": SR, "seconds": DURATION, "bpm": 120, "peak": float(np.max(np.abs(mix))), "source": "original procedural composition, no external samples"}


if __name__ == '__main__':
    print(compose(Path(__file__).resolve().parent / 'output' / 'original-score.wav'))
