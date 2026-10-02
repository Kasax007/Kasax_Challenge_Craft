package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Against what hits too hard to stand up to (a piglin brute's axe, a hoglin's tusks, a crowd):
 * three blocks up on a pillar, as players do - nothing that only strikes close reaches up there
 * (two is not quite enough: a brute's axe still catches the feet),
 * and whatever comes to the foot of it is hit from above, blow by blow, until it is dead.
 */
public final class PillarFightTask implements BotTask {
    private static final int MAX_TICKS = 1200;
    private final LivingEntity target;
    private BlockPos base;
    private int ticks, placed, lastPlaced, placedAt;

    public PillarFightTask(LivingEntity target) {
        this.target = target;
    }

    /** Whether to take this one on from a pillar, rather than toe to toe. */
    public static boolean wanted(Bot bot, LivingEntity m) {
        BotPlayer body = bot.body();
        boolean brute = m instanceof net.minecraft.world.entity.monster.piglin.PiglinBrute;
        boolean hoglin = m instanceof net.minecraft.world.entity.monster.hoglin.Hoglin || m instanceof net.minecraft.world.entity.monster.Zoglin;
        if (!brute && !hoglin) return false;
        // Well armed and armoured, at full health: a fight like any other.
        boolean diamondSword = body.getInventory().countItem(Items.DIAMOND_SWORD) + body.getInventory().countItem(Items.NETHERITE_SWORD) > 0;
        return !(diamondSword && body.getArmorValue() >= 15 && body.getHealth() >= 18);
    }

    /** Room for two blocks under it and its head, and something to build with. */
    public static boolean possible(Bot bot) {
        return HideTask.pillarPossible(bot);
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > MAX_TICKS || !target.isAlive()) return Result.DONE;
        if (ticks % 100 == 0) bot.say("on the pillar: " + target.getType().toShortString() + " at " + String.format("%.1f", KillTask.hitDistance(body, target))
                + ", its health " + Math.round(target.getHealth()));
        if (base == null) {
            base = body.blockPosition();
            bot.navigator().stop();
            bot.actions().reset();
        }
        int height = body.blockPosition().getY() - base.getY();
        // Knocked off: up again from where it landed.
        if (body.onGround() && placed > 0 && height < placed
                && (body.blockPosition().getX() != base.getX() || body.blockPosition().getZ() != base.getZ())) {
            base = body.blockPosition();
            placed = 0;
            height = 0;
        }
        body.stopInputs();
        // High enough that its reach (up to the top of its body, a little more - and a hoglin
        // leaps as it strikes) misses the feet: three. Not getting higher for a while: fought
        // from where it is.
        int need = 3;
        if (placed > lastPlaced) {
            lastPlaced = placed;
            placedAt = ticks;
        }
        if (placed < need && height < need && ticks - placedAt < 80) {
            // Jump, and at the top of the jump a block where the feet were.
            body.jump = true;
            BlockPos below = body.blockPosition().below();
            if (!body.onGround() && level.getBlockState(below).canBeReplaced() && body.getY() - body.blockPosition().getY() < 0.6) {
                if (bot.actions().placeThrowaway(below)) placed++;
                else if (ticks % 20 == 0) bot.say("pillar: no block at " + below.toShortString() + " (" + level.getBlockState(below) + ")");
            }
            if (!bot.actions().hasThrowaway() && body.onGround()) return Result.FAILED;
            return Result.RUNNING;
        }
        // Up there: blow by blow on whatever comes to the foot (the one it is about first).
        LivingEntity hitting = target;
        if (KillTask.hitDistance(body, hitting) > 3.0) {
            hitting = level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(4),
                            m -> m.isAlive() && m.getTarget() == body && KillTask.hitDistance(body, m) <= 3.0)
                    .stream().findFirst().map(m -> (LivingEntity) m).orElse(null);
        }
        if (hitting == null) {
            body.lookAt(target.getEyePosition());
            // Gone off out of reach a long while (it lost interest): down and on.
            return ticks > 600 && target.distanceTo(body) > 8 ? Result.DONE : Result.RUNNING;
        }
        if (ticks % 10 == 1) KillTask.equipWeapon(bot);
        body.lookAt(hitting.getBoundingBox().getCenter());
        if (body.getAttackStrengthScale(0.5f) >= 1f) {
            body.attack(hitting);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "fight " + target.getType().toShortString() + " from a pillar";
    }
}
