"""
The kart's texture, and the uv mapping of blockbench/kart/kart_ctr.bbmodel onto it.

  python tools/make_kart_texture.py

Writes src/main/resources/assets/ctrcraft/textures/item/kart_ctr.png (128 x 128, only if missing: it is a placeholder, yours to repaint) and, ONCE, maps the faces of the cubes in
kart_ctr.bbmodel onto it by their names (it does nothing to a model whose texture is already kart_ctr.png: after that the uvs are yours). The picture is 16 x 16 uv units, 8 texels to a
unit; each part has a tile:

  tintable (grey scale, multiplied by the kart's colour in the game: tintindex 0 the body colour, 1 the seat's)       tub_*  body panel       strut_*  bar with clamp rings       seat_*  quilted seat
  fixed colours                                                                       engine_*  vents   exhaust_*  pipe and its open end   steering_0  column   steering_1  the wheel

The steering wheel is the oblong, flat bottomed kind of a racing car, drawn on both big faces of its plate with see-through openings (the game draws cutouts), carbon centre, suede
grips; the plate's edges are dark.
"""
import base64
import io
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "ctrcraft", "textures", "item", "kart_ctr.png")
BB = os.path.join(ROOT, "blockbench", "kart", "kart_ctr.bbmodel")
N = 128
U = 8                       # texels to a uv unit

# tile -> (u0, v0, u1, v1) in uv units
REGIONS = {
    "body": (0, 0, 8, 8),
    "seat": (8, 0, 16, 8),
    "strut": (0, 8, 8, 16),
    "engine": (8, 8, 12, 12),
    "exhaust": (12, 8, 14, 12),
    "exhaust_end": (14, 8, 16, 10),
    "column": (8, 12, 10, 14),
    "edge": (8, 14, 10, 16),
    "wheel": (10, 12, 16, 16),
}


def px(region):
    u0, v0, u1, v1 = region
    return u0 * U, v0 * U, u1 * U, v1 * U


def noise(rng, shape, amp):
    return rng.integers(-amp, amp + 1, size=shape)


