package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
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
        HudRenderCallback.EVENT.register(AllEntitiesHUD::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickDelta) {
        if (!active || totalEntities == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();

        boolean completed = currentIndex >= totalEntities;
        Text entityName = completed ? Text.translatable("challengecraft.completed").formatted(Formatting.GREEN, Formatting.BOLD) : (currentEntity != null ? currentEntity.getName().copy().formatted(Formatting.RED) : Text.literal("???"));
        String progressStr = "Obtained: " + currentIndex + " / " + totalEntities;
        Text progressText = Text.literal(progressStr).formatted(Formatting.GRAY);

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

        // Draw icon
        ItemStack icon;
        if (completed) {
            icon = new ItemStack(Items.NETHER_STAR);
        } else if (currentEntity != null) {
            icon = ICON_CACHE.computeIfAbsent(currentEntity, type -> {
                SpawnEggItem egg = SpawnEggItem.forEntity(type);
                if (egg != null) {
                    return new ItemStack(egg);
                }
                return new ItemStack(Items.ZOMBIE_SPAWN_EGG);
            });
        } else {
            icon = new ItemStack(Items.BARRIER);
        }
        ctx.drawItem(icon, centerX - 8, y);
        
        // Draw entity name below
        ctx.drawCenteredTextWithShadow(tr, entityName, centerX, y + 18, 0xFFFFFF);
        
        // Draw progress below name
        ctx.drawCenteredTextWithShadow(tr, progressText, centerX, y + 28, 0xFFFFFF);
    }
}
