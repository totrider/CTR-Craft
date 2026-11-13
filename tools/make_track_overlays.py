"""
The flat sprites drawn on the ground for a race track: the start grid's kart spots and the gathering area behind the Start node.

  python tools/make_track_overlays.py

Writes textures/misc/grid_slot.png (one kart's spot: an outline with an arrow pointing the way the kart faces, UP in the picture is
forward) and textures/misc/gather_zone.png (the square gathering area: a dashed border, corner marks and a chevron pattern pointing at the
start line, UP is toward the line). They are cutouts (pixels are fully drawn or not at all). Placeholder art: a file that exists is yours and is
not overwritten, so repaint them. Run from the project folder.
"""
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures", "misc")


def grid_slot():
    # 16 px per block: a spot is 2 blocks wide and 2.5 long
    w, h = 32, 40
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    white = (245, 245, 245, 255)
    gold = (255, 210, 31, 255)
    d.rectangle([0, 0, w - 1, h - 1], outline=white)
    d.rectangle([1, 1, w - 2, h - 2], outline=white)
    # arrow: forward is up
    d.polygon([(16, 7), (24, 18), (19, 18), (19, 30), (13, 30), (13, 18), (8, 18)], fill=gold, outline=(40, 30, 10, 255))
    return im


def gather_zone():
    # 8 px per block: 16 x 16 blocks
    n = 128
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    green = (70, 235, 120, 255)
    dark = (20, 90, 50, 255)
    # dashed border, 3 px wide, dashes of 6 px
    for i in range(0, n, 12):
        for t in range(3):
            for (x, y) in ((i, t), (i + 1, t), (i + 2, t), (i + 3, t), (i + 4, t), (i + 5, t)):
                for (px, py) in ((x, y), (y, x), (x, n - 1 - y), (n - 1 - y, x)):
                    if 0 <= px < n and 0 <= py < n:
                        im.putpixel((px, py), green)
    # solid corner brackets
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (n - 1, 0, -1, 1), (0, n - 1, 1, -1), (n - 1, n - 1, -1, -1)):
        for k in range(18):
            for t in range(5):
                im.putpixel((cx + sx * k, cy + sy * t), green)
                im.putpixel((cx + sx * t, cy + sy * k), green)
    # chevrons pointing at the line (up), a few of them down the middle
    for row in range(3):
        y0 = 30 + row * 28
        d.line([(n // 2 - 14, y0 + 12), (n // 2, y0), (n // 2 + 14, y0 + 12)], fill=green, width=3)
        d.line([(n // 2 - 14, y0 + 14), (n // 2, y0 + 2), (n // 2 + 14, y0 + 14)], fill=dark, width=1)
    return im


def main():
    os.makedirs(ASSETS, exist_ok=True)
    for name, make in (("grid_slot", grid_slot), ("gather_zone", gather_zone)):
        path = os.path.join(ASSETS, name + ".png")
        if not os.path.exists(path):
            make().save(path)
    print("track overlay sprites ok")


if __name__ == "__main__":
    main()
