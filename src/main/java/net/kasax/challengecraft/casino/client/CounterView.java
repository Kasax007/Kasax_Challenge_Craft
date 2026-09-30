package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.BlackjackRevival;
import net.kasax.challengecraft.casino.BlackjackTable;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CasinoSounds;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static net.kasax.challengecraft.casino.DeviceLayouts.*;

/**
 * The croupier's counters as the world shows them: the bell and the deposit tray on the middle
 * counter (only its owner sees their own goods and what they fetch), and a hand of blackjack on
 * counter 0 or 2 while someone plays for their life — cards dealt one by one from the croupier's
 * side, his hole card turned over, the plaques HIT / STAND / DOUBLE / SPLIT at the front edge and,
 * standing above the back of the counter, both totals, the clock and the outcome.
 */
@Environment(EnvType.CLIENT)
final class CounterView {
    private static final Identifier CARDS = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/cards.png");
    private static final float ATLAS_W = 980f, ATLAS_H = 380f, CELL_W = 70f, CELL_H = 95f;
    private static final double TOP = COUNTER_TOP;
    /** Ticks a card needs from the croupier's hand to its place, and between two cards. */
    static final int FLY_TICKS = 7, CARD_GAP = 8, FLIP_TICKS = 6;
    private static final double CARD_W = 3.0, CARD_H = 4.2;

    private CounterView() {
    }

    static void draw(DevicePainter p, BlockPos pos, int index, float partial) {
        String me = p.mc.player.getName().getString();
        CasinoClientState.BjView game = null;
        for (CasinoClientState.BjView v : CasinoClientState.BLACKJACK.values()) {
            if (v.state.seat() == index) game = v;
        }
        if (index == 1) {
            drawBell(p, pos);
            CasinoClientState.BjView mine = CasinoClientState.BLACKJACK.get(me);
            if (mine == null) drawTray(p, pos);
        }
        if (game != null) drawHand(p, pos, game, game.state.player().equals(me), partial);
    }

    // ---- bell and deposit tray ----------------------------------------------------------------

    private static boolean aimed(BlockPos pos, int kind, int a) {
        CasinoClientState.Aim aim = CasinoClientState.aim;
        return aim != null && aim.master().equals(pos) && aim.zone().kind() == kind && (a < 0 || aim.zone().a() == a);
    }

    private static void drawBell(DevicePainter p, BlockPos pos) {
        boolean hover = aimed(pos, BELL, -1);
        int brass = hover ? 0xFFFFE08A : 0xFFD9A63A;
        double u = BELL_U, v = BELL_V;
        p.discTop(u, v, TOP + 0.1, 1.35, 0xFF6B4A1C, 16);
        p.box(u - 0.35, TOP + 0.1, v - 0.35, u + 0.35, TOP + 0.45, v + 0.35, 0xFF8A6526);
        // The dome, as stacked discs getting smaller.
        for (int i = 0; i < 5; i++) {
            double r = 1.15 * Math.cos(i * 0.32);
            p.discTop(u, v, TOP + 0.5 + i * 0.28, r, DevicePainter.shade(brass, 0.8f + i * 0.05f), 16);
        }
        p.box(u - 0.15, TOP + 1.9, v - 0.15, u + 0.15, TOP + 2.3, v + 0.15, brass);
    }

    private static void drawTray(DevicePainter p, BlockPos pos) {
        CasinoNet.Pending pend = CasinoClientState.pending;
        List<ItemStack> items = pend == null || pend.counter() != pos.asLong() ? List.of() : pend.items();
        if (items.isEmpty()) {
            p.textTop(Component.translatable("challengecraft.casino.counter.place_here"), 7.0, 9.4, TOP + 0.1, 1.0,
                    0xFF6A5230, 0, DevicePainter.FULLBRIGHT);
            return;
        }
        long total = 0;
        for (int i = 0; i < items.size() && i < DEPOSIT_SLOTS; i++) {
            double u = SLOT_U[i % 3], v = SLOT_V[i / 3];
            long value = pend.values()[i];
            total += value;
            if (aimed(pos, PENDING, i)) p.rectTop(u - 1.9, v - 1.5, u + 1.9, v + 1.5, TOP + 0.08, 0x60FFFFFF);
            // A small felt mat under each stack.
            p.rectTop(u - 1.6, v - 0.5, u + 1.6, v + 0.9, TOP + 0.05, value > 0 ? 0xFF1C5A34 : 0xFF5A1C1C);
            p.item(items.get(i), u, TOP + 1.5, v + 0.2, 2.6);
            String label = value > 0 ? CasinoEconomy.format(value * 100) : "0";
            double h = 0.9;
            double w = p.width(Component.literal(label)) * h / 7.0;
            if (w > 3.4) h *= 3.4 / w;
            p.textTop(Component.literal(label), u, v - 1.25, TOP + 0.1, h, value > 0 ? 0xFF14602C : 0xFFB01818, 0,
                    DevicePainter.FULLBRIGHT);
            if (items.get(i).getCount() > 1) {
                p.textTop(Component.literal("×" + items.get(i).getCount()), u + 1.2, v + 0.4, TOP + 0.12, 0.6, 0xFF202020, 0,
                        DevicePainter.FULLBRIGHT);
            }
        }
        Component sum = Component.translatable("challengecraft.casino.counter.total", CasinoEconomy.formatFull(total * 100));
        double h = 0.9, w = p.width(sum) * h / 7.0;
        if (w > 10.5) h *= 10.5 / w;
        p.textTop(sum, 1.0, BELL_V, TOP + 0.1, h, total > 0 ? 0xFF5A3A10 : 0xFFB01818, -1, DevicePainter.FULLBRIGHT);
    }

