package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

/** Generates block tags required by the mod's content. */
// 26.2 dropped the FabricTagsProvider.BlockTagProvider/ItemTagProvider inner classes in favour of
// one generic FabricTagsProvider<T> that takes the registry key explicitly.
public class ModBlockTagProvider extends FabricTagsProvider<Block> {
    public ModBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, Registries.BLOCK, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
    }
}
