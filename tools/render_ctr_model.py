"""
A small software renderer for the original's models (from YOUR OWN disc, local use only): turns a model of the shared pack or a level file into pictures seen from any angle,
with the game's own textures, palettes and vertex shading. The mod uses it to pre-render the weapons (docs/POWERUPS.md): a model that is not a box cannot be a Minecraft block
model, so it is shown as a sprite chosen by the angle it is seen from.

  python tools/render_ctr_model.py --pack bomb shield crate_tnt            (contact sheets in local-assets/ctr-sprites/models/)

Orthographic, nearest-texel, a z-buffer, both sides of a triangle drawn, texels of 0x0000 transparent, the colour table's shade applied as colour / 128 (128 unchanged).
"""
import argparse
import math
import os
import struct
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ctr_disc_textures as ct  # noqa: E402
import extract_ctr_sounds as sounds  # noqa: E402


class Prepared:
    """A model's triangles with their texture pictures, ready to be drawn from any side."""

    def __init__(self, vram, model, frame=0, up=2):
        """{up} is the coordinate the model's height is in: the level file's models have it in the third (what render_view draws as up), the shared model pack's (the weapons,
        the crates, the characters) in the second, so a pack model is turned a quarter about x, (x, y, z) -> (x, -z, y), which keeps its handedness: its top is then the
        view from above and its roll axis is still x."""
        self.verts = np.array(ct.mesh_vertices(model, frame), dtype=np.float64)
        self.mirror = True
        if up == 1:
            # The pack's models have their height in the second coordinate: a quarter turn about x, (x, y, z) -> (x, -z, y), puts it up, and render_view's own mirror (the game's
            # frame is left handed) is kept. Checked on two things that cannot be wrong together: the TNT crate's lettering reads the right way round on its sides, and the
            # bomb, which moves towards its model's -z, shows its spark (+x) on the LEFT from behind, as in the original's footage. Yaw 0 is the camera at +z of the model, which is
            # BEHIND something that travels towards -z.
            self.verts = np.stack([self.verts[:, 0], -self.verts[:, 2], self.verts[:, 1]], axis=1)
        self.tris = ct.mesh_triangles(model)
        table = ct.colour_table(model)
        self.table = table
        self.layouts = {}
        for _, tex, _, _ in self.tris:
            if tex and tex not in self.layouts:
                lay = ct.layout_bytes(model, tex)
                img = vram.texture(lay)
                if img is None:
                    continue
                c = struct.unpack("<BBHBBHBBBB", lay)
                self.layouts[tex] = (min(c[0], c[3], c[6], c[8]), min(c[1], c[4], c[7], c[9]), np.asarray(img), c)
        v = self.verts
        self.centre = (v.min(axis=0) + v.max(axis=0)) / 2.0
        self.radius = float(np.linalg.norm(v - self.centre, axis=1).max()) or 1.0


def render_view(p, yaw_deg, pitch_deg, size, margin=0.04, fit_bbox=False):
    """One picture: the model turned {yaw_deg} about its up axis (the game's third coordinate) and tipped {pitch_deg} towards the viewer, fitted to size x size."""
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    v = p.verts - p.centre
    # turn about the up axis (z), then tip about the screen's horizontal axis
    x1 = v[:, 0] * cy - v[:, 1] * sy
    y1 = v[:, 0] * sy + v[:, 1] * cy
    z1 = v[:, 2]
    if getattr(p, "mirror", True):
        x1 = -x1                           # the level models' axes run the other way round: without this the pictures are mirrored
    up = z1 * cp - y1 * sp
    depth = y1 * cp + z1 * sp            # towards the viewer is smaller
    if fit_bbox:
        # the projected outline fills the picture exactly (for the face of a box: the face is the picture)
        w, h = x1.max() - x1.min(), up.max() - up.min()
        scale = size / max(w, h, 1e-9)
        sx = (x1 - (x1.max() + x1.min()) / 2.0) * scale + size / 2.0
        sy_ = -(up - (up.max() + up.min()) / 2.0) * scale + size / 2.0
    else:
        scale = size * (1.0 - 2 * margin) / (2.0 * p.radius)
        sx = x1 * scale + size / 2.0
        sy_ = -up * scale + size / 2.0
    img = np.zeros((size, size, 4), dtype=np.uint8)
    zbuf = np.full((size, size), 1e18)
    for corner, tex, col, flags in p.tris:
        pts = [(sx[k], sy_[k], depth[k]) for k in corner]
        lay = p.layouts.get(tex)
        shade = np.ones(3)
        if p.table and col < len(p.table):
            shade = np.array(p.table[col], dtype=np.float64) / 128.0
        (ax, ay, az), (bx, by, bz), (cx, cy_, cz) = pts
        den = (by - cy_) * (ax - cx) + (cx - bx) * (ay - cy_)
        if abs(den) < 1e-9:
            continue
        x0, x1_ = max(0, int(math.floor(min(ax, bx, cx)))), min(size - 1, int(math.ceil(max(ax, bx, cx))))
        y0, y1_ = max(0, int(math.floor(min(ay, by, cy_)))), min(size - 1, int(math.ceil(max(ay, by, cy_))))
        if lay:
            umin, vmin, tex_img, c = lay
            uv = [(c[0], c[1]), (c[3], c[4]), (c[6], c[7])]
        for y in range(y0, y1_ + 1):
            for x in range(x0, x1_ + 1):
                a = ((by - cy_) * (x + .5 - cx) + (cx - bx) * (y + .5 - cy_)) / den
                b = ((cy_ - ay) * (x + .5 - cx) + (ax - cx) * (y + .5 - cy_)) / den
                g = 1 - a - b
                if a < -1e-6 or b < -1e-6 or g < -1e-6:
                    continue
                z = a * az + b * bz + g * cz
                if z >= zbuf[y, x]:
                    continue
                if lay:
                    u = a * uv[0][0] + b * uv[1][0] + g * uv[2][0]
                    w = a * uv[0][1] + b * uv[1][1] + g * uv[2][1]
                    iu = int(min(max(round(u - umin), 0), tex_img.shape[1] - 1))
                    iv = int(min(max(round(w - vmin), 0), tex_img.shape[0] - 1))
                    t = tex_img[iv, iu]
                    if t[3] == 0:
                        continue
                    rgb = np.minimum(t[:3] * shade, 255)
                else:
                    rgb = np.minimum(np.array([200, 200, 200]) * shade, 255)
                zbuf[y, x] = z
                img[y, x] = (int(rgb[0]), int(rgb[1]), int(rgb[2]), 255)
    return Image.fromarray(img, "RGBA")


