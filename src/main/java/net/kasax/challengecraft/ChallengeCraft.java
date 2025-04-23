package net.kasax.challengecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kasax.challengecraft.challenges.Chal_1_LevelItem;
import net.kasax.challengecraft.item.ModItems;
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

		// register items, commands, world‐load hooks, etc.
		ModItems.initialize();
		ChallengeManager.register();
		//ClientCommands.register();
		Chal_1_LevelItem.register();

		// 1) Tell Fabric about our SERVER‑BOUND channel:
		PayloadTypeRegistry.playC2S()
				.register(ChallengePacket.ID, ChallengePacket.CODEC);

		// 2) Now hook up the handler:
		PacketHandler.register();

	}
}
