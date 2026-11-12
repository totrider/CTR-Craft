"""
The boost blocks: a plate, a full block, a slab and stairs, each with a direction (a chevron arrow on top), CTR's turbo pad boost.

  python tools/make_boost_pad.py

Models are Blockbench's: blockbench/boost_pad/*.bbmodel are the SOURCE (each is written once from the shapes below if it is
missing, then it is yours; this script only reads them). Non-obvious structure, read this before editing in Blockbench:
  * Texture slot "top" is the chevron arrow, slot "side" the plain metal. Only the UP faces that carry the arrow use "top".
  * The arrow's uv and face rotation are GENERATED per direction (an up face using "top" is re-mapped here: the arrow points
    north in the file, and is turned 0/90/180/270 for the game). Change an arrow face's size by changing its element; do not
    fiddle with its uv, it is recomputed (a centred slice of the arrow texture at 1:1 pixel scale, so a half-block tread shows
    the middle of the arrow, not a squashed one).
  * The stairs are authored for facing = EAST (high side to the east) like vanilla, in 3 shapes (straight, inner, outer) and 2
    halves. The _top models are the bottom ones turned upside down (x 180) and then edited so that their visible top is one big
    arrow; the boxes are split so every arrow face is exactly the visible tread (some boxes only exist to be hidden under a step).
  * The plate (boost_pad) is 1 px high and has NO collision in the game (a kart drives over it; it is a marker on the floor).

Connecting textures (Fusion, optional): with the Fusion mod installed, boost blocks of the same state next to each other join up
(the yellow border is only drawn where nothing continues it). This is a 5-tile "pieced" sheet made FROM boost_top.png by compose_pieced():
the outer 2 px ring of the base texture is the border, tile 0 is the base as drawn, and the others replace the ring with the
background colour where a neighbour continues it. So repaint boost_top.png (keep a 2 px border ring and keep the arrow inside it) and
the sheet follows on the next build. The sheet lives in src/main/resources/fusion-overrides/ and is only used when Fusion is
present (pack.mcmeta's fusion.overrides_folder); without Fusion the plain boost_top.png is used. `--preview` draws a sample field to
build/boost_connect_preview.png.

Writes models, item models, block states, loot tables, tags and names, and draws placeholder textures once if missing (yours to
repaint). Run from the project folder. The stair rotation table is vanilla's (oak_stairs blockstate), kept here as data.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ctrcraft")
DATA = os.path.join(RES, "data")
NS = "ctrcraft"
TEX_DIR = os.path.join(ASSETS, "textures", "block")
TEX_TOP = os.path.join(TEX_DIR, "boost_top.png")
TEX_SIDE = os.path.join(TEX_DIR, "boost_side.png")
BB_DIR = os.path.join("blockbench", "boost_pad")

FACINGS = ("north", "east", "south", "west")
ANGLE = {"north": 0, "east": 90, "south": 180, "west": 270}

# vanilla stairs blockstate (facing, half, shape) -> (model kind, y rotation); x 180 for half=top is replaced by our own flipped models
STAIR_TABLE = {
    ('east', 'bottom', 'inner_left'): ('inner', 270),
    ('east', 'bottom', 'inner_right'): ('inner', 0),
    ('east', 'bottom', 'outer_left'): ('outer', 270),
    ('east', 'bottom', 'outer_right'): ('outer', 0),
    ('east', 'bottom', 'straight'): ('straight', 0),
    ('east', 'top', 'inner_left'): ('inner', 0),
    ('east', 'top', 'inner_right'): ('inner', 90),
    ('east', 'top', 'outer_left'): ('outer', 0),
    ('east', 'top', 'outer_right'): ('outer', 90),
    ('east', 'top', 'straight'): ('straight', 0),
    ('north', 'bottom', 'inner_left'): ('inner', 180),
    ('north', 'bottom', 'inner_right'): ('inner', 270),
    ('north', 'bottom', 'outer_left'): ('outer', 180),
    ('north', 'bottom', 'outer_right'): ('outer', 270),
    ('north', 'bottom', 'straight'): ('straight', 270),
    ('north', 'top', 'inner_left'): ('inner', 270),
    ('north', 'top', 'inner_right'): ('inner', 0),
    ('north', 'top', 'outer_left'): ('outer', 270),
    ('north', 'top', 'outer_right'): ('outer', 0),
    ('north', 'top', 'straight'): ('straight', 270),
    ('south', 'bottom', 'inner_left'): ('inner', 0),
    ('south', 'bottom', 'inner_right'): ('inner', 90),
    ('south', 'bottom', 'outer_left'): ('outer', 0),
    ('south', 'bottom', 'outer_right'): ('outer', 90),
    ('south', 'bottom', 'straight'): ('straight', 90),
    ('south', 'top', 'inner_left'): ('inner', 90),
    ('south', 'top', 'inner_right'): ('inner', 180),
    ('south', 'top', 'outer_left'): ('outer', 90),
    ('south', 'top', 'outer_right'): ('outer', 180),
    ('south', 'top', 'straight'): ('straight', 90),
    ('west', 'bottom', 'inner_left'): ('inner', 90),
    ('west', 'bottom', 'inner_right'): ('inner', 180),
    ('west', 'bottom', 'outer_left'): ('outer', 90),
    ('west', 'bottom', 'outer_right'): ('outer', 180),
    ('west', 'bottom', 'straight'): ('straight', 180),
    ('west', 'top', 'inner_left'): ('inner', 180),
    ('west', 'top', 'inner_right'): ('inner', 270),
    ('west', 'top', 'outer_left'): ('outer', 180),
    ('west', 'top', 'outer_right'): ('outer', 270),
    ('west', 'top', 'straight'): ('straight', 180),
}
# the stair's arrow, relative to a climber walking up it (its facing is the high side): world angle offset
STAIR_DIRECTIONS = {"up": 0, "right": 90, "down": 180, "left": 270}


# ---------------------------------------------------------------------------------------------------------------- textures

def draw_top():
    """Dark metal, a yellow border and three chevrons pointing up the texture (north in the model file)."""
    im = Image.new("RGBA", (16, 16), (46, 48, 58, 255))
    yellow, dark = (255, 205, 40, 255), (30, 31, 38, 255)
    for i in range(16):
        for edge in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel(edge, yellow)
    for top in (2, 6, 10):
        for d in range(5):
            for dx in (0, 1):
                im.putpixel((8 - d - dx, top + d), yellow)
                im.putpixel((7 + d + dx, top + d), yellow)
    for x in range(1, 15):
        im.putpixel((x, 1), dark)
        im.putpixel((x, 14), dark)
    return im


def draw_side():
    """Plain dark metal with a yellow stripe along the top edge and a few rivets."""
    im = Image.new("RGBA", (16, 16), (46, 48, 58, 255))
    for x in range(16):
        im.putpixel((x, 0), (255, 205, 40, 255))
        im.putpixel((x, 1), (255, 205, 40, 255))
        im.putpixel((x, 2), (30, 31, 38, 255))
        im.putpixel((x, 15), (30, 31, 38, 255))
    for x in (3, 12):
        im.putpixel((x, 8), (96, 98, 110, 255))
    return im


# ---------------------------------------------------------------------------------------------------------------- shapes

def element(frm, to, arrow=False, up=True):
    """A box. arrow: its up face carries the arrow (uv/rotation are generated); up=False leaves the up face out (hidden)."""
    faces = {}
    for f in bbmodel.FACES:
        if f == "up":
            if not up:
                continue
            if arrow:
                faces[f] = {"uv": arrow_uv(frm, to, 0), "texture": "#top"}
                continue
        uv = bbmodel.default_uv(f, frm, to)
        faces[f] = {"uv": uv, "texture": "#side"}
    return {"from": frm, "to": to, "faces": faces}


def arrow_uv(frm, to, r):
    """The centred slice of the 16x16 arrow texture for an up face of this size, turned by r (0/90/180/270): at 1:1 pixel scale."""
    ex, ez = to[0] - frm[0], to[2] - frm[2]
    w, h = (ex, ez) if r in (0, 180) else (ez, ex)
    return [(16 - w) / 2, (16 - h) / 2, (16 + w) / 2, (16 + h) / 2]


def stair_boxes(shape, half):
    """Boxes of a stair authored for facing = east. Arrow faces are exactly the visible treads."""
    if half == "bottom":
        if shape == "straight":
            return [([0, 0, 0], [8, 8, 16], True, True), ([8, 0, 0], [16, 8, 16], False, False), ([8, 8, 0], [16, 16, 16], True, True)]
        if shape == "inner":
            return [([0, 0, 0], [8, 8, 8], True, True), ([0, 0, 8], [8, 8, 16], False, False), ([8, 0, 0], [16, 8, 16], False, False),
                    ([8, 8, 0], [16, 16, 16], True, True), ([0, 8, 8], [8, 16, 16], True, True)]
        return [([0, 0, 0], [16, 8, 8], True, True), ([0, 0, 8], [8, 8, 16], True, True), ([8, 0, 8], [16, 8, 16], False, False),
                ([8, 8, 8], [16, 16, 16], True, True)]
    # top half: upside down; the visible top is one big slab
    if shape == "straight":
        return [([0, 8, 0], [16, 16, 16], True, True), ([8, 0, 0], [16, 8, 16], False, False)]
    if shape == "inner":
        return [([0, 8, 0], [16, 16, 16], True, True), ([8, 0, 0], [16, 8, 16], False, False), ([0, 0, 0], [8, 8, 8], False, False)]
    return [([0, 8, 0], [16, 16, 16], True, True), ([8, 0, 0], [16, 8, 8], False, False)]


def display():
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.75, 0.75, 0.75]},
            "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"scale": [0.75, 0.75, 0.75]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.5, 0.5, 0.5]}}


COMMENT = ("Boost block shape. Texture slots: 'top' (the chevron arrow, points NORTH in this file) and 'side' (plain metal). "
           "Arrow faces get their uv and rotation GENERATED by tools/make_boost_pad.py for each direction, so only change their "
           "size via the element. See the notes at the top of that script.")


def sources():
    """name -> list of (name, [elements]) groups, for every bbmodel source."""
    out = {
        "boost_pad": [("plate", [element([0, 0, 0], [16, 1, 16], arrow=True)])],
        "boost_block": [("block", [element([0, 0, 0], [16, 16, 16], arrow=True)])],
        "boost_slab_bottom": [("slab", [element([0, 0, 0], [16, 8, 16], arrow=True)])],
        "boost_slab_top": [("slab", [element([0, 8, 0], [16, 16, 16], arrow=True)])],
    }
    for half in ("bottom", "top"):
        for shape in ("straight", "inner", "outer"):
            els = [element(f, t, arrow=arrow, up=up) for f, t, arrow, up in stair_boxes(shape, half)]
            out["boost_stairs_%s_%s" % (shape, half)] = [("stairs", els)]
    return out


def ensure_sources():
    os.makedirs(TEX_DIR, exist_ok=True)
    for path, draw in ((TEX_TOP, draw_top), (TEX_SIDE, draw_side)):
        if not os.path.exists(path):
            draw().save(path)
    for name, groups in sources().items():
        path = os.path.join(BB_DIR, name + ".bbmodel")
        if not os.path.exists(path):
            bbmodel.write(path, name, {"top": TEX_TOP, "side": TEX_SIDE}, groups, display=display(), gui_light_front=False,
                          comment=COMMENT)


# ---------------------------------------------------------------------------------------------------------------- models

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def turned(elements, r):
    """The elements with every arrow face (an up face using #top) turned to point r degrees clockwise from north."""
    out = []
    for el in elements:
        el = json.loads(json.dumps(el))
        up = el["faces"].get("up")
        if up and up.get("texture") == "#top":
            up["uv"] = arrow_uv(el["from"], el["to"], r)
            if r:
                up["rotation"] = r
            else:
                up.pop("rotation", None)
        out.append(el)
    return out


def write_model(name, elements, parent_display=None):
    dump(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "textures": {"top": f"{NS}:block/boost_top", "side": f"{NS}:block/boost_side", "particle": f"{NS}:block/boost_side"},
        "elements": elements})


