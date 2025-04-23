package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.GameRules;

public class Chal_5_NoRegen {
    private static boolean active = false;
    public static void setActive(boolean on) { active = on; }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getGameRules()
                    .get(GameRules.NATURAL_REGENERATION)
                    .set(!active, server);
        });
    }
}
