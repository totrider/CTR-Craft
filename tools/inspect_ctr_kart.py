"""
Take the racers' models (the kart body and the driver in one: the high detail ones, BIGFILE 242-257) and the wheel sprites out of YOUR OWN disc image for inspection, and measure them.

  python tools/inspect_ctr_kart.py                      (finds the disc image in the project folder)

Writes into local-assets/ctr-kart/ (git-ignored, this machine only: the art is Sony / Naughty Dog's, never commit or share it):
  <racer>/<racer>.obj, .mtl, tex_<n>.png, triangles.txt   the model at the middle steering frame, in blocks (1 block = a crate = 108.8 units), the same layout as
                                                          tools/export_ctr_mesh.py writes (open in Blender: File > Import > Wavefront)
  tires/tire00.png ... tire16.png, trtire00.png ...       the wheel sprites (the game draws a kart's wheels as pictures: rotation frames, not as meshes)
  tires/tires_sheet.png                                   all of them in one picture
  report.txt                                              sizes of every racer, and what the steering animation does to the driver's parts (measured from the frames)
The model has no wheels (they are the sprites). Its animations: 0 drive (21 steering frames, frame 0 = full left, 10 = straight, 20 = full right), 1 stationary,
2 (see VehFrame.c), 3 airborne. Needs numpy and Pillow.
"""
import math
import os
import struct
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ctr_disc_textures as ct  # noqa: E402
import export_ctr_mesh as ex  # noqa: E402
import extract_ctr_sounds as sounds  # noqa: E402

RACERS = ["crash", "cortex", "tiny", "coco", "ngin", "dingo", "polar", "pura", "pinstripe", "papu", "roo", "joe", "ntropy", "pen", "fake", "oxide"]
BLOCK = 108.8
RACER_HI = 242              # BIGFILE index of the first high detail racer model (BI_RACERMODELHI; medium 308, low 332)


def steering_report(model):
    """How the drive animation moves the driver: per height band, the sideways shift of the parts that move and their turn about the vertical axis (Procrustes in x, z),
    between the straight frame and the two locks. In the model's own units (the second coordinate is up)."""
    n = ct.frame_count(model)
    frames = [np.array(ct.mesh_vertices(model, f)) for f in range(n)]
    mid = frames[n // 2]
    lines = ["drive animation: %d frames (0 = full left, %d = straight, %d = full right)" % (n, n // 2, n - 1)]
    for f in (0, n - 1):
        d = frames[f] - mid
        mag = np.linalg.norm(d, axis=1)
        lines.append("frame %d against the straight frame: %d of %d vertices move" % (f, int((mag > 0.5).sum()), len(mag)))
        for lo, hi, what in ((0, 40, "hands / wheel / legs"), (40, 60, "torso"), (60, 75, "chest"), (75, 120, "head")):
            sel = (mid[:, 1] >= lo) & (mid[:, 1] < hi) & (mag > 0.5)
            if not sel.any():
                continue
            a = mid[sel][:, [0, 2]]
            b = frames[f][sel][:, [0, 2]]
            a0, b0 = a - a.mean(0), b - b.mean(0)
            ang = math.degrees(math.atan2((a0[:, 0] * b0[:, 1] - a0[:, 1] * b0[:, 0]).sum(), (a0 * b0).sum()))
            shift = (frames[f][sel] - mid[sel]).mean(0)
            lines.append("  height %3d-%3d (%s): %2d vertices, mean shift x %.1f y %.1f z %.1f units, turn about the vertical %.1f degrees" % (lo, hi, what, sel.sum(), shift[0], shift[1], shift[2], ang))
    return lines


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(project, "local-assets", "ctr-kart")
    disc_path = sounds.find_disc(project)
    if not disc_path:
        sys.exit("No disc image found in the project folder.")
    big = ct.BigFile(sounds.Disc(disc_path))
    vram = ct.Vram()
    vram.load(big.read(ct.SHARED_VRM))
    mpk = big.read(ct.SHARED_MPK)
    models = {}
    for i in range(16):                                  # BI_RACERMODELHI: one file per racer (the high detail model, ~270 triangles); the shared pack's are the lowest detail
        for name, m in ct.pack_models(big.read(RACER_HI + i)).items():
            models[name] = m
    report = ["Racers (model pack), the middle steering frame; units are level units, 108.8 to a block (a crate). x across, y up, z along (the front is +z in the file)."]
    for name in RACERS:
        if name not in models:
            continue
        m = models[name]
        frame = ct.frame_count(m) // 2
        try:
            ex.export(vram, name, m, os.path.join(out, name), 1.0 / BLOCK, frame, 1)
        except ValueError as e:
            report.append("%-10s skipped: %s" % (name, e))
            continue
        v = np.array(ct.mesh_vertices(m, frame))
        size = v.max(0) - v.min(0)
        report.append("%-10s %3d triangles: width %5.1f, height %5.1f, length %5.1f units = %.2f x %.2f x %.2f blocks" % (name, len(ct.mesh_triangles(m)), size[0], size[1], size[2], size[0] / BLOCK, size[1] / BLOCK, size[2] / BLOCK))
    report += [""] + steering_report(models["crash"])
    # the wheel sprites
    os.makedirs(os.path.join(out, "tires"), exist_ok=True)
    pics = []
    for name, layout in ct.icons(mpk):
        if name.startswith("tire") or name.startswith("trtire"):
            img = vram.texture(layout)
            if img is None:
                continue
            Image.fromarray(img, "RGBA").save(os.path.join(out, "tires", name + ".png"))
            pics.append((name, img))
    if pics:
        w = max(p.shape[1] for _, p in pics)
        h = max(p.shape[0] for _, p in pics)
        cols = 17
        rows = (len(pics) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * (w + 2), rows * (h + 2)), (70, 100, 140, 255))
        for i, (_, p) in enumerate(pics):
            sheet.alpha_composite(Image.fromarray(p, "RGBA"), ((i % cols) * (w + 2), (i // cols) * (h + 2)))
        sheet.save(os.path.join(out, "tires", "tires_sheet.png"))
        report += ["", "Wheel sprites: %d pictures of up to %d x %d texels (tire00-16: 17 rotation frames; trtire00-16: the same for the other wheel set)" % (len(pics), w, h)]
    with open(os.path.join(out, "report.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(report))
    print("written to", out)


if __name__ == "__main__":
    main()
