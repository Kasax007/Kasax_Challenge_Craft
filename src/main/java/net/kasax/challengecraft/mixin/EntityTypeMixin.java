package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_15_RandomMobDrops;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(EntityType.class)
/** Marks duplicated entities so recursive double-spawn loops are avoided. */
public abstract class EntityTypeMixin {
    @Inject(method = "getDefaultLootTable", at = @At("HEAD"), cancellable = true)
    private void onGetLootTableKey(CallbackInfoReturnable<Optional<ResourceKey<LootTable>>> cir) {
        if (Chal_15_RandomMobDrops.isActive() && !Chal_15_RandomMobDrops.isBypassing()) {
            ServerLevel world = Chal_15_RandomMobDrops.getCurrentWorld();
            if (world != null) {
                EntityType<?> type = (EntityType<?>) (Object) this;
                cir.setReturnValue(Chal_15_RandomMobDrops.getSwappedLootTableKey(type, world));
            }
        }
    }
}
