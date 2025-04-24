package net.kasax.challengecraft.mixin;

import net.minecraft.loot.LootTable;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(LootTable.class)
public interface LootTableAccessor {
    @Accessor("type")
    ContextType getType();

    @Accessor("randomSequenceId")
    Optional<Identifier> getRandomSequenceId();
}
