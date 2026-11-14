"""
A CTR model as a Minecraft model for a LOCAL resource pack (from YOUR OWN disc, never in the jar): an OBJ that NeoForge's own loader ({"loader": "neoforge:obj"}) reads, one
atlas texture, and the model JSON that ties them. Used by tools/extract_ctr_textures.py for N. Brio's Beakers.

  files = mesh_pack_files(vram, model, "beaker_green", blocks_per_unit=1 / 108.8)

returns {path under assets/ctrcraft: image, text or dict (JSON)}:
  textures/item/<name>_original.png   every triangle's picture (the texture layout it uses, texel 0x0000 transparent) in a cell of its own with the game's shading baked in
                                      (the colour table's grey / 128 per triangle, capped at full), one pixel of the edge repeated round each cell so no neighbour bleeds in
  models/item/<name>.obj, <name>.mtl  one material; the triangles wound outwards, all with the same normal (straight up, see below); units are blocks (the model's level units
                                      times blocks_per_unit), centred on the block in x and z with its base at y = 0, +Y up, as the stand-in models are
  models/item/pickup_<name>.json      the model the renderer fetches, a loader entry; display settings copied from the stand-in's
and, for a model with an animation, the triangles that move in it (the beaker's bubbles) are NOT in the OBJ; they come as billboards:
  textures/entity/<name>_bubbles.png  the pictures of the moving triangles (each cut by its own uv triangle, shaded as above) in cells of a grid
  beaker/<name>_bubbles.json          {frames, texture, bubbles: [{uv: [u0, v0, u1, v1], width, height, centres: [[x, y, z] per frame]}]} in blocks, x and z from the middle of
                                      the flask, y from its base: the picture's size and where its middle is in every frame (the game steps one frame per tick of 30 Hz)

Every face of the OBJ gets the normal (0, 1, 0): NeoForge's OBJ loader sets a quad's direction to the axis nearest its normal (ObjModel.makeQuad), and the entity renderer
lights a quad by that one of six directions, so with the true normals the slanted triangles of one face land on different axes and are lit differently (odd bright and dark
triangles in the game, clean in Blender). The game's own shading is already baked into the atlas, so a uniform light shows exactly that.
"""
import struct

import numpy as np
from PIL import Image

import ctr_disc_textures as ct

UP = 1                      # the shared pack's models have their height in the second coordinate (their scale array shows it: 848, 1585, 848)
# The model's colour table, shaded per vertex, would tint the beaker (brown and blue at the base and the neck: the table's entries 0 and 8), and the original's flask is not tinted
# like that (its HUD icon is a green flask with a pale neck): so by default the pictures are used as they are, plus their transparency. True gives the Gouraud shading of the
# table (the corner colours interpolated across each triangle).
SHADE_FROM_TABLE = False
GOURAUD_CELL = 12            # px of the picture of an untextured triangle
MOVES = 0.5                 # a vertex that moves more than this (level units) in some frame is part of the animation


def _shade(table, col):
    if table and col < len(table):
        return np.array(table[col], dtype=np.float64) / 128.0
    return np.ones(3)


def _triangle_sprite(img, uv_local, shade):
    """The part of a texture picture inside its triangle (the pixels whose centre is inside, not transparent), cropped: (RGBA array, bounding box x0, y0, x1, y1 in texels)."""
    h, w = img.shape[:2]
    (ax, ay), (bx, by), (cx, cy) = uv_local
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    mask = np.zeros((h, w), dtype=bool)
    if abs(den) > 1e-9:
        for y in range(h):
            for x in range(w):
                px, py = x + 0.5, y + 0.5
                a = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
                b = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
                if a >= -0.02 and b >= -0.02 and 1 - a - b >= -0.02:
                    mask[y, x] = True
    mask &= img[:, :, 3] > 0
    ys, xs = np.where(mask)
    if len(ys) == 0:
        return None
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    rgb = np.minimum(img[:, :, :3].astype(np.float64) * shade, 255).astype(np.uint8)
    out = np.dstack([rgb, np.where(mask, img[:, :, 3], 0).astype(np.uint8)])
    return out[y0:y1, x0:x1], (x0, y0, x1, y1)


