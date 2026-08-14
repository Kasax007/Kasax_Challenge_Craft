package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.util.Mth;

/** Applies the configured server tick-rate multiplier and restores vanilla speed on disable. */
public class Chal_37_GameSpeed {
    private static final float BASE_TICK_RATE = 20.0f;

    private static boolean active = false;
    private static int multiplier = 1;
    private static float lastAppliedTickRate = BASE_TICK_RATE;

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            float desiredTickRate = getDesiredTickRate();
            var tickManager = server.tickRateManager();

            if (active) {
                if (tickManager.tickrate() != desiredTickRate || lastAppliedTickRate != desiredTickRate) {
                    tickManager.setTickRate(desiredTickRate);
                    lastAppliedTickRate = desiredTickRate;
                }
            } else if (lastAppliedTickRate != BASE_TICK_RATE) {
                if (tickManager.tickrate() != BASE_TICK_RATE) {
                    tickManager.setTickRate(BASE_TICK_RATE);
                }
                lastAppliedTickRate = BASE_TICK_RATE;
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    if (active) {
                        server.tickRateManager().updateJoiningPlayer(handler.player);
                    }
                })
        );

        ChallengeCraft.LOGGER.info("[Chal37] Registered tick callback");
    }

    public static void setActive(boolean isActive) {
        active = isActive;
        ChallengeCraft.LOGGER.info("[Chal37] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }

    public static int getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(int mult) {
        multiplier = Mth.clamp(mult, 1, 10);
    }

    private static float getDesiredTickRate() {
        return BASE_TICK_RATE * multiplier;
    }
}
