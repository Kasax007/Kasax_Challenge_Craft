package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;

/**
 * A bow drawn on it: standing still behind the raised shield, facing the shooter, until the arrow
 * is off (it ends in the shield) — as a player does instead of taking it in the back.
 */
public final class ShieldUpTask implements BotTask {
    private final Mob shooter;
    private int ticks;
    private boolean drawn = true;

    public ShieldUpTask(Mob shooter) {
        this.shooter = shooter;
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        bot.navigator().stop();
        body.stopInputs();
        // Let go: the arrow is off (a moment more for it to arrive), or nothing to block.
        if (shooter.isUsingItem()) drawn = true;
        else if (drawn) {
            drawn = false;
            ticks = Math.max(ticks, 30);
        }
        if (!shooter.isAlive() || ++ticks > 40 || !body.getOffhandItem().is(Items.SHIELD)) {
            if (body.isUsingItem()) body.releaseUsingItem();
            return Result.DONE;
        }
        body.lookAt(shooter.getEyePosition());
        if (!body.isUsingItem()) body.gameMode.useItem(body, body.level(), body.getOffhandItem(), InteractionHand.OFF_HAND);
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "shield up";
    }
}
