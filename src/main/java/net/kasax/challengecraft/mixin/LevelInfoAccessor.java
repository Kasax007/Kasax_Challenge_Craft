package net.kasax.challengecraft.mixin;

import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelInfo.class)
/** Accessor for world metadata needed during restart handling. */
public interface LevelInfoAccessor {
    @Accessor("hardcore")
    void setHardcore(boolean hardcore);
}
