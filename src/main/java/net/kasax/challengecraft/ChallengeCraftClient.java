// src/main/java/net/kasax/challengecraft/ChallengeCraftClient.java
package net.kasax.challengecraft;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.util.Identifier;

public class ChallengeCraftClient implements ClientModInitializer {
    /** Must match the server channel ID. */
    public static final Identifier CHANNEL = Identifier.of(ChallengeCraft.MOD_ID, "challenge_select");

    @Override
    public void onInitializeClient() {
        // All GUI injection & packet‐sending is done in CreateWorldScreenMixin.
    }
}