def write_item(name, parent, disp):
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": f"{NS}:block/{parent}", "display": disp})


def read(name):
    return bbmodel.read(os.path.join(BB_DIR, name + ".bbmodel"))


# ---------------------------------------------------------------------------------------------------------------- Fusion

RING = 2                                    # px of border ring in boost_top.png
OVERRIDES = os.path.join(RES, "fusion-overrides", "assets", NS, "textures", "block")
# Fusion's pieced layout (PiecedLayoutHandler): 5 tiles in a row, chosen per quarter of the face by (side, side, diagonal) bits
PIECED_CORNER_TILE = (0, 3, 2, 4, 0, 3, 2, 1)


def compose_pieced(base):
    """The 5-tile pieced sheet (80 x 16) made from the base texture: tile 0 = isolated, 1 = all eight neighbours, 2 = up and down,
    3 = left and right, 4 = the four sides but no diagonals (inner corners)."""
    from collections import Counter
    base = base.convert("RGBA")
    assert base.size == (16, 16), "boost_top.png must be 16x16"
    bg = Counter(base.getdata()).most_common(1)[0][0]
    specs = [dict(t=0, r=0, b=0, l=0, tl=0, tr=0, bl=0, br=0),
             dict(t=1, r=1, b=1, l=1, tl=1, tr=1, bl=1, br=1),
             dict(t=1, r=0, b=1, l=0, tl=0, tr=0, bl=0, br=0),
             dict(t=0, r=1, b=0, l=1, tl=0, tr=0, bl=0, br=0),
             dict(t=1, r=1, b=1, l=1, tl=0, tr=0, bl=0, br=0)]
    sheet = Image.new("RGBA", (16 * len(specs), 16))
    for i, c in enumerate(specs):
        tile = base.copy()
        px = tile.load()
        for y in range(16):
            for x in range(16):
                top, bot, left, right = y < RING, y >= 16 - RING, x < RING, x >= 16 - RING
                if not (top or bot or left or right):
                    continue
                h, v = top or bot, left or right
                if h and v:
                    # a corner: its horizontal edge (top/bottom) has a border unless the vertical neighbour continues it, and
                    # its vertical edge (left/right) unless the horizontal neighbour does
                    vert = c["t"] if top else c["b"]
                    horz = c["l"] if left else c["r"]
                    diag = c[("t" if top else "b") + ("l" if left else "r")]
                    edge_h, edge_v = not vert, not horz
                    if edge_h and edge_v:
                        continue                                          # an outer corner: as drawn
                    if edge_h:                                            # only the top/bottom border continues across: extend it
                        px[x, y] = base.getpixel((min(max(x, RING), 15 - RING), y))
                    elif edge_v:                                          # only the left/right border continues across
                        px[x, y] = base.getpixel((x, min(max(y, RING), 15 - RING)))
                    elif diag:
                        px[x, y] = bg                                     # surrounded: no border
                    continue                                              # surrounded but the diagonal is missing: an inner corner, as drawn
                border = not (c["t"] if top else c["b"]) if h else not (c["l"] if left else c["r"])
                if not border:
                    px[x, y] = bg
        sheet.paste(tile, (16 * i, 0))
    return sheet


