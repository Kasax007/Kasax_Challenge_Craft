package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.*;

@Environment(EnvType.CLIENT)
public class TimerOverlay {
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

            if (basePlayTicks < 0 || client.player == null || client.world == null) {
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

        HudRenderCallback.EVENT.register(TimerOverlay::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || basePlayTicks < 0) {
            return;
        }

        double totalTicks = basePlayTicks + extraTicks;
        long totalSeconds = (long)(totalTicks / 20.0);
        long hrs = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        String timeString = (hrs > 0)
                ? String.format("%d:%02d:%02d", hrs, mins, secs)
                : String.format("%02d:%02d", mins, secs);

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        float scale = 1.5f;
        int textW = tr.getWidth(timeString);
        int x0 = (sw - (int)(textW * scale)) / 2;
        int y0 = sh - 60;

        long now = System.currentTimeMillis();
        float pulseT = (now % 20000L) / 20000f;
        float hue = 0.13f;
        float sat = 1f;
        float baseB = 0.95f;
        float amp = 0.05f;
        float bright = baseB + amp * (float)Math.sin(2 * Math.PI * pulseT);
        int baseGold = (Color.HSBtoRGB(hue, sat, bright) & 0xFFFFFF) | 0xFF000000;

        long shinePeriod = 7000L;
        float shineT = (now % shinePeriod) / (float)shinePeriod;
        float stripeW = 8f;
        float stripeCX = (textW + stripeW) * shineT - stripeW / 2f;
        float halfW = stripeW / 2f;

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x0, y0, 0);
        ms.scale(scale, scale, 1f);
        ctx.drawText(tr, timeString, 0, 0, baseGold, true);

        float xAcc = 0f;
        int highlight = 0xFFD4AF37;

        for (char c : timeString.toCharArray()) {
            String s = String.valueOf(c);
            float cw = tr.getWidth(s);
            float cx = xAcc + cw / 2f;
            if (Math.abs(cx - stripeCX) <= halfW) {
                ctx.drawText(tr, s, Math.round(xAcc), 0, highlight, true);
            }
            xAcc += cw;
        }

        ms.pop();
    }
}
