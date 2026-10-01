package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Fills an empty bucket at a water or lava source it can see: walk up, look at it, use the bucket. */
public final class FillBucketTask implements BotTask {
    private final TagKey<Fluid> fluid;
    private final Item filled, empty;
    private final Set<BlockPos> skip = new HashSet<>();
    private final Explorer explorer = new Explorer(2400);
    private BlockPos source;
    private boolean walking;
    private int tries, idleLegs, walkFails;
    private boolean closer;

    public FillBucketTask(TagKey<Fluid> fluid) {
        this.fluid = fluid;
        this.filled = fluid == FluidTags.LAVA ? Items.LAVA_BUCKET : Items.WATER_BUCKET;
        this.empty = Items.BUCKET;
    }

    /** A glass bottle filled with water (a water bottle: a potion, as far as drinking one goes). */
    public static FillBucketTask bottle() {
        return new FillBucketTask(FluidTags.WATER, Items.POTION, Items.GLASS_BOTTLE);
    }

    private FillBucketTask(TagKey<Fluid> fluid, Item filled, Item empty) {
        this.fluid = fluid;
        this.filled = filled;
        this.empty = empty;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (BotInventory.slotOf(body, filled) >= 0) return Result.DONE;
        if (BotInventory.slotOf(body, empty) < 0 || ++tries > 3000) return Result.FAILED;
        if (source == null || !level.getFluidState(source).isSource()) {
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> any =
                    s -> s.getFluidState().is(fluid) && s.getFluidState().isSource();
            // What a player would take: the source in sight (or the one it remembers: a lake, a
            // river passed on the way) that is quickest to get to. A pool far down a ravine is
            // worth less than a river a little further on; a lake under the hill it never saw is
            // not known at all (see BotWorld#seen).
            BlockPos inSight = BotWorld.nearest(level, body.blockPosition(), 32, 32, any, true, skip);
            BlockPos remembered = bot.memory().nearest(level, body.blockPosition(), any, skip);
            source = effort(body, inSight) <= effort(body, remembered) ? inSight : remembered;
            walking = false;
            if (source == null) {
                if (fluid == FluidTags.LAVA) return Result.FAILED;
                // Nothing known: to the nearest river, sea, beach or swamp it has seen, then out
                // over the surface until some turns up (water is never far).
                if (!toWaterBiome) {
                    toWaterBiome = true;
                    Identifier biome = nearestWaterBiome(bot);
                    if (biome != null) {
                        bot.say("no water known: to the " + biome.getPath().replace('_', ' ') + " I passed");
                        bot.interject(new GoToBiomeTask(biome));
                        return Result.RUNNING;
                    }
                }
                return explorer.tick(bot);
            }
            explorer.pause(bot);
        }
        if (!bot.actions().inReach(source)) {
            // (Far measured over the ground: water in a cave right under the hill it stands on is
            // near, and a leg "over the surface" to it would end where it stands, again and again.)
            double hx = source.getX() - body.getX(), hz = source.getZ() - body.getZ();
            boolean farOff = hx * hx + hz * hz > 24 * 24;
            if (!walking) {
                boolean far = farOff;
                // Far off (a lake it remembers): up out of a mine first, then over the surface in legs.
                if (far && SurfaceTask.underground(body)) {
                    bot.interject(new SurfaceTask());
                    return Result.RUNNING;
                }
                if (far) bot.navigator().goNear(Explorer.legToward(level, bot.navigator().feet(), source, 40), 4);
                else bot.navigator().goNear(source, closer ? 1.5 : 3.2);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            // (A leg of a long way done: the next leg.)
            if (s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(source) && farOff) {
                walking = false;
                // (Legs that end where they began: not getting anywhere this way, another source.)
                if (++idleLegs > 4) {
                    skip.add(source);
                    source = null;
                    idleLegs = 0;
                }
                return Result.RUNNING;
            }
            if (s == BotNavigator.Status.MOVING) idleLegs = 0;
            if (s == BotNavigator.Status.IDLE) walking = false;
            // There by the walk's measure but not in reach (down a hole, round a corner): once
            // more, right up to it; then another one.
            if (s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(source) && !closer) {
                closer = true;
                walking = false;
                return Result.RUNNING;
            }
            // (One failed walk is no reason to give up a source: a fall, a mob in the way. A few.)
            if (s == BotNavigator.Status.FAILED && ++walkFails <= 3) {
                walking = false;
                return Result.RUNNING;
            }
            if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(source)) {
                skip.add(source);
                source = null;
                closer = false;
                walkFails = 0;
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        bot.tools().select(BotInventory.slotOf(body, empty));
        body.lookAt(Vec3.atCenterOf(source).add(0, 0.4, 0));
        body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
        if (BotInventory.slotOf(body, filled) >= 0) return Result.DONE;
        skip.add(source); // the look did not hit it (something in between): another one
        source = null;
        return Result.RUNNING;
    }

    private boolean toWaterBiome;

    /** Roughly the seconds to get to a source: the walk, and the climb down (digging) to it. */
    private static double effort(BotPlayer body, BlockPos p) {
        if (p == null) return Double.MAX_VALUE;
        double hx = p.getX() - body.getX(), hz = p.getZ() - body.getZ();
        int down = Math.max(0, body.getBlockY() - p.getY() - 2), up = Math.max(0, p.getY() - body.getBlockY() - 2);
        return Math.sqrt(hx * hx + hz * hz) / 4.3 + down * 1.5 + up * 0.6;
    }

    private static final java.util.List<String> WATER_BIOMES = java.util.List.of("river", "ocean", "beach", "swamp", "mangrove_swamp",
            "lukewarm_ocean", "warm_ocean", "cold_ocean", "deep_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "frozen_river",
            "stony_shore", "mushroom_fields");

    private static Identifier nearestWaterBiome(Bot bot) {
        Identifier best = null;
        double bestD = 600 * 600;
        for (String name : WATER_BIOMES) {
            Identifier id = Identifier.withDefaultNamespace(name);
            BlockPos p = bot.senses().biome(id);
            if (p == null) continue;
            double d = p.distSqr(bot.body().blockPosition());
            if (d < bestD) {
                bestD = d;
                best = id;
            }
        }
        return best;
    }

    @Override
    public String status() {
        return describe() + " [source " + (source == null ? "-" : source.toShortString()) + ", closer " + closer + ", skipped " + skip.size() + ", tries " + tries + "]";
    }

    @Override
    public String describe() {
        return "fill a " + (empty == Items.GLASS_BOTTLE ? "bottle" : "bucket") + " with " + (fluid == FluidTags.LAVA ? "lava" : "water");
    }
}
