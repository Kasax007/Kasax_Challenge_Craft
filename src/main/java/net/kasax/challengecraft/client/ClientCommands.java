package net.kasax.challengecraft.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.client.screen.ChallengeSelectionScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ClientCommands implements ClientModInitializer {
    // a simple flag that we flip when /challenges is run
    private static boolean openOnNextTick = false;

    @Override
    public void onInitializeClient() {
        // Register the chat command
        ClientCommandRegistrationCallback.EVENT.register(this::register);
        ChallengeCraft.LOGGER.info("Challenge Craft Command loaded");

        // Register a tick listener to actually open the screen
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openOnNextTick) {
                openOnNextTick = false;
                ChallengeCraft.LOGGER.info("Opening ChallengeSelectionScreen on tick");
                client.setScreen(new ChallengeSelectionScreen());
            }
        });
    }

    private void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                          CommandRegistryAccess registryAccess) {
        dispatcher.register(
                ClientCommandManager.literal("challenges")
                        .executes(ctx -> {
                            if (ctx.getSource().getClient().player != null && ctx.getSource().getClient().player.hasPermissionLevel(2)) {
                                // just set our flag—don't call setScreen() here
                                ChallengeCraft.LOGGER.info("'/challenges' received, scheduling UI open");
                                openOnNextTick = true;
                                return 1;
                            } else {
                                ctx.getSource().sendFeedback(Text.literal("You do not have permission to use this command.").formatted(Formatting.RED));
                                return 0;
                            }
                        })
        );
    }
}
