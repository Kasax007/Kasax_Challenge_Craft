package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * "Return to sender": a ghast killed with its own fireball. Stands where the ghast sees it, and
 * when a fireball comes close, hits it while looking at the ghast: the fireball flies back where
 * the bot looks. Low on health it gives up (a fireball that gets through hurts).
 */
public final class GhastDeflectTask implements BotTask {
    private final Explorer explorer = new Explorer(4800);
    private Ghast ghast;
    private int ticks, hits;
    private boolean walking;

    public GhastDeflectTask() {
        explorer.lookingFor(Set.of(EntityTypes.GHAST));
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        if (++ticks > 4800 || body.getHealth() < 9) return Result.FAILED;
        if (ghast != null && !ghast.isAlive()) return Result.DONE;
        if (ghast == null) {
            ghast = body.level().getEntitiesOfClass(Ghast.class, new AABB(body.blockPosition()).inflate(64), Ghast::isAlive)
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(body), b.distanceToSqr(body))).orElse(null);
            if (ghast == null) return explorer.tick(bot);
            explorer.pause(bot);
        }
        // A fireball close by: back at the ghast.
        for (LargeFireball f : body.level().getEntitiesOfClass(LargeFireball.class, body.getBoundingBox().inflate(5), e -> e.isAlive())) {
            if (body.distanceTo(f) > 3.4) continue;
            bot.navigator().stop();
            body.stopInputs();
            body.lookAt(ghast.getBoundingBox().getCenter());
            body.attack(f);
            body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            hits++;
            return Result.RUNNING;
        }
        // In its sight, not too far (it shoots within about 64 blocks, the closer the surer).
        double d = body.distanceTo(ghast);
        if (d > 40 || !ghast.hasLineOfSight(body)) {
            if (!walking || ticks % 40 == 0) {
                bot.navigator().goNear(ghast.blockPosition().below(Math.max(0, (int) (ghast.getY() - body.getY()) - 4)), 20);
                walking = true;
            }
            bot.navigator().tick();
            return Result.RUNNING;
        }
        if (walking) bot.navigator().stop();
        walking = false;
        body.stopInputs();
        // A ghast only goes for a player within four blocks of its own height: up a pillar to
        // about its height (a few blocks; the pillar is also out of reach of anything on foot).
        if (ghast.getY() - body.getY() > 3.5 && pillared < 14 && bot.actions().hasThrowaway()) {
            pillar(bot);
            return Result.RUNNING;
        }
        body.lookAt(ghast.getBoundingBox().getCenter());
        return Result.RUNNING;
    }

    private int pillared;
    private net.minecraft.core.BlockPos jumpedFrom;

    /** One step of a pillar: jump, and a block where the feet were. */
    private void pillar(Bot bot) {
        BotPlayer body = bot.body();
        var level = body.level();
        if (jumpedFrom == null) {
            net.minecraft.core.BlockPos head = body.blockPosition().above(2);
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                bot.actions().breakTick(head);
                return;
            }
            if (body.onGround()) {
                jumpedFrom = body.blockPosition();
                body.jump = true;
            }
            return;
        }
        body.jump = false;
        if (body.getY() > jumpedFrom.getY() + 1.05) {
            if (bot.actions().placeThrowaway(jumpedFrom)) pillared++;
            jumpedFrom = null;
        } else if (body.onGround() && body.getY() < jumpedFrom.getY() + 0.5 && body.tickCount % 20 == 0) {
            jumpedFrom = null;
        }
    }

    @Override
    public String describe() {
        return "send a ghast its fireball back (" + hits + " hit back)";
    }
}
