package net.kasax.challengecraft.data;

import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.challenges.Chal_45_ForceItemBattle;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Boot-time audit that every hardcoded registry id the mod ships actually resolves.
 *
 * <p><b>Why this exists.</b> The mod hardcodes ~544 distinct {@code "minecraft:..."} id strings
 * (489 in {@link LockoutBingoGoalPool}, 368 occurrences in {@link Chal_45_ForceItemBattle}, plus
 * the surveyed block dataset). Nothing checks them: the compiler cannot see inside a string, the
 * remapping tools used for a version port do not touch string literals, and the mod's own runtime
 * behaviour is to log-and-skip or silently fall back to AIR.
 *
 * <p>The consequence, and the reason this class is the first thing built for the 26.2 port: a
 * Minecraft version bump produces a <b>green build, a clean boot, and a silently degraded game</b>
 * — a shorter Force Item Battle pool, invisible "air" Lockout goals, a thinner Chal_44 block pool.
 * This audit converts that silent failure surface into a loud, itemised worklist.
 *
 * <p>Run it with {@code /challengecraft_verify_ids}, or set
 * {@code CHALLENGECRAFT_VERIFY_IDS=1} to have it run automatically on server start (for CI and for
 * the port, where it should be re-run after every version bump).
 */
public final class RegistryIdAudit {
    /** One unresolved id. */
    public record Problem(String source, String id, String expected) {
    }

    /** Outcome of a full audit run. */
    public record Result(int checked, List<Problem> problems, Map<String, Integer> perSourceChecked) {
        public boolean clean() {
            return problems.isEmpty();
        }
    }

    private static final String AUTORUN_ENV = System.getenv("CHALLENGECRAFT_VERIFY_IDS");

    private RegistryIdAudit() {
    }

