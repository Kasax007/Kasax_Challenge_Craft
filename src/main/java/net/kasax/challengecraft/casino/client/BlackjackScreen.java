package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.BlackjackRevival;
import net.kasax.challengecraft.casino.BlackjackTable;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CasinoSounds;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "The House offers you a game." One hand of blackjack for your life, shown instead of the death
 * screen. Cards slide out of the shoe, the croupier's hole card flips when he plays; there is a
 * timer per decision (running out means standing). It cannot be closed — the only ways out are
 * winning, losing or the push that deals a fresh hand.
 */
@Environment(EnvType.CLIENT)
public class BlackjackScreen extends Screen {
    private static final Identifier CARDS = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/cards.png");
    private static final Identifier FELT = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/felt.png");
    private static final int CARD_W = 35, CARD_H = 48;
    private static final int ATLAS_W = 980, ATLAS_H = 380, CELL_W = 70, CELL_H = 95;

    private CasinoNet.Blackjack state;
    private final Map<String, Long> seenAt = new HashMap<>();
    private final Map<String, Integer> seenCard = new HashMap<>();
    private final Map<String, Long> flippedAt = new HashMap<>();
    private long receivedAt = System.currentTimeMillis();
    private long outcomeAt;
    private final long openedAt = System.currentTimeMillis();

    public BlackjackScreen(CasinoNet.Blackjack state) {
        super(Component.translatable("challengecraft.casino.blackjack.title"));
        this.state = state;
        noteCards(false);
    }

    public void update(CasinoNet.Blackjack fresh) {
        boolean newHand = state != null && state.phase() == BlackjackRevival.RESULT && fresh.phase() == BlackjackRevival.PLAYING;
        if (newHand) {
            seenAt.clear();
            seenCard.clear();
            flippedAt.clear();
        }
        if (state == null || state.outcome() < 0 && fresh.outcome() >= 0) outcomeAt = System.currentTimeMillis();
        this.state = fresh;
        this.receivedAt = System.currentTimeMillis();
        noteCards(true);
        rebuildWidgets();
    }

    public void closeFromServer() {
        this.minecraft.setScreenAndShow(null);
    }

