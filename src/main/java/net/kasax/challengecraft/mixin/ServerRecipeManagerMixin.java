package net.kasax.challengecraft.mixin;
import net.kasax.challengecraft.challenges.Chal_20_RandomizedCrafting;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(ServerRecipeManager.class)
public abstract class ServerRecipeManagerMixin {

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch1(RecipeType<T> type, I input, World world, CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        handle(type, world, cir);
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/recipe/RecipeEntry;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch2(RecipeType<T> type, I input, World world, RecipeEntry<T> cache, CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        handle(type, world, cir);
    }

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/registry/RegistryKey;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void onGetFirstMatch3(RecipeType<T> type, I input, World world, RegistryKey<Recipe<?>> cacheId, CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        handle(type, world, cir);
    }

    private <T extends Recipe<?>> void handle(RecipeType<T> type, World world, CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        if (!world.isClient() && Chal_20_RandomizedCrafting.isActive()) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                Optional<RecipeEntry<T>> original = cir.getReturnValue();
                Optional<RecipeEntry<T>> shuffled = Chal_20_RandomizedCrafting.getShuffledEntry(type, original, server);
                if (shuffled != original) {
                    cir.setReturnValue(shuffled);
                }
            }
        }
    }
}
