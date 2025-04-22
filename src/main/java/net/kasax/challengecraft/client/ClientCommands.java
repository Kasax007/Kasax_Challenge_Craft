package net.kasax.challengecraft.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.kasax.challengecraft.client.screen.ChallengeSelectionScreen;
import net.minecraft.client.MinecraftClient;

public class ClientCommands {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("challenges").executes(c -> {
                MinecraftClient.getInstance().setScreen(new ChallengeSelectionScreen());
                return 1;
            }));
        });
    }
}
