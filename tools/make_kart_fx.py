"""
The kart's ground shadow and its dust puffs: stand-in textures in the original game's style (the original has a dark blob under every kart at the ground, a
kart-shaped silhouette with a bump at each wheel, and dust puffs that go from a bright puff to a dark one over five pictures).

  python tools/make_kart_fx.py

Writes textures/entity/kart_shadow.png (64 x 80: a soft dark kart-shaped blob, front at the top) and textures/particle/dust_puff_0..4.png (16 x 16 each: a
rough round puff that grows and darkens from picture to picture). Placeholder art: a file that exists is yours and is not overwritten (delete one to have it
drawn again). Run from the project folder.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ASSETS = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures")


def shadow():
    w, h = 64, 80
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([16, 6, 47, 73], radius=10, fill=255)           # the body
    for y0 in (8, 52):                                                   # a bump at each wheel: two axles
        d.rounded_rectangle([5, y0, 20, y0 + 20], radius=5, fill=255)
        d.rounded_rectangle([43, y0, 58, y0 + 20], radius=5, fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(2.2))
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            a = mask.getpixel((x, y))
            if a > 12:
                px[x, y] = (0, 0, 0, int(a * 0.55))
    return im


def puff(frame):
    n = 16
    rnd = random.Random(40 + frame)
    radii = [rnd.uniform(0.78, 1.0) for _ in range(16)]
    r0 = 4.0 + frame * 0.9
    shade = int(245 - frame * 40)
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            dx, dy = x - 7.5, y - 7.5
            ang = math.atan2(dy, dx)
            r = math.hypot(dx, dy)
            edge = r0 * radii[int((ang + math.pi) / (2 * math.pi) * 16) % 16]
            if r <= edge:
                core = max(0.0, 1.0 - r / edge)
                px[x, y] = (255, 255, 255, int(235 * min(1.0, (edge - r) / 1.6 + 0.35) * (0.55 + 0.45 * core)))        # a white mask: the game colours it
    return im


def clod():
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([1, 1, 6, 6], fill=(110, 78, 50, 255), outline=(70, 48, 30, 255))
    d.point((3, 2), fill=(150, 112, 78, 255))
    d.point((4, 3), fill=(150, 112, 78, 255))
    return im


def smoke_ring():
    n = 16
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            r = math.hypot(dx, dy)
            spike = max(0.0, 1.0 - min(dx, dy) / 1.4) * max(0.0, 1.0 - max(dx, dy) / 8.0)       # the four points of the star
            glow = max(0.0, 1.0 - r / 5.0)
            v = max(spike, glow)
            if v > 0.05:
                s = int(255 * min(1.0, 0.55 + v))
                px[x, y] = (s, s, int(s * 0.85), int(255 * min(1.0, v * 1.4)))
    return im


def skid_mark():
    """A piece of tyre mark: black, soft at both sides, with a tread of short bars (u across the mark, v along it)."""
    n = 16
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            across = abs((x + 0.5) / (n / 2.0) - 1.0)
            edge = max(0.0, min(1.0, (1.0 - across) / 0.28))
            tread = 1.0 if (y // 2) % 2 == 0 else 0.78
            a = int(255 * edge * tread)
            if a > 0:
                px[x, y] = (0, 0, 0, a)
    return im


def main():
    os.makedirs(os.path.join(ASSETS, "entity"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "particle"), exist_ok=True)
    # (the kart's shadow is made from its model by tools/make_kart_shadow.py)
    for i in range(5):
        path = os.path.join(ASSETS, "particle", "dust_puff_%d.png" % i)
        if not os.path.exists(path):
            puff(i).save(path)
    path = os.path.join(ASSETS, "particle", "clod.png")
    if not os.path.exists(path):
        clod().save(path)
    path = os.path.join(ASSETS, "particle", "skid_mark.png")
    if not os.path.exists(path):
        skid_mark().save(path)
    path = os.path.join(ASSETS, "particle", "smoke_ring.png")
    if not os.path.exists(path):
        smoke_ring().save(path)
    print("kart fx textures ok")


if __name__ == "__main__":
    main()
