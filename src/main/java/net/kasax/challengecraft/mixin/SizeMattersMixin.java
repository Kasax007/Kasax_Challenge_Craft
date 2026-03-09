package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_33_SizeMatters;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SizeMattersMixin {

    @Unique
    private static final Identifier SCALE_MODIFIER_ID = Identifier.of("challengecraft", "size_matters_scale");
    @Unique
    private static final Identifier SPEED_MODIFIER_ID = Identifier.of("challengecraft", "size_matters_speed");
    @Unique
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.of("challengecraft", "size_matters_health");
    @Unique
    private static final Identifier DAMAGE_MODIFIER_ID = Identifier.of("challengecraft", "size_matters_damage");

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTick(CallbackInfo ci) {
        LivingEntity living = (LivingEntity) (Object) this;

        // Only run on server and skip players
        if (living.getWorld().isClient || living instanceof PlayerEntity) {
            return;
        }

        // Run check every 20 ticks (1 second) to be efficient
        if (living.age % 20 != 0) {
            return;
        }

        EntityAttributeInstance scaleAttr = living.getAttributeInstance(EntityAttributes.SCALE);
        if (scaleAttr == null) {
            return;
        }

        if (Chal_33_SizeMatters.isActive()) {
            if (scaleAttr.getModifier(SCALE_MODIFIER_ID) == null) {
                float scale = 0.5f + living.getRandom().nextFloat() * 2.5f;
                scaleAttr.addPersistentModifier(new EntityAttributeModifier(SCALE_MODIFIER_ID, scale - 1.0, EntityAttributeModifier.Operation.ADD_VALUE));

                // Speed: smaller is faster
                EntityAttributeInstance speedAttr = living.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    double speedMult = 1.7 - 0.4 * scale; // 0.5x -> 1.5x, 3.0x -> 0.5x
                    speedAttr.addPersistentModifier(new EntityAttributeModifier(SPEED_MODIFIER_ID, speedMult - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }

                // Health: larger has more health
                EntityAttributeInstance healthAttr = living.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (healthAttr != null) {
                    healthAttr.addPersistentModifier(new EntityAttributeModifier(HEALTH_MODIFIER_ID, (double) scale - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                    living.setHealth(living.getMaxHealth());
                }

                // Attack Damage: larger deals more damage
                EntityAttributeInstance damageAttr = living.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
                if (damageAttr != null) {
                    damageAttr.addPersistentModifier(new EntityAttributeModifier(DAMAGE_MODIFIER_ID, (double) scale - 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }

            }
        } else {
            // Remove modifiers if challenge is no longer active
            if (scaleAttr.getModifier(SCALE_MODIFIER_ID) != null) {
                scaleAttr.removeModifier(SCALE_MODIFIER_ID);
                EntityAttributeInstance speedAttr = living.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                if (speedAttr != null) speedAttr.removeModifier(SPEED_MODIFIER_ID);
                EntityAttributeInstance healthAttr = living.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (healthAttr != null) {
                    healthAttr.removeModifier(HEALTH_MODIFIER_ID);
                    if (living.getHealth() > living.getMaxHealth()) {
                        living.setHealth(living.getMaxHealth());
                    }
                }
                EntityAttributeInstance damageAttr = living.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
                if (damageAttr != null) damageAttr.removeModifier(DAMAGE_MODIFIER_ID);
            }
        }
    }
}
