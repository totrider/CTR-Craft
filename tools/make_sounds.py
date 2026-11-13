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
          "hang_time", "wall_hit", "slam", "spin_out",
          "light_count", "light_green", "fruit_pickup", "glass_shatter", "mine_drop", "tnt_hit", "tnt_land", "tnt_tick", "tnt_explode", "crate_break", "bomb_launch", "bomb_roll", "bomb_explode", "item_roll", "item_get", "item_get_juiced", "crash_soft", "crash_mid", "crash_hard", "fruit_arrive"]


# the race music's events: empty here (the mod ships no music), a resource pack puts its files in them (see docs/MUSIC.md)
MUSIC_EVENTS = ["music.race_intro", "music.race", "music.final_lap", "music.race_end.first", "music.race_end.other"]


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


def light_count():
    t = t_axis(0.28)
    x = np.sin(2 * np.pi * 440 * t) * decay(t, 0.14) + 0.3 * np.sin(2 * np.pi * 880 * t) * decay(t, 0.08)
    return fade(norm(x, 0.65))


def light_green():
    t = t_axis(0.6)
    x = np.sin(2 * np.pi * 880 * t) * decay(t, 0.3) + 0.35 * np.sin(2 * np.pi * 1320 * t) * decay(t, 0.2)
    return fade(norm(x, 0.7))


def fruit_pickup():
    t = t_axis(0.22)
    x = np.sin(2 * np.pi * chirp_phase(t, 700, 1500)) * decay(t, 0.09)
    x += 0.2 * np.sin(2 * np.pi * 2100 * t) * decay(t, 0.05)
    return fade(norm(x, 0.6))


def fruit_arrive():
    t = t_axis(0.16)
    x = np.sin(2 * np.pi * chirp_phase(t, 1100, 1700)) * decay(t, 0.06)
    x += 0.25 * np.sin(2 * np.pi * 2600 * t) * decay(t, 0.03)
    return fade(norm(x, 0.5))


def chirp_phase(t, f0, f1):
    return f0 * t + (f1 - f0) * t * t / (2 * t[-1])


def glass_shatter():
    t = t_axis(0.5)
    x = bandpass(noise(len(t)), 2500, 9000) * decay(t, 0.12) + 0.5 * np.sin(2 * np.pi * 2400 * t) * decay(t, 0.05)
    x += 0.4 * lowpass(noise(len(t)), 700) * decay(t, 0.06)
    return fade(norm(x, 0.8))


def mine_drop():
    t = t_axis(0.2)
    x = np.sin(2 * np.pi * (260 - 120 * t / 0.2) * t) * decay(t, 0.06) + 0.3 * bandpass(noise(len(t)), 500, 3000) * decay(t, 0.02)
    return fade(norm(x, 0.65))


def tnt_hit():
    t = t_axis(0.22)
    x = np.sin(2 * np.pi * (320 + 200 * t / 0.22) * t) * decay(t, 0.07) + 0.2 * noise(len(t)) * decay(t, 0.02)
    return fade(norm(x, 0.65))


def tnt_land():
    t = t_axis(0.25)
    x = np.sin(2 * np.pi * (140 - 60 * t / 0.25) * t) * decay(t, 0.08) + 0.3 * bandpass(noise(len(t)), 300, 2500) * decay(t, 0.03)
    return fade(norm(x, 0.7))


def tnt_tick():
    t = t_axis(0.18)
    x = (np.sin(2 * np.pi * 880 * t) + 0.4 * np.sin(2 * np.pi * 1320 * t)) * decay(t, 0.07)
    return fade(norm(x, 0.55))


def tnt_explode():
    t = t_axis(0.9)
    x = lowpass(noise(len(t)), 900) * decay(t, 0.25) + 0.8 * np.sin(2 * np.pi * (70 - 30 * t) * t) * decay(t, 0.3)
    x += 0.4 * bandpass(noise(len(t)), 1500, 6000) * decay(t, 0.05)
    return fade(norm(x, 0.9))


def bomb_launch():
    t = t_axis(0.3)
    x = lowpass(noise(len(t)), 1800) * decay(t, 0.07) + 0.7 * np.sin(2 * np.pi * chirp_phase(t, 420, 110)) * decay(t, 0.09)
    return fade(norm(x, 0.8))


