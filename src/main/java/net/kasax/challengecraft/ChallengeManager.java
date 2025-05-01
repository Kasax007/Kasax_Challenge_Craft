package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ChallengeManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(ChallengeCraft.MOD_ID);

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            // 1) pull your saved data
            var data = ChallengeSavedData.get(world);

            // 2) if challenge 7 is in the saved “active” list, restore its slider value:
            if (data.getActive().contains(7)) {
                int savedTicks = data.getMaxHeartsTicks();        // 1…20
                float hearts   = savedTicks * 0.5f;               // 0.5–10.0
                Chal_7_MaxHealthModify.setMaxHearts(hearts);
                LOGGER.info("[Manager] restored Chal7 maxHearts = {} hearts ({} ticks)",
                        hearts, savedTicks);
            }
            if (data.getActive().contains(12)) {
                int saved = data.getLimitedInventorySlots();
                Chal_12_LimitedInventory.setLimitedSlots(saved);
                ChallengeCraft.LOGGER.info("[Manager] restored limited inventory slots = {}", saved);
            }

            // 3) now go ahead and apply *all* challenges according to the saved list
            applyTo(world);
        });
    }

    /**
     * Force-reapply all active challenges (e.g. after /reload).
     */
    public static void applyAll(ServerWorld world) {
        LOGGER.info("ChallengeManager.applyAll: re-applying");
        applyTo(world);
    }

    private static void applyTo(ServerWorld world) {
        // 1) load or create our per-world saved data
        ChallengeSavedData data = ChallengeSavedData.get(world);
        List<Integer> saved = data.getActive();

        // 2) On very first boot (when the only saved challenge is the dummy ID 1),
        //    seed both the chosen challenge IDs AND the chosen max-hearts slider
        if (saved.size() == 1 && saved.get(0) == 1
                && !ChallengeCraftClient.LAST_CHOSEN.equals(List.of(1))) {
            // SELECTED_MAX_HEARTS is the number of hearts chosen on the client (e.g. 4)
            int clientTicks = MathHelper.clamp(ChallengeCraftClient.SELECTED_MAX_HEARTS, 1, 20);
            float clientHearts = clientTicks * 0.5f;

            LOGGER.info("ChallengeManager: seeding from client LAST_CHOSEN {} (ticks={})",
                    ChallengeCraftClient.LAST_CHOSEN, clientTicks);

            // persist the client’s HP choice
            data.setMaxHeartsTicks(clientTicks);

            // persist the client’s challenge selection
            data.setActive(List.copyOf(ChallengeCraftClient.LAST_CHOSEN));

            // apply immediately into our runtime modifier
            Chal_7_MaxHealthModify.setMaxHearts(clientHearts);
            LOGGER.info("ChallengeManager: seeded maxHearts from client → {} ticks = {} hearts",
                    clientTicks, clientHearts);
            data.setLimitedInventorySlots(ChallengeCraftClient.SELECTED_LIMITED_INVENTORY);
            Chal_12_LimitedInventory.setLimitedSlots(ChallengeCraftClient.SELECTED_LIMITED_INVENTORY);
            ChallengeCraft.LOGGER.info("[Manager] seeded limited inventory slots = {}", ChallengeCraftClient.SELECTED_LIMITED_INVENTORY);


            // re-read the saved list
            saved = data.getActive();
        }

        // 3) Turn everything off
        LOGGER.info("ChallengeManager: turning all challenges OFF");
        Chal_1_LevelItem        .setActive(false);
        Chal_2_NoBlockDrops     .setActive(false);
        Chal_3_NoMobDrops       .setActive(false);
        Chal_4_NoChestLoot      .setActive(false);
        Chal_5_NoRegen          .setActive(false);
        Chal_6_NoVillagerTrading.setActive(false);
        Chal_7_MaxHealthModify  .setActive(false);
        Chal_8_NoCraftingTable  .setActive(false);
        Chal_9_ExpWorldBorder   .setActive(false);
        Chal_10_RandomItem      .setActive(false);
        Chal_11_SkyblockWorld   .setActive(false);
        Chal_12_LimitedInventory.setActive(false);

        // 4) Turn back on only the ones in the saved list
        LOGGER.info("ChallengeManager: got actives → {}", saved);
        for (int id : saved) {
            switch (id) {
                case 1  -> { Chal_1_LevelItem        .setActive(true); LOGGER.info("Challenge 1 ON"); }
                case 2  -> { Chal_2_NoBlockDrops     .setActive(true); LOGGER.info("Challenge 2 ON"); }
                case 3  -> { Chal_3_NoMobDrops       .setActive(true); LOGGER.info("Challenge 3 ON"); }
                case 4  -> { Chal_4_NoChestLoot      .setActive(true); LOGGER.info("Challenge 4 ON"); }
                case 5  -> { Chal_5_NoRegen          .setActive(true); LOGGER.info("Challenge 5 ON"); }
                case 6  -> { Chal_6_NoVillagerTrading.setActive(true); LOGGER.info("Challenge 6 ON"); }
                case 7  -> { Chal_7_MaxHealthModify  .setActive(true); LOGGER.info("Challenge 7 ON"); }
                case 8  -> { Chal_8_NoCraftingTable  .setActive(true); LOGGER.info("Challenge 8 ON"); }
                case 9  -> { Chal_9_ExpWorldBorder   .setActive(true); LOGGER.info("Challenge 9 ON"); }
                case 10 -> { Chal_10_RandomItem      .setActive(true); LOGGER.info("Challenge 10 ON"); }
                case 11 -> { Chal_11_SkyblockWorld   .setActive(true); LOGGER.info("Challenge 11 ON"); }
                case 12 -> { Chal_12_LimitedInventory.setActive(true); LOGGER.info("Challenge 12 ON"); }
                default -> LOGGER.warn("Unknown challenge id {}", id);
            }
        }

        // 5) Finally, update game rules for block/mob drops
        var rules         = world.getGameRules();
        var tileDropsRule = rules.get(GameRules.DO_TILE_DROPS);
        var mobLootRule   = rules.get(GameRules.DO_MOB_LOOT);

        tileDropsRule.set(!Chal_2_NoBlockDrops.isActive(), world.getServer());
        mobLootRule .set(!Chal_3_NoMobDrops    .isActive(), world.getServer());
    }

}
