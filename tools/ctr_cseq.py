"""
CTR's level music, rendered offline: a CSEQ parser, a port of the game's sequencer and an emulation of the PlayStation SPU voices (ADPCM + gaussian
interpolation + ADSR + the reverb unit), numpy only. Used by tools/extract_ctr_music.py; nothing here reads or writes assets by itself.

The original's race music is not recorded: each level has a CSEQ song pack in SOUNDS/KART.HWL (a small MIDI-like format: tempo, ticks per quarter note and a
few note streams) played at 60 Hz by the game, through the instrument/drum samples of the sound bank, on the SPU's 24 voices. Everything below follows the decomp
(ctr-native, game/HOWL/*.c and platform/native_audio.c), function by function; each function names its C source. docs/MUSIC.md lists what is approximate.

Layout of a song pack (howl_ParseCseqHeader, HOWL_Load.c): CseqHeader {int songSize; u8 numLongSamples; u8 numShortSamples; u16 numSongs}, then numLongSamples *
SampleInstrument (12 bytes), numShortSamples * SampleDrums (8 bytes), numSongs * u16 song offsets, then (aligned up to 4) the song data. A song
(CseqSongHeader, SongPool_Start in HOWL_SongPool.c): {u8 unk; u8 numSeqs; s16 bpm; s16 tpqn; u16 seqOffsets[numSeqs]}, then (aligned up to 4) one SongNoteHeader
{u8 flags (1 = drum track); u8 unk} and the event stream per sequence. An event is a variable length delta time (7 bits a byte, top bit = more) then an opcode
byte and its parameters (the sizes are data.opcodeOffset).
"""
import struct

import numpy as np

import ctr_spu_tables as T

# ---------------------------------------------------------------------------------------------------------------- constants

FRAME_RATE = 60000.0 / 1001.0         # the game runs the sequencer from the NTSC vertical sync callback (MainDrawCb.c -> howl_PlayAudio_Update)
OUT_RATE = 44100                      # NATIVE_AUDIO_SAMPLE_RATE: the SPU's rate
SPF = OUT_RATE / FRAME_RATE           # output samples per game frame
NUM_VOICES = 24                       # NUM_SFX_CHANNELS
VOL_MUSIC = 175                       # howl_InitGlobals (HOWL_Load.c): sdata->vol_Music
MASTER = 0x3FFF                       # howl_InitGlobals: SpuSetCommonMasterVolume(0x3fff, 0x3fff)
OPCODE_SIZE = (1, 2, 2, 1, 2, 3, 2, 2, 2, 2, 2)   # data.opcodeOffset
DISTORT_NONE = 0x80                   # HOWL_SFX_DISTORTION_NONE

LEVELS = ("dingo_canyon", "dragon_mines", "blizzard_bluff", "crash_cove", "tiger_temple", "papu_pyramid", "roo_tubes", "hot_air_skyway", "sewer_speedway",
          "mystery_caves", "cortex_castle", "n_gin_labs", "polar_pass", "oxide_station", "coco_park", "tiny_arena", "slide_coliseum", "turbo_track",
          "nitro_court", "rampage_ruins", "parking_lot", "skull_rock", "the_north_bowl", "rocky_road", "lab_basement")   # namespace_Level.h, levels 0 to 24


# ---------------------------------------------------------------------------------------------------------------- parser


class Seq:
    """One note stream of a song."""

    def __init__(self, data, start, drums, unk):
        self.data, self.start, self.drums, self.unk = data, start, drums, unk


class Song:
    def __init__(self, bpm, tpqn, seqs):
        self.bpm, self.tpqn, self.seqs = bpm, tpqn, seqs


