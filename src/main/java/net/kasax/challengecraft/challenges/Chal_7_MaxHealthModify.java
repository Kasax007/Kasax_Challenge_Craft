package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.UUID;

/** Applies a configurable max-health cap through a persistent attribute modifier. */
public class Chal_7_MaxHealthModify {
    private static boolean active = false;
    private static float maxHearts = 10f;
    private static final Identifier MAX_HEALTH_MOD_ID = Identifier.fromNamespaceAndPath("challengecraft", "max_health");


    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            server.getPlayerList().getPlayers().forEach(player -> {
                var attr = player.getAttribute(Attributes.MAX_HEALTH);
                if (attr == null) return;
                
                float newMax = maxHearts * 2f;
                double expectedAmount = newMax - attr.getBaseValue();
                
                var existing = attr.getModifier(MAX_HEALTH_MOD_ID);
                if (existing == null || Math.abs(existing.amount() - expectedAmount) >= 0.001) {
                    if (existing != null) attr.removeModifier(MAX_HEALTH_MOD_ID);
                    attr.addPermanentModifier(new AttributeModifier(
                            MAX_HEALTH_MOD_ID, expectedAmount, AttributeModifier.Operation.ADD_VALUE));
                }
                
                if (player.getHealth() > newMax) {
                    player.setHealth(newMax);
                }
            });
        });
    }

    public static void setActive(boolean v) { active = v; }
    public static boolean isActive() { return active; }
    public static void setMaxHearts(float hearts) { maxHearts = Mth.clamp(hearts, 0.5f, 10f); }
    public static float getMaxHearts() { return maxHearts; }
}
