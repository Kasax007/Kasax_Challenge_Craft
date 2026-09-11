#!/usr/bin/env python3
"""Sorts the seed-probe results by the qualities a daily challenge can be built around.

Reads run/challengecraft_seed_probe.json (one JSON object per line, written by SeedProbe) and
prints, per category, the seeds that fit it best. Categories exist because a daily is more
interesting when the terrain argues with the ruleset: no crafting table hurts far more in a desert,
Only Down is a different game on a mountain, No Villager Trading is a joke worth telling next to a
village.
"""
import io
import json
import sys

PATH = sys.argv[1] if len(sys.argv) > 1 else "run/challengecraft_seed_probe.json"
rows = [json.loads(l) for l in io.open(PATH, encoding="utf-8") if l.strip()]
print(f"{len(rows)} Seeds vermessen\n")

TREELESS = {"desert", "badlands", "eroded_badlands", "wooded_badlands", "snowy_plains",
            "ice_spikes", "stony_peaks", "jagged_peaks", "frozen_peaks", "beach",
            "snowy_beach", "stony_shore", "mushroom_fields"}


def near(row, *keys):
    """Smallest distance among the named structures, or a large number if none is in range."""
    st = row.get("structures", {})
    hits = [st[k] for k in keys if k in st]
    return min(hits) if hits else 10 ** 9


def village(row):
    return near(row, "village_plains", "village_desert", "village_savanna",
                "village_snowy", "village_taiga")


def show(title, why, rows_sorted, fmt):
    print(f"── {title}")
    print(f"   {why}")
    for r in rows_sorted[:5]:
        print(f"   {r['seed']:>12}  {r['spawn_biome']:<18} {fmt(r)}")
    print()


show("Dorf direkt am Spawn", "für 'kein Handel' / 'keine Werkbank' — die Rettung liegt in Sicht",
     sorted(rows, key=village), lambda r: f"Dorf {village(r)} Blöcke")

show("Gebirge", "für Only Down, No Jumping, Walk = Damage",
     sorted(rows, key=lambda r: -(r["surface_max"] - r["surface_min"])),
     lambda r: f"H {r['surface_min']}-{r['surface_max']} (Δ{r['surface_max'] - r['surface_min']})")

show("Wasserwelt / Inselstart", "für begrenztes Inventar, Heavy Pockets, No Food",
     sorted(rows, key=lambda r: -r["water_share"]), lambda r: f"{r['water_share']}% Wasser")

show("Festland", "für alles, was Laufwege braucht",
     sorted(rows, key=lambda r: r["water_share"]), lambda r: f"{r['water_share']}% Wasser")

show("Biomvielfalt", "für Sammel- und Erkundungsläufe",
     sorted(rows, key=lambda r: -r["biome_count"]), lambda r: f"{r['biome_count']} Biome")

show("Trial Chambers nah", "für Kampf-Dailies",
     sorted(rows, key=lambda r: near(r, "trial_chambers")),
     lambda r: f"{near(r, 'trial_chambers')} Blöcke")

show("Ancient City nah", "für Size Matters / Deep Dark",
     sorted(rows, key=lambda r: near(r, "ancient_city")),
     lambda r: f"{near(r, 'ancient_city')} Blöcke")

show("Woodland Mansion nah", "seltenste Struktur im Spiel",
     sorted(rows, key=lambda r: near(r, "woodland_mansion")),
     lambda r: f"{near(r, 'woodland_mansion')} Blöcke")

show("Ruined Portal nah", "für Läufe, die schnell in den Nether müssen",
     sorted(rows, key=lambda r: near(r, "ruined_portal")),
     lambda r: f"{near(r, 'ruined_portal')} Blöcke")

show("Baumloser Start", "kein Holz in Reichweite — härtester ehrlicher Start",
     sorted(rows, key=lambda r: (r["spawn_biome"] not in TREELESS, village(r))),
     lambda r: f"Dorf {village(r)}")

print("── Rohdaten")
for r in sorted(rows, key=lambda r: r["seed"]):
    st = r.get("structures", {})
    top = sorted(st.items(), key=lambda kv: kv[1])[:6]
    print(f"   {r['seed']:>12} {r['spawn_biome']:<18} Spawn {r['spawn']} "
          f"H{r['surface_min']}-{r['surface_max']} W{r['water_share']}% B{r['biome_count']}")
    print(f"                " + ", ".join(f"{k} {v}" for k, v in top))
