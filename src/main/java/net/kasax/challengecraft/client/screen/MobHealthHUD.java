package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.kasax.challengecraft.challenges.Chal_24_MobHealthMultiply;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import java.util.Locale;

@Environment(EnvType.CLIENT)
/** HUD element that exposes the current mob-health multiplier to players. */
public class MobHealthHUD {

    public static void register() {
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("challengecraft", "mob_health_hud"), MobHealthHUD::onHudRender);
    }

    private static void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickDelta) {
        if (!Chal_24_MobHealthMultiply.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        Entity targeted = client.crosshairPickEntity;
        if (!(targeted instanceof LivingEntity living)) return;

        Font tr = client.font;
        int sw = client.getWindow().getGuiScaledWidth();
        int centerX = sw / 2;

        int y = 5;
        if (AllItemsHUD.isActive() || AllEntitiesHUD.isActive() || AllAchievementsHUD.isActive()) {
            y = 45;
        }

        Component nameText = living.getDisplayName().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        float health = living.getHealth();
        float maxHealth = living.getMaxHealth();
        // HUD formatting should not change with the client's locale.
        Component healthText = Component.translatable(
                "challengecraft.mob_health.health",
                String.format(Locale.US, "%.1f", health),
                String.format(Locale.US, "%.1f", maxHealth)
        ).withStyle(ChatFormatting.RED);

        int boxWidth = 150;
        int boxHeight = 40;
        int x = centerX - boxWidth / 2;

        ctx.fill(x, y, x + boxWidth, y + boxHeight, 0x80000000);
        ctx.outline(x, y, boxWidth, boxHeight, 0xFFFFFFFF);

        ItemStack icon = SpawnEggItem.byId(living.getType())
                .map(item -> new ItemStack(item.value()))
                .orElseGet(() -> new ItemStack(Items.ZOMBIE_SPAWN_EGG));
        
        ctx.item(icon, x + 10, y + 12);

        ctx.text(tr, nameText, x + 40, y + 10, 0xFFFFFF);
        ctx.text(tr, healthText, x + 40, y + 22, 0xFFFFFF);
    }
}
