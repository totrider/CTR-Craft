"""
The Bomb's rolling picture: an atlas of 8 columns by 32 rows of 64 x 64 px pictures. Column k is the bomb seen from the camera's azimuth k * 45 degrees round it (0 = from
behind, where a chase camera sees a bomb that is going away; 4 = from in front), row i is the bomb turned i * 11.25 degrees about its axle (the original's bomb rolls with
its whole model, fuse included). Stand-in art, drawn here in 3D and projected: a dark ball with a lighter band, and a fuse with a glowing spark that leaves the ball at
45 degrees between the axle and the way it goes, as the original's does, so it sweeps a cone round the axle as the ball turns.

  python tools/make_bomb_sprites.py

Writes src/main/resources/assets/ctrcraft/textures/entity/bomb_roll.png. A file that exists is yours and is not overwritten (delete it to have it drawn again). The original's
bomb is rendered from the player's own disc into a local resource pack by tools/extract_ctr_textures.py with the same layout, which replaces this file.
Axes: x the axle, y the way it travels, z up; the camera is tools/render_ctr_model.py's (yaw, pitch). Run from the project folder.
"""
import math
import os

from PIL import Image, ImageDraw

OUT = os.path.join("src", "main", "resources", "assets", "ctrcraft", "textures", "entity", "bomb_roll.png")
FRAMES = 32
AZIMUTHS = 8
SIZE = 64
SS = 4                      # drawn at 4x and scaled down
PITCH = math.radians(20.0)
BALL = 0.30                 # the ball's radius, with the fuse's reach as 1.0 (64 lu across, the fuse out to 94 lu)
FUSE_DIR = (0.7071, 0.0, 0.7071)          # at roll 0 (as the original's rendering does) the fuse is up and to the side, the way the fired bomb is seen in the footage
BAND = (100, 102, 124, 255)


def roll(v, a):
    c, s = math.cos(a), math.sin(a)
    return (v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c)


def project(v, yaw):
    """render_view's camera: (screen x, screen up, depth: smaller is nearer)."""
    cy, sy = math.cos(yaw), math.sin(yaw)
    x1 = -(v[0] * cy - v[1] * sy)
    y1 = v[0] * sy + v[1] * cy
    return x1, v[2] * math.cos(PITCH) - y1 * math.sin(PITCH), y1 * math.cos(PITCH) + v[2] * math.sin(PITCH)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def unit(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / l for c in v)


def picture(roll_a, yaw):
    n = SIZE * SS
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    half = n / 2.0 * 0.96

    def pt(v):
        x, up, depth = project(v, yaw)
        return n / 2 + x * half, n / 2 - up * half, depth

    f0 = pt(roll(tuple(c * BALL for c in FUSE_DIR), roll_a))
    f1 = pt(roll(tuple(c * 0.93 for c in FUSE_DIR), roll_a))
    cx, cy, _ = pt((0.0, 0.0, 0.0))
    r = BALL * half

    def fuse():
        d.line([(f0[0], f0[1]), (f1[0], f1[1])], fill=(130, 85, 40, 255), width=int(n * 0.035))
        for rad, col in ((n * 0.075, (255, 120, 30, 255)), (n * 0.048, (255, 220, 80, 255)), (n * 0.022, (255, 255, 230, 255))):
            d.ellipse([f1[0] - rad, f1[1] - rad, f1[0] + rad, f1[1] + rad], fill=col)

    behind = f1[2] > 0                  # the spark is farther from the camera than the ball's centre
    if behind:
        fuse()
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(38, 36, 46, 255), outline=(14, 12, 20, 255), width=int(n * 0.02))
    # a band round the ball (a great circle turning with it): the half nearer the camera
    normal = unit(roll((0.3, 0.0, 0.954), roll_a))
    w = unit(cross(normal, (0.0, 1.0, 0.0)))
    u = cross(normal, w)
    run = []
    for t in range(73):
        ang = 2 * math.pi * t / 72
        x, y, depth = pt(tuple(BALL * 0.98 * (math.cos(ang) * u[k] + math.sin(ang) * w[k]) for k in range(3)))
        if depth < 0.0:
            run.append((x, y))
        else:
            if len(run) > 1:
                d.line(run, fill=BAND, width=int(n * 0.045))
            run = []
    if len(run) > 1:
        d.line(run, fill=BAND, width=int(n * 0.045))
    d.ellipse([cx - r * 0.1, cy - r * 0.75, cx + r * 0.55, cy - r * 0.2], fill=(120, 124, 150, 120))   # the light does not roll
    if not behind:
        fuse()
    return im.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    if os.path.exists(OUT):
        print("exists, left alone:", OUT)
        return
    atlas = Image.new("RGBA", (SIZE * AZIMUTHS, SIZE * FRAMES), (0, 0, 0, 0))
    for i in range(FRAMES):
        for k in range(AZIMUTHS):
            atlas.alpha_composite(picture(2 * math.pi * i / FRAMES, 2 * math.pi * k / AZIMUTHS), (k * SIZE, i * SIZE))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    atlas.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
