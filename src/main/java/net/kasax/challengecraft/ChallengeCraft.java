package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.network.*;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.kasax.challengecraft.util.ModPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the shared gameplay systems, commands, packets, and world hooks for the mod. */
public class ChallengeCraft implements ModInitializer {
	public static final String MOD_ID = "challengecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Level> LIMBO_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(MOD_ID, "limbo"));

	/**
	 * Headless self-test for the world-restart flow, driven by environment variables because
	 * console stdin does not reliably reach a server started this way on Windows.
	 *
	 * <p>{@code CHALLENGECRAFT_TEST_RESTART=1} makes the server print its seed and spawn shortly
	 * after boot and then trigger the same restart the "Save and Restart" button does. The rotation
	 * itself happens on the NEXT boot, so the harness starts the server a second time against the
	 * same directory and compares the two printed values.
	 *
	 * <p>This exists because the restart is the one flow the per-challenge boot sweep cannot reach:
	 * it only occurs on a world's second start, and it fails silently — a restart that reuses the
	 * old chunks logs nothing but success.
	 */
	private static void registerRestartSelfTest() {
		String mode = System.getenv("CHALLENGECRAFT_TEST_RESTART");
		boolean shouldRestart = mode != null && (mode.equals("1") || mode.equalsIgnoreCase("true"));

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(new
				net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick() {
			private int ticks = 0;
			private boolean fired = false;

			@Override
			public void onEndTick(net.minecraft.server.MinecraftServer server) {
				if (fired || System.getenv("CHALLENGECRAFT_TEST_RESTART") == null) return;
				ticks++;
				// A short delay so world load and challenge application have settled.
				if (ticks == 40) {
					ServerLevel overworld = server.overworld();
					// gameTime and the clock are printed too: a restart used to leave them at
					// whatever the seed randomiser happened to write, which is how a "fresh" world
					// could open in the middle of the night.
					// The generator class is the only honest answer to "is this actually a Skyblock
					// world?" — the challenge flag says what we intended, this says what the world IS.
					LOGGER.info("[TEST-WORLDINFO] seed={} spawn={} gameTime={} dayTime={} runTicks={} generator={}",
							overworld.getSeed(), overworld.getRespawnData().pos(),
							overworld.getGameTime(), overworld.getOverworldClockTime(),
							ChallengeSavedData.get(overworld).getRunTicks(),
							overworld.getChunkSource().getGenerator().getClass().getSimpleName());
					if (shouldRestart) {
						LOGGER.info("[TEST-RESTART] triggering restart via CHALLENGECRAFT_TEST_RESTART");
						net.kasax.challengecraft.network.ChallengeWorldRestarter.initiateRestart(server);
					}
					fired = true;
				}
			}
		});
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Challenge Craft loaded");

		ModItems.initialize();
		// Blocked barriers are a UI device, never a placeable block — see BlockedBarrierItem.
		net.kasax.challengecraft.util.BlockedBarrierItem.register();
		// Registering an entity type obliges the client to register a renderer for it —
		// see ChallengeCraftClient, or a dev client crashes on resource reload.
		net.kasax.challengecraft.entity.ModEntities.initialize();
		ChallengeManager.register();
		Chal_1_LevelItem.register();
		Chal_5_NoRegen.register();
		Chal_6_NoVillagerTrading.register();
		Chal_7_MaxHealthModify.register();
		Chal_8_NoCraftingTable.register();
		Chal_9_ExpWorldBorder.register();
		Chal_10_RandomItem.register();
		Chal_12_LimitedInventory.register();
		Chal_13_RandomEnchantment.register();
		Chal_14_RandomBlockDrops.register();
		Chal_15_RandomMobDrops.register();
		Chal_22_AllItems.register();
		Chal_23_AllEntities.register();
		Chal_25_DamageWorldBorder.register();
		Chal_26_AllAchievements.register();
		Chal_27_NoArmor.register();
		Chal_28_WalkDamage.register();
		Chal_36_TriviaChallenge.register();
		Chal_37_GameSpeed.register();
		Chal_38_ChunkHunt.register();
		Chal_39_NoFood.register();
		Chal_40_LockoutBingo.register();
		Chal_42_RandomMobSpawn.register();
		Chal_43_OnlyDown.register();
		Chal_44_ProgressiveBlockDrops.register();
		Chal_45_ForceItemBattle.register();
		net.kasax.challengecraft.challenges.Chal_46_Dice.register();
		net.kasax.challengecraft.data.BlockSurvey.register();
		net.kasax.challengecraft.data.RegistryIdAudit.register();
		LevelXpListener.register();
		net.kasax.challengecraft.block.InfiniteChestRegistry.initialize();

		registerRestartSelfTest();

		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("challengecraft_skip_item")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						Chal_22_AllItems.skipItem(context.getSource().getServer(), 1);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_item.single"), true);
						return 1;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_22_AllItems.skipItem(context.getSource().getServer(), amount);
								context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_item.multiple", amount), true);
								return 1;
							}))
			);

			dispatcher.register(Commands.literal("challengecraft_all_items_list")
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						ChallengeSavedData data = ChallengeSavedData.get(player.level().getServer().overworld());
						if (Chal_22_AllItems.isActive()) {
							ServerPlayNetworking.send(player, new AllItemsListPacket(data.getAllItemsOrder(), data.getAllItemsIndex()));
						} else {
							context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.all_items.inactive"), false);
						}
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_skip_entity")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						Chal_23_AllEntities.skipEntity(context.getSource().getServer(), 1);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_entity.single"), true);
						return 1;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_23_AllEntities.skipEntity(context.getSource().getServer(), amount);
								context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_entity.multiple", amount), true);
								return 1;
							}))
			);

			dispatcher.register(Commands.literal("challengecraft_all_entities_list")
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						ChallengeSavedData data = ChallengeSavedData.get(player.level().getServer().overworld());
						if (Chal_23_AllEntities.isActive()) {
							ServerPlayNetworking.send(player, new net.kasax.challengecraft.network.AllEntitiesListPacket(data.getAllEntitiesOrder(), data.getAllEntitiesIndex()));
						} else {
							context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.all_entities.inactive"), false);
						}
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_skip_advancement")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						Chal_26_AllAchievements.skipAdvancement(context.getSource().getServer(), 1);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_advancement.single"), true);
						return 1;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_26_AllAchievements.skipAdvancement(context.getSource().getServer(), amount);
								context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_advancement.multiple", amount), true);
								return 1;
							}))
			);

			dispatcher.register(Commands.literal("challengecraft_all_advancements_list")
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						if (Chal_26_AllAchievements.isActive()) {
							Chal_26_AllAchievements.sendListToPlayer(player);
						} else {
							context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.all_advancements.inactive"), false);
						}
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_fib_teams")
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						ServerPlayNetworking.send(player, new ForceItemOpenScreenPacket());
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_fib_joker")
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						Chal_45_ForceItemBattle.useJoker(player);
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_fib_skip")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						Chal_45_ForceItemBattle.skipItem(player);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.fib_skip.done"), true);
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_fib_results")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						Chal_45_ForceItemBattle.triggerResults(context.getSource().getServer());
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.fib_results.done"), true);
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_fib_debug_solo")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						Chal_45_ForceItemBattle.startBattle(context.getSource().getServer(), player, true);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.fib_debug_solo.started"), true);
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_fib_restart")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						Chal_45_ForceItemBattle.restartBattle(context.getSource().getServer());
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.fib_restart.done"), true);
						return 1;
					}));

			// Test hook: triggers exactly the same restart path as the selection screen's
			// "Save and Restart" button, but from a command — so a headless dedicated server can
			// exercise the whole rotate/reseed/regenerate cycle without a client or a GUI click.
			// The restart is the one flow that cannot be verified by the boot sweep, because it only
			// happens on the SECOND boot of a world.
			dispatcher.register(Commands.literal("challengecraft_test_restart")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						var server = context.getSource().getServer();
						ServerLevel overworld = server.overworld();
						LOGGER.info("[TEST-RESTART] before: seed={} spawn={}",
								overworld.getSeed(), overworld.getRespawnData().pos());
						net.kasax.challengecraft.network.ChallengeWorldRestarter.initiateRestart(server);
						context.getSource().sendSuccess(
								() -> Component.literal("Restart triggered").withStyle(ChatFormatting.YELLOW), false);
						return 1;
					}));

			// Prints the two values a restart must change. The test harness greps for these.
			dispatcher.register(Commands.literal("challengecraft_test_worldinfo")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						ServerLevel overworld = context.getSource().getServer().overworld();
						LOGGER.info("[TEST-WORLDINFO] seed={} spawn={}",
								overworld.getSeed(), overworld.getRespawnData().pos());
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_verify_ids")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						var result = net.kasax.challengecraft.data.RegistryIdAudit.run(context.getSource().getServer());
						boolean ok = net.kasax.challengecraft.data.RegistryIdAudit.logReport(result);
						context.getSource().sendSuccess(() -> Component.translatable(
								ok ? "challengecraft.command.verify_ids.ok"
								   : "challengecraft.command.verify_ids.failed",
								result.checked(), result.problems().size())
								.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
						return ok ? 1 : 0;
					}));

			dispatcher.register(Commands.literal("challengecraft_block_survey")
					.requires(source -> ModPermissions.isOp(source))
					.then(Commands.literal("start").executes(context -> {
						net.kasax.challengecraft.data.BlockSurvey.start();
						context.getSource().sendSuccess(() -> Component.translatable(
								"challengecraft.command.block_survey.started",
								net.kasax.challengecraft.data.BlockSurvey.getCollectedCount()), true);
						return 1;
					}))
					.then(Commands.literal("stop").executes(context -> {
						net.kasax.challengecraft.data.BlockSurvey.stop();
						context.getSource().sendSuccess(() -> Component.translatable(
								"challengecraft.command.block_survey.stopped",
								net.kasax.challengecraft.data.BlockSurvey.getCollectedCount(),
								net.kasax.challengecraft.data.BlockSurvey.getTotalBlocksCounted()), true);
						return 1;
					}))
					.then(Commands.literal("status").executes(context -> {
						String state = net.kasax.challengecraft.data.BlockSurvey.isGenerating() ? "GENERATING"
								: (net.kasax.challengecraft.data.BlockSurvey.isActive() ? "ON" : "OFF");
						context.getSource().sendSuccess(() -> Component.translatable(
								"challengecraft.command.block_survey.status",
								state,
								net.kasax.challengecraft.data.BlockSurvey.getCollectedCount(),
								net.kasax.challengecraft.data.BlockSurvey.getTotalBlocksCounted(),
								net.kasax.challengecraft.data.BlockSurvey.getScannedChunkCount()), false);
						return 1;
					}))
					.then(Commands.literal("generate")
							.executes(context -> {
								net.kasax.challengecraft.data.BlockSurvey.startGenerate(context.getSource().getServer(), 16);
								context.getSource().sendSuccess(() -> Component.translatable(
										"challengecraft.command.block_survey.generating", 16), true);
								return 1;
							})
							.then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
									.executes(context -> {
										int radius = IntegerArgumentType.getInteger(context, "radius");
										net.kasax.challengecraft.data.BlockSurvey.startGenerate(context.getSource().getServer(), radius);
										context.getSource().sendSuccess(() -> Component.translatable(
												"challengecraft.command.block_survey.generating", radius), true);
										return 1;
									}))));

			dispatcher.register(Commands.literal("challengecraft_progressive_blocks_list")
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						ChallengeSavedData data = ChallengeSavedData.get(player.level().getServer().overworld());
						if (Chal_44_ProgressiveBlockDrops.isActive()) {
							ServerPlayNetworking.send(player, new net.kasax.challengecraft.network.ProgressiveBlocksListPacket(
									data.getProgressiveBlocksOrder(), data.getProgressiveBlocksIndex()));
						} else {
							context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.progressive_blocks.inactive"), false);
						}
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_skip_block")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						Chal_44_ProgressiveBlockDrops.skipBlock(context.getSource().getServer(), 1);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_block.single"), true);
						return 1;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_44_ProgressiveBlockDrops.skipBlock(context.getSource().getServer(), amount);
								context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.skip_block.multiple", amount), true);
								return 1;
							}))
			);

			// Console-drivable equivalent of the /challenges screen's Save and Save-and-Restart
			// buttons. Those are the only ways to change challenges mid-game, and being GUI-only made
			// the "deselect Skyblock, restart, still Skyblock" report impossible to reproduce headlessly
			// — the per-challenge harness forces its selection through an environment variable on every
			// boot, which is exactly the thing under test. Takes a comma-separated id list, or "none".
			dispatcher.register(Commands.literal("challengecraft_debug_set_challenges")
					.requires(source -> ModPermissions.isOp(source))
					.then(Commands.argument("ids", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
							.executes(context -> {
								String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "ids").trim();
								java.util.List<Integer> ids = new java.util.ArrayList<>();
								if (!raw.isEmpty() && !raw.equalsIgnoreCase("none")) {
									for (String part : raw.split("[,\s]+")) {
										try {
											ids.add(Integer.parseInt(part.trim()));
										} catch (NumberFormatException ignored) {
											context.getSource().sendFailure(Component.literal("Not a challenge id: " + part));
											return 0;
										}
									}
								}
								var server = context.getSource().getServer();
								ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
								data.setActive(ids);
								ChallengeManager.applyAll(server);
								LOGGER.info("[DEBUG] active challenges set to {}", ids);
								context.getSource().sendSuccess(() -> Component.literal("Active challenges: " + ids), true);
								return 1;
							})));

			dispatcher.register(Commands.literal("challengecraft_debug_restart")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						ChallengeWorldRestarter.initiateRestart(context.getSource().getServer());
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_lockout_debug_solo")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						Chal_40_LockoutBingo.startSoloDebugRun(player);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.lockout_debug_solo.started"), true);
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_lockout_debug_claim")
					.requires(source -> ModPermissions.isOp(source))
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						int claimed = Chal_40_LockoutBingo.debugClaimTiles(player, 1);
						if (claimed > 0) {
							context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.lockout_debug_claim.result", claimed), true);
						} else {
							context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.lockout_debug_claim.none"), false);
						}
						return claimed;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(1, 25))
							.executes(context -> {
								ServerPlayer player = context.getSource().getPlayerOrException();
								int amount = IntegerArgumentType.getInteger(context, "amount");
								int claimed = Chal_40_LockoutBingo.debugClaimTiles(player, amount);
								if (claimed > 0) {
									context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.lockout_debug_claim.result", claimed), true);
								} else {
									context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.lockout_debug_claim.none"), false);
								}
								return claimed;
							}))
			);
		});

		PayloadTypeRegistry.serverboundPlay()
				.register(ChallengePacket.ID, ChallengePacket.CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(ClientXpSyncPacket.ID, ClientXpSyncPacket.CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(TriviaAnswerPacket.ID, TriviaAnswerPacket.CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(net.kasax.challengecraft.network.InfiniteChestClickPayload.ID, net.kasax.challengecraft.network.InfiniteChestClickPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(LockoutBingoActionPacket.ID, LockoutBingoActionPacket.CODEC);
		PayloadTypeRegistry.serverboundPlay()
				.register(ForceItemActionPacket.ID, ForceItemActionPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(
				ChallengeSyncPacket.ID,
				ChallengeSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				net.kasax.challengecraft.network.DiceSyncPacket.ID,
				net.kasax.challengecraft.network.DiceSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				StatsSyncPacket.ID,
				StatsSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				LevelSyncPacket.ID,
				LevelSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				PlayTimeSyncPacket.ID,
				PlayTimeSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				AllItemsSyncPacket.ID,
				AllItemsSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				AllItemsListPacket.ID,
				AllItemsListPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				net.kasax.challengecraft.network.AllEntitiesSyncPacket.ID,
				net.kasax.challengecraft.network.AllEntitiesSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				ChallengeRewardPacket.ID,
				ChallengeRewardPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				net.kasax.challengecraft.network.AllEntitiesListPacket.ID,
				net.kasax.challengecraft.network.AllEntitiesListPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				AllAchievementsSyncPacket.ID,
				AllAchievementsSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				AllAchievementsListPacket.ID,
				AllAchievementsListPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				net.kasax.challengecraft.network.RestartPendingPacket.ID,
				net.kasax.challengecraft.network.RestartPendingPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				TriviaQuestionPacket.ID,
				TriviaQuestionPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				net.kasax.challengecraft.network.InfiniteChestSyncPayload.ID,
				net.kasax.challengecraft.network.InfiniteChestSyncPayload.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				LockoutBingoSyncPacket.ID,
				LockoutBingoSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				ProgressiveBlocksSyncPacket.ID,
				ProgressiveBlocksSyncPacket.CODEC
		);

		PayloadTypeRegistry.clientboundPlay().register(
				net.kasax.challengecraft.network.ProgressiveBlocksListPacket.ID,
				net.kasax.challengecraft.network.ProgressiveBlocksListPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				ForceItemSyncPacket.ID,
				ForceItemSyncPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				ForceItemResultsPacket.ID,
				ForceItemResultsPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				ForceItemOpenScreenPacket.ID,
				ForceItemOpenScreenPacket.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				LockoutBingoOpenScreenPacket.ID,
				LockoutBingoOpenScreenPacket.CODEC
		);

		PacketHandler.register();
		PlayTimePacketHandler.registerServer();

		Registry.register(
				BuiltInRegistries.CHUNK_GENERATOR,
				Identifier.fromNamespaceAndPath("challengecraft", "skyblock_chunk_generator"),
				SkyblockChunkGenerator.MAP_CODEC
		);

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			net.kasax.challengecraft.network.ChallengeWorldRestarter.handlePlayerTeleport(server);
			server.execute(() -> sendProgressCommandReminders(handler.player));
		});
	}

	private static void sendProgressCommandReminders(ServerPlayer player) {
		ChallengeSavedData data = ChallengeSavedData.get(player.level().getServer().overworld());

		if (data.getActive().contains(22)) {
			player.sendSystemMessage(Component.translatable("challengecraft.command.all_items.reminder")
					.withStyle(ChatFormatting.GOLD));
		}
		if (data.getActive().contains(23)) {
			player.sendSystemMessage(Component.translatable("challengecraft.command.all_entities.reminder")
					.withStyle(ChatFormatting.GOLD));
		}
		if (data.getActive().contains(26)) {
			player.sendSystemMessage(Component.translatable("challengecraft.command.all_advancements.reminder")
					.withStyle(ChatFormatting.GOLD));
		}
		if (data.getActive().contains(44)) {
			player.sendSystemMessage(Component.translatable("challengecraft.command.progressive_blocks.reminder")
					.withStyle(ChatFormatting.GOLD));
		}
	}
}
