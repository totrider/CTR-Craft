"""
The pickups in the world: the Wumpa fruit, the fruit crate and the power-up ("?") crate. Stand-in models, original art in the original game's style and size.

  python tools/make_pickup_models.py

The models are Blockbench's: blockbench/pickups/<name>.bbmodel is the source (written once from the shapes below if missing, then it is yours; this
script only reads it). Writes src/main/resources/assets/ctrcraft/models/item/pickup_<name>.json (the pickup entity renderer fetches these baked
models) and draws the shared placeholder texture textures/item/pickups.png once if it is missing (yours to repaint).

SIZES are the original game's PROPORTIONS, normalised so that a crate is exactly one block, the unit Minecraft works in (the kart, which already
feels right, is the other reference). The original crate is a cube of 108.8 level units and the fruit 54.8 x 60.6 x 67.5 (measured from the model data
on the disc), so one level unit here is 1/108.8 of a block: the fruit comes out about 0.5 x 0.56 x 0.62 blocks. A crate has a flat face on each side
with a frame of strips along its twelve edges. The original's faces are flat (a ring of four mitred strips around a panel in the same plane, nothing stands
out of the face); the stand-in's are too: each face is a flat panel showing its whole texture and four flat strips, 30 flat pieces and no overlap.

The texture is two faces wide and high (FACE texels per face, 16 or 32; uv units 0..16): top left a palette of flat colour cells (a quarter of the quadrant each), top right the "?" face of the power-up
crate (wood with crossed braces and a big yellow ? with a red outline), bottom left the fruit crate's side (vertical planks), bottom right the crate's edge strip in its top left quarter (both crates use it on all twelve frame bars). They are
drawn here as look-alikes, they are not the original game's art. Origin of every model: the bottom centre of the block; the models are larger than a
block (a block model may extend from -16 to 32 px).
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ctrcraft")
TEX = os.path.join(ASSETS, "textures", "item", "pickups.png")
BB = os.path.join("blockbench", "pickups")

PX_PER_BLOCK = 16.0
PX_PER_UNIT = PX_PER_BLOCK / 108.8          # the original's crate (108.8 level units) is one block
CRATE = 108.8 * PX_PER_UNIT                  # 16 px: one block
CRATE_LO, CRATE_HI = 8.0 - CRATE / 2.0, 8.0 + CRATE / 2.0
FRAME = 18.1 * PX_PER_UNIT                   # the bevel strip of the original's crate: 18.1 units wide

# The stand-in's resolution: FACE is the size in texels of one crate face (= one block face, 16 model px): 16 is the vanilla block texture's resolution, 32 a
# finer one. The sheet is two faces wide and high. Choose with  python tools/make_pickup_models.py --face 16   (the sheet is only drawn if it is missing; delete
# textures/item/pickups.png to redraw it, then refresh the textures embedded in the bbmodels with --embed). Everything on the sheet is laid out in fractions of
# the quadrant, so the packs made by tools/extract_ctr_textures.py can use any whole multiple of the sheet's quadrant.
FACE = 16
ART = 16                    # the resolution the crates' and the fruit's art is drawn in (vanilla's 16 texels per block face); the sheet doubles it, so the leaves, which are
                            # small, get cutouts of twice the resolution (4 x 8 and 5 x 8 texels: a 2 x 4 triangle is too crude to read as a leaf)
if "--face" in sys.argv:
    FACE = int(sys.argv[sys.argv.index("--face") + 1])
if "--art" in sys.argv:
    ART = int(sys.argv[sys.argv.index("--art") + 1])
ART = min(ART, FACE)
SHEET = 2 * FACE
QUAD = FACE
CELL = QUAD // 4
# palette cells: name -> (column, row, rgb); each cell is a quarter of the quadrant, in the top left quadrant
PALETTE = {
    "orange": (0, 0, (255, 140, 40)), "orange_dark": (1, 0, (205, 95, 25)), "orange_light": (2, 0, (255, 190, 100)), "green": (3, 0, (70, 170, 60)),
    "green_dark": (0, 1, (40, 110, 40)), "brown": (1, 1, (150, 100, 55)), "brown_dark": (2, 1, (105, 70, 40)), "brown_light": (3, 1, (195, 145, 85)),
    "yellow": (0, 2, (255, 215, 40)), "yellow_dark": (1, 2, (200, 160, 20)), "red": (2, 2, (215, 50, 50)), "red_dark": (3, 2, (140, 30, 30)),
    "white": (0, 3, (245, 245, 245)), "black": (1, 3, (30, 28, 34)), "wood": (2, 3, (214, 124, 40)), "wood_dark": (3, 3, (150, 80, 25)),
}
QUESTION = [  # a 7 x 9 "?"
    ".XXXXX.", "XX...XX", "XX...XX", "....XX.", "...XX..", "...XX..", "...XX..", ".......", "...XX..",
]
QUESTION_SMALL = [  # a 5 x 7 "?" for the 16 px sheet
    ".XXX.", "X...X", "....X", "..XX.", "..X..", ".....", "..X..",
]


def wood(d, box, base, dark, light, seed, vertical=True):
    """Grainy wood in a box: a base colour with darker and lighter streaks."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=base)
    for _ in range((x1 - x0) * 2):
        if vertical:
            x = rnd.randint(x0, x1)
            ya = rnd.randint(y0, y1)
            d.line([(x, ya), (x, min(y1, ya + rnd.randint(3, 12)))], fill=rnd.choice((dark, light)))
        else:
            y = rnd.randint(y0, y1)
            xa = rnd.randint(x0, x1)
            d.line([(xa, y), (min(x1, xa + rnd.randint(3, 12)), y)], fill=rnd.choice((dark, light)))


