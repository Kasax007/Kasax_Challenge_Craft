package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/** Shot by a skeleton (a tile wants it): at full health, into its range, and wait for the arrow. */
public final class GetShotTask implements BotTask {
    private final Explorer explorer = new Explorer(3600);
    private int ticks;
    private boolean walking, explored;

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        var src = body.getLastDamageSource();
        if (src != null && src.is(DamageTypeTags.IS_PROJECTILE) && src.getEntity() instanceof AbstractSkeleton) return Result.DONE;
        if (++ticks > 4800 || body.getHealth() < 10) return Result.FAILED;
        AbstractSkeleton sk = level.getEntitiesOfClass(AbstractSkeleton.class, new AABB(body.blockPosition()).inflate(32), AbstractSkeleton::isAlive)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(body))).orElse(null);
        // (Nothing more to explore here - a small place, say: wait for one to come by.)
        if (sk == null) {
            if (!explored && explorer.tick(bot) == Result.FAILED) explored = true;
            return Result.RUNNING;
        }
        explorer.pause(bot);
        if (sk.distanceTo(body) > 12 || !sk.hasLineOfSight(body)) {
            if (!walking || ticks % 30 == 0) {
                bot.navigator().goNear(sk.blockPosition(), 8);
                walking = true;
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED) walking = false;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        walking = false;
        body.stopInputs();
        body.lookAt(sk.getEyePosition());
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "get shot by a skeleton";
    }
}
