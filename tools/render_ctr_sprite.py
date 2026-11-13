"""
Renders the original Wumpa fruit of YOUR OWN CTR disc image as a sprite strip: its spin animation (32 frames), seen from the front, drawn frame by frame with the
model's own per-vertex colours. Used by tools/extract_ctr_textures.py for the local resource packs (the pickup renderer shows a flat sprite model as a billboard);
run by itself it writes a preview into the git-ignored local-assets/ctr-sprites/.

  python tools/render_ctr_sprite.py [--size 96] [--tilt 0]

The picture is software-rendered here (no GPU): an orthographic camera at the model's side, the game's up axis (the third coordinate) up, Gouraud-shaded triangles
with a depth buffer, drawn 4 times larger and shrunk with the alpha thresholded (the game draws a cutout, so the edge stays crisp). One block is {size} px, so
the fruit has the size the stand-in has (a crate is one block).
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ctr_disc_textures as ct  # noqa: E402
import extract_ctr_sounds as sounds  # noqa: E402

UNITS_PER_BLOCK = 108.8          # the original's crate: the mod's one block
SUPERSAMPLE = 4


def render_frame(verts, colours, tris, size, origin, tilt=0.0):
    """One frame: verts [(b0, b1, b2)], colours [(r, g, b)] per vertex, tris [(i, j, k)]; origin = (x, up) in level units that lands at the bottom centre of the picture."""
    n = size * SUPERSAMPLE
    scale = n / UNITS_PER_BLOCK
    c, s = np.cos(np.radians(tilt)), np.sin(np.radians(tilt))
    pts = []
    for b0, b1, b2 in verts:
        near = -b1                                           # towards the camera, which stands on the -b1 side
        pts.append((n / 2.0 + (b0 - origin[0]) * scale, n - ((b2 * c - near * s) - origin[1]) * scale, near * c + b2 * s))
    img = np.zeros((n, n, 3), dtype=np.float64)
    alpha = np.zeros((n, n), dtype=bool)
    zbuf = np.full((n, n), -1e18)
    cols = np.array(colours, dtype=np.float64)
    for i, j, k in tris:
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = pts[i], pts[j], pts[k]
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        x0, x1 = max(0, int(min(ax, bx, cx))), min(n - 1, int(max(ax, bx, cx)) + 1)
        y0, y1 = max(0, int(min(ay, by, cy))), min(n - 1, int(max(ay, by, cy)) + 1)
        if x1 < x0 or y1 < y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den
        w1 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-9) & (w1 >= -1e-9) & (w2 >= -1e-9)
        z = w0 * az + w1 * bz + w2 * cz
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        win = inside & (z > sub)
        if not win.any():
            continue
        colour = w0[..., None] * cols[i] + w1[..., None] * cols[j] + w2[..., None] * cols[k]
        img[y0:y1 + 1, x0:x1 + 1][win] = colour[win]
        sub[win] = z[win]
        alpha[y0:y1 + 1, x0:x1 + 1][win] = True
    # shrink: the colour averaged over the covered samples, the pixel solid when at least half is covered
    a = alpha.reshape(size, SUPERSAMPLE, size, SUPERSAMPLE).sum(axis=(1, 3)).astype(np.float64)
    rgb = (img * alpha[..., None]).reshape(size, SUPERSAMPLE, size, SUPERSAMPLE, 3).sum(axis=(1, 3))
    rgb = rgb / np.maximum(a, 1.0)[..., None]
    out = np.zeros((size, size, 4), dtype=np.uint8)
    solid = a >= SUPERSAMPLE * SUPERSAMPLE / 2.0
    out[solid, :3] = np.clip(rgb[solid], 0, 255).astype(np.uint8)
    out[solid, 3] = 255
    return Image.fromarray(out, "RGBA")


def render_strip(model, size, tilt=0.0, turns=None):
    """The fruit's spin as a vertical strip of {size} x {size} frames (the format of an animated Minecraft texture). The model's own 32 "spin" frames are all the same
    shape (the game turns the object in code), so each frame turns the mesh about its vertical axis by one 32nd of a full turn more."""
    count = turns or ct.frame_count(model)
    table = ct.colour_table(model)
    vcol = [tuple(float(v) for v in table[i]) for i in ct.mesh_vertex_colours(model)]
    tris = [corner for corner, _, _, _ in ct.mesh_triangles(model)]
    base = ct.mesh_vertices(model, 0)
    cx = (min(v[0] for v in base) + max(v[0] for v in base)) / 2.0           # the axis of the spin: the middle of the fruit
    cy = (min(v[1] for v in base) + max(v[1] for v in base)) / 2.0
    bottom = min(v[2] for v in base)
    strip = Image.new("RGBA", (size, size * count), (0, 0, 0, 0))
    for f in range(count):
        a = np.radians(360.0 * f / count)
        ca, sa = np.cos(a), np.sin(a)
        verts = [(cx + (v[0] - cx) * ca - (v[1] - cy) * sa, cy + (v[0] - cx) * sa + (v[1] - cy) * ca, v[2]) for v in base]
        strip.alpha_composite(render_frame(verts, vcol, tris, size, (cx, bottom), tilt), (0, f * size))
    return centre_frames(strip, size, count), count


def centre_frames(strip, size, count):
    """Moves the picture of every frame by the same amount so that the middle of the fruit's outline over all frames is the middle of the frame: the pickup renderer turns
    the flat sprite to the camera about the middle of the block, which must be the middle of the fruit (the model's origin is its bottom, so it was drawn low in the frame)."""
    a = np.array(strip)
    ys, xs = np.nonzero(a[:, :, 3])
    if not len(ys):
        return strip
    rows = ys % size
    dy = size // 2 - (int(rows.min()) + int(rows.max()) + 1) // 2
    dx = size // 2 - (int(xs.min()) + int(xs.max()) + 1) // 2
    out = np.zeros_like(a)
    for f in range(count):
        frame = a[f * size:(f + 1) * size]
        shifted = np.zeros_like(frame)
        sy0, sy1 = max(0, -dy), min(size, size - dy)
        sx0, sx1 = max(0, -dx), min(size, size - dx)
        shifted[sy0 + dy:sy1 + dy, sx0 + dx:sx1 + dx] = frame[sy0:sy1, sx0:sx1]
        out[f * size:(f + 1) * size] = shifted
    return Image.fromarray(out, "RGBA")


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--disc")
    ap.add_argument("--size", type=int, default=96, help="px per block (= the frame's size)")
    ap.add_argument("--tilt", type=float, default=0.0, help="degrees the camera looks down from the side")
    ap.add_argument("--out", default=os.path.join(project, "local-assets", "ctr-sprites"))
    args = ap.parse_args()
    disc_path = args.disc or sounds.find_disc(project)
    if not disc_path or not os.path.exists(disc_path):
        sys.exit("No disc image found. Pass --disc \"path\to\CTR.bin\".")
    big = ct.BigFile(sounds.Disc(disc_path))
    model = ct.level_models(big.read(ct.CRASH_COVE_1P_LEV))["fruit"]
    strip, count = render_strip(model, args.size, args.tilt)
    os.makedirs(args.out, exist_ok=True)
    strip.save(os.path.join(args.out, "wumpa_sprite_%d.png" % args.size))
    # a contact sheet of the frames side by side, on a grey backdrop
    sheet = Image.new("RGBA", (args.size * 8, args.size * ((count + 7) // 8)), (90, 120, 160, 255))
    for f in range(count):
        sheet.alpha_composite(strip.crop((0, f * args.size, args.size, (f + 1) * args.size)), ((f % 8) * args.size, (f // 8) * args.size))
    sheet.save(os.path.join(args.out, "wumpa_contact_%d.png" % args.size))
    print("%d frames of %d px -> %s" % (count, args.size, args.out))


if __name__ == "__main__":
    main()
