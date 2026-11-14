"""
Reading and writing Blockbench models (.bbmodel, "Java Block/Item" format) for the model generators.

The .bbmodel files in blockbench/ are the SOURCE of our block and item models: the generators (make_*.py) read the
geometry from them, put the right textures in, and write the JSON models the game loads (src/main/resources/.../models).
So: edit the model in Blockbench, save it, run the build (or its generator), done. Nothing in a generator should hold
geometry any more.

What a generator gets from a bbmodel (see read):
  * "groups": the outliner groups by name, each a list of elements in the JSON model format (from, to, faces with
    uv / texture "#slot" / tintindex / cullface / rotation, and a "rotation" {origin, axis, angle} where an element is
    tilted). Elements outside any group are in the group "".
  * "slots": the texture slots by id (the texture's "id" in Blockbench, e.g. "wood", "mat") with the file they show.
  * "display": the item display transforms, if the model has any.
Conventions used by our models: a texture slot is the id of a texture in the bbmodel; a face's "tint" is the JSON
tintindex (0 = the fabric / dye colour, 1 = the frame colour; see the generators); groups mean what the generator that
reads the file says (e.g. the crib's "w", "e", "end", "wpost" ... are pieces a block state switches on and off).
"""
import base64
import json
import os
import uuid

FACES = ("north", "east", "south", "west", "up", "down")


def default_uv(face, frm, to):
    """The uv Minecraft uses for a face that has none (so the bbmodel always has explicit ones)."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {
        "down": [x0, 16 - z1, x1, 16 - z0],
        "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


def _clean(v):
    """Whole numbers as ints, so the JSON stays tidy."""
    if isinstance(v, float) and v == int(v):
        return int(v)
    return v


def _png_source(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")


def write(path, name, slots, groups, display=None, gui_light_front=False, ambient_occlusion=True, comment=None):
    """
    slots:  ordered {slot id: png path or None} (None: a plain grey placeholder, for textures that are not ours).
    groups: list of (group name, [JSON model elements]); a group named "" puts its elements at the top level.
    """
    slot_ids = list(slots)
    textures = []
    for i, sid in enumerate(slot_ids):
        png = slots[sid]
        entry = {"path": os.path.abspath(png) if png and os.path.exists(png) else "", "name": sid + ".png", "folder": "",
                 "namespace": "", "id": sid, "particle": sid == "particle", "render_mode": "default", "render_sides": "auto",
                 "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
                 "mode": "bitmap", "saved": True, "uuid": str(uuid.uuid4())}
        if png and os.path.exists(png):
            entry["relative_path"] = os.path.relpath(png, os.path.dirname(os.path.abspath(path))).replace("\\", "/")
            entry["source"] = _png_source(png)
        else:
            entry["source"] = _placeholder(sid)
        textures.append(entry)
    elements, outliner = [], []
    for group_name, els in groups:
        ids = []
        for i, el in enumerate(els):
            bb = _to_bb_element(el, f"{group_name or 'cube'}_{i}", slot_ids)
            elements.append(bb)
            ids.append(bb["uuid"])
        if group_name:
            outliner.append({"name": group_name, "origin": [8, 8, 8], "color": 0, "uuid": str(uuid.uuid4()), "export": True,
                             "isOpen": False, "locked": False, "visibility": True, "autouv": 0, "children": ids})
        else:
            outliner.extend(ids)
    model = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": name, "parent": "", "ambientocclusion": ambient_occlusion, "front_gui_light": gui_light_front,
        "visible_box": [1, 1, 0], "resolution": {"width": 16, "height": 16},
        "elements": elements, "outliner": outliner, "textures": textures,
    }
    if display:
        model["display"] = display
    if comment:
        model["ctr_craft_note"] = comment
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=1)


def _placeholder(sid):
    """A small flat swatch for a texture slot whose real texture is not in the project (a vanilla texture)."""
    import io
    from PIL import Image
    colour = {"wood": (190, 150, 100), "particle": (190, 150, 100), "mat": (240, 240, 240)}.get(sid, (200, 200, 200))
    buf = io.BytesIO()
    Image.new("RGBA", (16, 16), colour + (255,)).save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


def _to_bb_element(el, name, slot_ids):
    faces = {}
    for face in FACES:
        f = el["faces"].get(face)
        if not f:
            faces[face] = {"uv": [0, 0, 0, 0], "texture": None}
            continue
        uv = f.get("uv") or default_uv(face, el["from"], el["to"])
        bb = {"uv": [_clean(c) for c in uv], "texture": slot_ids.index(f["texture"].lstrip("#"))}
        if "tintindex" in f:
            bb["tint"] = f["tintindex"]
        if "cullface" in f:
            bb["cullface"] = f["cullface"]
        if f.get("rotation"):
            bb["rotation"] = f["rotation"]
        faces[face] = bb
    rotation, origin, rescale = [0, 0, 0], [8, 8, 8], False
    if "rotation" in el:
        r = el["rotation"]
        rotation = {"x": [r["angle"], 0, 0], "y": [0, r["angle"], 0], "z": [0, 0, r["angle"]]}[r["axis"]]
        origin = list(r["origin"])
        rescale = bool(r.get("rescale", False))
    return {"name": name, "box_uv": False, "rescale": rescale, "locked": False, "light_emission": 0, "render_order": "default",
            "allow_mirror_modeling": True, "from": [_clean(c) for c in el["from"]], "to": [_clean(c) for c in el["to"]],
            "autouv": 0, "color": 0, "origin": origin, "rotation": rotation, "faces": faces, "type": "cube",
            "uuid": str(uuid.uuid4())}


def read(path):
    """The model's groups, texture slots and display (see the module doc)."""
    with open(path, encoding="utf-8") as f:
        model = json.load(f)
    slot_ids = [t.get("id") or str(i) for i, t in enumerate(model.get("textures", []))]
    slots = {t.get("id") or str(i): t.get("relative_path", "") for i, t in enumerate(model.get("textures", []))}
    by_uuid = {e["uuid"]: _from_bb_element(e, slot_ids) for e in model.get("elements", []) if e.get("export", True)}      # an element with export off is a guide, not geometry
    group_info = {g.get("uuid"): g for g in model.get("groups", []) if isinstance(g, dict)}
    groups = {}

    def collect(nodes, group):
        for node in nodes:
            if isinstance(node, str):
                if node in by_uuid:
                    groups.setdefault(group, []).append(by_uuid[node])
            else:                                          # a (sub)group: its name, or the outer one for a nested group
                info = group_info.get(node.get("uuid"), {})
                if node.get("export", info.get("export", True)) is False:
                    continue                                   # a group with export off (guides) is left out whole
                collect(node.get("children", []), node.get("name", info.get("name")) if not group else group)
    collect(model.get("outliner", []), "")
    for gname in list(groups):
        pass
    return {"groups": groups, "slots": slots, "display": model.get("display"), "name": model.get("name", "")}