def paint():
    rng = np.random.default_rng(3)
    im = np.zeros((N, N, 4), dtype=np.int32)
    im[:, :, 3] = 0

    def put(region, arr):
        x0, y0, x1, y1 = px(region)
        im[y0:y1, x0:x1] = arr

    def solid(w, h, rgb):
        a = np.zeros((h, w, 4), dtype=np.int32)
        a[:, :] = list(rgb) + [255]
        return a

    # body panel (tintable): light grey, a rim, rivets, one seam
    t = solid(64, 64, (234, 234, 238))
    t[:, :, :3] += noise(rng, (64, 64, 1), 3)
    t[0:2, :, :3] = 206
    t[-2:, :, :3] = 206
    t[:, 0:2, :3] = 206
    t[:, -2:, :3] = 206
    t[31:33, :, :3] = 218
    for cx in (6, 57):
        for cy in (6, 57):
            t[cy - 1:cy + 2, cx - 1:cx + 2, :3] = 190
            t[cy, cx, :3] = 245
    put(REGIONS["body"], t)

    # seat (tintable): a padded cushion with stitched diamonds and a lighter bolster round the edge
    t = solid(64, 64, (224, 224, 228))
    t[:, :, :3] += noise(rng, (64, 64, 1), 3)
    for i in range(-64, 128, 16):
        for k in range(64):
            for (x, y) in ((k, (k + i) % 64), (k, (-k + i) % 64)):
                t[y, x, :3] = 186
    t[0:6, :, :3] = 240
    t[-6:, :, :3] = 240
    t[:, 0:6, :3] = 240
    t[:, -6:, :3] = 240
    put(REGIONS["seat"], t)

    # strut (tintable): a round bar seen side on (darker towards both edges) with a clamp ring now and then
    t = solid(64, 64, (236, 236, 240))
    for y in range(64):
        k = abs((y + 0.5) / 32.0 - 1.0)
        t[y, :, :3] = int(236 - 54 * k * k)
    for x in list(range(10, 16)) + list(range(48, 54)):
        t[:, x, :3] = (t[:, x, :3] * 0.82).astype(np.int32)
    put(REGIONS["strut"], t)

    # engine (fixed): dark metal with cooling slats and bolts
    t = solid(32, 32, (62, 64, 70))
    t[:, :, :3] += noise(rng, (32, 32, 1), 3)
    for y in range(6, 27, 4):
        t[y, 3:29, :3] = (96, 98, 106)
        t[y + 1, 3:29, :3] = (40, 41, 46)
    for cx in (3, 28):
        for cy in (3, 28):
            t[cy - 1:cy + 1, cx - 1:cx + 1, :3] = (140, 142, 150)
    put(REGIONS["engine"], t)

    # exhaust (fixed): a metal pipe, brighter down its middle, heat-tinted near its end
    t = solid(16, 32, (150, 154, 164))
    for x in range(16):
        k = abs((x + 0.5) / 8.0 - 1.0)
        t[:, x, :3] = int(176 - 70 * k * k)
    t[0:6, :, 2] = np.clip(t[0:6, :, 2] + 30, 0, 255)
    t[0:6, :, 0] = np.clip(t[0:6, :, 0] - 25, 0, 255)
    put(REGIONS["exhaust"], t)
    t = solid(16, 16, (140, 144, 154))
    yy, xx = np.mgrid[0:16, 0:16]
    r = np.hypot(xx + 0.5 - 8, yy + 0.5 - 8)
    t[r < 6.5, :3] = (108, 112, 122)
    t[r < 5.0, :3] = (16, 16, 20)
    put(REGIONS["exhaust_end"], t)

    # steering column and the plate's edge
    t = solid(16, 16, (68, 70, 78))
    t[:, 6:9, :3] = (96, 98, 108)
    put(REGIONS["column"], t)
    put(REGIONS["edge"], solid(16, 16, (30, 30, 34)))

    # the wheel: 48 x 32, an oblong racing wheel (grips at the sides with horns at the top, a carbon plate narrow at the top, a flat bottom bar), the openings see-through
    W, H = 48, 32
    ys, xs = np.mgrid[0:H, 0:W]
    x = (xs + 0.5) / W
    y = (ys + 0.5) / H

    def poly(points):
        mask = np.zeros((H, W), dtype=bool)
        n = len(points)
        for yy_ in range(H):
            for xx_ in range(W):
                px_, py_ = (xx_ + 0.5) / W, (yy_ + 0.5) / H
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = points[i]
                    xj, yj = points[j]
                    if (yi > py_) != (yj > py_) and px_ < (xj - xi) * (py_ - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                mask[yy_, xx_] = inside
        return mask

    suede = (26, 26, 28)
    grip_l = poly([(0.02, 0.18), (0.10, 0.06), (0.20, 0.10), (0.20, 0.30), (0.26, 0.70), (0.30, 0.94), (0.08, 0.94), (0.02, 0.60)])
    grip_r = poly([(0.98, 0.18), (0.90, 0.06), (0.80, 0.10), (0.80, 0.30), (0.74, 0.70), (0.70, 0.94), (0.92, 0.94), (0.98, 0.60)])
    bottom = poly([(0.20, 0.80), (0.80, 0.80), (0.82, 0.96), (0.18, 0.96)])
    plate = poly([(0.36, 0.14), (0.64, 0.14), (0.78, 0.30), (0.80, 0.80), (0.20, 0.80), (0.22, 0.30)])
    wheel = np.zeros((H, W, 4), dtype=np.int32)
    wheel[grip_l | grip_r | bottom, :3] = suede
    wheel[grip_l | grip_r | bottom, 3] = 255
    weave = ((xs // 2 + ys // 2) % 2 == 0)
    wheel[plate & weave, :3] = (84, 88, 96)
    wheel[plate & ~weave, :3] = (56, 60, 68)
    wheel[plate, 3] = 255
    wheel[(abs(y - 0.22) < 0.03) & (abs(x - 0.5) < 0.12) & plate, :3] = (228, 228, 232)   # the maker's stripe on the narrow top of the plate (the 3D bar covers the middle)
    for bx, col in ((0.32, (200, 40, 40)), (0.68, (230, 190, 40))):                         # two buttons
        b = np.hypot((xs + 0.5) / W - bx, ((ys + 0.5) / H - 0.70) * 0.66) < 0.035
        wheel[b, :3] = col
        wheel[b, 3] = 255
    put(REGIONS["wheel"], wheel)
    return Image.fromarray(np.clip(im, 0, 255).astype(np.uint8), "RGBA")


WHEEL_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "ctrcraft", "textures", "item", "kart_ctr_wheel.png")
WHEEL_FRONT = (0, 0, 6, 4)          # the face towards the driver (uv units of the wheel picture, 8 texels to a unit): with the buttons and the maker's stripe
WHEEL_BACK = (0, 4, 6, 8)           # the face towards the front of the kart: the same wheel, plain


def wheel_face(buttons):
    """48 x 32: an oblong racing wheel (grips at the sides with horns at the top, a carbon plate narrow at the top, a flat bottom bar); the openings are see-through. With {buttons}
    the maker's stripe and two buttons, as the driver sees it; without, the plain back."""
    W, H = 48, 32
    ys, xs = np.mgrid[0:H, 0:W]
    x = (xs + 0.5) / W
    y = (ys + 0.5) / H

    def poly(points):
        mask = np.zeros((H, W), dtype=bool)
        n = len(points)
        for yy_ in range(H):
            for xx_ in range(W):
                px_, py_ = (xx_ + 0.5) / W, (yy_ + 0.5) / H
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = points[i]
                    xj, yj = points[j]
                    if (yi > py_) != (yj > py_) and px_ < (xj - xi) * (py_ - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                mask[yy_, xx_] = inside
        return mask

    suede = (26, 26, 28)
    grip_l = poly([(0.02, 0.18), (0.10, 0.06), (0.20, 0.10), (0.20, 0.30), (0.26, 0.70), (0.30, 0.94), (0.08, 0.94), (0.02, 0.60)])
    grip_r = poly([(0.98, 0.18), (0.90, 0.06), (0.80, 0.10), (0.80, 0.30), (0.74, 0.70), (0.70, 0.94), (0.92, 0.94), (0.98, 0.60)])
    bottom = poly([(0.20, 0.80), (0.80, 0.80), (0.82, 0.96), (0.18, 0.96)])
    plate = poly([(0.36, 0.14), (0.64, 0.14), (0.78, 0.30), (0.80, 0.80), (0.20, 0.80), (0.22, 0.30)])
    wheel = np.zeros((H, W, 4), dtype=np.int32)
    wheel[grip_l | grip_r | bottom, :3] = suede
    wheel[grip_l | grip_r | bottom, 3] = 255
    weave = ((xs // 2 + ys // 2) % 2 == 0)
    wheel[plate & weave, :3] = (84, 88, 96)
    wheel[plate & ~weave, :3] = (56, 60, 68)
    wheel[plate, 3] = 255
    if buttons:
        wheel[(abs(y - 0.22) < 0.03) & (abs(x - 0.5) < 0.12) & plate, :3] = (228, 228, 232)
        for bx, col in ((0.32, (200, 40, 40)), (0.68, (230, 190, 40))):
            b = np.hypot((xs + 0.5) / W - bx, ((ys + 0.5) / H - 0.70) * 0.66) < 0.035
            wheel[b, :3] = col
            wheel[b, 3] = 255
    return wheel


def paint_wheel():
    """The steering wheel's own picture (128 x 128, uv 0-16): the driver's face top left, the back face under it."""
    im = np.zeros((N, N, 4), dtype=np.int32)
    for region, buttons in ((WHEEL_FRONT, True), (WHEEL_BACK, False)):
        x0, y0, x1, y1 = px(region)
        im[y0:y1, x0:x1] = wheel_face(buttons)
    return Image.fromarray(np.clip(im, 0, 255).astype(np.uint8), "RGBA")


# the faces of a cube: the two sizes its picture has (px), by face
def face_sizes(frm, to):
    dx, dy, dz = (to[0] - frm[0]), (to[1] - frm[1]), (to[2] - frm[2])
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}


def window(region, w, h):
    u0, v0, u1, v1 = region
    return [u0, v0, round(u0 + min(max(w, 0.5), u1 - u0), 3), round(v0 + min(max(h, 0.5), v1 - v0), 3)]


def map_bbmodel(path):
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    tex = d["textures"][0]
    if tex.get("name") == "kart_ctr.png":
        print("the model is already on kart_ctr.png: its uvs are left as they are")
        return False
    changed = []
    for e in d["elements"]:
        name = str(e.get("name", ""))
        if name.startswith("guide_") or "marker" in name:
            continue
        sizes = face_sizes(e["from"], e["to"])
        for face, f in e["faces"].items():
            w, h = sizes[face]
            tint = -1
            if name.startswith("tub"):
                region, tint = REGIONS["body"], 0
            elif name.startswith("strut"):
                region, tint = REGIONS["strut"], 0
            elif name.startswith("seat"):
                region, tint = REGIONS["seat"], 1
            elif name.startswith("engine"):
                region = REGIONS["engine"]
            elif name.startswith("exhaust"):
                region = REGIONS["exhaust_end"] if face in ("up", "down") else REGIONS["exhaust"]
            elif name == "steering_0" or name.startswith("steering_0"):
                region = REGIONS["column"]
            elif name.startswith("steering_2"):
                region = REGIONS["edge"]                                # the 3D bar across the wheel
            elif name.startswith("steering_3"):
                region = REGIONS["column"]                              # the pivot box between the rod and the bar
            elif name.startswith("steering_1"):
                region = REGIONS["wheel"] if face in ("north", "south") else REGIONS["edge"]
                if face in ("north", "south"):
                    f["uv"] = list(region)
                    f["tint"] = -1
                    f["texture"] = 0
                    continue
            else:
                continue
            f["uv"] = window(region, w, h)
            f["tint"] = tint
            f["texture"] = 0
        changed.append(name)
    buf = io.BytesIO()
    Image.open(TEX).save(buf, "PNG")
    tex.update({"name": "kart_ctr.png", "relative_path": "../../src/main/resources/assets/ctrcraft/textures/item/kart_ctr.png", "width": N, "height": N, "uv_width": 16, "uv_height": 16,
                "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")})
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, separators=(",", ":"), ensure_ascii=False)
    print("mapped:", ", ".join(changed))
    return True


def main():
    if not os.path.exists(WHEEL_TEX):
        paint_wheel().save(WHEEL_TEX)
        print("kart_ctr_wheel.png written")
    if not os.path.exists(TEX):
        os.makedirs(os.path.dirname(TEX), exist_ok=True)
        paint().save(TEX)
        print("kart_ctr.png written")
    map_bbmodel(BB)


if __name__ == "__main__":
    main()
