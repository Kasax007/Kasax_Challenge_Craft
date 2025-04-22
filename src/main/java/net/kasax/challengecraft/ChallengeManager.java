package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.kasax.challengecraft.challenges.Challenge1Handler;
//import net.kasax.challengecraft.challenges.Challenge2Handler;
//import net.kasax.challengecraft.challenges.Challenge3Handler;
//import net.kasax.challengecraft.challenges.Challenge4Handler;
//import net.kasax.challengecraft.challenges.Challenge5Handler;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.kasax.challengecraft.ChallengeCraft.MOD_ID;

public class ChallengeManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;

            // Deactivate _all_ challenges first
            Challenge1Handler.setActive(false);
            //Challenge2Handler.setActive(false);
            //Challenge3Handler.setActive(false);
            //Challenge4Handler.setActive(false);
            //Challenge5Handler.setActive(false);

            // Load the saved number and activate just that one
            int challenge = ChallengeSavedData.get(world).getSelectedChallenge();
            switch (challenge) {
                case 1 -> { Challenge1Handler.setActive(true);  LOGGER.info("Activated Challenge #1"); }
                //case 2 -> { Challenge2Handler.setActive(true);  LOGGER.info("Activated Challenge #2"); }
                //case 3 -> { Challenge3Handler.setActive(true);  LOGGER.info("Activated Challenge #3"); }
                //case 4 -> { Challenge4Handler.setActive(true);  LOGGER.info("Activated Challenge #4"); }
                //case 5 -> { Challenge5Handler.setActive(true);  LOGGER.info("Activated Challenge #5"); }
                default -> LOGGER.info("No challenge active");
            }
        });
    }
}