    // ---- blackjack ----------------------------------------------------------------------------

    /** Hands from the packet: {count, stake, done, cards...} each. */
    static List<int[]> hands(CasinoNet.Blackjack s) {
        List<int[]> out = new ArrayList<>();
        int[] h = s.hands();
        int p = 0;
        while (p + 2 < h.length) {
            int count = h[p];
            int[] hand = new int[3 + count];
            System.arraycopy(h, p, hand, 0, Math.min(3 + count, h.length - p));
            out.add(hand);
            p += 3 + count;
        }
        return out;
    }

    /**
     * Schedules the cards of a new state packet: every card not seen before lands {@link #CARD_GAP}
     * ticks after the previous one (the opening deal alternates player / dealer like at a real
     * table), a hole card that is now known is turned over in turn.
     */
    static void schedule(CasinoClientState.BjView v, CasinoNet.Blackjack s, long now) {
        List<String> order = new ArrayList<>();
        List<int[]> hands = hands(s);
        boolean opening = v.appear.isEmpty();
        if (opening && !hands.isEmpty()) {
            int[] h0 = hands.get(0);
            for (int i = 0; i < Math.max(h0[0], s.dealer().length); i++) {
                if (i < h0[0]) order.add("h0-" + i);
                if (i < s.dealer().length) order.add("d" + i);
            }
            for (int k = 1; k < hands.size(); k++) for (int i = 0; i < hands.get(k)[0]; i++) order.add("h" + k + "-" + i);
        } else {
            for (int k = 0; k < hands.size(); k++) for (int i = 0; i < hands.get(k)[0]; i++) order.add("h" + k + "-" + i);
            for (int i = 0; i < s.dealer().length; i++) order.add("d" + i);
        }
        long t = Math.max(now, v.lastEvent);
        for (String key : order) {
            if (v.appear.containsKey(key)) continue;
            t = Math.max(now, v.lastEvent + CARD_GAP);
            v.appear.put(key, t);
            v.lastEvent = t;
        }
        // The hole card: known now, was hidden before.
        if (s.dealer().length > 1 && s.dealer()[1] >= 0 && !v.flipAt.containsKey("d1")) {
            Integer shown = v.cardShown.get("d1");
            if (shown != null && shown < 0) {
                t = Math.max(now, v.lastEvent + CARD_GAP);
                v.flipAt.put("d1", t);
                v.lastEvent = t;
                // Cards the dealer draws after turning over come after the flip.
                for (int i = 2; i < s.dealer().length; i++) {
                    String key = "d" + i;
                    Long at = v.appear.get(key);
                    if (at != null && at <= t) {
                        long moved = Math.max(v.lastEvent + CARD_GAP, t + CARD_GAP);
                        v.appear.put(key, moved);
                        v.lastEvent = moved;
                    }
                }
            }
        }
        for (int i = 0; i < s.dealer().length; i++) v.cardShown.putIfAbsent("d" + i, s.dealer()[i]);
        if (s.dealer().length > 1 && s.dealer()[1] >= 0) v.cardShown.put("d1", s.dealer()[1]);
        v.settledAt = v.lastEvent + FLY_TICKS + 4;
        v.state = s;
    }

