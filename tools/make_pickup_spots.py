"""Generates the resources of the three pickup spot blocks (fruit, fruit crate, power-up crate): the textures, block models, item models,
blockstates and loot tables. Stand-in art: a small marker block per kind, to be replaced by real models later.

Run from the project root:  python tools/make_pickup_spots.py
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ctrcraft")
DATA = os.path.join(RES, "data", "ctrcraft")

SPOTS = {
    "fruit_spot": ((255, 140, 40), "F"),
    "fruit_crate_spot": ((190, 120, 60), "C"),
    "powerup_crate_spot": ((250, 210, 40), "?"),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def texture(name, colour, glyph):
    img = Image.new("RGBA", (16, 16), colour + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(30, 20, 40, 255))
    d.rectangle([1, 1, 14, 14], outline=tuple(max(0, c - 60) for c in colour) + (255,))
    d.text((5, 3), glyph, fill=(30, 20, 40, 255))
    path = os.path.join(ASSETS, "textures", "block", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


for name, (colour, glyph) in SPOTS.items():
    texture(name, colour, glyph)
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "textures": {"all": "ctrcraft:block/" + name, "particle": "ctrcraft:block/" + name},
        "elements": [{
            "from": [4, 0, 4], "to": [12, 8, 12],
            "faces": {side: {"uv": [0, 0, 16, 16], "texture": "#all"} for side in ("down", "up", "north", "south", "west", "east")},
        }],
    })
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": "ctrcraft:block/" + name}}})
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "ctrcraft:block/" + name})
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": "ctrcraft:" + name}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
print("pickup spot resources written")
