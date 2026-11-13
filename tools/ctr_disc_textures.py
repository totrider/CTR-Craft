"""
Reading CTR's textures from the player's own disc image: BIGFILE.BIG, PS1 VRAM pages (TIM), icon packs (.mpk) and the models of a level file.

Format knowledge: CTR-tools (GPL-2.0), CTR-tools/ctr-native (GPL-3.0) and the ctr-assets notes of world-of-kartcraft (GPL-3.0), the same
sources as extract_ctr_sounds.py. This file only reads; extract_ctr_textures.py decides what to build from it.

VRAM is a 1024 x 512 grid of 16-bit words. A texture is a rectangle of texels in a "texture page" (4, 8 or 15 bits per texel) plus, for 4 and 8
bit, a palette (CLUT) row; both are named by a 12-byte TextureLayout:
    u8 u0, v0; u16 clut; u8 u1, v1; u16 tpage; u8 u2, v2, u3, v3
"""
import struct

import numpy as np

# NTSC-U (SCUS-94426) BIGFILE indices (CTR-tools big_usa_release.txt, ctr-native namespace_Load.h)
CRASH_COVE_1P_VRM = 112
CRASH_COVE_1P_LEV = 113
SHARED_VRM = 258
SHARED_MPK = 259


class BigFile:
    def __init__(self, disc):
        self.disc = disc
        self.lba, _ = disc.find("BIGFILE.BIG")
        head = disc.read(self.lba, 8)
        count = struct.unpack_from("<I", head, 4)[0]
        if struct.unpack_from("<I", head, 0)[0] != 0 or not 0 < count <= 4096:
            raise SystemExit("BIGFILE.BIG: unexpected header")
        toc = disc.read(self.lba, 8 + count * 8)
        self.entries = [struct.unpack_from("<II", toc, 8 + i * 8) for i in range(count)]

    def read(self, index):
        offset, size = self.entries[index]
        return self.disc.read(self.lba + offset, size)


# ------------------------------------------------------------------------------------------------------------------------------- VRAM


