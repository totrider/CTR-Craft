"""
The original bomb as a 3D model for a LOCAL resource pack (from YOUR OWN disc, never in the jar): the ball and the fuse as an OBJ (NeoForge's OBJ loader), and the spark at the tip of
the fuse as a picture and a place, because the game draws the spark as a flat star that always glows: the mod draws it as a billboard at the fuse's tip (client/BombRenderer).

  files = bomb_files(vram, model, rm)         {path under assets/ctrcraft: image, text or dict (JSON)}:
    models/item/pickup_bomb.json, bomb.obj, bomb.mtl, textures/item/bomb_original.png   the ball and the fuse (tools/ctr_mesh_pack.py; the colour table's shading baked in)
    textures/entity/bomb_spark.png                                                       the spark's picture (black made see-through by its brightness)
    bomb/bomb_fuse.json                                                                  {tip: [x, y, z], spark_size, ground} in blocks from the ball's middle, the spark's width, the ball's radius

The mesh (shared model pack, 60 triangles): triangles 8-59 are the ball (culled), 6 and 7 the fuse (a flat ribbon, drawn from both sides: the game's cull flag is off) and 0-5 the spark (a
flat star, also both sides). The origin is the middle of the ball; the model is turned like the other pack models (x flipped, +Y up). Units: 72.53 to the block, the kart's scale.
"""
import json
import os

import numpy as np
from PIL import Image

import ctr_disc_textures as ct
import ctr_mesh_pack

BLOCKS_PER_UNIT = 1.5 / 108.8


def classify(model):
    tris = ct.mesh_triangles(model)
    verts = np.array(ct.mesh_vertices(model, 0))
    spark, body, fuse = [], [], []
    for i, (corner, tex, col, flags) in enumerate(tris):
        cx = verts[list(corner)][:, 0].mean()
        if flags.get("cull", True):
            body.append(i)
        elif cx > 58.0:
            spark.append(i)
        else:
            fuse.append(i)
    return spark, body, fuse, verts, tris


def bomb_files(vram, model, rm, size=64):
    spark, body, fuse, verts, tris = classify(model)
    files = ctr_mesh_pack.mesh_pack_files(vram, model, "bomb", BLOCKS_PER_UNIT, exclude=set(spark), pivot_ids=body, shade=True, honour_cull=True, translucent=False)
    # the same frame as the OBJ: x flipped; the ball's middle is the origin
    flip = verts * np.array([-1.0, 1.0, 1.0])
    centre = np.array([flip[list(tris[i][0])] for i in body]).reshape(-1, 3).mean(axis=0)
    tip = (np.array([flip[list(tris[i][0])] for i in spark]).reshape(-1, 3).mean(axis=0) - centre) * BLOCKS_PER_UNIT
    ext = np.array([flip[list(tris[i][0])] for i in body]).reshape(-1, 3)
    half = (ext.max(axis=0) - ext.min(axis=0)) / 2.0
    ground = float(np.sort(half)[1]) * BLOCKS_PER_UNIT                      # the middle one of the three half extents: the oblate shape's mean radius
    # the spark's picture: its triangles alone, seen face on (the star lies across x and the model's height)
    prepared = rm.Prepared(vram, model, 0, up=1)
    prepared.tris = [tris[i] for i in spark]
    sv = prepared.verts[sorted({k for i in spark for k in tris[i][0]})]
    prepared.centre = (sv.min(axis=0) + sv.max(axis=0)) / 2.0
    prepared.radius = float(np.linalg.norm(sv - prepared.centre, axis=1).max()) * 1.05
    pic = np.array(rm.render_view(prepared, 0, 0, size, margin=0.0))
    lum = pic[:, :, :3].max(axis=2).astype(np.float64)
    pic[:, :, 3] = np.where(pic[:, :, 3] > 0, np.clip(lum * 1.6, 0, 255), 0).astype(np.uint8)        # black is no light: see-through
    files["textures/entity/bomb_spark.png"] = Image.fromarray(pic, "RGBA")
    files["bomb/bomb_fuse.json"] = {"tip": [round(float(v), 5) for v in tip], "spark_size": round(2.0 * prepared.radius * BLOCKS_PER_UNIT, 4), "ground": round(ground, 4)}
    return files
