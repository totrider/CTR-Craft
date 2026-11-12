"""
The kart sounds: original, synthesised placeholders (CTR's recordings are Sony / Naughty Dog's and are not ours to ship).

  python tools/make_sounds.py

Writes src/main/resources/assets/ctrcraft/sounds/*.ogg and sounds.json. Needs numpy and Krita's libogg / libvorbis DLLs
(tools/oggenc.py; set KRITA_BIN if Krita is elsewhere). Every sound is mono 44.1 kHz.

The engine and skid are LOOPS (the game bends the engine's rate with CTR's own engine-sound values, so the base note sits in the
middle of the range); the rest are one-shots. To use your own recordings instead, drop .ogg files with the same names into a
resource pack at assets/ctrcraft/sounds/ (the names are in SOUNDS below); nothing else changes.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oggenc  # noqa: E402

SR = 44100
OUT = os.path.join("src", "main", "resources", "assets", "ctrcraft")
rng = np.random.default_rng(1996)

SOUNDS = ["engine_loop", "skid_loop", "turbo_loop", "hop", "land", "drift_start", "boost", "boost_fail", "meter_red", "meter_full",
          "hang_time", "wall_hit", "slam", "spin_out"]


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def bandpass(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = np.exp(-(np.maximum(lo - f, 0) / (0.35 * lo + 1)) ** 2) * np.exp(-(np.maximum(f - hi, 0) / (0.35 * hi + 1)) ** 2)
    return np.fft.irfft(spec * mask, len(x))


def lowpass(x, hi):
    return bandpass(x, 0, hi)


def noise(n):
    return rng.standard_normal(n)


def decay(t, tau):
    return np.exp(-t / tau)


def fade(x, ms=4):
    n = int(SR * ms / 1000)
    x = x.copy()
    x[:n] *= np.linspace(0, 1, n)
    x[-n:] *= np.linspace(1, 0, n)
    return x


def norm(x, peak=0.8):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def chirp(t, f0, f1, power=1.0):
    k = (f1 - f0)
    # phase of a frequency sweep from f0 to f1 over the whole duration
    T = t[-1] + 1 / SR
    return np.sin(2 * np.pi * (f0 * t + k * T / (power + 1) * (t / T) ** (power + 1)))


def engine_loop():
    f0, cycles = 90.0, 54
    T = cycles / f0
    n = int(round(T * SR))
    t = np.arange(n) * T / n
    x = np.zeros(n)
    for k in range(1, 12):                       # a rough, buzzy harmonic stack
        x += np.sin(2 * np.pi * k * f0 * t + rng.uniform(0, 6.28)) / (k ** 1.15)
    x *= 0.78 + 0.22 * np.sin(2 * np.pi * 2 * f0 * t)           # the putt-putt of the cylinders
    for k in range(13, 90):                      # periodic "noise": many weak harmonics with random phases
        x += 0.035 * rng.uniform(0.3, 1) * np.sin(2 * np.pi * k * f0 * t + rng.uniform(0, 6.28))
    return norm(x, 0.7)


def skid_loop():
    n = SR * 1
    x = bandpass(noise(n + 8000), 1400, 4200)[: n + 8000]
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * 7 * np.arange(len(x)) / SR)
    xf = 4000                                     # cross-fade the tail into the head so it loops cleanly
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.6)


def turbo_loop():
    n = int(SR * 0.65)
    x = lowpass(noise(n + 6000), 2600)
    x *= 0.85 + 0.15 * np.sin(2 * np.pi * 23 * np.arange(len(x)) / SR)
    xf = 3000                                     # cross-fade the tail into the head so it loops cleanly
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.55)


def hop():
    t = t_axis(0.22)
    x = chirp(t, 260, 620, 1.0) * decay(t, 0.09) + 0.35 * noise(len(t)) * decay(t, 0.01)
    return fade(norm(x))


def land():
    t = t_axis(0.2)
    x = np.sin(2 * np.pi * (60 + 90 * decay(t, 0.04)) * t) * decay(t, 0.07)
    x += 0.5 * lowpass(noise(len(t)), 900) * decay(t, 0.03)
    return fade(norm(x))


def drift_start():
    t = t_axis(0.3)
    x = bandpass(noise(len(t)), 1800, 3800) * (np.sin(np.pi * t / 0.3) ** 0.6)
    x += 0.4 * chirp(t, 1500, 2300) * decay(t, 0.2)
    return fade(norm(x, 0.7))


def boost():
    t = t_axis(0.7)
    swell = np.minimum(t / 0.08, 1) * decay(np.maximum(t - 0.08, 0), 0.35)
    x = lowpass(noise(len(t)), 5000) * swell * 0.9
    x += 0.5 * chirp(t, 140, 700, 1.4) * swell
    x += 0.25 * np.sin(2 * np.pi * 1760 * t) * decay(t, 0.05)
    return fade(norm(x))


def boost_fail():
    t = t_axis(0.28)
    x = lowpass(noise(len(t)), 3000) * decay(t, 0.09)
    x += 0.4 * chirp(t, 220, 90) * decay(t, 0.08)
    return fade(norm(x, 0.7))


def meter_red():
    t = t_axis(0.12)
    x = np.sin(2 * np.pi * 1318 * t) * decay(t, 0.05)
    return fade(norm(x, 0.55))


def meter_full():
    t = t_axis(0.35)
    x = np.sin(2 * np.pi * 330 * t) * decay(t, 0.12) + 0.5 * np.sin(2 * np.pi * 247 * t) * decay(t, 0.15)
    return fade(norm(x, 0.6))


def hang_time():
    notes = [523.25, 659.25, 783.99, 1046.5]
    out = np.zeros(int(SR * 0.5))
    for i, f in enumerate(notes):
        t = t_axis(0.22)
        tone = (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(4 * np.pi * f * t)) * decay(t, 0.09)
        start = int(i * 0.07 * SR)
        out[start: start + len(tone)] += tone
    return fade(norm(out, 0.6))


def wall_hit():
    t = t_axis(0.25)
    x = bandpass(noise(len(t)), 300, 2500) * decay(t, 0.05) + 0.7 * np.sin(2 * np.pi * 130 * t) * decay(t, 0.08)
    return fade(norm(x))


def slam():
    t = t_axis(0.55)
    x = np.sin(2 * np.pi * (45 + 80 * decay(t, 0.06)) * t) * decay(t, 0.18)
    x += 0.8 * lowpass(noise(len(t)), 1800) * decay(t, 0.07)
    x += 0.3 * bandpass(noise(len(t)), 2000, 6000) * decay(t, 0.02)
    return fade(norm(x, 0.9))


def spin_out():
    t = t_axis(0.7)
    x = chirp(t, 900, 260, 0.8) * decay(t, 0.45) * (0.7 + 0.3 * np.sin(2 * np.pi * 14 * t))
    x += 0.25 * bandpass(noise(len(t)), 1500, 3500) * decay(t, 0.3)
    return fade(norm(x, 0.7))


def write_ogg(name, samples):
    os.makedirs(os.path.join(OUT, "sounds"), exist_ok=True)
    oggenc.write_ogg(os.path.join(OUT, "sounds", name + ".ogg"), np.clip(samples, -1, 1), SR, quality=0.45)


def main():
    for name in SOUNDS:
        write_ogg(name, globals()[name]())
    sj = {name: {"sounds": ["ctrcraft:" + name]} for name in SOUNDS}
    with open(os.path.join(OUT, "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sj, f, indent=2)
    print("%d sounds written" % len(SOUNDS))


if __name__ == "__main__":
    main()