class CseqPack:
    """The song pack of one HOWL sector offset (howl_LoadSong + howl_ParseCseqHeader)."""

    def __init__(self, hwl, sector):
        o = sector * 2048
        size, n_long, n_short, n_songs = struct.unpack_from("<IBBH", hwl, o)
        a = o + 8
        self.instruments = [dict(zip(("one", "volume", "zero", "base_pitch", "spu", "ad", "sr"), struct.unpack_from("<BBhHhHH", hwl, a + i * 12))) for i in range(n_long)]
        a += 12 * n_long
        self.drums = [dict(zip(("one", "volume", "pitch", "spu", "zero"), struct.unpack_from("<BBhhh", hwl, a + i * 8))) for i in range(n_short)]
        a += 8 * n_short
        offsets = struct.unpack_from("<%dH" % n_songs, hwl, a)
        a += 2 * n_songs
        self.buf = hwl[o: o + max(size, a - o + 4)]
        base = (a - o + 3) & ~3                         # the song data is aligned up to 4
        self.songs = []
        for off in offsets:
            c = base + off
            _unk, n_seq, bpm, tpqn = struct.unpack_from("<BBhh", self.buf, c)
            seq_offs = struct.unpack_from("<%dH" % n_seq, self.buf, c + 6)
            first = (c + 6 + 2 * n_seq + 3) & ~3        # SongPool_Start: the first note header, aligned up to 4
            seqs = []
            for so in seq_offs:
                h = first + so
                seqs.append(Seq(self.buf, h + 2, bool(self.buf[h] & 1), self.buf[h + 1]))
            self.songs.append(Song(bpm, tpqn, seqs))


def get_next_note(buf, pos):
    """howl_GetNextNote (HOWL_Load.c): the delta time at pos and the position of the opcode that follows it."""
    n = buf[pos]
    pos += 1
    if n & 0x80:
        n &= 0x7F
        while True:
            b = buf[pos]
            pos += 1
            n = ((n << 7) + (b & 0x7F)) & 0xFFFFFFFF
            if not b & 0x80:
                break
    return n, pos


# ---------------------------------------------------------------------------------------------------------------- sequencer


def instrument_pitch(base_pitch, pitch_index, distort):
    """howl_InstrumentPitch (HOWL_Load.c): the SPU pitch register of a note (0x1000 = the sample's own rate)."""
    note_offset = (distort >> 6) - 2
    i = min(max(pitch_index + note_offset, 0), len(T.NOTE_FREQUENCY) - 1)
    freq = (T.NOTE_FREQUENCY[i] * base_pitch) >> 12
    idx = distort & 0x3F
    if idx:
        freq = ((freq & 0xFFFF) * (T.DISTORT_MUSIC[idx] + 0x100000)) >> 20
    return freq & 0xFFFF


def drum_pitch(pitch, distort):
    """howl_InitChannelAttr_Music / cseq_opcode0a (HOWL_CseqOpcode.c), the drum branch."""
    pitch &= 0xFFFF
    if distort != DISTORT_NONE:
        return (pitch * T.DISTORT_OTHER_FX[distort]) >> 16
    return pitch


def channel_volume(volume, lr):
    """Channel_SetVolume (HOWL_Channel.c), stereo: (left, right) of the SPU voice volume registers."""
    if volume >= 0x4000:
        volume = 0x3FFF
    return (volume * T.VOLUME_LR[0xFF - lr]) >> 8, (volume * T.VOLUME_LR[lr]) >> 8


class Note:
    """One voice's use: from the key on to the key off (None = never), with the changes the song makes while it sounds."""

    def __init__(self, voice, start, kind, index, pitch, vol, reverb, ad, sr, spu):
        self.voice, self.start, self.end = voice, start, None
        self.kind, self.index = kind, index          # 'drum' / 'inst' and the pitch index (the note number) or drum number
        self.ad, self.sr, self.spu = ad, sr, spu
        self.vol = [(start, vol)]                     # (frame, (left, right))
        self.pitch = [(start, pitch)]
        self.reverb = [(start, reverb)]


