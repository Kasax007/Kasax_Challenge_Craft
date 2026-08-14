package net.kasax.challengecraft.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Advances the world's run clock and syncs it to every player, so all HUDs show the same time. */
public class PlayTimePacketHandler {
    private static long lastSyncMillis = 0L;

    public static void registerServer() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // One tick of the world is one tick of the run — counted here rather than read from any
            // player's statistics, so it keeps going while nobody is online and is identical for
            // everyone who is. END_SERVER_TICK fires exactly once per server tick.
            ChallengeSavedData.get(server.overworld()).tickRun();

            // Broadcast on wall time, not tick count, so the HUD clock stays honest if TPS drops.
            long now = System.currentTimeMillis();
            if (lastSyncMillis == 0L) {
                lastSyncMillis = now;
            }

            if (now - lastSyncMillis >= 1000L) {
                lastSyncMillis = now;
                int runTicks = ChallengeTimeUtil.getDisplayRunTicks(server);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, new PlayTimeSyncPacket(runTicks));
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register(
                (ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) -> {
                    // A joining player adopts the run already in progress instead of starting at zero.
                    server.execute(() -> sender.sendPacket(
                            new PlayTimeSyncPacket(ChallengeTimeUtil.getDisplayRunTicks(server))));
                }
        );
    }
}
