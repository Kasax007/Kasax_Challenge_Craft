"""Writes the block/item models, blockstates, loot tables and recipes of the casino devices.

  python3 make_models.py RESOURCES_DIR

Each device is modelled facing north (front towards -Z, the way block models are authored) and
rotated by the blockstate. The moving parts are NOT in these models - reels, wheel, ball and
rocket are drawn by CasinoWorldRenderer, whose constants (window, drum, wheel height, rocket base)
must match the geometry here.
"""
import json
import os
import sys

RES = sys.argv[1]
NS = "challengecraft"
T = lambda name: f"{NS}:block/casino/{name}"


def uv_for(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    def clamp(a, b):
        span = min(16.0, b - a)
        start = a % 16 if (a % 16) + span <= 16 else 0.0
        return round(start, 3), round(start + span, 3)
    if face in ("north", "south"):
        u0, u1 = clamp(x0, x1)
        v0, v1 = clamp(16 - y1 if y1 <= 16 else 0, 16 - y0 if y1 <= 16 else y1 - y0)
        return [u0, v0, u1, v1]
    if face in ("east", "west"):
        u0, u1 = clamp(z0, z1)
        v0, v1 = clamp(16 - y1 if y1 <= 16 else 0, 16 - y0 if y1 <= 16 else y1 - y0)
        return [u0, v0, u1, v1]
    u0, u1 = clamp(x0, x1)
    v0, v1 = clamp(z0, z1)
    return [u0, v0, u1, v1]


def box(f, t, tex, overrides=None, full=None, skip=()):
    """tex: default texture key; overrides: {face: key}; full: faces that map the whole texture."""
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        if face in skip:
            continue
        key = (overrides or {}).get(face, tex)
        uv = [0, 0, 16, 16] if full and face in full else uv_for(face, f, t)
        faces[face] = {"uv": uv, "texture": "#" + key}
    return {"from": f, "to": t, "faces": faces}


def write(path, data):
    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


TALL_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -3.2, 0], "scale": [0.42, 0.42, 0.42]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 1.5], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
}
BLOCK_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def with_face(element, face, texture, uv):
    element["faces"][face] = {"uv": uv, "texture": "#" + texture}
    return element


def slot_machine():
    tex = {"body": T("slot_body"), "gold": T("slot_gold"), "dark": T("slot_dark"), "panel": T("slot_panel"),
           "marquee": T("slot_marquee"), "top": T("slot_top"), "knob": T("slot_knob"), "chrome": T("slot_chrome"),
           "particle": T("slot_body")}
    el = [
        box([1, 0, 2], [15, 3, 15], "dark"),
        box([1.5, 3, 3], [14.5, 9.5, 14.5], "body", {"north": "panel"}, full=("north",)),
        box([3.5, 3.5, 1.5], [12.5, 5, 3], "gold", {"up": "dark"}),
        box([1, 9.5, 1], [15, 10.2, 15], "gold"),
        box([2, 10.2, 2.5], [14, 10.4, 13.5], "dark"),
        box([1, 10.2, 2], [2, 18, 15], "body", {"north": "gold", "east": "dark", "up": "gold", "down": "gold"}),
        box([14, 10.2, 2], [15, 18, 15], "body", {"north": "gold", "west": "dark", "up": "gold", "down": "gold"}),
        box([2, 10.2, 13.5], [14, 18, 14.5], "dark"),
        box([1, 18, 2], [15, 19, 15], "gold"),
        with_face(box([1.5, 19, 3], [14.5, 25, 13], "body", {"up": "top"}), "north", "marquee", [0, 4.5, 16, 11.5]),
        box([2.5, 25, 4], [13.5, 26.5, 12], "gold", {"up": "top"}),
        # Lever on the player's right: a north-facing front is seen from the north, so that is -X.
        box([0, 12, 7.5], [1, 13.5, 9.5], "chrome"),
        box([0.2, 13.5, 8], [0.8, 20.5, 8.6], "chrome"),
        box([-0.2, 20.5, 7.6], [1.2, 21.9, 9], "knob", full=("north", "south", "east", "west", "up", "down")),
    ]
    return {"parent": "block/block", "textures": tex, "elements": el, "display": TALL_DISPLAY}


