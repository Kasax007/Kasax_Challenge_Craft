package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.kasax.challengecraft.network.AdvancementInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
/** Compact HUD for the current all-achievements target. */
public class AllAchievementsHUD {
    private static AdvancementInfo currentAdvancement = null;
    private static int currentIndex = 0;
    private static int total = 0;
    private static boolean active = false;

    public static void update(AdvancementInfo info, int index, int totalCount) {
        currentAdvancement = info;
        currentIndex = index;
        total = totalCount;
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void register() {
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("challengecraft", "all_achievements_hud"), AllAchievementsHUD::onHudRender);
    }

    private static void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickDelta) {
        if (!active || total == 0) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        Font tr = client.font;
        int sw = client.getWindow().getGuiScaledWidth();

        boolean completed = currentIndex >= total;
        
        Component advName = completed ? Component.translatable("challengecraft.completed").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.translatable("challengecraft.placeholder.pending");
        ItemStack icon = completed ? new ItemStack(Items.NETHER_STAR) : ItemStack.EMPTY;

        if (!completed && currentAdvancement != null) {
            advName = currentAdvancement.title().copy().withStyle(ChatFormatting.GOLD);
            icon = currentAdvancement.icon();
        }

        Component progressText = Component.translatable("challengecraft.progress.simple", currentIndex, total).withStyle(ChatFormatting.GRAY);

        int centerX = sw / 2;
        int activeCount = (AllItemsHUD.isActive() ? 1 : 0) + (AllEntitiesHUD.isActive() ? 1 : 0) + (active ? 1 : 0);
        
        if (activeCount == 3) {
            centerX = sw / 2 + 120;
        } else if (activeCount == 2) {
            if (AllItemsHUD.isActive() || AllEntitiesHUD.isActive()) {
                centerX = sw / 2 + 70;
            }
        }
        
        int y = 5;

        ctx.item(icon, centerX - 8, y);
        ctx.centeredText(tr, advName, centerX, y + 18, 0xFFFFFF);
        ctx.centeredText(tr, progressText, centerX, y + 28, 0xFFFFFF);
    }
}
