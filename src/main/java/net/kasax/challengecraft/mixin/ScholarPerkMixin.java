package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(Player.class)
/** Applies the scholar perk bonus to earned experience. */
public abstract class ScholarPerkMixin {
    @ModifyVariable(method = "giveExperiencePoints", at = @At("HEAD"), argsOnly = true)
    private int modifyExperienceGain(int experience) {
        Player player = (Player) (Object) this;
        if (player.level().isClientSide()) {
            return experience;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.level().getServer() == null) return experience;
            ChallengeSavedData data = ChallengeSavedData.get(serverPlayer.level().getServer().overworld());
            List<Integer> activePerks = data.getActivePerks();
            long totalXp = XpManager.getXp(player.getUUID());
            int level = LevelManager.getLevelForXp(totalXp);

            if (activePerks.contains(LevelManager.PERK_SCHOLAR) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_SCHOLAR)) {
                return (int) (experience * 1.5);
            }
        }
        return experience;
    }
}
