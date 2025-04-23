package net.kasax.challengecraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_2_NoBlockDrops;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.SERVER)
@Mixin(Block.class)
public class NoBlockDropsMixin {
    @Inject(
            method = "afterBreak(Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;"
                    + "Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/block/entity/BlockEntity;"
                    + "Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAfterBreak(
            World world,
            PlayerEntity player,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            ItemStack tool,
            CallbackInfo ci
    ) {
        if (Chal_2_NoBlockDrops.isActive()) {
            // cancel the vanilla dropStacks call entirely
            ci.cancel();
        }
    }
}
