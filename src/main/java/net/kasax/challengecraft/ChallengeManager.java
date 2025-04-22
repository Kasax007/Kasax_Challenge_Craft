package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static net.kasax.challengecraft.ChallengeCraft.MOD_ID;

public class ChallengeManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            applyTo(world);
        });
    }

    public static void applyAll(ServerWorld world) {
        LOGGER.info("ChallengeManager.applyAll: re‑applying");
        applyTo(world);
    }

    private static void applyTo(ServerWorld world) {
        ChallengeSavedData data = ChallengeSavedData.get(world);
        List<Integer> saved = data.getActive();

        // If this is a fresh world (we only see the default [1]),
        // and the client had selected something else, seed now:
        if (saved.size() == 1 && saved.get(0) == 1
                && !ChallengeCraftClient.LAST_CHOSEN.equals(List.of(1))) {
            LOGGER.info("ChallengeManager: seeding from client LAST_CHOSEN {}", ChallengeCraftClient.LAST_CHOSEN);
            data.setActive(List.copyOf(ChallengeCraftClient.LAST_CHOSEN));
            saved = data.getActive();
        }

        // turn everything off
        LOGGER.info("ChallengeManager: turning all challenges OFF");
        Challenge1Handler.setActive(false);
        //Challenge2Handler.setActive(false);
        //Challenge3Handler.setActive(false);
        //Challenge4Handler.setActive(false);
        //Challenge5Handler.setActive(false);

        // read+apply exactly what’s in the now‐seeded state
        LOGGER.info("ChallengeManager: got actives → {}", saved);
        for (int id : saved) {
            switch (id) {
                case 1 -> { Challenge1Handler.setActive(true); LOGGER.info("Challenge 1 ON"); }
               // case 2 -> { Challenge2Handler.setActive(true); LOGGER.info("Challenge 2 ON"); }
               // case 3 -> { Challenge3Handler.setActive(true); LOGGER.info("Challenge 3 ON"); }
               // case 4 -> { Challenge4Handler.setActive(true); LOGGER.info("Challenge 4 ON"); }
               // case 5 -> { Challenge5Handler.setActive(true); LOGGER.info("Challenge 5 ON"); }
                default -> LOGGER.warn("Unknown challenge id {}", id);
            }
        }
    }
}
