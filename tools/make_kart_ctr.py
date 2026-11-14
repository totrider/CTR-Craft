"""
The baseline kart: a Blockbench model with the original's proportions (not its geometry: no CTR asset is in the repository). The mod's own parts, to be swapped for upgrades.

  python tools/make_kart_ctr.py

blockbench/kart/kart_ctr.bbmodel is the source (written once from the numbers below if it is missing, then it is yours; this script only reads it) and
src/main/resources/assets/ctrcraft/models/item/kart_ctr.json is generated from it (the entity renderer fetches that model; the spawn item keeps the older blockbench/kart/kart.bbmodel).
The placeholder texture is the same 16x16 palette as the old kart's (textures/item/kart.png).

Where the numbers come from: tools/inspect_ctr_kart.py measures the original's racer models (local only). Crash's kart in level units, drawn at 72.7 units to the block (16 pixels = 1 block here, so 1 unit = 0.22 px; between the crates' 108.8, which was too small beside a player, and the physics' 50, which was a car): 56 units across is 0.77 block:
  * the tub (body) is 56 wide, about 100 long (z -48 to +52, the front is +z), 10 to 30 high; the nose lip along the ground is 14 wide and 58 long; the rear cowl behind the driver
    is 44 wide and 40 high at the top; two exhaust pipes stand behind it, up to about 62
  * the wheels are drawn as sprites of 32 x 32 units (the game's `DrawTires`: the centres are at x +-36, y 16, z +50 at the front and -24 at the back, in the model's own units); here
    they are boxes of 32 high and 14 thick, their centres at those places
  * the driver's waist is 41 up (0.56 block): KartEntity's seat height puts a Minecraft rider's hips there. The rider is bigger than the original's driver (a player is 0.6 wide, the tub 0.77), so his elbows stick out of the chassis, as Crash's do.
The model's front is NORTH (-z), the way every Minecraft model faces; the middle of the kart (x = 8, z = 8 in pixels) is the origin of the entity.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402
import make_kart as base  # noqa: E402

BB = os.path.join("blockbench", "kart", "kart_ctr.bbmodel")
WHEELS_OUT = os.path.join(base.ASSETS, "kart", "kart_wheels.json")
STEERING_OUT = os.path.join(base.ASSETS, "models", "item", "kart_steering.json")
STEERING_DATA = os.path.join(base.ASSETS, "kart", "kart_steering.json")
OUT = os.path.join(base.ASSETS, "models", "item", "kart_ctr.json")
PX = 16.0 * 1.5 / 108.8   # pixels per level unit: the kart is drawn 3/2 as big as the crates' scale would make it (1 block = 108.8 / 1.5 = 72.53 units; 0.2206 px a unit). Tuned by eye: 0.32 (50 units a block) was a car beside the player, 0.147 (the crates' 108.8) too small; 0.22 was right and is 3:2 to within 0.3 %


def lu_box(x0, x1, y0, y1, z0, z1, colour):
    """A box from level units (x across, y up, z FORWARD) to the model's pixels (front is -z, middle at 8, 8)."""
    frm = [8 + x0 * PX, y0 * PX, 8 - z1 * PX]
    to = [8 + x1 * PX, y1 * PX, 8 - z0 * PX]
    return base.box([round(v, 3) for v in frm], [round(v, 3) for v in to], colour)


def sym(x0, x1, *rest):
    """Both sides of the kart: x0..x1 on the right and mirrored on the left."""
    return [lu_box(x0, x1, *rest), lu_box(-x1, -x0, *rest)]


