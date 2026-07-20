package net.kasax.challengecraft.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class)
/** Exposes the goal selector so Chal_42 can bolt an attack goal onto passive mobs. */
public interface MobEntityAccessor {
    @Accessor("goalSelector")
    GoalSelector challengecraft$getGoalSelector();
}