def write_fusion(preview=False):
    base = Image.open(TEX_TOP)
    sheet = compose_pieced(base)
    os.makedirs(OVERRIDES, exist_ok=True)
    sheet.save(os.path.join(OVERRIDES, "boost_top.png"))
    dump(os.path.join(OVERRIDES, "boost_top.png.mcmeta"), {"fusion": {
        "type": "connecting", "layout": "pieced", "connections": {"type": "is_same_state"}}})
    # the mod's own pack.mcmeta: tells Fusion where the optional files are (it reads it for every mod). The formats cover resources
    # (34) and data (48) of 1.21.1 so the mod's pack is never flagged
    dump(os.path.join(RES, "pack.mcmeta"), {
        "pack": {"description": "Crash Team Racing Craft", "pack_format": 34,
                 "supported_formats": {"min_inclusive": 34, "max_inclusive": 48}},
        "fusion": {"overrides_folder": "fusion-overrides"}})
    if preview:
        draw_preview(sheet)


def draw_preview(sheet):
    """A sample field of same-state pads put together the way Fusion would (quarters from the pieced tiles), for a look."""
    cells = {(x, z) for x in range(1, 5) for z in range(1, 4)} | {(5, 2), (6, 2), (6, 3), (1, 5)}
    scale, size = 6, 16
    img = Image.new("RGBA", (8 * size, 7 * size), (110, 150, 90, 255))
    for (x, z) in cells:
        has = lambda dx, dz: (x + dx, z + dz) in cells
        for qx in (0, 1):
            for qz in (0, 1):
                dx, dz = (-1 if qx == 0 else 1), (-1 if qz == 0 else 1)
                idx = (1 if has(dx, 0) else 0) | ((1 if has(0, dz) else 0) << 1) | ((1 if has(dx, dz) else 0) << 2)
                tile = PIECED_CORNER_TILE[idx]
                piece = sheet.crop((tile * 16 + qx * 8, qz * 8, tile * 16 + qx * 8 + 8, qz * 8 + 8))
                img.paste(piece, (x * size + qx * 8, z * size + qz * 8))
    os.makedirs("build", exist_ok=True)
    img.resize((img.width * scale, img.height * scale), Image.NEAREST).save(os.path.join("build", "boost_connect_preview.png"))
    print("preview: build/boost_connect_preview.png")


