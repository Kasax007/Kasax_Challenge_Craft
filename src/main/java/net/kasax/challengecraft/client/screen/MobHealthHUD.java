package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_24_MobHealthMultiply;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

@Environment(EnvType.CLIENT)
/** Target HUD card showing the looked-at mob's remaining hearts out of its total. */
public class MobHealthHUD {

    public static HudCard buildCard() {
        if (!Chal_24_MobHealthMultiply.isActive()) {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        Entity targeted = client.crosshairPickEntity;
        if (!(targeted instanceof LivingEntity living)) {
            return null;
        }

        float health = living.getHealth();
        float maxHealth = living.getMaxHealth();
        int hearts = (int) Math.ceil(health / 2f);
        int maxHearts = (int) Math.ceil(maxHealth / 2f);
        float progress = maxHealth > 0f ? health / maxHealth : 0f;

        // 26.2: SpawnEggItem.byId returns Optional<Holder<Item>> instead of the egg item, so
        // the old null check becomes an Optional fold onto the same zombie-egg fallback.
        ItemStack icon = SpawnEggItem.byId(living.getType())
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.ZOMBIE_SPAWN_EGG));

        Component value = Component.nullToEmpty(hearts + " / " + maxHearts);
        // Bar shifts red→amber→green with remaining health.
        int accent = progress > 0.5f ? CraftUI.SUCCESS : (progress > 0.25f ? CraftUI.WARNING : CraftUI.DANGER);
        return new HudCard("mob_health", icon, living.getDisplayName(), value, progress, accent, hearts);
    }
}