def kart_parts():
    parts = {
        "skid": [lu_box(-7, 7, 0, 10, 0, 58, "dark")],
        "tub": [lu_box(-28, 28, 10, 30, -48, 40, "body")],
        "nose": [lu_box(-16, 16, 10, 26, 40, 58, "body")],
        "cowl": [lu_box(-22, 22, 30, 40, -54, -14, "body")],
        "seat": [lu_box(-14, 14, 30, 34, -22, 2, "seat"), lu_box(-14, 14, 34, 52, -26, -22, "seat")],
        "steering": [lu_box(-2, 2, 34, 48, 14, 18, "metal"), lu_box(-10, 10, 46, 50, 14, 20, "blue")],
        "exhaust_left": [lu_box(-21, -15, 40, 62, -56, -46, "metal")],
        "exhaust_right": [lu_box(15, 21, 40, 62, -56, -46, "metal")],
    }
    wheels = {"front_left": (-36, 50), "front_right": (36, 50), "rear_left": (-36, -24), "rear_right": (36, -24)}
    for name, (x, z) in wheels.items():
        inner, outer = (x - 7, x + 7)
        parts["wheel_" + name] = [lu_box(inner, outer, 0, 32, z - 16, z + 16, "tyre")]
    return parts


VALID_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)       # all that a vanilla block model's element rotation may be (anything else: "Invalid rotation", the model does not load)
# The faces a quarter turn (+90 degrees, the sense of the rotation in the model) carries to each other, per axis
QUARTER = {
    "x": {"up": "south", "south": "down", "down": "north", "north": "up"},
    "y": {"east": "north", "north": "west", "west": "south", "south": "east"},
    "z": {"east": "up", "up": "west", "west": "down", "down": "east"},
}


def _quarter(box, origin, axis, k):
    """A box (from, to) turned k quarter turns (+90 degrees each, as the model's rotation turns) about the axis through origin: another axis aligned box."""
    h, w = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[axis]           # the two other coordinates: a turn takes (a, b) to (-b, a) in them
    pts = [[box[1][0] if i & 1 else box[0][0], box[1][1] if i & 2 else box[0][1], box[1][2] if i & 4 else box[0][2]] for i in range(8)]
    for _ in range(k % 4):
        for q in pts:
            a, b = q[h] - origin[h], q[w] - origin[w]
            q[h], q[w] = origin[h] - b, origin[w] + a
    return [min(q[i] for q in pts) for i in range(3)], [max(q[i] for q in pts) for i in range(3)]


def snap_angles(elements):
    """Elements whose rotation is an angle a vanilla model refuses (Blockbench's Java Block format lets any be drawn) get the nearest valid one, the position kept: the angle is
    taken round to (-180, 180]; whole quarter turns are applied exactly to the box (another axis aligned box about the same origin, its faces renamed to where they went) and the
    remainder, within 45 degrees of a quarter, is snapped to the nearest of -45, -22.5, 0, 22.5, 45. So -70 is a quarter turn back and 20, which is snapped to 22.5; 135 is a quarter
    turn and 45. Returns (elements, a list of notes of what was changed)."""
    out, notes = [], []
    for el in elements:
        rot = el.get("rotation")
        if not rot or float(rot["angle"]) in VALID_ANGLES:
            out.append(el)
            continue
        el = dict(el)
        rot = dict(rot)
        angle = float(rot["angle"])
        a = (angle + 180.0) % 360.0 - 180.0
        k = int(round(a / 90.0))
        rest = a - 90.0 * k
        snapped = min(VALID_ANGLES, key=lambda v: abs(v - rest))
        if k % 4:
            lo, hi = _quarter((el["from"], el["to"]), rot["origin"], rot["axis"], k)
            el["from"], el["to"] = [round(v, 4) for v in lo], [round(v, 4) for v in hi]
            mapping = QUARTER[rot["axis"]]
            faces = {}
            for name, face in el["faces"].items():
                moved = name
                for _ in range(k % 4):
                    moved = mapping.get(moved, moved)
                faces[moved] = face
            el["faces"] = faces
        rot["angle"] = snapped
        el["rotation"] = rot
        notes.append("%s: %g -> %g (%d quarter turn%s + %g)" % (el.get("name", "element"), angle, snapped + 90.0 * k, k, "" if abs(k) == 1 else "s", snapped))
        out.append(el)
    return out, notes


