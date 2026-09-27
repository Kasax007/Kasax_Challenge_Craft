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
    private static net.minecraft.client.gui.screens.Screen pendingScreen = null;

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
            if (pendingScreen != null) {
                net.minecraft.client.gui.screens.Screen s = pendingScreen;
                pendingScreen = null;
                client.setScreenAndShow(s);
            }
        });
    }

    /**
     * A believable card for design work: a long run, a fat difficulty, eleven challenges and a
     * record, because a card that only ever shows two icons and a round number hides every layout
     * problem the real thing will have.
     */
    private static net.kasax.challengecraft.network.RunSummaryPacket demoSummary(boolean daily, boolean versus) {
        var kind = versus
                ? net.kasax.challengecraft.network.RunSummaryPacket.Kind.LOCKOUT_BINGO
                : net.kasax.challengecraft.network.RunSummaryPacket.Kind.DRAGON;
        java.util.List<Integer> ids = java.util.List.of(2, 5, 11, 21, 27, 29, 33, 35, 41, 43, 46);
        long ticks = 20L * (42 * 60 + 17);
        long epochDay = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        return new net.kasax.challengecraft.network.RunSummaryPacket(
                kind, ticks, 7.4, false, 740L, 3260L, 4000L, true, ids,
                versus ? "Team Rot" : "", versus ? 2 : 0, versus ? 4 : 0,
                daily, daily ? net.kasax.challengecraft.daily.DailyManager.todayIndex() : -1,
                daily ? epochDay : 0L);
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

        // Workaround, not a fix, and not for a bug in this mod: on some mod combinations item
        // textures fail to appear while shaders are on, and a plain F3+T does not clear it.
        // Taking the shader pack down and putting it straight back up makes Iris rebuild every
        // program and re-bind its textures, which does. Delete this command once the underlying
        // mod is fixed.
        dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("challengecraft_fix_textures")
                        .executes(ctx -> {
                            if (!net.kasax.challengecraft.client.ShaderCompat.irisPresent()) {
                                ctx.getSource().sendFeedback(
                                        Component.translatable("challengecraft.shaderfix.no_iris")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            boolean ran = net.kasax.challengecraft.client.ShaderCompat.rebuildShaderPipeline();
                            ctx.getSource().sendFeedback(Component
                                    .translatable(ran ? "challengecraft.shaderfix.done"
                                            : "challengecraft.shaderfix.not_needed")
                                    .withStyle(ran ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                            return ran ? 1 : 0;
                        }));

        // All Biomes (49): the checklist is already on the client (every find syncs the whole
        // list), so the screen opens without a round trip to the server.
        dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("challengecraft_all_biomes_list")
                        .executes(ctx -> {
                            if (!net.kasax.challengecraft.challenges.Chal_49_AllBiomes.isActive()
                                    || net.kasax.challengecraft.client.screen.AllBiomesHUD.all().isEmpty()) {
                                ctx.getSource().sendFeedback(Component.translatable("challengecraft.command.all_biomes.inactive")
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            pendingScreen = new net.kasax.challengecraft.client.screen.AllBiomesScreen();
                            return 1;
                        }));

        // Reopens the last run summary. Without this the card is a one-shot: miss it and the run
        // it describes is gone.
        dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("challengecraft_summary")
                        .executes(ctx -> {
                            if (net.kasax.challengecraft.network.RunSummaryHandler.last() == null) {
                                ctx.getSource().sendFeedback(
                                        Component.translatable("challengecraft.summary.none")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            pendingScreen = new net.kasax.challengecraft.client.screen.RunSummaryScreen(
                                    net.kasax.challengecraft.network.RunSummaryHandler.last());
                            return 1;
                        })
                        // Fake cards for looking at the layout without beating a dragon first.
                        // The design cannot be judged from source, and finishing a real run just to
                        // check a font size is not a workflow.
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("demo")
                                .executes(ctx -> {
                                    pendingScreen = new net.kasax.challengecraft.client.screen.RunSummaryScreen(
                                            demoSummary(false, false));
                                    return 1;
                                })
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("daily")
                                        .executes(ctx -> {
                                            pendingScreen = new net.kasax.challengecraft.client.screen.RunSummaryScreen(
                                                    demoSummary(true, false));
                                            return 1;
                                        }))
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("versus")
                                        .executes(ctx -> {
                                            pendingScreen = new net.kasax.challengecraft.client.screen.RunSummaryScreen(
                                                    demoSummary(false, true));
                                            return 1;
                                        }))));

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
