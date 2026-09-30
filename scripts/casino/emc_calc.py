"""Computes the Jeton value of every vanilla item for "The House Always Wins" (challenge 50).

Faithful to ProjectE (github.com/sinkillerj/ProjectE, MIT):
  * the base values of pe_custom_conversions/defaults.json + metals.json ("before" values are fixed),
  * ProjectE's extra conversion groups (chainmail, concrete, anvils, dirt, fluids),
  * every vanilla recipe: value(output) = sum(inputs) / count, the CHEAPEST recipe wins,
  * an ingredient that accepts several items is worth its cheapest valued option,
  * ores and raw materials are worth nothing (OreBlacklistMapper / RawMaterialsBlacklistMapper),
  * fractions are kept exactly and floored at the end; anything below 1 has no value.

Inputs are the ProjectE conversion files and the 26.3 data dump from github.com/misode/mcmeta
(tag 26.3-data-json). Output: src/main/resources/data/challengecraft/casino/emc_values.json

usage: python3 emc_calc.py PROJECTE_CONVERSIONS_DIR MCMETA_DATA_DIR OUT_JSON
"""
import glob
import json
import os
import sys
from fractions import Fraction

pe_dir, mc_dir, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
TAG_DIR = os.path.join(mc_dir, "data/minecraft/tags/item")
RECIPE_DIR = os.path.join(mc_dir, "data/minecraft/recipe")


def mc(i):
    return i if ":" in i else "minecraft:" + i


# --- tags -----------------------------------------------------------------------------------------
_tag_cache = {}


def vanilla_tag(name):
    name = name.split(":", 1)[1] if name.startswith("minecraft:") else name
    if name in _tag_cache:
        return _tag_cache[name]
    path = os.path.join(TAG_DIR, name + ".json")
    out = []
    if os.path.exists(path):
        for v in json.load(open(path))["values"]:
            v = v["id"] if isinstance(v, dict) else v
            out += vanilla_tag(v[1:]) if v.startswith("#") else [mc(v)]
    _tag_cache[name] = out
    return out


# Conventional (c:) tags have no vanilla data; these are the vanilla members that matter here.
C_TAGS = {
    "c:ingots/iron": ["minecraft:iron_ingot"], "c:ingots/gold": ["minecraft:gold_ingot"],
    "c:ingots/copper": ["minecraft:copper_ingot"], "c:gems/diamond": ["minecraft:diamond"],
    "c:gems/emerald": ["minecraft:emerald"], "c:gems/quartz": ["minecraft:quartz"],
    "c:gems/amethyst": ["minecraft:amethyst_shard"], "c:nether_stars": ["minecraft:nether_star"],
    "c:rods/blaze": ["minecraft:blaze_rod"], "c:rods/breeze": ["minecraft:breeze_rod"],
    "c:rods/wooden": ["minecraft:stick"], "c:seeds/wheat": ["minecraft:wheat_seeds"],
    "c:seeds/beetroot": ["minecraft:beetroot_seeds"], "c:crops/wheat": ["minecraft:wheat"],
    "c:crops/nether_wart": ["minecraft:nether_wart"], "c:crops/carrot": ["minecraft:carrot"],
    "c:crops/beetroot": ["minecraft:beetroot"], "c:crops/potato": ["minecraft:potato"],
    "c:pumpkins/normal": ["minecraft:pumpkin"], "c:dusts/redstone": ["minecraft:redstone"],
    "c:dusts/glowstone": ["minecraft:glowstone_dust"],
}


def tag_members(tag):
    if tag.startswith("c:"):
        return C_TAGS.get(tag, [])
    return vanilla_tag(tag)


def options(ing):
    """Every item an ingredient accepts."""
    if isinstance(ing, list):
        out = []
        for i in ing:
            out += options(i)
        return out
    if isinstance(ing, dict):
        if "tag" in ing:
            return tag_members(ing["tag"])
        if "item" in ing:
            return [mc(ing["item"])]
        if "id" in ing:
            return [mc(ing["id"])]
        return []
    if ing.startswith("#"):
        return tag_members(ing[1:])
    return [mc(ing)]


# --- base values ----------------------------------------------------------------------------------
fixed = {}
conversions = []  # (output, count, [(options, amount)])


def pe_stack(s):
    if s.get("type") == "projecte:fluid":
        return ["fluid:" + (s.get("tag") or s.get("id"))]
    if s.get("type") == "projecte:fake":
        return ["fake:" + s.get("description", "?")]
    if "tag" in s:
        return tag_members(s["tag"])
    return [mc(s["id"])]


for fname in ("defaults.json", "metals.json"):
    data = json.load(open(os.path.join(pe_dir, fname)))
    for entry in data.get("values", {}).get("before", []):
        v = entry["emc_value"]
        if v == "free":
            v = 0
        for item in pe_stack(entry):
            fixed[item] = Fraction(v)
    for conv in data.get("values", {}).get("conversion", []):
        for out in pe_stack(conv["output"]):
            conversions.append((out, conv.get("count", 1),
                                [(pe_stack(i), i.get("amount", 1)) for i in conv["ingredients"]]))
    for group in data.get("groups", {}).values():
        for conv in group["conversions"]:
            for out in pe_stack(conv["output"]):
                conversions.append((out, conv.get("count", 1),
                                    [(pe_stack(i), i.get("amount", 1)) for i in conv["ingredients"]]))

