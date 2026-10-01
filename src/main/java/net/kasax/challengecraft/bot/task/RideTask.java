package net.kasax.challengecraft.bot.task;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Rides a pig or a strider: a saddle put on one (or one saddled already), then up onto it; a
 * moment on its back, then off again where there is ground to step onto (a strider walks on
 * lava: off only beside the shore).
 */
public final class RideTask implements BotTask {
    private final EntityType<?> type;
    private final Explorer explorer = new Explorer(4800);
    private final Set<UUID> failed = new HashSet<>();
    private LivingEntity target;
    private int chase, riding, ticks;
    private boolean walking;

    public RideTask(EntityType<?> type) {
        this.type = type;
        explorer.lookingFor(Set.of(type));
    }

    private static boolean saddled(LivingEntity e) {
        return !e.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        if (++ticks > 6000) return Result.FAILED;
        if (body.getVehicle() != null) {
            body.forward = 0;
            // Long enough on it to count, then off where it is safe to step down.
            if (++riding < 60) return Result.RUNNING;
            if (safeToStepOff(body)) {
                body.sneak = true;
                return Result.RUNNING;
            }
            return riding > 2400 ? Result.FAILED : Result.RUNNING;
        }
        if (riding > 0) {
            body.sneak = false;
            return Result.DONE;
        }
        if (target == null || !target.isAlive()) {
            target = body.level().getEntitiesOfClass(LivingEntity.class, new AABB(body.blockPosition()).inflate(48),
                            e -> e.getType() == type && e.isAlive() && !e.isBaby() && !failed.contains(e.getUUID())
                                    && (saddled(e) || BotInventory.slotOf(body, Items.SADDLE) >= 0))
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(body), b.distanceToSqr(body))).orElse(null);
            chase = 0;
            walking = false;
            if (target == null) return explorer.tick(bot);
            explorer.pause(bot);
        }
        if (++chase > 900) {
            failed.add(target.getUUID());
            target = null;
            return Result.RUNNING;
        }
        if (body.distanceTo(target) > 2.6) {
            if (!walking || chase % 30 == 0) {
                bot.navigator().goNear(target.blockPosition(), 2.0);
                walking = true;
            }
            if (bot.navigator().tick() == net.kasax.challengecraft.bot.BotNavigator.Status.FAILED) {
                walking = false;
                if (body.distanceTo(target) > 5) {
                    failed.add(target.getUUID());
                    target = null;
                }
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.stopInputs();
        // Saddle on first, then up with an empty hand.
        if (!saddled(target)) bot.tools().select(BotInventory.slotOf(body, Items.SADDLE));
        else bot.tools().selectEmptyHandPublic();
        body.lookAt(target.getEyePosition());
        var hit = new EntityHitResult(target, target.getEyePosition());
        InteractionResult r = UseEntityCallback.EVENT.invoker().interact(body, body.level(), InteractionHand.MAIN_HAND, target, hit);
        if (r == InteractionResult.PASS) r = body.interactOn(target, InteractionHand.MAIN_HAND, target.getEyePosition());
        if (r.consumesAction()) body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        return Result.RUNNING;
    }

    /** Ground beside the mount to step onto (not lava, room to stand). */
    private static boolean safeToStepOff(BotPlayer body) {
        var level = body.level();
        BlockPos at = body.getVehicle().blockPosition();
        if (level.getFluidState(at).isEmpty() && level.getFluidState(at.below()).isEmpty()) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = at.relative(d);
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos f = q.above(dy);
                if (!level.getBlockState(f.below()).getCollisionShape(level, f.below()).isEmpty() && level.getFluidState(f.below()).isEmpty()
                        && level.getBlockState(f).getCollisionShape(level, f).isEmpty() && level.getFluidState(f).isEmpty()
                        && level.getBlockState(f.above()).getCollisionShape(level, f.above()).isEmpty()) return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return "ride a " + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }
}
