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
    private BlockPos lastKnown;

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (from == null) from = level.dimension();
        if (level.dimension() != from) return Result.DONE;
        if (++ticks > 4800) return Result.FAILED;
        if (build != null) return build.tick(bot);
        if (portal == null || !level.getBlockState(portal).is(Blocks.NETHER_PORTAL)) {
            portal = BotWorld.nearest(level, bot.body().blockPosition(), 64, 24, s -> s.is(Blocks.NETHER_PORTAL), false, Set.of());
            walking = false;
            if (portal == null) {
                // The one it came through, however far away: it remembers where that was.
                BlockPos known = bot.senses().knownPortal(bot.body().blockPosition());
                if (known != null && !known.equals(lastKnown)) {
                    lastKnown = known;
                    bot.interject(new GoToTask(known, 3));
                    return Result.RUNNING;
                }
                // Obsidian carried: build the frame; else, with the buckets, cast one at a lava pool.
                boolean buckets = BotInventory.slotOf(bot.body(), Items.WATER_BUCKET) >= 0 && BotInventory.slotOf(bot.body(), Items.BUCKET) >= 0;
                build = ObtainPlanner.countAny(bot.body(), Set.of(Items.OBSIDIAN)) < 10 && buckets && level.dimension() == Level.OVERWORLD
                        ? new CastPortalTask(level) : new PortalTask(level);
                return Result.RUNNING;
            }
        }
        if (bot.body().blockPosition().equals(portal)) {
            bot.navigator().stop();
            bot.body().stopInputs();
            return Result.RUNNING; // standing in it: the game takes it through after a moment
        }
        if (!walking) {
            bot.navigator().goTo(portal);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "go back through the portal";
    }
}