def bomb_roll():
    n = int(SR * 0.5)
    x = bandpass(noise(n + 6000), 90, 700)
    x *= 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 4 * np.arange(len(x)) / SR))      # a heavy ball turning over, four beats a second
    xf = 3000                                     # cross-fade the tail into the head so it loops cleanly
    body = x[xf: xf + n].copy()
    body[:xf] = body[:xf] * np.linspace(0, 1, xf) + x[:xf] * np.linspace(1, 0, xf)
    return norm(body, 0.5)


def bomb_explode():
    t = t_axis(1.0)
    x = lowpass(noise(len(t)), 1200) * decay(t, 0.3) + 0.9 * np.sin(2 * np.pi * (85 - 40 * t) * t) * decay(t, 0.32)
    x += 0.5 * bandpass(noise(len(t)), 1800, 7000) * decay(t, 0.06)
    return fade(norm(x, 0.95))


def crate_break():
    t = t_axis(0.35)
    x = bandpass(noise(len(t)), 400, 4000) * decay(t, 0.07) + 0.6 * np.sin(2 * np.pi * 180 * t) * decay(t, 0.05)
    x += 0.4 * bandpass(noise(len(t)), 2500, 7000) * decay(t, 0.02)
    return fade(norm(x, 0.85))


def item_roll():
    t = t_axis(0.5)
    ticks = (np.sin(2 * np.pi * 18 * t) > 0.6).astype(float)
    x = np.sin(2 * np.pi * chirp_phase(t, 500, 900)) * ticks * 0.8 + 0.1 * noise(len(t)) * ticks
    return fade(norm(x, 0.55))


def item_get():
    t = t_axis(0.4)
    x = np.sin(2 * np.pi * 659 * t) * decay(t, 0.18) + 0.5 * np.sin(2 * np.pi * 988 * t) * decay(t, 0.22)
    return fade(norm(x, 0.65))


def item_get_juiced():
    out = np.zeros(int(SR * 0.7))
    for i, f in enumerate((1568.0, 2093.0)):
        t = t_axis(0.5)
        tone = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(4 * np.pi * f * t)) * decay(t, 0.2)
        s0 = int(i * 0.09 * SR)
        out[s0: s0 + len(tone)] += tone
    return fade(norm(out, 0.65))


def crash_soft():
    t = t_axis(0.2)
    x = np.sin(2 * np.pi * (90 + 60 * decay(t, 0.03)) * t) * decay(t, 0.06) + 0.4 * lowpass(noise(len(t)), 1200) * decay(t, 0.03)
    return fade(norm(x, 0.6))


def crash_mid():
    t = t_axis(0.3)
    x = np.sin(2 * np.pi * (70 + 80 * decay(t, 0.04)) * t) * decay(t, 0.09) + 0.6 * lowpass(noise(len(t)), 2200) * decay(t, 0.05)
    return fade(norm(x, 0.8))


def crash_hard():
    t = t_axis(0.5)
    x = np.sin(2 * np.pi * (50 + 90 * decay(t, 0.05)) * t) * decay(t, 0.16) + 0.8 * lowpass(noise(len(t)), 3000) * decay(t, 0.07)
    x += 0.3 * bandpass(noise(len(t)), 2500, 7000) * decay(t, 0.025)
    return fade(norm(x, 0.95))


def write_ogg(name, samples):
    os.makedirs(os.path.join(OUT, "sounds"), exist_ok=True)
    oggenc.write_ogg(os.path.join(OUT, "sounds", name + ".ogg"), np.clip(samples, -1, 1), SR, quality=0.45)


def main():
    for name in SOUNDS:
        write_ogg(name, globals()[name]())
    sj = {name: {"sounds": ["ctrcraft:" + name]} for name in SOUNDS}
    sj.update({name: {"sounds": []} for name in MUSIC_EVENTS})
    with open(os.path.join(OUT, "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sj, f, indent=2)
    print("%d sounds written" % len(SOUNDS))


if __name__ == "__main__":
    main()
