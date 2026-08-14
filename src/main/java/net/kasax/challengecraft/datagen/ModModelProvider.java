package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;

/** Generates block and item models used by the mod. */
public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockStateModelGenerator) {
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerator) {
        itemModelGenerator.generateFlatItem(ModItems.CHALLENGE_STICK, ModelTemplates.FLAT_ITEM);
        // NOTE: ModItems.DICE is deliberately NOT registered here. Datagen's item API can only
        // express single-sprite models — ItemModelGenerator.upload(Item, Model) builds a layer0-only
        // TextureMap — but the die needs a 6-face cube so it reads as a real 3D die in the
        // inventory. Its two JSONs are therefore hand-written under resources/. This is the one
        // documented exception to the "prefer datagen" rule in CLAUDE.md.
    }
}