def wood_strip():
    """The crate trim's 16 x 16 texture, as the original's: orange wood with the grain along v, darker edges, and a rivet speck in the middle of the first
    and the last row."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    p = lambda n: PALETTE[n][2] + (255,)
    wood(d, [0, 0, 15, 15], p("wood"), p("wood_dark"), p("orange_light"), 3)
    d.line([(0, 0), (0, 15)], fill=p("wood_dark"))
    d.line([(15, 0), (15, 15)], fill=p("wood_dark"))
    d.rectangle([6, 0, 9, 0], fill=p("black"))
    d.rectangle([6, 15, 9, 15], fill=(150, 154, 164, 255))
    return im


def trapezoid(size, frame, strip):
    """The bottom strip of the frame as the original draws it: a trapezoid from the face's bottom edge up by {frame}, mitred at 45 degrees at both ends, with the
    whole strip texture mapped on it, v along the strip (the rivet rows end up on the mitres, at the corners)."""
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px, sp = out.load(), strip.load()
    for y in range(frame):
        for x in range(y, size - y):
            u = (y + 0.5) / frame
            v = (x - y + 0.5) / (size - 2 * y)
            px[x, size - 1 - y] = sp[min(15, int(u * 16)), min(15, int(v * 16))]
    return out


def crate_face(size, panel_art):
    """A whole crate face, size x size: four mitred strips round a square panel, all in one plane, as the original's. {panel_art(n)} draws the panel, n px square."""
    frame = round(size * 18.1 / 108.8)
    strip = wood_strip()
    base = trapezoid(size, frame, strip)
    face = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for turns in range(4):
        face.alpha_composite(base.rotate(90 * turns))
    inner = size - 2 * frame
    face.alpha_composite(panel_art(inner), (frame, frame))
    return face


