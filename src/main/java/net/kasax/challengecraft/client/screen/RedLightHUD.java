package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_48_RedLight;
import net.kasax.challengecraft.client.SoundCues;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.network.RedLightSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * HUD card for "Red Light, Green Light" (challenge 48): the light's colour, and during yellow the
 * seconds left until red.
 *
 * <p>Holds its own copy of the phase, separate from the server state in {@link Chal_48_RedLight}
 * (singleplayer shares statics between the two sides). Yellow counts down locally from the moment
 * the packet arrived, so the number moves every frame without a packet per second.
 */
@Environment(EnvType.CLIENT)
public final class RedLightHUD {
    private static volatile int phase = RedLightSyncPacket.OFF;
    private static volatile long yellowEndsAt = 0L;

    private RedLightHUD() {
    }

    public static void update(RedLightSyncPacket packet) {
        int previous = phase;
        // Only a CHANGE of colour sounds; the sync that arrives on joining is silent.
        if (previous != RedLightSyncPacket.OFF && previous != packet.phase) {
            switch (packet.phase) {
                case RedLightSyncPacket.GREEN -> SoundCues.play(SoundCues.Cue.GREEN);
                case RedLightSyncPacket.RED -> SoundCues.play(SoundCues.Cue.RED);
                case RedLightSyncPacket.YELLOW -> {
                    SoundCues.play(SoundCues.Cue.YELLOW);
                    for (int left = packet.yellowTicksLeft - 1; left > 0; left--) {
                        if (left % 20 == 0) {
                            SoundCues.play(SoundCues.Cue.TICK, packet.yellowTicksLeft - left);
                        }
                    }
                }
                default -> {
                }
            }
        }
        phase = packet.phase;
        yellowEndsAt = packet.yellowTicksLeft >= 0 ? Util.getMillis() + packet.yellowTicksLeft * 50L : 0L;
    }

    public static void reset() {
        phase = RedLightSyncPacket.OFF;
        yellowEndsAt = 0L;
    }

    public static HudCard buildCard() {
        Minecraft client = Minecraft.getInstance();
        if (!Chal_48_RedLight.isActive() || client.player == null || phase == RedLightSyncPacket.OFF) {
            return null;
        }
        return switch (phase) {
            case RedLightSyncPacket.GREEN -> new HudCard("red_light",
                    new ItemStack(Items.CONCRETE.pick(DyeColor.LIME)),
                    Component.translatable("challengecraft.red_light.hud.green"),
                    Component.translatable("challengecraft.red_light.hud.go"),
                    1f, CraftUI.SUCCESS, RedLightSyncPacket.GREEN);
            case RedLightSyncPacket.YELLOW -> {
                long msLeft = Math.max(0L, yellowEndsAt - Util.getMillis());
                int seconds = (int) Math.ceil(msLeft / 1000.0);
                yield new HudCard("red_light",
                        new ItemStack(Items.CONCRETE.pick(DyeColor.YELLOW)),
                        Component.translatable("challengecraft.red_light.hud.yellow"),
                        Component.translatable("challengecraft.red_light.hud.countdown", Math.max(1, seconds)),
                        Math.min(1f, msLeft / 3000f), CraftUI.WARNING, RedLightSyncPacket.YELLOW);
            }
            default -> new HudCard("red_light",
                    new ItemStack(Items.CONCRETE.pick(DyeColor.RED)),
                    Component.translatable("challengecraft.red_light.hud.red"),
                    Component.translatable("challengecraft.red_light.hud.freeze"),
                    0f, CraftUI.DANGER, RedLightSyncPacket.RED);
        };
    }
}