    /** Card and flip sounds as they fall due (called every client tick). */
    static void tickSounds(Minecraft mc, CasinoClientState.BjView v, BlockPos counter) {
        long now = CasinoClientState.clientTick;
        for (var e : v.appear.entrySet()) {
            if (e.getValue() + FLY_TICKS <= now && v.sounded.add(e.getKey())) {
                mc.level.playLocalSound(counter.getX() + 0.5, counter.getY() + 1, counter.getZ() + 0.5, CasinoSounds.CARD_PLACE,
                        SoundSource.BLOCKS, 0.6f, 0.9f + mc.level.getRandom().nextFloat() * 0.2f, false);
            }
        }
        Long flip = v.flipAt.get("d1");
        if (flip != null && flip <= now && v.sounded.add("flip")) {
            mc.level.playLocalSound(counter.getX() + 0.5, counter.getY() + 1, counter.getZ() + 0.5, CasinoSounds.CARD_FLIP,
                    SoundSource.BLOCKS, 0.6f, 1.0f, false);
        }
    }

    private static void drawHand(DevicePainter p, BlockPos pos, CasinoClientState.BjView v, boolean mine, float partial) {
        CasinoNet.Blackjack s = v.state;
        float now = CasinoClientState.clientTick + partial;
        List<int[]> hands = hands(s);

        // Dealer row at the back, towards the croupier.
        int[] dealer = s.dealer();
        List<Integer> dealerVisible = new ArrayList<>();
        double dStart = 8.0 - (CARD_W + (dealer.length - 1) * 2.0) / 2 + CARD_W / 2;
        for (int i = 0; i < dealer.length; i++) {
            String key = "d" + i;
            int card = dealer[i];
            Long flip = v.flipAt.get(key);
            if (i == 1 && flip != null && now < flip) card = -1;
            drawCard(p, v, key, card, dStart + i * 2.0, 11.9, i, 1f, flip, now);
            Long at = v.appear.get(key);
            if (card >= 0 && at != null && now >= at + FLY_TICKS) dealerVisible.add(card);
        }

        // The player's hands at the front, side by side.
        double scale = 1.0;
        double total = 0;
        for (int[] h : hands) total += CARD_W + (Math.max(1, h[0]) - 1) * 1.6 + 0.8;
        if (total > 15.2) scale = 15.2 / total;
        double hu = 8.0 - total * scale / 2;
        List<Integer> activeCards = new ArrayList<>();
        for (int k = 0; k < hands.size(); k++) {
            int[] h = hands.get(k);
            double width = (CARD_W + (Math.max(1, h[0]) - 1) * 1.6) * scale;
            boolean active = s.phase() == BlackjackRevival.PLAYING && k == s.active();
            if (active && hands.size() > 1) p.rectTop(hu - 0.3, 3.7, hu + width + 0.3, 8.7, TOP + 0.06, 0x70FFD24A);
            for (int i = 0; i < h[0]; i++) {
                double cu = hu + (CARD_W / 2 + i * 1.6) * scale;
                drawCard(p, v, "h" + k + "-" + i, h[3 + i], cu, 6.2, i, (float) scale, null, now);
                Long at = v.appear.get("h" + k + "-" + i);
                if (k == s.active() && at != null && now >= at + FLY_TICKS) activeCards.add(h[3 + i]);
            }
            hu += width + 0.8 * scale;
        }

        drawPlaques(p, pos, s, mine && now >= v.settledAt - FLY_TICKS);

        // Standing above the back of the counter: totals, clock, outcome.
        double sv = 14.9;
        p.rectFront(1.0, TOP + 0.3, 15.0, TOP + 3.3, sv + 0.12, 0xE0100A06);
        Component dealerLine = dealerVisible.isEmpty() ? Component.translatable("challengecraft.casino.blackjack.board.dealer_wait")
                : Component.translatable("challengecraft.casino.blackjack.board.dealer", BlackjackTable.total(dealerVisible));
        SlotView.fit(p, dealerLine, 8.0, TOP + 2.65, sv, 13.5, 0.75, 0xFFB7C0D0);
        Component you = activeCards.isEmpty() ? Component.empty()
                : Component.translatable(mine ? "challengecraft.casino.blackjack.board.you" : "challengecraft.casino.blackjack.board.player",
                BlackjackTable.total(activeCards), s.player());
        SlotView.fit(p, you, 8.0, TOP + 1.7, sv, 13.5, 0.75, 0xFFFFFFFF);
        if (s.phase() == BlackjackRevival.PLAYING) {
            float left = Math.max(0, s.ticksLeft() - (now - v.stateTick));
            float frac = left / (20f * 20f);
            p.rectFront(2.0, TOP + 0.6, 14.0, TOP + 0.95, sv - 0.05, 0xFF2A2A30);
            p.rectFront(2.0, TOP + 0.6, 2.0 + 12.0 * frac, TOP + 0.95, sv - 0.1, frac < 0.25f ? 0xFFE25B5B : 0xFFE3B35A);
        } else if (s.outcome() >= 0 && now >= v.settledAt) {
            String key = s.outcome() == BlackjackRevival.WIN ? "challengecraft.casino.blackjack.board.win"
                    : s.outcome() == BlackjackRevival.LOSE ? "challengecraft.casino.blackjack.board.lose"
                    : "challengecraft.casino.blackjack.board.push";
            int colour = s.outcome() == BlackjackRevival.WIN ? 0xFFFFD24A : s.outcome() == BlackjackRevival.LOSE ? 0xFFE25B5B : 0xFF8FD0FF;
            boolean blink = ((long) now / 5) % 2 == 0;
            SlotView.fit(p, Component.translatable(key), 8.0, TOP + 0.85, sv, 13.5, 0.8, blink ? colour : DevicePainter.shade(colour, 0.8f));
        }
    }

