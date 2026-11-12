"""
The kart: model, item model, texture (placeholder) and name.

  python tools/make_kart.py

The model is Blockbench's: blockbench/kart/kart.bbmodel is the source (written once from the shape below if it is
missing, then it is yours; this script only reads it). Writes src/main/resources/assets/ctrcraft/models/item/kart.json
(the spawn item AND the entity renderer use it: the renderer fetches the baked item model), and draws the placeholder
texture textures/item/kart.png once if missing (yours to repaint). Run from the project folder.

Orientation of the model: the FRONT of the kart is NORTH (-z), the way every Minecraft block model faces. The renderer turns
it to the kart's heading. The model is centred on x = 8, z = 8 (the kart's origin, the bottom centre, is the middle of the block).
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbmodel  # noqa: E402

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ctrcraft")
NS = "ctrcraft"
TEX = os.path.join(ASSETS, "textures", "item", "kart.png")
BB = os.path.join("blockbench", "kart", "kart.bbmodel")

# The placeholder texture is a 16x16 palette: 4x4 px cells of flat colour; faces point at a cell.
PALETTE = {
    "body": (0, 0, (214, 52, 52)),       # red
    "dark": (4, 0, (54, 54, 62)),        # chassis / wheels
    "tyre": (8, 0, (28, 28, 32)),
    "seat": (12, 0, (240, 196, 60)),     # yellow
    "metal": (0, 4, (170, 174, 184)),
    "white": (4, 4, (240, 240, 240)),
    "blue": (8, 4, (60, 110, 220)),
}


def draw_texture():
    im = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    for _, (cx, cy, rgb) in PALETTE.items():
        for y in range(cy, cy + 4):
            for x in range(cx, cx + 4):
                im.putpixel((x, y), rgb + (255,))
    return im


def box(frm, to, colour):
    cx, cy, _ = PALETTE[colour]
    uv = [cx + 1, cy + 1, cx + 3, cy + 3]
    face = {"uv": uv, "texture": "#kart"}
    return {"from": frm, "to": to, "faces": {f: dict(face) for f in bbmodel.FACES}}


def kart_parts():
    """Groups of boxes, pixels (16 = 1 block). Front is -z (north). Centre x = 8, z = 8."""
    return {
        "chassis": [box([3, 2, -2], [13, 5, 18], "dark")],
        "nose": [box([4, 3, -4], [12, 6, 4], "body")],
        "body": [box([3, 5, 4], [13, 7, 12], "body")],
        "seat": [box([5, 5, 10], [11, 6, 15], "seat"), box([5, 6, 14], [11, 11, 15], "seat")],
        "steering": [box([7.5, 6, 5], [8.5, 9, 6], "metal"), box([6, 9, 4], [10, 10, 6], "blue")],
        "wheel_front_left": [box([0.5, 0, 0], [3.5, 6, 6], "tyre")],
        "wheel_front_right": [box([12.5, 0, 0], [15.5, 6, 6], "tyre")],
        "wheel_rear_left": [box([0, 0, 10], [3.5, 7, 17], "tyre")],
        "wheel_rear_right": [box([12.5, 0, 10], [16, 7, 17], "tyre")],
        "exhaust": [box([6, 3, 17], [10, 5, 20], "metal")],
    }


def display():
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
            "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.35, 0.35, 0.35]},
            "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]}}


def write_first_bbmodel():
    groups = list(kart_parts().items())
    bbmodel.write(BB, "kart", {"kart": TEX}, groups, display=display(), gui_light_front=False,
                  comment="Placeholder go-kart. FRONT IS NORTH (-z). One group per part (chassis, nose, body, seat, steering, "
                          "four wheels, exhaust) so parts can be animated later. The single texture slot 'kart' is a 16x16 "
                          "palette of flat 4x4 colour cells (placeholder: repaint it, and re-map the uvs in Blockbench).")


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


def main():
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    if not os.path.exists(TEX):
        draw_texture().save(TEX)
    if not os.path.exists(BB):
        write_first_bbmodel()
    model = bbmodel.read(BB)
    dump(os.path.join(ASSETS, "models", "item", "kart.json"), {
        "textures": {"kart": f"{NS}:item/kart", "particle": f"{NS}:item/kart"},
        "elements": bbmodel.elements(model),
        "display": model["display"],
    })
    lp = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(lp, encoding="utf-8")) if os.path.exists(lp) else {}
    lang[f"item.{NS}.kart"] = "Kart"
    lang[f"entity.{NS}.kart"] = "Kart"
    lang[f"itemGroup.{NS}"] = "Crash Team Racing Craft"
    lang["key.categories.ctrcraft"] = "CTR Craft"
    lang["key.ctrcraft.hop2"] = "Hop / Boost (second shoulder)"
    lang["key.ctrcraft.brake"] = "Brake"
    dump(lp, lang)
    print("kart written")


if __name__ == "__main__":
    main()
