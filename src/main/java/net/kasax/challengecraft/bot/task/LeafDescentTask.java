package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;

/**
 * Down out of a tree's crown (the world spawn can be up there): the leaves under the feet broken
 * one after the other - a block's fall each, not the jump off the top the fall guard forbids.
 * Only where the fall after it is a short one: a crown hanging out over a ravine or a cliff has
 * nothing under its outer leaves (a self-play game: the leaf broken, forty-one blocks down). There
 * first along the crown to a spot with the trunk or more leaves under it.
 */
public final class LeafDescentTask implements BotTask {
    private int ticks, goes;
    private BlockPos over;
    /** Spots along the crown walked for and not reached: another one next. */
    private final java.util.Set<BlockPos> tried = new java.util.HashSet<>();
    /** No way along the crown: straight down through it after all (a fall of a few hearts at most). */
    private boolean takeTheFall;
    /**
     * When a bot may try again after finding no safe way down (the level's game time: a new body
     * after a death counts its ticks from naught, and a mark in the old body's count kept him out
     * of every tree for as long as the old one had lived).
     */
    private static final java.util.Map<Bot, Long> NO_WAY_UNTIL = new java.util.WeakHashMap<>();

    /** Whether it stands on leaves with the ground more than a safe fall below. */
    public static boolean upATree(Bot bot) {
        var level = bot.body().level();
        BlockPos feet = bot.body().blockPosition();
        if (level.getGameTime() < NO_WAY_UNTIL.getOrDefault(bot, -1L)) return false;
        if (!level.getBlockState(feet.below()).is(BlockTags.LEAVES)) return false;
        var ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet);
        return ground.getY() < feet.getY() - 3;
    }

    /** Blocks of air under {@code p} before something that holds him (leaves too) or water; 99: none in forty. */
    private static int airUnder(net.minecraft.world.level.Level level, BlockPos p) {
        for (int i = 1; i <= 40; i++) {
            BlockPos q = p.below(i);
            if (!level.getFluidState(q).isEmpty() || !level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return i - 1;
        }
        return 99;
    }

    private static boolean crown(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(net.minecraft.world.level.block.Blocks.VINE);
    }

    /** Whether there is something to stand on at {@code p} (not a vine: it holds nobody). */
    private static boolean holds(net.minecraft.world.level.Level level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    @Override
    public Result tick(Bot bot) {
        if (++ticks > 600) return giveUp(bot);
        var body = bot.body();
        var level = body.level();
        body.stopInputs();
        if (!body.onGround()) return Result.RUNNING;
        BlockPos feet = body.blockPosition(), below = feet.below();
        var state = level.getBlockState(below);
        // (Leaves, and the vines or the odd log in a crown: whatever, it is only a block's fall -
        // if it is: with nothing under that block for more than two, the fall would hurt.)
        if (!crown(state)) return Result.DONE;
        if (airUnder(level, below) <= 2 || takeTheFall) {
            over = null;
            // (The one fall taken, not every one after it: on the next layer it looks again.)
            if (bot.actions().breakTick(below)) takeTheFall = false;
            return Result.RUNNING;
        }
        // Along the crown to where it does go down safely: the nearest spot on it with something
        // close under the block it would stand on.
        // (The walk there back without getting there: that spot is out of reach, the next one.)
        if (over != null && !feet.equals(over)) {
            tried.add(over);
            over = null;
        }
        if (over == null || feet.equals(over)) {
            over = null;
            double best = Double.MAX_VALUE;
            // (A crown reaches some six out from its trunk; a big one, more.)
            // (On leaves or a log, not over a vine: that holds nobody, and no way leads onto it -
            // a jungle crown in a self-play game, the spot a block off over a vine walked for in
            // vain nine hundred times.)
            for (BlockPos p : BlockPos.betweenClosed(feet.offset(-6, -1, -6), feet.offset(6, 1, 6))) {
                if (!crown(level.getBlockState(p.below())) || !holds(level, p.below()) || !level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                        || !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty() || airUnder(level, p.below()) > 2
                        || tried.contains(p)) continue;
                double d = p.distSqr(feet);
                if (d < best) {
                    best = d;
                    over = p.immutable();
                }
            }
            if (over == null) return noWayAlong(bot, below);
        }
        // (A walk there that keeps failing: no way along this crown.)
        if (++goes > 4) return noWayAlong(bot, below);
        bot.interject(new GoToTask(over, 0));
        return Result.RUNNING;
    }

    /**
     * No way along the crown to a short drop: straight down through the leaves after all where
     * that fall costs a few hearts at most (a player jumps off rather than stay up a tree), else
     * given up for now.
     */
    private Result noWayAlong(Bot bot, BlockPos below) {
        var level = bot.body().level();
        int air = airUnder(level, below);
        if (air < 40) {
            BlockPos landing = below.below(air + 1);
            boolean water = level.getFluidState(landing).is(net.minecraft.tags.FluidTags.WATER);
            // (A fall of n blocks takes n - 3 hearts' halves; into water, none.)
            int damage = water ? 0 : Math.max(0, air + 1 - 3);
            if (damage <= 6 && bot.body().getHealth() - damage >= 8) {
                bot.say("no short way down along this crown: through the leaves, a fall of " + (air + 1));
                takeTheFall = true;
                over = null;
                return Result.RUNNING;
            }
        }
        return giveUp(bot);
    }

    private Result giveUp(Bot bot) {
        bot.say("no safe way down out of this tree here");
        NO_WAY_UNTIL.put(bot, bot.body().level().getGameTime() + 600);
        return Result.FAILED;
    }

    @Override
    public String describe() {
        return "down out of the tree";
    }
}
