"""
Browse CTR's textures on YOUR OWN disc image: writes a local web page (and the pictures it shows) so you can look through what is there and tell the
extractors (extract_ctr_textures.py) which picture to use.

  python tools/browse_ctr_assets.py            (finds the disc image in the project folder)
  python tools/browse_ctr_assets.py --disc "D:/CTR.bin" --open

Writes local-assets/ctr-browse/index.html (git-ignored, for this machine only: the pictures are Sony / Naughty Dog's, never commit or share them):
  * every icon of packs\\shared.mpk by name (HUD pictures, fonts, lamps, item icons, tyres, effects);
  * every model of Crash Cove's level file (crates, fruit, start banner ...) with each texture its draw commands use: its index in the model's
    table, how often it is used, its size, and the raw texture layout (tpage / palette / corners) so it can be named exactly; and the model's colour table
    (the shades the game multiplies the textures with: this is where the bevel shading of the crate trims comes from);
  * the whole video memory (1024 x 512) of the shared pack, and of the shared pack with the level's, as pictures, drawn as 15-bit colour (paletted
    textures look wrong there, as they need their palette: use the entries above for the right look).
Everything else on the disc (the other levels, the racers) uses the same readers (ctr_disc_textures.py): ask for what you want added.
"""
import argparse
import html
import os
import struct
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ctr_disc_textures as ct  # noqa: E402
import extract_ctr_sounds as sounds  # noqa: E402


def save(arr, path, scale=1):
    im = Image.fromarray(arr, "RGBA")
    if scale != 1:
        im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
    im.save(path)


def vram_picture(vram):
    w = vram.mem
    r = (w & 31).astype(np.uint32) * 255 // 31
    g = ((w >> 5) & 31).astype(np.uint32) * 255 // 31
    b = ((w >> 10) & 31).astype(np.uint32) * 255 // 31
    out = np.stack([r, g, b, np.full(w.shape, 255)], axis=-1).astype(np.uint8)
    return out


def describe(layout):
    u0, v0, clut, u1, v1, tpage, u2, v2, u3, v3 = struct.unpack("<BBHBBHBBBB", layout)
    depth = {0: "4 bit", 1: "8 bit", 2: "15 bit"}.get((tpage >> 7) & 3, "?")
    return ("tpage 0x%04X (page x %d, y %d, %s), palette 0x%04X, corners (%d,%d) (%d,%d) (%d,%d) (%d,%d)"
            % (tpage, (tpage & 15) * 64, ((tpage >> 4) & 1) * 256, depth, clut, u0, v0, u1, v1, u2, v2, u3, v3))


def colour_table(model):
    data = model["data"]
    hdr = model["header"]
    ptr = struct.unpack_from("<I", data, hdr + 0x2C)[0]
    count = struct.unpack_from("<I", data, model["cmds"])[0]
    out = []
    for i in range(min(count, 64)):
        r, g, b, _ = struct.unpack_from("4B", data, ptr + i * 4)
        out.append((r, g, b))
    return out


