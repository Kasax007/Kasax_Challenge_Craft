package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Generates crafting recipes for mod items and blocks. */
public class ModRecipeProvider extends FabricRecipeProvider {

    public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider wrapperLookup, RecipeOutput recipeExporter) {
        return new RecipeProvider(wrapperLookup, recipeExporter) {
            @Override
            public void buildRecipes() {
                ShapedRecipeBuilder.shaped(wrapperLookup.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM), net.minecraft.data.recipes.RecipeCategory.MISC, net.kasax.challengecraft.block.InfiniteChestRegistry.INFINITE_CHEST_ITEM.asItem())
                        .pattern("DDD")
                        .pattern("DCD")
                        .pattern("DDD")
                        .define('D', Items.DIAMOND_BLOCK.asItem())
                        .define('C', Items.CHEST.asItem())
                        .unlockedBy(getHasName(Items.DIAMOND_BLOCK.asItem()), has(Items.DIAMOND_BLOCK.asItem()))
                        .unlockedBy(getHasName(Items.CHEST.asItem()), has(Items.CHEST.asItem()))
                        .save(output);
            }
        };
    }

    @Override
    public String getName() {
        return "";
    }
}
