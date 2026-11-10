#!/usr/bin/env python3
"""Priors for Bob's world model (knob ``beliefs``) from the game's own data.

Reads the worldgen JSON of the 26.3 server jar (ore placement, biome spawners, tree features)
and writes ``src/main/resources/bob-knowledge/beliefs.json``:

* ``ores``: for iron and coal, the expected ore blocks per chunk at each height (y -64..319),
  from every placed feature of the ore: count x expected vein blocks x the height
  distribution (uniform, or trapezoid as TrapezoidHeight samples it).
* ``biomes``: per overworld biome, the natural spawn weights (creature: cow, pig, sheep,
  chicken and the total; monster: zombie, skeleton, spider, creeper and the total) and the
  expected trees per chunk from its tree features (count providers and rarity filters).

Usage: python3 scripts/bot/belief_priors.py [server-jar]
(default: the loom cache's minecraft-extracted_server.jar for 26.3).
"""
import json
import os
import sys
import zipfile

JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-extracted_server.jar")
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "bob-knowledge", "beliefs.json")
MIN_Y, MAX_Y = -64, 320  # the overworld: bottom -64, top 320 (exclusive)
WG = "data/minecraft/worldgen/"

# Expected ore blocks a vein of this size leaves (OreFeature's blobs along a line, stone only):
# about half the size and a half. An estimate - the placement numbers above it come from the jar.
def vein_blocks(size, discard):
    return (0.55 * size + 0.5) * (1 - 0.2 * discard)


def anchor(a):
    if "absolute" in a:
        return a["absolute"]
    if "above_bottom" in a:
        return MIN_Y + a["above_bottom"]
    if "below_top" in a:
        return MAX_Y - 1 - a["below_top"]
    raise ValueError(a)


def height_pdf(h):
    """Probability of each y (MIN_Y..MAX_Y-1) for a height provider."""
    lo, hi = anchor(h["min_inclusive"]), anchor(h["max_inclusive"])
    pdf = [0.0] * (MAX_Y - MIN_Y)
    if h["type"] == "minecraft:uniform":
        for y in range(lo, hi + 1):
            if MIN_Y <= y < MAX_Y:
                pdf[y - MIN_Y] += 1 / (hi - lo + 1)
    elif h["type"] == "minecraft:trapezoid":
        k = hi - lo
        plateau = h.get("plateau", 0)
        l = (k - plateau) // 2
        m = k - l
        for a in range(m + 1):
            for b in range(l + 1):
                y = lo + a + b
                if MIN_Y <= y < MAX_Y:
                    pdf[y - MIN_Y] += 1 / ((m + 1) * (l + 1))
    else:
        raise ValueError(h["type"])
    return pdf


def expected_count(c):
    if isinstance(c, (int, float)):
        return float(c)
    t = c["type"]
    if t == "minecraft:uniform":
        return (c["min_inclusive"] + c["max_inclusive"]) / 2
    if t == "minecraft:weighted_list":
        tot = sum(d["weight"] for d in c["distribution"])
        return sum(expected_count(d["data"]) * d["weight"] for d in c["distribution"]) / tot
    if t == "minecraft:constant":
        return float(c["value"])
    if t == "minecraft:clamped":
        return expected_count(c["source"])
    if t == "minecraft:biased_to_bottom":
        return (c["min_inclusive"] + c["max_inclusive"]) / 3
    raise ValueError(t)


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else JAR
    z = zipfile.ZipFile(jar)
    read = lambda p: json.loads(z.read(WG + p))
    ores = {}
    for ore, feats in {"iron": ["ore_iron_upper", "ore_iron_middle", "ore_iron_small"],
                       "coal": ["ore_coal_upper", "ore_coal_lower"]}.items():
        total = [0.0] * (MAX_Y - MIN_Y)
        for f in feats:
            placed = read("placed_feature/%s.json" % f)
            feature = read("feature/%s.json" % placed["feature"].split(":")[1])
            count, pdf = 1.0, None
            for p in placed["placement"]:
                if p["type"] == "minecraft:count":
                    count *= expected_count(p["count"])
                elif p["type"] == "minecraft:rarity_filter":
                    count /= p["chance"]
                elif p["type"] == "minecraft:height_range":
                    pdf = height_pdf(p["height"])
            per = count * vein_blocks(feature["size"], feature.get("discard_chance_on_air_exposure", 0))
            for i in range(len(total)):
                total[i] += per * pdf[i]
        ores[ore] = {"min_y": MIN_Y, "per_chunk": [round(v, 4) for v in total]}

    trees = {}
    tree_names = {n.split("/")[-1][:-5] for n in z.namelist() if n.startswith(WG + "placed_feature/trees_")}
    tree_names |= {"birch_tall", "dark_forest_vegetation", "pale_garden_vegetation", "bamboo_vegetation", "rooted_azalea_tree"}
    for name in tree_names:
        try:
            placed = read("placed_feature/%s.json" % name)
        except KeyError:
            continue
        count = 1.0
        for p in placed["placement"]:
            if p["type"] == "minecraft:count":
                count *= expected_count(p["count"])
            elif p["type"] == "minecraft:rarity_filter":
                count /= p["chance"]
        trees[name] = count

    biomes = {}
    for n in sorted(z.namelist()):
        if not n.startswith(WG + "biome/") or not n.endswith(".json"):
            continue
        bid = n.split("/")[-1][:-5]
        b = json.loads(z.read(n))
        spawns = b.get("attributes", {}).get("minecraft:gameplay/natural_mob_spawns", {}).get("argument", {}).get("spawns_by_category", {})
        out = {"trees": 0.0, "creature": {}, "monster": {}}
        for cat, keep in (("creature", ("cow", "pig", "sheep", "chicken")), ("monster", ("zombie", "skeleton", "spider", "creeper", "husk", "stray", "drowned"))):
            tot = 0
            for s in spawns.get(cat, []):
                t = s["type"].split(":")[1]
                tot += s["weight"]
                if t in keep:
                    out[cat][t] = out[cat].get(t, 0) + s["weight"]
            out[cat]["_total"] = tot
        for step in b.get("features", []):
            for f in step:
                k = f.split(":")[1]
                if k in trees:
                    out["trees"] += trees[k]
        out["trees"] = round(out["trees"], 3)
        biomes[bid] = out
    doc = {"source": "26.3 server jar, data/minecraft/worldgen (scripts/bot/belief_priors.py)",
           "ores": ores, "biomes": biomes}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as fh:
        json.dump(doc, fh, separators=(",", ":"))
    it = ores["iron"]["per_chunk"]
    print("iron blocks/chunk: total %.1f, at y16 %.3f, y40 %.3f, y100 %.3f, y200 %.3f" % (
        sum(it), it[16 - MIN_Y], it[40 - MIN_Y], it[100 - MIN_Y], it[200 - MIN_Y]))
    print("trees/chunk plains %.2f forest %.2f desert %.2f taiga %.2f" % tuple(
        biomes.get(b, {}).get("trees", -1) for b in ("plains", "forest", "desert", "taiga")))
    print("biomes", len(biomes), "->", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
