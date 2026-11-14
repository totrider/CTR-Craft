"""
The boost flame's stand-in model for the jar (the original's eight flame models are in the local resource pack, as item/pickup_boost_flame_0 .. 7).

  python tools/make_boost_flame.py

blockbench/kart/boost_flame.bbmodel is the source (written once from the shape below if missing, then it is yours; this script only reads it) and
models/item/pickup_boost_flame_0..7.json (eight copies: the original's flame is eight pictures of one shape, so the pack can give each its own) are generated from it.
The texture textures/item/boost_flame.png (16 x 16, written once if missing) is a soft yellow-to-orange gradient, brightest at the root; its alpha fades towards the tip.

The model's middle (8, 8, 8) is the root of the flame, where the exhaust pipe ends; the flame streams towards +z (the back of the kart, whose front is -z) 14 pixels long.
The renderer scales it by the flame's size and mirrors it for the right pipe.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import make_kart as base  # noqa: E402

BB = os.path.join("blockbench", "kart", "boost_flame.bbmodel")
TEX = os.path.join(base.ASSETS, "textures", "item", "boost_flame.png")


def draw_texture():
    im = np.zeros((16, 16, 4), dtype=np.uint8)
    for y in range(16):
        t = y / 15.0
        im[y, :] = (255, int(235 - 120 * t), int(150 - 130 * t), int(255 * (1.0 - 0.55 * t)))
    return Image.fromarray(im, "RGBA")


def box(frm, to, angle=0):
    face = {"uv": [0, 0, 16, 16], "texture": "#flame"}
    el = {"from": frm, "to": to, "faces": {f: dict(face) for f in bbmodel.FACES}}
    if angle:
        el["rotation"] = {"origin": [8, 8, 8], "axis": "x", "angle": angle}
    return el


def parts():
    # a cone of stacked boxes: root (4 across) to tip (1 across), along +z, the whole cone turned 45 degrees about x round its root (8, 8, 8): it leaves the root back and DOWN in this
    # model, and the game draws the model turned over (y negated), so on the kart it streams back and UP, the way the exhaust pipes point
    cuts = [(8.0, 11.5, 4.0), (11.5, 15.0, 3.0), (15.0, 18.5, 2.0), (18.5, 22.0, 1.0)]
    return {"flame": [box([8 - w / 2, 8 - w / 2, z0], [8 + w / 2, 8 + w / 2, z1], 45) for z0, z1, w in cuts]}


def main():
    if not os.path.exists(TEX):
        os.makedirs(os.path.dirname(TEX), exist_ok=True)
        draw_texture().save(TEX)
    if not os.path.exists(BB):
        bbmodel.write(BB, "boost_flame", {"flame": TEX}, list(parts().items()), display=base.display(), gui_light_front=False,
                      comment="The boost flame's stand-in: a cone of stacked boxes streaming towards +z (the back of the kart) from its root at (8, 8, 8), the end of the exhaust pipe. "
                              "The game draws one per pipe, scaled by the flame's size, the right one mirrored, additive and bright. Texture slot 'flame'.")
    model = bbmodel.read(BB)
    out_dir = os.path.join(base.ASSETS, "models", "item")
    os.makedirs(out_dir, exist_ok=True)
    data = {"textures": {"flame": base.NS + ":item/boost_flame", "particle": base.NS + ":item/boost_flame"}, "elements": bbmodel.elements(model), "display": model["display"]}
    for i in range(8):
        with open(os.path.join(out_dir, "pickup_boost_flame_%d.json" % i), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
    # the pack's flame has a second model per frame, the shell that the game draws subtractively; the jar has none (an empty model)
    empty = {"textures": {"particle": base.NS + ":item/boost_flame"}, "elements": []}
    for i in range(8):
        with open(os.path.join(out_dir, "pickup_boost_flame_sub_%d.json" % i), "w", encoding="utf-8", newline="\n") as f:
            json.dump(empty, f, indent=2)
    print("boost flame written")


if __name__ == "__main__":
    main()
