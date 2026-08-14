package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.client.screen.AllAchievementsHUD;
import net.kasax.challengecraft.client.screen.AllAchievementsScreen;
import net.kasax.challengecraft.client.screen.AllEntitiesHUD;
import net.kasax.challengecraft.client.screen.AllEntitiesScreen;
import net.kasax.challengecraft.client.screen.AllItemsHUD;
import net.kasax.challengecraft.client.screen.AllItemsScreen;
import net.kasax.challengecraft.client.screen.LockoutBingoBoardScreen;
import net.kasax.challengecraft.client.screen.ForceItemClientState;
import net.kasax.challengecraft.client.screen.ForceItemHUD;
import net.kasax.challengecraft.client.screen.ForceItemResultsScreen;
import net.kasax.challengecraft.client.screen.ForceItemTeamScreen;
import net.kasax.challengecraft.client.screen.ProgressiveBlocksHUD;
import net.kasax.challengecraft.client.screen.LockoutBingoClientState;
import net.kasax.challengecraft.client.screen.LockoutBingoTeamScreen;
import net.kasax.challengecraft.client.screen.TimerOverlay;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.ChallengeCraftClient;

/** Applies server challenge sync packets to client-side state and HUDs. */
public class ChallengeSyncHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayTimeSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                TimerOverlay.setBasePlayTicks(payload.playTicks);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChallengeSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                ChallengeManager.setAllActive(false);
                AllItemsHUD.setActive(false);
                AllEntitiesHUD.setActive(false);
                AllAchievementsHUD.setActive(false);
                ProgressiveBlocksHUD.setActive(false);
                ForceItemHUD.setActive(false);

                for (int id : payload.active) {
                    ChallengeManager.applyActiveFlag(id, null, null);
                    if (id == 22) AllItemsHUD.setActive(true);
                    if (id == 23) AllEntitiesHUD.setActive(true);
                    if (id == 26) AllAchievementsHUD.setActive(true);
                    if (id == 44) ProgressiveBlocksHUD.setActive(true);
                    if (id == 45) ForceItemHUD.setActive(true);
                }

                ChallengeCraftClient.LAST_CHOSEN = payload.active;
                ChallengeCraftClient.SELECTED_PERKS = payload.perks;
                ChallengeCraftClient.SELECTED_MAX_HEARTS = payload.maxHearts;
                ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = payload.limitedInventorySlots;
                ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = payload.mobHealthMultiplier;
                ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER = payload.doubleTroubleMultiplier;
                ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER = payload.gameSpeedMultiplier;
                ChallengeCraftClient.SELECTED_FIB_MINUTES = payload.forceItemBattleMinutes;
                
                Chal_24_MobHealthMultiply.setMultiplier(payload.mobHealthMultiplier);
                Chal_35_DoubleTrouble.setMultiplier(payload.doubleTroubleMultiplier);
                Chal_37_GameSpeed.setMultiplier(payload.gameSpeedMultiplier);
                Chal_12_LimitedInventory.setLimitedSlots(payload.limitedInventorySlots);
                Chal_7_MaxHealthModify.setMaxHearts(payload.maxHearts * 0.5f);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllItemsSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                AllItemsHUD.update(payload.currentItem, payload.currentIndex, payload.totalItems);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllItemsListPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreenAndShow(new AllItemsScreen(payload.items, payload.currentIndex));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ProgressiveBlocksListPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreenAndShow(
                        new net.kasax.challengecraft.client.screen.ProgressiveBlocksScreen(payload.blockIds, payload.currentIndex));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ForceItemSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> ForceItemClientState.update(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(DiceSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                net.kasax.challengecraft.client.screen.DiceClientState.update(payload);
                // The movement mixin runs on the client and reads this, not the server map.
                net.kasax.challengecraft.challenges.Chal_46_Dice.setClientRemaining(payload.remaining);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ForceItemResultsPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().gui.screen() instanceof ForceItemResultsScreen screen) {
                    screen.updateFromPacket(payload);
                } else {
                    context.client().setScreenAndShow(new ForceItemResultsScreen(payload));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ForceItemOpenScreenPacket.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreenAndShow(new ForceItemTeamScreen()));
        });

        ClientPlayNetworking.registerGlobalReceiver(ProgressiveBlocksSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                ProgressiveBlocksHUD.update(payload.targetBlockId, payload.currentIndex, payload.totalBlocks);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllEntitiesSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                AllEntitiesHUD.update(payload.currentEntity, payload.currentIndex, payload.totalEntities);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllEntitiesListPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreenAndShow(new AllEntitiesScreen(payload.entities, payload.currentIndex));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllAchievementsSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                AllAchievementsHUD.update(payload.currentAdvancement, payload.currentIndex, payload.total);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllAchievementsListPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreenAndShow(new AllAchievementsScreen(payload.advancements, payload.currentIndex));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TriviaQuestionPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreenAndShow(new net.kasax.challengecraft.client.screen.TriviaScreen(payload.question(), payload.answers(), payload.correctIndex()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(LockoutBingoSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> LockoutBingoClientState.update(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(LockoutBingoOpenScreenPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.boardScreen()) {
                    context.client().setScreenAndShow(new LockoutBingoBoardScreen());
                } else {
                    context.client().setScreenAndShow(new LockoutBingoTeamScreen());
                }
            });
        });
    }
}
