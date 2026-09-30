package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Item;

/** Feeds one animal until it is tame (a wolf takes a few bones, a cat a few fish). */
public final class TameTask implements BotTask {
    private final EntityType<?> type;
    private final Item food;
    private BotTask use;
    private int uses;

    public TameTask(EntityType<?> type, Item food) {
        this.type = type;
        this.food = food;
    }

    @Override
    public Result tick(Bot bot) {
        if (use == null) {
            if (BotInventory.slotOf(bot.body(), food) < 0 || ++uses > 20) return Result.FAILED;
            use = new UseOnMobTask(type, food, e -> e instanceof TamableAnimal t && !t.isTame() && !t.isBaby());
        }
        Result r = use.tick(bot);
        if (r == Result.RUNNING) return r;
        use = null;
        if (r == Result.FAILED) return Result.FAILED;
        // Tame now? (One of them close by owned by us.)
        boolean tamed = !bot.body().level().getEntitiesOfClass(TamableAnimal.class, bot.body().getBoundingBox().inflate(8),
                t -> t.getType() == type && t.isOwnedBy(bot.body())).isEmpty();
        return tamed ? Result.DONE : Result.RUNNING;
    }

    @Override
    public String describe() {
        return "tame a " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }
}
