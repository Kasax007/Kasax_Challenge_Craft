package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_24_MobHealthMultiply;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
/** Target HUD card showing the looked-at mob's remaining hearts out of its total. */
public class MobHealthHUD {

    public static HudCard buildCard() {
        if (!Chal_24_MobHealthMultiply.isActive()) {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Entity targeted = client.targetedEntity;
        if (!(targeted instanceof LivingEntity living)) {
            return null;
        }

        float health = living.getHealth();
        float maxHealth = living.getMaxHealth();
        int hearts = (int) Math.ceil(health / 2f);
        int maxHearts = (int) Math.ceil(maxHealth / 2f);
        float progress = maxHealth > 0f ? health / maxHealth : 0f;

        ItemStack icon = SpawnEggItem.forEntity(living.getType()) != null
                ? new ItemStack(SpawnEggItem.forEntity(living.getType()))
                : new ItemStack(Items.ZOMBIE_SPAWN_EGG);

        Text value = Text.of(hearts + " / " + maxHearts);
        // Bar shifts red→amber→green with remaining health.
        int accent = progress > 0.5f ? CraftUI.SUCCESS : (progress > 0.25f ? CraftUI.WARNING : CraftUI.DANGER);
        return new HudCard("mob_health", icon, living.getDisplayName(), value, progress, accent, hearts);
    }
}
