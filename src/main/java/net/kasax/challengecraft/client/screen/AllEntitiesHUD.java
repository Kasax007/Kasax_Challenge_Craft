package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
/** Compact HUD card for the current all-entities target. */
public class AllEntitiesHUD {
    private static EntityType<?> currentEntity = null;
    private static int currentIndex = 0;
    private static int totalEntities = 0;
    private static boolean active = false;
    private static final Map<EntityType<?>, ItemStack> ICON_CACHE = new HashMap<>();

    public static void update(EntityType<?> entity, int index, int total) {
        currentEntity = entity;
        currentIndex = index;
        totalEntities = total;
    }


    /**
     * Drops the progress of the world just left.
     *
     * <p>Without this the card keeps last world's numbers: {@code setActive(false)} only hides it,
     * and after a save-and-restart the new world re-enables the challenge before its first progress
     * packet arrives — so the old count is what the player sees.
     */
    public static void reset() {
        currentEntity = null;
        currentIndex = 0;
        totalEntities = 0;
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

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
        boolean completed = totalEntities > 0 && currentIndex >= totalEntities;
        Component title;
        ItemStack icon;
        int accent;
        if (completed) {
            title = Component.translatable("challengecraft.completed");
            icon = new ItemStack(Items.NETHER_STAR);
            accent = CraftUI.SUCCESS;
        } else if (currentEntity != null) {
            title = currentEntity.getDescription();
            // 26.2: SpawnEggItem.byId returns Optional<Holder<Item>> instead of the egg item, so
            // the old null check becomes an Optional fold onto the same zombie-egg fallback.
            icon = ICON_CACHE.computeIfAbsent(currentEntity, type -> SpawnEggItem.byId(type)
                    .map(ItemStack::new)
                    .orElseGet(() -> new ItemStack(Items.ZOMBIE_SPAWN_EGG)));
            accent = CraftUI.DANGER;
        } else {
            title = Component.translatable("challengecraft.placeholder.unknown");
            icon = new ItemStack(Items.BARRIER);
            accent = CraftUI.DANGER;
        }

        float progress = totalEntities == 0 ? 0f : currentIndex / (float) totalEntities;
        Component value = Component.nullToEmpty(currentIndex + " / " + totalEntities);
        return new HudCard("all_entities", icon, title, value, progress, accent, currentIndex);
    }
}
