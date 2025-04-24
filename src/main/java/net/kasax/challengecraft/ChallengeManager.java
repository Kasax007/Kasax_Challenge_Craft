package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.world.ServerWorld;
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
        ChallengeSavedData data = ChallengeSavedData.get(world);
        List<Integer> saved = data.getActive();

        // First boot-up seeding from client choice
        if (saved.size() == 1 && saved.get(0) == 1
                && !ChallengeCraftClient.LAST_CHOSEN.equals(List.of(1))) {
            LOGGER.info("ChallengeManager: seeding from client LAST_CHOSEN {}", ChallengeCraftClient.LAST_CHOSEN);
            data.setActive(List.copyOf(ChallengeCraftClient.LAST_CHOSEN));
            saved = data.getActive();
        }

        // Turn everything off
        LOGGER.info("ChallengeManager: turning all challenges OFF");
        Chal_1_LevelItem       .setActive(false);
        Chal_2_NoBlockDrops    .setActive(false);
        Chal_3_NoMobDrops      .setActive(false);
        Chal_4_Hardcore        .setActive(false);
        Chal_5_NoRegen         .setActive(false);
        Chal_6_NoVillagerTrading.setActive(false);

        // Turn on only those in the saved list
        LOGGER.info("ChallengeManager: got actives → {}", saved);
        for (int id : saved) {
            switch (id) {
                case 1 -> { Chal_1_LevelItem       .setActive(true); LOGGER.info("Challenge 1 ON"); }
                case 2 -> { Chal_2_NoBlockDrops    .setActive(true); LOGGER.info("Challenge 2 ON"); }
                case 3 -> { Chal_3_NoMobDrops      .setActive(true); LOGGER.info("Challenge 3 ON"); }
                case 4 -> { Chal_4_Hardcore        .setActive(true); LOGGER.info("Challenge 4 ON"); }
                case 5 -> { Chal_5_NoRegen         .setActive(true); LOGGER.info("Challenge 5 ON"); }
                case 6 -> { Chal_6_NoVillagerTrading.setActive(true); LOGGER.info("Challenge 6 ON"); }
                default -> LOGGER.warn("Unknown challenge id {}", id);
            }
        }
        // now *after* you’ve turned your Chal_2 and Chal_3 on/off, do:
        var rules = world.getGameRules();
        var tileDropsRule = rules.get(GameRules.DO_TILE_DROPS);
        var mobLootRule  = rules.get(GameRules.DO_MOB_LOOT);

        // disable block drops if Challenge 2 is active, else enable them
        tileDropsRule.set(!Chal_2_NoBlockDrops.isActive(), world.getServer());

        // disable mob loot if Challenge 3 is active, else enable it
        mobLootRule.set(!Chal_3_NoMobDrops.isActive(), world.getServer());
    }
}
