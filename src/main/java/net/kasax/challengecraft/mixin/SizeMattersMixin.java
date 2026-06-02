package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_33_SizeMatters;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
/** Scales living entities according to the size-matters challenge state. */
public abstract class SizeMattersMixin {

    @Unique
    private static final Identifier SCALE_MODIFIER_ID = Identifier.fromNamespaceAndPath("challengecraft", "size_matters_scale");
    @Unique
    private static final Identifier SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("challengecraft", "size_matters_speed");
    @Unique
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath("challengecraft", "size_matters_health");
    @Unique
    private static final Identifier DAMAGE_MODIFIER_ID = Identifier.fromNamespaceAndPath("challengecraft", "size_matters_damage");

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void onBaseTick(CallbackInfo ci) {
        LivingEntity living = (LivingEntity) (Object) this;

        if (living.level().isClientSide() || living instanceof Player) {
            return;
        }

        if (living.tickCount % 20 != 0) {
            return;
        }

        AttributeInstance scaleAttr = living.getAttribute(Attributes.SCALE);
        if (scaleAttr == null) {
            return;
        }

        if (Chal_33_SizeMatters.isActive()) {
            if (scaleAttr.getModifier(SCALE_MODIFIER_ID) == null) {
                float scale = 0.5f + living.getRandom().nextFloat() * 2.5f;
                scaleAttr.addPermanentModifier(new AttributeModifier(SCALE_MODIFIER_ID, scale - 1.0, AttributeModifier.Operation.ADD_VALUE));

                AttributeInstance speedAttr = living.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    double speedMult = 1.7 - 0.4 * scale; // 0.5x -> 1.5x, 3.0x -> 0.5x
                    speedAttr.addPermanentModifier(new AttributeModifier(SPEED_MODIFIER_ID, speedMult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }

                AttributeInstance healthAttr = living.getAttribute(Attributes.MAX_HEALTH);
                if (healthAttr != null) {
                    healthAttr.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER_ID, (double) scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                    living.setHealth(living.getMaxHealth());
                }

                AttributeInstance damageAttr = living.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damageAttr != null) {
                    damageAttr.addPermanentModifier(new AttributeModifier(DAMAGE_MODIFIER_ID, (double) scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }

            }
        } else {
            if (scaleAttr.getModifier(SCALE_MODIFIER_ID) != null) {
                scaleAttr.removeModifier(SCALE_MODIFIER_ID);
                AttributeInstance speedAttr = living.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) speedAttr.removeModifier(SPEED_MODIFIER_ID);
                AttributeInstance healthAttr = living.getAttribute(Attributes.MAX_HEALTH);
                if (healthAttr != null) {
                    healthAttr.removeModifier(HEALTH_MODIFIER_ID);
                    if (living.getHealth() > living.getMaxHealth()) {
                        living.setHealth(living.getMaxHealth());
                    }
                }
                AttributeInstance damageAttr = living.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damageAttr != null) damageAttr.removeModifier(DAMAGE_MODIFIER_ID);
            }
        }
    }
}
