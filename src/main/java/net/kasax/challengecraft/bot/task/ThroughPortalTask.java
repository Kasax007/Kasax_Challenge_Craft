package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Back through a portal: walks into the nearest lit portal it knows (the one it came through) and
 * waits in it. Without one nearby, builds a new one if it carries the obsidian, or casts one at a
 * lava pool with its buckets.
 */
public final class ThroughPortalTask implements BotTask {
    private ResourceKey<Level> from;
    private BlockPos portal;
    private boolean walking;
    private int ticks;
    private BotTask build;
    private boolean legging;
    private BlockPos lastFeet;
    private int legFails;

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (from == null) from = level.dimension();
        if (level.dimension() != from) return Result.DONE;
        if (++ticks > 4800) return failed(bot);
        if (build != null) {
            Result r = build.tick(bot);
            return r == Result.FAILED ? failed(bot) : r;
        }
        // Out of blocks to climb or bridge with on the way (a pillar up to a portal on a ledge):
        // a stack of the rock about first, then on.
        if (fetch != null) {
            Result r = fetch.tick(bot);
            if (r == Result.RUNNING) return r;
            fetch = null;
            walking = legging = false;
            if (r == Result.FAILED && ++fetchFails > 2) return failed(bot);
            return Result.RUNNING;
        }
        if (portal == null || !level.getBlockState(portal).is(Blocks.NETHER_PORTAL)) {
            portal = BotWorld.nearest(level, bot.body().blockPosition(), 64, 24, s -> s.is(Blocks.NETHER_PORTAL), false, Set.of());
            walking = false;
            if (portal == null) {
                // The one it came through, however far away: it remembers where that was.
                BlockPos known = bot.senses().knownPortal(bot.body().blockPosition());
                if (known != null && legFails < 16) {
                    // Over there in legs (far off, the path search cannot see it all at once). Far
                    // above or below (down a cliff, by the lava sea), or the legs getting nowhere:
                    // straight at it instead, through the rock (steps dug, blocks put under).
                    if (!legging) {
                        BlockPos feet = bot.navigator().feet();
                        if (legFails >= 3 || Math.abs(known.getY() - feet.getY()) > 12) {
                            BlockPos target = known;
                            BlockPos aim = feet.distSqr(known) > 40 * 40
                                    ? feet.offset((known.getX() - feet.getX()) / 3, (known.getY() - feet.getY()) / 2, (known.getZ() - feet.getZ()) / 3) : known;
                            bot.navigator().setGoal(p -> p.distSqr(aim) < 6 * 6 || p.distSqr(target) < 6 * 6, aim);
                        } else {
                            bot.navigator().goStandNear(Explorer.legToward(level, feet, known, 40), 3);
                        }
                        legging = true;
                    }
                    BotNavigator.Status s = bot.navigator().tick();
                    if (s != BotNavigator.Status.MOVING) legging = false;
                    if (s == BotNavigator.Status.FAILED) {
                        legFails++;
                        fetchBlocks(bot, level);
                    }
                    return Result.RUNNING;
                }
                // Obsidian carried: build the frame; else, with the buckets, cast one at a lava pool.
                boolean buckets = BotInventory.slotOf(bot.body(), Items.WATER_BUCKET) >= 0 && BotInventory.slotOf(bot.body(), Items.BUCKET) >= 0;
                build = ObtainPlanner.countAny(bot.body(), Set.of(Items.OBSIDIAN)) < 10 && buckets && level.dimension() == Level.OVERWORLD
                        ? new LavaPortalTask(level, null) : new PortalTask(level);
                return Result.RUNNING;
            }
        }
        lastFeet = bot.body().blockPosition();
        if (bot.body().blockPosition().equals(portal)) {
            bot.navigator().stop();
            bot.body().stopInputs();
            // Into the middle of it: the portal is only a thin sheet down the middle of the block,
            // and the game takes along only who touches it (after a moment).
            double dx = portal.getX() + 0.5 - bot.body().getX(), dz = portal.getZ() + 0.5 - bot.body().getZ();
            if (dx * dx + dz * dz > 0.01) {
                bot.body().setYRot((float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f);
                bot.body().forward = 0.3f;
            }
            return Result.RUNNING;
        }
        if (!walking) {
            bot.navigator().goTo(portal);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        if (s == BotNavigator.Status.FAILED) {
            fetchBlocks(bot, level);
            // (The portal in sight but no way there, over and over: the known way, through the rock.)
            if (++walkFails > 8) return failed(bot);
        }
        return Result.RUNNING;
    }

    private BotTask fetch;
    private int fetchFails, walkFails;

    /** No way found and few blocks left: very likely the way needs blocks (a pillar, a bridge). */
    private void fetchBlocks(Bot bot, ServerLevel level) {
        int have = net.kasax.challengecraft.bot.BotActions.buildingBlocks(bot.body());
        if (have >= 24 || fetchFails > 2) return;
        var kinds = net.kasax.challengecraft.bot.BotActions.buildingBlocks(level);
        bot.say("few blocks (" + have + ") for the way to the portal: a stack of " + kinds.iterator().next().toString().replace("minecraft:", "") + " and the like first");
        fetch = new ObtainTask(kinds, ObtainPlanner.countAny(bot.body(), kinds) + 32);
    }

    /** Remembered for a while, so the Overworld tiles are not tried (and failed) again at once. */
    private Result failed(Bot bot) {
        bot.portalBackFailedAt = bot.body().level().getGameTime();
        return Result.FAILED;
    }

    @Override
    public String status() {
 return describe() + " [portal " + (portal == null ? "-" : portal.toShortString()) + " from " + (lastFeet == null ? "-" : lastFeet.toShortString()) + ", walking " + walking + ", legs failed " + legFails
                + (build != null ? ", " + build.status() : "") + "]";
    }

    @Override
    public String describe() {
        return "go back through the portal";
    }
}
