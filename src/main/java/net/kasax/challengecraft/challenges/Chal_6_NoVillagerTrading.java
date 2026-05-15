package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.ActionResult;

/** Prevents players from opening villager trades while the challenge is active. */
public class Chal_6_NoVillagerTrading {
    private static boolean active = false;

    public static void setActive(boolean on) {
        active = on;
    }
    public static boolean isActive() { return active; }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (isActive() && entity instanceof VillagerEntity) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

}