def write_wheels(path=BB):
    """Where the wheels go: the four boxes named wheel_front_left / wheel_front_right / wheel_rear_left / wheel_rear_right (any suffix, in a group with export off: they are markers,
    not geometry) in the Blockbench model give each wheel's middle (blocks from the kart's origin: x to its right, y up, z forward is NEGATIVE as in the model) and its size (thickness
    along x, then the height and the length, which the wheel is scaled to). The game reads kart/kart_wheels.json for them."""
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    found = {}
    for e in raw.get("elements", []):
        name = str(e.get("name", ""))
        for key in ("front_left", "front_right", "rear_left", "rear_right"):
            if name.startswith("wheel_" + key):
                found[key] = e
    if len(found) < 4:
        print("wheel markers missing (%s): kart_wheels.json left as it is" % ", ".join(k for k in ("front_left", "front_right", "rear_left", "rear_right") if k not in found))
        return
    wheels = []
    for key in ("front_left", "front_right", "rear_left", "rear_right"):
        e = found[key]
        frm, to = e["from"], e["to"]
        pos = [(frm[0] + to[0]) / 2.0 - 8.0, (frm[1] + to[1]) / 2.0, (frm[2] + to[2]) / 2.0 - 8.0]
        wheels.append({"name": key, "front": key.startswith("front"), "pos": [round(v / 16.0, 5) for v in pos], "size": [round((to[i] - frm[i]) / 16.0, 5) for i in range(3)]})
    os.makedirs(os.path.dirname(WHEELS_OUT), exist_ok=True)
    with open(WHEELS_OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"source": "blockbench/kart/kart_ctr.bbmodel, the wheel_* marker boxes (tools/make_kart_ctr.py)", "wheels": wheels}, f, indent=2)
    print("kart_wheels written")


def is_wheel_part(el):
    """The cubes of the steering WHEEL (steering_1, steering_2, ...): they turn with the steering; steering_0 is the rod and stays with the kart."""
    name = str(el.get("name", ""))
    return name.startswith("steering_") and name[9:10].isdigit() and name != "steering_0"


def write_steering_data(path=BB):
    """The steering wheel's pivot and axis, from the bar (steering_2, else the plane steering_1): the bar's middle, turned as the bar is, in blocks in the model's own frame, and the way
    the wheel's face looks (its +z turned the same way: towards the driver and up). The wheel turns about that axis."""
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    found = {str(e.get("name")): e for e in raw.get("elements", [])}
    e = found.get("steering_2") or found.get("steering_1")
    if e is None:
        return False
    frm, to = e["from"], e["to"]
    c = [(frm[i] + to[i]) / 2.0 for i in range(3)]
    rot = e.get("rotation") or [0, 0, 0]
    o = e.get("origin", [8, 8, 8])
    ang = math.radians(rot[0])
    cs, sn = math.cos(ang), math.sin(ang)
    dy, dz = c[1] - o[1], c[2] - o[2]
    pivot = [c[0], o[1] + dy * cs - dz * sn, o[2] + dy * sn + dz * cs]
    axis = [0.0, -sn, cs]                                          # (0, 0, 1) turned about x by the angle
    # the grips (where the hands go): on the plane (steering_1), a ninth of its width in from each end, half way down, turned as the plane is
    plane = found.get("steering_1") or e
    pf, pt = plane["from"], plane["to"]
    pr = plane.get("rotation") or [0, 0, 0]
    po = plane.get("origin", [8, 8, 8])
    pa = math.radians(pr[0])
    pc, ps = math.cos(pa), math.sin(pa)
    grips = []
    for u in (0.11, 0.89):
        gx, gy, gz = pf[0] + u * (pt[0] - pf[0]), (pf[1] + pt[1]) / 2.0, (pf[2] + pt[2]) / 2.0
        dy, dz = gy - po[1], gz - po[2]
        grips.append([round(gx / 16.0, 5), round((po[1] + dy * pc - dz * ps) / 16.0, 5), round((po[2] + dy * ps + dz * pc) / 16.0, 5)])
    os.makedirs(os.path.dirname(STEERING_DATA), exist_ok=True)
    with open(STEERING_DATA, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"source": "blockbench/kart/kart_ctr.bbmodel, the steering bar and plane (tools/make_kart_ctr.py)", "pivot": [round(v / 16.0, 5) for v in pivot], "axis": [round(v, 5) for v in axis],
                   "grip_left": grips[0], "grip_right": grips[1]}, f, indent=2)
    return True


