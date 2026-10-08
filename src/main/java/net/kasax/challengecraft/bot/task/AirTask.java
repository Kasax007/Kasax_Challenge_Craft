package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

/**
 * Out of air under a roof of water (a tunnel dug into a flooded cave, a sea cave): to the nearest
 * spot where the head is in the air, before anything else. Swimming up is not enough there.
 * <p>
 * As a player goes about it: open water straight up is swum up at once; else the nearest air the
 * way finder knows of; not getting there, a thin roof with air over it is dug through, or else the
 * way it came is swum back (the staircase it dug down into the flood); only last of all is a thick
 * roof dug at.
 */
public final class AirTask implements BotTask {
    private enum Way { SEARCH, BACK, SWIM, DIG }

    private Way way = Way.SEARCH;
    private boolean started, backTried;
    /** Straight up did not get it any higher (a current, a waterfall): not again. */
    private boolean upBlocked;
    private int upTicks;
    private double upFrom;
    /** Ticks with the head out, catching breath. */
    private int breathing;
    private int ticks, fails, stuck;
    private net.minecraft.world.phys.Vec3 last;
    /** The spot on its trail it swims back to: the last one with air. */
    private BlockPos back;

    /** The head would be out of the water standing (or swimming) here. */
    public static boolean breathable(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        return level.getFluidState(head).isEmpty() && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        // Head out: there until the lungs are nearly full again - a breath and straight back
        // under (the way on leads through the water) and the next breath is shorter, until
        // there is none. Meanwhile still, afloat (head kept up) or standing.
        if (!body.isEyeInFluid(FluidTags.WATER)) {
            if (body.getAirSupply() >= body.getMaxAirSupply() * 9 / 10 || ++breathing > 200) return Result.DONE;
            bot.navigator().stop();
            body.stopInputs();
            if (body.isInWater()) body.jump = true;
            return Result.RUNNING;
        }
        if (breathing > 0 && body.getAirSupply() >= body.getMaxAirSupply() / 2) {
            // (Bobbing under for a moment while waiting at the surface: up again, no new search.)
            body.stopInputs();
            body.jump = true;
            return Result.RUNNING;
        }
        if (++ticks > (way == Way.DIG ? 1600 : 600)) return Result.FAILED;
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = body.blockPosition();
        // Water straight up to the air (the commonest case: a dive for something on the bottom):
        // swum up, nothing to work out - as long as that gets it higher. (Not rising - a current,
        // water falling down a shaft - it drowned so twice on seed 77, swimming "up" in place.)
        if (!upBlocked && openAbove(level, feet, 24)) {
            if (upTicks++ == 0 || body.getY() > upFrom + 0.5) {
                upFrom = body.getY();
                if (upTicks > 1) upTicks = 1;
            }
            if (upTicks > 30) {
                upBlocked = true;
                bot.say("up for air: not getting higher here, another way");
            } else {
                bot.navigator().stop();
                body.stopInputs();
                body.jump = true;
                return Result.RUNNING;
            }
        }
        if (ticks % 20 == 1) {
            // (Measured over a second: swimming is slow, but not this slow.)
            stuck = last != null && body.position().distanceToSqr(last) < 0.25 ? stuck + 1 : 0;
            last = body.position();
        }
        // The way it is going about it gets it nowhere (two seconds without moving, or no way
        // found twice): the next one. (Once in a while, not for good after one slow second -
        // digging up through the rock of a flooded cave drowned it on seed 66, the staircase it
        // had come down by right behind it.)
        if ((stuck >= 2 || fails > 1) && way != Way.DIG) {
            stuck = 0;
            fails = 0;
            started = false;
            bot.navigator().stop();
            Way was = way;
            way = next(bot, level, feet);
            if (way != was && way != Way.BACK) bot.say("up for air: " + way.name().toLowerCase() + " instead");
        }
        switch (way) {
            case DIG -> {
                BlockPos head = BlockPos.containing(body.getX(), body.getEyeY(), body.getZ());
                for (BlockPos q : new BlockPos[]{head.above(), head.above(2)}) {
                    if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getFluidState(q).isEmpty()) {
                        // Standing on the bottom while at it: afloat, digging is five times slower.
                        bot.navigator().stop();
                        body.stopInputs();
                        bot.sinkToDig = true;
                        bot.actions().breakTick(q);
                        return Result.RUNNING;
                    }
                }
                // Through: up.
                body.stopInputs();
                body.jump = true;
                return Result.RUNNING;
            }
            case SWIM -> {
                bot.navigator().stop();
                body.stopInputs();
                body.jump = true;
                BlockPos open = openColumn(level, feet);
                if (open != null && (open.getX() != feet.getX() || open.getZ() != feet.getZ())) {
                    body.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(open.atY(feet.getY())));
                    body.forward = 1f;
                }
                return Result.RUNNING;
            }
            case BACK -> {
                if (!started) {
                    bot.navigator().goTo(back);
                    bot.navigator().forAir = true;
                    started = true;
                }
            }
            default -> {
                if (!started) {
                    bot.navigator().setGoal(p -> breathable(level, p), feet.above(8));
                    bot.navigator().forAir = true;
                    started = true;
                }
            }
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s == BotNavigator.Status.FAILED) {
            started = false;
            fails++;
        } else if (s == BotNavigator.Status.ARRIVED) {
            started = false;
        }
        return Result.RUNNING;
    }

    /**
     * What to try next: a roof of a block or two with air over it, dug through; the way it came,
     * back to the last spot of its trail with air; the nearest water open to the sky, swum to;
     * last, whatever is over the head, dug at.
     */
    private Way next(Bot bot, ServerLevel level, BlockPos feet) {
        if (thinRoof(level, BlockPos.containing(bot.body().getX(), bot.body().getEyeY(), bot.body().getZ()))) return Way.DIG;
        if (!backTried) {
            backTried = true;
            back = trailBack(bot, level, feet);
            if (back != null) {
                bot.say("no air above: back the way I came, to " + back.toShortString());
                return Way.BACK;
            }
        }
        if (way != Way.SWIM && openColumn(level, feet) != null) return Way.SWIM;
        return Way.DIG;
    }

    /** One or two solid blocks over the head, and air (not more water) right over them. */
    private static boolean thinRoof(ServerLevel level, BlockPos head) {
        for (int up = 1; up <= 3; up++) {
            BlockPos q = head.above(up);
            boolean solid = !level.getBlockState(q).getCollisionShape(level, q).isEmpty();
            if (!solid) return up > 1 && level.getFluidState(q).isEmpty();
        }
        return false;
    }

    /** The last spot on the way it came (its trail, newest first) where the head is in the air, not too far back. */
    private static BlockPos trailBack(Bot bot, ServerLevel level, BlockPos feet) {
        var trail = bot.trail;
        for (int i = trail.size() - 1, n = 0; i >= 0 && n < 12; i--, n++) {
            BlockPos p = trail.get(i);
            if (p.distSqr(feet) > 40 * 40) break;
            for (int up = 0; up <= 1; up++) if (breathable(level, p.above(up))) return p.above(up);
        }
        return null;
    }

    /** Nothing but still water from the feet up to the air, at most {@code max} blocks (water falling down a shaft is no way up). */
    private static boolean openAbove(ServerLevel level, BlockPos feet, int max) {
        for (int up = 1; up <= max; up++) {
            BlockPos q = feet.above(up);
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return false;
            var fluid = level.getFluidState(q);
            if (fluid.isEmpty()) return true;
            if (!fluid.isSource()) return false;
        }
        return false;
    }

    /** The nearest column (within six) where nothing but water stands between the feet's level and the air above. */
    private static BlockPos openColumn(ServerLevel level, BlockPos feet) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = dx * dx + dz * dz;
                if (d >= bestD) continue;
                BlockPos p = feet.offset(dx, 0, dz);
                for (int up = 0; up <= 16; up++) {
                    BlockPos q = p.above(up);
                    if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) break;
                    if (level.getFluidState(q).isEmpty()) {
                        best = p;
                        bestD = d;
                        break;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public String describe() {
        return "up for air";
    }
}
