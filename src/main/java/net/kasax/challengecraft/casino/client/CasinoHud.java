package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.kasax.challengecraft.casino.CasinoDeviceBlock;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CasinoRegistry;
import net.kasax.challengecraft.casino.CasinoPartBlock;
import net.kasax.challengecraft.casino.CasinoSounds;
import net.kasax.challengecraft.casino.CrashGame;
import net.kasax.challengecraft.casino.CrashMath;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.kasax.challengecraft.casino.DeviceType;
import net.kasax.challengecraft.casino.RouletteMath;
import net.kasax.challengecraft.casino.SlotMath;
import net.kasax.challengecraft.challenges.Chal_50_HouseAlwaysWins;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.client.ui.HudStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Everything the casino draws on the HUD:
 * <ul>
 *   <li>a card in the objective stack: time and amount of the next fee, own balance, and a red
 *       warning when the whole team cannot cover it;</li>
 *   <li>one line under the crosshair saying what the spot of a device the player aims at does
 *       (which chip, which bet and what it pays, what the button does);</li>
 *   <li>banners for fees, deposits, unlocks and waves.</li>
 * </ul>
 * Playing happens on the devices themselves (see {@code CasinoWorldRenderer}). The reel panel and
 * the crash panel this overlay used to draw are kept below, unused, in case another feature wants
 * them again ({@link #drawSlotPanel}, {@link #drawCrashPanel}).
 */
@Environment(EnvType.CLIENT)
public final class CasinoHud {
    private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath("challengecraft", "casino_overlay");

    private record Banner(Component title, Component subtitle, int colour, long start, long duration) {
    }

    private static final List<Banner> BANNERS = new ArrayList<>();

    private CasinoHud() {
    }

    public static void register() {
        HudStack.addSource(CasinoHud::card, 0);
        HudElementRegistry.addLast(ELEMENT_ID, (context, tickCounter) -> extract(context));
    }

    // ---- card ---------------------------------------------------------------------------------

    private static HudCard card() {
        CasinoNet.State s = CasinoClientState.state;
        if (!Chal_50_HouseAlwaysWins.isActive() || s == null) return null;
        if (s.bankrupt()) {
            return new HudCard("casino", new ItemStack(CasinoRegistry.CHIP_WALLET),
                    Component.translatable("challengecraft.casino.hud.bankrupt"), Component.literal("0"), 0f, CraftUI.DANGER, 0);
        }
        int secs = CasinoClientState.secondsToFee();
        boolean short_ = s.total() < s.nextFee();
        Component title = Component.translatable("challengecraft.casino.hud.fee",
                String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60), CasinoEconomy.format(s.nextFee()));
        Component value = Component.translatable("challengecraft.casino.hud.balance", CasinoEconomy.format(s.balance()));
        int accent = short_ ? (flash() ? CraftUI.DANGER : CraftUI.WARNING) : secs <= 60 ? CraftUI.WARNING : CraftUI.GOLD;
        float progress = Math.min(1f, secs / 600f);
        return new HudCard("casino", new ItemStack(CasinoRegistry.CHIP_WALLET), title, value, progress, accent, s.feeIndex());
    }

    private static boolean flash() {
        return (System.currentTimeMillis() / 400L) % 2 == 0;
    }

    // ---- overlay ------------------------------------------------------------------------------

    private static void extract(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (!Chal_50_HouseAlwaysWins.isActive() || client.player == null || client.level == null) return;
        if (client.gui.hud.isHidden()) return;
        Font font = client.font;
        int w = client.getWindow().getGuiScaledWidth();
        int h = client.getWindow().getGuiScaledHeight();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // Under the fee card at the top, out of the way of the device the player looks at.
        if (client.gui.screen() == null) drawDeviceHint(context, font, client, w, 44);
        drawBanners(context, font, w);
    }

    // ---- slot panel ---------------------------------------------------------------------------

    private static SlotAnimation nearestSlot(Minecraft client) {
        SlotAnimation best = null;
        double bestD = 7.5 * 7.5;
        String me = client.player.getName().getString();
        for (SlotAnimation a : CasinoClientState.SLOTS.values()) {
            BlockPos p = BlockPos.of(a.pos);
            double d = client.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (a.result.player().equals(me) && d < 40 * 40) return a; // your own spin always shows
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        return best;
    }

    private static final int CELL = 26;

    private static void drawSlotPanel(GuiGraphicsExtractor ctx, Font font, SlotAnimation anim, int w, int h, float partial) {
        float t = anim.elapsed(partial);
        SlotAnimation.Phase phase = anim.phaseAt(t);
        int panelW = CELL * SlotMath.REELS + 24;
        int panelH = CELL * SlotMath.ROWS + 44;
        int x = (w - panelW) / 2;
        int y = h - 64 - panelH;
        boolean fs = phase.kind() == SlotAnimation.Kind.FS_SPIN || phase.kind() == SlotAnimation.Kind.FS_EXPAND
                || phase.kind() == SlotAnimation.Kind.FS_OUTRO || phase.kind() == SlotAnimation.Kind.FS_INTRO;
        int accent = fs ? 0xFFB57BFF : CraftUI.GOLD;

        // Cabinet.
        CraftUI.frame(ctx, x, y, panelW, panelH, 0xF0140C10, 0xFF5A1E1E, accent);
        Component name = Component.translatable("challengecraft.casino.slot.name");
        ctx.centeredText(font, name, x + panelW / 2, y + 7, accent);
        int rx = x + 12;
        int ry = y + 20;
        ctx.fill(rx - 2, ry - 2, rx + CELL * SlotMath.REELS + 2, ry + CELL * SlotMath.ROWS + 2, 0xFF2A1B10);

        ctx.enableScissor(rx, ry, rx + CELL * SlotMath.REELS, ry + CELL * SlotMath.ROWS);
        for (int r = 0; r < SlotMath.REELS; r++) {
            int cx = rx + r * CELL;
            boolean tension = anim.reelInTension(r, t);
            int bgTop = tension ? 0xFFFFF1B8 : 0xFFF5EBD8;
            int bgBottom = tension ? 0xFFE8C06A : 0xFFD9C9A8;
            ctx.fill(cx + 1, ry, cx + CELL - 1, ry + CELL * SlotMath.ROWS / 2, bgTop);
            ctx.fill(cx + 1, ry + CELL * SlotMath.ROWS / 2, cx + CELL - 1, ry + CELL * SlotMath.ROWS, bgBottom);
            float position = anim.reelPosition(r, t);
            int base = (int) Math.floor(position);
            float frac = position - base;
            boolean spinning = anim.reelSpinning(r, t);
            for (int k = -1; k <= 3; k++) {
                int symbol = SlotMath.symbolAt(r, base + k, 0);
                float sy = ry + (k - frac) * CELL;
                ctx.pose().pushMatrix();
                ctx.pose().translate(cx + CELL / 2f, sy + CELL / 2f);
                float scale = spinning ? 1.3f : 1.35f;
                ctx.pose().scale(scale, spinning ? scale * 1.25f : scale);
                ctx.item(new ItemStack(SlotMath.SYMBOL_ITEMS[symbol]), -8, -8);
                ctx.pose().popMatrix();
            }
            if (spinning) {
                // Motion blur: a soft vertical sheen over a turning reel.
                ctx.fill(cx + 1, ry, cx + CELL - 1, ry + CELL * SlotMath.ROWS, 0x30FFFFFF);
            }
        }
        // Expanded lucky item during the free spins.
        int spinIdx = Math.min(phase.spin(), anim.spinCount - 1);
        boolean showExpand = phase.kind() == SlotAnimation.Kind.FS_EXPAND || phase.kind() == SlotAnimation.Kind.FS_OUTRO;
        if (showExpand && anim.result.luckySymbol() >= 0) {
            int mask = anim.result.expandMasks()[spinIdx];
            float grow = phase.kind() == SlotAnimation.Kind.FS_EXPAND ? Math.min(1f, phase.local() / 8f) : 1f;
            for (int r = 0; r < SlotMath.REELS; r++) {
                if ((mask & (1 << r)) == 0) continue;
                int cx = rx + r * CELL;
                int glow = CraftUI.applyAlpha(0xFFFFD34D, 0.85f * grow);
                ctx.fill(cx + 1, ry, cx + CELL - 1, ry + CELL * SlotMath.ROWS, glow);
                ctx.pose().pushMatrix();
                ctx.pose().translate(cx + CELL / 2f, ry + CELL * SlotMath.ROWS / 2f);
                float s = 1.4f + 2.2f * grow;
                ctx.pose().scale(s, s);
                ctx.item(new ItemStack(SlotMath.SYMBOL_ITEMS[anim.result.luckySymbol()]), -8, -8);
                ctx.pose().popMatrix();
            }
        }
        ctx.disableScissor();

        // Win lines.
        boolean lines = phase.kind() == SlotAnimation.Kind.BASE_WIN || phase.kind() == SlotAnimation.Kind.FS_EXPAND
                || (phase.kind() == SlotAnimation.Kind.FS_SPIN && phase.local() > SlotMath.FS_STOP_TICK[4] + 2)
                || phase.kind() == SlotAnimation.Kind.DONE;
        if (lines) {
            SlotMath.Spin spin = anim.spins.get(anim.spinShown(t));
            if (!spin.lineWins().isEmpty()) {
                int idx = (int) (phase.local() / 10f) % spin.lineWins().size();
                SlotMath.LineWin lw = spin.lineWins().get(idx);
                int colour = LINE_COLOURS[lw.line() % LINE_COLOURS.length];
                int[] rows = SlotMath.LINE_ROWS[lw.line()];
                for (int r = 0; r < SlotMath.REELS; r++) {
                    int cx = rx + r * CELL + CELL / 2;
                    int cy = ry + rows[r] * CELL + CELL / 2;
                    if (r < lw.count()) {
                        ctx.outline(rx + r * CELL + 1, ry + rows[r] * CELL + 1, CELL - 2, CELL - 2, colour);
                        ctx.outline(rx + r * CELL + 2, ry + rows[r] * CELL + 2, CELL - 4, CELL - 4, colour);
                    }
                    if (r < SlotMath.REELS - 1) {
                        int nx = rx + (r + 1) * CELL + CELL / 2;
                        int ny = ry + rows[r + 1] * CELL + CELL / 2;
                        drawLine(ctx, cx, cy, nx, ny, CraftUI.applyAlpha(colour, 0.8f));
                    }
                }
                Component lineText = Component.translatable("challengecraft.casino.slot.line", lw.line() + 1,
                        CasinoEconomy.formatFull(lw.units() * anim.bet() / SlotMath.LINES));
                ctx.centeredText(font, lineText, x + panelW / 2, ry + CELL * SlotMath.ROWS + 5, colour);
            }
        }

        // Stake and win.
        long shown = anim.shownWin(t);
        Component bottom = shown > 0
                ? Component.translatable("challengecraft.casino.slot.win", CasinoEconomy.formatFull(shown))
                : Component.translatable("challengecraft.casino.slot.stake", CasinoEconomy.formatFull(anim.bet()));
        if (!lines) ctx.centeredText(font, bottom, x + panelW / 2, ry + CELL * SlotMath.ROWS + 5,
                shown > 0 ? CraftUI.SUCCESS : CraftUI.TEXT_SECONDARY);
        if (!anim.result.payItem().isEmpty()) {
            ItemStack pay = new ItemStack(net.kasax.challengecraft.casino.SlotGame.itemById(anim.result.payItem()));
            ctx.item(pay, x + panelW - 22, y + 3);
        }

        // Free spin counter and lucky item.
        if (fs) {
            int done = phase.kind() == SlotAnimation.Kind.FS_INTRO ? 0 : phase.spin();
            Component fsText = Component.translatable("challengecraft.casino.slot.fs_counter", done, anim.spinCount - 1);
            CraftUI.labelChip(ctx, font, fsText, x + 4, y - 14, 0xFFB57BFF);
            if (anim.result.luckySymbol() >= 0 && phase.kind() != SlotAnimation.Kind.FS_INTRO) {
                CraftUI.iconTileItem(ctx, new ItemStack(SlotMath.SYMBOL_ITEMS[anim.result.luckySymbol()]),
                        x + panelW - 24, y - 20, 20, 0xFFFFD34D);
            }
        }

        // Big banners.
        if (phase.kind() == SlotAnimation.Kind.FS_INTRO) {
            float k = phase.local() / phase.length();
            float pop = Math.min(1f, phase.local() / 8f);
            CraftUI.drawCenteredScaled(ctx, font, Component.translatable("challengecraft.casino.slot.fs_title", anim.spinCount - 1)
                    .withStyle(net.minecraft.ChatFormatting.BOLD), w / 2, y - 34, 1.2f + 1.3f * easeOutBack(pop), rainbow(t));
            // The lucky item is drawn from a whirl of every item, slowing to a stop.
            int lucky = anim.result.luckySymbol();
            int shownSymbol = k < 0.75f ? (int) (t * (1.2f - k)) % SlotMath.ITEM_SYMBOLS : lucky;
            ctx.pose().pushMatrix();
            ctx.pose().translate(x + panelW / 2f, ry + CELL * SlotMath.ROWS / 2f);
            float s = k < 0.75f ? 3.0f : 3.0f + 0.6f * (float) Math.sin((k - 0.75f) * 30f) * (1f - k) * 4f;
            ctx.pose().scale(s, s);
            ctx.fill(-12, -12, 12, 12, 0xC0000000);
            ctx.item(new ItemStack(SlotMath.SYMBOL_ITEMS[Math.max(0, shownSymbol)]), -8, -8);
            ctx.pose().popMatrix();
            if (k >= 0.75f) {
                ctx.centeredText(font, Component.translatable("challengecraft.casino.slot.lucky"), x + panelW / 2, ry + CELL * SlotMath.ROWS - 4, 0xFFFFD34D);
            }
        }
        long win = anim.result.totalWin();
        boolean finale = (phase.kind() == SlotAnimation.Kind.BASE_WIN && anim.spinCount == 1)
                || phase.kind() == SlotAnimation.Kind.FS_OUTRO || phase.kind() == SlotAnimation.Kind.DONE;
        if (finale && win >= anim.bet() * 5) {
            String key = win >= anim.bet() * 50 ? "challengecraft.casino.slot.epic"
                    : win >= anim.bet() * 20 ? "challengecraft.casino.slot.mega" : "challengecraft.casino.slot.big";
            float pulse = 1.0f + 0.08f * (float) Math.sin(t * 0.5f);
            CraftUI.drawCenteredScaled(ctx, font, Component.translatable(key).withStyle(net.minecraft.ChatFormatting.BOLD),
                    w / 2, y - 34, 2.2f * pulse, rainbow(t));
            CraftUI.drawCenteredScaled(ctx, font, Component.literal(CasinoEconomy.formatFull(anim.shownWin(t))),
                    w / 2, y - 16, 1.5f, CraftUI.GOLD);
        }
    }

    private static final int[] LINE_COLOURS = {0xFFFFD233, 0xFF4DE6FF, 0xFFFF5959, 0xFF80FF73, 0xFFFF80FF,
            0xFFFF9933, 0xFF9999FF, 0xFF33FFCC, 0xFFFFFFFF, 0xFFE666E6};

    private static void drawLine(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int colour) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 1) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            ctx.fill(x - 1, y - 1, x + 1, y + 1, colour);
        }
    }

    /** A warm gold-to-rose shimmer for the big win titles (hand-rolled HSV, no AWT on the client). */
    private static int rainbow(float t) {
        float hue = (t * 0.02f) % 1f;
        float sat = 0.55f;
        int i = (int) (hue * 6f);
        float f = hue * 6f - i;
        float p = 1f - sat, q = 1f - f * sat, u = 1f - (1f - f) * sat;
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = 1f; g = u; b = p; }
            case 1 -> { r = q; g = 1f; b = p; }
            case 2 -> { r = p; g = 1f; b = u; }
            case 3 -> { r = p; g = q; b = 1f; }
            case 4 -> { r = u; g = p; b = 1f; }
            default -> { r = 1f; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // ---- crash panel --------------------------------------------------------------------------

    private static CasinoNet.CrashState nearestCrash(Minecraft client) {
        CasinoNet.CrashState best = null;
        double bestD = 28 * 28;
        String me = client.player.getName().getString();
        for (CasinoNet.CrashState s : CasinoClientState.CRASH.values()) {
            if (s.phase() == CrashGame.WAITING) continue;
            BlockPos p = BlockPos.of(s.pos());
            double d = client.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            boolean seated = s.seats().stream().anyMatch(seat -> seat.player().equals(me));
            if (seated && s.phase() == CrashGame.FLYING) return s;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private static void drawCrashPanel(GuiGraphicsExtractor ctx, Font font, CasinoNet.CrashState s, int w, int h, float partial) {
        int pw = 150, ph = 118;
        int x = w - pw - 8;
        int y = 44;
        CraftUI.frame(ctx, x, y, pw, ph, 0xF00B1020, 0xFF22324A, 0xFFFF7A45);
        ctx.text(font, Component.translatable("challengecraft.casino.crash.name"), x + 10, y + 7, 0xFFFF9B6A, false);

        float flight = CasinoWorldRenderer.flightTicks(s, partial);
        double m = s.phase() == CrashGame.CRASHED ? s.crashedAt() / 100.0 : CrashMath.multiplierAt((int) flight)
                * Math.exp(CrashMath.GROWTH_PER_SECOND * (flight - (int) flight) / 20.0);
        if (s.phase() == CrashGame.BETTING) m = 1.0;
        int colour = s.phase() == CrashGame.CRASHED ? CraftUI.DANGER
                : m < 2 ? 0xFFFFFFFF : m < 5 ? CraftUI.SUCCESS : m < 20 ? CraftUI.GOLD : 0xFFD08CFF;

        // Graph area with the curve drawn up to now.
        int gx = x + 10, gy = y + 20, gw = pw - 20, gh = 48;
        ctx.fill(gx, gy, gx + gw, gy + gh, 0xFF070A14);
        for (int i = 1; i < 4; i++) ctx.fill(gx, gy + gh * i / 4, gx + gw, gy + gh * i / 4 + 1, 0x22FFFFFF);
        if (s.phase() != CrashGame.BETTING) {
            float maxT = Math.max(200f, flight);
            double maxM = Math.max(2.0, m * 1.15);
            int prevX = gx, prevY = gy + gh;
            for (int i = 1; i <= 60; i++) {
                float ft = flight * i / 60f;
                double mm = Math.exp(CrashMath.GROWTH_PER_SECOND * ft / 20.0);
                int px = gx + (int) (gw * ft / maxT);
                int py = gy + gh - (int) ((gh - 2) * (mm - 1.0) / (maxM - 1.0));
                drawLine(ctx, prevX, prevY, px, Math.max(gy, py), CraftUI.applyAlpha(colour, 0.9f));
                prevX = px;
                prevY = Math.max(gy, py);
            }
            ctx.item(new ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET), prevX - 8, prevY - 12);
        }
        String mult = String.format(Locale.ROOT, "%.2f×", m);
        float shake = s.phase() == CrashGame.CRASHED ? (float) Math.sin(System.currentTimeMillis() * 0.06) * 1.5f : 0f;
        CraftUI.drawCenteredScaled(ctx, font, Component.literal(mult).withStyle(net.minecraft.ChatFormatting.BOLD),
                (int) (x + pw / 2 + shake), gy + gh / 2, s.phase() == CrashGame.CRASHED ? 2.4f : 2.0f, colour);

        Component status = switch (s.phase()) {
            case CrashGame.BETTING -> Component.translatable("challengecraft.casino.crash.betting", (s.ticksLeft() + 19) / 20);
            case CrashGame.FLYING -> Component.translatable("challengecraft.casino.crash.flying");
            default -> Component.translatable("challengecraft.casino.crash.crashed", String.format(Locale.ROOT, "%.2f", s.crashedAt() / 100.0));
        };
        ctx.text(font, status, x + 10, gy + gh + 4, CraftUI.TEXT_SECONDARY, false);
        int ly = gy + gh + 15;
        for (int i = 0; i < Math.min(3, s.seats().size()); i++) {
            CasinoNet.CrashSeat seat = s.seats().get(i);
            String right = seat.cashedAt() > 0 ? String.format(Locale.ROOT, "%.2f×", seat.cashedAt() / 100.0)
                    : s.phase() == CrashGame.CRASHED ? "✖" : "…";
            int c = seat.cashedAt() > 0 ? CraftUI.SUCCESS : s.phase() == CrashGame.CRASHED ? CraftUI.DANGER : CraftUI.TEXT_PRIMARY;
            ctx.text(font, Component.literal(CraftUI.trimToWidth(font, seat.player(), 70) + "  " + CasinoEconomy.format(seat.bet())),
                    x + 10, ly, CraftUI.TEXT_MUTED, false);
            ctx.text(font, Component.literal(right), x + pw - 10 - font.width(right), ly, c, false);
            ly += 10;
        }
        // History chips.
        int hx = x + 8;
        for (int i = 0; i < Math.min(5, s.history().length); i++) {
            double v = s.history()[i] / 100.0;
            int hc = v < 2 ? CraftUI.DANGER : v < 10 ? CraftUI.SUCCESS : CraftUI.GOLD;
            hx += CraftUI.labelChip(ctx, font, Component.literal(String.format(Locale.ROOT, "%.1f", v)), hx, y + ph + 2, hc) + 2;
        }
        if (s.phase() == CrashGame.FLYING) {
            Component hint = Component.translatable("challengecraft.casino.crash.hint", CasinoClient.CASH_OUT.getTranslatedKeyMessage());
            ctx.centeredText(font, hint, x + pw / 2, y + ph - 11, flash() ? CraftUI.GOLD : CraftUI.TEXT_SECONDARY);
        }
    }

    // ---- device hint --------------------------------------------------------------------------

    private static void drawDeviceHint(GuiGraphicsExtractor ctx, Font font, Minecraft client, int w, int y) {
        CasinoClientState.Aim aim = CasinoClientState.aim;
        CasinoNet.State s = CasinoClientState.state;
        if (aim == null || s == null) return;
        DeviceLayouts.Zone zone = aim.zone();
        Component line;
        int colour = CraftUI.TEXT_PRIMARY;
        if (zone.kind() == DeviceLayouts.CHIP) {
            int level = DeviceLayouts.trayLevel(s.betLevel() < 0 ? DeviceLayouts.ALL_IN_LEVEL : s.betLevel(), zone.a());
            line = level >= DeviceLayouts.ALL_IN_LEVEL ? Component.translatable("challengecraft.casino.hint.chip_all_in")
                    : Component.translatable("challengecraft.casino.hint.chip", CasinoEconomy.formatFull(DeviceLayouts.STAKES[level] * 100));
            colour = CraftUI.GOLD;
        } else if (zone.kind() == DeviceLayouts.BET) {
            line = Component.translatable("challengecraft.casino.hint.bet_spot", betName(zone.a(), zone.b()),
                    RouletteMath.odds(zone.a()), CasinoEconomy.formatFull(s.betAmount()), myBet(aim, zone));
        } else if (zone.kind() == DeviceLayouts.TAKE_BACK) {
            line = Component.translatable("challengecraft.casino.hint.take_back");
        } else if (zone.kind() == DeviceLayouts.BUTTON || (zone.kind() == DeviceLayouts.PLAY && aim.type() == DeviceType.CRASH)) {
            line = Component.translatable("challengecraft.casino.hint.crash", CasinoEconomy.formatFull(s.betAmount()),
                    CasinoClient.CASH_OUT.getTranslatedKeyMessage());
        } else if (zone.kind() == DeviceLayouts.PLAY) {
            line = switch (aim.type()) {
                case SLOT -> Component.translatable("challengecraft.casino.hint.slot", CasinoEconomy.formatFull(s.betAmount()));
                case PLINKO -> Component.translatable("challengecraft.casino.hint.plinko", CasinoEconomy.formatFull(s.betAmount()));
                case CASHIER -> Component.translatable("challengecraft.casino.hint.cashier");
                default -> Component.translatable("challengecraft.casino.hint.roulette");
            };
        } else if (aim.type() == DeviceType.ROULETTE) {
            line = Component.translatable("challengecraft.casino.hint.roulette");
        } else {
            return;
        }
        int tw = font.width(line);
        ctx.fill(w / 2 - tw / 2 - 4, y - 3, w / 2 + tw / 2 + 4, y + font.lineHeight + 1, 0x90000000);
        ctx.centeredText(font, line, w / 2, y, colour);
    }

    /** "Split 17/20", "Street 13–15", "Red"... */
    private static Component betName(int kind, int target) {
        return switch (kind) {
            case RouletteMath.STRAIGHT -> Component.translatable("challengecraft.casino.bet.straight", target);
            case RouletteMath.SPLIT -> Component.translatable("challengecraft.casino.bet.split", target / 100, target % 100);
            case RouletteMath.STREET -> Component.translatable("challengecraft.casino.bet.street", target, target + 2);
            case RouletteMath.CORNER -> Component.translatable("challengecraft.casino.bet.corner", target, target + 1, target + 3, target + 4);
            case RouletteMath.SIX_LINE -> Component.translatable("challengecraft.casino.bet.six_line", target, target + 5);
            case RouletteMath.DOZEN -> Component.translatable("challengecraft.casino.bet.dozen", target * 12 + 1, target * 12 + 12);
            case RouletteMath.COLUMN -> Component.translatable("challengecraft.casino.bet.column", target + 1);
            case RouletteMath.RED -> Component.translatable("challengecraft.casino.bet.red");
            case RouletteMath.BLACK -> Component.translatable("challengecraft.casino.bet.black");
            case RouletteMath.EVEN -> Component.translatable("challengecraft.casino.bet.even");
            case RouletteMath.ODD -> Component.translatable("challengecraft.casino.bet.odd");
            case RouletteMath.LOW -> Component.translatable("challengecraft.casino.bet.low");
            default -> Component.translatable("challengecraft.casino.bet.high");
        };
    }

    /** What the viewer already has on that spot, as a short suffix ("" if nothing). */
    private static Component myBet(CasinoClientState.Aim aim, DeviceLayouts.Zone zone) {
        CasinoNet.RouletteState rs = CasinoClientState.ROULETTE.get(aim.master().asLong());
        if (rs == null) return Component.empty();
        String me = Minecraft.getInstance().player.getName().getString();
        long mine = 0;
        for (CasinoNet.RouletteBet b : rs.bets()) {
            if (b.player().equals(me) && b.kind() == zone.a() && b.target() == zone.b()) mine += b.amount();
        }
        return mine == 0 ? Component.empty() : Component.translatable("challengecraft.casino.hint.yours", CasinoEconomy.formatFull(mine));
    }

    // ---- banners ------------------------------------------------------------------------------

    public static void fx(CasinoNet.Fx fx) {
        Minecraft client = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        switch (fx.kind()) {
            case CasinoNet.Fx.FEE_PAID -> {
                BANNERS.add(new Banner(Component.translatable("challengecraft.casino.banner.fee_paid",
                        CasinoEconomy.formatFull(Long.parseLong(fx.text()))),
                        Component.translatable("challengecraft.casino.banner.fee_share", CasinoEconomy.formatFull(fx.amount())),
                        CraftUI.GOLD, now, 4500));
                ui(client, CasinoSounds.REGISTER, 1.0f);
            }
            case CasinoNet.Fx.FEE_WARNING -> {
                BANNERS.add(new Banner(Component.translatable("challengecraft.casino.banner.fee_warning"),
                        Component.translatable("challengecraft.casino.banner.fee_warning_sub", CasinoEconomy.formatFull(fx.amount())),
                        CraftUI.WARNING, now, 5000));
                ui(client, CasinoSounds.FEE_WARNING, 1.0f);
            }
            case CasinoNet.Fx.DEPOSIT -> BANNERS.add(new Banner(
                    Component.translatable("challengecraft.casino.banner.deposit", CasinoEconomy.formatFull(fx.amount())),
                    Component.translatable("challengecraft.casino.banner.deposit_sub", fx.text()), CraftUI.SUCCESS, now, 2500));
            case CasinoNet.Fx.UNLOCK -> BANNERS.add(new Banner(
                    Component.translatable("challengecraft.casino.banner.unlock", Component.translatable("block.challengecraft." + fx.text())),
                    Component.translatable("challengecraft.casino.banner.unlock_sub"), 0xFFB57BFF, now, 5000));
            case CasinoNet.Fx.PURCHASE -> BANNERS.add(new Banner(
                    Component.translatable("challengecraft.casino.banner.purchase", Component.translatable("block.challengecraft." + fx.text())),
                    Component.empty(), CraftUI.GOLD, now, 2500));
            case CasinoNet.Fx.DEATH_TAX -> {
                BANNERS.add(new Banner(Component.translatable("challengecraft.casino.banner.death_tax", CasinoEconomy.formatFull(fx.amount())),
                        Component.translatable("challengecraft.casino.banner.death_tax_sub"), CraftUI.DANGER, now, 4500));
                ui(client, CasinoSounds.LOSE, 1.0f);
            }
            case CasinoNet.Fx.WAVE -> BANNERS.add(new Banner(
                    Component.translatable("challengecraft.casino.banner.wave", fx.text()),
                    Component.translatable("challengecraft.casino.banner.wave_sub", CasinoEconomy.formatFull(fx.amount())),
                    CraftUI.DANGER, now, 3500));
            case CasinoNet.Fx.BANKRUPT -> BANNERS.add(new Banner(
                    Component.translatable("challengecraft.casino.bankrupt.title"),
                    Component.translatable("challengecraft.casino.banner.bankrupt_sub"), CraftUI.DANGER, now, 12000));
            default -> {
            }
        }
        while (BANNERS.size() > 4) BANNERS.remove(0);
    }

    private static void ui(Minecraft client, SoundEvent sound, float pitch) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.45f));
    }

    private static void drawBanners(GuiGraphicsExtractor ctx, Font font, int w) {
        long now = System.currentTimeMillis();
        int y = 58;
        for (Iterator<Banner> it = BANNERS.iterator(); it.hasNext(); ) {
            Banner b = it.next();
            long age = now - b.start;
            if (age > b.duration) {
                it.remove();
                continue;
            }
            float in = Math.min(1f, age / 250f);
            float out = Math.min(1f, (b.duration - age) / 400f);
            float alpha = Math.min(in, out);
            int bw = Math.max(font.width(b.title) + 30, font.width(b.subtitle) + 20);
            int bh = b.subtitle.getString().isEmpty() ? 18 : 29;
            int bx = (w - bw) / 2;
            int by = y - (int) ((1f - in) * 12);
            ctx.fill(bx, by, bx + bw, by + bh, CraftUI.applyAlpha(0xE0101018, alpha));
            ctx.fill(bx, by, bx + bw, by + 1, CraftUI.applyAlpha(b.colour, alpha));
            ctx.fill(bx, by + bh - 1, bx + bw, by + bh, CraftUI.applyAlpha(b.colour, alpha * 0.6f));
            ctx.centeredText(font, b.title, w / 2, by + 5, CraftUI.applyAlpha(b.colour, Math.max(0.1f, alpha)));
            if (bh > 18) ctx.centeredText(font, b.subtitle, w / 2, by + 16, CraftUI.applyAlpha(CraftUI.TEXT_SECONDARY, Math.max(0.1f, alpha)));
            y += bh + 4;
        }
    }
}
