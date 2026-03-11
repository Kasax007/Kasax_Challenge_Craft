package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.data.recipe.RecipeExporter;
import net.minecraft.data.recipe.RecipeGenerator;
import net.minecraft.data.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.data.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {

    public ModRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
        return new RecipeGenerator(wrapperLookup, recipeExporter) {
            @Override
            public void generate() {
                ShapedRecipeJsonBuilder.create(wrapperLookup.getOrThrow(net.minecraft.registry.RegistryKeys.ITEM), net.minecraft.recipe.book.RecipeCategory.MISC, net.kasax.challengecraft.block.InfiniteChestRegistry.INFINITE_CHEST_ITEM.asItem())
                        .pattern("DDD")
                        .pattern("DCD")
                        .pattern("DDD")
                        .input('D', Items.DIAMOND_BLOCK.asItem())
                        .input('C', Items.CHEST.asItem())
                        .criterion(hasItem(Items.DIAMOND_BLOCK.asItem()), conditionsFromItem(Items.DIAMOND_BLOCK.asItem()))
                        .criterion(hasItem(Items.CHEST.asItem()), conditionsFromItem(Items.CHEST.asItem()))
                        .offerTo(exporter);
            }
        };
    }

    @Override
    public String getName() {
        return "";
    }
}
