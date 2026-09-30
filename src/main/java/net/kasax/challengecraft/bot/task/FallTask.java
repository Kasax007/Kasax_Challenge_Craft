package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * A deliberate fall: pillars up {@code height} blocks where it stands and steps off the top into
 * the open (for "take fall damage", "fall 20 blocks and survive": the water bucket reflex saves
 * the long fall when it carries one).
 */
public final class FallTask implements BotTask {
    private final int height;
    private final boolean hurt;
    private int startY = Integer.MIN_VALUE, ticks;
    private BlockPos jumpedFrom;
    private boolean stepping, airborne;

    /** {@code hurt}: land without the water bucket (the damage is the point). */
    public FallTask(int height, boolean hurt) {
        this.height = height;
        this.hurt = hurt;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 2400) return Result.FAILED;
        BlockPos feet = body.blockPosition();
        if (startY == Integer.MIN_VALUE) startY = feet.getY();
        if (airborne) {
            if (body.onGround() || body.isInWater()) {
                body.stopInputs();
                bot.waterLandingAllowed = true;
                return Result.DONE;
            }
            return Result.RUNNING;
        }
        if (!stepping) {
            if (feet.getY() >= startY + height && body.onGround()) {
                stepping = true;
                return Result.RUNNING;
            }
            // Pillar: jump, set a block where the feet were.
            if (!clear(level, feet.above(2))) {
                bot.actions().breakTick(feet.above(2));
                return Result.RUNNING;
            }
            if (jumpedFrom == null) {
                if (!bot.actions().hasThrowaway()) return Result.FAILED;
                if (body.onGround()) {
                    jumpedFrom = feet;
                    body.jump = true;
                }
                return Result.RUNNING;
            }
            body.jump = false;
            if (body.getY() > jumpedFrom.getY() + 1.05) {
                bot.actions().placeThrowaway(jumpedFrom);
                jumpedFrom = null;
            } else if (body.onGround() && body.getY() < jumpedFrom.getY() + 0.5 && ticks % 20 == 0) {
                jumpedFrom = null;
            }
            return Result.RUNNING;
        }
        // Off the top: towards a side with nothing under it.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = feet.relative(d);
            if (clear(level, n) && clear(level, n.above()) && clear(level, n.below())) {
                body.setYRot(d.toYRot());
                body.forward = 0.6f;
                if (!body.onGround()) {
                    body.stopInputs();
                    airborne = true;
                    if (hurt) bot.waterLandingAllowed = false;
                }
                return Result.RUNNING;
            }
        }
        return Result.FAILED; // walled in up here
    }

    private static boolean clear(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
    }

    @Override
    public String describe() {
        return "pillar up " + height + " and jump off";
    }
}
