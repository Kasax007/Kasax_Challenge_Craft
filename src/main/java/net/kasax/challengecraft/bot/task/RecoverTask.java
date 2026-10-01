package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/**
 * Back to where it died for its things (they lie there five minutes): quicker than gathering wood
 * and stone again, and the iron and the rest come back too. Over the surface in legs, then every
 * item lying about there picked up.
 */
public final class RecoverTask implements BotTask {
    private final BlockPos spot;
    private boolean walking, there;
    private int ticks, fails, collectTicks;
    private ItemEntity picking;
    private final java.util.Set<java.util.UUID> skipped = new java.util.HashSet<>();

    public RecoverTask(BlockPos spot) {
        this.spot = spot.immutable();
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BotNavigator nav = bot.navigator();
        if (++ticks > 6000) return Result.FAILED;
        if (!there) {
            double dx = spot.getX() - bot.body().getX(), dz = spot.getZ() - bot.body().getZ();
            if (dx * dx + dz * dz < 36 && Math.abs(spot.getY() - bot.body().getY()) < 8) {
                there = true;
                nav.stop();
                walking = false;
            } else {
                if (!walking) {
                    nav.goStandNear(Explorer.legToward(level, nav.feet(), spot, 40), 3);
                    walking = true;
                }
                BotNavigator.Status s = nav.tick();
                if (s != BotNavigator.Status.MOVING) walking = false;
                // Arrived as near as there is a way to: look about from here (the drops may lie
                // a little below or above).
                if (s == BotNavigator.Status.ARRIVED && dx * dx + dz * dz < 100) there = true;
                if (s == BotNavigator.Status.FAILED && ++fails > 6) return Result.FAILED;
                return Result.RUNNING;
            }
        }
        // There: everything lying about.
        if (picking == null || !picking.isAlive()) {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(10), e -> e.isAlive() && !skipped.contains(e.getUUID()));
            picking = items.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(bot.body()))).orElse(null);
            if (picking == null) return Result.DONE;
            nav.goPickUp(picking);
            collectTicks = 0;
        }
        BotNavigator.Status s = nav.tick();
        if (s == BotNavigator.Status.FAILED || ++collectTicks > 200) {
            skipped.add(picking.getUUID()); // (out of reach: the others)
            picking = null;
            if (++fails > 12) return Result.DONE;
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "fetch my things at " + spot.toShortString();
    }
}
