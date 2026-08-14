package net.kasax.challengecraft.mixin;
import net.kasax.challengecraft.challenges.Chal_20_RandomizedCrafting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(RecipeManager.class)
/** Redirects recipe lookup through the randomized crafting mapping. */
public abstract class ServerRecipeManagerMixin {

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch1(RecipeType<T> type, I input, Level world, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        handle(type, world, cir);
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch2(RecipeType<T> type, I input, Level world, RecipeHolder<T> cache, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        handle(type, world, cir);
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch3(RecipeType<T> type, I input, Level world, ResourceKey<Recipe<?>> cacheId, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        handle(type, world, cir);
    }

    private <T extends Recipe<?>> void handle(RecipeType<T> type, Level world, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        if (!world.isClientSide() && Chal_20_RandomizedCrafting.isActive()) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                Optional<RecipeHolder<T>> original = cir.getReturnValue();
                Optional<RecipeHolder<T>> shuffled = Chal_20_RandomizedCrafting.getShuffledEntry(type, original, server);
                if (shuffled != original) {
                    cir.setReturnValue(shuffled);
                }
            }
        }
    }
}
