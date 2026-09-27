package net.kasax.challengecraft.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.kasax.challengecraft.block.InfiniteChestRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.DynamicLoot;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import java.util.concurrent.CompletableFuture;

/** Generates block loot tables for custom blocks. */
public class ModLootTableProvider extends FabricBlockLootSubProvider {
    public ModLootTableProvider(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        // 26.3: number providers split into int/float variants; ConstantValue moved under
        // providers.number.ints, and LootPool.setRolls now takes a Holder<ContextIntProvider>
        // (ContextIntProviders.exactly(int) builds one) instead of a raw float NumberProvider.
        add(InfiniteChestRegistry.INFINITE_CHEST_BLOCK, LootTable.lootTable()
            .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                .add(LootItem.lootTableItem(InfiniteChestRegistry.INFINITE_CHEST_BLOCK))
                .when(ExplosionCondition.survivesExplosion()))
            .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                .add(DynamicLoot.dynamicEntry(ShulkerBoxBlock.CONTENTS))
                .when(ExplosionCondition.survivesExplosion()))
        );
    }
}