    private static void drawPlaques(DevicePainter p, BlockPos pos, CasinoNet.Blackjack s, boolean interactive) {
        String[] keys = {"hit", "stand", "double", "split"};
        int[] colours = {0xFF2E9A55, 0xFFC98F2A, 0xFF3A6FC0, 0xFF8A4FC8};
        int[] masks = {BlackjackTable.HIT, BlackjackTable.STAND, BlackjackTable.DOUBLE, BlackjackTable.SPLIT};
        for (int i = 0; i < 4; i++) {
            boolean allowed = s.phase() == BlackjackRevival.PLAYING && (s.allowed() & masks[i]) != 0;
            boolean hover = interactive && allowed && aimed(pos, BJ, i);
            double u0 = plaqueU(i), u1 = u0 + PLAQUE_W;
            int c = allowed ? (hover ? DevicePainter.shade(colours[i], 1.25f) : colours[i]) : 0xFF3A3A40;
            p.rectTop(u0 - 0.15, PLAQUE_V0 - 0.15, u1 + 0.15, PLAQUE_V1 + 0.15, TOP + 0.05, allowed ? 0xFFE3B35A : 0xFF55555C);
            p.rectTop(u0, PLAQUE_V0, u1, PLAQUE_V1, TOP + 0.12, c);
            Component label = Component.translatable("challengecraft.casino.blackjack." + keys[i]);
            double h = 0.9, w = p.width(label) * h / 7.0;
            if (w > PLAQUE_W - 0.4) h *= (PLAQUE_W - 0.4) / w;
            p.textTop(label, (u0 + u1) / 2, (PLAQUE_V0 + PLAQUE_V1) / 2, TOP + 0.14, h,
                    allowed ? 0xFFFFFFFF : 0xFF8A8A92, 0, DevicePainter.FULLBRIGHT);
        }
    }

    /** One card lying on the counter, flying in from the croupier's hand and turning over if asked. */
    private static void drawCard(DevicePainter p, CasinoClientState.BjView v, String key, int card, double u, double vv,
                                 int layer, float scale, Long flipAt, float now) {
        Long at = v.appear.get(key);
        if (at == null || now < at) return;
        float t = Math.min(1f, (now - at) / FLY_TICKS);
        float e = 1f - (1f - t) * (1f - t);
        // From the croupier's hand (back right, above the counter) to the place on the felt.
        double fu = 13.5, fv = 16.5, fy = TOP + 5.0;
        double y = TOP + 0.2 + layer * 0.12;
        double cu = fu + (u - fu) * e, cv = fv + (vv - fv) * e, cy = fy + (y - fy) * e + Math.sin(Math.PI * t) * 1.2;
        double scaleU = 1.0;
        int shown = card;
        if (flipAt != null && now >= flipAt && now < flipAt + FLIP_TICKS) {
            float f = (now - flipAt) / FLIP_TICKS;
            scaleU = Math.max(0.05, Math.abs(1 - 2 * f));
            if (f < 0.5f) shown = -1;
        }
        double hw = CARD_W / 2 * scale * scaleU, hh = CARD_H / 2 * scale;
        int col = shown < 0 ? 13 : shown % 13, row = shown < 0 ? 0 : shown / 13;
        float s0 = col * CELL_W / ATLAS_W, s1 = (col * CELL_W + CELL_W) / ATLAS_W;
        float t0 = row * CELL_H / ATLAS_H, t1 = (row * CELL_H + CELL_H) / ATLAS_H;
        // A thin dark edge so cards on top of each other stay apart.
        p.rectTop(cu - hw - 0.08, cv - hh - 0.08, cu + hw + 0.08, cv + hh + 0.08, cy - 0.05, 0xFF30302E);
        // Texture top towards the croupier (back), so the card reads upright for the player.
        p.texturedQuad(CARDS,
                cu - hw, cy, cv + hh, s0, t0,
                cu + hw, cy, cv + hh, s1, t0,
                cu + hw, cy, cv - hh, s1, t1,
                cu - hw, cy, cv - hh, s0, t1);
    }
}
