"""
Export models from YOUR OWN CTR disc image as Wavefront OBJ files (with UVs, materials and textures) that open in Blender, to compare them with the
mod's own models.

  python tools/export_ctr_mesh.py                          (the crates and the Wumpa fruit of Crash Cove's level file)
  python tools/export_ctr_mesh.py --models crate_question crate_fruit fruit --scale 0.02

Writes local-assets/ctr-mesh/<model>/<model>.obj, <model>.mtl, tex_<n>.png and triangles.txt (git-ignored, this machine only: the art is Sony / Naughty
Dog's, never commit or share it).

In Blender: File > Import > Wavefront (.obj). The import settings' forward / up axes are Blender's defaults (-Z forward, Y up), which fit the file.
  * Axes: the model is turned so +Y is up (the game's up is its third coordinate); the raw coordinates are in triangles.txt.
  * Units: the file is in the game's level units times --scale. The default 0.02 is one block per 50 units, the mod's scale (a crate comes out 2.18 wide).
  * UVs: every triangle's corner uses the corner of its texture's layout exactly as the game draws it; each texture index of the model is one material
    (tex_<n>.png is that layout's rectangle of the texture page with its palette applied). A texel of 0x0000 is transparent. Faces are not smoothed.
  * Vertex colours: the game shades a model with a colour table (the crates have five greys, 128 = unchanged, so brighter and darker faces); the OBJ carries
    that as vertex colours (value / 128, capped at 1) so Blender can show or multiply it (Vertex Color node). The table is also in triangles.txt.
  * Winding: triangles are turned to face outwards from the model's centre (the game's own winding flags are in triangles.txt).
Format knowledge: see ctr_disc_textures.py.
"""
import argparse
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ctr_disc_textures as ct  # noqa: E402
import extract_ctr_sounds as sounds  # noqa: E402
from PIL import Image  # noqa: E402


