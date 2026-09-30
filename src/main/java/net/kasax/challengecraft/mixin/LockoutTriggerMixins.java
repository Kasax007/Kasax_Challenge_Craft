package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.minecraft.advancements.triggers.BredAnimalsTrigger;
import net.minecraft.advancements.triggers.TameAnimalTrigger;
import net.minecraft.advancements.triggers.TradeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lockout bingo listens to the moments vanilla itself reports to the advancement system: an
 * animal tamed, a pair bred, a trade completed. These triggers fire exactly when the thing really
 * happened (a wolf that refuses the bone, a breeding attempt without a partner or a trade the
 * villager rejects do not reach them), for every player and however it was done, and unlike the
 * advancements they feed they fire again in every run.
 */
public final class LockoutTriggerMixins {
    private LockoutTriggerMixins() {
    }

    @Mixin(TameAnimalTrigger.class)
    public static class Tame {
        @Inject(method = "trigger", at = @At("HEAD"))
        private void challengecraft$onTame(ServerPlayer player, Animal animal, CallbackInfo ci) {
            Chal_40_LockoutBingo.onAnimalTamed(player, animal);
        }
    }

    @Mixin(BredAnimalsTrigger.class)
    public static class Breed {
        @Inject(method = "trigger", at = @At("HEAD"))
        private void challengecraft$onBreed(ServerPlayer player, Animal parent, Animal partner, AgeableMob child, CallbackInfo ci) {
            Chal_40_LockoutBingo.onAnimalsBred(player, parent);
        }
    }

    @Mixin(TradeTrigger.class)
    public static class Trade {
        @Inject(method = "trigger", at = @At("HEAD"))
        private void challengecraft$onTrade(ServerPlayer player, AbstractVillager merchant, ItemStack result, CallbackInfo ci) {
            Chal_40_LockoutBingo.onTrade(player, merchant, result);
        }
    }
}