    /** Records when each card first appeared, and plays the deal/flip sounds. */
    private void noteCards(boolean sound) {
        long now = System.currentTimeMillis();
        List<String> keys = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < state.dealer().length; i++) {
            keys.add("d" + i);
            values.add(state.dealer()[i]);
        }
        int p = 0, hand = 0;
        int[] h = state.hands();
        while (p + 2 < h.length) {
            int count = h[p];
            for (int i = 0; i < count; i++) {
                keys.add("h" + hand + "-" + i);
                values.add(h[p + 3 + i]);
            }
            p += 3 + count;
            hand++;
        }
        int dealt = 0;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int v = values.get(i);
            if (!seenAt.containsKey(k)) {
                seenAt.put(k, now + dealt * 180L);
                seenCard.put(k, v);
                if (sound) play(CasinoSounds.CARD_SLIDE, 1.0f + dealt * 0.03f);
                dealt++;
            } else if (seenCard.get(k) == -1 && v != -1) {
                seenCard.put(k, v);
                flippedAt.put(k, now);
                if (sound) play(CasinoSounds.CARD_FLIP, 1.0f);
            }
        }
    }

    private void play(SoundEvent sound, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.9f));
    }

    @Override
    protected void init() {
        int bw = 78, gap = 6;
        int total = bw * 4 + gap * 3;
        int bx = (width - total) / 2;
        int by = height - 40;
        String[] keys = {"hit", "stand", "double", "split"};
        int[] masks = {BlackjackTable.HIT, BlackjackTable.STAND, BlackjackTable.DOUBLE, BlackjackTable.SPLIT};
        int[] actions = {CasinoNet.Action.BJ_HIT, CasinoNet.Action.BJ_STAND, CasinoNet.Action.BJ_DOUBLE, CasinoNet.Action.BJ_SPLIT};
        int[] colours = {0xFF7BE0A4, 0xFFE3B35A, 0xFFB3D5FF, 0xFFB57BFF};
        for (int i = 0; i < 4; i++) {
            int mask = masks[i], action = actions[i];
            addRenderableWidget(new CasinoButton(bx + i * (bw + gap), by, bw, 20,
                    Component.translatable("challengecraft.casino.blackjack." + keys[i]), colours[i],
                    () -> state != null && state.phase() == BlackjackRevival.PLAYING && (state.allowed() & mask) != 0,
                    () -> CasinoClient.send(CasinoNet.Action.of(action))));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Deep red vignette over the world, then the felt table.
        ctx.fill(0, 0, width, height, 0xE0100404);
        int tx = 20, ty = 16, tw = width - 40, th = height - 32;
        for (int x = tx; x < tx + tw; x += 64) {
            for (int y = ty; y < ty + th; y += 64) {
                int w = Math.min(64, tx + tw - x), h = Math.min(64, ty + th - y);
                ctx.blit(RenderPipelines.GUI_TEXTURED, FELT, x, y, 0, 0, w, h, 64, 64);
            }
        }
        CraftUI.frame(ctx, tx - 4, ty - 4, tw + 8, th + 8, 0x00000000, 0xFF3D2412, CraftUI.GOLD);
        // The printed arc of the table.
        int cx = width / 2;
        ctx.centeredText(font, Component.translatable("challengecraft.casino.blackjack.felt1"), cx, height / 2 - 6, 0x60F4E6B0);
        ctx.centeredText(font, Component.translatable("challengecraft.casino.blackjack.felt2"), cx, height / 2 + 5, 0x40F4E6B0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (state == null) return;
        long now = System.currentTimeMillis();
        int cx = width / 2;
        float intro = Anim.easeOutCubic(Math.min(1f, (now - openedAt) / 600f));

        CraftUI.drawCenteredScaled(ctx, font, this.title.copy().withStyle(ChatFormatting.BOLD), cx, (int) (26 - (1 - intro) * 20), 1.5f, CraftUI.GOLD);
        ctx.centeredText(font, Component.translatable("challengecraft.casino.blackjack.stakes"), cx, 40, CraftUI.TEXT_SECONDARY);
        if (state.lostChips() > 0) {
            ctx.centeredText(font, Component.translatable("challengecraft.casino.blackjack.tax", CasinoEconomy.formatFull(state.lostChips())),
                    cx, 51, 0xFFE07070);
        }

        // Shoe.
        int shoeX = width - 70, shoeY = 30;
        ctx.fill(shoeX - 2, shoeY - 2, shoeX + CARD_W + 6, shoeY + CARD_H + 2, 0xFF1A0F08);
        drawCard(ctx, -1, shoeX + 4, shoeY, 1f);

        // Dealer.
        int[] dealer = state.dealer();
        int dy = 70;
        int dStart = cx - (dealer.length * (CARD_W + 6)) / 2;
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < dealer.length; i++) {
            drawAnimatedCard(ctx, "d" + i, dealer[i], dStart + i * (CARD_W + 6), dy, shoeX + 4, shoeY, now);
            if (dealer[i] >= 0) visible.add(dealer[i]);
        }
        if (!visible.isEmpty()) {
            int total = BlackjackTable.total(visible);
            CraftUI.labelChip(ctx, font, Component.translatable("challengecraft.casino.blackjack.dealer", total),
                    dStart - 70, dy + CARD_H / 2 - 6, total > 21 ? CraftUI.SUCCESS : CraftUI.TEXT_SECONDARY);
        }

        // Player hands.
        int[] h = state.hands();
        List<int[]> hands = new ArrayList<>();
        int p = 0;
        while (p + 2 < h.length) {
            int count = h[p];
            int[] hand = new int[3 + count];
            System.arraycopy(h, p, hand, 0, 3 + count);
            hands.add(hand);
            p += 3 + count;
        }
        int handW = 0;
        for (int[] hand : hands) handW += Math.max(2, hand[0]) * 18 + CARD_W + 24;
        int hx = cx - handW / 2;
        int hy = height / 2 + 24;
        for (int i = 0; i < hands.size(); i++) {
            int[] hand = hands.get(i);
            int count = hand[0];
            boolean active = state.phase() == BlackjackRevival.PLAYING && i == state.active();
            List<Integer> cards = new ArrayList<>();
            for (int k = 0; k < count; k++) {
                drawAnimatedCard(ctx, "h" + i + "-" + k, hand[3 + k], hx + k * 18, hy - k * 3, shoeX + 4, shoeY, now);
                cards.add(hand[3 + k]);
            }
            int total = BlackjackTable.total(cards);
            int width_ = Math.max(2, count) * 18 + CARD_W;
            int colour = total > 21 ? CraftUI.DANGER : total == 21 ? CraftUI.GOLD : active ? 0xFFFFFFFF : CraftUI.TEXT_SECONDARY;
            if (active) {
                float pulse = Anim.pulse(900);
                ctx.outline(hx - 4, hy - 4 - count * 3, width_ + 8 - 18 + 4, CARD_H + 8 + count * 3, CraftUI.applyAlpha(CraftUI.GOLD, 0.5f + 0.5f * pulse));
            }
            String label = total + (BlackjackTable.isSoft(cards) && total < 21 ? " (soft)" : "") + (hand[1] > 1 ? "  ×2" : "");
            ctx.centeredText(font, Component.literal(label), hx + width_ / 2 - 9, hy + CARD_H + 6, colour);
            hx += width_ + 24;
        }

        // Decision timer.
        if (state.phase() == BlackjackRevival.PLAYING) {
            float left = Math.max(0, state.ticksLeft() - (now - receivedAt) / 50f);
            float frac = left / (20f * 20f);
            int bw = 200;
            CraftUI.progressBar(ctx, cx - bw / 2, height - 52, bw, 4, frac, frac < 0.25f ? CraftUI.DANGER : CraftUI.GOLD);
            ctx.centeredText(font, Component.translatable("challengecraft.casino.blackjack.timer", (int) Math.ceil(left / 20f)),
                    cx, height - 64, CraftUI.TEXT_SECONDARY);
        }

        // Outcome.
        if (state.outcome() >= 0) {
            float k = Anim.easeOutCubic(Math.min(1f, (now - outcomeAt) / 400f));
            String key = state.outcome() == BlackjackRevival.WIN ? "challengecraft.casino.blackjack.win"
                    : state.outcome() == BlackjackRevival.LOSE ? "challengecraft.casino.blackjack.lose"
                    : "challengecraft.casino.blackjack.push";
            int colour = state.outcome() == BlackjackRevival.WIN ? CraftUI.GOLD
                    : state.outcome() == BlackjackRevival.LOSE ? CraftUI.DANGER : CraftUI.INFO;
            int by = height / 2 - 12;
            ctx.fill(0, by - 18, width, by + 26, CraftUI.applyAlpha(0xD0000000, k));
            ctx.fill(0, by - 18, width, by - 17, CraftUI.applyAlpha(colour, k));
            ctx.fill(0, by + 25, width, by + 26, CraftUI.applyAlpha(colour, k));
            CraftUI.drawCenteredScaled(ctx, font, Component.translatable(key).withStyle(ChatFormatting.BOLD), cx, by + 2,
                    1.6f + 1.0f * k, CraftUI.applyAlpha(colour, Math.max(0.1f, k)));
            ctx.centeredText(font, Component.translatable(key + ".sub"), cx, by + 16, CraftUI.applyAlpha(CraftUI.TEXT_SECONDARY, Math.max(0.1f, k)));
        }
    }

    private void drawAnimatedCard(GuiGraphicsExtractor ctx, String key, int card, int x, int y, int fromX, int fromY, long now) {
        Long seen = seenAt.get(key);
        if (seen == null) seen = now;
        float t = (now - seen) / 260f;
        if (t < 0) return; // still in the shoe
        float k = Anim.easeOutCubic(Math.min(1f, t));
        int px = Math.round(fromX + (x - fromX) * k);
        int py = Math.round(fromY + (y - fromY) * k);
        Long flip = flippedAt.get(key);
        float scaleX = 1f;
        int shown = card;
        if (flip != null) {
            float f = (now - flip) / 320f;
            if (f < 1f) {
                scaleX = Math.abs(1f - 2f * f);
                if (f < 0.5f) shown = -1;
            }
        }
        // Soft shadow under the card.
        ctx.fill(px + 2, py + 3, px + CARD_W + 2, py + CARD_H + 3, 0x50000000);
        ctx.pose().pushMatrix();
        ctx.pose().translate(px + CARD_W / 2f, py);
        ctx.pose().scale(Math.max(0.02f, scaleX), 1f);
        drawCard(ctx, shown, -CARD_W / 2, 0, 1f);
        ctx.pose().popMatrix();
    }

    private void drawCard(GuiGraphicsExtractor ctx, int card, int x, int y, float scale) {
        int col, row;
        if (card < 0) {
            col = 13;
            row = 0;
        } else {
            col = card % 13;
            row = card / 13;
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, CARDS, x, y, col * CELL_W, row * CELL_H, CARD_W, CARD_H, CELL_W, CELL_H, ATLAS_W, ATLAS_H);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
