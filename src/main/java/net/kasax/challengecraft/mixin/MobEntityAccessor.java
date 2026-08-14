package net.kasax.challengecraft.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
/** Exposes the goal selector so Chal_42 can bolt an attack goal onto passive mobs. */
public interface MobEntityAccessor {
    @Accessor("goalSelector")
    GoalSelector challengecraft$getGoalSelector();
}