def _bake(img, stp, uv_local, shades, abr, additive=False):
    """A triangle's picture with its Gouraud shading baked in and its see-through texels given an alpha: shades are the three corners' colours (colour / 128), abr the blend mode
    of its texture page (0: half and half, 1: added, 2: subtracted, 3: a quarter added)."""
    h, w = img.shape[:2]
    (ax, ay), (bx, by), (cx, cy) = uv_local
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    shade = np.ones((h, w, 3)) * np.mean(shades, axis=0)
    if abs(den) > 1e-9:
        ys, xs = np.mgrid[0:h, 0:w]
        px, py = xs + 0.5, ys + 0.5
        a = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
        b = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
        wts = np.clip(np.stack([a, b, 1 - a - b], axis=-1), 0.0, None)         # outside the triangle: the nearest corners' blend
        wts /= wts.sum(axis=-1, keepdims=True)
        shade = wts @ np.array(shades)
    rgb = np.minimum(img[:, :, :3].astype(np.float64) * shade, 255).astype(np.uint8)
    if additive:
        # the picture is ADDED to the screen (the renderer's additive blend): the colour is the texel times the vertex colours, and black adds nothing, so only the texels the picture leaves out are see-through
        alpha = np.where(img[:, :, 3] > 0, 255, 0).astype(np.uint8)
        return np.dstack([rgb, alpha])
    alpha = np.where(stp & (img[:, :, 3] > 0), 176 if abr == 1 else 128, img[:, :, 3]).astype(np.uint8)
    return np.dstack([rgb, alpha])


def _outward(corners, pts):
    """For each triangle (vertex indices into pts), whether to turn it round so that all of them wind outwards. Triangles that share an edge must run along it in opposite
    directions (that orients a connected piece consistently), and each piece is then turned as a whole so that its volume (the divergence theorem) is positive; a piece too small
    to have one falls back to the old test against the middle of the model. Returns a list of bools: True means swap two corners."""
    n = len(corners)
    edges = {}
    for t, (a, b, c) in enumerate(corners):
        for u, v in ((a, b), (b, c), (c, a)):
            edges.setdefault(frozenset((u, v)), []).append((t, u, v))
    flip = [None] * n
    comp = [-1] * n
    pieces = 0
    for start in range(n):
        if flip[start] is not None:
            continue
        flip[start] = False
        comp[start] = pieces
        stack = [start]
        while stack:
            t = stack.pop()
            a, b, c = corners[t]
            if flip[t]:
                b, c = c, b
            for u, v in ((a, b), (b, c), (c, a)):
                for t2, u2, v2 in edges[frozenset((u, v))]:
                    if t2 == t or flip[t2] is not None:
                        continue
                    flip[t2] = (u2 == u and v2 == v)           # it runs the same way as this one along the shared edge: turn it
                    comp[t2] = pieces
                    stack.append(t2)
        pieces += 1
    mid = pts.mean(axis=0)
    for k in range(pieces):
        ids = [t for t in range(n) if comp[t] == k]
        radial = 0.0
        vol = 0.0
        for t in ids:
            a, b, c = corners[t]
            if flip[t]:
                b, c = c, b
            p0, p1, p2 = pts[a], pts[b], pts[c]
            nrm = np.cross(p1 - p0, p2 - p0)                              # twice the area, as a vector
            centre = (p0 + p1 + p2) / 3.0
            radial += float(np.dot(nrm, np.array([centre[0] - mid[0], 0.0, centre[2] - mid[2]])))
            vol += float(np.dot(p0, np.cross(p1, p2)))
        # a model that is a tube round a vertical axis (a flask, open at both ends: the volume of an open surface means nothing): outwards is away from the axis
        sign = radial if abs(radial) > 1e-9 else vol
        if sign < 0:
            for t in ids:
                flip[t] = not flip[t]
    return flip


