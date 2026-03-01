package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_17_WalkRandomItem;
import net.kasax.challengecraft.challenges.Chal_18_DamageRandomItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MovementAndDamageMixin {

    @Unique
    private double walkDistanceAccumulator = 0;
    @Unique
    private Vec3d lastPos = null;
    @Unique
    private float damageAccumulator = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (Chal_17_WalkRandomItem.isActive()) {
            Vec3d currentPos = player.getPos();
            if (lastPos != null) {
                double dist = currentPos.distanceTo(lastPos);
                walkDistanceAccumulator += dist;

                while (walkDistanceAccumulator >= 500.0) {
                    walkDistanceAccumulator -= 500.0;
                    player.getInventory().insertStack(Chal_17_WalkRandomItem.getRandomItem(player.getRandom()));
                }
            }
            lastPos = currentPos;
        } else {
            lastPos = null;
            walkDistanceAccumulator = 0;
        }
    }

    @Inject(method = "damage", at = @At("RETURN"), cancellable = true)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && Chal_18_DamageRandomItem.isActive()) {
            ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
            damageAccumulator += amount;

            if (damageAccumulator >= 2.0f) {
                int hearts = (int)(damageAccumulator / 2.0f);
                damageAccumulator %= 2.0f;

                ItemStack reward = Chal_18_DamageRandomItem.getRandomItem(player.getRandom());
                reward.setCount(hearts);
                player.getInventory().insertStack(reward);
            }
        }
    }
}
