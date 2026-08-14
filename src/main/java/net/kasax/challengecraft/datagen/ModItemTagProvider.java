package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

import java.util.concurrent.CompletableFuture;

/** Generates item tags required by the mod's content. */
// See ModBlockTagProvider: 26.2 replaced the inner-class providers with a generic
// FabricTagsProvider<T> that is handed the registry key.
public class ModItemTagProvider extends FabricTagsProvider<Item> {

    public ModItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, Registries.ITEM, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
    }
}
