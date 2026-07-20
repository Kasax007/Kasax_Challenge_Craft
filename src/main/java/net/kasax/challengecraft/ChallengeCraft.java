package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.network.*;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the shared gameplay systems, commands, packets, and world hooks for the mod. */
public class ChallengeCraft implements ModInitializer {
	public static final String MOD_ID = "challengecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final RegistryKey<World> LIMBO_KEY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "limbo"));

	@Override
	public void onInitialize() {
		LOGGER.info("Challenge Craft loaded");

		ModItems.initialize();
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
		net.kasax.challengecraft.data.BlockSurvey.register();
		LevelXpListener.register();
		net.kasax.challengecraft.block.InfiniteChestRegistry.initialize();

		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("challengecraft_skip_item")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_22_AllItems.skipItem(context.getSource().getServer(), 1);
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_item.single"), true);
						return 1;
					})
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_22_AllItems.skipItem(context.getSource().getServer(), amount);
								context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_item.multiple", amount), true);
								return 1;
							}))
			);

			dispatcher.register(CommandManager.literal("challengecraft_all_items_list")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						ChallengeSavedData data = ChallengeSavedData.get(player.getServer().getOverworld());
						if (Chal_22_AllItems.isActive()) {
							ServerPlayNetworking.send(player, new AllItemsListPacket(data.getAllItemsOrder(), data.getAllItemsIndex()));
						} else {
							context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.all_items.inactive"), false);
						}
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_skip_entity")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_23_AllEntities.skipEntity(context.getSource().getServer(), 1);
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_entity.single"), true);
						return 1;
					})
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_23_AllEntities.skipEntity(context.getSource().getServer(), amount);
								context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_entity.multiple", amount), true);
								return 1;
							}))
			);

			dispatcher.register(CommandManager.literal("challengecraft_all_entities_list")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						ChallengeSavedData data = ChallengeSavedData.get(player.getServer().getOverworld());
						if (Chal_23_AllEntities.isActive()) {
							ServerPlayNetworking.send(player, new net.kasax.challengecraft.network.AllEntitiesListPacket(data.getAllEntitiesOrder(), data.getAllEntitiesIndex()));
						} else {
							context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.all_entities.inactive"), false);
						}
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_skip_advancement")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_26_AllAchievements.skipAdvancement(context.getSource().getServer(), 1);
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_advancement.single"), true);
						return 1;
					})
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_26_AllAchievements.skipAdvancement(context.getSource().getServer(), amount);
								context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_advancement.multiple", amount), true);
								return 1;
							}))
			);

			dispatcher.register(CommandManager.literal("challengecraft_all_advancements_list")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						if (Chal_26_AllAchievements.isActive()) {
							Chal_26_AllAchievements.sendListToPlayer(player);
						} else {
							context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.all_advancements.inactive"), false);
						}
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_fib_teams")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						ServerPlayNetworking.send(player, new ForceItemOpenScreenPacket());
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_fib_joker")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						Chal_45_ForceItemBattle.useJoker(player);
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_fib_skip")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						Chal_45_ForceItemBattle.skipItem(player);
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.fib_skip.done"), true);
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_fib_results")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_45_ForceItemBattle.triggerResults(context.getSource().getServer());
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.fib_results.done"), true);
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_fib_debug_solo")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						Chal_45_ForceItemBattle.startBattle(context.getSource().getServer(), player, true);
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.fib_debug_solo.started"), true);
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_fib_restart")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_45_ForceItemBattle.restartBattle(context.getSource().getServer());
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.fib_restart.done"), true);
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_block_survey")
					.requires(source -> source.hasPermissionLevel(2))
					.then(CommandManager.literal("start").executes(context -> {
						net.kasax.challengecraft.data.BlockSurvey.start();
						context.getSource().sendFeedback(() -> Text.translatable(
								"challengecraft.command.block_survey.started",
								net.kasax.challengecraft.data.BlockSurvey.getCollectedCount()), true);
						return 1;
					}))
					.then(CommandManager.literal("stop").executes(context -> {
						net.kasax.challengecraft.data.BlockSurvey.stop();
						context.getSource().sendFeedback(() -> Text.translatable(
								"challengecraft.command.block_survey.stopped",
								net.kasax.challengecraft.data.BlockSurvey.getCollectedCount(),
								net.kasax.challengecraft.data.BlockSurvey.getTotalBlocksCounted()), true);
						return 1;
					}))
					.then(CommandManager.literal("status").executes(context -> {
						String state = net.kasax.challengecraft.data.BlockSurvey.isGenerating() ? "GENERATING"
								: (net.kasax.challengecraft.data.BlockSurvey.isActive() ? "ON" : "OFF");
						context.getSource().sendFeedback(() -> Text.translatable(
								"challengecraft.command.block_survey.status",
								state,
								net.kasax.challengecraft.data.BlockSurvey.getCollectedCount(),
								net.kasax.challengecraft.data.BlockSurvey.getTotalBlocksCounted(),
								net.kasax.challengecraft.data.BlockSurvey.getScannedChunkCount()), false);
						return 1;
					}))
					.then(CommandManager.literal("generate")
							.executes(context -> {
								net.kasax.challengecraft.data.BlockSurvey.startGenerate(context.getSource().getServer(), 16);
								context.getSource().sendFeedback(() -> Text.translatable(
										"challengecraft.command.block_survey.generating", 16), true);
								return 1;
							})
							.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 128))
									.executes(context -> {
										int radius = IntegerArgumentType.getInteger(context, "radius");
										net.kasax.challengecraft.data.BlockSurvey.startGenerate(context.getSource().getServer(), radius);
										context.getSource().sendFeedback(() -> Text.translatable(
												"challengecraft.command.block_survey.generating", radius), true);
										return 1;
									}))));

			dispatcher.register(CommandManager.literal("challengecraft_skip_block")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_44_ProgressiveBlockDrops.skipBlock(context.getSource().getServer(), 1);
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_block.single"), true);
						return 1;
					})
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_44_ProgressiveBlockDrops.skipBlock(context.getSource().getServer(), amount);
								context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.skip_block.multiple", amount), true);
								return 1;
							}))
			);

			dispatcher.register(CommandManager.literal("challengecraft_lockout_debug_solo")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						Chal_40_LockoutBingo.startSoloDebugRun(player);
						context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.lockout_debug_solo.started"), true);
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_lockout_debug_claim")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
						int claimed = Chal_40_LockoutBingo.debugClaimTiles(player, 1);
						if (claimed > 0) {
							context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.lockout_debug_claim.result", claimed), true);
						} else {
							context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.lockout_debug_claim.none"), false);
						}
						return claimed;
					})
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 25))
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
								int amount = IntegerArgumentType.getInteger(context, "amount");
								int claimed = Chal_40_LockoutBingo.debugClaimTiles(player, amount);
								if (claimed > 0) {
									context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.lockout_debug_claim.result", claimed), true);
								} else {
									context.getSource().sendFeedback(() -> Text.translatable("challengecraft.command.lockout_debug_claim.none"), false);
								}
								return claimed;
							}))
			);
		});

		PayloadTypeRegistry.playC2S()
				.register(ChallengePacket.ID, ChallengePacket.CODEC);
		PayloadTypeRegistry.playC2S()
				.register(ClientXpSyncPacket.ID, ClientXpSyncPacket.CODEC);
		PayloadTypeRegistry.playC2S()
				.register(TriviaAnswerPacket.ID, TriviaAnswerPacket.CODEC);
		PayloadTypeRegistry.playC2S()
				.register(net.kasax.challengecraft.network.InfiniteChestClickPayload.ID, net.kasax.challengecraft.network.InfiniteChestClickPayload.CODEC);
		PayloadTypeRegistry.playC2S()
				.register(LockoutBingoActionPacket.ID, LockoutBingoActionPacket.CODEC);
		PayloadTypeRegistry.playC2S()
				.register(ForceItemActionPacket.ID, ForceItemActionPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(
				ChallengeSyncPacket.ID,
				ChallengeSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				StatsSyncPacket.ID,
				StatsSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				LevelSyncPacket.ID,
				LevelSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				PlayTimeSyncPacket.ID,
				PlayTimeSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				AllItemsSyncPacket.ID,
				AllItemsSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				AllItemsListPacket.ID,
				AllItemsListPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				net.kasax.challengecraft.network.AllEntitiesSyncPacket.ID,
				net.kasax.challengecraft.network.AllEntitiesSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				ChallengeRewardPacket.ID,
				ChallengeRewardPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				net.kasax.challengecraft.network.AllEntitiesListPacket.ID,
				net.kasax.challengecraft.network.AllEntitiesListPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				AllAchievementsSyncPacket.ID,
				AllAchievementsSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				AllAchievementsListPacket.ID,
				AllAchievementsListPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				net.kasax.challengecraft.network.RestartPendingPacket.ID,
				net.kasax.challengecraft.network.RestartPendingPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				TriviaQuestionPacket.ID,
				TriviaQuestionPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				net.kasax.challengecraft.network.InfiniteChestSyncPayload.ID,
				net.kasax.challengecraft.network.InfiniteChestSyncPayload.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				LockoutBingoSyncPacket.ID,
				LockoutBingoSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				ProgressiveBlocksSyncPacket.ID,
				ProgressiveBlocksSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				ForceItemSyncPacket.ID,
				ForceItemSyncPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				ForceItemResultsPacket.ID,
				ForceItemResultsPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				ForceItemOpenScreenPacket.ID,
				ForceItemOpenScreenPacket.CODEC
		);
		PayloadTypeRegistry.playS2C().register(
				LockoutBingoOpenScreenPacket.ID,
				LockoutBingoOpenScreenPacket.CODEC
		);

		PacketHandler.register();
		PlayTimePacketHandler.registerServer();

		Registry.register(
				Registries.CHUNK_GENERATOR,
				Identifier.of("challengecraft", "skyblock_chunk_generator"),
				SkyblockChunkGenerator.MAP_CODEC
		);

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			net.kasax.challengecraft.network.ChallengeWorldRestarter.handlePlayerTeleport(server);
			server.execute(() -> sendProgressCommandReminders(handler.player));
		});
	}

	private static void sendProgressCommandReminders(ServerPlayerEntity player) {
		ChallengeSavedData data = ChallengeSavedData.get(player.getServer().getOverworld());

		if (data.getActive().contains(22)) {
			player.sendMessage(Text.translatable("challengecraft.command.all_items.reminder")
					.formatted(Formatting.GOLD), false);
		}
		if (data.getActive().contains(23)) {
			player.sendMessage(Text.translatable("challengecraft.command.all_entities.reminder")
					.formatted(Formatting.GOLD), false);
		}
		if (data.getActive().contains(26)) {
			player.sendMessage(Text.translatable("challengecraft.command.all_advancements.reminder")
					.formatted(Formatting.GOLD), false);
		}
	}
}
