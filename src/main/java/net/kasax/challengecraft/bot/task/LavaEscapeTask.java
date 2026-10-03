package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Fallen into lava (or standing on magma): out onto the nearest firm, cool ground at once, before
 * anything else - the way searched through the lava itself, jumping all the while. Should the
 * search find nothing, straight for the nearest ground seen, jumping, as a player would.
 */
public final class LavaEscapeTask implements BotTask {
    private int ticks, safe;
    private boolean walking;
    private BlockPos firm;

    /** Whether the bot is where lava or magma burns it. */
    public static boolean burning(Bot bot) {
        var body = bot.body();
        if (body.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) return false;
        return body.isInLava() || body.onGround() && body.level().getBlockState(body.getOnPos()).is(Blocks.MAGMA_BLOCK)
                // (Crouched on the way across, it does not burn: on along the way then. Standing about on it: off.)
                && (!body.isSteppingCarefully() || bot.navigator().status() != net.kasax.challengecraft.bot.BotNavigator.Status.MOVING)
                // (Standing in fire, soul fire: out of it, the same way.)
                || body.level().getBlockState(body.blockPosition()).is(net.minecraft.tags.BlockTags.FIRE);
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        Level level = body.level();
        if (!burning(bot)) {
            // (A few ticks on firm ground first: a step out may still slide back in.)
            if (++safe > 4) {
                if (walking) bot.navigator().stop();
                return Result.DONE;
            }
        } else safe = 0;
        if (++ticks > 400) {
            if (walking) bot.navigator().stop();
            return Result.FAILED;
        }
        if (ticks == 1) bot.say("burning (" + (body.isInLava() ? "in lava" : body.onGround() && level.getBlockState(body.getOnPos()).is(Blocks.MAGMA_BLOCK)
                ? "on magma" : "in fire") + ") at " + body.blockPosition().toShortString() + ": out");
        if (firm == null) firm = nearestFirm(level, body.blockPosition());
        // Firm ground a step or two off: straight for it, jumping (a path through the lava is
        // walked no faster, and every tick in there burns).
        if (firm != null && body.isInLava() && Vec3.atBottomCenterOf(firm).distanceToSqr(body.position()) < 3.5 * 3.5) {
            if (walking) bot.navigator().stop();
            walking = false;
            body.lookAt(Vec3.atCenterOf(firm.above()));
            body.setXRot(0);
            body.forward = 1f;
            body.jump = true;
            return Result.RUNNING;
        }
        if (!walking) {
            BlockPos from = body.blockPosition();
            bot.navigator().setGoal(p -> cool(level, p), firm != null ? firm : from);
            walking = true;
        }
        var st = bot.navigator().tick();
        if (st == BotNavigator.Status.FAILED || ticks > 60 && st != BotNavigator.Status.MOVING) {
            // No way found: straight for the nearest ground, jumping.
            if (firm == null) firm = nearestFirm(level, body.blockPosition());
            if (firm != null) {
                body.lookAt(Vec3.atCenterOf(firm.above()));
                body.setXRot(0);
                body.forward = 1f;
            }
            body.jump = true;
        }
        if (body.isInLava()) body.jump = true;
        return Result.RUNNING;
    }

    /** A spot to stand on clear of lava and magma. */
    static boolean cool(Level level, BlockPos p) {
        var at = level.getBlockState(p);
        var head = level.getBlockState(p.above());
        var below = level.getBlockState(p.below());
        if (!at.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) return false;
        if (!at.getCollisionShape(level, p).isEmpty() || !head.getCollisionShape(level, p.above()).isEmpty()) return false;
        if (at.is(net.minecraft.tags.BlockTags.FIRE)) return false;
        return !below.getCollisionShape(level, p.below()).isEmpty() && !below.is(Blocks.MAGMA_BLOCK)
                && below.getFluidState().isEmpty();
    }

    private static BlockPos nearestFirm(Level level, BlockPos feet) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(feet.offset(-8, -2, -8), feet.offset(8, 3, 8))) {
            if (q.distSqr(feet) >= bestD || !cool(level, q)) continue;
            bestD = q.distSqr(feet);
            best = q.immutable();
        }
        return best;
    }

    @Override
    public String describe() {
        return "out of the lava";
    }
}
