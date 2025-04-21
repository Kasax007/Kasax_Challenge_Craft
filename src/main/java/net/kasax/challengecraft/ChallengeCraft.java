// src/main/java/net/kasax/challengecraft/ChallengeCraft.java
package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.kasax.challengecraft.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChallengeCraft implements ModInitializer {
	public static final String MOD_ID = "challengecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Challenge Craft loaded");

		// 1) Register the C2S payload type and its CODEC:
		PayloadTypeRegistry.playC2S()
				.register(ChallengePacket.ID, ChallengePacket.CODEC);

		// 2) Now it's safe to hook up the handler:
		PacketHandler.register();

		// Only on world‐create for SP: check if our data is missing before writing
		ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
			ServerWorld overworld = server.getOverworld();
			PersistentStateManager mgr = overworld.getPersistentStateManager();

			// mgr.get(...) returns null if no .dat file existed
			if (mgr.get(ChallengeSavedData.TYPE) == null) {
				// first time: create & write our LAST_CHOSEN value
				ChallengeSavedData.get(overworld)
						.setSelectedChallenge(ChallengeCraftClient.LAST_CHOSEN);
			}
		});
	}
}
