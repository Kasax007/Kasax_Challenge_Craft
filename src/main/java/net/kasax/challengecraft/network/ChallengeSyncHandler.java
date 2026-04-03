package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.client.screen.AllAchievementsHUD;
import net.kasax.challengecraft.client.screen.AllAchievementsScreen;
import net.kasax.challengecraft.client.screen.AllEntitiesHUD;
import net.kasax.challengecraft.client.screen.AllEntitiesScreen;
import net.kasax.challengecraft.client.screen.AllItemsHUD;
import net.kasax.challengecraft.client.screen.AllItemsScreen;
import net.kasax.challengecraft.client.screen.TimerOverlay;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.ChallengeCraftClient;

public class ChallengeSyncHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayTimeSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                TimerOverlay.setBasePlayTicks(payload.playTicks);
                //ChallengeCraft.LOGGER.info("→ synced playTicks = " + payload.playTicks);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChallengeSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                // Clear all active first (on client)
                ChallengeManager.setAllActive(false);
                AllItemsHUD.setActive(false);
                AllEntitiesHUD.setActive(false);
                AllAchievementsHUD.setActive(false);

                for (int id : payload.active) {
                    ChallengeManager.applyActiveFlag(id, null, null);
                    if (id == 22) AllItemsHUD.setActive(true);
                    if (id == 23) AllEntitiesHUD.setActive(true);
                    if (id == 26) AllAchievementsHUD.setActive(true);
                }

                ChallengeCraftClient.LAST_CHOSEN = payload.active;
                ChallengeCraftClient.SELECTED_PERKS = payload.perks;
                ChallengeCraftClient.SELECTED_MAX_HEARTS = payload.maxHearts;
                ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = payload.limitedInventorySlots;
                ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = payload.mobHealthMultiplier;
                ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER = payload.doubleTroubleMultiplier;
                ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER = payload.gameSpeedMultiplier;
                
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
                context.client().setScreen(new AllItemsScreen(payload.items, payload.currentIndex));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllEntitiesSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                AllEntitiesHUD.update(payload.currentEntity, payload.currentIndex, payload.totalEntities);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllEntitiesListPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new AllEntitiesScreen(payload.entities, payload.currentIndex));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllAchievementsSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                AllAchievementsHUD.update(payload.currentAdvancement, payload.currentIndex, payload.total);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AllAchievementsListPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new AllAchievementsScreen(payload.advancements, payload.currentIndex));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TriviaQuestionPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new net.kasax.challengecraft.client.screen.TriviaScreen(payload.question(), payload.answers(), payload.correctIndex()));
            });
        });
    }
}
