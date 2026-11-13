"""
The HUD pictures: the power-up icons, the Wumpa fruit and the four start lamps. Stand-in pixel art in the original game's size, proportions and style (drawn
from memory as look-alikes, not the original game's art).

  python tools/make_power_icons.py

Writes textures/gui/power/<id>.png (32 x 48, portrait, as the original's; one per power-up in com.ctrcraft.items.HeldItem), textures/gui/wumpa.png (48 x 32)
and textures/gui/lights/{red,green}_{on,off}.png (48 x 32). Placeholder art: a file that exists is yours and is not overwritten, so repaint them (delete one to
have it drawn again). Run from the project folder.
"""
import math
import os

from PIL import Image, ImageDraw

GUI = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures", "gui")

OUT = (22, 18, 30, 255)
WHITE = (245, 245, 245, 255)
GREY = (170, 174, 184, 255)
DGREY = (90, 92, 102, 255)
BLACK = (32, 30, 38, 255)
RED = (215, 55, 50, 255)
DRED = (150, 30, 30, 255)
ORANGE = (255, 140, 40, 255)
YELLOW = (255, 215, 40, 255)
GREEN = (70, 190, 70, 255)
LGREEN = (150, 235, 120, 255)
DGREEN = (35, 120, 50, 255)
BLUE = (60, 120, 235, 255)
DBLUE = (30, 50, 150, 255)
LBLUE = (140, 200, 255, 255)
BROWN = (150, 100, 55, 255)
DBROWN = (105, 70, 40, 255)
GOLD = (225, 170, 40, 255)
DGOLD = (150, 100, 20, 255)