def main():
    ensure_sources()
    models = []                                           # every model file written, to check the block states against

    # ---- plate, block, slab: one model each, the block state turns it
    for name in ("boost_pad", "boost_block", "boost_slab_bottom", "boost_slab_top"):
        m = read(name)
        write_model(name, turned(bbmodel.elements(m), 0))
        models.append(name)
    disp = read("boost_block")["display"] or display()
    write_item("boost_pad", "boost_pad", disp)
    write_item("boost_block", "boost_block", disp)
    write_item("boost_slab", "boost_slab_bottom", disp)

    def ys(facing):
        return {} if ANGLE[facing] == 0 else {"y": ANGLE[facing]}

    dump(os.path.join(ASSETS, "blockstates", "boost_pad.json"),
         {"variants": {"facing=%s" % f: dict({"model": f"{NS}:block/boost_pad"}, **ys(f)) for f in FACINGS}})
    dump(os.path.join(ASSETS, "blockstates", "boost_block.json"),
         {"variants": {"facing=%s" % f: dict({"model": f"{NS}:block/boost_block"}, **ys(f)) for f in FACINGS}})
    slab = {}
    for f in FACINGS:
        slab["type=bottom,facing=%s" % f] = dict({"model": f"{NS}:block/boost_slab_bottom"}, **ys(f))
        slab["type=top,facing=%s" % f] = dict({"model": f"{NS}:block/boost_slab_top"}, **ys(f))
        slab["type=double,facing=%s" % f] = dict({"model": f"{NS}:block/boost_block"}, **ys(f))
    dump(os.path.join(ASSETS, "blockstates", "boost_slab.json"), {"variants": slab})

    # ---- stairs: a model per shape, half and arrow direction in the model's own frame (0/90/180/270)
    for shape in ("straight", "inner", "outer"):
        for half in ("bottom", "top"):
            els = bbmodel.elements(read("boost_stairs_%s_%s" % (shape, half)))
            for r in (0, 90, 180, 270):
                name = "boost_stairs_%s_%s_%d" % (shape, half, r)
                write_model(name, turned(els, r))
                models.append(name)
    write_item("boost_stairs", "boost_stairs_straight_bottom_90", disp)
    stairs = {}
    for (facing, half, shape), (kind, y) in STAIR_TABLE.items():
        for direction, offset in STAIR_DIRECTIONS.items():
            world = (ANGLE[facing] + offset) % 360          # where the arrow points in the world
            r = (world - y) % 360                           # and in the model's own frame (the block state turns it by y)
            v = {"model": f"{NS}:block/boost_stairs_{kind}_{half}_{r}"}
            if y:
                v["y"] = y
            stairs["direction=%s,facing=%s,half=%s,shape=%s" % (direction, facing, half, shape)] = v
    dump(os.path.join(ASSETS, "blockstates", "boost_stairs.json"), {"variants": stairs})
    assert len(stairs) == 160

    # ---- loot tables: each drops itself (a double slab two)
    def loot(block, extra=None):
        entry = {"type": "minecraft:item", "name": f"{NS}:{block}"}
        if extra:
            entry["functions"] = extra
        dump(os.path.join(DATA, NS, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry],
                                                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for b in ("boost_pad", "boost_block", "boost_stairs"):
        loot(b)
    loot("boost_slab", [{"function": "minecraft:set_count", "count": 2.0, "add": False, "conditions": [{
        "condition": "minecraft:block_state_property", "block": f"{NS}:boost_slab", "properties": {"type": "double"}}]}])

    # ---- tags
    blocks = ["boost_pad", "boost_block", "boost_slab", "boost_stairs"]
    ours = [f"{NS}:{b}" for b in blocks]
    def tag(kind, ns, path, values):
        dump(os.path.join(DATA, ns, "tags", kind, path + ".json"), {"replace": False, "values": values})
    tag("block", NS, "turbo_pad", ours)
    tag("block", "minecraft", "slabs", [f"{NS}:boost_slab"])
    tag("block", "minecraft", "stairs", [f"{NS}:boost_stairs"])
    tag("block", "minecraft", "mineable/pickaxe", ours)
    tag("item", "minecraft", "slabs", [f"{NS}:boost_slab"])
    tag("item", "minecraft", "stairs", [f"{NS}:boost_stairs"])
    # Create's copycats accept these as materials (a copycat of a boost block boosts, see McBlockSource)
    tag("block", "create", "copycat_allow", [o for o in ours if not o.endswith("boost_pad")])

    # ---- names
    lp = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(lp, encoding="utf-8")) if os.path.exists(lp) else {}
    lang[f"block.{NS}.boost_pad"] = "Boost Pad"
    lang[f"block.{NS}.boost_block"] = "Boost Block"
    lang[f"block.{NS}.boost_slab"] = "Boost Slab"
    lang[f"block.{NS}.boost_stairs"] = "Boost Stairs"
    dump(lp, lang)

    write_fusion(preview="--preview" in sys.argv)

    # ---- check every model a block state names was written
    for fn in ("boost_pad", "boost_block", "boost_slab", "boost_stairs"):
        states = json.load(open(os.path.join(ASSETS, "blockstates", fn + ".json"), encoding="utf-8"))["variants"]
        for k, v in states.items():
            name = v["model"].split("/")[-1]
            assert os.path.exists(os.path.join(ASSETS, "models", "block", name + ".json")), (fn, k, name)
    print("boost blocks written: %d models, %d stair variants" % (len(models), len(stairs)))


if __name__ == "__main__":
    main()
