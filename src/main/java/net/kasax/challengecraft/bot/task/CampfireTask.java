package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lights a campfire: one put down burns already, so it is put out first with a shovel, then lit
 * again with flint and steel (that is what counts as lighting one).
 */
public final class CampfireTask implements BotTask {
    private BlockPos fire;
    private int ticks, tries;

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        var level = body.level();
        if (++ticks > 600) return Result.FAILED;
        if (fire == null || !(level.getBlockState(fire).getBlock() instanceof CampfireBlock)) {
            fire = bot.actions().placeNearby(Items.CAMPFIRE);
            if (fire == null) {
                if (++tries > 20) return Result.FAILED;
                bot.interject(new GoToTask(body.blockPosition().offset(body.getRandom().nextInt(5) - 2, 0, body.getRandom().nextInt(5) - 2), 1));
            }
            return Result.RUNNING;
        }
        boolean lit = level.getBlockState(fire).getValue(CampfireBlock.LIT);
        if (lit && ticks > 5 && doused) return Result.DONE; // put out and lit again
        int slot = -1;
        if (lit) {
            for (int i = 0; i < body.getInventory().getNonEquipmentItems().size(); i++) {
                if (body.getInventory().getNonEquipmentItems().get(i).is(ItemTags.SHOVELS)) slot = i;
            }
            if (slot < 0) return Result.FAILED;
        } else {
            slot = BotInventory.slotOf(body, Items.FLINT_AND_STEEL);
            if (slot < 0) slot = BotInventory.slotOf(body, Items.FIRE_CHARGE);
            if (slot < 0) return Result.FAILED;
        }
        bot.tools().select(slot);
        Vec3 hit = Vec3.atCenterOf(fire).add(0, 0.2, 0);
        body.lookAt(hit);
        body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, fire, false));
        if (lit) doused = true;
        return Result.RUNNING;
    }

    private boolean doused;

    @Override
    public String describe() {
        return "light a campfire";
    }
}
