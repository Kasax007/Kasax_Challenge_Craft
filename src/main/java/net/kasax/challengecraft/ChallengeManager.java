package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.kasax.challengecraft.ChallengeCraft.MOD_ID;

public class ChallengeManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Call this once during mod init to hook world‐load. */
    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            // Only trigger on the Overworld:
            if (world.getRegistryKey() == World.OVERWORLD) {
                int challenge = ChallengeSavedData.get(world).getSelectedChallenge();
                applyChallenge(world, challenge);
            }
        });
    }

    private static void applyChallenge(ServerWorld world, int challenge) {
        switch (challenge) {
            case 1 -> applyChallengeOne(world);
            case 2 -> applyChallengeTwo(world);
            case 3 -> applyChallengeThree(world);
            case 4 -> applyChallengeFour(world);
            case 5 -> applyChallengeFive(world);
            default -> {/* no challenge */}
        }
    }

    private static void applyChallengeOne(ServerWorld world) {
        // TODO: your logic for challenge #1
        LOGGER.info("Challenge #1 activated!");
    }

    private static void applyChallengeTwo(ServerWorld world) {
        // TODO: your logic for challenge #2
        LOGGER.info("Challenge #2 activated!");
    }

    private static void applyChallengeThree(ServerWorld world) {
        // TODO: your logic for challenge #3
        LOGGER.info("Challenge #3 activated!");
    }

    private static void applyChallengeFour(ServerWorld world) {
        // TODO: your logic for challenge #4
        LOGGER.info("Challenge #4 activated!");
    }

    private static void applyChallengeFive(ServerWorld world) {
        // TODO: your logic for challenge #5
        LOGGER.info("Challenge #5 activated!");
    }
}
