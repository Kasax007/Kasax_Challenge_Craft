package net.kasax.challengecraft.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For the bot's smelting: how long the fuel burning in a furnace still burns (ticks), counted with the fuel in its slot. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccessor {
    @Accessor("litTimeRemaining")
    int challengecraft$litTimeRemaining();
}