def export(vram, name, model, out_dir, scale, frame=0):
    os.makedirs(out_dir, exist_ok=True)
    verts = ct.mesh_vertices(model, frame)
    tris = ct.mesh_triangles(model)
    table = ct.colour_table(model)
    cx = sum(v[0] for v in verts) / len(verts)
    cy = sum(v[1] for v in verts) / len(verts)
    cz = sum(v[2] for v in verts) / len(verts)

    layouts = {}
    for _, tex, _, _ in tris:
        if tex and tex not in layouts:
            lay = ct.layout_bytes(model, tex)
            img = vram.texture(lay)
            if img is None:
                continue
            u0, v0, clut, u1, v1, tpage, u2, v2, u3, v3 = struct.unpack("<BBHBBHBBBB", lay)
            us, vs = (u0, u1, u2, u3), (v0, v1, v2, v3)
            layouts[tex] = (min(us), min(vs), img.shape[1], img.shape[0], lay)
            Image.fromarray(img, "RGBA").save(os.path.join(out_dir, "tex_%d.png" % tex))

    obj = ["# CTR %s exported from the owner's own disc (local use only). Units: level units x %g." % (name, scale), "mtllib %s.mtl" % name, "o %s" % name]
    lines = []
    vt_lines = []
    faces = []
    vn_lines = []
    n_out = 0
    for i, (corner, tex, col, flags) in enumerate(tris):
        pts = [verts[k] for k in corner]
        lay = layouts.get(tex)
        uvs = []
        if lay:
            _, _, clut, _, _, tpage, *_ = struct.unpack("<BBHBBHBBBB", lay[4])
            c = struct.unpack("<BBHBBHBBBB", lay[4])
            corners = [(c[0], c[1]), (c[3], c[4]), (c[6], c[7])]
            umin, vmin, w, h, _ = lay
            uvs = [((u - umin + 0.5) / w, 1.0 - (v - vmin + 0.5) / h) for u, v in corners]
        else:
            uvs = [(0.0, 0.0)] * 3
        # outward winding
        ax, ay, az = (pts[1][k] - pts[0][k] for k in range(3))
        bx, by, bz = (pts[2][k] - pts[0][k] for k in range(3))
        nx, ny, nz = ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx
        mx = sum(p[0] for p in pts) / 3 - cx
        my = sum(p[1] for p in pts) / 3 - cy
        mz = sum(p[2] for p in pts) / 3 - cz
        order = [0, 1, 2]
        if nx * mx + ny * my + nz * mz < 0:
            order = [0, 2, 1]
            nx, ny, nz = -nx, -ny, -nz
        ln = (nx * nx + ny * ny + nz * nz) ** 0.5 or 1.0
        vn_lines.append("vn %.5f %.5f %.5f" % (nx / ln, nz / ln, -ny / ln))
        shade = (1.0, 1.0, 1.0)
        if table and col < len(table):
            shade = tuple(min(1.0, c / 128.0) for c in table[col])
        idx = []
        for k in order:
            p = pts[k]
            # the game's up is the third coordinate: turn the model so +Y is up (a plain rotation about X, nothing mirrored): (x, y, z) -> (x, z, -y)
            lines.append("v %.5f %.5f %.5f %.3f %.3f %.3f" % ((p[0] * scale, p[2] * scale, -p[1] * scale) + shade))
            vt_lines.append("vt %.5f %.5f" % uvs[k])
            idx.append(len(lines))
        n_out += 1
        faces.append((tex, "f %d/%d/%d %d/%d/%d %d/%d/%d" % (idx[0], idx[0], n_out, idx[1], idx[1], n_out, idx[2], idx[2], n_out)))
    obj += lines + vt_lines + vn_lines
    cur = None
    for tex, f in faces:
        if tex != cur:
            obj.append("usemtl tex_%d" % tex)
            cur = tex
        obj.append(f)
    with open(os.path.join(out_dir, name + ".obj"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(obj) + "\n")
    mtl = []
    for tex in sorted(layouts):
        mtl += ["newmtl tex_%d" % tex, "Ka 1 1 1", "Kd 1 1 1", "Ks 0 0 0", "d 1", "illum 1", "map_Kd tex_%d.png" % tex, "map_d tex_%d.png" % tex, ""]
    with open(os.path.join(out_dir, name + ".mtl"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(mtl))
    with open(os.path.join(out_dir, "triangles.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# %s: %d vertices, %d triangles. colour table (RGB bytes; 128 = unchanged): %s\n" % (name, len(verts), len(tris), table))
        f.write("# triangle: corner vertices (level units) | texture index | colour index | flags | layout (%s)\n" % "u0 v0 palette u1 v1 tpage u2 v2 u3 v3")
        for corner, tex, col, flags in tris:
            f.write("%s | tex %d | col %d | %s | %s\n" % ([tuple(round(c, 1) for c in verts[k]) for k in corner], tex, col, flags,
                                                           ct.layout_bytes(model, tex).hex() if tex else "-"))
    return len(verts), len(tris), len(layouts)


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--disc", help="the disc image; default: found in the project folder")
    ap.add_argument("--out", default=os.path.join(project, "local-assets", "ctr-mesh"))
    ap.add_argument("--models", nargs="*", default=None, help="model names (or 'all'); default: the crates and the fruit of Crash Cove's level file, or the weapons with --pack")
    ap.add_argument("--pack", action="store_true", help="take the models from the shared model pack (the weapons: beakers, bomb, warp orb, cloud, shield, shockwaves, the TNT / Nitro crates) instead of Crash Cove")
    ap.add_argument("--frame", type=int, default=0, help="for an animated model (the fruit's 'spin'): which frame")
    ap.add_argument("--scale", type=float, default=0.02, help="units to Blender metres: 0.02 = one block per 50 level units")
    args = ap.parse_args()
    disc_path = args.disc or sounds.find_disc(project)
    if not disc_path or not os.path.exists(disc_path):
        sys.exit("No disc image found. Pass --disc \"path\\to\\CTR.bin\".")
    disc = sounds.Disc(disc_path)
    big = ct.BigFile(disc)
    vram = ct.Vram()
    vram.load(big.read(ct.SHARED_VRM))
    if args.pack:
        models = ct.pack_models(big.read(ct.SHARED_MPK))
        default = ["crate_tnt", "crate_nitro", "crate_explosion", "beakergreen", "beakerred", "bomb", "warpball", "cloud", "shockwavered", "shockwavegreen",
                   "shield", "shielddark", "shield_green", "warpedburst", "akumouth", "akubeam", "token"]
    else:
        vram.load(big.read(ct.CRASH_COVE_1P_VRM))
        models = ct.level_models(big.read(ct.CRASH_COVE_1P_LEV))
        default = ["crate_question", "crate_fruit", "fruit"]
    names = list(models) if args.models == ["all"] else (args.models or default)
    for name in names:
        if name not in models:
            print("no model %r (the level has: %s)" % (name, ", ".join(models)))
            continue
        try:
            v, t, m = export(vram, name, models[name], os.path.join(args.out, name), args.scale, args.frame)
        except ValueError as e:
            print("%-16s skipped: %s" % (name, e))
            continue
        print("%-16s %3d vertices %3d triangles %2d textures -> %s" % (name, v, t, m, os.path.join(args.out, name, name + ".obj")))


if __name__ == "__main__":
    main()