def contact_sheet(p, size=96, yaws=8, pitch=20.0):
    sheet = Image.new("RGBA", (size * yaws, size), (70, 100, 140, 255))
    for i in range(yaws):
        sheet.alpha_composite(render_view(p, 360.0 * i / yaws, pitch, size), (i * size, 0))
    return sheet


def roll_atlas(p, size=64, frames=32, azimuths=8, pitch=20.0, phase=270.0):
    """A rolling model as an atlas of {azimuths} columns (the camera's side) by {frames} rows (one full turn about the model's own x axis, the axle: the game's bomb rolls
    with dir.x += 0x200 a frame) of pictures {size} px square. {p} must be a pack model made with up=1 (Prepared turns it so its height is up; the bomb travels towards
    its model's -z): column 0 (yaw 0) sees it from BEHIND, which is what a chase camera sees of a bomb going away, column 4 from the front; column k is seen with render_view's yaw 360 * k / {azimuths}. The pitch is from above (up is the model's own height). Checked against the original's
    footage: at {phase} degrees from behind the skull faces the camera and the spark is at the top left, which is how the footage shows a fired bomb, and row 0 is that. The centre is the model's origin (the bomb's body), so the body stays put and the fuse swings round it; one scale for every picture."""
    import copy
    q = copy.copy(p)
    q.centre = np.zeros(3)
    q.radius = float(np.linalg.norm(p.verts, axis=1).max()) or 1.0
    atlas = Image.new("RGBA", (size * azimuths, size * frames), (0, 0, 0, 0))
    for i in range(frames):
        a = math.radians(phase + 360.0 * i / frames)
        c, s = math.cos(a), math.sin(a)
        v = p.verts
        q.verts = np.stack([v[:, 0], v[:, 1] * c - v[:, 2] * s, v[:, 1] * s + v[:, 2] * c], axis=1)
        for k in range(azimuths):
            atlas.alpha_composite(render_view(q, 360.0 * k / azimuths, pitch, size, margin=0.02), (k * size, i * size))
    return atlas


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--disc")
    ap.add_argument("--pack", action="store_true", help="models of the shared pack (weapons, crates); default: Crash Cove's level file")
    ap.add_argument("models", nargs="*")
    ap.add_argument("--out", default=os.path.join(project, "local-assets", "ctr-sprites", "models"))
    ap.add_argument("--size", type=int, default=96)
    ap.add_argument("--pitch", type=float, default=20.0)
    args = ap.parse_args()
    disc_path = args.disc or sounds.find_disc(project)
    if not disc_path or not os.path.exists(disc_path):
        sys.exit("No disc image found. Pass --disc \"path\\to\\CTR.bin\".")
    big = ct.BigFile(sounds.Disc(disc_path))
    vram = ct.Vram()
    vram.load(big.read(ct.SHARED_VRM))
    if args.pack:
        models = ct.pack_models(big.read(ct.SHARED_MPK))
    else:
        vram.load(big.read(ct.CRASH_COVE_1P_VRM))
        models = ct.level_models(big.read(ct.CRASH_COVE_1P_LEV))
    os.makedirs(args.out, exist_ok=True)
    for name in args.models or list(models):
        try:
            p = Prepared(vram, models[name])
        except (ValueError, KeyError) as e:
            print("%-16s skipped: %s" % (name, e))
            continue
        contact_sheet(p, args.size, 8, args.pitch).save(os.path.join(args.out, name + ".png"))
        print("%-16s -> %s" % (name, os.path.join(args.out, name + ".png")))


if __name__ == "__main__":
    main()