def canvas(w=32, h=48):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def gloss(d, box, colour=WHITE):
    x0, y0, x1, y1 = box
    d.ellipse([x0, y0, x0 + max(2, (x1 - x0) // 3), y0 + max(2, (y1 - y0) // 3)], fill=colour)


def turbo():
    """A nitro canister: red body, blue cap with a gauge."""
    im, d = canvas()
    d.rounded_rectangle([8, 18, 24, 44], radius=4, fill=RED, outline=OUT)
    d.rectangle([8, 28, 24, 31], fill=DRED)
    d.rectangle([10, 22, 12, 40], fill=(245, 120, 110, 255))
    d.rounded_rectangle([9, 8, 23, 20], radius=3, fill=BLUE, outline=OUT)
    d.ellipse([12, 9, 20, 17], fill=YELLOW, outline=OUT)
    d.line([(16, 13), (19, 11)], fill=OUT)
    d.rectangle([14, 3, 18, 8], fill=GREY, outline=OUT)
    return im


def bomb_at(im, cx, cy, r):
    d = ImageDraw.Draw(im)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=DBLUE, outline=OUT)
    d.ellipse([cx - r + 2, cy - r + 2, cx - r + 2 + r // 2, cy - r + 2 + r // 2], fill=BLUE)
    # a skull
    d.ellipse([cx - r // 2, cy - r // 3, cx + r // 2, cy + r // 2], fill=WHITE, outline=OUT)
    d.rectangle([cx - r // 3, cy, cx - r // 6, cy + r // 6], fill=BLACK)
    d.rectangle([cx + r // 6, cy, cx + r // 3, cy + r // 6], fill=BLACK)
    # the fuse and its spark
    d.line([(cx + r // 2, cy - r + 1), (cx + r, cy - r - r // 2)], fill=BROWN, width=2)
    sx, sy = cx + r, cy - r - r // 2
    d.polygon([(sx, sy - 4), (sx + 2, sy - 1), (sx + 5, sy), (sx + 2, sy + 1), (sx, sy + 4), (sx - 2, sy + 1), (sx - 5, sy), (sx - 2, sy - 1)], fill=YELLOW)
    d.ellipse([sx - 1, sy - 1, sx + 1, sy + 1], fill=RED)


def bomb():
    im, _ = canvas()
    bomb_at(im, 15, 30, 13)
    return im


def rocket_at(im, cx, cy, s):
    """A tracking missile pointing down: light body, dark nose, green fins and flame at the top."""
    d = ImageDraw.Draw(im)

    def p(x, y):
        return (cx + x * s, cy + y * s)
    d.polygon([p(-9, -14), p(0, -22), p(9, -14), p(7, -10), p(-7, -10)], fill=ORANGE)
    d.rectangle([p(-6, -12), p(6, 8)], fill=GREY, outline=OUT)
    d.rectangle([p(-3, -10), p(-1, 6)], fill=WHITE)
    d.polygon([p(-6, 8), p(0, 20), p(6, 8)], fill=DGREY, outline=OUT)
    d.polygon([p(-6, -4), p(-14, -10), p(-6, 4)], fill=GREEN, outline=OUT)
    d.polygon([p(6, -4), p(14, -10), p(6, 4)], fill=GREEN, outline=OUT)
    d.rectangle([p(-6, -3), p(6, 0)], fill=RED)


def missile():
    im, _ = canvas()
    rocket_at(im, 16, 24, 1.0)
    return im


def tnt():
    """A TNT crate seen corner-on: dark red left face, red right face, light top."""
    im, d = canvas()
    d.polygon([(16, 6), (30, 13), (16, 20), (2, 13)], fill=(235, 110, 90, 255), outline=OUT)
    d.polygon([(2, 13), (16, 20), (16, 44), (2, 36)], fill=DRED, outline=OUT)
    d.polygon([(16, 20), (30, 13), (30, 36), (16, 44)], fill=RED, outline=OUT)
    for pts in ([(4, 22), (14, 27), (14, 33), (4, 28)], [(18, 27), (28, 22), (28, 28), (18, 33)]):
        d.polygon(pts, fill=YELLOW, outline=OUT)
    d.line([(6, 24), (12, 28)], fill=BLACK, width=2)
    d.line([(20, 28), (26, 24)], fill=BLACK, width=2)
    d.line([(16, 6), (19, 2)], fill=BROWN, width=2)
    d.ellipse([18, 0, 22, 4], fill=YELLOW)
    return im


def potion():
    """A bubbling beaker of green potion."""
    im, d = canvas()
    d.polygon([(12, 6), (20, 6), (20, 18), (28, 40), (4, 40), (12, 18)], fill=(215, 235, 245, 255), outline=OUT)
    d.polygon([(10, 26), (22, 26), (28, 40), (4, 40)], fill=GREEN)
    d.polygon([(10, 26), (22, 26), (24, 30), (8, 30)], fill=LGREEN)
    d.rectangle([11, 3, 21, 7], fill=BROWN, outline=OUT)
    d.line([(9, 38), (24, 38)], fill=DGREEN)
    for bx, by, br in ((13, 34, 2), (19, 30, 1), (17, 14, 2), (14, 9, 1)):
        d.ellipse([bx - br, by - br, bx + br, by + br], fill=LGREEN, outline=DGREEN)
    return im


def spring():
    im, d = canvas()
    for i in range(6):
        y = 10 + i * 6
        d.line([(8, y), (24, y + 3)], fill=GREY, width=3)
        d.line([(24, y + 3), (8, y + 6)], fill=DGREY, width=2)
    d.rounded_rectangle([5, 5, 27, 9], radius=1, fill=RED, outline=OUT)
    d.rounded_rectangle([5, 42, 27, 46], radius=1, fill=RED, outline=OUT)
    return im


def shield():
    """A glossy green bubble."""
    im, d = canvas()
    d.ellipse([3, 4, 29, 46], fill=(40, 150, 70, 255), outline=OUT)
    d.ellipse([6, 8, 26, 42], fill=(60, 190, 90, 255))
    d.ellipse([9, 11, 16, 24], fill=(190, 255, 200, 255))
    d.ellipse([20, 32, 25, 40], fill=(120, 230, 140, 255))
    return im


def mask():
    """A wooden tiki mask with a crown of leaves."""
    im, d = canvas()
    for pts, col in (([(5, 14), (2, 4), (11, 11)], BLUE), ([(10, 12), (12, 1), (17, 11)], GREEN), ([(16, 11), (22, 2), (24, 13)], YELLOW), ([(23, 13), (30, 6), (28, 18)], RED)):
        d.polygon(pts, fill=col, outline=OUT)
    d.rounded_rectangle([5, 10, 27, 46], radius=8, fill=BROWN, outline=OUT)
    d.rectangle([8, 20, 24, 23], fill=DBROWN)
    d.ellipse([8, 24, 14, 32], fill=WHITE, outline=OUT)
    d.ellipse([18, 24, 24, 32], fill=WHITE, outline=OUT)
    d.rectangle([10, 27, 12, 30], fill=BLACK)
    d.rectangle([20, 27, 22, 30], fill=BLACK)
    d.rectangle([10, 36, 22, 42], fill=DBROWN, outline=OUT)
    for x in (12, 15, 18):
        d.line([(x, 36), (x, 42)], fill=WHITE)
    return im


def clock():
    """A gold stopwatch."""
    im, d = canvas()
    d.rectangle([13, 4, 19, 10], fill=GOLD, outline=OUT)
    d.ellipse([3, 9, 29, 43], fill=GOLD, outline=OUT, width=2)
    d.ellipse([7, 13, 25, 39], fill=WHITE, outline=DGOLD)
    d.line([(16, 26), (16, 17)], fill=BLACK, width=2)
    d.line([(16, 26), (21, 30)], fill=RED, width=2)
    for a in range(0, 360, 90):
        x, y = 16 + 8 * math.sin(math.radians(a)), 26 - 11 * math.cos(math.radians(a))
        d.point((x, y), fill=BLACK)
    d.line([(24, 11), (28, 7)], fill=GOLD, width=2)
    return im


def warpball():
    """A dark blue orb crackling with lightning."""
    im, d = canvas()
    d.ellipse([4, 10, 28, 40], fill=DBLUE, outline=OUT)
    d.ellipse([8, 15, 24, 35], fill=(70, 90, 200, 255))
    d.ellipse([13, 20, 19, 30], fill=LBLUE)
    for pts in (([(2, 6), (8, 14), (4, 18), (10, 24)]), ([(30, 8), (24, 16), (28, 22), (22, 30)]), ([(8, 46), (12, 38), (8, 34)]), ([(26, 44), (22, 38), (27, 34)])):
        d.line(pts, fill=(200, 235, 255, 255), width=1)
    return im


def bomb_3x():
    im, _ = canvas()
    for cx, cy in ((9, 36), (23, 36), (16, 22)):
        bomb_at(im, cx, cy, 8)
    return im


def missile_3x():
    im, _ = canvas()
    for cx, cy in ((8, 32), (24, 32), (16, 18)):
        rocket_at(im, cx, cy, 0.55)
    return im


def invisibility():
    im, d = canvas()
    pts = [(6, 44), (6, 20), (9, 9), (16, 5), (23, 9), (26, 20), (26, 44), (22, 39), (19, 44), (16, 39), (13, 44), (10, 39)]
    for a, b in zip(pts, pts[1:] + pts[:1]):
        d.line([a, b], fill=LBLUE, width=2)
    d.ellipse([11, 19, 14, 24], fill=LBLUE)
    d.ellipse([18, 19, 21, 24], fill=LBLUE)
    return im


def super_engine():
    im, d = canvas()
    for k in range(8):
        a = k * math.pi / 4
        x, y = 16 + 12 * math.cos(a), 22 + 12 * math.sin(a)
        d.rectangle([x - 3, y - 3, x + 3, y + 3], fill=GOLD, outline=OUT)
    d.ellipse([5, 11, 27, 33], fill=YELLOW, outline=OUT)
    d.ellipse([12, 18, 20, 26], fill=DGREY, outline=OUT)
    d.polygon([(8, 47), (12, 36), (16, 47)], fill=ORANGE)
    d.polygon([(16, 47), (20, 36), (24, 47)], fill=ORANGE)
    return im


def wumpa():
    """The Wumpa fruit sprite, 48 x 32: green on top, through yellow and orange to red below, a leaf on top."""
    im, d = canvas(48, 32)
    mask = Image.new("L", (48, 32), 0)
    ImageDraw.Draw(mask).ellipse([6, 5, 42, 31], fill=255)
    for y in range(32):
        t = min(1.0, max(0.0, (y - 5) / 26.0))
        stops = ((0.0, (150, 200, 60)), (0.3, (250, 210, 50)), (0.6, (250, 140, 40)), (1.0, (200, 50, 35)))
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            if t0 <= t <= t1:
                u = (t - t0) / (t1 - t0)
                col = tuple(int(c0[i] + (c1[i] - c0[i]) * u) for i in range(3)) + (255,)
        for x in range(48):
            if mask.getpixel((x, y)):
                im.putpixel((x, y), col)
    d.ellipse([6, 5, 42, 31], outline=(120, 50, 20, 255))
    d.ellipse([12, 10, 20, 17], fill=(255, 235, 150, 255))
    d.polygon([(24, 6), (17, 1), (27, 2)], fill=GREEN, outline=DGREEN)
    d.polygon([(24, 6), (32, 1), (33, 8)], fill=GREEN, outline=DGREEN)
    return im


def lamp(colour, lit):
    """A start lamp, 48 x 32: a grey housing with blue clips either side and a glossy red or green bulb."""
    im, d = canvas(48, 32)
    d.rectangle([0, 11, 3, 21], fill=(60, 110, 200, 255), outline=OUT)
    d.rectangle([44, 11, 47, 21], fill=(60, 110, 200, 255), outline=OUT)
    d.ellipse([3, 1, 44, 30], fill=(70, 72, 84, 255), outline=OUT)
    d.ellipse([5, 3, 42, 28], fill=(120, 124, 138, 255))
    if colour == "red":
        bulb, edge, shine = ((255, 70, 60, 255), (200, 20, 20, 255), (255, 200, 190, 255)) if lit else ((110, 28, 28, 255), (70, 15, 15, 255), (150, 60, 60, 255))
    else:
        bulb, edge, shine = ((90, 255, 110, 255), (30, 170, 60, 255), (210, 255, 215, 255)) if lit else ((25, 80, 35, 255), (15, 50, 25, 255), (60, 120, 70, 255))
    d.ellipse([8, 5, 39, 26], fill=edge)
    d.ellipse([10, 6, 37, 24], fill=bulb)
    d.ellipse([13, 8, 26, 14], fill=shine)
    d.rectangle([20, 29, 27, 31], fill=(60, 110, 200, 255))
    return im


ICONS = {
    "turbo": turbo, "bomb": bomb, "missile": missile, "tnt": tnt, "potion": potion, "spring": spring, "shield": shield, "mask": mask,
    "clock": clock, "warpball": warpball, "bomb_3x": bomb_3x, "missile_3x": missile_3x, "invisibility": invisibility, "super_engine": super_engine,
}


def main():
    os.makedirs(os.path.join(GUI, "power"), exist_ok=True)
    os.makedirs(os.path.join(GUI, "lights"), exist_ok=True)
    for name, make in ICONS.items():
        path = os.path.join(GUI, "power", name + ".png")
        if not os.path.exists(path):
            make().save(path)
    path = os.path.join(GUI, "wumpa.png")
    if not os.path.exists(path):
        wumpa().save(path)
    for colour in ("red", "green"):
        for lit in (True, False):
            path = os.path.join(GUI, "lights", "%s_%s.png" % (colour, "on" if lit else "off"))
            if not os.path.exists(path):
                lamp(colour, lit).save(path)
    print("HUD pictures ok")


if __name__ == "__main__":
    main()