def _from_bb_element(e, slot_ids):
    faces = {}
    for face in FACES:
        f = e["faces"].get(face)
        if not f or f.get("texture") is None:
            continue
        out = {"uv": [_clean(c) for c in f["uv"]], "texture": "#" + slot_ids[f["texture"]]}
        tint = f.get("tint", f.get("tintindex", -1))
        if tint is not None and tint >= 0:
            out["tintindex"] = tint
        if f.get("cullface"):
            out["cullface"] = f["cullface"]
        if f.get("rotation"):
            out["rotation"] = f["rotation"]
        faces[face] = out
    el = {"from": [_clean(c) for c in e["from"]], "to": [_clean(c) for c in e["to"]], "faces": faces}
    if e.get("name"):
        el["name"] = str(e["name"])
    rot = e.get("rotation") or [0, 0, 0]
    if any(rot):
        axis = "xyz"[[i for i, v in enumerate(rot) if v][0]]
        el["rotation"] = {"origin": [_clean(c) for c in e.get("origin", [8, 8, 8])], "axis": axis,
                          "angle": _clean(rot["xyz".index(axis)]), "rescale": bool(e.get("rescale", False))}
    return el


def elements(model, *group_names):
    """All the elements of the given groups, in the order of the groups asked for (all groups if none are named)."""
    names = group_names or list(model["groups"])
    return [el for name in names for el in model["groups"][name]]