    /**
     * Hooks the audit to server start when {@code CHALLENGECRAFT_VERIFY_IDS=1} is set, so it can
     * run headlessly (CI, and after every version bump during the 26.2 port) without a player.
     */
    public static void register() {
        if (AUTORUN_ENV == null || AUTORUN_ENV.isBlank()) return;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ChallengeCraft.LOGGER.info("[IdAudit] autorun via CHALLENGECRAFT_VERIFY_IDS");
            logReport(run(server));
        });
    }

    public static Result run(MinecraftServer server) {
        List<Problem> problems = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        counts.put("Chal_45 FORCE_ITEM_IDS", auditItems(
                "Chal_45 FORCE_ITEM_IDS", Chal_45_ForceItemBattle.FORCE_ITEM_IDS, problems));

        counts.put("LockoutBingo goals", auditLockoutGoals(server, problems));

        counts.put("BlockSurvey dataset", auditBlocks(
                "BlockSurvey dataset", BlockSurvey.getCollectedIds(), problems));

        int checked = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new Result(checked, problems, counts);
    }

    // ---- individual registries ------------------------------------------------------------

    private static int auditItems(String source, List<String> ids, List<Problem> out) {
        for (String id : ids) {
            Identifier key = parse(id);
            if (key == null || BuiltInRegistries.ITEM.getValue(key) == Items.AIR) {
                out.add(new Problem(source, id, "item"));
            }
        }
        return ids.size();
    }

    private static int auditBlocks(String source, List<String> ids, List<Problem> out) {
        for (String id : ids) {
            Identifier key = parse(id);
            if (key == null || BuiltInRegistries.BLOCK.getValue(key) == Blocks.AIR) {
                out.add(new Problem(source, id, "block"));
            }
        }
        return ids.size();
    }

    /**
     * Lockout goals are the nastiest surface: {@code createIconStack()} falls back to AIR without
     * complaint, so a stale icon id yields a blank tile, and a stale target yields a goal that can
     * never be completed. Both are invisible in play.
     */
    private static int auditLockoutGoals(MinecraftServer server, List<Problem> out) {
        int checked = 0;
        for (LockoutBingoGoal goal : LockoutBingoGoalPool.all()) {
            String where = "LockoutBingo goal '" + goal.id() + "'";

            if (!goal.iconItemId().isBlank()) {
                checked++;
                Identifier key = parse(goal.iconItemId());
                if (key == null || BuiltInRegistries.ITEM.getValue(key) == Items.AIR) {
                    out.add(new Problem(where + " icon", goal.iconItemId(), "item"));
                }
            }

            for (String target : goal.targets()) {
                if (target == null || target.isBlank() || !target.contains(":")) {
                    continue; // tags, biomes, structures and free-form action targets
                }
                checked++;
                checkTarget(server, where + " target", goal.type(), target, out);
            }
        }
        return checked;
    }

    /**
     * Semantic pseudo-targets that intentionally name no registry entry. Verified by reading the
     * goal definitions: e.g. {@code interact(..., "minecraft:bed", "minecraft:red_bed")} passes the
     * *concept* as the target and a concrete item only as the icon.
     */
    private static final java.util.Set<String> PSEUDO_TARGETS = java.util.Set.of(
            "minecraft:animal",  // "breed_animals" — any breedable animal
            "minecraft:bed"      // "sleep_in_bed" — any bed colour
    );

    /** Resolves a goal target against whichever registry its goal type implies. */
    private static void checkTarget(MinecraftServer server, String where,
                                    LockoutBingoGoalType type, String target, List<Problem> out) {
        // Tags (#namespace:path) are not identifiers and are resolved through tag lookup at
        // runtime, not the item registry.
        if (target.startsWith("#")) return;
        // The mod's own namespace is used for semantic action targets (challengecraft:enchant,
        // challengecraft:y_-50, ...) which deliberately have no registry entry.
        if (target.startsWith(ChallengeCraft.MOD_ID + ":")) return;
        if (PSEUDO_TARGETS.contains(target)) return;

        Identifier key = parse(target);
        if (key == null) {
            out.add(new Problem(where, target, "well-formed identifier"));
            return;
        }
        switch (type) {
            case KILL -> {
                // EntityType.byString is gone in 26.2; go through the registry like every other
                // case here does. `key` is the already-parsed form of `target`.
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                    out.add(new Problem(where, target, "entity type"));
                }
            }
            case PLACE_BLOCK -> {
                if (BuiltInRegistries.BLOCK.getValue(key) == Blocks.AIR) {
                    out.add(new Problem(where, target, "block"));
                }
            }
            case ADVANCEMENT -> {
                if (server != null && server.getAdvancements().get(key) == null) {
                    out.add(new Problem(where, target, "advancement"));
                }
            }
            case ITEM_TAG, BIOME, DIMENSION, STRUCTURE, ACTION, DAMAGE_EVENT -> {
                // Data-driven / free-form targets, not resolvable against a static registry here.
                // ITEM_TAG in particular is resolved by the goal itself — see
                // LockoutBingoGoal.matchesTagLikeGoal(), which matches "minecraft:music_discs" by
                // item-path prefix rather than by a registry or tag lookup.
            }
            default -> {
                // Goals name all sorts of things: items, blocks, mobs to interact with (milk a
                // cow, shear a sheep), villager professions to trade with, potion effects to
                // obtain. Accept the target if ANY of those registries knows it, and only report
                // when it resolves nowhere — that is what a genuine rename looks like.
                if (!resolvesAnywhere(key, target)) {
                    out.add(new Problem(where, target, "item / block / entity / effect / profession"));
                }
            }
        }
    }

    private static boolean resolvesAnywhere(Identifier key, String raw) {
        if (BuiltInRegistries.ITEM.getValue(key) != Items.AIR) return true;
        if (BuiltInRegistries.BLOCK.getValue(key) != Blocks.AIR) return true;
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) return true;
        if (BuiltInRegistries.POTION.containsKey(key)) return true;
        if (BuiltInRegistries.MOB_EFFECT.containsKey(key)) return true;
        return BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key);
    }

    private static Identifier parse(String id) {
        try {
            return Identifier.parse(id);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- reporting ---------------------------------------------------------------------------

    /** Logs a human-readable report. Returns true when everything resolved. */
    public static boolean logReport(Result result) {
        ChallengeCraft.LOGGER.info("[IdAudit] checked {} hardcoded registry ids", result.checked());
        result.perSourceChecked().forEach((source, n) ->
                ChallengeCraft.LOGGER.info("[IdAudit]   {} -> {} ids", source, n));

        if (result.clean()) {
            ChallengeCraft.LOGGER.info("[IdAudit] OK — every id resolves.");
            return true;
        }

        ChallengeCraft.LOGGER.error("[IdAudit] {} UNRESOLVED id(s) — these fail SILENTLY in game:",
                result.problems().size());
        for (Problem p : result.problems()) {
            ChallengeCraft.LOGGER.error("[IdAudit]   {}: '{}' is not a valid {}",
                    p.source(), p.id(), p.expected());
        }
        return false;
    }
}
