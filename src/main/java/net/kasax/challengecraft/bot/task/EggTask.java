package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * An egg: chickens lay one every few minutes. Off to the nearest flock (or where one was last
 * seen), and wait among them picking up what they lay; eggs already lying about first.
 */
public final class EggTask implements BotTask {
    private static final int MAX_TICKS = 9000;
    private final Explorer explorer = new Explorer(3600);
    private int ticks;
    private boolean walking;
    private ItemEntity egg;

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BotNavigator nav = bot.navigator();
        if (ObtainPlanner.countAny(bot.body(), Set.of(Items.EGG)) > 0) return Result.DONE;
        if (++ticks > MAX_TICKS) return Result.FAILED;
        BlockPos at = bot.body().blockPosition();
        // An egg lying about: fetch it.
        if (egg == null || !egg.isAlive()) {
            egg = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(24), e -> e.isAlive() && e.getItem().is(Items.EGG))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(bot.body()))).orElse(null);
            if (egg != null) {
                nav.goPickUp(egg);
                walking = true;
            }
        }
        if (egg != null) {
            if (nav.tick() != BotNavigator.Status.MOVING) egg = null;
            return Result.RUNNING;
        }
        // Among the chickens.
        List<Chicken> flock = level.getEntitiesOfClass(Chicken.class, new AABB(at).inflate(32), c -> c.isAlive() && !c.isBaby());
        if (flock.isEmpty()) {
            BlockPos seen = bot.memory().lastSeen(level, EntityTypes.CHICKEN, at);
            if (seen != null && seen.distSqr(at) > 16 * 16) {
                if (!walking) {
                    nav.goStandNear(Explorer.legToward(level, nav.feet(), seen, 40), 3);
                    walking = true;
                }
                if (nav.tick() != BotNavigator.Status.MOVING) walking = false;
                return Result.RUNNING;
            }
            return explorer.tick(bot);
        }
        explorer.pause(bot);
        Chicken nearest = flock.stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(bot.body()))).orElseThrow();
        if (nearest.distanceTo(bot.body()) > 6) {
            if (!walking || ticks % 40 == 0) {
                nav.goStandNear(nearest.blockPosition(), 3);
                walking = true;
            }
            if (nav.tick() != BotNavigator.Status.MOVING) walking = false;
            return Result.RUNNING;
        }
        // Waiting for one to lay.
        nav.stop();
        walking = false;
        bot.body().stopInputs();
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "wait for a chicken's egg";
    }
}
