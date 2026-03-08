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
                Chal_1_LevelItem.setActive(false);
                Chal_5_NoRegen.setActive(false);
                Chal_6_NoVillagerTrading.setActive(false);
                Chal_7_MaxHealthModify.setActive(false);
                Chal_8_NoCraftingTable.setActive(false);
                Chal_9_ExpWorldBorder.setActive(false);
                Chal_10_RandomItem.setActive(false);
                Chal_12_LimitedInventory.setActive(false);
                Chal_13_RandomEnchantment.setActive(false);
                Chal_14_RandomBlockDrops.setActive(false);
                Chal_15_RandomMobDrops.setActive(false);
                Chal_17_WalkRandomItem.setActive(false);
                Chal_18_DamageRandomItem.setActive(false);
                Chal_19_MinePotionEffect.setActive(false);
                Chal_20_RandomizedCrafting.setActive(false);
                Chal_21_Hardcore.setActive(false);
                Chal_22_AllItems.setActive(false);
                Chal_23_AllEntities.setActive(false);
                Chal_24_MobHealthMultiply.setActive(false);
                Chal_25_DamageWorldBorder.setActive(false);
                Chal_26_AllAchievements.setActive(false);
                AllItemsHUD.setActive(false);
                AllEntitiesHUD.setActive(false);
                AllAchievementsHUD.setActive(false);

                for (int id : payload.active) {
                    switch (id) {
                        case 1 -> Chal_1_LevelItem.setActive(true);
                        case 5 -> Chal_5_NoRegen.setActive(true);
                        case 6 -> Chal_6_NoVillagerTrading.setActive(true);
                        case 7 -> Chal_7_MaxHealthModify.setActive(true);
                        case 8 -> Chal_8_NoCraftingTable.setActive(true);
                        case 9 -> Chal_9_ExpWorldBorder.setActive(true);
                        case 10 -> Chal_10_RandomItem.setActive(true);
                        case 11 -> Chal_11_SkyblockWorld.setActive(true);
                        case 12 -> Chal_12_LimitedInventory.setActive(true);
                        case 13 -> Chal_13_RandomEnchantment.setActive(true);
                        case 14 -> Chal_14_RandomBlockDrops.setActive(true);
                        case 15 -> Chal_15_RandomMobDrops.setActive(true);
                        case 17 -> Chal_17_WalkRandomItem.setActive(true);
                        case 18 -> Chal_18_DamageRandomItem.setActive(true);
                        case 19 -> Chal_19_MinePotionEffect.setActive(true);
                        case 20 -> Chal_20_RandomizedCrafting.setActive(true);
                        case 21 -> Chal_21_Hardcore.setActive(true);
                        case 22 -> {
                            Chal_22_AllItems.setActive(true);
                            AllItemsHUD.setActive(true);
                        }
                        case 23 -> {
                            Chal_23_AllEntities.setActive(true);
                            AllEntitiesHUD.setActive(true);
                        }
                        case 24 -> Chal_24_MobHealthMultiply.setActive(true);
                        case 25 -> Chal_25_DamageWorldBorder.setActive(true);
                        case 26 -> {
                            Chal_26_AllAchievements.setActive(true);
                            AllAchievementsHUD.setActive(true);
                        }
                    }
                }

                net.kasax.challengecraft.ChallengeCraftClient.LAST_CHOSEN = payload.active;
                net.kasax.challengecraft.ChallengeCraftClient.SELECTED_PERKS = payload.perks;
                net.kasax.challengecraft.ChallengeCraftClient.SELECTED_MAX_HEARTS = payload.maxHearts;
                net.kasax.challengecraft.ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = payload.limitedInventorySlots;
                net.kasax.challengecraft.ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = payload.mobHealthMultiplier;
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
    }
}
