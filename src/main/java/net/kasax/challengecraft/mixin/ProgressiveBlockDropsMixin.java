package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_44_ProgressiveBlockDrops;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Block.class)
/**
 * Empties the loot of not-yet-unlocked pool blocks for Progressive Block Drops (44). Both
 * static getDroppedStacks overloads are intercepted so tool breaks and non-entity breaks
 * (explosions, pistons) are gated identically. XP is untouched (see NoBlockDropsMixin).
 */
public class ProgressiveBlockDropsMixin {

    @Inject(
            method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void gateDrops(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (Chal_44_ProgressiveBlockDrops.isDropSuppressed(state.getBlock())) {
            cir.setReturnValue(List.of());
        }
    }

    @Inject(
            method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void gateDropsWithTool(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack stack, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (Chal_44_ProgressiveBlockDrops.isDropSuppressed(state.getBlock())) {
            cir.setReturnValue(List.of());
        }
    }
}
