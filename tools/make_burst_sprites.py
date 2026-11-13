"""
The explosion starburst and the shine behind the wumpa fruit and held power-up at ten wumpa. Stand-in art, drawn here: a spiky white-grey burst, white in the middle and
darker towards the tips, which the code tints (the bomb's burst: yellow to red).

  python tools/make_burst_sprites.py

Writes textures/gui/glow.png (48 x 32: one QUARTER of the burst, its centre in the bottom right corner, as the original's picture is; ItemHud draws it four times mirrored with the game's colours) and textures/particle/burst.png (96 x 96, the same burst
centred on a square, as particles are square). A file that exists is yours and is not overwritten (delete it to have it drawn again). The original's burst picture (the
shared pack's `poweredup` icon) is made into the same two files, in a local resource pack, by tools/extract_ctr_textures.py. Run from the project folder.
"""
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures")
W, H = 96, 64
SS = 4


def burst():
    rnd = random.Random(1996)
    n = W * SS, H * SS
    cx, cy = n[0] / 2, n[1] / 2
    im = Image.new("RGBA", n, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    spikes = 14
    for layer, (scale, grey) in enumerate(((1.0, 120), (0.82, 175), (0.55, 225), (0.3, 255))):
        pts = []
        for i in range(spikes * 2):
            a = math.pi * 2 * i / (spikes * 2) + (0.15 if layer == 0 else 0.0)
            outer = i % 2 == 0
            r = (1.0 if outer else 0.55) * (0.8 + 0.2 * rnd.random()) * scale
            pts.append((cx + math.cos(a) * r * cx, cy + math.sin(a) * r * cy))
        d.polygon(pts, fill=(grey, grey, grey, 255))
    return im.resize((W, H), Image.LANCZOS)


def main():
    glow = os.path.join(ROOT, "gui", "glow.png")
    part = os.path.join(ROOT, "particle", "burst.png")
    img = burst()
    for path, make in ((glow, lambda: original_grey(img.crop((0, 0, W // 2, H // 2)))), (part, lambda: pad(img))):
        if os.path.exists(path):
            print("exists, left alone:", path)
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        make().save(path)
        print("wrote", path)


def original_grey(quarter):
    """The shine's picture holds the original's grey, which tops out at 189 of 255 (the game modulates it by colour / 128): scale the stand-in's to that."""
    import numpy as np
    a = np.array(quarter)
    a[..., :3] = (a[..., :3].astype(np.float64) * 189 / 255).astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def pad(img):
    sq = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    sq.alpha_composite(img, (0, (W - H) // 2))
    return sq


if __name__ == "__main__":
    main()
