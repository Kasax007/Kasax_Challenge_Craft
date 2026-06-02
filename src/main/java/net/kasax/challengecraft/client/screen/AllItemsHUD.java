package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
/** Compact HUD for the current all-items target. */
public class AllItemsHUD {
    private static ItemStack currentItem = ItemStack.EMPTY;
    private static int currentIndex = 0;
    private static int totalItems = 0;
    private static boolean active = false;

    public static void update(ItemStack stack, int index, int total) {
        currentItem = stack;
        currentIndex = index;
        totalItems = total;
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void register() {
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("challengecraft", "all_items_hud"), AllItemsHUD::onHudRender);
    }

    private static void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickDelta) {
        if (!active || totalItems == 0) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        Font tr = client.font;
        int sw = client.getWindow().getGuiScaledWidth();

        boolean completed = currentIndex >= totalItems;
        Component itemName = completed ? Component.translatable("challengecraft.completed").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Chal_22_AllItems.getFormattedItemName(currentItem).copy().withStyle(ChatFormatting.GOLD);
        Component progressText = Component.translatable("challengecraft.progress.simple", currentIndex, totalItems).withStyle(ChatFormatting.GRAY);

        int centerX = sw / 2;
        int activeCount = (active ? 1 : 0) + (AllEntitiesHUD.isActive() ? 1 : 0) + (AllAchievementsHUD.isActive() ? 1 : 0);
        
        if (activeCount == 3) {
            centerX = sw / 2 - 120;
        } else if (activeCount == 2) {
            if (AllEntitiesHUD.isActive() || AllAchievementsHUD.isActive()) {
                centerX = sw / 2 - 70;
            }
        }
        int y = 5;

        if (!completed) {
            ctx.item(currentItem, centerX - 8, y);
        } else {
            ctx.item(new ItemStack(net.minecraft.world.item.Items.NETHER_STAR), centerX - 8, y);
        }
        
        ctx.centeredText(tr, itemName, centerX, y + 18, 0xFFFFFF);
        ctx.centeredText(tr, progressText, centerX, y + 28, 0xFFFFFF);
    }
}
