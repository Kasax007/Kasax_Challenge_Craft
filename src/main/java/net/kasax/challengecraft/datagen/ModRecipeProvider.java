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

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {

    public ModRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
        //List<ItemConvertible> SMELTABLES = List.of(ModItems.CHALLENGE_STICK);
        //ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.CHALLENGE_STICK, 1)
        //        .input(Items.STICK)
        //        .criterion(hasItem(Items.STICK), conditionsFromItem(ModItems.CHALLENGE_STICK))
        //        .offerTo(recipeExporter);
        return null;
    }

    @Override
    public String getName() {
        return "";
    }
}
