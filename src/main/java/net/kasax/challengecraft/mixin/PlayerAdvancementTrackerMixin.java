package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
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

                    if (data.getActive().contains(22) || data.getActive().contains(23)) {
                        LOGGER.info("[Advancement] Skipping Ender Dragon XP award because All Items (22) or All Entities (23) challenge is active.");
                        return;
                    }

                    if (!data.isXpAwarded()) {
                        // Record completion for all active challenges
                        int playTicks = owner.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
                        for (int cid : data.getActive()) {
                            StatsManager.recordCompletion(cid, playTicks);
                        }

                        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                        long xpAmount = Math.round(100.0 * difficulty);
                        
                        if (xpAmount > 0) {
                            final long baseAmount = xpAmount;
                            owner.getServer().getPlayerManager().getPlayerList().forEach(p -> {
                                LevelManager.XpResult res = LevelManager.addXp(p, baseAmount);
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount));
                            });
                            
                            Text chatMsg = Text.translatable("challengecraft.reward.xp_earned", xpAmount)
                                    .formatted(Formatting.GOLD, Formatting.BOLD);
                            owner.getServer().getPlayerManager().broadcast(chatMsg, false);
                            
                            // Visual and Audio reward for everyone
                            owner.getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
                            
                            Text title = Text.translatable("challengecraft.reward.title").formatted(Formatting.GREEN, Formatting.BOLD);
                            Text subtitle = Text.translatable("challengecraft.reward.xp_earned", xpAmount).formatted(Formatting.GOLD);
                            
                            owner.getServer().getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 70, 20));
                            owner.getServer().getPlayerManager().sendToAll(new TitleS2CPacket(title));
                            owner.getServer().getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));
                            
                            LOGGER.info("[Advancement] Awarded {} XP to all players (triggered by {})", xpAmount, owner.getName().getString());
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