def main():
    project = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--disc", help="the disc image; default: found in the project folder")
    ap.add_argument("--out", default=os.path.join(project, "local-assets", "ctr-browse"))
    ap.add_argument("--open", action="store_true", help="open the page in your browser when done")
    args = ap.parse_args()
    disc_path = args.disc or sounds.find_disc(project)
    if not disc_path or not os.path.exists(disc_path):
        sys.exit("No disc image found. Pass --disc \"path\\to\\CTR.bin\".")
    disc = sounds.Disc(disc_path)
    big = ct.BigFile(disc)
    out = args.out
    os.makedirs(os.path.join(out, "icons"), exist_ok=True)
    os.makedirs(os.path.join(out, "models"), exist_ok=True)

    vram = ct.Vram()
    vram.load(big.read(ct.SHARED_VRM))
    save(vram_picture(vram), os.path.join(out, "vram_shared.png"))
    icon_rows = []
    for name, lay in ct.icons(big.read(ct.SHARED_MPK)):
        tex = vram.texture(lay)
        if tex is None:
            continue
        fn = "icons/%s.png" % "".join(ch if ch.isalnum() or ch in "-_" else "_" for ch in name)
        # keep the file name unique when two icons share a name
        n = 1
        while os.path.exists(os.path.join(out, fn)) and fn in [r[0] for r in icon_rows]:
            n += 1
            fn = fn.replace(".png", "_%d.png" % n) if n == 2 else fn.replace("_%d.png" % (n - 1), "_%d.png" % n)
        save(tex, os.path.join(out, fn), 2 if tex.shape[0] <= 64 else 1)
        icon_rows.append((fn, name, "%d x %d" % (tex.shape[1], tex.shape[0]), describe(lay)))

    vram.load(big.read(ct.CRASH_COVE_1P_VRM))
    save(vram_picture(vram), os.path.join(out, "vram_shared_plus_coco1.png"))
    models = ct.level_models(big.read(ct.CRASH_COVE_1P_LEV))
    model_rows = []
    for mname, m in models.items():
        used, count = ct.model_textures(m)
        swatches = colour_table(m)
        entries = []
        for idx, uses in used:
            lay = ct.layout_bytes(m, idx)
            tex = vram.texture(lay)
            if tex is None:
                continue
            fn = "models/%s_tex%d.png" % (mname, idx)
            save(tex, os.path.join(out, fn), 4 if tex.shape[0] <= 32 else (2 if tex.shape[0] <= 64 else 1))
            entries.append((fn, idx, uses, "%d x %d" % (tex.shape[1], tex.shape[0]), describe(lay)))
        model_rows.append((mname, entries, swatches))

    css = ("body{background:#14121c;color:#e8e4f0;font-family:Segoe UI,Arial,sans-serif;margin:24px}h1,h2{color:#ffd21f}"
           ".grid{display:flex;flex-wrap:wrap;gap:12px}.card{background:#221e2e;border:1px solid #3a3450;padding:8px;width:210px;font-size:12px}"
           ".card img{background:repeating-conic-gradient(#3a3646 0% 25%,#2a2634 0% 50%) 50%/16px 16px;image-rendering:pixelated;max-width:194px;display:block;margin-bottom:6px}"
           "code{color:#9fe8b0;font-size:11px}.sw{display:inline-block;width:18px;height:18px;margin:1px;border:1px solid #000}"
           "a{color:#8fd0ff}")
    h = ["<!doctype html><meta charset=utf-8><title>CTR disc textures</title><style>%s</style>" % css,
         "<h1>CTR textures on this disc</h1><p>Local only: do not share (Sony / Naughty Dog's art). Disc: %s</p>" % html.escape(os.path.basename(disc_path)),
         "<p><a href='#models'>Models of Crash Cove's level</a> | <a href='#icons'>Icons (shared.mpk)</a> | <a href='#vram'>Video memory</a></p>",
         "<h2 id='models'>Models of Crash Cove's level (crates, fruit ...)</h2>"]
    for mname, entries, swatches in model_rows:
        h.append("<h3>%s</h3><p>colour table (%d): %s</p><div class=grid>" % (
            html.escape(mname), len(swatches), "".join("<span class=sw title='%d,%d,%d' style='background:rgb(%d,%d,%d)'></span>" % (c + c) for c in swatches)))
        for fn, idx, uses, size, desc in entries:
            h.append("<div class=card><img src='%s'><b>#%d</b> used %dx, %s<br><code>%s</code></div>" % (fn, idx, uses, size, html.escape(desc)))
        h.append("</div>")
    h.append("<h2 id='icons'>Icons of packs\\shared.mpk</h2><div class=grid>")
    for fn, name, size, desc in icon_rows:
        h.append("<div class=card><img src='%s'><b>%s</b> %s<br><code>%s</code></div>" % (fn, html.escape(name), size, html.escape(desc)))
    h.append("</div><h2 id='vram'>Video memory</h2><p>1024 x 512, every word read as 15-bit colour. Paletted textures look scrambled here.</p>"
             "<h3>shared.vrm</h3><img src='vram_shared.png' style='max-width:100%'><h3>shared + Crash Cove</h3><img src='vram_shared_plus_coco1.png' style='max-width:100%'>")
    with open(os.path.join(out, "index.html"), "w", encoding="utf-8") as f:
        f.write("\n".join(h))
    print("Written:", os.path.join(out, "index.html"), "|", len(icon_rows), "icons,", len(model_rows), "models")
    if args.open:
        os.startfile(os.path.join(out, "index.html"))


if __name__ == "__main__":
    main()
