package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.client.data.BlockStateModelGenerator;
import net.minecraft.client.data.ItemModelGenerator;
import net.minecraft.client.data.Models;

/** Generates block and item models used by the mod. */
public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {
    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {
        itemModelGenerator.register(ModItems.CHALLENGE_STICK, Models.GENERATED);
        // NOTE: ModItems.DICE is deliberately NOT registered here. Datagen's item API can only
        // express single-sprite models — ItemModelGenerator.upload(Item, Model) builds a layer0-only
        // TextureMap — but the die needs a 6-face cube so it reads as a real 3D die in the
        // inventory. Its two JSONs are therefore hand-written under resources/. This is the one
        // documented exception to the "prefer datagen" rule in CLAUDE.md.
    }
}
