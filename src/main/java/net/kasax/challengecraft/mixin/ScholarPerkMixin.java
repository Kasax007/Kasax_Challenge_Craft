package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class ScholarPerkMixin {
    @ModifyVariable(method = "addExperience", at = @At("HEAD"), argsOnly = true)
    private int modifyExperienceGain(int experience) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getWorld().isClient()) {
            // On client, we could use the synced perks, but XP gain is usually server-side anyway.
            return experience;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (serverPlayer.getServer() == null) return experience;
            ChallengeSavedData data = ChallengeSavedData.get(serverPlayer.getServer().getOverworld());
            List<Integer> activePerks = data.getActivePerks();
            long totalXp = XpManager.getXp(player.getUuid());
            int level = LevelManager.getLevelForXp(totalXp);

            if (activePerks.contains(LevelManager.PERK_SCHOLAR) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_SCHOLAR)) {
                return (int) (experience * 1.5);
            }
        }
        return experience;
    }
}
