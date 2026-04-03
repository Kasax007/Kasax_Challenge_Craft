package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.util.math.MathHelper;

public class Chal_37_GameSpeed {
    private static final float BASE_TICK_RATE = 20.0f;

    private static boolean active = false;
    private static int multiplier = 1;
    private static float lastAppliedTickRate = BASE_TICK_RATE;

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            float desiredTickRate = getDesiredTickRate();
            var tickManager = server.getTickManager();

            if (active) {
                if (tickManager.getTickRate() != desiredTickRate || lastAppliedTickRate != desiredTickRate) {
                    tickManager.setTickRate(desiredTickRate);
                    lastAppliedTickRate = desiredTickRate;
                }
            } else if (lastAppliedTickRate != BASE_TICK_RATE) {
                if (tickManager.getTickRate() != BASE_TICK_RATE) {
                    tickManager.setTickRate(BASE_TICK_RATE);
                }
                lastAppliedTickRate = BASE_TICK_RATE;
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    if (active) {
                        server.getTickManager().sendPackets(handler.player);
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
        multiplier = MathHelper.clamp(mult, 1, 10);
    }

    private static float getDesiredTickRate() {
        return BASE_TICK_RATE * multiplier;
    }
}
