// src/main/java/net/kasax/challengecraft/ChallengeCraftClient.java
package net.kasax.challengecraft;

import net.fabricmc.api.ClientModInitializer;
import net.kasax.challengecraft.client.screen.TimerOverlay;

import java.util.Collections;
import java.util.List;

public class ChallengeCraftClient implements ClientModInitializer {
    /** Used for single‐player: last value chosen on the Create World screen */
    public static List<Integer> LAST_CHOSEN = Collections.singletonList(1);
    public static int SELECTED_MAX_HEARTS = 0;


    @Override
    public void onInitializeClient() {
        TimerOverlay.register();
    }
}
