"""
The Bomb: the jar's stand-in 3D model, its texture, its spark and where the spark is.

  python tools/make_bomb_model.py

The model is Blockbench's: blockbench/pickups/bomb.bbmodel is the source (written once from the shape below if it is missing, then it is yours; this script only reads it). Writes
src/main/resources/assets/ctrcraft/models/item/pickup_bomb.json (the renderer fetches that model; the local resource pack's original bomb replaces it) and, once if missing,
textures/item/bomb.png (a 16 x 16 palette of flat 4 x 4 cells, yours to repaint), textures/entity/bomb_spark.png (the glow at the tip of the fuse) and bomb/bomb_fuse.json
({tip: [x, y, z] in blocks from the ball's middle, spark_size, ground: the ball's radius}).

The ball is a sphere approximated as five octagonal prisms along x (the axle it rolls about), narrowing to the poles, each made of four flat slabs, 0, 90 and +-45 degrees (the
only angles a block model takes): a regular octagon is the union of four rectangles as long as it is wide between flats and 0.414 of that across, at 45 degree steps. Their caps
are put a hair apart so they do not fight. The model's middle (8, 8, 8) is the ball's middle; the front is north (-z); the fuse leaves the -x cap towards the back, as the original's leaves its ball.
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "ctrcraft")
TEX = os.path.join(ASSETS, "textures", "item", "bomb.png")
SPARK = os.path.join(ASSETS, "textures", "entity", "bomb_spark.png")
FUSE = os.path.join(ASSETS, "bomb", "bomb_fuse.json")
OUT = os.path.join(ASSETS, "models", "item", "pickup_bomb.json")
BB = os.path.join("blockbench", "pickups", "bomb.bbmodel")

PALETTE = {
    "ball": (0, 0, (34, 36, 64)),
    "band": (4, 0, (70, 72, 104)),
    "white": (8, 0, (236, 236, 236)),
    "fuse": (12, 0, (150, 124, 78)),
}
D = 12.0                    # the ball's width between flats, pixels
S = D * (math.sqrt(2.0) - 1.0)           # 0.414 D: the side of the octagon
X0, X1 = 3.0, 13.0          # the ball's length along the axle
TIP = (-0.55, 0.09, 0.31)    # the fuse's tip, blocks from the ball's middle
SPARK_SIZE = 0.8
GROUND = 0.4                # the ball's radius (blocks)


def draw_texture():
    im = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    for _, (cx, cy, rgb) in PALETTE.items():
        for y in range(cy, cy + 4):
            for x in range(cx, cx + 4):
                im.putpixel((x, y), rgb + (255,))
    return im


def draw_spark():
    n = 32
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - n / 2, y + 0.5 - n / 2) / (n / 2)
            star = max(0.0, 1.0 - d) ** 1.6 + max(0.0, 1.0 - min(abs(x + 0.5 - n / 2), abs(y + 0.5 - n / 2)) / 1.6) * max(0.0, 1.0 - d) * 0.7
            a = max(0.0, min(1.0, star))
            im.putpixel((x, y), (255, int(120 + 135 * a), int(30 + 150 * a * a), int(255 * a)))
    return im


def box(frm, to, colour, rotation=None):
    cx, cy, _ = PALETTE[colour]
    face = {"uv": [cx + 1, cy + 1, cx + 3, cy + 3], "texture": "#bomb"}
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {f: dict(face) for f in bbmodel.FACES}}
    if rotation:
        el["rotation"] = {"origin": [8, 8, 8], "axis": "x", "angle": rotation}
    return el


def parts():
    """The ball is a sphere approximated as a lathe along the axle: five octagonal prisms (each four slabs at 0, 90 and +-45 degrees) whose widths follow a circle, so it
    reads round from every side. The fuse leaves the -x pole off the axle (the original's is 35 units off it) and steps up and out, so it stands at a slant instead of straight."""
    eps = 0.02
    # (x from, x to, width between flats): a circle of 12 px across, x from 2 to 14
    rings = [(2.0, 3.3, 6.0), (3.3, 5.0, 10.0), (5.0, 11.0, 12.0), (11.0, 12.7, 10.0), (12.7, 14.0, 6.0)]
    slabs = []
    for n, (x0, x1, d) in enumerate(rings):
        h, s_ = d / 2.0, d * (math.sqrt(2.0) - 1.0) / 2.0
        k = n * 4 * eps
        slabs += [
            box([x0, 8 - h, 8 - s_], [x1, 8 + h, 8 + s_], "ball"),
            box([x0 + eps, 8 - s_, 8 - h], [x1 - eps, 8 + s_, 8 + h], "ball"),
            box([x0 + 2 * eps, 8 - h, 8 - s_], [x1 - 2 * eps, 8 + h, 8 + s_], "band", rotation=45),
            box([x0 + 3 * eps, 8 - h, 8 - s_], [x1 - 3 * eps, 8 + h, 8 + s_], "band", rotation=-45),
        ]
    skulls = [box([2.0 - 0.06, 8 - 2.4, 8 - 1.9], [2.0 + 0.01, 8 + 2.4, 8 + 1.9], "white"), box([14.0 - 0.01, 8 - 2.4, 8 - 1.9], [14.0 + 0.06, 8 + 2.4, 8 + 1.9], "white")]
    # the fuse: from the ball's surface (5 px off the axle, where the ball is 10 across) out along -x, then a step up: the tip is (TIP)
    tx = 8 + TIP[0] * 16
    ty = 8 + TIP[1] * 16
    tz = 8 + TIP[2] * 16
    fuse = [box([2.4, 8 - 0.4, tz - 0.4], [4.4, 8 + 0.4, tz + 0.4], "fuse"),
            box([(tx + 2.4) / 2.0, 8 - 0.4 + (ty - 8) / 2.0, tz - 0.4], [2.4, 8 + 0.4 + (ty - 8) / 2.0, tz + 0.4], "fuse"),
            box([tx, ty - 0.4, tz - 0.4], [(tx + 2.4) / 2.0, ty + 0.4, tz + 0.4], "fuse")]
    return {"ball": slabs, "skulls": skulls, "fuse": fuse}


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.5, 0.5, 0.5]}, "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.5, 0.5, 0.5]}}


def main():
    for path, make in ((TEX, draw_texture), (SPARK, draw_spark)):
        if not os.path.exists(path):
            os.makedirs(os.path.dirname(path), exist_ok=True)
            make().save(path)
    if not os.path.exists(FUSE):
        os.makedirs(os.path.dirname(FUSE), exist_ok=True)
        with open(FUSE, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"tip": list(TIP), "spark_size": SPARK_SIZE, "ground": GROUND}, f, indent=2)
    if not os.path.exists(BB):
        bbmodel.write(BB, "bomb", {"bomb": TEX}, list(parts().items()), display=display(), gui_light_front=False,
                      comment="Stand-in Bomb: an octagonal prism along x (the axle it rolls about) from four slabs (0, 90, +-45 degrees), the model's middle (8, 8, 8) is the ball's middle. "
                              "The fuse leaves the -x cap; the spark is a billboard drawn by the game at the fuse's tip (bomb/bomb_fuse.json), not part of this model.")
    model = bbmodel.read(BB)
    els = bbmodel.elements(model)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": {"bomb": "ctrcraft:item/bomb", "particle": "ctrcraft:item/bomb"}, "elements": els, "display": model["display"]}, f, indent=2)
    print("bomb written")


if __name__ == "__main__":
    main()
