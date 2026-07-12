package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.network.AdvancementInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
/** Compact HUD card for the current all-achievements target. */
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

    /** Builds this frame's HUD card, or {@code null} while the challenge is inactive. */
    public static HudCard buildCard() {
        if (!active || total == 0) {
            return null;
        }

        boolean completed = currentIndex >= total;
        Text title;
        ItemStack icon;
        int accent;
        if (completed) {
            title = Text.translatable("challengecraft.completed");
            icon = new ItemStack(Items.NETHER_STAR);
            accent = CraftUI.SUCCESS;
        } else if (currentAdvancement != null) {
            title = currentAdvancement.title();
            icon = currentAdvancement.icon();
            accent = CraftUI.GOLD;
        } else {
            title = Text.translatable("challengecraft.placeholder.pending");
            icon = ItemStack.EMPTY;
            accent = CraftUI.GOLD;
        }

        float progress = total == 0 ? 0f : currentIndex / (float) total;
        Text value = Text.of(currentIndex + " / " + total);
        return new HudCard("all_achievements", icon, title, value, progress, accent, currentIndex);
    }
}
