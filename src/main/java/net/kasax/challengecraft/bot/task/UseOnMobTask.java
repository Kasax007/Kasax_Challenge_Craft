package net.kasax.challengecraft.bot.task;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Uses an item on a mob, the way a player right-clicks it: milk a cow with a bucket, shear a
 * sheep, ... Goes to the nearest suitable one (or explores), then clicks it with the item in hand.
 */
public final class UseOnMobTask implements BotTask {
    private final EntityType<?> type;
    private final Item tool;
    private final Predicate<LivingEntity> suitable;
    private final Explorer explorer = new Explorer(3600);
    private final Set<UUID> failed = new HashSet<>();
    private LivingEntity target;
    private boolean walking;
    private int repath, chase;

    public UseOnMobTask(EntityType<?> type, Item tool, Predicate<LivingEntity> suitable) {
        this.type = type;
        this.tool = tool;
        this.suitable = suitable;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        if (BotInventory.slotOf(body, tool) < 0) return Result.FAILED;
        if (target == null || !target.isAlive() || !suitable.test(target)) {
            target = body.level().getEntitiesOfClass(LivingEntity.class, new AABB(body.blockPosition()).inflate(48),
                            e -> e.getType() == type && e.isAlive() && suitable.test(e) && !failed.contains(e.getUUID()))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(body))).orElse(null);
            walking = false;
            chase = 0;
            if (target == null) return explorer.tick(bot);
            explorer.pause(bot);
        }
        if (++chase > 1200) {
            failed.add(target.getUUID());
            target = null;
            return Result.RUNNING;
        }
        double dist = body.distanceTo(target);
        if (dist > 2.8) {
            if (!walking || --repath <= 0) {
                bot.navigator().goNear(target.blockPosition(), 2.2);
                walking = true;
                repath = 30;
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED) {
                walking = false;
                if (dist < 6) {
                    body.lookAt(target.getEyePosition());
                    body.forward = 1f;
                } else {
                    failed.add(target.getUUID());
                    target = null;
                }
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        bot.tools().select(BotInventory.slotOf(body, tool));
        body.lookAt(target.getEyePosition());
        // What the network handler does for a player's click: the interaction event, then the use.
        var hit = new EntityHitResult(target, target.getEyePosition());
        InteractionResult r = UseEntityCallback.EVENT.invoker().interact(body, body.level(), InteractionHand.MAIN_HAND, target, hit);
        if (r == InteractionResult.PASS) r = body.interactOn(target, InteractionHand.MAIN_HAND, target.getEyePosition());
        if (r.consumesAction()) {
            body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            return Result.DONE;
        }
        failed.add(target.getUUID());
        target = null;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "use " + BuiltInRegistries.ITEM.getKey(tool).getPath() + " on a " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }
}
