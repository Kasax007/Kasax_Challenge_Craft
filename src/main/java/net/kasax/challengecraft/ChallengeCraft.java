package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.network.ChallengePacket;
import net.kasax.challengecraft.network.PacketHandler;
import net.kasax.challengecraft.network.PlayTimePacketHandler;
import net.kasax.challengecraft.network.PlayTimeSyncPacket;
import net.kasax.challengecraft.world.SkyblockChunkGenerator;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
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

		// 1) Tell Fabric about our SERVER‑BOUND channel:
		PayloadTypeRegistry.playC2S()
				.register(ChallengePacket.ID, ChallengePacket.CODEC);
		// inside onInitializeClient() or equivalent:
		PayloadTypeRegistry.playS2C().register(
				PlayTimeSyncPacket.ID,
				PlayTimeSyncPacket.CODEC
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
