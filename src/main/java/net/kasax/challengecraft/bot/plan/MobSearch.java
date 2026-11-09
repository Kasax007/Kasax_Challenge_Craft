package net.kasax.challengecraft.bot.plan;

import net.kasax.challengecraft.bot.Bot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * How long finding one mob of a kind takes, from how the game makes them appear - what a player
 * means by "skeletons at night or in a cave, creepers stay into the morning, endermen are rare,
 * witches keep to swamp huts". Looking about, so many mobs of its category come into view per
 * minute (more in a cave or the Nether than on the dark surface, few by day: only those the sun
 * does not burn are left), and the kind's share of them is its share of the biome's spawn list.
 * The search is the wait for the first: 60 / (rate × share) seconds.
 *
 * <p>The rates are calibrated on the hunts of the self-play leagues (league 9 of docs/bob-plan.md:
 * a night hunt for a skeleton took Bob 72 s, one for a zombie or a creeper 80 to 90 s, the fight
 * included). Mobs that only come at their structures (blazes, pillagers, guardians) or by special
 * rules (slimes, phantoms) are not found this way: {@link #UNKNOWN}, and the caller keeps its own
 * figure for them.
 */
public final class MobSearch {
    /** Monsters coming into view per minute of looking about on the surface at night. */
    static final double NIGHT = 5;
    /** ... in a cave or in the Nether: dark at noon, and the caves hold most of the mob cap. */
    static final double CAVE = 6;
    /** ... on the surface by day: what the night left that the sun does not burn (spiders, creepers, endermen). */
    static final double DAY = 0.8;
    /** Animals coming into view per minute where they live (herds on grass, seen from afar). */
    static final double ANIMALS = 6;
    /** Fish and squid per minute at the water's edge. */
    static final double WATER = 4;
    /** Longer than this is no search but a wait for a different time or place. */
    static final double LONGEST = 1800;
    /** Not found by looking about (structure-only, special rules): the caller decides. */
    public static final double UNKNOWN = -1;

    private MobSearch() {
    }

    /**
     * Seconds to come across one, looking about where Bob is now ({@code atHome}: as it will be
     * once in the mob's habitat, after the walk there), in this light: {@code dark} on the surface,
     * {@code underground} in a cave, {@code nether}. {@link #UNKNOWN} for mobs not found so.
     */
    public static double seconds(Bot bot, EntityType<?> type, boolean dark, boolean underground, boolean nether, boolean atHome) {
        SpeedrunKnowledge.Mob facts = SpeedrunKnowledge.get().mob(type);
        String spawn = facts == null ? "" : facts.spawn();
        if (spawn.equals("structure") || spawn.equals("special")) return UNKNOWN;
        ServerLevel level = (ServerLevel) bot.body().level();
        double share = atHome ? MobHabitats.shareAtHome(level, type) : MobHabitats.shareHere(bot, type);
        // (None here, of a kind found nearly everywhere - a mushroom island, a void: the next
        // biome over will do, as at home.)
        if (share <= 0 && !atHome && MobHabitats.anywhere(level, type)) share = MobHabitats.shareAtHome(level, type);
        if (share <= 0) return atHome ? UNKNOWN : LONGEST;
        MobCategory cat = type.getCategory();
        double rate;
        if (cat == MobCategory.MONSTER) {
            if (nether || underground) rate = CAVE;
            else if (dark) rate = NIGHT;
            // (By day the burning kinds are gone from the surface altogether.)
            else rate = type.builtInRegistryHolder().is(EntityTypeTags.BURN_IN_DAYLIGHT) ? 0 : DAY;
        } else if (cat == MobCategory.CREATURE) {
            rate = ANIMALS;
        } else if (cat == MobCategory.WATER_CREATURE || cat == MobCategory.WATER_AMBIENT || cat == MobCategory.UNDERGROUND_WATER_CREATURE
                || cat == MobCategory.AXOLOTLS) {
            rate = WATER;
        } else return UNKNOWN;
        if (rate <= 0) return LONGEST;
        double sight = facts == null ? 1 : Math.max(0.1, facts.sight());
        return Math.min(LONGEST, 60 / (rate * share * sight));
    }

    /**
     * Whether a monster of this kind is only about in the dark: on the surface by day there are
     * none to find (the sun burns them) - a wait for the night, or a cave.
     */
    public static boolean onlyInTheDark(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER && type.builtInRegistryHolder().is(EntityTypeTags.BURN_IN_DAYLIGHT);
    }

    /** Bob's seconds to kill one once found (the way in under fire included), from the knowledge base. */
    public static double fight(EntityType<?> type, double fallback) {
        SpeedrunKnowledge.Mob facts = SpeedrunKnowledge.get().mob(type);
        return facts == null ? fallback : facts.fight();
    }

    /** How many come together (the next one of a pack is close by). */
    public static int pack(EntityType<?> type) {
        SpeedrunKnowledge.Mob facts = SpeedrunKnowledge.get().mob(type);
        return facts == null ? 1 : Math.max(1, facts.pack());
    }
}
