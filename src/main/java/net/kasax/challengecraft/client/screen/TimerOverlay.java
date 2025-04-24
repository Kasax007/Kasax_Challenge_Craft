package net.kasax.challengecraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;

import java.awt.*;

// ... your imports ...

@Environment(EnvType.CLIENT)
public class TimerOverlay {
    private static int basePlayTicks = -1;
    private static int extraTicks    = 0;

    /** Called by our packet handler on the client. */
    public static void setBasePlayTicks(int ticks) {
        basePlayTicks = ticks;
        extraTicks    = 0;
    }

    public static void register() {
        // 2) each end of world tick, if it’s the same world our client is in, bump extraTicks
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || basePlayTicks < 0 || client.world == null) return;

            // only count ticks for the world the client is currently viewing
            if (client.world.getRegistryKey() == world.getRegistryKey()) {
                extraTicks++;
            }
        });
        HudRenderCallback.EVENT.register(TimerOverlay::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || basePlayTicks < 0) return;

        // 1) Build the time string
        int totalTicks    = basePlayTicks + extraTicks;
        long totalSeconds = totalTicks / 20L;
        long hrs          = totalSeconds / 3600;
        long mins         = (totalSeconds % 3600) / 60;
        long secs         = totalSeconds % 60;
        String timeString = (hrs > 0)
                ? String.format("%d:%02d:%02d", hrs, mins, secs)
                : String.format("%02d:%02d",        mins, secs);

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        // 2) Scale & base position
        float scale = 1.5f;
        int textW   = tr.getWidth(timeString);
        int textH   = tr.fontHeight;
        int x0      = (sw - (int)(textW * scale)) / 2;
        int y0      = sh - 60;

        // 3) Subtle, slow pulse (20s cycle, ±5% brightness)
        long now      = System.currentTimeMillis();
        float pulseT  = (now % 20000L) / 20000f; // 20s
        float hue     = 0.13f, sat = 1f;
        float baseB   = 0.95f;                   // center brightness
        float amp     = 0.05f;                   // ±5%
        float bright  = baseB + amp * (float)Math.sin(2*Math.PI * pulseT);
        int baseGold  = (Color.HSBtoRGB(hue, sat, bright) & 0xFFFFFF) | 0xFF000000;

        // 4) Highlight sweep every 5s
        long   shinePeriod = 7000L;                // 5s
        float  shineT      = (now % shinePeriod) / (float)shinePeriod;
        float  stripeW     = 8f;
        float  stripeCX    = (textW + stripeW) * shineT - stripeW/2f;
        float  halfW       = stripeW / 2f;

        // 5) Draw base text
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x0, y0, 0);
        ms.scale(scale, scale, 1f);
        ctx.drawText(tr, timeString, 0, 0, baseGold, true);

        // 6) Per‐glyph darker‐gold highlight when inside stripe
        float xAcc        = 0f;
        int   highlight   = 0xFFD4AF37; // darker gold

        for (char c : timeString.toCharArray()) {
            String s  = String.valueOf(c);
            float  cw = tr.getWidth(s);
            float  cx = xAcc + cw/2f;
            if (Math.abs(cx - stripeCX) <= halfW) {
                ctx.drawText(tr, s, Math.round(xAcc), 0, highlight, true);
            }
            xAcc += cw;
        }

        ms.pop();
    }
}
