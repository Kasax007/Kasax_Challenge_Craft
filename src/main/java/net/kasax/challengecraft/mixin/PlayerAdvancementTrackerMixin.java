package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.network.ChallengeRewardPacket;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
/** Observes completed advancements for ordered advancement progression. */
public class PlayerAdvancementTrackerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Advancement");

    @Shadow
    private ServerPlayer owner;

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void onGrantCriterion(AdvancementHolder entry, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            String id = entry.id().toString();
            LOGGER.info("[Advancement] criterion {} granted for {}", criterionName, id);

            if (id.equals("minecraft:end/kill_dragon")) {
                if (this.owner.getAdvancements().getOrStartProgress(entry).isDone()) {
                    ChallengeSavedData data = ChallengeSavedData.get(owner.level().getServer().overworld());
                    LOGGER.info("[Advancement] Free the End completed. Tainted: {}, Initial Difficulty: {}", data.isTainted(), data.getInitialDifficulty());

                    if (data.getActive().contains(22) || data.getActive().contains(23)) {
                        LOGGER.info("[Advancement] Skipping Ender Dragon XP award because All Items (22) or All Entities (23) challenge is active.");
                        return;
                    }

                    List<ServerPlayer> eligiblePlayers = owner.level().getServer().getPlayerList().getPlayers().stream()
                            .filter(p -> !data.isXpAwarded(p.getUUID()))
                            .toList();

                    if (!eligiblePlayers.isEmpty()) {
                        for (int cid : data.getActive()) {
                            eligiblePlayers.forEach(p -> {
                                int pTicks = ChallengeTimeUtil.getDisplayPlayTicks(p);
                                StatsManager.recordCompletion(p.getStringUUID(), cid, pTicks);
                            });
                        }
                        
                        eligiblePlayers.forEach(LevelManager::sync);

                        double difficulty = data.isTainted() ? 0 : data.getInitialDifficulty();
                        long xpAmount = Math.round(100.0 * difficulty);
                        
                        if (xpAmount > 0) {
                            final long baseAmount = xpAmount;
                            eligiblePlayers.forEach(p -> {
                                LevelManager.XpResult res = LevelManager.addXp(p, baseAmount);
                                data.setXpAwarded(p.getUUID(), true);
                                // Dragon completion is a reward trigger, not a full run-completion screen.
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new ChallengeRewardPacket(res.oldXp, res.newXp, res.actualAmount, false));
                                
                                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
                            });
                            
                            Component chatMsg = Component.translatable("challengecraft.reward.xp_earned", xpAmount)
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                            owner.level().getServer().getPlayerList().broadcastSystemMessage(chatMsg, false);
                            
                            Component title = Component.translatable("challengecraft.reward.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
                            Component subtitle = Component.translatable("challengecraft.reward.xp_earned", xpAmount).withStyle(ChatFormatting.GOLD);
                            
                            owner.level().getServer().getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                            owner.level().getServer().getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));
                            owner.level().getServer().getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));
                            
                            LOGGER.info("[Advancement] Awarded {} XP to eligible players (triggered by {})", xpAmount, owner.getName().getString());
                        } else {
                            if (data.isTainted()) {
                                owner.sendSystemMessage(Component.translatable("challengecraft.reward.no_xp")
                                        .withStyle(ChatFormatting.RED));
                                LOGGER.info("[Advancement] No XP awarded (world is tainted)");
                            } else {
                                LOGGER.info("[Advancement] No XP awarded (difficulty was 0 or negative: {})", difficulty);
                            }
                        }
                    } else {
                        LOGGER.info("[Advancement] XP already awarded for this world.");
                    }
                }
            }
        }
    }
}
