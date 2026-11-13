"""
Extracts the race jingles of YOUR OWN CTR disc into a local resource pack that puts them into the mod's music events (the race start, the last lap and the two
finish jingles; see docs/MUSIC.md). The disc image must be a raw one (2352 bytes a sector: the music is CD-XA audio, which a cooked .iso does not keep).

  python tools/extract_ctr_music.py [--disc "D:/CTR.bin"] [--install "F:/.../resourcepacks"] [--list] [--no-level-music] [--jobs N]

The pack goes to the git-ignored local-assets/ctr-music/ (and into every --install folder): never in the jar, never uploaded, the music is Sony's / Naughty Dog's.

How the game finds a track (CDSYS_XAPlay in the decomp, platform/native_audio.c): XA/ENG.XNF holds, per category (0 = music), a first track index and a count, and
per track a channel number, a file number and a sector count; the audio is XA/MUSIC/S<file>.XA, sectors of several channels interleaved, the track being the sectors of
its channel. Each sector is 18 frames of 8 sound units of 28 CD-XA ADPCM samples; the sector's coding byte says mono or stereo and 37.8 or 18.9 kHz.
Which track is played when (HOWL_AudioState.c Audio_SetState): the race intro jingles are music 0 to 3, a win is 4, any other finish 5, the last lap 6.

The level music is sequenced, not recorded: SOUNDS/KART.HWL holds a CSEQ song pack per level, played by the game's sequencer through the instrument samples of the
sound bank. tools/ctr_cseq.py ports that sequencer and the SPU voices and renders song 0 of each level's pack (one full loop, see docs/MUSIC.md) to
sounds/music/ctr_level_<level>.ogg, listed under music.race; the Aku Aku and Uka Uka mask songs (songs 1 and 2 of the pack, which differ a little from level to level
because the pack's instrument table does) go to ctr_aku_aku_<level>.ogg / ctr_uka_uka_<level>.ogg under music.aku_aku / music.uka_uka, which the mod does not play yet.
A report with the checks of every song (tempo, length, loop, level, spectrum) is written to music-report.json beside the pack.
"""
import argparse
import hashlib
import json
import multiprocessing
import os
import shutil
import struct
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ctr_cseq  # noqa: E402
import extract_ctr_sounds as sounds  # noqa: E402
import oggenc  # noqa: E402

RAW = 2352
POS = (0, 60, 115, 98, 122)
NEG = (0, 0, -52, -55, -60)

# our sound event -> the music tracks (category 0) it plays, and the decomp site
EVENTS = {
    "music.race_intro": ([0, 1, 2, 3], "Audio_SetState AUDIO_RACE_INTRO: XA music 0 to 3 in turn"),
    "music.final_lap": ([6], "Audio_SetState AUDIO_FINAL_LAP: XA music 6, the level music turned down"),
    "music.race_end.first": ([4], "Audio_SetState AUDIO_RACE_END: XA music 4 when the driver won"),
    "music.race_end.other": ([5], "Audio_SetState AUDIO_RACE_END: XA music 5 for any other place"),
}


def raw_sector(disc, lba):
    if disc.layout != "raw":
        sys.exit("The XA audio needs a raw disc image (2352 bytes a sector); this one is a cooked .iso.")
    disc.f.seek(lba * RAW)
    return disc.f.read(RAW)


def xnf_track(disc, category, track):
    """(channel, file number, sector count) of a track, from XA/ENG.XNF."""
    lba, size = disc.find("XA/ENG.XNF")
    d = disc.read(lba, size)
    if struct.unpack_from("<I", d, 0)[0] != 0x464E4958:
        sys.exit("XA/ENG.XNF has an unexpected header")
    total_xas, total_tracks = struct.unpack_from("<II", d, 0x0C)
    count = struct.unpack_from("<I", d, 0x2C + category * 4)[0]
    first = struct.unpack_from("<I", d, 0x38 + category * 4)[0]
    if track >= count:
        raise IndexError("category %d has %d tracks" % (category, count))
    entry = 0x44 + total_xas * 4 + (first + track) * 4
    channel, filenum, sectors = struct.unpack_from("<BBh", d, entry)
    return channel, filenum, sectors, count


