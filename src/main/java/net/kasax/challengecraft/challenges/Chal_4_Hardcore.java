package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.mixin.LevelInfoAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.SaveProperties;

public class Chal_4_Hardcore {
    private static boolean active = false;
    public static void setActive(boolean on) { active = on; }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (!active) return;

            SaveProperties props = server.getSaveProperties();

            // 1) flip the hardcore bit using our accessor
            ((LevelInfoAccessor)(Object)props.getLevelInfo()).setHardcore(true);

            // 2) re‐mark the level.dat dirty so Fabric will write it out
            props.setGameMode(props.getGameMode());

            // 3) re‐send the new world info (incl. hardcore) to every connected player
            PlayerManager pm = server.getPlayerManager();
            for (ServerPlayerEntity player : pm.getPlayerList()) {
                pm.sendWorldInfo(player, player.getServerWorld());
            }
        });
    }
}
