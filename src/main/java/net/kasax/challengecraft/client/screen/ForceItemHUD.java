package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.data.ForceItemBattleSavedData;
import net.kasax.challengecraft.network.ForceItemSyncPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active || client.player == null) {
            return null;
        }
        ForceItemSyncPacket state = ForceItemClientState.get();
        if (state.state() == ForceItemBattleSavedData.STATE_IDLE) {
            return null;
        }
        ForceItemSyncPacket.PlayerEntry entry = ForceItemClientState.getEntry(client.player.getUuid());
        if (entry == null) {
            return null;
        }

        boolean ended = state.state() == ForceItemBattleSavedData.STATE_ENDED;
        Item targetItem = entry.itemId().isEmpty() ? Items.AIR : Registries.ITEM.get(Identifier.of(entry.itemId()));

        Text title;
        ItemStack icon;
        int accent;
        if (ended) {
            title = Text.translatable("challengecraft.fib.hud_ended");
            icon = new ItemStack(Items.NETHER_STAR);
            accent = CraftUI.SUCCESS;
        } else {
            title = targetItem.getName();
            icon = new ItemStack(targetItem);
            accent = CraftUI.GOLD;
        }

        long remaining = ForceItemClientState.remainingTicksNow();
        long seconds = remaining / 20L;
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        Text value = ended
                ? Text.of(String.valueOf(entry.score()))
                : Text.of("✦" + entry.score() + " · " + time);

        // The bar drains with the battle timer; jokers are visible in the game screen.
        long totalTicks = Math.max(1L, net.kasax.challengecraft.ChallengeCraftClient.SELECTED_FIB_MINUTES * 1200L);
        float progress = ended ? 1f : remaining / (float) totalTicks;
        return new HudCard("force_item_battle", icon, title, value, progress, accent, entry.score());
    }
}