def decode_track(disc, filenum, channel, max_sectors):
    """The decoded PCM (int16, shape (n, 2)) and the rate of a track: the audio sectors of {channel} among the first {max_sectors} sectors of XA/MUSIC/S{filenum}.XA
    (the game scans that many sectors of the file, channels interleaved, and plays what belongs to its channel)."""
    lba, size = disc.find("XA/MUSIC/S%02d.XA" % filenum)
    old = [0, 0]
    older = [0, 0]
    out = []
    rate = 37800
    stereo = False
    n_sectors = 0
    for s in range(min(size // 2048, max_sectors)):
        sec = raw_sector(disc, lba + s)
        file_no, chan, submode, coding = sec[16], sec[17], sec[18], sec[19]
        if not (submode & 0x04) or file_no != 1 or chan != channel or (coding >> 4) & 3:
            continue
        n_sectors += 1
        stereo = bool(coding & 3)
        rate = 37800 if ((coding >> 2) & 3) == 0 else 18900
        left, right = [], []
        for frame in range(18):
            fo = 24 + frame * 128
            units = []
            for su in range(8):
                p_index = (su & 3) | ((su & 4) << 1)
                param = sec[fo + 4 + p_index]
                shift, weight = param & 15, min((param >> 4) & 15, 4)
                w0, w1 = POS[weight], NEG[weight]
                ch = (su & 1) if stereo else 0
                samples = []
                for i in range(28):
                    byte = sec[fo + 16 + i * 4 + (su >> 1)]
                    nib = ((byte & 15) << 4) if (su & 1) == 0 else (byte & 0xF0)
                    v = (nib - 256 if nib > 127 else nib) * 256
                    v >>= shift
                    v += (old[ch] * w0) >> 6
                    v += (older[ch] * w1) >> 6
                    v = max(-32768, min(32767, v))
                    older[ch], old[ch] = old[ch], v
                    samples.append(v)
                units.append(samples)
            if stereo:
                for block in range(4):
                    left += units[block * 2]
                    right += units[block * 2 + 1]
            else:
                for su in range(8):
                    left += units[su]
        if stereo:
            out.append(np.stack([np.array(left), np.array(right)], axis=1))
        else:
            a = np.array(left)
            out.append(np.stack([a, a], axis=1))
    if not out:
        return None, rate, 0
    return np.concatenate(out).astype(np.int16), rate, n_sectors


# ---------------------------------------------------------------------------------------------------------------- level music (CSEQ)

TARGET_PEAK = 0.89            # the loudest level song peaks here after the one makeup gain every song gets (-1 dBFS)
MAX_GAIN = 16.0


def _song_ticks(song):
    """The length of the longest sequence in ticks, read straight from the event streams (not through the sequencer): the independent check of the length."""
    best = 0
    for seq in song.seqs:
        pos, ticks = seq.start, 0
        while True:
            n, pos = ctr_cseq.get_next_note(seq.data, pos)
            ticks += n
            op = seq.data[pos]
            if op == 3 or op > 10:
                break
            pos += ctr_cseq.OPCODE_SIZE[op]
        best = max(best, ticks)
    return best


_WORKER = {}


def _init_worker(hwl_bytes):
    howl = sounds.Howl(hwl_bytes)
    _WORKER["hwl"], _WORKER["howl"] = hwl_bytes, howl
    _WORKER["bank"] = ctr_cseq.SampleBank(howl, sounds.decode_adpcm, sounds.steady_loop)


def _render_job(job):
    """Renders one song in a worker process: the audio goes to a .npy file in the temp folder, the numbers come back."""
    name, level, song_id, tmp = job
    howl, bank = _WORKER["howl"], _WORKER["bank"]
    pack = ctr_cseq.CseqPack(_WORKER["hwl"], howl.song_offsets[level])
    song = pack.songs[song_id]
    audio, info = ctr_cseq.render_song(song, pack, song_id, bank)
    ticks = _song_ticks(song)
    info["expected_seconds"] = ticks / (info["tempo_per_frame"] / 65536.0) / ctr_cseq.FRAME_RATE      # the tempo the game computes (SongPool_CalculateTempo), at its frame rate
    info["nominal_seconds"] = ticks * 60.0 / (song.bpm * song.tpqn)                                  # what bpm and tpqn say
    info["beat_check"] = _beat_check(audio, song.bpm)
    np.save(os.path.join(tmp, name + ".npy"), audio)
    info["peak"] = float(np.abs(audio).max())
    return name, info


def _beat_check(audio, bpm):
    """Tempo seen from the audio alone: the autocorrelation of the onset strength must peak at the beat period (the ratio to its mean over 0.25 to 2 beats is the
    peak's height; well above 1 means a steady pulse at that tempo)."""
    mono = audio.mean(axis=1)
    hop = 441                                                  # 10 ms
    n = len(mono) // hop
    env = np.sqrt(np.mean(mono[: n * hop].reshape(n, hop) ** 2, axis=1))
    onset = np.maximum(np.diff(env), 0)
    onset -= onset.mean()
    ac = np.correlate(onset, onset, mode="full")[len(onset) - 1:]
    beat = 60.0 / bpm * 60.0 / ctr_cseq.FRAME_RATE * 100       # in 10 ms hops (the game's 60 Hz tempo at 59.94 Hz)
    lo, hi = int(beat * 0.25), int(beat * 2)
    k = int(round(beat))
    peak = float(ac[max(k - 1, 0): k + 2].max())
    return {"beat_period_s": round(beat / 100, 4), "autocorr_peak_over_mean": round(peak / max(float(np.mean(np.abs(ac[lo:hi]))), 1e-12), 2)}


def _spectrum_report(audio):
    """Sanity numbers of a rendered song: loudness, silence and where the energy sits."""
    mono = audio.mean(axis=1)
    rate = ctr_cseq.OUT_RATE
    rms = float(np.sqrt(np.mean(mono ** 2)))
    win = rate // 10
    n = len(mono) // win
    wr = np.sqrt(np.mean(mono[: n * win].reshape(n, win) ** 2, axis=1))
    block = 1 << 15
    count = max(1, len(mono) // block)
    spec = np.zeros(block // 2 + 1)
    window = np.hanning(block)
    for i in range(count):
        seg = mono[i * block: (i + 1) * block]
        if len(seg) < block:
            seg = np.pad(seg, (0, block - len(seg)))
        spec += np.abs(np.fft.rfft(seg * window)) ** 2
    freqs = np.fft.rfftfreq(block, 1.0 / rate)
    total = float(spec.sum()) or 1.0
    bands = {}
    for lo, hi in ((0, 100), (100, 500), (500, 2000), (2000, 6000), (6000, 12000), (12000, 22050)):
        bands["%d-%d Hz" % (lo, hi)] = round(float(spec[(freqs >= lo) & (freqs < hi)].sum()) / total, 4)
    return {"rms_dbfs": round(20 * np.log10(max(rms, 1e-9)), 1), "dc_offset": round(float(mono.mean()), 6),
            "silent_tenths_of_seconds": int((wr < 10 ** (-80 / 20)).sum()), "centroid_hz": round(float((freqs * spec).sum() / total), 0), "energy_by_band": bands}


def _pitch_check(howl, hwl):
    """Plays one looping instrument at notes 57, 60, 69 and 72 through the same pitch table and voice code and measures the pitch of the result: octaves must be 2.0 and
    a semitone 1.059, whatever sample is behind it."""
    pack = ctr_cseq.CseqPack(hwl, howl.song_offsets[0])
    bank = ctr_cseq.SampleBank(howl, sounds.decode_adpcm, sounds.steady_loop)
    # the instrument whose sample is longest at its own pitch, so a few thousand samples of steady tone can be measured
    inst = max(pack.instruments, key=lambda i: len(bank.get(i["spu"])[0]) * 4096 / max(i["base_pitch"], 1) if bank.get(i["spu"])[1] is None else 1e9)
    out = {}
    for idx in (57, 60, 69, 72):
        note = ctr_cseq.Note(0, 0, "inst", idx, ctr_cseq.instrument_pitch(inst["base_pitch"], idx, 0x80), (0x3000, 0x3000), 0, inst["ad"], inst["sr"], inst["spu"])
        l, _r, _n = ctr_cseq.render_voice(note, bank, {}, 0, 44100, 44100)
        seg = l[:8192] * np.hanning(len(l[:8192]))
        spec = np.abs(np.fft.rfft(seg))
        # parabolic interpolation around the strongest bin gives the frequency more finely than the bin width
        k = int(np.argmax(spec[1:-1])) + 1
        a, b, c = np.log(spec[k - 1] + 1e-12), np.log(spec[k] + 1e-12), np.log(spec[k + 1] + 1e-12)
        out[idx] = (k + 0.5 * (a - c) / (a - 2 * b + c)) * 44100 / 8192
    return {"strongest_peak_hz": {str(k): round(float(v), 1) for k, v in out.items()},
            "ratio_note72_to_60_expect_2": round(float(out[72] / out[60]), 3), "ratio_note69_to_57_expect_2": round(float(out[69] / out[57]), 3),
            "ratio_note60_to_57_expect_1.189": round(float(out[60] / out[57]), 3)}


def render_level_music(disc, pack_dir, jobs):
    """Renders every level's song 0 and the mask songs into {pack_dir}/assets/ctrcraft/sounds/music; returns ({sound event: [sound names]}, report)."""
    lba, size = disc.find("SOUNDS/KART.HWL")
    hwl = disc.read(lba, size)
    howl = sounds.Howl(hwl)
    if len(howl.song_offsets) < len(ctr_cseq.LEVELS):
        sys.exit("KART.HWL lists only %d song packs" % len(howl.song_offsets))
    # the jobs: song 0 of each level 0..24; songs 1 and 2 (Aku Aku, Uka Uka) once per distinct version (same notes and instruments)
    todo, seen = [], {}
    for level, lname in enumerate(ctr_cseq.LEVELS):
        todo.append(("ctr_level_" + lname, level, 0))
        pack = ctr_cseq.CseqPack(hwl, howl.song_offsets[level])
        for sid, base in ((1, "ctr_aku_aku_"), (2, "ctr_uka_uka_")):
            song = pack.songs[sid]
            notes, _, _ = ctr_cseq.sequence(song, pack, sid)
            used = sorted({(n.kind, n.spu, n.ad, n.sr) for n in notes})       # what the song sounds like: its notes and the samples behind them
            key = hashlib.md5(repr((sid, song.bpm, [(n.start, n.end, n.index, n.kind) for n in notes], used)).encode()).hexdigest()
            if key not in seen:
                seen[key] = (base + lname, level, sid)
                todo.append(seen[key])
    tmp = tempfile.mkdtemp(prefix="ctr-music-", dir=os.path.dirname(pack_dir.rstrip("\\/")))
    sounds_dir = os.path.join(pack_dir, "assets", "ctrcraft", "sounds", "music")
    try:
        print("Rendering %d songs on %d processes ..." % (len(todo), jobs))
        with multiprocessing.Pool(jobs, initializer=_init_worker, initargs=(hwl,)) as pool:
            results = dict(pool.imap_unordered(_render_job, [(n, lv, sid, tmp) for n, lv, sid in todo]))
        level_peak = max(results["ctr_level_" + n]["peak"] for n in ctr_cseq.LEVELS)
        all_peak = max(r["peak"] for r in results.values())
        gain = min(MAX_GAIN, TARGET_PEAK / level_peak, 0.98 / all_peak)
        print("Loudest level song peaks at %.3f, loudest of all at %.3f before the makeup gain; gain %.2fx (%.1f dB) for every song" % (level_peak, all_peak, gain, 20 * np.log10(gain)))
        events = {"music.race": [], "music.aku_aku": [], "music.uka_uka": []}
        report = {"gain": round(gain, 4), "gain_db": round(20 * np.log10(gain), 2), "songs": {}}
        for name, level, sid in todo:
            audio = np.load(os.path.join(tmp, name + ".npy")) * gain
            info = results[name]
            spec = _spectrum_report(audio)
            exp_s = info["expected_seconds"]
            entry = {"level": ctr_cseq.LEVELS[level], "song": sid, "bpm": info["bpm"], "ticks_per_quarter": info["tpqn"], "sequences": info["seqs"],
                     "seconds": round(info["seconds"], 3), "expected_seconds_from_ticks": round(exp_s, 3), "nominal_seconds_from_bpm": round(info["nominal_seconds"], 3),
                     "note_ons_first_pass": info["note_ons_first_pass"], "note_ons_second_pass": info["note_ons_second_pass"], "beat_check": info["beat_check"],
                     "length_error_frames": round((info["seconds"] - exp_s) * ctr_cseq.FRAME_RATE, 2),
                     "loop_frames": list(info["loop_frames"]), "notes": info["notes_total"], "voices_dropped": info["dropped"], "peak": round(float(np.abs(audio).max()), 4),
                     "clipped_samples": int((np.abs(audio) >= 1.0).sum()), "reverb_opcodes": info["reverb_ops"], "pitch_bend_opcodes": info["pitch_ops"],
                     "volume_opcodes": info["volume_ops"], "unknown_opcodes": info["unknown_opcodes"], **spec}
            report["songs"][name] = entry
            oggenc.write_ogg(os.path.join(sounds_dir, name + ".ogg"), np.clip(audio, -1.0, 1.0), ctr_cseq.OUT_RATE, quality=0.5)
            ev = ("music.race", "music.aku_aku", "music.uka_uka")[sid]
            events[ev].append("ctrcraft:music/" + name)
            print("  %-34s %6.1f s (%.1f s from the ticks) %3d bpm  peak %.2f  rms %5.1f dBFS  %4d notes" % (name, entry["seconds"], exp_s, info["bpm"], entry["peak"], spec["rms_dbfs"], info["notes_total"]))
        report["pitch_check"] = _pitch_check(howl, hwl)
        print("Pitch check (one instrument at notes 57/60/69/72):", report["pitch_check"])
        return events, report
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--disc")
    ap.add_argument("--out", default=os.path.join(project, "local-assets", "ctr-music"))
    ap.add_argument("--install", action="append", help="also copy the pack into this resourcepacks folder; may be given more than once")
    ap.add_argument("--list", action="store_true", help="only list the music tracks (channel, file, sectors, seconds)")
    ap.add_argument("--no-level-music", action="store_true", help="skip rendering the sequenced level music (the jingles only)")
    ap.add_argument("--jobs", type=int, default=max(1, min(6, (os.cpu_count() or 2) - 1)), help="processes for rendering the level music")
    args = ap.parse_args()
    disc_path = args.disc or sounds.find_disc(project)
    if not disc_path or not os.path.exists(disc_path):
        sys.exit("No disc image found. Pass --disc \"path\\to\\CTR.bin\".")
    disc = sounds.Disc(disc_path)
    print("Disc:", disc_path, "| layout:", disc.layout)
    wanted = sorted({t for tracks, _ in EVENTS.values() for t in tracks})
    if args.list:
        wanted = list(range(xnf_track(disc, 0, 0)[3]))
    pack = args.out
    sounds_dir = os.path.join(pack, "assets", "ctrcraft", "sounds", "music")
    if not args.list:
        if os.path.isdir(pack):
            shutil.rmtree(pack)
        os.makedirs(sounds_dir)
    names = {}
    for t in wanted:
        channel, filenum, sectors, _ = xnf_track(disc, 0, t)
        pcm, rate, got = decode_track(disc, filenum, channel, sectors)
        secs = 0.0 if pcm is None else len(pcm) / rate
        print("music %2d: file S%02d channel %2d, %4d sectors (%d audio) %6.1f s @ %d Hz" % (t, filenum, channel, sectors, got, secs, rate))
        if args.list or pcm is None:
            continue
        name = "ctr_music_%02d" % t
        oggenc.write_ogg(os.path.join(sounds_dir, name + ".ogg"), np.clip(pcm / 32768.0, -1, 1), rate, quality=0.5)
        names[t] = name
    if args.list:
        return
    sj = {}
    for event, (tracks, _) in EVENTS.items():
        sj[event] = {"sounds": [{"name": "ctrcraft:music/" + names[t], "stream": True} for t in tracks if t in names]}
    if not args.no_level_music:
        events, report = render_level_music(disc, pack, max(1, args.jobs))
        for event, files in events.items():
            sj[event] = {"sounds": [{"name": n, "stream": True} for n in sorted(files)]}
        with open(os.path.join(pack, "music-report.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(report, f, indent=2)
    with open(os.path.join(pack, "assets", "ctrcraft", "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sj, f, indent=2)
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"pack": {"pack_format": 34, "description": "Craft Team Racing: the original race jingles from your own disc (local use only, do not share)"}}, f, indent=2)
    with open(os.path.join(pack, "README.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("Made by tools/extract_ctr_music.py from the owner's own copy of Crash Team Racing.\n"
                "The music belongs to Sony / Naughty Dog: keep this pack on this machine, do not share or upload it.\n"
                "Enable it in Options > Resource Packs, above the Craft Team Racing mod. It fills the mod's music events (race intro, race music, last lap, race end).\n"
                "The race music is rendered offline from the game's sequenced songs (tools/ctr_cseq.py): music-report.json has the checks of each song.\n")
    print("Pack written to", pack)
    for target in args.install or []:
        dst = os.path.join(target, "CTRCraft-OriginalMusic")
        if os.path.isdir(dst):
            shutil.rmtree(dst)
        shutil.copytree(pack, dst)
        print("  installed to", dst)


if __name__ == "__main__":
    main()
