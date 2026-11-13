"""
Extract CTR's original kart sounds from YOUR OWN copy of the game into a LOCAL resource pack.

  python tools/extract_ctr_sounds.py                      (finds the disc image in the project folder)
  python tools/extract_ctr_sounds.py --disc "D:/CTR.bin" --install "F:/ATLauncher/instances/CTRCraft/resourcepacks"

What it does: reads the raw disc image (.bin / MODE2/2352, or a plain .iso), finds SOUNDS\\KART.HWL (CTR's HOWL sound bank),
decodes the PS-ADPCM samples the kart events use, and writes them as mono Ogg Vorbis into a resource pack folder
(default: local-assets/ctr-sounds/) with the same names as the mod's own placeholder sounds, so the pack simply replaces them.
Nothing is sent anywhere and nothing is read from the disc except those files. The result is for YOUR machine: it is
git-ignored, never put it in the jar, never share the pack (the recordings are Sony / Naughty Dog's).

Format knowledge: CTR-tools/ctr-native (include/namespace_Howl.h) as read by world-of-kartcraft's ctr-assets (docs/ctr-assets.md,
GPL-3.0). Which sound belongs to which event is in MAPPING below, with the decomp site it comes from.

Needs numpy and Krita's libogg/libvorbis DLLs for the encoder (tools/oggenc.py; KRITA_BIN if Krita is elsewhere).
"""
import argparse
import glob
import json
import os
import shutil
import struct
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oggenc  # noqa: E402

RAW = 2352
DATA = 2048

# ---------------------------------------------------------------------------------------------------------------- disc


