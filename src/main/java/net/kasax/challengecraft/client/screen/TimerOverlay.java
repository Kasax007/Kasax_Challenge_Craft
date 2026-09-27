package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.kasax.challengecraft.client.config.TimerSettings;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import java.awt.*;

@Environment(EnvType.CLIENT)
/** Shared run timer overlay backed by server-synced display time. */
public class TimerOverlay {
    /** Id of the HUD element this overlay draws through (26.2 replaced the draw callback). */
    private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath("challengecraft", "timer_overlay");

    private static int basePlayTicks = -1;
    private static double extraTicks = 0.0;
    private static long lastUpdateMillis = -1L;

    public static void setBasePlayTicks(int ticks) {
        basePlayTicks = ticks;
        extraTicks = 0.0;
        lastUpdateMillis = System.currentTimeMillis();
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();

            if (basePlayTicks < 0 || client.player == null || client.level == null) {
                lastUpdateMillis = now;
                return;
            }

            if (lastUpdateMillis < 0L) {
                lastUpdateMillis = now;
                return;
            }

            if (client.isPaused()) {
                lastUpdateMillis = now;
                return;
            }

            extraTicks += (now - lastUpdateMillis) / 50.0;
            lastUpdateMillis = now;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            basePlayTicks = -1;
            extraTicks = 0.0;
            lastUpdateMillis = -1L;
        });

        // Drawn after every vanilla element, exactly where the old HudRenderCallback sat.
        HudElementRegistry.addLast(ELEMENT_ID, TimerOverlay::onHudRender);
    }

    /** The clock as the player sees it, in their chosen format. */
    public static String formatted(TimerSettings s, double totalTicks) {
        long totalSeconds = (long) (totalTicks / 20.0);
        long hrs = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        return switch (s.format) {
            case ALWAYS_HOURS -> String.format("%d:%02d:%02d", hrs, mins, secs);
            case TENTHS -> {
                long tenths = (long) (totalTicks / 2.0) % 10;
                yield hrs > 0
                        ? String.format("%d:%02d:%02d.%d", hrs, mins, secs, tenths)
                        : String.format("%02d:%02d.%d", mins, secs, tenths);
            }
            case AUTO -> hrs > 0
                    ? String.format("%d:%02d:%02d", hrs, mins, secs)
                    : String.format("%02d:%02d", mins, secs);
        };
    }

    private static void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickDelta) {
        Minecraft client = Minecraft.getInstance();
        TimerSettings s = TimerSettings.get();
        if (client.player == null || basePlayTicks < 0 || !s.visible || TimerSettings.hiddenByKey) {
            return;
        }
        String timeString = formatted(s, basePlayTicks + extraTicks);
        draw(ctx, client.font, timeString, s,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(), 1.0f);
    }

    /**
     * Draws the timer wherever the settings say, on any screen.
     *
     * <p>Shared with the settings screen so its preview is the real thing rather than a second
     * drawing path that could drift out of step with the HUD. The point of the preview is that what
     * you drag around is exactly what you get in game.
     *
     * @param alpha overall opacity, so the preview can dim it while another control has focus
     */
    public static void draw(GuiGraphicsExtractor ctx, Font tr, String timeString,
                            TimerSettings s, int sw, int sh, float alpha) {
        int[] box = bounds(tr, timeString, s, sw, sh);
        int x0 = box[0];
        int y0 = box[1];

        if (s.background) {
            int pad = 3;
            CraftUI.panelFloat(ctx, x0 - pad, y0 - pad, box[2] + 2 * pad, box[3] + 2 * pad,
                    CraftUI.applyAlpha(CraftUI.BORDER, s.backgroundOpacity * alpha));
        }

        long now = System.currentTimeMillis();
        int rgb = s.color & 0xFFFFFF;
        if (s.pulse) {
            // Brightness only. Hue and saturation stay the player's, so a pulsing red stays red.
            float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            float pulseT = (now % 20000L) / 20000f;
            float bright = Math.min(1f, hsb[2] + 0.05f * (float) Math.sin(2 * Math.PI * pulseT));
            rgb = Color.HSBtoRGB(hsb[0], hsb[1], bright) & 0xFFFFFF;
        }
        int alphaBits = Math.round(255 * Math.max(0f, Math.min(1f, alpha))) << 24;
        int base = rgb | alphaBits;

        Matrix3x2fStack ms = ctx.pose();
        ms.pushMatrix();
        ms.translate(x0, y0);
        ms.scale(s.scale, s.scale);

        if (s.outline == TimerSettings.Outline.FULL) {
            // A ring of offset copies. Stays readable over bright terrain, where a single drop
            // shadow on one side vanishes.
            int dark = Math.round(200 * Math.max(0f, Math.min(1f, alpha))) << 24;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        ctx.text(tr, timeString, dx, dy, dark, false);
                    }
                }
            }
        }
        boolean shadow = s.outline == TimerSettings.Outline.SHADOW;
        ctx.text(tr, timeString, 0, 0, base, shadow);

        if (s.shine) {
            float shineT = (now % 7000L) / 7000f;
            float stripeW = 8f;
            float stripeCX = (tr.width(timeString) + stripeW) * shineT - stripeW / 2f;
            // A lighter version of the player's own colour, not a fixed gold — otherwise a blue
            // timer flashes gold every time the stripe sweeps past.
            int highlight = lighten(rgb, 0.45f) | alphaBits;
            float xAcc = 0f;
            for (char c : timeString.toCharArray()) {
                String ch = String.valueOf(c);
                float cw = tr.width(ch);
                if (Math.abs(xAcc + cw / 2f - stripeCX) <= stripeW / 2f) {
                    ctx.text(tr, ch, Math.round(xAcc), 0, highlight, shadow);
                }
                xAcc += cw;
            }
        }

        ms.popMatrix();
    }

    /**
     * Where the timer's box lands, in scaled screen pixels: {x, y, w, h}.
     *
     * <p>The position is a fraction of the screen, so it survives a resize or a GUI-scale change —
     * a pixel position moved clear of the held-item name would land back on top of it. The clamp is
     * what guarantees a stored value can never push the timer off-screen entirely.
     */
    public static int[] bounds(Font tr, String timeString, TimerSettings s, int sw, int sh) {
        int drawnW = Math.round(tr.width(timeString) * s.scale);
        int drawnH = Math.round(tr.lineHeight * s.scale);
        int x0;
        int y0;
        if (s.customPosition) {
            int anchorX = Math.round(s.x * sw);
            x0 = switch (s.align) {
                case LEFT -> anchorX;
                case RIGHT -> anchorX - drawnW;
                case CENTER -> anchorX - drawnW / 2;
            };
            y0 = Math.round(s.y * sh) - drawnH / 2;
        } else {
            x0 = (sw - drawnW) / 2;
            y0 = heldItemNameY(sh) - GAP - drawnH;
        }
        x0 = Math.max(0, Math.min(x0, Math.max(0, sw - drawnW)));
        y0 = Math.max(0, Math.min(y0, Math.max(0, sh - drawnH)));
        return new int[]{x0, y0, drawnW, drawnH};
    }

    /** Breathing room between the timer and the item name it has to stay off. */
    private static final int GAP = 6;

    /**
     * Where vanilla puts the held-item name, read out of the 26.2 {@code Hud} bytecode rather than
     * guessed: {@code guiHeight - 59}, moved 14 DOWN when {@code gameMode.canHurtPlayer()} is false,
     * which is creative and spectator. Getting that second part wrong is why the settings preview
     * did not match what a creative-mode player actually saw.
     */
    public static int heldItemNameY(int sh) {
        Minecraft mc = Minecraft.getInstance();
        boolean survival = mc.gameMode == null || mc.gameMode.canHurtPlayer();
        return sh - 59 + (survival ? 0 : 14);
    }

    /** The position the timer would take by itself, as the fraction the settings would store. */
    public static float[] autoFraction(Font tr, String timeString, TimerSettings s, int sw, int sh) {
        TimerSettings probe = new TimerSettings();
        probe.scale = s.scale;
        probe.format = s.format;
        probe.customPosition = false;
        int[] box = bounds(tr, timeString, probe, sw, sh);
        float fx = switch (s.align) {
            case LEFT -> box[0] / (float) sw;
            case RIGHT -> (box[0] + box[2]) / (float) sw;
            case CENTER -> (box[0] + box[2] / 2f) / sw;
        };
        return new float[]{fx, (box[1] + box[3] / 2f) / sh};
    }

    private static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (Math.round(r + (255 - r) * t) << 16)
                | (Math.round(g + (255 - g) * t) << 8)
                | Math.round(b + (255 - b) * t);
    }
}
