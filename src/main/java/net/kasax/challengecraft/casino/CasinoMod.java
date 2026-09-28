package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kasax.challengecraft.ChallengeCraft;

import java.util.Locale;

/** Common-side entry point of the casino, called from {@code ChallengeCraft.onInitialize}. */
public final class CasinoMod {
    private CasinoMod() {
    }

    public static void initialize() {
        CasinoSounds.initialize();
        CasinoRegistry.initialize();
        CasinoNet.register();
        CasinoGames.register();
        BlackjackRevival.register();
        CasinoCommands.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EmcValues.load();
            ChallengeCraft.LOGGER.info("[Casino] slot machine RTP (exact from reel strips): {} %",
                    String.format(Locale.ROOT, "%.4f", SlotMath.theoreticalRtp() * 100.0));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SlotGame.reset();
            CrashGame.reset();
            RouletteGame.reset();
            BlackjackRevival.reset();
        });
    }
}
