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

    /** call once in ModInitializer */
    public static void register() {
        // hook world‐load
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;

            // first turn all off:
            Challenge1Handler.setActive(false);
            // Challenge2Handler.setActive(false);
            // … up to 5
            //Challenge5Handler.setActive(false);

            // then re‐activate from saved data
            List<Integer> actives = ChallengeSavedData.get(world).getActive();
            for (int id : actives) {
                switch (id) {
                    case 1 -> { Challenge1Handler.setActive(true); LOGGER.info("Challenge 1 ON"); }
                    //case 2 -> { Challenge2Handler.setActive(true); LOGGER.info("Challenge 2 ON"); }
                    //case 3 -> { Challenge3Handler.setActive(true); LOGGER.info("Challenge 3 ON"); }
                    //case 4 -> { Challenge4Handler.setActive(true); LOGGER.info("Challenge 4 ON"); }
                    //case 5 -> { Challenge5Handler.setActive(true); LOGGER.info("Challenge 5 ON"); }
                }
            }
        });
    }

    /** also handy to re‐apply immediately after a packet */
    public static void applyAll(ServerWorld world) {
        // flip off all
        register(); // that triggers the same LOAD logic, or you can inline the above
    }
}
