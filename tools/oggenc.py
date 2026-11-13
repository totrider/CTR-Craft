"""
A tiny Ogg Vorbis encoder over the libogg / libvorbis / libvorbisenc DLLs that ship with Krita (no pip packages, no ffmpeg:
Krita's ffmpeg only encodes stereo, and Minecraft needs mono sounds for them to have a position). Samples are 1-D (mono, for sound effects) or an array of
shape (n, 2) (stereo, for music, which Minecraft plays without a position).

  from oggenc import write_ogg
  write_ogg("out.ogg", float_samples, 44100, quality=0.4)

Set KRITA_BIN if Krita is not in C:\\Program Files\\Krita (x64)\\bin. Follows libvorbis's encoder_example.c.
"""
import ctypes as C
import os

import numpy as np

KRITA_BIN = os.environ.get("KRITA_BIN") or r"C:\Program Files\Krita (x64)\bin"


class OggPacket(C.Structure):
    _fields_ = [("packet", C.POINTER(C.c_ubyte)), ("bytes", C.c_long), ("b_o_s", C.c_long), ("e_o_s", C.c_long),
                ("granulepos", C.c_int64), ("packetno", C.c_int64)]


class OggPage(C.Structure):
    _fields_ = [("header", C.POINTER(C.c_ubyte)), ("header_len", C.c_long), ("body", C.POINTER(C.c_ubyte)),
                ("body_len", C.c_long)]


def _load():
    if hasattr(os, "add_dll_directory"):
        os.add_dll_directory(KRITA_BIN)
    ogg = C.CDLL(os.path.join(KRITA_BIN, "libogg.dll"))
    vorbis = C.CDLL(os.path.join(KRITA_BIN, "libvorbis.dll"))
    enc = C.CDLL(os.path.join(KRITA_BIN, "libvorbisenc.dll"))
    return ogg, vorbis, enc


def write_ogg(path, samples, rate=44100, quality=0.4):
    ogg, vorbis, enc = _load()
    data = np.asarray(samples, dtype=np.float32)
    channels = 1 if data.ndim == 1 else data.shape[1]
    if channels not in (1, 2):
        raise ValueError("write_ogg takes mono or stereo samples")
    big = lambda: C.create_string_buffer(8192)      # opaque, zeroed state structs: generously sized
    vi, vc, vd, vb, os_ = big(), big(), big(), big(), big()
    vorbis.vorbis_info_init(vi)
    enc.vorbis_encode_init_vbr.argtypes = [C.c_void_p, C.c_long, C.c_long, C.c_float]
    if enc.vorbis_encode_init_vbr(vi, channels, rate, quality) != 0:
        raise RuntimeError("vorbis_encode_init_vbr failed")
    vorbis.vorbis_comment_init(vc)
    vorbis.vorbis_comment_add_tag(vc, b"ENCODER", b"ctr-craft tools/oggenc.py")
    vorbis.vorbis_analysis_init(vd, vi)
    vorbis.vorbis_block_init(vd, vb)
    ogg.ogg_stream_init(os_, 1996)

    header, comm, code = OggPacket(), OggPacket(), OggPacket()
    vorbis.vorbis_analysis_headerout(vd, vc, C.byref(header), C.byref(comm), C.byref(code))
    for p in (header, comm, code):
        ogg.ogg_stream_packetin(os_, C.byref(p))

    out = bytearray()
    page = OggPage()

    def emit():
        out.extend(C.string_at(page.header, page.header_len))
        out.extend(C.string_at(page.body, page.body_len))

    while ogg.ogg_stream_flush(os_, C.byref(page)):
        emit()

    vorbis.vorbis_analysis_buffer.restype = C.POINTER(C.POINTER(C.c_float))
    vorbis.vorbis_analysis_buffer.argtypes = [C.c_void_p, C.c_int]
    pos, n_total, chunk = 0, len(data), 1024
    op = OggPacket()
    eos = False
    while not eos:
        n = min(chunk, n_total - pos)
        if n <= 0:
            vorbis.vorbis_analysis_wrote(vd, 0)
        else:
            buf = vorbis.vorbis_analysis_buffer(vd, n)
            for c in range(channels):
                chan = np.ascontiguousarray(data[pos: pos + n] if channels == 1 else data[pos: pos + n, c])
                C.memmove(buf[c], chan.ctypes.data, n * 4)
            vorbis.vorbis_analysis_wrote(vd, n)
            pos += n
        while vorbis.vorbis_analysis_blockout(vd, vb) == 1:
            vorbis.vorbis_analysis(vb, None)
            vorbis.vorbis_bitrate_addblock(vb)
            while vorbis.vorbis_bitrate_flushpacket(vd, C.byref(op)):
                ogg.ogg_stream_packetin(os_, C.byref(op))
                while not eos:
                    if ogg.ogg_stream_pageout(os_, C.byref(page)) == 0:
                        break
                    emit()
                    if ogg.ogg_page_eos(C.byref(page)):
                        eos = True
    with open(path, "wb") as f:
        f.write(bytes(out))
