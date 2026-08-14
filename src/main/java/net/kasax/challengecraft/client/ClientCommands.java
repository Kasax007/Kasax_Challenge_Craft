package net.kasax.challengecraft.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
// Fabric's own ClientCommands (26.2 renamed it from ClientCommandManager) collides with this
// class's name, so it is referenced fully qualified below rather than imported.
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.client.screen.ChallengeSelectionScreen;
import net.kasax.challengecraft.util.ModPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

/** Client-side command entry points for opening local mod screens. */
public class ClientCommands implements ClientModInitializer {
    private static boolean openOnNextTick = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(this::register);
        ChallengeCraft.LOGGER.info("Challenge Craft Command loaded");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openOnNextTick) {
                openOnNextTick = false;
                ChallengeCraft.LOGGER.info("Opening ChallengeSelectionScreen on tick");
                client.setScreenAndShow(new ChallengeSelectionScreen());
            }
        });
    }

    private void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                          CommandBuildContext registryAccess) {
        // Replays the first-run tutorial. Also how the tutorial gets tested without wiping the
        // progress file by hand.
        dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("challengecraft_tutorial")
                        .executes(ctx -> {
                            net.kasax.challengecraft.client.tutorial.TutorialManager.replay();
                            ctx.getSource().sendFeedback(Component.translatable("challengecraft.tutorial.replay")
                                    .withStyle(ChatFormatting.GREEN));
                            return 1;
                        }));

        dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("challenges")
                        .executes(ctx -> {
                            if (ctx.getSource().getClient().player != null && ModPermissions.isOp(ctx.getSource().getClient().player)) {
                                // Open on the next tick so the command handler does not mutate screens mid-dispatch.
                                ChallengeCraft.LOGGER.info("'/challenges' received, scheduling UI open");
                                openOnNextTick = true;
                                return 1;
                            } else {
                                ctx.getSource().sendFeedback(Component.translatable("challengecraft.command.no_permission").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                        })
        );
    }
}
