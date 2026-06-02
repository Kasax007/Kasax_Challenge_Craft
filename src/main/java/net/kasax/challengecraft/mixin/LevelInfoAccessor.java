package net.kasax.challengecraft.mixin;

import net.minecraft.world.level.LevelSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelSettings.class)
/** Accessor for world metadata needed during restart handling. */
public interface LevelInfoAccessor {
    @Accessor("hardcore")
    void setHardcore(boolean hardcore);
}