class Vram:
    """The PS1's 1024 x 512 16-bit video memory, filled from .vrm files (a stream of TIMs, or one TIM)."""

    def __init__(self):
        self.mem = np.zeros((512, 1024), dtype=np.uint16)

    def load(self, data):
        if struct.unpack_from("<I", data, 0)[0] == 0x20:
            pos = 4
            while pos + 4 <= len(data):
                size = struct.unpack_from("<I", data, pos)[0]
                if size == 0:
                    break
                self._tim(data[pos + 4: pos + 4 + size])
                pos += 4 + size
        else:
            self._tim(data)

    def _tim(self, tim):
        if struct.unpack_from("<I", tim, 0)[0] != 0x10:
            return
        flags = struct.unpack_from("<I", tim, 4)[0]
        pos = 8
        for _ in range(2 if flags & 8 else 1):
            length, x, y, w, h = struct.unpack_from("<IHHHH", tim, pos)
            words = np.frombuffer(tim, dtype="<u2", count=w * h, offset=pos + 12).reshape(h, w)
            self.mem[y: y + h, x: x + w] = words
            pos += length

    def texture_stp(self, layout):
        """The same texels as {texture}, and which of them have the semi-transparency bit (bit 15 of the colour word) set: (RGBA image, bool array of the same size). The GPU
        blends only those texels of a primitive that asks for semi-transparency; the others are drawn solid."""
        img = self.texture(layout)
        u0, v0, clut, u1, v1, tpage, u2, v2, u3, v3 = struct.unpack("<BBHBBHBBBB", layout)
        us = (u0, u1, u2, u3)
        vs = (v0, v1, v2, v3)
        x0, y0 = min(us), min(vs)
        h, w = img.shape[:2]
        px, py = (tpage & 15) * 64, ((tpage >> 4) & 1) * 256
        depth = (tpage >> 7) & 3
        cx, cy = (clut & 63) * 16, clut >> 6
        stp = np.zeros((h, w), dtype=bool)
        for j in range(h):
            for i in range(w):
                u, v = x0 + i, y0 + j
                if depth == 0:
                    word = int(self.mem[py + v, px + u // 4])
                    c = int(self.mem[cy, cx + ((word >> (4 * (u % 4))) & 15)])
                elif depth == 1:
                    word = int(self.mem[py + v, px + u // 2])
                    c = int(self.mem[cy, cx + ((word >> (8 * (u % 2))) & 255)])
                else:
                    c = int(self.mem[py + v, px + u])
                stp[j, i] = c != 0 and (c >> 15) & 1 == 1
        return img, stp

    def texture(self, layout):
        """Decode a TextureLayout (12 bytes) to an RGBA numpy image (the layout's bounding box). Texel 0x0000 is transparent."""
        u0, v0, clut, u1, v1, tpage, u2, v2, u3, v3 = struct.unpack("<BBHBBHBBBB", layout)
        us = (u0, u1, u2, u3)
        vs = (v0, v1, v2, v3)
        x0, x1, y0, y1 = min(us), max(us), min(vs), max(vs)
        px, py = (tpage & 15) * 64, ((tpage >> 4) & 1) * 256
        depth = (tpage >> 7) & 3
        w, h = x1 - x0 + 1, y1 - y0 + 1          # the bounding box is inclusive: 0xFF is the last texel of a page
        if w <= 0 or h <= 0:
            return None
        cx, cy = (clut & 63) * 16, clut >> 6
        out = np.zeros((h, w, 4), dtype=np.uint8)
        for j in range(h):
            for i in range(w):
                u, v = x0 + i, y0 + j
                if depth == 0:
                    word = int(self.mem[py + v, px + u // 4])
                    c = int(self.mem[cy, cx + ((word >> (4 * (u % 4))) & 15)])
                elif depth == 1:
                    word = int(self.mem[py + v, px + u // 2])
                    c = int(self.mem[cy, cx + ((word >> (8 * (u % 2))) & 255)])
                else:
                    c = int(self.mem[py + v, px + u])
                if c == 0:
                    continue
                out[j, i] = ((c & 31) * 255 // 31, ((c >> 5) & 31) * 255 // 31, ((c >> 10) & 31) * 255 // 31, 255)
        return out


# ------------------------------------------------------------------------------------------------------------------------------- packs


def _u32(data, pos):
    return struct.unpack_from("<I", data, pos)[0]


def icons(mpk):
    """The icons of a .mpk ({u32 size, data, ...}; data[0] points to {num_icons, ptr_icons, ...}): a list of (name, TextureLayout bytes)."""
    size = _u32(mpk, 0)
    data = mpk[4: 4 + size]
    pack = _u32(data, 0)
    num, ptr = _u32(data, pack), _u32(data, pack + 4)
    out = []
    for i in range(num):
        rec = data[ptr + i * 32: ptr + i * 32 + 32]
        name = rec[:16].split(b"\0")[0].decode("ascii", "replace")
        out.append((name, bytes(rec[20:32])))
    return out


def level_models(lev):
    """The models in a level file: {name: ModelHeader's (command list, texture layouts) summary}. Level header: num_models @0x14, ptr_models @0x18."""
    size = _u32(lev, 0)
    data = lev[4: 4 + size]
    num, ptr = _u32(data, 0x14), _u32(data, 0x18)
    models = {}
    for i in range(num):
        mp = _u32(data, ptr + i * 4)
        name = data[mp: mp + 16].split(b"\0")[0].decode("ascii", "replace")
        num_meshes, ptr_meshes = struct.unpack_from("<hxxI", data, mp + 0x12)[0], _u32(data, mp + 0x14)
        if num_meshes < 1:
            continue
        hdr = ptr_meshes                            # the headers follow one another, 0x40 bytes each; the first is the _hi one
        ptr_cmds = _u32(data, hdr + 0x20)
        ptr_tex = _u32(data, hdr + 0x28)
        models[name] = {"data": data, "header": hdr, "cmds": ptr_cmds, "tex": ptr_tex}
    return models


def pack_models(pack):
    """The models of a model pack (the shared pack, BIGFILE entry 259: the weapons, the crates, the racers, the icons): {name: the same summary level_models gives}. The pack is
    a size word and then the data; a model starts with its 16 byte zero-padded name (a model with num_meshes below 1 is only a name that a LOD header repeats)."""
    size = _u32(pack, 0)
    data = pack[4: 4 + size]
    models = {}
    for k in range(0, len(data) - 0x40, 4):
        chunk = data[k: k + 16]
        z = chunk.split(b"\0")[0]
        if not (3 <= len(z) <= 15 and all(32 < c < 127 for c in z) and chunk[len(z):] == b"\0" * (16 - len(z))):
            continue
        num_meshes = struct.unpack_from("<h", data, k + 0x12)[0]
        ptr_meshes = _u32(data, k + 0x14)
        if num_meshes < 1 or ptr_meshes % 4 or not 0 < ptr_meshes < len(data) - 0x40:
            continue
        ptr_cmds, ptr_tex = _u32(data, ptr_meshes + 0x20), _u32(data, ptr_meshes + 0x28)
        if not (0 < ptr_cmds < len(data) - 4 and 0 < ptr_tex < len(data) and _u32(data, ptr_cmds) < 20000):
            continue
        models.setdefault(z.decode("ascii"), {"data": data, "header": ptr_meshes, "cmds": ptr_cmds, "tex": ptr_tex})
    return models


def model_textures(model):
    """The texture layout indices a model's draw commands use, most used first ([(index, count)]; index is 1-based into the layout table)."""
    data, cmds = model["data"], model["cmds"]
    count = _u32(data, cmds)
    pos = cmds + 4
    used = {}
    n = 0
    while pos + 4 <= len(data) and n < 20000:
        c = _u32(data, pos)
        pos += 4
        n += 1
        if c == 0xFFFFFFFF:
            break
        if c >> 16 == 0:
            continue
        tex = c & 0x1FF
        if tex:
            used[tex] = used.get(tex, 0) + 1
    return sorted(used.items(), key=lambda kv: -kv[1]), count


def layout_bytes(model, index):
    data = model["data"]
    pos = _u32(data, model["tex"] + (index - 1) * 4)      # the table holds pointers to the 12-byte layouts
    return bytes(data[pos: pos + 12])


# ------------------------------------------------------------------------------------------------------------------------------- meshes


def _vertex_count(data, model):
    """The number of vertices a model decodes: the commands of its list that do not reuse a vertex (bit 26 clear)."""
    count = 0
    pos = model["cmds"] + 4
    while True:
        c = _u32(data, pos)
        pos += 4
        if c == 0xFFFFFFFF:
            break
        if c >> 16 and not (c >> 26) & 1:
            count += 1
    return count


def _sign_extend(value, bits):
    value &= (1 << bits) - 1
    return value - (1 << bits) if value & (1 << (bits - 1)) else value


def _signed_bits(data, base, bit_index, bits):
    """RenderBucket_GetSignedBits: the MSB first bit stream read through little endian 32 bit words; returns (value, new bit index)."""
    b = bit_index >> 5
    s = (32 - bits) - (bit_index & 31)
    word = _u32(data, base + b * 4)
    if s < 0:
        ret = ((word << -s) & 0xFFFFFFFF) | (_u32(data, base + (b + 1) * 4) >> (s & 31))
    else:
        ret = word >> s
    return _sign_extend(ret, bits), bit_index + bits


def _s8(v):
    return _sign_extend(v, 8)


def _compressed_vertices(data, frame, deltas, scale, count):
    """The vertices of a delta-compressed animation frame (RenderBucket_UncompressAnimationFrame): the frame's bytes after its 0x1C header are a bit stream; for each vertex
    the model's delta array holds a temporal word (bits 6-8 / 3-5 / 0-2: the field widths minus one of the three components, bits 25-31 / 17-24 / 9-16: a bias for each, the
    first doubled), and each component is a running signed byte sum of the fields ({sum + field + bias}, or the field alone when the width is 8 bits). The stream's order is
    the model's own (b0, b1, b2): x, z, and y last, which is up."""
    fpos = struct.unpack_from("<3h", data, frame)
    base = frame + 0x1C
    bit = 0
    acc = [0, 0, 0]             # x, "y" (read second), "z" (read third): the game's x_alu, y_alu, z_alu
    out = []
    for i in range(count):
        t = _u32(data, deltas + 4 * i)
        widths = (t >> 6 & 7, t >> 3 & 7, t & 7)
        biases = (_sign_extend(t >> 25, 7) * 2, _sign_extend(t >> 17, 8), _sign_extend(t >> 9, 8))
        for k in range(3):
            value, bit = _signed_bits(data, base, bit, widths[k] + 1)
            acc[k] = _s8(value) if widths[k] == 7 else _s8(acc[k] + value + biases[k])
        b = (acc[0] & 0xFF, acc[1] & 0xFF, acc[2] & 0xFF)       # the stream's order is the model's: x, z, then y, which is up (the third coordinate)
        out.append(tuple((b[k] + fpos[k]) * scale[k] / 4096.0 for k in range(3)))
    return out


def mesh_vertices(model, frame_index=0):
    """
    The model's vertices (b0, b1, b2) in model units (level units). The game's up axis is the THIRD coordinate b2 (the Wumpa fruit's leaves rise along it) and the plane
    of the ground is (b0, b1); the exporter turns it to a y-up file. Header: scale[3] s16 @0x18, ptr_frame @0x24, num_anims @0x34, ptr_anims @0x38; frame: pos[3] s16,
    vertex bytes at +0x1C. A model with ptr_frame 0 is animated (the Wumpa fruit's 32 "spin" frames): its frames follow the animation's header (name[16], u16 frame
    count, s16 frame size, u32 delta pointer, then the frames); {frame_index} picks one (raw, uncompressed animations only).
    """
    data, hdr = model["data"], model["header"]
    scale = struct.unpack_from("<3h", data, hdr + 0x18)
    frame = _u32(data, hdr + 0x24)
    if frame == 0:
        num_anims, ptr_anims = _u32(data, hdr + 0x34), _u32(data, hdr + 0x38)
        if num_anims < 1:
            raise ValueError("model has neither a frame nor an animation")
        anim = _u32(data, ptr_anims)
        count, size, deltas = struct.unpack_from("<HhI", data, anim + 16)
        frame = anim + 24 + (frame_index % (count & 0x7FFF)) * size
        if deltas != 0:
            return _compressed_vertices(data, frame, deltas, scale, _vertex_count(data, model))
    fpos = struct.unpack_from("<3h", data, frame)
    count = 0
    pos = model["cmds"] + 4
    while True:
        c = _u32(data, pos)
        pos += 4
        if c == 0xFFFFFFFF:
            break
        if c >> 16 and not (c >> 26) & 1:
            count += 1
    out = []
    for i in range(count):
        b = struct.unpack_from("3B", data, frame + 0x1C + i * 3)
        out.append(tuple((b[k] + fpos[k]) * scale[k] / 4096.0 for k in range(3)))
    return out


def frame_count(model):
    """The number of frames of an animated model (the Wumpa fruit's spin), 1 for a model with a single frame."""
    data, hdr = model["data"], model["header"]
    if _u32(data, hdr + 0x24) != 0 or _u32(data, hdr + 0x34) < 1:
        return 1
    anim = _u32(data, _u32(data, hdr + 0x38))
    return struct.unpack_from("<H", data, anim + 16)[0] & 0x7FFF


def mesh_vertex_colours(model):
    """The colour index of every vertex (parallel to mesh_vertices): the game shades an untextured model per vertex from its colour table, and each draw command that
    loads a new vertex carries that vertex's colour (colour_table(model)[index], used as it is, no division by 128, for a Gouraud-shaded polygon without texture)."""
    data = model["data"]
    pos = model["cmds"] + 4
    out = []
    while True:
        c = _u32(data, pos)
        pos += 4
        if c == 0xFFFFFFFF:
            break
        if c >> 16 and not (c >> 26) & 1:
            out.append((c >> 9) & 0x7F)
    return out


def mesh_triangles(model):
    """
    The model's triangles from its draw commands (bits: s l n d k v ? ? | stack:8 | color:7 | tex:9). Each command loads a vertex (the next one, or with v the one
    saved in slot {stack}) into a 4-deep FIFO and emits the triangle of the last three; l copies FIFO[0] over FIFO[1] first (a fan round the older vertex);
    s starts a strip; the first triangle of a strip takes the strip's first command's texture. Returns [(corner vertex indices, texture index (1-based),
    colour index, flags)], corner i of a triangle uses uv corner i of its texture layout.
    """
    data = model["data"]
    pos = model["cmds"] + 4
    nxt = 0
    saved = {}
    fifo = [None] * 4
    seen = 0
    first_tex = first_col = 0
    tris = []
    while True:
        c = _u32(data, pos)
        pos += 4
        if c == 0xFFFFFFFF:
            break
        if c >> 16 == 0:
            continue
        s, l, n, d, k, v = ((c >> b) & 1 for b in (31, 30, 29, 28, 27, 26))
        slot = (c >> 16) & 0xFF
        col = (c >> 9) & 0x7F
        tex = c & 0x1FF
        if v:
            vtx = saved[slot]
        else:
            vtx = nxt
            nxt += 1
            saved[slot] = vtx
        if s:
            seen = 0
            first_tex, first_col = tex, col
        fifo = fifo[1:] + [vtx]
        if l:
            fifo[1] = fifo[0]
        seen += 1
        if seen >= 3:
            tris.append(((fifo[1], fifo[2], fifo[3]), first_tex if seen == 3 else tex, first_col if seen == 3 else col,
                         {"flip": bool(n), "cull": bool(d), "k": bool(k)}))
    return tris


def colour_table(model):
    """The model's colour table (RGB bytes; the game multiplies a texture by colour / 128, so 128 is unchanged)."""
    data, hdr = model["data"], model["header"]
    ptr = _u32(data, hdr + 0x2C)
    count = _u32(data, model["cmds"])
    return [tuple(struct.unpack_from("3B", data, ptr + i * 4)) for i in range(min(count, 128))]
