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


def dbox(u0, y0, v0, u1, y1, v1, tex, overrides=None, full=None, skip=()):
    """A box given in device space (u to the player's right, v to the back); see DeviceSpace.java."""
    return box([round(16 - u1, 3), y0, v0], [round(16 - u0, 3), y1, v1], tex, overrides, full, skip)


WIDE_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [-2.5, -2.5, 0], "scale": [0.32, 0.32, 0.32]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.15, 0.15, 0.15]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 1.5], "scale": [0.22, 0.22, 0.22]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.22, 0.22, 0.22]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.22, 0.22, 0.22]},
}


def plinko_board():
    tex = {"wood": T("mahogany"), "brass": T("brass"), "board": T("plinko_board"), "dark": T("slot_dark"),
           "particle": T("mahogany")}
    el = [
        dbox(0.5, 0, 3, 31.5, 6, 14, "wood"),
        dbox(7, 5, 0.2, 25, 6.2, 3.2, "brass", {"up": "dark"}),
        dbox(1, 6, 10.5, 31, 31, 13, "wood", {"north": "board"}),
        dbox(0, 6, 8, 1, 32, 14, "wood"),
        dbox(31, 6, 8, 32, 32, 14, "wood"),
        dbox(1, 31, 8, 31, 32, 14, "wood"),
        dbox(1, 6, 8, 31, 6.4, 10.5, "brass"),
        # Brass funnel at the top where the balls come in.
        dbox(14.5, 30, 9, 17.5, 31, 10.5, "brass"),
    ]
    return {"parent": "block/block", "textures": tex, "elements": el, "display": WIDE_DISPLAY}


# The roulette table is four blocks wide and two deep, more than one block model may cover, so it is
# described once in device space and cut into one model per cell.
ROULETTE_TEX = {"wood": T("mahogany"), "side": T("roulette_side"), "felt": T("felt"), "brass": T("brass"),
                "dark": T("slot_dark"), "particle": T("mahogany")}


def roulette_boxes():
    b = []
    for (u, v) in [(1, 1), (61, 1), (1, 29), (61, 29), (31, 1), (31, 29)]:
        b.append((u, 0, v, u + 2, 11, v + 2, "wood", {}))
    b.append((0.5, 10, 0.5, 63.5, 12, 31.5, "side", {"down": "wood", "up": "wood"}))
    b.append((0, 12, 0, 64, 14, 32, "side", {"up": "felt", "down": "wood"}))
    b.append((0, 14, 0, 64, 14.8, 0.8, "wood", {}))
    b.append((0, 14, 31.2, 64, 14.8, 32, "wood", {}))
    b.append((0, 14, 0.8, 0.8, 14.8, 31.2, "wood", {}))
    b.append((63.2, 14, 0.8, 64, 14.8, 31.2, "wood", {}))
    # Wooden bowl under the wheel.
    b.append((1.2, 14, 8.2, 16.8, 14.3, 23.8, "wood", {}))
    # Brass-framed board behind the wheel that shows the last numbers.
    b.append((1, 14, 30.2, 2, 30, 31.4, "wood", {}))
    b.append((16, 14, 30.2, 17, 30, 31.4, "wood", {}))
    b.append((2, 18, 30.4, 16, 29.4, 31.2, "dark", {}))
    b.append((1, 29.4, 30, 17, 30.4, 31.6, "brass", {}))
    b.append((1, 17.4, 30, 17, 18, 31.6, "brass", {}))
    return b


def roulette_cell(ci, cj):
    el = []
    ou, ov = ci * 16, cj * 16
    for (u0, y0, v0, u1, y1, v1, tex, ov_) in roulette_boxes():
        a0, a1 = max(u0, ou), min(u1, ou + 16)
        b0, b1 = max(v0, ov), min(v1, ov + 16)
        if a1 - a0 < 0.01 or b1 - b0 < 0.01:
            continue
        el.append(dbox(a0 - ou, y0, b0 - ov, a1 - ou, y1, b1 - ov, tex, ov_))
    return {"parent": "block/block", "textures": ROULETTE_TEX, "elements": el}


