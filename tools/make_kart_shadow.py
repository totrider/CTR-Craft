"""
The kart's ground shadow, made from the kart's own model.

  python tools/make_kart_shadow.py

Looks straight down on blockbench/kart/kart_ctr.bbmodel: every exported cube (turned as it is) and the four wheel markers are projected onto the ground, their outlines filled, softened and
darkened; the result is textures/entity/kart_shadow.png (40 texels a block, front at the top, always rewritten: it comes from the model, so repaint the model, not this) and
assets/ctrcraft/kart/kart_shadow.json, where the picture lies in blocks from the kart's origin ({x0, x1, z0, z1}, the front being -z), which the renderer puts it on. So the shadow follows
whatever sticks out of the kart, wheels, exhausts, the nose and the rest, and the original's picture (made for the original's kart, with its own wheels and tail) is not used. Needs numpy and
Pillow.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ctrcraft")
BB = os.path.join(ROOT, "blockbench", "kart", "kart_ctr.bbmodel")
TEX = os.path.join(ASSETS, "textures", "entity", "kart_shadow.png")
DATA = os.path.join(ASSETS, "kart", "kart_shadow.json")
TEXELS = 40.0              # to a block
MARGIN = 0.07              # blocks of soft edge round the outline
MAX_ALPHA = 200


def corners(el):
    f, t = el["from"], el["to"]
    pts = [[t[0] if i & 1 else f[0], t[1] if i & 2 else f[1], t[2] if i & 4 else f[2]] for i in range(8)]
    rot = el.get("rotation") or [0, 0, 0]
    axes = [i for i in range(3) if rot[i]]
    if axes:
        ai = axes[0]
        a = math.radians(rot[ai])
        c, s = math.cos(a), math.sin(a)
        o = el.get("origin", [8, 8, 8])
        for p in pts:
            x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
            if ai == 0:
                y, z = y * c - z * s, y * s + z * c
            elif ai == 1:
                x, z = x * c + z * s, -x * s + z * c
            else:
                x, y = x * c - y * s, x * s + y * c
            p[0], p[1], p[2] = x + o[0], y + o[1], z + o[2]
    return pts


def hull(points):
    pts = sorted(set(points))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def outlines(path=BB):
    """The footprints (convex polygons of (x, z) in blocks from the origin, the front being -z) of what the model has."""
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    groups = {g.get("uuid"): g for g in d.get("groups", []) if isinstance(g, dict)}
    hidden = set()

    def walk(nodes, off):
        for n in nodes:
            if isinstance(n, str):
                if off:
                    hidden.add(n)
            else:
                g = groups.get(n.get("uuid"), {})
                walk(n.get("children", []), off or n.get("export", g.get("export", True)) is False)
    walk(d.get("outliner", []), False)
    polys = []
    for e in d["elements"]:
        if e.get("type", "cube") != "cube":
            continue
        name = str(e.get("name", ""))
        wheel = name.startswith("wheel_")
        if not wheel and (e["uuid"] in hidden or e.get("export") is False):
            continue
        poly = hull([((p[0] - 8.0) / 16.0, (p[2] - 8.0) / 16.0) for p in corners(e)])
        polys.append(poly)
    return polys


def inside(poly, xs, zs):
    """Which of the sample points are inside the convex polygon (counter clockwise or clockwise)."""
    n = len(poly)
    sign = None
    ok = np.ones(xs.shape, dtype=bool)
    for i in range(n):
        ax, az = poly[i]
        bx, bz = poly[(i + 1) % n]
        c = (bx - ax) * (zs - az) - (bz - az) * (xs - ax)
        ok &= (c >= -1e-9)
    ok2 = np.ones(xs.shape, dtype=bool)
    for i in range(n):
        ax, az = poly[i]
        bx, bz = poly[(i + 1) % n]
        c = (bx - ax) * (zs - az) - (bz - az) * (xs - ax)
        ok2 &= (c <= 1e-9)
    return ok | ok2


def main():
    polys = outlines()
    xs_all = [p[0] for poly in polys for p in poly]
    zs_all = [p[1] for poly in polys for p in poly]
    x0, x1 = min(xs_all) - MARGIN, max(xs_all) + MARGIN
    z0, z1 = min(zs_all) - MARGIN, max(zs_all) + MARGIN
    w = int(math.ceil((x1 - x0) * TEXELS))
    h = int(math.ceil((z1 - z0) * TEXELS))
    x1 = x0 + w / TEXELS
    z1 = z0 + h / TEXELS
    ss = 4
    gx = x0 + (np.arange(w * ss) + 0.5) / (TEXELS * ss)
    gz = z0 + (np.arange(h * ss) + 0.5) / (TEXELS * ss)
    xx, zz = np.meshgrid(gx, gz)
    mask = np.zeros(xx.shape, dtype=bool)
    for poly in polys:
        if len(poly) >= 3:
            mask |= inside(poly, xx, zz)
    cover = mask.reshape(h, ss, w, ss).mean(axis=(1, 3))
    img = Image.fromarray((cover * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(MARGIN * TEXELS * 0.55))
    a = np.array(img).astype(np.float64) / 255.0
    alpha = np.clip(a * 1.35, 0.0, 1.0) * MAX_ALPHA
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[:, :, 3] = alpha.astype(np.uint8)
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    Image.fromarray(out, "RGBA").save(TEX)
    os.makedirs(os.path.dirname(DATA), exist_ok=True)
    with open(DATA, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"source": "blockbench/kart/kart_ctr.bbmodel seen from above (tools/make_kart_shadow.py)", "x0": round(x0, 5), "x1": round(x1, 5), "z0": round(z0, 5), "z1": round(z1, 5)}, f, indent=2)
    print("kart shadow %dx%d texels, x %.3f..%.3f, z %.3f..%.3f blocks" % (w, h, x0, x1, z0, z1))


if __name__ == "__main__":
    main()
