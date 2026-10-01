package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Freezes in powder snow: leather boots off (they let one walk on it), up to a patch it has seen,
 * a step into it, and there until the cold bites (some seven seconds); then out again.
 */
public final class PowderSnowTask implements BotTask {
    private BlockPos snow;
    private boolean walking;
    private int ticks, inside;

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        var level = (net.minecraft.server.level.ServerLevel) body.level();
        if (++ticks > 3600) return Result.FAILED;
        // Frozen enough: hurt by the cold once.
        if (inside > 0 && body.getLastDamageSource() != null && body.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.FREEZE)) {
            body.stopInputs();
            body.jump = true;
            return ++inside > 400 ? Result.DONE : body.isInPowderSnow ? Result.RUNNING : Result.DONE;
        }
        if (body.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS)) {
            ItemStack boots = body.getItemBySlot(EquipmentSlot.FEET).copy();
            body.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            CraftTask.give(body, boots);
        }
        if (body.isInPowderSnow || level.getBlockState(body.blockPosition()).is(Blocks.POWDER_SNOW)) {
            inside++;
            body.stopInputs();
            bot.navigator().stop();
            return Result.RUNNING;
        }
        if (snow == null || !level.getBlockState(snow).is(Blocks.POWDER_SNOW)) {
            snow = bot.memory().nearest(level, body.blockPosition(), st -> st.is(Blocks.POWDER_SNOW), java.util.Set.of());
            walking = false;
            if (snow == null) return Result.FAILED;
        }
        double d = Math.sqrt(snow.distSqr(body.blockPosition()));
        if (d > 2.2) {
            if (!walking) {
                bot.navigator().goNear(snow, 1.8);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED) return Result.FAILED;
            if (s == BotNavigator.Status.ARRIVED) walking = false;
            return Result.RUNNING;
        }
        // The one step the path search refuses: into it.
        bot.navigator().stop();
        body.lookAt(Vec3.atBottomCenterOf(snow));
        body.forward = 0.6f;
        body.jump = body.horizontalCollision;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "freeze in powder snow";
    }
}
