package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;

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

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static HudCard buildCard() {
        if (!active || totalEntities == 0) {
            return null;
        }

        boolean completed = currentIndex >= totalEntities;
        Text title;
        ItemStack icon;
        int accent;
        if (completed) {
            title = Text.translatable("challengecraft.completed");
            icon = new ItemStack(Items.NETHER_STAR);
            accent = CraftUI.SUCCESS;
        } else if (currentEntity != null) {
            title = currentEntity.getName();
            icon = ICON_CACHE.computeIfAbsent(currentEntity, type -> {
                SpawnEggItem egg = SpawnEggItem.forEntity(type);
                return egg != null ? new ItemStack(egg) : new ItemStack(Items.ZOMBIE_SPAWN_EGG);
            });
            accent = CraftUI.DANGER;
        } else {
            title = Text.translatable("challengecraft.placeholder.unknown");
            icon = new ItemStack(Items.BARRIER);
            accent = CraftUI.DANGER;
        }

        float progress = totalEntities == 0 ? 0f : currentIndex / (float) totalEntities;
        Text value = Text.of(currentIndex + " / " + totalEntities);
        return new HudCard("all_entities", icon, title, value, progress, accent, currentIndex);
    }
}
