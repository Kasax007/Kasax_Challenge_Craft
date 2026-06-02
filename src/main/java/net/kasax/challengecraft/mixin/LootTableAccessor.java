package net.kasax.challengecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.LootTable;

@Mixin(LootTable.class)
/** Exposes the loot-table registry key used by randomized mob drops. */
public interface LootTableAccessor {
    @Accessor("type")
    ContextKeySet getType();

    @Accessor("randomSequenceId")
    Optional<Identifier> getRandomSequenceId();
}