class Disc:
    """A raw (2352-byte, Mode 2) or cooked (2048-byte) disc image, read-only."""

    def __init__(self, path):
        self.f = open(path, "rb")
        self.f.seek(16 * RAW)
        raw = self.f.read(64)
        self.f.seek(16 * DATA)
        cooked = self.f.read(64)
        if raw[:12] == bytes([0] + [255] * 10 + [0]) and raw[24 + 1: 24 + 6] == b"CD001":
            self.layout, self.off = "raw", 24            # Mode 2 Form 1
        elif raw[:12] == bytes([0] + [255] * 10 + [0]) and raw[16 + 1: 16 + 6] == b"CD001":
            self.layout, self.off = "raw", 16            # Mode 1
        elif cooked[1:6] == b"CD001":
            self.layout, self.off = "cooked", 0
        else:
            raise SystemExit("%s: no ISO 9660 volume found (is it a PlayStation disc image?)" % path)

    def sectors(self, lba, count):
        out = bytearray()
        for i in range(count):
            if self.layout == "cooked":
                self.f.seek((lba + i) * DATA)
                out += self.f.read(DATA)
            else:
                self.f.seek((lba + i) * RAW + self.off)
                out += self.f.read(DATA)
        return bytes(out)

    def read(self, lba, size):
        return self.sectors(lba, -(-size // DATA))[:size]

    def find(self, path):
        pvd = self.sectors(16, 1)
        if pvd[1:6] != b"CD001":
            raise SystemExit("primary volume descriptor not found")
        lba, size = struct.unpack_from("<I", pvd, 156 + 2)[0], struct.unpack_from("<I", pvd, 156 + 10)[0]
        for part in [p for p in path.replace("\\", "/").split("/") if p]:
            data = self.read(lba, size)
            found = None
            pos = 0
            while pos < len(data):
                n = data[pos]
                if n == 0:
                    pos = (pos // DATA + 1) * DATA
                    continue
                rec = data[pos: pos + n]
                name = rec[33: 33 + rec[32]].decode("latin-1")
                if name not in ("\x00", "\x01") and name.split(";")[0].rstrip(".").upper() == part.upper():
                    found = (struct.unpack_from("<I", rec, 2)[0], struct.unpack_from("<I", rec, 10)[0])
                    break
                pos += n
            if not found:
                raise SystemExit("%s: %s not found on the disc" % (path, part))
            lba, size = found
        return lba, size

    def game_id(self):
        try:
            lba, size = self.find("SYSTEM.CNF")
            text = self.read(lba, size).decode("latin-1")
            for line in text.splitlines():
                if line.strip().upper().startswith("BOOT"):
                    return line.split("\\")[-1].split(":")[-1].split(";")[0].strip()
        except SystemExit:
            pass
        return "?"


# ---------------------------------------------------------------------------------------------------------------- HOWL


class Howl:
    """SOUNDS\\KART.HWL: tables of instruments and the PS-ADPCM sample banks (layouts: ctr-native namespace_Howl.h)."""

    def __init__(self, d):
        if d[:4] != b"HOWL":
            raise SystemExit("KART.HWL is not a HOWL file")
        u32 = lambda at: struct.unpack_from("<I", d, at)[0]
        u16 = lambda at: struct.unpack_from("<H", d, at)[0]
        n_spu, n_other, n_engine, n_banks = u32(0x10), u32(0x14), u32(0x18), u32(0x1C)
        if max(n_spu, n_other, n_engine) > 4096 or n_banks > 1024:
            raise SystemExit("implausible HOWL header")
        at = 0x28
        sizes = []
        for _ in range(n_spu):
            sizes.append(u16(at + 2) * 8)
            at += 4
        self.other, self.engine = [], []
        for table, count, spu_off in ((self.other, n_other, 4), (self.engine, n_engine, 6)):
            for _ in range(count):
                table.append({"flags": d[at], "volume": d[at + 1], "pitch": u16(at + 2), "spu": u16(at + spu_off)})
                at += 8
        # the offsets (in 2048-byte sectors) of the CSEQ song packs follow the bank offsets: howl_ParseHeader in HOWL_Load.c (howl_songOffsets)
        n_songs = u32(0x20)
        self.data = d
        self.spu_sizes = sizes
        self.song_offsets = [u16(at + n_banks * 2 + i * 2) for i in range(n_songs)] if n_songs < 4096 else []
        self.samples = {}
        for b in range(n_banks):
            start = u16(at + b * 2) * 2048
            count = u16(start)
            pos = -(-(start + 2 + count * 2) // 2048) * 2048
            for i in range(count):
                idx = u16(start + 2 + i * 2)
                size = sizes[idx]
                if idx not in self.samples:
                    self.samples[idx] = d[pos: pos + size]
                pos += size


F0 = (0, 60, 115, 98, 122)
F1 = (0, 0, -52, -55, -60)


def decode_block(block, hist, out):
    shift = block[0] & 0x0F
    filt = min(block[0] >> 4, 4)
    if shift > 12:
        shift = 9
    for i in range(28):
        byte = block[2 + i // 2]
        nib = (byte & 0x0F) if i % 2 == 0 else (byte >> 4)
        s = ((nib << 12) - 65536 if (nib << 12) & 0x8000 else (nib << 12)) >> shift
        v = s + ((hist[0] * F0[filt] + hist[1] * F1[filt] + 32) >> 6)
        v = max(-32768, min(32767, v))
        hist[1], hist[0] = hist[0], v
        out.append(v)


def decode_adpcm(data):
    """PCM and the loop region (start, end) the block flags give, or None."""
    pcm, hist, loop_start, looping = [], [0, 0], None, None
    for i in range(0, len(data) - 15, 16):
        block = data[i: i + 16]
        flags = block[1]
        if flags & 4 and loop_start is None:
            loop_start = len(pcm)
        decode_block(block, hist, pcm)
        if flags & 1:
            if flags & 2:
                looping = (loop_start or 0, len(pcm))
            break
    return pcm, looping


def steady_loop(data):
    """A looped sound's body as the SPU plays it on every pass after the first, so repeating it is seamless."""
    pcm, looping = decode_adpcm(data)
    if not looping:
        return None
    start, end = looping
    if end <= start + 28:
        return None
    hist = [pcm[end - 1], pcm[end - 2]]
    out = []
    for blk in range(start // 28, start // 28 + (end - start) // 28):
        decode_block(data[blk * 16: blk * 16 + 16], hist, out)
    return out


# ---------------------------------------------------------------------------------------------------------------- mapping

# our sound event -> (table, id, loop?, gain 0..1, trim seconds or None, decomp site)
MAPPING = {
    "engine_loop": ("engine", 0, True, 1.0, None, "HOWL_Engine.c EngineSound_Player: EngineFX[engine*4 + driver], BALANCED slot 0"),
    "skid_loop": ("other", 17, True, 1.0, None, "VehEmitter.c: terrain skidSound = 17 (asphalt)"),
    "turbo_loop": ("other", 0x0E, True, 1.0, None, "VehTurbo.c: TURBO_AUDIO_SFX_ID = 0xE, every frame while the reserves burn"),
    "hop": ("other", 0x08, False, 1.0, None, "VehPhysGeneral.c: VEH_PHYS_JUMP_NORMAL_SFX = 8"),
    "land": ("other", 0x07, False, 1.0, None, "COLL.c: first ground contact, OtherFX 7"),
    "boost": ("other", 0x0D, False, 1.0, None, "VehFire.c VehFire_Audio: VEH_FIRE_AUDIO_SFX = 0xD (the game bends its pitch per boost)"),
    "hang_time": ("other", 0x0D, False, 1.0, None, "hang time boosts through the same VehFire_Increment, so the same sound"),
    "meter_full": ("other", 0x0F, False, 1.0, None, "VehPhysProc.c: VEH_PHYS_PROC_DRIFT_METER_FULL_FX = 0xF"),
    "wall_hit": ("other", 0x14, False, 1.0, 0.6, "VehEmitter.c: wall rub loop OtherFX 0x14, first 0.6 s"),
    "slam": ("other", 0x06, False, 1.0, None, "COLL.c: hard wall impact, OtherFX 6"),
    # race, pickups and kart bumps (all through the same OtherFX bank)
    "light_count": ("other", 0x45, False, 1.0, None, "DotLights.c: DOT_LIGHT_FX_COUNTDOWN, each red lamp"),
    "light_green": ("other", 0x46, False, 1.0, None, "DotLights.c: DOT_LIGHT_FX_GREEN, the green lamp at GO"),
    "fruit_pickup": ("other", 0x43, False, 1.0, None, "RB_Fruit.c: PlaySound3D(0x43) when a Wumpa fruit is taken"),
    "fruit_arrive": ("other", 0x42, False, 1.0, None, "UI_RenderFrame.c: OtherFX_Play(0x42, 1) each time a flying Wumpa fruit reaches the HUD"),
    "glass_shatter": ("other", 0x3F, False, 1.0, None, "RB_GenericMine.c: PlaySound3D(0x3f), a Nitro crate or a flask bursting (a TNT crate has its own, 0x3d)"),
    "mine_drop": ("other", 0x52, False, 1.0, None, "VehPickupItem.c SOUND_MINE_DROP: a TNT or Nitro crate or a flask put down behind the kart, and a flask thrown"),
    "tnt_hit": ("other", 0x50, False, 1.0, None, "RB_GenericMine.c: PlaySound3D(0x50), the 'bounce' when a kart runs into a TNT crate and it is thrown onto its head"),
    "tnt_land": ("other", 0x51, False, 1.0, None, "RB_TNT.c: PlaySound3D(0x51) when the TNT crate lands on a kart's head"),
    "tnt_tick": ("other", 0x3E, False, 1.0, None, "RB_TNT.c: PlaySound3D(0x3e), the honk at frames 0, 20, 40, 60, 70 and 80 of the 90 on the head"),
    "tnt_explode": ("other", 0x3D, False, 1.0, None, "RB_TNT.c: PlaySound3D(0x3d), the explosion"),
    "bomb_launch": ("other", 0x47, False, 1.0, None, "VehPickupItem.c SOUND_BOMB_LAUNCH: PlaySound3D(0x47) when a bomb is fired"),
    "bomb_roll": ("other", 0x48, True, 1.0, None, "RB_MovingExplosive.c: PlaySound3D_Flags(0x48) every frame a bomb rolls, stopped when it explodes"),
    "bomb_explode": ("other", 0x49, False, 1.0, None, "RB_MovingExplosive.c RB_MovingExplosive_Explode: PlaySound3D(0x49) for a bomb"),
    "crate_break": ("other", 0x3C, False, 1.0, None, "RB_Crate.c: PlaySound3D(0x3c) when a crate breaks"),
    "item_roll": ("other", 0x5D, True, 1.0, None, "RB_Crate.c: OtherFX_Play(0x5d, 0) when the weapon roulette starts; the sound loops for the whole roll (MainGameEnd.c stops it): the mod loops it on the client while the roulette runs"),
    "item_get": ("other", 0x5E, False, 1.0, None, "VehPhysProc.c: VEH_PHYS_PROC_ITEM_ROLL_NORMAL_FX, the item is drawn"),
    "item_get_juiced": ("other", 0x41, False, 1.0, None, "VehPhysProc.c: VEH_PHYS_PROC_ITEM_ROLL_JUICED_FX, with ten wumpa"),
    "crash_hard": ("other", 0x0A, False, 1.0, None, "HOWL_OtherFXRecycle.c OtherFX_DriverCrashing: volume 0xDD and up"),
    "crash_mid": ("other", 0x0C, False, 1.0, None, "HOWL_OtherFXRecycle.c OtherFX_DriverCrashing: volume above 0xA0"),
    "crash_soft": ("other", 0x0B, False, 1.0, None, "HOWL_OtherFXRecycle.c OtherFX_DriverCrashing: the rest"),
}
# no CTR sound exists for: drift_start, boost_fail (the early boost only puffs smoke), meter_red, spin_out -> placeholders stay.

MIN_RATE = 22050          # libvorbis encodes down to 8 kHz only; upsampling changes nothing audible
MIN_LOOP_SECONDS = 0.5    # the engine loop is 78 ms long: repeat it so the game's looping has something to chew on


def to_float(pcm):
    return np.asarray(pcm, dtype=np.float64) / 32768.0


def resample(x, rate, loop):
    if rate >= MIN_RATE:
        return x, rate
    n = int(round(len(x) * MIN_RATE / rate))
    pos = np.arange(n) * (len(x) / n)
    if loop:
        return np.interp(pos, np.arange(len(x)), x, period=len(x)), MIN_RATE
    return np.interp(pos, np.arange(len(x)), x), MIN_RATE


def build(howl, name, spec, report):
    table, ident, loop, gain, trim, site = spec
    inst = (howl.engine if table == "engine" else howl.other)[ident]
    data = howl.samples.get(inst["spu"])
    if data is None:
        raise SystemExit("%s: SPU sample %d is in no bank" % (name, inst["spu"]))
    rate = int(round(inst["pitch"] * 44100 / 4096))
    looped_flag = bool(inst["flags"] & 2)
    if loop:
        pcm = steady_loop(data)
        if pcm is None:
            pcm = decode_adpcm(data)[0]
            loop = False
    else:
        pcm = decode_adpcm(data)[0]
    x = to_float(pcm)
    # trailing silence off one-shots
    if not loop:
        nz = np.nonzero(np.abs(x) > 0.004)[0]
        if len(nz):
            x = x[: nz[-1] + 1]
    if trim:
        x = x[: int(trim * rate)].copy()
        n = min(len(x), int(0.05 * rate))
        x[-n:] *= np.linspace(1, 0, n)        # a short fade, so the cut does not click
    if loop:
        reps = max(1, int(np.ceil(MIN_LOOP_SECONDS * rate / len(x))))
        x = np.tile(x, reps)
    peak = float(np.max(np.abs(x))) or 1.0
    x = x / peak * 0.8 * gain * min(1.0, inst["volume"] / 153.0 + 0.15)
    x, out_rate = resample(x, rate, loop)
    report.append({"event": name, "howl": "%s %d (spu %d)" % (table, ident, inst["spu"]), "rate": rate,
                   "seconds": round(len(x) / out_rate, 3), "looped_in_game": looped_flag, "loop": loop,
                   "instrument_volume": inst["volume"], "source": site})
    return x, out_rate


def find_disc(project):
    for pattern in ("CTR*/*.bin", "CTR*/*.iso", "*.bin", "*.iso"):
        for p in glob.glob(os.path.join(project, pattern)):
            return p
    return None


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--disc", help="the disc image (.bin raw 2352 or .iso); default: found in the project folder")
    ap.add_argument("--out", default=os.path.join(project, "local-assets", "ctr-sounds"), help="resource pack folder to write")
    ap.add_argument("--install", help="also copy the pack into this resourcepacks folder (an ATLauncher instance's)")
    args = ap.parse_args()

    disc_path = args.disc or find_disc(project)
    if not disc_path or not os.path.exists(disc_path):
        sys.exit("No disc image found. Pass --disc \"path\\to\\CTR.bin\".")
    disc = Disc(disc_path)
    print("Disc:", disc_path, "| boot file:", disc.game_id(), "| layout:", disc.layout)
    lba, size = disc.find("SOUNDS/KART.HWL")
    print("KART.HWL: %d bytes at sector %d" % (size, lba))
    howl = Howl(disc.read(lba, size))
    print("HOWL: %d OtherFX, %d EngineFX, %d samples" % (len(howl.other), len(howl.engine), len(howl.samples)))

    pack = args.out
    sounds_dir = os.path.join(pack, "assets", "ctrcraft", "sounds")
    if os.path.isdir(pack):
        shutil.rmtree(pack)
    os.makedirs(sounds_dir)
    report = []
    for name, spec in MAPPING.items():
        x, rate = build(howl, name, spec, report)
        oggenc.write_ogg(os.path.join(sounds_dir, name + ".ogg"), np.clip(x, -1, 1), rate, quality=0.5)
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"pack": {"pack_format": 34,
                            "description": "Craft Team Racing: original sounds extracted from your own disc (local use only, do not share)"}}, f, indent=2)
    with open(os.path.join(pack, "README.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("Made by tools/extract_ctr_sounds.py from the owner's own copy of Crash Team Racing.\n"
                "The recordings belong to Sony / Naughty Dog: keep this pack on this machine, do not share or upload it.\n"
                "Enable it in Options > Resource Packs; it replaces the mod's synthesised placeholder sounds.\n")
    with open(os.path.join(pack, "extracted.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(report, f, indent=2)
    print("\n%-12s %-22s %7s %7s  %s" % ("event", "CTR sound", "rate", "secs", "loop"))
    for r in report:
        print("%-12s %-22s %7d %7.2f  %s" % (r["event"], r["howl"], r["rate"], r["seconds"], "loop" if r["loop"] else ""))
    print("\nPack written to", pack)
    if args.install:
        dst = os.path.join(args.install, "CTRCraft-OriginalSounds")
        if os.path.isdir(dst):
            shutil.rmtree(dst)
        shutil.copytree(pack, dst)
        print("Installed to", dst, "(enable it in Options > Resource Packs)")


if __name__ == "__main__":
    main()
