// src/main/java/net/kasax/challengecraft/ChallengeCraft.java
package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.kasax.challengecraft.network.PacketHandler;
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

		// Then for SP integrated worlds, after the server's started:
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// Write the last chosen challenge into the Overworld state
			ChallengeSavedData.get(server.getOverworld())
					.setSelectedChallenge(ChallengeCraftClient.LAST_CHOSEN);
		});
	}
}
