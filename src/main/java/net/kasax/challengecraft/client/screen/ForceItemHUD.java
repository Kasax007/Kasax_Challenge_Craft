package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.data.ForceItemBattleSavedData;
import net.kasax.challengecraft.network.ForceItemSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
/** HUD card showing the local player's current Force Item Battle target, score, and timer. */
public class ForceItemHUD {
    private static boolean active = false;

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static HudCard buildCard() {
        Minecraft client = Minecraft.getInstance();
        if (!active || client.player == null) {
            return null;
        }
        ForceItemSyncPacket state = ForceItemClientState.get();
        if (state.state() == ForceItemBattleSavedData.STATE_IDLE) {
            return null;
        }
        ForceItemSyncPacket.PlayerEntry entry = ForceItemClientState.getEntry(client.player.getUUID());
        if (entry == null) {
            return null;
        }

        boolean ended = state.state() == ForceItemBattleSavedData.STATE_ENDED;
        Item targetItem = entry.itemId().isEmpty() ? Items.AIR : BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.itemId()));

        Component title;
        ItemStack icon;
        int accent;
        if (ended) {
            title = Component.translatable("challengecraft.fib.hud_ended");
            icon = new ItemStack(Items.NETHER_STAR);
            accent = CraftUI.SUCCESS;
        } else {
            // 26.2 dropped the no-arg Item.getName(); the display name now comes off the stack
            // (ItemStack.getItemName() is exactly getItem().getName(stack)).
            icon = new ItemStack(targetItem);
            title = icon.getItemName();
            accent = CraftUI.GOLD;
        }

        long remaining = ForceItemClientState.remainingTicksNow();
        long seconds = remaining / 20L;
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        Component value = ended
                ? Component.nullToEmpty(String.valueOf(entry.score()))
                : Component.nullToEmpty("✦" + entry.score() + " · " + time);

        // The bar drains with the battle timer; jokers are visible in the game screen.
        long totalTicks = Math.max(1L, net.kasax.challengecraft.ChallengeCraftClient.SELECTED_FIB_MINUTES * 1200L);
        float progress = ended ? 1f : remaining / (float) totalTicks;
        return new HudCard("force_item_battle", icon, title, value, progress, accent, entry.score());
    }
}