def sequence(song, pack, song_id, max_frames=None, passes=2):
    """The game's sequencer run offline: SongPool_Start (HOWL_SongPool.c) and Channel_ParseSongToChannels (HOWL_Channel.c) with the opcodes of HOWL_CseqOpcode.c.
    Returns (notes, loop_frames, info): the voice uses, the frame at which each sequence went back to its start (first time), and counters of what was
    dropped. Runs until every sequence has looped `passes` times, or max_frames."""
    # SongPool_CalculateTempo(60, tpqn, bpm)
    tempo = (((song.bpm * song.tpqn) // 60) << 16) // 60
    song_vol = 0xFF if 0 < song_id < 3 else 0xBE       # SongPool_Start: songs 1 and 2 (the masks) are at full volume
    seqs = []
    for i, s in enumerate(song.seqs):
        n, pos = get_next_note(s.data, s.start)
        seqs.append({"s": s, "i": i, "first": s.start, "cur": pos, "len": n, "elapsed": 0, "inst": 0, "reverb": 0, "vol": 255, "distort": 0x80, "lr": 0x80,
                     "loops": [], "restart": False})
    free = list(range(NUM_VOICES))                     # the channel free list (LIST_AddBack on release, first one is allocated)
    taken = {}                                         # voice -> (seq index, pitch index/drum, vol, note)
    notes = []
    info = {"dropped": 0, "pitch_clamped": 0, "unknown_opcodes": {}, "reverb_ops": 0, "pitch_ops": 0, "volume_ops": 0}
    total = 0
    frame = 0
    limit = max_frames or 60 * 60 * 20

    def update_volume(sq, f):
        # cseq_opcode_from06and07: the channels of this sequence get volume (sampleVol * channel volume) >> 18 and the sequence's pan
        for v, (si, idx, cvol, note) in taken.items():
            if si == sq["i"]:
                sample_vol = VOL_MUSIC * song_vol * sq["vol"]
                note.vol.append((f, channel_volume((sample_vol * cvol) >> 18, sq["lr"])))

    while frame < limit and not all(len(sq["loops"]) >= passes for sq in seqs):
        total += tempo                                     # Channel_ParseSongToChannels: the 16.16 tick timer
        ticks = total >> 16
        total &= 0xFFFF
        for sq in seqs:
            sq["elapsed"] += ticks
            guard = 0
            while sq["len"] <= sq["elapsed"]:
                guard += 1
                if guard > 100000:
                    raise RuntimeError("sequence does not advance")
                sq["elapsed"] -= sq["len"]
                buf = sq["s"].data
                cur = sq["cur"]
                op = buf[cur]
                if op < 11:
                    if op == 1:                            # cseq_opcode01_noteoff
                        for v in [v for v, t in taken.items() if t[0] == sq["i"] and t[1] == buf[cur + 1]]:
                            taken[v][3].end = frame
                            del taken[v]
                            free.append(v)
                    elif op == 3:                          # cseq_opcode03: the song loops (boolLoopAtEnd), the sequence restarts after this event
                        sq["restart"] = True
                        sq["loops"].append(frame)
                    elif op == 5:                          # cseq_opcode05_noteon
                        idx, cvol = buf[cur + 1], buf[cur + 2]
                        if sq["vol"] and song_vol:
                            if sq["s"].drums:
                                smp = pack.drums[idx]
                                pitch, ad, sr, spu = drum_pitch(smp["pitch"], sq["distort"]), 0x80FF, 0x1FC2, smp["spu"]
                            else:
                                smp = pack.instruments[sq["inst"]]
                                pitch, ad, sr, spu = instrument_pitch(smp["base_pitch"], idx, sq["distort"]), smp["ad"], smp["sr"], smp["spu"]
                            mix = (VOL_MUSIC * song_vol * sq["vol"]) >> 10
                            vol = channel_volume((mix * smp["volume"] * cvol) >> 15, sq["lr"])
                            if free:
                                v = free.pop(0)
                                note = Note(v, frame, "drum" if sq["s"].drums else "inst", idx, pitch, vol, sq["reverb"], ad, sr, spu)
                                taken[v] = (sq["i"], idx, cvol, note)
                                notes.append(note)
                            else:
                                info["dropped"] += 1
                    elif op == 6:                          # cseq_opcode06: sequence volume
                        sq["vol"] = buf[cur + 1]
                        info["volume_ops"] += 1
                        update_volume(sq, frame)
                    elif op == 7:                          # cseq_opcode07: pan
                        sq["lr"] = buf[cur + 1]
                        info["volume_ops"] += 1
                        update_volume(sq, frame)
                    elif op == 8:                          # cseq_opcode08: reverb of the voices sounding now (seq->reverb is not touched)
                        info["reverb_ops"] += 1
                        for v, (si, idx, cvol, note) in taken.items():
                            if si == sq["i"]:
                                note.reverb.append((frame, buf[cur + 1]))
                    elif op == 9:                          # cseq_opcode09: instrument
                        sq["inst"] = buf[cur + 1]
                    elif op == 10:                         # cseq_opcode0a: pitch bend, applied to the voices sounding now and to the next notes
                        sq["distort"] = buf[cur + 1]
                        info["pitch_ops"] += 1
                        for v, (si, idx, cvol, note) in taken.items():
                            if si == sq["i"]:
                                if sq["s"].drums:
                                    p = drum_pitch(pack.drums[idx]["pitch"], sq["distort"])
                                else:
                                    p = instrument_pitch(pack.instruments[sq["inst"]]["base_pitch"], idx, sq["distort"])
                                note.pitch.append((frame, p))
                    if sq["restart"]:
                        sq["restart"] = False
                        pos = sq["first"]
                    else:
                        pos = cur + OPCODE_SIZE[op]
                    sq["len"], sq["cur"] = get_next_note(buf, pos)
                else:
                    info["unknown_opcodes"][op] = info["unknown_opcodes"].get(op, 0) + 1
                    sq["len"], sq["cur"] = get_next_note(buf, cur + 1)
        frame += 1
    info["frames"] = frame
    return notes, [sq["loops"] for sq in seqs], info


# ---------------------------------------------------------------------------------------------------------------- SPU voices
# platform/native_audio.c emulates the PlayStation's sound chip; the pieces a music voice needs are ported here (ADPCM decode: extract_ctr_sounds.decode_adpcm).

ADSR_MAX = 0x7FFF
ATTACK, DECAY, SUSTAIN, RELEASE = range(4)     # NATIVE_AUDIO_ADSR_*


def adsr_fields(ad, sr):
    """Channel_UpdateChannels (HOWL_Channel.c): the ad/sr words of a sample as SpuSetVoiceADSRAttr takes them. Modes: 1 linear inc, 3 linear dec, 5 exp inc, 7 exp dec."""
    return {"ar": (ad >> 8) & 127, "dr": (ad >> 4) & 15, "sl": ad & 15, "sr": (sr >> 6) & 127, "rr": sr & 31,
            "a_mode": 5 if ad >> 15 else 1,
            "s_mode": (7 if (sr >> 14) & 1 else 5) if sr >> 15 else (3 if (sr >> 14) & 1 else 1),
            "r_mode": 7 if (sr >> 5) & 1 else 3}


def _exp(mode):        # NativeAudio_AdsrModeIsExponential
    return mode in (5, 6, 7)


def _dec(mode):        # NativeAudio_AdsrModeIsDecreasing
    return mode in (3, 4, 7)


def _neg(mode):        # NativeAudio_AdsrModeIsPhaseNegative
    return mode in (2, 4, 6)


class _Voice:
    """The envelope state of one SPU voice (struct NativeAudioVoice: adsrLevel, adsrCounter, adsrPhase)."""

    def __init__(self, p):
        self.p, self.level, self.counter, self.phase = p, 0, 0, ATTACK


def _step_params(v, shift, step, exp, dec, neg):
    """The first half of NativeAudio_AdsrRunEnvelopeStep: (level change, counter increment) for the voice's current level."""
    a = 7 - step
    if dec != neg:
        a = ~a
    if shift < 11:
        a *= 1 << (11 - shift)
    inc = 0x8000
    if shift > 11:
        s = shift - 11
        inc = (0x8000 >> s) if s < 31 else 0
    if exp and not dec and v.level > 0x6000:
        if shift < 10:
            a >>= 2
        elif shift >= 11:
            inc >>= 2
        else:
            a >>= 1
            inc >>= 1
    elif exp and dec:
        a = int(a * v.level / 0x8000)
        if v.phase == RELEASE and a == 0 and v.level > 0:
            a = -1
    return a, max(inc, 1)


def _phase_rates(v):
    p = v.p
    if v.phase == ATTACK:
        shift, step = (p["ar"] >> 2) & 0x1F, p["ar"] & 3
        return shift, step, _exp(p["a_mode"]), False, _neg(p["a_mode"]), ((step | (shift << 2)) == 0x7F)
    if v.phase == DECAY:
        return p["dr"] & 15, 0, True, True, False, False
    if v.phase == SUSTAIN:
        shift, step = (p["sr"] >> 2) & 0x1F, p["sr"] & 3
        return shift, step, _exp(p["s_mode"]), _dec(p["s_mode"]), _neg(p["s_mode"]), ((step | (shift << 2)) == 0x7F)
    shift = p["rr"] & 0x1F
    return shift, 0, _exp(p["r_mode"]), True, _neg(p["r_mode"]), shift == 0x1F


def _apply_step(v, a, dec, neg):
    v.level += a
    if not dec:
        v.level = max(-0x8000, min(ADSR_MAX, v.level))
    elif neg:
        v.level = max(-0x8000, min(0, v.level))
    elif v.level < 0:
        v.level = 0


def envelope(p, release_at, tail_cap=OUT_RATE // 2):
    """NativeAudio_AdsrAdvance / NativeAudio_AdsrRunEnvelopeStep ported as an event-driven loop (the level only changes when the step counter overflows, so the
    samples between are filled at once). Returns the level (0..ADSR_MAX) the voice has at each output sample, before that sample's advance, as the mixer uses it:
    release_at samples of the held note, then the release until the level reaches 0 (or tail_cap samples)."""
    v = _Voice(p)
    out = []
    count = 0
    held_done = False
    target = min((p["sl"] + 1) * 0x800, ADSR_MAX)

    def emit(level, k):
        nonlocal count
        if k > 0:
            out.append(np.full(k, level, dtype=np.int32))
            count += k

    while True:
        limit = count + tail_cap if held_done else release_at
        remaining = limit - count
        if remaining <= 0:
            if held_done:
                break
            held_done = True                   # key off: SpuSetKey(0, ...) -> NativeAudio_AdsrKeyOff
            v.phase, v.counter = RELEASE, 0
            continue
        if v.phase == DECAY and v.level <= target:
            emit(v.level, 1)
            v.level, v.counter, v.phase = target, 0, SUSTAIN
            continue
        if v.phase == RELEASE and v.level <= 0:
            break                              # NativeAudio_AdsrForceOff: the voice is silent from here
        shift, step, exp, dec, neg, ones = _phase_rates(v)
        if ones:                               # the "no change" rate: the level stays (a release at 0x1F would never end; the cap cuts it)
            emit(v.level, remaining)
            continue
        a, inc = _step_params(v, shift, step, exp, dec, neg)
        s = -(-(0x8000 - v.counter) // inc)
        if s > remaining:
            emit(v.level, remaining)
            v.counter += remaining * inc
            continue
        emit(v.level, s)
        v.counter = v.counter + s * inc - 0x8000
        before = v.level
        _apply_step(v, a, dec, neg)
        if v.phase == ATTACK and v.level >= ADSR_MAX:
            v.level, v.counter, v.phase = ADSR_MAX, 0, DECAY
        elif v.phase == DECAY and v.level <= target:
            v.level, v.counter, v.phase = target, 0, SUSTAIN
        elif v.phase in (DECAY, SUSTAIN) and v.level == before:
            emit(v.level, limit - count)       # a step that rounds to nothing never changes the level again
    return np.concatenate(out) if out else np.zeros(0, dtype=np.int32)


class SampleBank:
    """Decoded SPU samples by index (extract_ctr_sounds.Howl.samples): the PCM and, when the sample loops, the loop region and its steady-state body."""

    def __init__(self, howl, decode_adpcm, steady_loop):
        self.howl, self.decode, self.steady, self.cache = howl, decode_adpcm, steady_loop, {}

    def get(self, idx):
        s = self.cache.get(idx)
        if s is None:
            data = self.howl.samples[idx]
            pcm, loop = self.decode(data)
            pcm = np.asarray(pcm, dtype=np.float32)
            body = None
            if loop and loop[1] - loop[0] > 28:
                body = np.asarray(self.steady(data), dtype=np.float32)
                loop = (loop[0], loop[0] + len(body))
            else:
                loop = None
            s = self.cache[idx] = (pcm, loop, body)
        return s


_GAUSS = np.asarray(T.GAUSS, dtype=np.float32)


def interpolate(smp, counter):
    """NativeAudio_InterpolateVoiceSample: the SPU's 4-point gaussian interpolation, at the 12-bit fixed-point positions in counter (int64).
    Returns (samples, n): n < len(counter) when a non-looping sample ends (NativeAudio_VoiceStreamAdvanceBlock fails and the voice is forced off)."""
    pcm, loop, body = smp
    idx = counter >> 12
    if loop is None:
        n = int(np.searchsorted(idx, len(pcm), side="left"))
        idx, counter = idx[:n], counter[:n]
    else:
        n = len(counter)
    g = ((counter >> 4) & 0xFF).astype(np.int64)

    def tap(i):
        if loop is None:
            return np.where(i >= 0, pcm[np.clip(i, 0, max(len(pcm) - 1, 0))], 0.0)
        le = loop[1]
        i2 = np.clip(i, 0, None)
        inside = pcm[np.clip(i2, 0, len(pcm) - 1)]
        wrapped = body[(i2 - le) % len(body)]
        return np.where(i < 0, 0.0, np.where(i2 < le, inside, wrapped))

    out = (_GAUSS[0xFF - g] * tap(idx - 3) + _GAUSS[0x1FF - g] * tap(idx - 2) + _GAUSS[0x100 + g] * tap(idx - 1) + _GAUSS[g] * tap(idx)) / 32768.0
    return out, n


def _segments(timeline, start, length):
    """A piecewise-constant change list [(frame, value)] as (samples, value) runs over `length` samples starting at output sample `start`."""
    pts = sorted(((max(int(round(f * SPF)) - start, 0), v) for f, v in timeline), key=lambda p: p[0])
    out = []
    for i, (pos, val) in enumerate(pts):
        nxt = min(pts[i + 1][0] if i + 1 < len(pts) else length, length)
        if nxt > pos:
            out.append((nxt - pos, val))
    return out


def render_voice(note, bank, env_cache, start, gate, cap):
    """What one voice sounds: (left, right, n), float32 contributions in the SPU mixer's scale (1.0 = full scale), or None."""
    key = (note.ad, note.sr, gate)
    env = env_cache.get(key)
    if env is None:
        env = envelope(adsr_fields(note.ad, note.sr), gate)
        if len(env_cache) < 8192:
            env_cache[key] = env
    n = min(len(env), cap)
    if n <= 0:
        return None
    env = env[:n]
    # the position counter advances by the pitch register (clamped to 0x4000, NATIVE_AUDIO_PITCH_STEP_MAX) every output sample
    segs = _segments(note.pitch, start, n)
    if len(segs) == 1:
        step = np.full(n, min(segs[0][1], 0x4000), dtype=np.int64)
    else:
        step = np.concatenate([np.full(k, min(v, 0x4000), dtype=np.int64) for k, v in segs])
    counter = np.concatenate([[0], np.cumsum(step[:-1])]) if n > 1 else np.zeros(1, dtype=np.int64)
    sample, m = interpolate(bank.get(note.spu), counter)
    if m < n:
        n, env, sample = m, env[:m], sample[:m]
    if n <= 0:
        return None
    s = sample * (env.astype(np.float32) / ADSR_MAX)          # NativeAudio_ApplyAdsrEnvelope
    vsegs = _segments(note.vol, start, n)
    if len(vsegs) == 1:
        vol_l, vol_r = float(vsegs[0][1][0]), float(vsegs[0][1][1])
    else:
        vol_l = np.concatenate([np.full(k, v[0], dtype=np.float32) for k, v in vsegs])
        vol_r = np.concatenate([np.full(k, v[1], dtype=np.float32) for k, v in vsegs])
    # NativeAudio_ApplyVolume: sample * voice volume * master volume / 0x8000^2; the samples above are int16-scaled, so one more / 0x8000 gives full scale 1.0
    k = (MASTER / 32768.0) / 32768.0 / 32768.0
    return s * (vol_l * k), s * (vol_r * k), n


def render_song(song, pack, song_id, bank):
    """Sequence the song and mix its voices. The song loops at the end of its sequences (boolLoopAtEnd), so the result is the *second* pass: from the frame the
    sequences first restart to the frame they restart the second time. It starts with the tails of the last notes of the first pass and ends where the next pass
    begins, so playing the file again and again is the game's endless loop. Returns (float32 array (n, 2) at OUT_RATE, report dict)."""
    notes, loops, info = sequence(song, pack, song_id)
    first = max(l[0] for l in loops if l)
    second = max(l[1] for l in loops if len(l) > 1)
    s0, s1 = int(round(first * SPF)), int(round(second * SPF))
    total = s1
    left = np.zeros(total + 8, dtype=np.float32)
    right = np.zeros(total + 8, dtype=np.float32)
    env_cache = {}
    cut = {}                                    # a voice is cut by the next note on it (key on restarts the voice)
    last = {}
    for nt in notes:
        if nt.voice in last:
            cut[id(last[nt.voice])] = nt.start
        last[nt.voice] = nt
    rendered = 0
    for nt in notes:
        start = int(round(nt.start * SPF))
        if start >= total:
            continue
        end_frame = nt.end if nt.end is not None else second
        gate = max(int(round(end_frame * SPF)) - start, 0)
        cap = total - start
        if id(nt) in cut:
            cap = min(cap, int(round(cut[id(nt)] * SPF)) - start)
        if cap <= 0 or (nt.end is not None and start + gate + 64 < s0):       # over before the window starts (the releases here are a few samples long)
            continue
        r = render_voice(nt, bank, env_cache, start, gate, cap)
        if r is None:
            continue
        l, rr, n = r
        left[start:start + n] += l
        right[start:start + n] += rr
        rendered += 1
    out = np.stack([left[s0:s1], right[s0:s1]], axis=1)
    info.update({"note_ons_first_pass": sum(1 for n in notes if n.start < first), "note_ons_second_pass": sum(1 for n in notes if first <= n.start < second),
                 "tempo_per_frame": ((song.bpm * song.tpqn) // 60 << 16) // 60,
                 "loop_frames": (first, second), "seconds": (s1 - s0) / OUT_RATE, "notes_rendered": rendered, "notes_total": len(notes),
                 "bpm": song.bpm, "tpqn": song.tpqn, "seqs": len(song.seqs)})
    return out, info
