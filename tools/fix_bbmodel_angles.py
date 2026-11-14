"""
Make a Blockbench model's cube rotations loadable by Minecraft: a vanilla block model only takes 0, 22.5 and 45 degrees (either way) on an element, and Blockbench's Java Block
format lets any angle be drawn. Each cube with another angle is changed IN PLACE in the .bbmodel to the nearest valid one, position kept (the rest of the file, and every other
edit, stays as it is); angles beyond 45 become exact quarter turns of the box plus a snapped remainder (see make_kart_ctr.snap_angles).

  python tools/fix_bbmodel_angles.py blockbench/kart/kart_ctr.bbmodel

Close the model in Blockbench first (it would save over this), and reload it after. Only a rotation about one axis per cube is handled (Java Block's own limit).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_kart_ctr as snap  # noqa: E402


def fix(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    changed = []
    for el in data.get("elements", []):
        rot = el.get("rotation") or [0, 0, 0]
        axes = [i for i in range(3) if rot[i]]
        if not axes or el.get("type", "cube") != "cube":
            continue
        if len(axes) > 1:
            print("skipped (turned about more than one axis):", el.get("name"))
            continue
        axis = "xyz"[axes[0]]
        if float(rot[axes[0]]) in snap.VALID_ANGLES:
            continue
        proxy = {"name": el.get("name"), "from": el["from"], "to": el["to"], "faces": el["faces"],
                 "rotation": {"origin": el.get("origin", [8, 8, 8]), "axis": axis, "angle": rot[axes[0]]}}
        out, notes = snap.snap_angles([proxy])
        new = out[0]
        el["from"], el["to"], el["faces"] = new["from"], new["to"], new["faces"]
        el["rotation"] = [0, 0, 0]
        el["rotation"][axes[0]] = new["rotation"]["angle"]
        changed.append("%s: %s" % (el.get("name"), notes[0].split(": ", 1)[1]))
    if changed:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, separators=(",", ":"), ensure_ascii=False)        # compact, as Blockbench writes it
    return changed


if __name__ == "__main__":
    for p in sys.argv[1:]:
        done = fix(p)
        print(p, "-", ("%d cubes changed:" % len(done)) if done else "nothing to change")
        for line in done:
            print("  ", line)
