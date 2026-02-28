package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_15_RandomMobDrops;
import net.minecraft.entity.EntityType;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
    @Inject(method = "getLootTableKey", at = @At("HEAD"), cancellable = true)
    private void onGetLootTableKey(CallbackInfoReturnable<Optional<RegistryKey<LootTable>>> cir) {
        if (Chal_15_RandomMobDrops.isActive() && !Chal_15_RandomMobDrops.isBypassing()) {
            ServerWorld world = Chal_15_RandomMobDrops.getCurrentWorld();
            if (world != null) {
                EntityType<?> type = (EntityType<?>) (Object) this;
                cir.setReturnValue(Chal_15_RandomMobDrops.getSwappedLootTableKey(type, world));
            }
        }
    }
}
