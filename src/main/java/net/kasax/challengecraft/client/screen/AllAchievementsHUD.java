package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.kasax.challengecraft.network.AdvancementInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        HudRenderCallback.EVENT.register(AllAchievementsHUD::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickDelta) {
        if (!active || total == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();

        boolean completed = currentIndex >= total;
        
        Text advName = completed ? Text.translatable("challengecraft.completed").formatted(Formatting.GREEN, Formatting.BOLD) : Text.translatable("challengecraft.placeholder.pending");
        ItemStack icon = completed ? new ItemStack(Items.NETHER_STAR) : ItemStack.EMPTY;

        if (!completed && currentAdvancement != null) {
            advName = currentAdvancement.title().copy().formatted(Formatting.GOLD);
            icon = currentAdvancement.icon();
        }

        Text progressText = Text.translatable("challengecraft.progress.simple", currentIndex, total).formatted(Formatting.GRAY);

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

        ctx.drawItem(icon, centerX - 8, y);
        ctx.drawCenteredTextWithShadow(tr, advName, centerX, y + 18, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, progressText, centerX, y + 28, 0xFFFFFF);
    }
}
