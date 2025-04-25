package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
                // remove previous modifier
                assert attr != null;
                attr.removeModifier(MAX_HEALTH_MOD_ID);
                // add new base maxHealth = hearts * 2
                float newMax = maxHearts * 2f;
                attr.addPersistentModifier(new EntityAttributeModifier(
                        MAX_HEALTH_MOD_ID, newMax - attr.getBaseValue(), EntityAttributeModifier.Operation.ADD_VALUE));
                if (player.getHealth() > newMax) {
                    player.setHealth(newMax);
                }
            });
        });
    }

    public static void setActive(boolean v) { active = v; }
    public static void setMaxHearts(float hearts) { maxHearts = MathHelper.clamp(hearts, 0.5f, 10f); }
}
