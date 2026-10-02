package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * On fire (out of lava, a fire block walked through, a burning arrow): put out as a player does -
 * a bucket of water poured at its feet and scooped back up, or, without one, into the nearest
 * water close by. (Not in the Nether: water boils away there.)
 */
public final class ExtinguishTask implements BotTask {
    private int ticks;
    private BlockPos poured, water;
    private boolean walking;

    /** Whether there is a way to put it out here: a water bucket, or water a few steps off. */
    public static boolean possible(Bot bot) {
        var level = bot.body().level();
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) return false;
        return BotInventory.slotOf(bot.body(), Items.WATER_BUCKET) >= 0 || nearWater(bot) != null;
    }

    private static BlockPos nearWater(Bot bot) {
        return BotWorld.nearest((ServerLevel) bot.body().level(), bot.body().blockPosition(), 6, 3,
                s -> s.getFluidState().is(FluidTags.WATER), false, java.util.Set.of());
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 120) return Result.FAILED;
        // Out (and the water it poured back in the bucket): done.
        if (!body.isOnFire() || body.isInWater()) {
            if (poured != null && level.getFluidState(poured).is(FluidTags.WATER) && level.getFluidState(poured).isSource()
                    && BotInventory.slotOf(body, Items.BUCKET) >= 0) {
                use(bot, level, Items.BUCKET, poured);
                poured = null;
                return Result.RUNNING;
            }
            if (walking) bot.navigator().stop();
            return Result.DONE;
        }
        if (poured == null && BotInventory.slotOf(body, Items.WATER_BUCKET) >= 0 && body.onGround()) {
            BlockPos feet = body.blockPosition();
            if (level.getBlockState(feet).canBeReplaced() && level.getFluidState(feet).isEmpty()) {
                bot.navigator().stop();
                body.stopInputs();
                use(bot, level, Items.WATER_BUCKET, feet.below());
                if (level.getFluidState(feet).is(FluidTags.WATER)) poured = feet;
                return Result.RUNNING;
            }
        }
        if (water == null) water = nearWater(bot);
        if (water == null) return Result.FAILED;
        if (!walking) {
            bot.navigator().goTo(water);
            walking = true;
        }
        return GoToTask.walk(bot.navigator().tick());
    }

    /** The bucket in hand, used on the top of {@code on} (pouring onto it, or scooping from it). */
    private static void use(Bot bot, ServerLevel level, net.minecraft.world.item.Item bucket, BlockPos on) {
        var body = bot.body();
        int slot = BotInventory.slotOf(body, bucket);
        if (slot < 0) return;
        bot.tools().select(slot);
        Vec3 hit = Vec3.atCenterOf(on).add(0, 0.5, 0);
        body.lookAt(hit);
        // (A bucket works by where it is looked at, as a player's does.)
        body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
        body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    @Override
    public String describe() {
        return "put the fire out";
    }
}
