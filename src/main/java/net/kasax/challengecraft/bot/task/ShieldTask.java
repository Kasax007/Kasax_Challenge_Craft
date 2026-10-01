package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/**
 * A hit taken on the shield: the shield into the off hand, up to a monster, and the shield raised
 * towards it while it attacks, until the game counts damage blocked.
 */
public final class ShieldTask implements BotTask {
    private final Explorer explorer = new Explorer(3600);
    private int ticks, start = -1;
    private boolean walking;

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        int blocked = body.getStats().getValue(Stats.CUSTOM.get(Stats.DAMAGE_BLOCKED_BY_SHIELD));
        if (start < 0) start = blocked;
        if (blocked > start) {
            body.stopUsingItem();
            return Result.DONE;
        }
        if (++ticks > 4800) return Result.FAILED;
        // The shield in the off hand.
        if (!body.getOffhandItem().is(Items.SHIELD)) {
            int slot = BotInventory.slotOf(body, Items.SHIELD);
            if (slot < 0) return Result.FAILED;
            ItemStack shield = body.getInventory().getItem(slot);
            ItemStack old = body.getOffhandItem().copy();
            body.setItemSlot(EquipmentSlot.OFFHAND, shield.copy());
            body.getInventory().setItem(slot, old);
            return Result.RUNNING;
        }
        Monster m = level.getEntitiesOfClass(Monster.class, new AABB(body.blockPosition()).inflate(24), e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.monster.Creeper))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(body))).orElse(null);
        if (m == null) {
            body.stopUsingItem();
            return explorer.tick(bot);
        }
        explorer.pause(bot);
        body.lookAt(m.getEyePosition());
        if (m.distanceTo(body) > 4) {
            body.stopUsingItem();
            if (!walking || ticks % 30 == 0) {
                bot.navigator().goNear(m.blockPosition(), 3);
                walking = true;
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED) walking = false;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        walking = false;
        body.stopInputs();
        // Up with it (held), facing the monster.
        if (!body.isUsingItem()) body.gameMode.useItem(body, level, body.getOffhandItem(), InteractionHand.OFF_HAND);
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "block a hit with a shield";
    }
}
