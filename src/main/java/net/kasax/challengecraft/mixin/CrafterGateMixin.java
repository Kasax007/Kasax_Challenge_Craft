package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.block.InfiniteChestRegistry;
import net.kasax.challengecraft.casino.CasinoRegistry;
import net.kasax.challengecraft.casino.DeviceType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * The crafter makes nothing that is gated per player: the Infinite Chest (level 20 and its perk)
 * and the casino devices (bought from the croupier once). Those gates sit on the crafting result
 * slot ({@code MixinScreenHandler}), which a crafter never clicks, so it used to hand them out to
 * anyone with the ingredients and a button. Covers the crafter's result preview too.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterGateMixin {
    @Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
    private static void challengecraft$gatedResults(ServerLevel level, CraftingInput input,
                                                    CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        Optional<RecipeHolder<CraftingRecipe>> recipe = cir.getReturnValue();
        if (recipe.isEmpty()) return;
        ItemStack result = recipe.get().value().assemble(input);
        DeviceType device = CasinoRegistry.deviceOf(result.getItem());
        if (result.is(InfiniteChestRegistry.INFINITE_CHEST_ITEM) || (device != null && device != DeviceType.CASHIER)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
