package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

/** Uses an item into the air (throws the ender pearl, the snowball, the egg). */
public final class UseItemTask implements BotTask {
    private final Item item;
    private final float pitch;

    public UseItemTask(Item item, float pitch) {
        this.item = item;
        this.pitch = pitch;
    }

    @Override
    public Result tick(Bot bot) {
        int slot = BotInventory.slotOf(bot.body(), item);
        if (slot < 0) return Result.FAILED;
        bot.tools().select(slot);
        bot.body().setXRot(pitch);
        bot.body().gameMode.useItem(bot.body(), bot.body().level(), bot.body().getMainHandItem(), InteractionHand.MAIN_HAND);
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "use " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
    }
}