EXHAUSTS_OUT = os.path.join(base.ASSETS, "kart", "kart_exhausts.json")


def write_exhausts(path=BB):
    """The open ends of the exhaust pipes (the cubes named exhaust_*): the middle of each one's top end turned as the cube is, and the way it points, in blocks from the kart's origin in the
    model's frame (x to the right, z towards the back). The game puts the exhaust smoke and the turbo flame there."""
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    pipes = []
    for e in raw.get("elements", []):
        if not str(e.get("name", "")).startswith("exhaust") or e.get("export") is False:
            continue
        frm, to = e["from"], e["to"]
        tip = [(frm[0] + to[0]) / 2.0, to[1], (frm[2] + to[2]) / 2.0]
        rot = e.get("rotation") or [0, 0, 0]
        o = e.get("origin", [8, 8, 8])
        a = math.radians(rot[0])
        c, s = math.cos(a), math.sin(a)
        dy, dz = tip[1] - o[1], tip[2] - o[2]
        tip = [tip[0], o[1] + dy * c - dz * s, o[2] + dy * s + dz * c]
        pipes.append({"tip": [round((tip[0] - 8.0) / 16.0, 5), round(tip[1] / 16.0, 5), round((tip[2] - 8.0) / 16.0, 5)], "dir": [0.0, round(c, 5), round(s, 5)]})
    os.makedirs(os.path.dirname(EXHAUSTS_OUT), exist_ok=True)
    with open(EXHAUSTS_OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"source": "blockbench/kart/kart_ctr.bbmodel, the exhaust_* cubes (tools/make_kart_ctr.py)", "pipes": pipes}, f, indent=2)


def main():
    if not os.path.exists(BB):
        bbmodel.write(BB, "kart_ctr", {"kart": base.TEX}, list(kart_parts().items()), display=base.display(), gui_light_front=False,
                      comment="Baseline kart with the original's proportions (see tools/make_kart_ctr.py). FRONT IS NORTH (-z). One group per part (skid, tub, nose, cowl, seat, "
                              "steering, exhausts, four wheels) so parts can be swapped or animated. The texture slot 'kart' is the same 16x16 palette as the old kart's.")
    model = bbmodel.read(BB)
    els, notes = snap_angles(bbmodel.elements(model))
    for n in notes:
        print("angle snapped:", n)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    for stale in (OUT[:-5] + ".obj", OUT[:-5] + ".mtl"):
        if os.path.exists(stale):
            os.remove(stale)
    textures = {sid: base.NS + ":item/" + os.path.splitext(os.path.basename(path))[0] for sid, path in model["slots"].items()}
    textures["particle"] = textures.get("kart", next(iter(textures.values())))
    wheel_els = [e for e in els if is_wheel_part(e)]
    body_els = [e for e in els if not is_wheel_part(e)]
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": textures, "elements": body_els, "display": model["display"]}, f, indent=2)
    # the steering wheel is its own model, turned about its axis by the steering (the rod stays with the kart)
    if wheel_els and write_steering_data():
        with open(STEERING_OUT, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"textures": textures, "elements": wheel_els, "display": model["display"]}, f, indent=2)
    else:
        with open(OUT, "w", encoding="utf-8", newline="\n") as f:                     # no wheel parts found: everything stays in the kart, nothing turns
            json.dump({"textures": textures, "elements": els, "display": model["display"]}, f, indent=2)
        for stale in (STEERING_OUT, STEERING_DATA):
            if os.path.exists(stale):
                os.remove(stale)
    write_wheels()
    write_exhausts()
    print("kart_ctr written")


if __name__ == "__main__":
    main()
