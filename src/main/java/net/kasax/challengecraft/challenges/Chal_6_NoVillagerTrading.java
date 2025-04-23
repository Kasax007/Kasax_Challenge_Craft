package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.ActionResult;

public class Chal_6_NoVillagerTrading {
    private static boolean active = false;

    public static void setActive(boolean on) {
        active = on;
    }

    static {
        // Prevent opening any villager trade UI
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!active) return ActionResult.PASS;
            ScreenHandler sh = player.currentScreenHandler;
            if (sh instanceof MerchantScreenHandler) {
                // cancel the trade screen
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}
