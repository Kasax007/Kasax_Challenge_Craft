package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_24_MobHealthMultiply;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
/** Applies the challenge mob-health multiplier when entities initialize attributes. */
public abstract class MobHealthMixin {

    @Unique
    private static final Identifier HEALTH_MULTIPLIER_ID = Identifier.of("challengecraft", "mob_health_multiplier");

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTick(CallbackInfo ci) {
        LivingEntity living = (LivingEntity) (Object) this;

        if (living.getWorld().isClient || living instanceof PlayerEntity) {
            return;
        }

        if (living.age % 10 != 0) {
            return;
        }

        EntityAttributeInstance healthAttr = living.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttr == null) {
            return;
        }

        if (Chal_24_MobHealthMultiply.isActive()) {
            int currentMultiplier = Chal_24_MobHealthMultiply.getMultiplier();
            double targetModifierValue = (double) currentMultiplier - 1.0;
            
            EntityAttributeModifier existing = healthAttr.getModifier(HEALTH_MULTIPLIER_ID);
            
            if (existing == null) {
                if (currentMultiplier > 1) {
                    healthAttr.addPersistentModifier(new EntityAttributeModifier(
                            HEALTH_MULTIPLIER_ID,
                            targetModifierValue,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ));
                    // Newly scaled mobs should not start below their new health cap.
                    living.setHealth(living.getMaxHealth());
                }
            } else if (Math.abs(existing.value() - targetModifierValue) > 0.001) {
                healthAttr.removeModifier(HEALTH_MULTIPLIER_ID);
                if (currentMultiplier > 1) {
                    healthAttr.addPersistentModifier(new EntityAttributeModifier(
                            HEALTH_MULTIPLIER_ID,
                            targetModifierValue,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ));
                    if (targetModifierValue > existing.value()) {
                        living.setHealth(living.getMaxHealth());
                    }
                }
            }
        } else {
            if (healthAttr.getModifier(HEALTH_MULTIPLIER_ID) != null) {
                healthAttr.removeModifier(HEALTH_MULTIPLIER_ID);
                if (living.getHealth() > living.getMaxHealth()) {
                    living.setHealth(living.getMaxHealth());
                }
            }
        }
    }
}