def question_panel(n):
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    p = lambda name: PALETTE[name][2] + (255,)
    wood(d, [0, 0, n - 1, n - 1], p("wood"), p("wood_dark"), p("orange_light"), 1, vertical=False)
    for a, b in (((0, 0), (n - 1, n - 1)), ((n - 1, 0), (0, n - 1))):          # crossed braces
        thick, thin = (max(3, n // 5), max(2, n // 7)) if n >= 16 else (2, 1)
        d.line([a, b], fill=p("wood_dark"), width=thick)
        d.line([a, b], fill=p("orange"), width=thin)
    d.rectangle([0, 0, n - 1, n - 1], outline=p("wood_dark"))
    glyph = QUESTION if n >= 16 else QUESTION_SMALL           # a panel of 10 px has room for a 5 x 7 "?" and its outline, not for the 7 x 9
    gw, gh = len(glyph[0]), len(glyph)
    k = max(1, (n - 2) // gh)
    mask = Image.new("L", (n, n), 0)
    md = ImageDraw.Draw(mask)
    gx, gy = (n - gw * k) // 2, (n - gh * k) // 2
    for y, row in enumerate(glyph):
        for x, c in enumerate(row):
            if c == "X":
                md.rectangle([gx + x * k, gy + y * k, gx + x * k + k - 1, gy + y * k + k - 1], fill=255)
    outline = Image.new("L", (n, n), 0)
    od = ImageDraw.Draw(outline)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            od.bitmap((dx, dy), mask, fill=255)
    for y in range(n):
        for x in range(n):
            if outline.getpixel((x, y)):
                im.putpixel((x, y), p("red"))
            if mask.getpixel((x, y)):
                im.putpixel((x, y), p("yellow"))
    return im


def plank_panel(n):
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    p = lambda name: PALETTE[name][2] + (255,)
    for i in range(4):
        x0, x1 = i * n // 4, (i + 1) * n // 4 - 1                      # four planks that together cover the panel at any size
        wood(d, [x0, 0, x1, n - 1], p("wood_dark") if i % 2 else p("brown"), p("brown_dark"), p("red_dark"), 10 + i)
        d.line([(x0, 0), (x0, n - 1)], fill=p("black"))
    d.rectangle([0, 0, n - 1, n - 1], outline=p("black"))
    return im


def draw_texture():
    a, cell_px = ART, ART // 4
    im = Image.new("RGBA", (2 * a, 2 * a), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _, (cx, cy, rgb) in PALETTE.items():
        d.rectangle([cx * cell_px, cy * cell_px, cx * cell_px + cell_px - 1, cy * cell_px + cell_px - 1], fill=rgb + (255,))
    im.alpha_composite(crate_face(a, question_panel), (a, 0))
    im.alpha_composite(crate_face(a, plank_panel), (0, a))
    fruit_palette = {n: c[2] for n, c in PALETTE.items()}
    fruit_palette.update(green_dark=(70, 90, 12), yellow_dark=(165, 160, 20), orange_light=(250, 190, 25), orange=(245, 110, 20), red=(220, 35, 30), green=(40, 150, 40))
    if a != FACE:                                   # the crates at vanilla resolution, doubled; the fruit and its leaves, which are small, are drawn at the sheet's own
        im = im.resize((SHEET, SHEET), Image.NEAREST)
    im.alpha_composite(fruit_cells(fruit_palette, QUAD), (QUAD, QUAD))
    return im


def cell(name):
    cx, cy, _ = PALETTE[name]
    u = 16.0 / SHEET
    return [cx * CELL * u + 0.25, cy * CELL * u + 0.25, (cx + 1) * CELL * u - 0.25, (cy + 1) * CELL * u - 0.25]


QUESTION_UV = [8, 0, 16, 8]
PLANKS_UV = [0, 8, 8, 16]


def box(frm, to, colour, face_uv=None, faces=("north", "east", "south", "west")):
    out = {}
    for f in bbmodel.FACES:
        uv = face_uv if (face_uv is not None and f in faces) else cell(colour)
        out[f] = {"uv": list(uv), "texture": "#pickups"}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": out}


def crate(face_uv):
    """The crate: one block, a plain cube. Each face is the WHOLE face picture (the frame of four mitred strips with the rivets in the corners and the panel in
    it, see crate_face), because that is how the original has it: its faces are flat, drawn as a ring of strips round a panel, with nothing standing out."""
    face = {"uv": list(face_uv), "texture": "#pickups"}
    return [("cube", [{"from": [CRATE_LO, 0, CRATE_LO], "to": [CRATE_HI, CRATE, CRATE_HI],
                       "faces": {f: dict(face) for f in ("north", "east", "south", "west", "up", "down")}}])]


# --- the Wumpa fruit -------------------------------------------------------------------------------------------------------------------------------
# Like the giant crops of vanilla-style mods: the body is ONE box, and the roundness is painted on it (a vertical colour gradient from the original's colour
# table, dark olive at the bottom through yellow-green, yellow-orange and orange to red at the top, shaded darker towards the edges of every face with a soft
# highlight, so the cube reads as a round fruit). The leaves are four flat planes, two for each of the original's two leaves (a leaf is two bent triangles):
# every plane is a rectangle whose one triangular half is green and the other transparent, laid on top of the fruit as in the original's top view.
FRUIT_W, FRUIT_H, FRUIT_D = 54.8 * PX_PER_UNIT, 50.7 * PX_PER_UNIT, 60.6 * PX_PER_UNIT     # the body's size, from the original mesh (x, up, z)
GRADIENT_STOPS = ["green_dark", "yellow_dark", "orange_light", "orange", "red"]       # bottom to top
# bottom right quadrant of the sheet: the side face (half its width and height), the top and the bottom face, and the two leaf cutouts
FRUIT_SIDE_UV = [8.0, 8.0, 10.5, 10.0]
FRUIT_TOP_UV = [12.0, 8.0, 14.0, 10.0]
FRUIT_BOTTOM_UV = [14.0, 8.0, 16.0, 10.0]
# The leaf cutouts: the planes of the model are 2.25 (dark ones) and 2.5 (light ones) px wide and 4 px long, so each cutout is a rectangle of that proportion, in
# whole texels of the sheet (a cutout cell is the plane's size times FACE / 16, rounded), and its green half is the triangle between the corners, so a texel
# stands for the same distance along and across the leaf.
LEAF_SIZE_PX = {"dark": (2.25, 4.0), "light": (2.5, 4.0)}


def leaf_layout(base):
    """The leaf cells in texels of a quadrant of {base} texels: shade -> (x, y, width, height); the dark one first, the light one next to it, below the fruit's side."""
    cells, x = {}, 0
    for shade in ("dark", "light"):
        w, h = LEAF_SIZE_PX[shade]
        cells[shade] = (x, base // 2, int(w * base / 16.0 + 0.5), int(h * base / 16.0 + 0.5))
        x += cells[shade][2]
    return cells


LEAF_UV = {}
for _shade, (_x, _y, _w, _h) in leaf_layout(QUAD).items():
    LEAF_UV[_shade] = [(QUAD + _x) * 16.0 / SHEET, (QUAD + _y) * 16.0 / SHEET, (QUAD + _x + _w) * 16.0 / SHEET, (QUAD + _y + _h) * 16.0 / SHEET]
# The four leaf triangles of the original (from its top view, in the picture's pixels: x right, y down) with the point of the right angle of the plane each
# is part of: [dark/light, the green triangle's three corners, the fourth corner of its plane, the corner where the green triangle has its right angle].
# (picture pixels -> model pixels: the original's leaves span 53.6 level units across 358 picture pixels)
LEAF_PLANES = [
    ("dark", [(122, 176), (196, 158), (238, 346)], (167, 361), (196, 158)),
    ("light", [(196, 158), (238, 346), (308, 233)], (262, 55), (238, 346)),
    ("dark", [(308, 233), (393, 202), (377, 341)], (300, 367), (393, 202)),
    ("light", [(393, 202), (377, 341), (472, 397)], (480, 250), (377, 341)),
]
LEAF_CENTRE = (308, 233)
LEAF_PX = PX_PER_UNIT * 53.6 / 358.0


def _mix(c0, c1, t):
    return tuple(int(c0[k] + (c1[k] - c0[k]) * t) for k in range(3))


def _gradient(palette, t):
    """The body colour at height t (0 bottom, 1 top)."""
    stops = [palette[n] for n in GRADIENT_STOPS]
    f = t * (len(stops) - 1)
    i = min(len(stops) - 2, int(f))
    return _mix(stops[i], stops[i + 1], f - i)


FRUIT_ROWS = 6          # the body is 5.5 px tall in three boxes: top slab (1 texel row), middle box (4 rows), bottom slab (1 row); the colour gradient runs over all six


def strip_image(w, h, palette, t_top, t_bottom):
    """A side strip of the fruit, w x h texels: the colour gradient from height t_top (1 is the fruit's top) at the first row to t_bottom at the last, and a gentle
    darkening towards the left and right edges (10 percent at the edge)."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    px = im.load()
    for y in range(h):
        base = _gradient(palette, t_top - (y + 0.5) / h * (t_top - t_bottom))
        for x in range(w):
            edge = abs((x + 0.5) / w - 0.5) * 2.0
            shade = 1.0 - 0.10 * edge ** 2
            px[x, y] = tuple(int(c * shade) for c in base) + (255,)
    return im


def cap_image(n, palette, top):
    """The top (red) or the bottom (dark olive) of the fruit: the plain colour, slightly darker towards the edge, with a small darker dimple on the top."""
    im = Image.new("RGBA", (n, n), (0, 0, 0, 255))
    px = im.load()
    base = _gradient(palette, 1.0 if top else 0.0)
    for y in range(n):
        for x in range(n):
            r = math.hypot((x + 0.5) / n - 0.5, (y + 0.5) / n - 0.5) * 2.0
            shade = 1.0 - 0.10 * min(1.0, r) ** 2
            col = tuple(int(c * shade) for c in base)
            if top and abs(x + 0.5 - n / 2.0) < n / 4.0 and abs(y + 0.5 - n / 2.0) < n / 4.0:
                col = tuple(int(c * 0.85) for c in palette["brown_dark"])       # the stem: a darkened brown dot, a quarter of the cap's area (2 x 2 texels of 4 x 4)
            px[x, y] = col + (255,)
    return im


def leaf_image(w, h, light, dark):
    """A leaf plane's cutout, w x h texels: the lower left triangle (its legs the left and the bottom edge) is green, from the light green at the base only slightly
    towards the dark at the tip, with a faint vein along the bottom edge; the upper right half is transparent."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            if (x + 0.5) / w <= (y + 0.5) / h:
                t = 0.45 * min(1.0, max(0.0, (x + 0.5) / w * 0.6 + (1 - (y + 0.5) / h) * 0.4))
                px[x, y] = _mix(light, dark, t) + (255,)
    for x in range(w if h >= 12 else 0):                  # the vein only where it is thinner than a fifth of the leaf
        if px[x, h - 1][3]:
            px[x, h - 1] = _mix(light, dark, 0.7) + (255,)
    return im


def fruit_cells(palette, q, leaves=True, only_leaves=False):
    """The bottom right quadrant (q x q px): the fruit's side, top and bottom faces and its two leaf cutouts, painted in {palette}. q is the sheet's quadrant or a
    whole multiple of it."""
    im = Image.new("RGBA", (q, q), (0, 0, 0, 0))
    half, quarter, scale = q // 2, q // 4, q / float(QUAD)
    scale_i = q // QUAD
    if not only_leaves:
        # the side strips, one texel per model pixel: the middle box 5 x 4 texels, the two slabs (4 px wide) 4 x 1 each, below it
        top_t, mid_t = 1.0 - 1.0 / FRUIT_ROWS, 1.0 / FRUIT_ROWS
        im.paste(strip_image(5 * scale_i, 4 * scale_i, palette, top_t, mid_t), (0, 0))
        im.paste(strip_image(4 * scale_i, scale_i, palette, 1.0, top_t), (0, 6 * scale_i))
        im.paste(strip_image(4 * scale_i, scale_i, palette, mid_t, 0.0), (0, 7 * scale_i))
        im.paste(cap_image(quarter, palette, True), (half, 0))
        im.paste(cap_image(quarter, palette, False), (half + quarter, 0))
    light = palette["green"]
    tones = {"dark": (tuple(int(c * 0.7) for c in light), tuple(int(c * 0.7 * 0.7) for c in light)),
             "light": (light, tuple(int(c * 0.7) for c in light))}
    for shade, (x, y, w, h) in (leaf_layout(QUAD).items() if leaves or only_leaves else ()):
        im.paste(leaf_image(int(w * scale), int(h * scale), *tones[shade]), (int(x * scale), int(y * scale)))
    return im


def _leaf_plane(shade, tri, fourth, right_angle, y):
    """One leaf plane: the smallest rectangle round the plane's four corners among the orientations a block model can turn a flat element to (0, 22.5, 45 and
    -22.5 degrees about the vertical axis), flat at height y, its cutout turned so the green half's right angle is at the right corner."""
    pts = [((px - LEAF_CENTRE[0]) * LEAF_PX, (py - LEAF_CENTRE[1]) * LEAF_PX) for px, py in tri + [fourth]]
    best = None
    for phi in (0.0, 22.5, 45.0, -22.5):
        c, sn = math.cos(math.radians(phi)), math.sin(math.radians(phi))
        a1, a2 = (c, -sn), (sn, c)                     # the element's x and z axes after turning by phi (a flat element turns about its centre)
        us = [p[0] * a1[0] + p[1] * a1[1] for p in pts]
        vs = [p[0] * a2[0] + p[1] * a2[1] for p in pts]
        area = (max(us) - min(us)) * (max(vs) - min(vs))
        if best is None or area < best[0] - 1e-9:
            best = (area, phi, a1, a2, min(us), max(us), min(vs), max(vs))
    _, phi, a1, a2, u0, u1, v0, v1 = best
    cu, cv = (u0 + u1) / 2.0, (v0 + v1) / 2.0
    wx, wz = u1 - u0, v1 - v0
    # the centre in world plan coordinates (x east, z south), the box's own axes being a1 and a2
    cx = 8 + cu * a1[0] + cv * a2[0]
    cz = 8 + cu * a1[1] + cv * a2[1]
    rx = ((right_angle[0] - LEAF_CENTRE[0]) * LEAF_PX) * a1[0] + ((right_angle[1] - LEAF_CENTRE[1]) * LEAF_PX) * a1[1] - cu
    rz = ((right_angle[0] - LEAF_CENTRE[0]) * LEAF_PX) * a2[0] + ((right_angle[1] - LEAF_CENTRE[1]) * LEAF_PX) * a2[1] - cv
    sx, sz = (1 if rx > 0 else -1), (1 if rz > 0 else -1)
    u0_, v0_, u1_, v1_ = LEAF_UV[shade]
    up = [u1_ if sx > 0 else u0_, v0_ if sz > 0 else v1_, u0_ if sx > 0 else u1_, v1_ if sz > 0 else v0_]
    down = [u1_ if sx > 0 else u0_, v1_ if sz > 0 else v0_, u0_ if sx > 0 else u1_, v0_ if sz > 0 else v1_]
    return {"from": [round(cx - wx / 2, 3), round(y, 3), round(cz - wz / 2, 3)], "to": [round(cx + wx / 2, 3), round(y, 3), round(cz + wz / 2, 3)],
            "faces": {"up": {"uv": up, "texture": "#pickups"}, "down": {"uv": down, "texture": "#pickups"}},
            "rotation": {"origin": [round(cx, 3), round(y, 3), round(cz, 3)], "axis": "y", "angle": phi}}


def wumpa():
    """The first version of the Wumpa fruit (one box with the roundness painted on it, four leaf planes on top, see the notes above). blockbench/pickups/wumpa.bbmodel is
    the model now: it was reshaped by hand (three stacked boxes and four leaf planes) and this function only writes the file if it is missing."""
    hw, hd = FRUIT_W / 2.0, FRUIT_D / 2.0
    face = lambda uv: {"uv": list(uv), "texture": "#pickups"}
    body = {"from": [round(8 - hw, 3), 0, round(8 - hd, 3)], "to": [round(8 + hw, 3), round(FRUIT_H, 3), round(8 + hd, 3)],
            "faces": {"north": face(FRUIT_SIDE_UV), "south": face(FRUIT_SIDE_UV), "east": face(FRUIT_SIDE_UV), "west": face(FRUIT_SIDE_UV),
                      "up": face(FRUIT_TOP_UV), "down": face(FRUIT_BOTTOM_UV)}}
    leaves = [_leaf_plane(shade, tri, fourth, ra, FRUIT_H + 0.12 + 0.08 * i) for i, (shade, tri, fourth, ra) in enumerate(LEAF_PLANES)]
    return [("body", [body]), ("leaves", leaves)]


def models():
    return {
        "wumpa": wumpa(),
        "fruit_crate": crate(PLANKS_UV),
        "powerup_crate": crate(QUESTION_UV),
    }


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.28, 0.28, 0.28]}, "ground": {"translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]}}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def embed():
    """Put the current sheet into the texture slot of every pickup bbmodel (the models carry their own copy of the picture)."""
    import base64
    src = "data:image/png;base64," + base64.b64encode(open(TEX, "rb").read()).decode()
    for name in models():
        path = os.path.join(BB, name + ".bbmodel")
        m = json.load(open(path, encoding="utf-8"))
        w, h = Image.open(TEX).size
        for t in m["textures"]:
            t["source"] = src
            t["width"], t["height"] = w, h
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(m, f, indent=1)


def main():
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    if not os.path.exists(TEX):
        draw_texture().save(TEX)
    os.makedirs(BB, exist_ok=True)
    for name, groups in models().items():
        path = os.path.join(BB, name + ".bbmodel")
        if not os.path.exists(path):
            bbmodel.write(path, name, {"pickups": TEX}, groups, display=display(), gui_light_front=False,
                          comment="Stand-in pickup model in the original game's proportions (a crate is exactly one block). One group per part. The single texture "
                                  "slot 'pickups' is shared by all pickups: a small sheet (palette cells, the ? face, the fruit crate side), see "
                                  "tools/make_pickup_models.py.")
        model = bbmodel.read(path)
        dump(os.path.join(ASSETS, "models", "item", "pickup_" + name + ".json"), {
            "textures": {"pickups": "ctrcraft:item/pickups", "particle": "ctrcraft:item/pickups"},
            "elements": bbmodel.elements(model),
            "display": model["display"] or display(),
        })
    if "--embed" in sys.argv:
        embed()
    print("pickup models written")


if __name__ == "__main__":
    main()