def slot_machine(with_lever=False):
    tex = {"body": T("slot_body"), "gold": T("slot_gold"), "dark": T("slot_dark"), "panel": T("slot_panel"),
           "marquee": T("slot_marquee"), "top": T("slot_top"), "knob": T("slot_knob"), "chrome": T("slot_chrome"),
           "particle": T("slot_body")}
    el = [
        box([1, 0, 2], [15, 3, 15], "dark"),
        box([1.5, 3, 3], [14.5, 9.5, 14.5], "body", {"north": "panel"}, full=("north",)),
        # Chip shelf in front of the lower cabinet (the tray chips are drawn on its top at y 4.2).
        box([1.5, 3, 0.2], [14.5, 4.2, 3.2], "gold", {"up": "dark"}),
        box([1, 9.5, 1], [15, 10.2, 15], "gold"),
        box([2, 10.2, 2.5], [14, 10.4, 13.5], "dark"),
        box([1, 10.2, 2], [2, 18, 15], "body", {"north": "gold", "east": "dark", "up": "gold", "down": "gold"}),
        box([14, 10.2, 2], [15, 18, 15], "body", {"north": "gold", "west": "dark", "up": "gold", "down": "gold"}),
        box([2, 10.2, 13.5], [14, 18, 14.5], "dark"),
        box([1, 18, 2], [15, 19, 15], "gold"),
        with_face(box([1.5, 19, 3], [14.5, 25, 13], "body", {"up": "top"}), "north", "marquee", [0, 4.5, 16, 11.5]),
        box([2.5, 25, 4], [13.5, 26.5, 12], "gold", {"up": "top"}),
        # Lever on the player's right: a north-facing front is seen from the north, so that is -X.
        # Only its mount is part of the block; the arm and knob swing when the machine is played, so
        # the world renderer draws them (SlotView#drawLever). The item keeps the whole lever.
        box([0, 12, 7.5], [1, 13.5, 9.5], "chrome"),
    ]
    if with_lever:
        el += [
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
    ]
    # The console with its monitor stands to the player's right of the pad (device space u 16..32).
    el += [
        dbox(17, 0, 2, 31, 11, 15, "steel", {"north": "box"}, full=("north",)),
        dbox(16.5, 11, 1.5, 31.5, 12, 15.5, "steel", {"up": "screen"}),
        dbox(21.5, 12, 7, 26.5, 12.4, 12, "steel"),
        dbox(22, 12, 13.5, 26, 13.5, 15, "steel"),
        dbox(17, 13, 13, 31, 29, 15, "steel", {"north": "screen"}),
        dbox(16.6, 12.6, 12.6, 31.4, 13.2, 15.2, "steel"),
        dbox(16.6, 28.8, 12.6, 31.4, 29.4, 15.2, "steel"),
    ]
    tex["screen"] = T("slot_dark")
    return {"parent": "block/block", "textures": tex, "elements": el, "display": WIDE_DISPLAY}


def cashier_counter():
    tex = {"front": T("counter_front"), "side": T("counter_side"), "marble": T("marble"), "brass": T("brass"),
           "wood": T("mahogany"), "particle": T("mahogany")}
    el = [
        box([0, 0, 2], [16, 13, 14], "side", {"north": "front", "down": "wood"}, full=("north",)),
        box([0, 13, 1], [16, 14.5, 15], "marble"),
        box([0, 11, 1.2], [16, 12, 2], "brass"),
        # No bell here: only the middle counter has one, drawn by CounterView (it is the deal bell).
    ]
    return {"parent": "block/block", "textures": tex, "elements": el, "display": BLOCK_DISPLAY}


MODELS = {"slot_machine": slot_machine, "plinko_board": plinko_board, "crash_pad": crash_pad,
          "cashier_counter": cashier_counter}
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def variants(model_for):
    out = {}
    for f, y in ROT.items():
        v = {"model": model_for(f)}
        if y:
            v["y"] = y
        out[f"facing={f}"] = v
    return out


for name, fn in MODELS.items():
    write(f"assets/{NS}/models/block/{name}.json", fn())
    write(f"assets/{NS}/blockstates/{name}.json", {"variants": variants(lambda f: f"{NS}:block/{name}")})
    write(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})

# The slot machine's item shows the lever, which in the world is drawn (and pulled) by the renderer.
write(f"assets/{NS}/models/block/slot_machine_item.json", slot_machine(with_lever=True))
write(f"assets/{NS}/items/slot_machine.json",
      {"model": {"type": "minecraft:model", "model": f"{NS}:block/slot_machine_item"}})