def _gouraud_cell(colours, size=GOURAUD_CELL):
    """The picture of an untextured Gouraud triangle (the Wumpa fruit): the three corner colours (RGB bytes of the model's colour table, used as they are) blended across a small
    square whose corners are the triangle's corners; the triangle's uv then maps onto it."""
    ys, xs = np.mgrid[0:size, 0:size]
    b = xs / (size - 1.0)
    c = ys / (size - 1.0)
    w = np.clip(np.stack([1 - b - c, b, c], axis=-1), 0.0, None)          # outside the triangle: the nearest corners' blend
    w /= w.sum(axis=-1, keepdims=True)
    rgb = np.clip(w @ np.array(colours, dtype=np.float64), 0, 255).astype(np.uint8)
    return np.dstack([rgb, np.full((size, size), 255, dtype=np.uint8)])


def mesh_pack_files(vram, model, name, blocks_per_unit, frame=0, up=UP, animated=True, honour_cull=False, exclude=(), pivot_ids=None, shade=None, translucent=True, kart_frame=False, pivot_point=None, additive=False, smooth=0, rim_zero=None, tri_override=None, gain=None):
    """{up}: the coordinate the model's height is in (1: the shared pack's models, 2: the level files', Wumpa fruit and crates; then the horizontal pair is turned and x flipped so
    the left handed game frame becomes Minecraft's right handed one). {animated}: False takes one frame of the model whole (the fruit's spin is a turn the renderer does itself). {exclude}: triangle indices left out (the bomb's spark is a billboard). {pivot_ids}: when given, the model's origin is the middle of those triangles' vertices, put at (0.5, 0.5, 0.5) of the block (a thing that rolls: the bomb), not the base; {translucent}: False ignores the textures' semi-transparency bit (the bomb is solid); {shade}: True bakes the colour table's Gouraud shading (default SHADE_FROM_TABLE). {kart_frame}: the model is in the kart's frame (the game's +z forward becomes the mod's -z forward: taken as it is: the first guess, z flipped, drew the flame's wide end into the kart). {pivot_point}: a point of the model (before turning) that becomes the origin, at (0.5, 0.5, 0.5) of the block. {additive}: the game adds the model's picture to the screen (the boost flame): black is nothing, so the baked alpha is the brightness and the colour is brought up to full. {smooth}: a picture smaller than this many texels is blown up (nearest) to about this size before its vertex colours are baked, so a flat 4 texel picture can carry a smooth gradient (the flame's soft edge). {tri_override}: {triangle index: (texture number to use instead, [three corner colours, 0-255 scale of the game's 128 = 1])}: a triangle drawn with another triangle's picture and colours of its own (the flame's shell, to cover the gaps its own triangles leave). {gain}: (triangle indices, factor): those triangles' vertex colours are multiplied by the factor (a brighter flame cone). {rim_zero}: (vertex indices, triangle indices): in those triangles those vertices' colours are black (a soft rim; the flame's outer ring). {honour_cull}: the game's per-triangle "cull" flag is obeyed, a triangle without it is written both ways round."""
    nframes = ct.frame_count(model)
    frames = [ct.mesh_vertices(model, f) for f in range(nframes)] if (animated and nframes > 1) else [ct.mesh_vertices(model, frame)]
    tris = ct.mesh_triangles(model)
    table = ct.colour_table(model)
    axes = [a for a in range(3) if a != up]            # the two horizontal ones: (x, z) of the output, in the model's order

    def to_xyz(v):
        # the game's frame is left handed (the crate lettering and the bomb in the footage show it), Minecraft's is right handed: x is flipped
        if kart_frame and up == 1:
            return np.array([v[0], v[1], v[2]], dtype=np.float64)
        if up == 2:
            return np.array([-v[0], v[2], -v[1]], dtype=np.float64)            # a reflection, as x flipped is for the pack's models (det -1)
        return np.array([-v[axes[0]], v[up], v[axes[1]]], dtype=np.float64)

    pts_all = [np.array([to_xyz(v) for v in fr]) for fr in frames]
    moving = set()
    if len(frames) > 1:
        spread = np.abs(np.array(pts_all) - pts_all[0]).max(axis=(0, 2))
        moving = set(int(k) for k in np.where(spread > MOVES)[0])
    bubble_ids = [i for i, (corner, tex, col, fl) in enumerate(tris) if moving and all(k in moving for k in corner)]
    static_ids = [i for i in range(len(tris)) if i not in bubble_ids and i not in exclude]

    # the flask is centred and based from its static triangles alone
    static_pts = np.array([pts_all[0][k] for i in static_ids for k in tris[i][0]])
    lo, hi = static_pts.min(axis=0), static_pts.max(axis=0)
    offset = np.array([(lo[0] + hi[0]) / 2.0, lo[1], (lo[2] + hi[2]) / 2.0])
    shift = np.array([0.5, 0.0, 0.5])
    if pivot_point is not None:
        offset = to_xyz(np.array(pivot_point, dtype=np.float64))
        shift = np.array([0.5, 0.5, 0.5])
    if pivot_ids is not None:
        offset = np.array([pts_all[0][k] for i in pivot_ids for k in tris[i][0]]).mean(axis=0)
        shift = np.array([0.5, 0.5, 0.5])
    use_shade = SHADE_FROM_TABLE if shade is None else shade

    def tf(p):
        return (p - offset) * blocks_per_unit            # blocks, x and z from the middle, y from the base

    pts = np.array([tf(p) + shift for p in pts_all[0]])
    centre = np.array([tf(p) for p in static_pts]).mean(axis=0) + shift

    # the cells: one per static triangle (the pictures are small), packed in rows. The shading is the game's: every vertex has its own colour from the model's colour table and
    # a textured triangle is shaded smoothly between its three corners (Gouraud), the texel times that colour / 128; every texel of the beaker has the semi-transparency bit, so
    # the flask is see-through, in the blend mode of its texture page: half and half (alpha 0.5) or added (the light adds to what is behind: approximated by alpha 0.7).
    vcols = ct.mesh_vertex_colours(model)
    cells = []
    for i in static_ids:
        f = 1
        corner, tex, col, flags = tris[i]
        if tri_override and i in tri_override:
            tex = tri_override[i][0]
        lay = ct.layout_bytes(model, tex) if tex else None
        got = vram.texture_stp(lay) if lay else None
        if not tex:
            # untextured: the corner colours of the colour table, blended (the Wumpa fruit)
            cell = _gouraud_cell([table[vcols[k]] if table and vcols[k] < len(table) else (255, 0, 255) for k in corner])
            img = cell
            c = (0, 0, 0, GOURAUD_CELL - 1, 0, 0, 0, GOURAUD_CELL - 1, 0, 0)
        elif got is None:
            img = np.zeros((1, 1, 4), dtype=np.uint8)
            img[0, 0] = (255, 0, 255, 255)
            cell = img
            c = (0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        else:
            img, stp = got
            if not translucent:
                stp = np.zeros_like(stp)
            c = struct.unpack("<BBHBBHBBBB", lay)
            umin, vmin = min(c[0], c[3], c[6], c[8]), min(c[1], c[4], c[7], c[9])
            uv_local = [(c[0] - umin, c[1] - vmin), (c[3] - umin, c[4] - vmin), (c[6] - umin, c[7] - vmin)]
            if additive:
                uv_local = [(a + 0.5, b + 0.5) for a, b in uv_local]         # the model's uv put a corner at the middle of its texel (see the vt lines below): shade where it is sampled
            if smooth:
                f = max(1, -(-smooth // min(img.shape[0], img.shape[1])))
                if f > 1:
                    img = np.kron(img, np.ones((f, f, 1), dtype=img.dtype))
                    stp = np.kron(stp, np.ones((f, f), dtype=stp.dtype))
                    uv_local = [(a * f, b * f) for a, b in uv_local]
            g = gain[1] if (gain and i in gain[0]) else 1.0
            cell = _bake(img, stp, uv_local, [x * g for x in ([np.array(x, dtype=np.float64) / 128.0 for x in tri_override[i][1]] if (tri_override and i in tri_override) else [np.zeros(3) if (rim_zero and i in rim_zero[1] and k in rim_zero[0]) else (_shade(table, vcols[k]) if use_shade else np.ones(3)) for k in corner])], (c[5] >> 5) & 3, additive)
        cells.append((np.pad(cell, ((1, 1), (1, 1), (0, 0)), mode="edge"), c, f, img.shape[0]))
    width = 256
    x = y = row_h = 0
    place = []
    for cell, c, w, h in cells:
        cw, ch = cell.shape[1], cell.shape[0]
        if x + cw > width:
            x, y, row_h = 0, y + row_h, 0
        place.append((x, y))
        x += cw
        row_h = max(row_h, ch)
    height = y + row_h
    atlas = np.zeros((height, width, 4), dtype=np.uint8)
    for (cell, c, w, h), (ox, oy) in zip(cells, place):
        atlas[oy: oy + cell.shape[0], ox: ox + cell.shape[1]] = cell

    turned = _outward([tuple(tris[i][0]) for i in static_ids], pts)
    v_lines, vt_lines, vn_lines, f_lines = [], [], [], []
    for n, (i, (cell, c, f, h), (ox, oy)) in enumerate(zip(static_ids, cells, place)):
        corner = tris[i][0]
        p3 = [pts[k] for k in corner]
        order = [0, 2, 1] if turned[n] else [0, 1, 2]              # wound outwards
        umin, vmin = min(c[0], c[3], c[6], c[8]), min(c[1], c[4], c[7], c[9])
        uv = [(c[0], c[1]), (c[3], c[4]), (c[6], c[7])]
        # a triangle the game draws from both sides (its "cull" flag is off: the fruit's leaves) is written twice, once each way round, so it is double sided whatever the renderer
        # culls (only with {honour_cull}: the beaker, a see-through tube, is drawn with culling); the others once, wound outwards. Every face gets the same normal, straight up (see the module's note).
        for ord_ in ([order, order[::-1]] if honour_cull and not tris[i][3].get("cull", True) else [order]):
            vn_lines.append("vn 0 1 0")
            idx = []
            for k in ord_:
                u, v = uv[k]
                v_lines.append("v %.5f %.5f %.5f" % tuple(p3[k]))
                vt_lines.append("vt %.5f %.5f" % ((ox + 1 + (u - umin + 0.5) * f) / width, 1.0 - (oy + 1 + (v - vmin + 0.5) * f) / height))
                idx.append(len(v_lines))
            f_lines.append("f %d/%d/%d %d/%d/%d %d/%d/%d" % (idx[0], idx[0], len(vn_lines), idx[1], idx[1], len(vn_lines), idx[2], idx[2], len(vn_lines)))
    obj = ["# CTR %s from the owner's own disc (local use only)" % name, "mtllib %s.mtl" % name, "o %s" % name, "usemtl atlas"] + v_lines + vt_lines + vn_lines + f_lines
    mtl = ["newmtl atlas", "Ka 1 1 1", "Kd 1 1 1", "Ks 0 0 0", "d 1", "illum 1", "map_Kd #atlas", ""]
    files = {
        "textures/item/%s_original.png" % name: Image.fromarray(atlas, "RGBA"),
        "models/item/%s.obj" % name: "\n".join(obj) + "\n",
        "models/item/%s.mtl" % name: "\n".join(mtl),
        "models/item/pickup_%s.json" % name: {
            "loader": "neoforge:obj",
            "model": "ctrcraft:models/item/%s.obj" % name,
            "flip_v": True,
            "automatic_culling": False,
            "shade_quads": False,
            "textures": {"atlas": "ctrcraft:item/%s_original" % name, "particle": "ctrcraft:item/%s_original" % name},
            "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}},
        },
    }
    if bubble_ids:
        files.update(_bubbles(vram, model, name, tris, bubble_ids, table, pts_all, tf))
    return files


def _bubbles(vram, model, name, tris, ids, table, pts_all, tf):
    """The moving triangles as billboards: their pictures in a sheet, and for each the size and the middle in every frame."""
    sprites = []
    for i in ids:
        corner, tex, col, flags = tris[i]
        lay = ct.layout_bytes(model, tex)
        img = vram.texture(lay)
        c = struct.unpack("<BBHBBHBBBB", lay)
        umin, vmin = min(c[0], c[3], c[6], c[8]), min(c[1], c[4], c[7], c[9])
        uv = [(c[0] - umin, c[1] - vmin), (c[3] - umin, c[4] - vmin), (c[6] - umin, c[7] - vmin)]
        got = _triangle_sprite(img, uv, _shade(table, col) if SHADE_FROM_TABLE else np.ones(3))
        if got is None:
            continue
        pic, (x0, y0, x1, y1) = got
        cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        centres = []
        size = None
        for fr in pts_all:
            q = [tf(fr[k]) for k in corner]
            m = np.array([[uv[1][0] - uv[0][0], uv[1][1] - uv[0][1]], [uv[2][0] - uv[0][0], uv[2][1] - uv[0][1]]], dtype=np.float64)
            rhs = np.array([q[1] - q[0], q[2] - q[0]])                  # 2 x 3
            d = np.linalg.solve(m, rhs)                                    # world vector per texel along u and along v
            centres.append((q[0] + d[0] * (cx - uv[0][0]) + d[1] * (cy - uv[0][1])).tolist())
            if size is None:
                size = (float(np.linalg.norm(d[0])) * (x1 - x0), float(np.linalg.norm(d[1])) * (y1 - y0))
        sprites.append((pic, size, centres))
    cell = max(max(p.shape[0], p.shape[1]) for p, _, _ in sprites) + 2
    cols = 3
    rows = (len(sprites) + cols - 1) // cols
    sheet = np.zeros((rows * cell, cols * cell, 4), dtype=np.uint8)
    out = []
    for n, (pic, size, centres) in enumerate(sprites):
        ox, oy = (n % cols) * cell + 1, (n // cols) * cell + 1
        sheet[oy: oy + pic.shape[0], ox: ox + pic.shape[1]] = pic
        out.append({"uv": [round(ox / (cols * cell), 5), round(oy / (rows * cell), 5), round((ox + pic.shape[1]) / (cols * cell), 5), round((oy + pic.shape[0]) / (rows * cell), 5)],
                    "width": round(size[0], 5), "height": round(size[1], 5), "centres": [[round(v, 5) for v in c] for c in centres]})
    return {
        "textures/entity/%s_bubbles.png" % name: Image.fromarray(sheet, "RGBA"),
        "beaker/%s_bubbles.json" % name: {"frames": len(pts_all), "texture": "ctrcraft:textures/entity/%s_bubbles.png" % name, "bubbles": out},
    }


def shard_files(vram, model, name, blocks_per_unit, up=2):
    """A model whose triangles all fly about in an animation (the crate's break-up: STATIC_CRATE_EXPLOSION, 22 triangles of 12 frames) as data for a particle:
      textures/entity/<name>.png   the pictures of its triangles, shaded by the model's colour table (smooth between the corners), one cell each
      <name>/<name>.json          {frames, texture, triangles: [{uv: [u0, v0, u1, v1, u2, v2] (0..1 in the sheet), pos: [[x0, y0, z0, x1, y1, z1, x2, y2, z2] per frame]}]}
    positions in blocks, relative to the middle of the model's FIRST frame (where the pieces still form the crate) with its base on y = 0 and the middle of the crate at y = 0.5 (the
    model's own origin sits far from where the pieces are: the game puts the instance on the crate, so the first frame is taken to be the crate's own place)."""
    nframes = ct.frame_count(model)
    # the break-up's height is its THIRD coordinate (the pieces fly up along it, as a level model's up is; up=1 would be the shared pack's usual second one): a reflection to Minecraft's frame
    turn = (lambda v: [-v[0], v[2], -v[1]]) if up == 2 else (lambda v: [-v[0], v[1], v[2]])
    frames = [np.array([turn(v) for v in ct.mesh_vertices(model, f)]) for f in range(nframes)]
    tris = ct.mesh_triangles(model)
    table = ct.colour_table(model)
    vcols = ct.mesh_vertex_colours(model)
    lo, hi = frames[0].min(axis=0), frames[0].max(axis=0)
    centre = (lo + hi) / 2.0
    shift = np.array([-centre[0], 0.5 / blocks_per_unit - centre[1], -centre[2]])

    cells = []
    for corner, tex, col, flags in tris:
        lay = ct.layout_bytes(model, tex) if tex else None
        got = vram.texture_stp(lay) if lay else None
        if got is None:
            cells.append((np.full((2, 2, 4), (200, 120, 40, 255), dtype=np.uint8), (0, 0, 0, 1, 0, 0, 0, 1, 0, 0)))
            continue
        img, stp = got
        c = struct.unpack("<BBHBBHBBBB", lay)
        umin, vmin = min(c[0], c[3], c[6], c[8]), min(c[1], c[4], c[7], c[9])
        uv_local = [(c[0] - umin, c[1] - vmin), (c[3] - umin, c[4] - vmin), (c[6] - umin, c[7] - vmin)]
        cells.append((_bake(img, stp, uv_local, [_shade(table, vcols[k]) for k in corner], (c[5] >> 5) & 3), c))
    width = 256
    x = y = row_h = 0
    place = []
    for cell, c in cells:
        cw, ch = cell.shape[1] + 2, cell.shape[0] + 2
        if x + cw > width:
            x, y, row_h = 0, y + row_h, 0
        place.append((x, y))
        x += cw
        row_h = max(row_h, ch)
    height = y + row_h
    atlas = np.zeros((height, width, 4), dtype=np.uint8)
    out = []
    for n, ((cell, c), (ox, oy), (corner, tex, col, flags)) in enumerate(zip(cells, place, tris)):
        atlas[oy: oy + cell.shape[0] + 2, ox: ox + cell.shape[1] + 2] = np.pad(cell, ((1, 1), (1, 1), (0, 0)), mode="edge")
        umin, vmin = min(c[0], c[3], c[6], c[8]), min(c[1], c[4], c[7], c[9])
        uv = []
        for u, v in ((c[0], c[1]), (c[3], c[4]), (c[6], c[7])):
            uv += [round((ox + 1 + (u - umin + 0.5)) / width, 5), round((oy + 1 + (v - vmin + 0.5)) / height, 5)]
        pos = []
        for fr in frames:
            p = [((fr[k] + shift) * blocks_per_unit) for k in corner]
            pos.append([round(float(c_), 4) for q in p for c_ in q])
        out.append({"uv": uv, "pos": pos})
    return {
        "textures/entity/%s.png" % name: Image.fromarray(atlas, "RGBA"),
        "%s/%s.json" % (name, name): {"frames": nframes, "texture": "ctrcraft:textures/entity/%s.png" % name, "triangles": out},
    }
