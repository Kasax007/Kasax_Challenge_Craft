package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
/** Compact HUD for the current all-entities target. */
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

    public static void register() {
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("challengecraft", "all_entities_hud"), AllEntitiesHUD::onHudRender);
    }

    private static void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickDelta) {
        if (!active || totalEntities == 0) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        Font tr = client.font;
        int sw = client.getWindow().getGuiScaledWidth();

        boolean completed = currentIndex >= totalEntities;
        Component entityName = completed ? Component.translatable("challengecraft.completed").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : (currentEntity != null ? currentEntity.getDescription().copy().withStyle(ChatFormatting.RED) : Component.translatable("challengecraft.placeholder.unknown"));
        Component progressText = Component.translatable("challengecraft.progress.obtained", currentIndex, totalEntities).withStyle(ChatFormatting.GRAY);

        int centerX = sw / 2;
        int activeCount = (AllItemsHUD.isActive() ? 1 : 0) + (active ? 1 : 0) + (AllAchievementsHUD.isActive() ? 1 : 0);
        
        if (activeCount == 3) {
            centerX = sw / 2;
        } else if (activeCount == 2) {
            if (AllItemsHUD.isActive()) {
                centerX = sw / 2 + 70; // AllItems is at -70
            } else if (AllAchievementsHUD.isActive()) {
                centerX = sw / 2 - 70; // AllAchievements is at +70
            }
        }
        int y = 5;

        ItemStack icon;
        if (completed) {
            icon = new ItemStack(Items.NETHER_STAR);
        } else if (currentEntity != null) {
            icon = ICON_CACHE.computeIfAbsent(currentEntity, type -> {
                var egg = SpawnEggItem.byId(type);
                if (egg.isPresent()) {
                    return new ItemStack(egg.get().value());
                }
                return new ItemStack(Items.ZOMBIE_SPAWN_EGG);
            });
        } else {
            icon = new ItemStack(Items.BARRIER);
        }
        ctx.item(icon, centerX - 8, y);
        
        ctx.centeredText(tr, entityName, centerX, y + 18, 0xFFFFFF);
        ctx.centeredText(tr, progressText, centerX, y + 28, 0xFFFFFF);
    }
}
