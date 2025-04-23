package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.GameRules;

public class Chal_5_NoRegen {
    private static boolean active = false;
    public static void setActive(boolean on) { active = on; }

    static {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (active) {
                // turn off vanilla natural regen
                server.getGameRules()
                        .get(GameRules.NATURAL_REGENERATION)
                        .set(false, server);
            }
        });
    }
}
