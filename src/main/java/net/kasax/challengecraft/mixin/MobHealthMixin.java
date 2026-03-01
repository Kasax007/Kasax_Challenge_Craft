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
public abstract class MobHealthMixin {

    @Unique
    private static final Identifier HEALTH_MULTIPLIER_ID = Identifier.of("challengecraft", "mob_health_multiplier");

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTick(CallbackInfo ci) {
        LivingEntity living = (LivingEntity) (Object) this;

        // Only run on server and skip players
        if (living.getWorld().isClient || living instanceof PlayerEntity) {
            return;
        }

        // Run check every 10 ticks (0.5 second) to be efficient
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
                // Apply modifier if it doesn't exist and multiplier is > 1
                if (currentMultiplier > 1) {
                    healthAttr.addPersistentModifier(new EntityAttributeModifier(
                            HEALTH_MULTIPLIER_ID,
                            targetModifierValue,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ));
                    // Heal the entity to its new max health when the modifier is first applied
                    living.setHealth(living.getMaxHealth());
                }
            } else if (Math.abs(existing.value() - targetModifierValue) > 0.001) {
                // Update existing modifier if the multiplier changed
                healthAttr.removeModifier(HEALTH_MULTIPLIER_ID);
                if (currentMultiplier > 1) {
                    healthAttr.addPersistentModifier(new EntityAttributeModifier(
                            HEALTH_MULTIPLIER_ID,
                            targetModifierValue,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ));
                    // Optional: heal when scaling up
                    if (targetModifierValue > existing.value()) {
                        living.setHealth(living.getMaxHealth());
                    }
                }
            }
        } else {
            // Remove modifier if challenge is no longer active
            if (healthAttr.getModifier(HEALTH_MULTIPLIER_ID) != null) {
                healthAttr.removeModifier(HEALTH_MULTIPLIER_ID);
                // Clamp current health if it exceeds new max
                if (living.getHealth() > living.getMaxHealth()) {
                    living.setHealth(living.getMaxHealth());
                }
            }
        }
    }
}
