package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kasax.challengecraft.challenges.Challenge1Handler;
import net.kasax.challengecraft.client.ClientCommands;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.network.ChallengePacket;
import net.kasax.challengecraft.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ChallengeCraft implements ModInitializer {
	public static final String MOD_ID = "challengecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Challenge Craft loaded");

		// register items, commands, world‐load hooks, etc.
		ModItems.initialize();
		ChallengeManager.register();
		ClientCommands.register();
		Challenge1Handler.register();

		// 1) Tell Fabric about our SERVER‑BOUND channel:
		PayloadTypeRegistry.playC2S()
				.register(ChallengePacket.ID, ChallengePacket.CODEC);

		// 2) Now hook up the handler:
		PacketHandler.register();

		// On singleplayer world create, seed the file if missing
		ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
			ServerWorld overworld = server.getOverworld();
			PersistentStateManager mgr = overworld.getPersistentStateManager();
			if (mgr.get(ChallengeSavedData.TYPE) == null) {
				// first run => start with no challenges active
				ChallengeSavedData.get(overworld).setActive(List.of());
			}
		});
	}
}
