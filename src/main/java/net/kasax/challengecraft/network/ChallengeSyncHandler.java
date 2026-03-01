package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.*;

public class ChallengeSyncHandler {
    public static void register() {
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

                for (int id : payload.active) {
                    switch (id) {
                        case 1 -> Chal_1_LevelItem.setActive(true);
                        case 5 -> Chal_5_NoRegen.setActive(true);
                        case 6 -> Chal_6_NoVillagerTrading.setActive(true);
                        case 7 -> Chal_7_MaxHealthModify.setActive(true);
                        case 8 -> Chal_8_NoCraftingTable.setActive(true);
                        case 9 -> Chal_9_ExpWorldBorder.setActive(true);
                        case 10 -> Chal_10_RandomItem.setActive(true);
                        case 12 -> Chal_12_LimitedInventory.setActive(true);
                        case 13 -> Chal_13_RandomEnchantment.setActive(true);
                        case 14 -> Chal_14_RandomBlockDrops.setActive(true);
                        case 15 -> Chal_15_RandomMobDrops.setActive(true);
                        case 17 -> Chal_17_WalkRandomItem.setActive(true);
                        case 18 -> Chal_18_DamageRandomItem.setActive(true);
                        case 19 -> Chal_19_MinePotionEffect.setActive(true);
                        case 20 -> Chal_20_RandomizedCrafting.setActive(true);
                        case 21 -> Chal_21_Hardcore.setActive(true);
                    }
                }
            });
        });
    }
}
