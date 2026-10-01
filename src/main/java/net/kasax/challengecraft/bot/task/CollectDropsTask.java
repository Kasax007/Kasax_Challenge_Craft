package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Everything lying about near a spot picked up (what a block just broken dropped, say). */
public final class CollectDropsTask implements BotTask {
    private final BlockPos spot;
    private final int radius, maxTicks;
    private final Set<UUID> skipped = new HashSet<>();
    private ItemEntity picking;
    private int ticks, collectTicks;

    public CollectDropsTask(BlockPos spot, int radius, int maxTicks) {
        this.spot = spot.immutable();
        this.radius = radius;
        this.maxTicks = maxTicks;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BotNavigator nav = bot.navigator();
        if (++ticks > maxTicks) return Result.DONE;
        // (A moment for it to fall first.)
        if (ticks < 10) return Result.RUNNING;
        if (picking == null || !picking.isAlive()) {
            picking = level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(radius), e -> e.isAlive() && !skipped.contains(e.getUUID()))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(bot.body()))).orElse(null);
            if (picking == null) return Result.DONE;
            nav.goPickUp(picking);
            collectTicks = 0;
        }
        if (nav.tick() == BotNavigator.Status.FAILED || ++collectTicks > 120) {
            skipped.add(picking.getUUID());
            picking = null;
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "pick up what lies at " + spot.toShortString();
    }
}
