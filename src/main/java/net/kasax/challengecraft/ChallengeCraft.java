package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.network.*;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
		LevelXpListener.register();
		net.kasax.challengecraft.block.InfiniteChestRegistry.initialize();

		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("challengecraft_skip_item")
					.requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
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
					.requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
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
					.requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
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

			dispatcher.register(Commands.literal("challengecraft_lockout_debug_solo")
					.requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						Chal_40_LockoutBingo.startSoloDebugRun(player);
						context.getSource().sendSuccess(() -> Component.translatable("challengecraft.command.lockout_debug_solo.started"), true);
						return 1;
					}));

			dispatcher.register(Commands.literal("challengecraft_lockout_debug_claim")
					.requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
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
		PayloadTypeRegistry.clientboundPlay().register(
				ChallengeSyncPacket.ID,
				ChallengeSyncPacket.CODEC
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
					.withStyle(ChatFormatting.GOLD), false);
		}
		if (data.getActive().contains(23)) {
			player.sendSystemMessage(Component.translatable("challengecraft.command.all_entities.reminder")
					.withStyle(ChatFormatting.GOLD), false);
		}
		if (data.getActive().contains(26)) {
			player.sendSystemMessage(Component.translatable("challengecraft.command.all_advancements.reminder")
					.withStyle(ChatFormatting.GOLD), false);
		}
	}
}
