package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChallengeCraft implements ModInitializer {
	public static final String MOD_ID = "challengecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Challenge Craft loaded");

		// register items, commands, world‐load hooks, etc.
		ModItems.initialize();
		ChallengeManager.register();
		//ClientCommands.register();
		Chal_1_LevelItem.register();
		//Chal_2_NoBlockDrops.register();
		//Chal_3_NoMobDrops.register();
		//Chal_4_NoChestLoot.register();
		Chal_5_NoRegen.register();
		Chal_6_NoVillagerTrading.register();
		Chal_7_MaxHealthModify.register();
		Chal_8_NoCraftingTable.register();
		Chal_9_ExpWorldBorder.register();
		Chal_10_RandomItem.register();
		//Chal_11_SkyblockWorld.register();
		Chal_12_LimitedInventory.register();
		Chal_13_RandomEnchantment.register();
		Chal_14_RandomBlockDrops.register();
		Chal_15_RandomMobDrops.register();
		Chal_22_AllItems.register();
		Chal_23_AllEntities.register();
		Chal_25_DamageWorldBorder.register();
		LevelXpListener.register();

		// Register Hidden Skip Command
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("challengecraft_skip_item")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_22_AllItems.skipItem(context.getSource().getServer(), 1);
						context.getSource().sendFeedback(() -> Text.literal("Skipped current item."), true);
						return 1;
					})
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_22_AllItems.skipItem(context.getSource().getServer(), amount);
								context.getSource().sendFeedback(() -> Text.literal("Skipped " + amount + " items."), true);
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
							context.getSource().sendFeedback(() -> Text.literal("All Items challenge is not active."), false);
						}
						return 1;
					}));

			dispatcher.register(CommandManager.literal("challengecraft_skip_entity")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						Chal_23_AllEntities.skipEntity(context.getSource().getServer(), 1);
						context.getSource().sendFeedback(() -> Text.literal("Skipped current entity."), true);
						return 1;
					})
					.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
							.executes(context -> {
								int amount = IntegerArgumentType.getInteger(context, "amount");
								Chal_23_AllEntities.skipEntity(context.getSource().getServer(), amount);
								context.getSource().sendFeedback(() -> Text.literal("Skipped " + amount + " entities."), true);
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
							context.getSource().sendFeedback(() -> Text.literal("All Entities challenge is not active."), false);
						}
						return 1;
					}));
		});

		// 1) Tell Fabric about our SERVER‑BOUND channel:
		PayloadTypeRegistry.playC2S()
				.register(ChallengePacket.ID, ChallengePacket.CODEC);
		PayloadTypeRegistry.playS2C().register(
				ChallengeSyncPacket.ID,
				ChallengeSyncPacket.CODEC
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

		// 2) Now hook up the handler:
		PacketHandler.register();
		PlayTimePacketHandler.register();

		// Chunk Generator Register
		Registry.register(
				Registries.CHUNK_GENERATOR,
				Identifier.of("challengecraft", "skyblock_chunk_generator"),
				SkyblockChunkGenerator.MAP_CODEC
		);

	}
}
