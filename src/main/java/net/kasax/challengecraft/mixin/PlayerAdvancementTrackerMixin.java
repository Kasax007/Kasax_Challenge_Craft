package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public class PlayerAdvancementTrackerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Advancement");

    @Shadow
    private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void onGrantCriterion(AdvancementEntry entry, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        // If the criterion was actually granted (returned true)
        if (cir.getReturnValue()) {
            String id = entry.id().toString();
            LOGGER.info("[Advancement] criterion {} granted for {}", criterionName, id);

            if (id.equals("minecraft:end/kill_dragon")) {
                // Check if the whole advancement is now done
                if (this.owner.getAdvancementTracker().getProgress(entry).isDone()) {
                    ChallengeSavedData data = ChallengeSavedData.get(owner.getServer().getOverworld());
                    LOGGER.info("[Advancement] Free the End completed. Tainted: {}, Initial Difficulty: {}", data.isTainted(), data.getInitialDifficulty());

                    if (!data.isXpAwarded()) {
                        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                        long xpAmount = Math.round(100.0 * difficulty);
                        
                        if (xpAmount > 0) {
                            XpManager.addXp(xpAmount);
                            owner.sendMessage(Text.translatable("challengecraft.reward.xp_earned", xpAmount)
                                    .formatted(Formatting.GOLD, Formatting.BOLD), false);
                            LOGGER.info("[Advancement] Awarded {} XP to {}", xpAmount, owner.getName().getString());
                        } else {
                            if (data.isTainted()) {
                                owner.sendMessage(Text.translatable("challengecraft.reward.no_xp")
                                        .formatted(Formatting.RED), false);
                                LOGGER.info("[Advancement] No XP awarded (world is tainted)");
                            } else {
                                LOGGER.info("[Advancement] No XP awarded (difficulty was 0 or negative: {})", difficulty);
                            }
                        }
                        
                        data.setXpAwarded(true);
                    } else {
                        LOGGER.info("[Advancement] XP already awarded for this world.");
                    }
                }
            }
        }
    }
}
