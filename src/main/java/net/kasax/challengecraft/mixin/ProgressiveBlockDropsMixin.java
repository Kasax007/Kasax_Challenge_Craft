package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_44_ProgressiveBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void gateDrops(BlockState state, ServerLevel world, BlockPos pos, BlockEntity blockEntity, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (Chal_44_ProgressiveBlockDrops.isDropSuppressed(state.getBlock(), world)) {
            cir.setReturnValue(List.of());
        }
    }

    @Inject(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void gateDropsWithTool(BlockState state, ServerLevel world, BlockPos pos, BlockEntity blockEntity, Entity entity, net.minecraft.world.item.ItemInstance stack, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (Chal_44_ProgressiveBlockDrops.isDropSuppressed(state.getBlock(), world)) {
            cir.setReturnValue(List.of());
        }
    }
}
