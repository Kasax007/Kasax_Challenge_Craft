package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.network.AdvancementInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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


    /**
     * Drops the progress of the world just left.
     *
     * <p>Without this the card keeps last world's numbers: {@code setActive(false)} only hides it,
     * and after a save-and-restart the new world re-enables the challenge before its first progress
     * packet arrives — so the old count is what the player sees.
     */
    public static void reset() {
        currentAdvancement = null;
        currentIndex = 0;
        total = 0;
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    /** Builds this frame's HUD card, or {@code null} while the challenge is inactive. */
    public static HudCard buildCard() {
        // Visible as soon as the challenge is ACTIVE, not once progress data has arrived.
        //
        // Gating on a zero total meant the card stayed hidden for the whole window between the
        // challenge-sync packet and this challenge's progress packet — and on joining a world that
        // window is exactly when a player looks for it. It only appeared later, when some gameplay
        // action happened to trigger another progress sync. Until the data lands the card shows its
        // pending placeholder, which is honest and, unlike nothing at all, tells the player the
        // challenge is running.
        if (!active) {
            return null;
        }

        // total 0 means "not synced yet", which must not read as a finished run.
        boolean completed = total > 0 && currentIndex >= total;
        Component title;
        ItemStack icon;
        int accent;
        if (completed) {
            title = Component.translatable("challengecraft.completed");
            icon = new ItemStack(Items.NETHER_STAR);
            accent = CraftUI.SUCCESS;
        } else if (currentAdvancement != null) {
            title = currentAdvancement.title();
            icon = currentAdvancement.icon();
            accent = CraftUI.GOLD;
        } else {
            title = Component.translatable("challengecraft.placeholder.pending");
            icon = ItemStack.EMPTY;
            accent = CraftUI.GOLD;
        }

        float progress = total == 0 ? 0f : currentIndex / (float) total;
        Component value = Component.nullToEmpty(currentIndex + " / " + total);
        return new HudCard("all_achievements", icon, title, value, progress, accent, currentIndex);
    }
}
