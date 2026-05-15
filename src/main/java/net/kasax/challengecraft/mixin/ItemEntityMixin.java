package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_34_UpsideDownDrops;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
/** Redirects item pickup handling for ordered collection challenges. */
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (Chal_34_UpsideDownDrops.isActive()) {
            Vec3d velocity = item.getVelocity();
            // Keep the effect readable instead of letting existing momentum decide the rise speed.
            item.setVelocity(velocity.x, 0.1, velocity.z);
            item.setNoGravity(true);
            
            if (!item.getWorld().isClient) {
                if (item.getY() > item.getWorld().getBottomY() + item.getWorld().getHeight() + 40 || item.getY() > 320) {
                    item.discard();
                }
            }
        }
    }
}
