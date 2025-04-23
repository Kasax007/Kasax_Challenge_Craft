package net.kasax.challengecraft.mixin;

import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelInfo.class)
public interface LevelInfoAccessor {
    @Accessor("hardcore")
    void setHardcore(boolean hardcore);
}
