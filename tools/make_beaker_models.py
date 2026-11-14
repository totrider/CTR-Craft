"""
N. Brio's Beaker, the stand-in 3D model: a round-bottomed flask with a neck and a lip, one green and one red (the juiced beaker), built from boxes.

  python tools/make_beaker_models.py

The models are Blockbench's: blockbench/pickups/beaker_green.bbmodel and beaker_red.bbmodel are the source (written once from the shapes below if missing, then they
are yours; this script only reads them). Writes src/main/resources/assets/ctrcraft/models/item/pickup_beaker_green.json and pickup_beaker_red.json (the beaker entity
renderer fetches these baked models), and draws the placeholder texture textures/item/beaker.png once if it is missing (yours to repaint).

The shape follows the original's flask in outline: a bulb (a stack of boxes narrowing at the bottom and the shoulder, so it reads as round), a narrow neck and a lip. One
model is one block tall at most (16 model px); the renderer scales it to the entity (0.58 blocks tall). The texture is four cells by four of 4 px: the top two rows are the
green and red palettes (liquid, shade, light, glass), the third row a cork and an outline. Origin: the bottom centre of the block.
The original's beakers (BEAKERGREEN, BEAKERRED in the shared model pack) are 40 triangles, six of which are the bubbles of a 15 frame animation: they replace these models in a
local resource pack (docs/POWERUPS.md). The bubbles are billboards (client/BeakerBubbles): textures/entity/beaker_<colour>_bubbles.png and beaker/beaker_<colour>_bubbles.json are
the generic ones, written once here and yours to edit; the pack's override them.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ctrcraft")
TEX = os.path.join(ASSETS, "textures", "item", "beaker.png")
BB = os.path.join("blockbench", "pickups")

# cell (column, row) -> rgb; every cell is 4 x 4 texels of the 16 x 16 sheet
PALETTE = {
    "green": ((0, 0), (62, 200, 60)), "green_dark": ((1, 0), (30, 130, 45)), "green_light": ((2, 0), (160, 240, 120)), "green_glass": ((3, 0), (205, 242, 205)),
    "red": ((0, 1), (222, 52, 40)), "red_dark": ((1, 1), (140, 24, 26)), "red_light": ((2, 1), (255, 135, 112)), "red_glass": ((3, 1), (246, 208, 208)),
    "cork": ((0, 2), (156, 104, 56)), "outline": ((1, 2), (40, 52, 48)),
}


def alpha_of(name):
    """The glass and the liquid are see-through (as the original's beaker is, every texel of it), so the bubbles show through; the lip's cork and outline cells are solid."""
    return 150 if name.endswith("_glass") else 205 if name in ("green", "green_dark", "green_light", "red", "red_dark", "red_light") else 255


def draw_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for name, ((cx, cy), rgb) in PALETTE.items():
        im.paste(Image.new("RGBA", (4, 4), rgb + (alpha_of(name),)), (cx * 4, cy * 4))
    return im


def cell(name):
    (cx, cy), _ = PALETTE[name]
    return [cx * 4 + 0.25, cy * 4 + 0.25, cx * 4 + 3.75, cy * 4 + 3.75]


def box(frm, to, side, top, bottom, name=None):
    faces = {}
    for f in ("north", "east", "south", "west"):
        faces[f] = {"uv": cell(side), "texture": "#beaker"}
    faces["up"] = {"uv": cell(top), "texture": "#beaker"}
    faces["down"] = {"uv": cell(bottom), "texture": "#beaker"}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}


def flask(colour):
    """Groups of boxes: the bulb (liquid), the neck and the lip (glass)."""
    liquid, dark, light, glass = colour, colour + "_dark", colour + "_light", colour + "_glass"
    bulb = [
        box((4.5, 0.0, 4.5), (11.5, 1.5, 11.5), liquid, dark, dark),          # the base, narrower: a rounded bottom
        box((3.0, 1.5, 3.0), (13.0, 7.5, 13.0), liquid, light, dark),          # the widest part
        box((4.0, 7.5, 4.0), (12.0, 9.0, 12.0), liquid, light, dark),          # the shoulder
        box((5.5, 9.0, 5.5), (10.5, 10.5, 10.5), glass, light, dark),          # into the neck
    ]
    neck = [box((6.5, 10.5, 6.5), (9.5, 13.5, 9.5), glass, light, dark)]
    lip = [box((5.75, 13.5, 5.75), (10.25, 14.75, 10.25), glass, light, dark)]
    return [("bulb", bulb), ("neck", neck), ("lip", lip)]


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def models():
    return {"beaker_green": flask("green"), "beaker_red": flask("red")}


BUBBLE_TEX = os.path.join(ASSETS, "textures", "entity", "beaker_%s_bubbles.png")
BUBBLE_DATA = os.path.join(ASSETS, "beaker", "beaker_%s_bubbles.json")
FRAMES = 15
# six bubbles: (rise phase in frames, x, z, size in blocks, bright?)
BUBBLES = [(0, -0.04, -0.02, 0.07, False), (3, 0.04, 0.0, 0.085, True), (6, 0.0, -0.04, 0.095, False), (8, 0.03, -0.02, 0.085, True), (11, -0.05, 0.0, 0.07, False), (13, 0.0, 0.04, 0.08, True)]


def draw_bubbles(colour):
    """The bubble sheet: a dark bubble and a bright glossy one side by side (18 x 18 texels each), in the beaker's colour."""
    liquid, dark, light = PALETTE[colour][1], PALETTE[colour + "_dark"][1], PALETTE[colour + "_light"][1]
    from PIL import ImageDraw
    n = 18 * 8
    im = Image.new("RGBA", (2 * n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for cell, bright in ((0, False), (1, True)):
        ox = cell * n
        d.ellipse([ox + 8, 8, ox + n - 8, n - 8], fill=(dark if not bright else liquid) + (255,), outline=(20, 30, 20, 255), width=8)
        if bright:
            d.ellipse([ox + 30, 28, ox + 60, 56], fill=light + (255,))
            d.ellipse([ox + 94, 96, ox + 110, 112], fill=light + (255,))
        else:
            d.ellipse([ox + 30, 28, ox + 52, 48], fill=liquid + (255,))
    return im.resize((2 * 18, 18), Image.LANCZOS)


def bubble_data(colour):
    """A generic rise through the flask: each bubble climbs from the liquid up the neck and out of the lip over the fifteen frames, quickly at first (as the original's do), and starts over."""
    out = []
    for phase, x, z, size, bright in BUBBLES:
        centres = []
        for f in range(FRAMES):
            tt = ((f + phase) % FRAMES) / (FRAMES - 1.0)
            centres.append([x * (1.0 if tt < 0.55 else 0.5), round(0.14 + 0.90 * (1.0 - (1.0 - tt) ** 2), 4), z * (1.0 if tt < 0.55 else 0.5)])   # up through the neck and out past the lip (0.92 of the block) before it starts over
        out.append({"uv": [0.5, 0.0, 1.0, 1.0] if bright else [0.0, 0.0, 0.5, 1.0], "width": size, "height": size, "centres": centres})
    return {"frames": FRAMES, "texture": "ctrcraft:textures/entity/beaker_%s_bubbles.png" % colour, "bubbles": out}


def main():
    for colour in ("green", "red"):
        if not os.path.exists(BUBBLE_TEX % colour):
            os.makedirs(os.path.dirname(BUBBLE_TEX % colour), exist_ok=True)
            draw_bubbles(colour).save(BUBBLE_TEX % colour)
        if not os.path.exists(BUBBLE_DATA % colour):
            os.makedirs(os.path.dirname(BUBBLE_DATA % colour), exist_ok=True)
            with open(BUBBLE_DATA % colour, "w", encoding="utf-8", newline="\n") as f:
                json.dump(bubble_data(colour), f, indent=1)
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    if not os.path.exists(TEX):
        draw_texture().save(TEX)
    os.makedirs(BB, exist_ok=True)
    for name, groups in models().items():
        path = os.path.join(BB, name + ".bbmodel")
        if not os.path.exists(path):
            bbmodel.write(path, name, {"beaker": TEX}, groups, display=display(), gui_light_front=False,
                          comment="Stand-in N. Brio's Beaker: a round-bottomed flask from boxes (bulb, neck, lip). One group per part. The texture slot 'beaker' is "
                                  "textures/item/beaker.png (palette cells: green, red; liquid, shade, light, glass), see tools/make_beaker_models.py.")
        model = bbmodel.read(path)
        out = os.path.join(ASSETS, "models", "item", "pickup_" + name + ".json")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"textures": {"beaker": "ctrcraft:item/beaker", "particle": "ctrcraft:item/beaker"},
                       "elements": bbmodel.elements(model),
                       "display": model["display"] or display()}, f, indent=2)
    print("beaker models written")


if __name__ == "__main__":
    main()
