package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

public class Chal_7_MaxHealthModify {
    private static boolean active = false;
    private static float maxHearts = 10f; // default
    private static final Identifier MAX_HEALTH_MOD_ID = Identifier.of("challengecraft", "max_health");


    public static void register() {
        // Apply health attribute each world tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            server.getPlayerManager().getPlayerList().forEach(player -> {
                var attr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (attr == null) return;
                
                float newMax = maxHearts * 2f;
                double expectedAmount = newMax - attr.getBaseValue();
                
                var existing = attr.getModifier(MAX_HEALTH_MOD_ID);
                if (existing != null && Math.abs(existing.value() - expectedAmount) < 0.001) {
                    // already correct
                } else {
                    if (existing != null) attr.removeModifier(MAX_HEALTH_MOD_ID);
                    attr.addPersistentModifier(new EntityAttributeModifier(
                            MAX_HEALTH_MOD_ID, expectedAmount, EntityAttributeModifier.Operation.ADD_VALUE));
                }
                
                if (player.getHealth() > newMax) {
                    player.setHealth(newMax);
                }
            });
        });
    }

    public static void setActive(boolean v) { active = v; }
    public static void setMaxHearts(float hearts) { maxHearts = MathHelper.clamp(hearts, 0.5f, 10f); }
}
