package net.kasax.challengecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.Chal_45_ForceItemBattle;
import net.kasax.challengecraft.client.config.TimerSettings;
import net.kasax.challengecraft.client.screen.ChallengeSelectionScreen;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.network.ForceItemActionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Challenge Craft's key bindings, under their own heading in Controls.
 *
 * <p>J opens the challenges, K the progress journey, H hides the mod's HUD (timer and cards, this
 * session only). The Force Item Battle joker is deliberately UNBOUND by default: a joker is spent
 * for good, and a stray key press should never cost one. Players who want it bind it themselves.
 */
@Environment(EnvType.CLIENT)
public final class ChallengeKeys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("challengecraft", "main"));

    public static final KeyMapping OPEN_CHALLENGES = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.challenges", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY));
    public static final KeyMapping OPEN_PROGRESS = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.progress", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
    public static final KeyMapping TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.toggle_hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));
    public static final KeyMapping USE_JOKER = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.joker", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

    private ChallengeKeys() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChallengeKeys::tick);
    }

    private static void tick(Minecraft client) {
        // consumeClick in a loop, as vanilla does, so presses queued during a lag spike are not lost;
        // only acted on in a world with no screen open.
        boolean inWorld = client.player != null && client.gui.screen() == null;
        while (OPEN_CHALLENGES.consumeClick()) {
            if (inWorld) {
                client.setScreenAndShow(new ChallengeSelectionScreen());
            }
        }
        while (OPEN_PROGRESS.consumeClick()) {
            if (inWorld) {
                client.setScreenAndShow(new LevelingScreen(null));
            }
        }
        while (TOGGLE_HUD.consumeClick()) {
            if (client.player != null) {
                TimerSettings.hiddenByKey = !TimerSettings.hiddenByKey;
                client.player.sendOverlayMessage(Component.translatable(TimerSettings.hiddenByKey
                        ? "challengecraft.key.hud_hidden" : "challengecraft.key.hud_shown"));
            }
        }
        while (USE_JOKER.consumeClick()) {
            if (inWorld && Chal_45_ForceItemBattle.isActive()) {
                ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.USE_JOKER, -1));
            }
        }
    }
}
