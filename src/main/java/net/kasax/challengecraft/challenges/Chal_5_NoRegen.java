package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.gamerules.GameRules;

/** Mirrors the challenge state into the natural regeneration gamerule. */
public class Chal_5_NoRegen {
    private static boolean active = false;
    public static void setActive(boolean on) { active = on; }
    public static boolean isActive() { return active; }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getGameRules()
                    .set(GameRules.NATURAL_HEALTH_REGENERATION, !active, server);
        });
    }
}
