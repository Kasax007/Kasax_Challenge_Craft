package net.kasax.challengecraft.mixin;

import net.minecraft.world.level.LevelSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for world metadata needed during restart handling.
 *
 * <p><b>DISABLED — deliberately not listed in {@code challengecraft.mixins.json}.</b> It is kept as
 * source only; nothing applies it and nothing calls it.
 *
 * <p>Two reasons it cannot work on 26.2:
 * <ul>
 *   <li>{@link LevelSettings} became a {@code record}, and {@code hardcore} is no longer one of its
 *       components — it moved into {@code LevelSettings.DifficultySettings}, where
 *       {@code hardcore()} is already public. A setter into an immutable record component is not a
 *       meaningful operation.</li>
 *   <li>The mixin has no call sites anywhere in the mod, so removing it from the config costs
 *       nothing. Left listed, its unresolvable {@code @Accessor} would fail the whole mixin
 *       config at boot ({@code "required": true}).</li>
 * </ul>
 *
 * <p>If a hardcore toggle is ever needed again, read it via
 * {@code settings.difficultySettings().hardcore()} rather than reviving this.
 */
@Mixin(LevelSettings.class)
public interface LevelInfoAccessor {
    @Accessor("hardcore")
    void setHardcore(boolean hardcore);
}