def roulette_table():
    tex = {"wood": T("mahogany"), "side": T("roulette_side"), "top": T("roulette_top"), "brass": T("brass"),
           "particle": T("mahogany")}
    el = []
    for (x, z) in [(1, 1), (13, 1), (1, 13), (13, 13)]:
        el.append(box([x, 0, z], [x + 2, 10, z + 2], "wood"))
    el.append(box([0.5, 10, 0.5], [15.5, 12, 15.5], "side", {"down": "wood", "up": "wood"}))
    el.append(box([0, 12, 0], [16, 13, 16], "side", {"up": "top", "down": "wood"}, full=("up",)))
    # Brass rail around the wheel bowl, clear of the wheel's 6.6 px radius.
    el.append(box([0.4, 13, 0.4], [15.6, 13.5, 1.0], "brass"))
    el.append(box([0.4, 13, 15.0], [15.6, 13.5, 15.6], "brass"))
    el.append(box([0.4, 13, 1.0], [1.0, 13.5, 15.0], "brass"))
    el.append(box([15.0, 13, 1.0], [15.6, 13.5, 15.0], "brass"))
    return {"parent": "block/block", "textures": tex, "elements": el, "display": BLOCK_DISPLAY}


def crash_pad():
    tex = {"top": T("crash_top"), "side": T("crash_side"), "steel": T("steel"), "box": T("crash_box"),
           "button": T("big_button"), "particle": T("steel")}
    el = [
        box([0, 0, 0], [16, 2, 16], "side", {"up": "top", "down": "steel"}, full=("up",)),
        box([1.5, 2, 6.5], [3, 9, 9.5], "steel"),
        box([13, 2, 6.5], [14.5, 9, 9.5], "steel"),
        box([3, 7.6, 7.5], [5.2, 8.6, 8.5], "steel"),
        box([10.8, 7.6, 7.5], [13, 8.6, 8.5], "steel"),
        # Control box at the front right (seen from the front, that is the -X side).
        box([0.5, 2, 0.5], [4, 6, 4], "steel", {"north": "box"}, full=("north",)),
        box([1.5, 6, 1.5], [3, 6.8, 3], "button", full=("up", "north", "south", "east", "west")),
    ]
    return {"parent": "block/block", "textures": tex, "elements": el, "display": BLOCK_DISPLAY}


def cashier_counter():
    tex = {"front": T("counter_front"), "side": T("counter_side"), "marble": T("marble"), "brass": T("brass"),
           "wood": T("mahogany"), "particle": T("mahogany")}
    el = [
        box([0, 0, 2], [16, 13, 14], "side", {"north": "front", "down": "wood"}, full=("north",)),
        box([0, 13, 1], [16, 14.5, 15], "marble"),
        box([0, 11, 1.2], [16, 12, 2], "brass"),
        box([6.5, 14.5, 6.5], [9.5, 15, 9.5], "brass"),
        box([7, 15, 7], [9, 16.2, 9], "brass"),
        box([7.7, 16.2, 7.7], [8.3, 16.8, 8.3], "brass"),
    ]
    return {"parent": "block/block", "textures": tex, "elements": el, "display": BLOCK_DISPLAY}


MODELS = {"slot_machine": slot_machine, "roulette_table": roulette_table, "crash_pad": crash_pad,
          "cashier_counter": cashier_counter}

for name, fn in MODELS.items():
    write(f"assets/{NS}/models/block/{name}.json", fn())
    write(f"assets/{NS}/blockstates/{name}.json", {"variants": {
        "facing=north": {"model": f"{NS}:block/{name}"},
        "facing=east": {"model": f"{NS}:block/{name}", "y": 90},
        "facing=south": {"model": f"{NS}:block/{name}", "y": 180},
        "facing=west": {"model": f"{NS}:block/{name}", "y": 270},
    }})
    write(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    write(f"data/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
        "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
        "rolls": 1}]})

write(f"assets/{NS}/items/chip_wallet.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/chip_wallet"}})
write(f"assets/{NS}/models/item/chip_wallet.json", {"parent": "minecraft:item/generated",
                                                   "textures": {"layer0": f"{NS}:item/chip_wallet"}})

# Recipes: craftable once the device has been bought from the croupier (enforced in code).
write(f"data/{NS}/recipe/slot_machine.json", {"type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone_block", "G": "minecraft:glass_pane",
            "L": "minecraft:lever", "O": "minecraft:gold_ingot"},
    "pattern": ["IOI", "IGL", "IRI"], "result": {"id": f"{NS}:slot_machine"}})
write(f"data/{NS}/recipe/crash_pad.json", {"type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"G": "minecraft:gold_ingot", "F": "minecraft:firework_rocket", "T": "minecraft:tnt",
            "S": "minecraft:smooth_stone_slab"},
    "pattern": ["GFG", "GTG", "SSS"], "result": {"id": f"{NS}:crash_pad"}})
write(f"data/{NS}/recipe/roulette_table.json", {"type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"W": "minecraft:green_wool", "D": "minecraft:diamond", "E": "minecraft:emerald", "P": "#minecraft:planks"},
    "pattern": ["WWW", "DED", "P P"], "result": {"id": f"{NS}:roulette_table"}})
print("models written")
