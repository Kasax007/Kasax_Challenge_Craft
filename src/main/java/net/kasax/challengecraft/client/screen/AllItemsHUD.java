package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
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
        HudRenderCallback.EVENT.register(AllItemsHUD::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickDelta) {
        if (!active || totalItems == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();

        boolean completed = currentIndex >= totalItems;
        Text itemName = completed ? Text.translatable("challengecraft.completed").formatted(Formatting.GREEN, Formatting.BOLD) : Chal_22_AllItems.getFormattedItemName(currentItem).copy().formatted(Formatting.GOLD);
        String progressStr = (currentIndex) + " / " + totalItems;
        Text progressText = Text.literal(progressStr).formatted(Formatting.GRAY);

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

        // Draw icon
        if (!completed) {
            ctx.drawItem(currentItem, centerX - 8, y);
        } else {
            ctx.drawItem(new ItemStack(net.minecraft.item.Items.NETHER_STAR), centerX - 8, y);
        }
        
        // Draw item name below
        ctx.drawCenteredTextWithShadow(tr, itemName, centerX, y + 18, 0xFFFFFF);
        
        // Draw progress below name
        ctx.drawCenteredTextWithShadow(tr, progressText, centerX, y + 28, 0xFFFFFF);
    }
}
