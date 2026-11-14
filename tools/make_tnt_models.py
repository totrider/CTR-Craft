"""
The TNT power-up's crate and the juiced one (the "Nitro", here creeper themed): the jar's stand-in models.

  python tools/make_tnt_models.py

The TNT keeps the Minecraft TNT block's own textures (as decided with the author) and gets what makes it a power-up and not a placed block: a steel frame, a rivetted corner
block at each of the eight corners and a strap along each of the twelve edges. The juiced one is a creeper crate: green with a creeper's face on its four sides and the same steel.
The models are Blockbench's: blockbench/pickups/tnt.bbmodel and nitro.bbmodel are the source (written once from the shapes below if missing, then they are yours; this script only
reads them). Writes models/item/pickup_tnt.json and pickup_nitro.json, and draws once if missing (yours to repaint) textures/item/tnt_metal.png (16 x 16: the steel plate, the
top left 4 x 4 is what the frame's pieces show), creeper_face.png and creeper_skin.png (16 x 16). Origin: the bottom centre of the block; 16 px is one block.
"""
import json
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "ctrcraft")
TEX = os.path.join(ASSETS, "textures", "item")
BB = os.path.join("blockbench", "pickups")


def draw_metal():
    """Brushed steel with a darker rim and a rivet; the frame pieces use its top left 4 x 4."""
    rnd = random.Random(7)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            v = 150 + rnd.randint(-8, 8) + (6 if y % 4 == 1 else 0)
            im.putpixel((x, y), (v, v + 4, v + 14, 255))
    for i in range(4):
        for x, y in ((i, 0), (i, 3), (0, i), (3, i)):
            c = im.getpixel((x, y))
            im.putpixel((x, y), (c[0] - 55, c[1] - 55, c[2] - 50, 255))
    for x, y in ((1, 1), (2, 1), (1, 2), (2, 2)):
        im.putpixel((x, y), (210, 214, 226, 255) if (x, y) == (1, 1) else (96, 100, 112, 255))
    return im


def draw_creeper():
    rnd = random.Random(11)
    face = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    skin = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            g = rnd.choice(((86, 170, 66), (70, 150, 55), (104, 188, 80), (58, 130, 48), (92, 176, 72)))
            face.putpixel((x, y), g + (255,))
            skin.putpixel((x, y), rnd.choice(((86, 170, 66), (70, 150, 55), (104, 188, 80), (58, 130, 48), (92, 176, 72))) + (255,))
    black = (18, 24, 18, 255)
    for x0, y0, x1, y1 in ((3, 4, 6, 7), (10, 4, 13, 7), (6, 7, 10, 10), (5, 10, 7, 13), (9, 10, 11, 13)):
        for y in range(y0, y1):
            for x in range(x0, x1):
                face.putpixel((x, y), black)
    return face, skin


def box(frm, to, faces):
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}


def metal_faces(slot="#metal"):
    f = {"uv": [0, 0, 4, 4], "texture": slot}
    return {d: dict(f) for d in bbmodel.FACES}


def frame():
    """Eight corner blocks (4.5 px, a little proud of the crate) and twelve straps between them, in steel."""
    lo, hi = (-0.4, 4.1), (11.9, 16.4)
    out = []
    for x in lo + hi if False else (lo, hi):
        for y in (lo, hi):
            for z in (lo, hi):
                out.append(box((x[0], y[0], z[0]), (x[1], y[1], z[1]), metal_faces()))
    s = (-0.3, 0.9)
    t = (15.1, 16.3)
    mid = (4.1, 11.9)
    for a in (s, t):
        for b in (s, t):
            out.append(box((mid[0], a[0], b[0]), (mid[1], a[1], b[1]), metal_faces()))     # along x
            out.append(box((a[0], mid[0], b[0]), (a[1], mid[1], b[1]), metal_faces()))     # along y
            out.append(box((a[0], b[0], mid[0]), (a[1], b[1], mid[1]), metal_faces()))     # along z
    return out


def body_tnt():
    side = lambda d: {"uv": [0, 0, 16, 16], "texture": d}
    return box((0, 0, 0), (16, 16, 16), {"north": side("#side"), "south": side("#side"), "east": side("#side"), "west": side("#side"), "up": side("#top"), "down": side("#bottom")})


def body_creeper():
    side = lambda d: {"uv": [0, 0, 16, 16], "texture": d}
    return box((0, 0, 0), (16, 16, 16), {"north": side("#face"), "south": side("#face"), "east": side("#face"), "west": side("#face"), "up": side("#skin"), "down": side("#skin")})


def display():
    return {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def main():
    os.makedirs(TEX, exist_ok=True)
    metal, face, skin = (os.path.join(TEX, n + ".png") for n in ("tnt_metal", "creeper_face", "creeper_skin"))
    if not os.path.exists(metal):
        draw_metal().save(metal)
    if not os.path.exists(face) or not os.path.exists(skin):
        f, k = draw_creeper()
        f.save(face)
        k.save(skin)
    os.makedirs(BB, exist_ok=True)
    jobs = {
        "tnt": ({"side": None, "top": None, "bottom": None, "metal": metal}, [("body", [body_tnt()]), ("frame", frame())],
                {"side": "minecraft:block/tnt_side", "top": "minecraft:block/tnt_top", "bottom": "minecraft:block/tnt_bottom", "metal": "ctrcraft:item/tnt_metal", "particle": "ctrcraft:item/tnt_metal"}),
        "nitro": ({"face": face, "skin": skin, "metal": metal}, [("body", [body_creeper()]), ("frame", frame())],
                  {"face": "ctrcraft:item/creeper_face", "skin": "ctrcraft:item/creeper_skin", "metal": "ctrcraft:item/tnt_metal", "particle": "ctrcraft:item/creeper_skin"}),
    }
    for name, (slots, groups, textures) in jobs.items():
        path = os.path.join(BB, name + ".bbmodel")
        if not os.path.exists(path):
            bbmodel.write(path, name, slots, groups, display=display(), gui_light_front=False,
                          comment="Stand-in %s crate: a 16 px cube (group body) in a steel frame (group frame: eight corner blocks and twelve straps, a little proud of the cube, "
                                  "all showing the top left 4 x 4 of the steel texture). See tools/make_tnt_models.py." % name)
        model = bbmodel.read(path)
        out = os.path.join(ASSETS, "models", "item", "pickup_" + name + ".json")
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"textures": textures, "elements": bbmodel.elements(model), "display": model["display"] or display()}, f, indent=2)
    print("tnt models written")


if __name__ == "__main__":
    main()