# Buckets hand their container back in crafting (ProjectE's CraftingMapper subtracts remainders).
REMAINDER = {"minecraft:milk_bucket": "minecraft:bucket", "minecraft:water_bucket": "minecraft:bucket",
             "minecraft:lava_bucket": "minecraft:bucket", "minecraft:honey_bottle": "minecraft:glass_bottle",
             "minecraft:dragon_breath": "minecraft:glass_bottle"}

# --- vanilla recipes ------------------------------------------------------------------------------
for f in sorted(glob.glob(os.path.join(RECIPE_DIR, "*.json"))):
    r = json.load(open(f))
    t = r["type"]
    res = r.get("result")
    if not isinstance(res, dict) or "id" not in res:
        continue
    out, count = mc(res["id"]), res.get("count", 1)
    ings = []
    if t == "minecraft:crafting_shaped":
        counts = {}
        for row in r["pattern"]:
            for ch in row:
                if ch != " ":
                    counts[ch] = counts.get(ch, 0) + 1
        ings = [(options(r["key"][k]), n) for k, n in counts.items()]
    elif t == "minecraft:crafting_shapeless":
        ings = [(options(i), 1) for i in r["ingredients"]]
    elif t == "minecraft:crafting_transmute":
        ings = [(options(r["input"]), 1), (options(r["material"]), 1)]
    elif t in ("minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
               "minecraft:campfire_cooking", "minecraft:stonecutting"):
        ings = [(options(r["ingredient"]), 1)]
    elif t == "minecraft:smithing_transform":
        ings = [(options(r["template"]), 1), (options(r["base"]), 1), (options(r["addition"]), 1)]
    else:
        continue
    extra = []
    for opts, n in ings:
        if len(opts) == 1 and opts[0] in REMAINDER:
            extra.append((REMAINDER[opts[0]], n))
    conversions.append((out, count, ings, extra))

# ProjectE blacklists: ores and raw materials are worth nothing.
blacklist = set(i for i in vanilla_tag("coal_ores") + vanilla_tag("iron_ores") + vanilla_tag("gold_ores")
                + vanilla_tag("copper_ores") + vanilla_tag("diamond_ores") + vanilla_tag("emerald_ores")
                + vanilla_tag("lapis_ores") + vanilla_tag("redstone_ores"))
blacklist |= {"minecraft:nether_quartz_ore", "minecraft:nether_gold_ore", "minecraft:ancient_debris",
              "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
              "minecraft:raw_iron_block", "minecraft:raw_gold_block", "minecraft:raw_copper_block"}

values = dict(fixed)
for b in blacklist:
    values.pop(b, None)

# --- fixed point: cheapest recipe wins ------------------------------------------------------------
changed = True
rounds = 0
while changed and rounds < 200:
    changed = False
    rounds += 1
    for conv in conversions:
        out, count, ings = conv[0], conv[1], conv[2]
        extra = conv[3] if len(conv) > 3 else []
        if out in fixed or out in blacklist:
            continue
        total = Fraction(0)
        ok = True
        for opts, n in ings:
            vals = [values[o] for o in opts if o in values and values[o] > 0]
            if not vals:
                ok = False
                break
            total += min(vals) * n
        if not ok:
            continue
        for item, n in extra:
            total -= values.get(item, 0) * n
        if total <= 0:
            continue
        v = total / count
        if out not in values or v < values[out]:
            values[out] = v
            changed = True

result = {}
for item, v in values.items():
    if not item.startswith("minecraft:"):
        continue
    iv = int(v)  # floor, like ProjectE
    if iv >= 1:
        result[item] = iv

os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w") as fh:
    fh.write("{\n  \"_comment\": \"Jeton values derived from ProjectE (MIT, sinkillerj/ProjectE) base values and the Minecraft 26.3 recipes. Generated by scripts/casino/emc_calc.py - do not edit by hand.\",\n")
    fh.write("  \"values\": {\n")
    items = sorted(result.items())
    for idx, (k, v) in enumerate(items):
        fh.write(f"    \"{k}\": {v}" + (",\n" if idx < len(items) - 1 else "\n"))
    fh.write("  }\n}\n")
print(f"{len(result)} items valued after {rounds} rounds")
for probe in ["oak_log", "oak_planks", "stick", "crafting_table", "torch", "iron_ingot", "iron_block",
              "gold_ingot", "copper_ingot", "diamond", "netherite_ingot", "iron_pickaxe", "diamond_pickaxe",
              "bread", "cooked_beef", "ender_eye", "raw_iron", "iron_ore", "glass", "furnace", "bucket",
              "chest", "lava_bucket", "cake", "firework_rocket", "tnt", "gold_nugget", "iron_nugget",
              "emerald", "lapis_lazuli", "redstone_block", "slime_ball", "paper", "book", "leather",
              "string", "white_wool", "arrow", "bow", "shield", "golden_apple", "enchanted_golden_apple"]:
    print(f"  {probe:24s} {result.get('minecraft:' + probe)}")
