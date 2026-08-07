package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_46_Dice;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
/** HUD card showing how many blocks of movement the local player has left. */
public class DiceHUD {
    public static HudCard buildCard() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!Chal_46_Dice.isActive() || client.player == null) {
            return null;
        }

        float remaining = DiceClientState.getRemaining();
        int lastRoll = DiceClientState.getLastRoll();
        boolean rolling = DiceClientState.isRolling();

        Text title = rolling
                ? Text.translatable("challengecraft.dice.hud_rolling")
                : Text.translatable("challengecraft.dice.hud_title");

        // One decimal: the budget drains continuously, so a bare integer would look frozen.
        Text value = rolling ? Text.of("...") : Text.of(String.format("%.1f", remaining));

        int accent = remaining <= 0f && !rolling ? CraftUI.DANGER : CraftUI.GOLD;
        float progress = lastRoll > 0 ? Math.min(1f, remaining / lastRoll) : 0f;

        return new HudCard("dice", new ItemStack(ModItems.DICE), title, value,
                progress, accent, Math.round(remaining * 10f));
    }
}
