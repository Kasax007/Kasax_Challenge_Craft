package net.kasax.challengecraft.bot.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where a kind of mob lives, from the game's own spawn lists: horses in plains and savannas,
 * drowned in rivers and oceans, slimes in swamps. Something a player knows; Bob looks it up. Mobs
 * that spawn nearly everywhere (zombies, skeletons) have no habitat to go to.
 */
public final class MobHabitats {
    private static final Map<EntityType<?>, Set<Identifier>> CACHE = new HashMap<>();
    private static final Map<EntityType<?>, Boolean> ANYWHERE = new HashMap<>();
    private static Object cachedFor;

    private MobHabitats() {
    }

    /** The biomes it spawns in; empty when it spawns (nearly) anywhere or nowhere naturally. */
    public static Set<Identifier> of(ServerLevel level, EntityType<?> type) {
        if (cachedFor != level.getServer()) {
            CACHE.clear();
            ANYWHERE.clear();
            cachedFor = level.getServer();
        }
        return CACHE.computeIfAbsent(type, t -> compute(level, t));
    }

    private static Set<Identifier> compute(ServerLevel level, EntityType<?> type) {
        Set<Identifier> out = new HashSet<>();
        int total = 0, maxWeight = 0;
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (var holder : biomes.listElements().toList()) {
            total++;
            MobSpawnSettings spawns = holder.value().getAttributes().applyModifier(
                    net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
            if (spawns == null) continue;
            boolean here = false;
            for (MobCategory cat : MobCategory.values()) {
                for (var w : spawns.getMobsToSpawn(cat).unwrap()) {
                    if (w.value().type() != type) continue;
                    here = true;
                    maxWeight = Math.max(maxWeight, w.weight());
                }
            }
            if (here) out.add(holder.key().identifier());
        }
        // (Almost everywhere is no habitat to walk to. "Anywhere" for a hunt only if common there
        // too: a witch is one in twenty of the night's monsters, and slimes keep to their own
        // chunks deep down and to swamps whatever the list says.)
        ANYWHERE.put(type, out.size() * 2 > total && maxWeight >= 50 && type != net.minecraft.world.entity.EntityTypes.SLIME);
        if (out.size() * 2 > total) return Set.of();
        return out;
    }

    /**
     * Whether it may turn up wherever Bob is (zombies, creepers at night): true for those that
     * spawn nearly everywhere, false for those with a habitat elsewhere and for those that never
     * spawn naturally (cave spiders: only from the spawners in mineshafts).
     */
    public static boolean anywhere(ServerLevel level, EntityType<?> type) {
        of(level, type);
        return ANYWHERE.getOrDefault(type, false);
    }

    /**
     * Of the mobs of its category a biome's spawn list makes (by weight times the pack size), the
     * share of this kind: a skeleton is about a quarter of the plains' night monsters, an enderman
     * one in seventy, a witch one in three hundred. Slimes are left out of the others' lists: they
     * spawn only in slime chunks deep down and in swamps, whatever the list says. 0: not here.
     */
    public static double share(ServerLevel level, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, EntityType<?> type) {
        MobSpawnSettings spawns = biome.value().getAttributes().applyModifier(
                net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
        if (spawns == null) return 0;
        double mine = 0, all = 0;
        for (var w : spawns.getMobsToSpawn(type.getCategory()).unwrap()) {
            var data = w.value();
            if (data.type() == net.minecraft.world.entity.EntityTypes.SLIME && type != net.minecraft.world.entity.EntityTypes.SLIME) continue;
            double n = w.weight() * (data.count().minInclusive() + data.count().maxInclusive()) / 2.0;
            all += n;
            if (data.type() == type) mine += n;
        }
        return all <= 0 ? 0 : mine / all;
    }

    /** {@link #share} where Bob stands. */
    public static double shareHere(net.kasax.challengecraft.bot.Bot bot, EntityType<?> type) {
        ServerLevel level = (ServerLevel) bot.body().level();
        return share(level, level.getBiome(bot.body().blockPosition()), type);
    }

    private static final Map<EntityType<?>, Double> HOME_SHARE = new HashMap<>();
    private static Object homeShareFor;

    /** {@link #share} in the biomes it lives in, on average (for a hunt in its habitat, after the walk there). */
    public static double shareAtHome(ServerLevel level, EntityType<?> type) {
        if (homeShareFor != level.getServer()) {
            HOME_SHARE.clear();
            homeShareFor = level.getServer();
        }
        return HOME_SHARE.computeIfAbsent(type, t -> {
            double sum = 0;
            int n = 0;
            for (var holder : level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
                double s = share(level, holder, t);
                if (s <= 0) continue;
                sum += s;
                n++;
            }
            return n == 0 ? 0 : sum / n;
        });
    }

    /** The nearest spot of its habitat Bob knows of in his atlas, or null. */
    public static BlockPos nearestKnown(net.kasax.challengecraft.bot.Bot bot, EntityType<?> type) {
        ServerLevel level = (ServerLevel) bot.body().level();
        Set<Identifier> home = of(level, type);
        if (home.isEmpty()) return null;
        BlockPos from = bot.body().blockPosition(), best = null;
        for (var e : bot.senses().biomes().entrySet()) {
            if (!home.contains(e.getKey())) continue;
            if (best == null || e.getValue().distSqr(from) < best.distSqr(from)) best = e.getValue();
        }
        return best;
    }

    /** Whether Bob stands in its habitat now (true too when it has none in particular). */
    public static boolean inHabitat(net.kasax.challengecraft.bot.Bot bot, EntityType<?> type) {
        ServerLevel level = (ServerLevel) bot.body().level();
        Set<Identifier> home = of(level, type);
        if (home.isEmpty()) return true;
        return level.getBiome(bot.body().blockPosition()).unwrapKey().map(k -> home.contains(k.identifier())).orElse(false);
    }
}
