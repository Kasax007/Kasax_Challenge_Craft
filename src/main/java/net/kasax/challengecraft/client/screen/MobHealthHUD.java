package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.kasax.challengecraft.challenges.Chal_24_MobHealthMultiply;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class MobHealthHUD {

    public static void register() {
        HudRenderCallback.EVENT.register(MobHealthHUD::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickDelta) {
        if (!Chal_24_MobHealthMultiply.isActive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        Entity targeted = client.targetedEntity;
        if (!(targeted instanceof LivingEntity living)) return;

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int centerX = sw / 2;

        int y = 5;
        if (AllItemsHUD.isActive() || AllEntitiesHUD.isActive()) {
            y = 45;
        }

        Text nameText = living.getDisplayName().copy().formatted(Formatting.YELLOW, Formatting.BOLD);
        float health = living.getHealth();
        float maxHealth = living.getMaxHealth();
        // Using Locale.US to ensure dot as decimal separator
        Text healthText = Text.literal(String.format(Locale.US, "%.1f / %.1f HP", health, maxHealth)).formatted(Formatting.RED);

        int boxWidth = 150;
        int boxHeight = 40;
        int x = centerX - boxWidth / 2;

        // Draw semi-transparent background
        ctx.fill(x, y, x + boxWidth, y + boxHeight, 0x80000000);
        ctx.drawBorder(x, y, boxWidth, boxHeight, 0xFFFFFFFF);

        // Draw Entity Icon
        ItemStack icon = SpawnEggItem.forEntity(living.getType()) != null 
                ? new ItemStack(SpawnEggItem.forEntity(living.getType()))
                : new ItemStack(Items.ZOMBIE_SPAWN_EGG);
        
        ctx.drawItem(icon, x + 10, y + 12);

        // Draw Name and Health
        ctx.drawTextWithShadow(tr, nameText, x + 40, y + 10, 0xFFFFFF);
        ctx.drawTextWithShadow(tr, healthText, x + 40, y + 22, 0xFFFFFF);
    }
}
