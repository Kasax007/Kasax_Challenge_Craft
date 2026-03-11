package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.kasax.challengecraft.block.InfiniteChestRegistry;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.SurvivesExplosionLootCondition;
import net.minecraft.loot.entry.DynamicEntry;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class ModLootTableProvider extends FabricBlockLootTableProvider {
    public ModLootTableProvider(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        addDrop(InfiniteChestRegistry.INFINITE_CHEST_BLOCK, LootTable.builder()
            .pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f))
                .with(ItemEntry.builder(InfiniteChestRegistry.INFINITE_CHEST_BLOCK))
                .conditionally(SurvivesExplosionLootCondition.builder()))
            .pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0f))
                .with(DynamicEntry.builder(ShulkerBoxBlock.CONTENTS_DYNAMIC_DROP_ID))
                .conditionally(SurvivesExplosionLootCondition.builder()))
        );
    }
}