# Roulette: the master is the front-left cell; its item shows the compact one-block table.
write(f"assets/{NS}/models/block/roulette_table_item.json", roulette_table())
for ci in range(4):
    for cj in range(2):
        write(f"assets/{NS}/models/block/roulette_table_{ci}_{cj}.json", roulette_cell(ci, cj))
write(f"assets/{NS}/blockstates/roulette_table.json",
      {"variants": variants(lambda f: f"{NS}:block/roulette_table_0_0")})
write(f"assets/{NS}/items/roulette_table.json",
      {"model": {"type": "minecraft:model", "model": f"{NS}:block/roulette_table_item"}})

for name in ("slot_machine", "plinko_board", "crash_pad", "roulette_table"):
    loot = {"type": "minecraft:block", "pools": [{
        "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
        "rolls": 1}]}
    write(f"data/{NS}/loot_table/blocks/{name}.json", loot)
if os.path.exists(os.path.join(RES, f"data/{NS}/loot_table/blocks/cashier_counter.json")):
    os.remove(os.path.join(RES, f"data/{NS}/loot_table/blocks/cashier_counter.json"))

# Part blocks: invisible cells (the master model draws the device), except the roulette table's slices.
PARTS = {"slot_machine_top": ("slot_machine", [(0, 1, 0)]),
         "plinko_board_part": ("plinko_board", [(1, 0, 0), (0, 1, 0), (1, 1, 0)]),
         "crash_pad_part": ("crash_pad", [(1, 0, 0), (1, 1, 0)]),
         "roulette_table_part": ("roulette_table", [(1, 0, 0), (2, 0, 0), (3, 0, 0), (0, 0, 1), (1, 0, 1), (2, 0, 1), (3, 0, 1)])}
PARTICLE = {"slot_machine": T("slot_body"), "plinko_board": T("mahogany"), "crash_pad": T("steel"),
            "roulette_table": T("mahogany")}
for part_block, (device, cells) in PARTS.items():
    empty = f"{device}_empty"
    write(f"assets/{NS}/models/block/{empty}.json", {"textures": {"particle": PARTICLE[device]}})
    vs = {}
    for f, y in ROT.items():
        for k in range(1, 8):
            if device == "roulette_table" and k <= len(cells):
                r, _, b = cells[k - 1]
                model = f"{NS}:block/roulette_table_{r}_{b}"
            else:
                model = f"{NS}:block/{empty}"
            v = {"model": model}
            if y:
                v["y"] = y
            vs[f"facing={f},part={k}"] = v
    write(f"assets/{NS}/blockstates/{part_block}.json", {"variants": vs})

# The croupier's booth: indestructible look-alikes of vanilla blocks.
BOOTH = {"booth_floor": "minecraft:block/polished_blackstone_bricks", "booth_trim": "minecraft:block/gold_block",
         "booth_carpet": "minecraft:block/red_carpet", "booth_post": "minecraft:block/dark_oak_fence_post",
         "booth_lantern": "minecraft:block/lantern"}
for name, model in BOOTH.items():
    write(f"assets/{NS}/blockstates/{name}.json", {"variants": {"": {"model": model}}})
immune = [f"{NS}:{n}" for n in BOOTH] + [f"{NS}:cashier_counter"]
for tag in ("wither_immune", "dragon_immune"):
    write(f"data/minecraft/tags/block/{tag}.json", {"replace": False, "values": immune})

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
write(f"data/{NS}/recipe/plinko_board.json", {"type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "L": "minecraft:lapis_block",
            "N": "minecraft:iron_nugget", "P": "#minecraft:planks"},
    "pattern": ["PNP", "NLN", "IGI"], "result": {"id": f"{NS}:plinko_board"}})
write(f"data/{NS}/recipe/roulette_table.json", {"type": "minecraft:crafting_shaped", "category": "misc",
    "key": {"W": "minecraft:green_wool", "D": "minecraft:diamond", "E": "minecraft:emerald", "P": "#minecraft:planks"},
    "pattern": ["WWW", "DED", "P P"], "result": {"id": f"{NS}:roulette_table"}})
print("models written")
