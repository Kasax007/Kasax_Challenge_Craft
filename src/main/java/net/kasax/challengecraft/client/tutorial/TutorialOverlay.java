package net.kasax.challengecraft.client.tutorial;

import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the tutorial on top of whatever screen is open: a dimmed backdrop with a hole cut around
 * the thing to click, an arrow pointing at it, a text card, and the skip button.
 *
 * <p>The dim is drawn as four rectangles AROUND the highlight rather than one rectangle with a
 * blend over it — that keeps the highlighted widget at its true colours, so what the player is
 * being pointed at looks exactly like it will once the tutorial ends.
 */
public final class TutorialOverlay {
    private static final int DIM = 0xB0000000;
    private static final int CARD_W = 280;
    private static final int PAD = 10;

    /** Skip button geometry, recomputed each frame; also used for hit-testing. */
    private static int skipX, skipY, skipW = 92, skipH = 20;

    private TutorialOverlay() {
    }

    public static void render(GuiGraphicsExtractor ctx, Screen screen, int mouseX, int mouseY) {
        TutorialStep step = TutorialManager.current(screen);
        if (step == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = screen.width;
        int sh = screen.height;

        AbstractWidget target = TutorialManager.resolveTarget(step, screen);

        int[] hi = highlightRect(screen, target);

        if (hi != null) {
            int x1 = hi[0], y1 = hi[1], x2 = hi[2], y2 = hi[3];

            // Dim everything except the target.
            ctx.fill(0, 0, sw, y1, DIM);
            ctx.fill(0, y2, sw, sh, DIM);
            ctx.fill(0, y1, x1, y2, DIM);
            ctx.fill(x2, y1, sw, y2, DIM);

            // A POSITIVE highlight, not merely the absence of dimming. Leaving a hole in the dim
            // reads as "nothing here" on a busy screen — the eye needs something to land on, so the
            // target gets a warm wash and a double ring.
            ctx.fill(x1, y1, x2, y2, 0x30FFE3B3);
            ctx.outline(x1, y1, x2 - x1, y2 - y1, CraftUI.GOLD);
            ctx.outline(x1 - 1, y1 - 1, x2 - x1 + 2, y2 - y1 + 2, 0x60FFE3B3);
            drawArrow(ctx, x1, y1, x2, y2, sw);
        } else {
            ctx.fill(0, 0, sw, sh, DIM);
        }

        drawCard(ctx, font, step, screen, sw, sh, hi);
        drawSkip(ctx, font, sw, sh, mouseX, mouseY);
    }

    /**
     * Where the target actually IS on screen, as {@code {x1, y1, x2, y2}}, or null for no highlight.
     *
     * <p>Two corrections happen here, both invisible from the widget alone:
     *
     * <p>A child of a {@link net.kasax.challengecraft.client.screen.WidgetScrollPanel} reports its
     * LAYOUT position — the panel shifts it by the scroll amount, draws it, and shifts it back
     * within a single method — so reading {@code getY()} from outside gives a coordinate the widget
     * has not occupied since the panel was last scrolled. Highlighting that would ring blank
     * background somewhere below the card.
     *
     * <p>And a card can simply be scrolled out of the panel's visible band, where no coordinate
     * would be right. So the panel is scrolled to bring the target into view first, and the rect is
     * clipped to the panel afterwards — a highlight can never bleed over the panel's edges.
     */
    private static int[] highlightRect(Screen screen, AbstractWidget target) {
        if (target == null || !target.visible) return null;

        var panel = TutorialManager.panelOf(screen, target);
        int scroll = 0;
        if (panel != null) {
            // Measure against the offset the panel just DREW with, then ask it to scroll. Using the
            // post-scroll offset would place the ring one scroll-step away from the card for the
            // frame in which the correction happens; this way it simply appears a frame later, in
            // exactly the right place.
            scroll = panel.scrollOffset();
            panel.scrollIntoView(target);
        }

        int x1 = target.getX() - 4;
        int y1 = target.getY() - scroll - 4;
        int x2 = target.getX() + target.getWidth() + 4;
        int y2 = target.getY() - scroll + target.getHeight() + 4;

        if (panel != null) {
            y1 = Math.max(y1, panel.getY());
            y2 = Math.min(y2, panel.getBottom());
            if (y2 - y1 < 6) return null;   // still clipped away: better no ring than a wrong one
        }
        return new int[]{x1, y1, x2, y2};
    }

    /** A small triangle beside the highlight, on whichever side has room. */
    private static void drawArrow(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int sw) {
        boolean fromLeft = x1 > 60;
        int tipX = fromLeft ? x1 - 3 : x2 + 3;
        int cy = (y1 + y2) / 2;
        for (int i = 0; i < 8; i++) {
            int px = fromLeft ? tipX - i : tipX + i;
            ctx.fill(px, cy - i, px + 1, cy + i + 1, CraftUI.GOLD);
        }
    }

    private static void drawCard(GuiGraphicsExtractor ctx, Font font, TutorialStep step, Screen screen,
                                 int sw, int sh, int[] hi) {
        List<Component> lines = new ArrayList<>(step.body());

        // The difficulty step quotes the live value so it can never contradict the screen behind it.
        if ("difficulty".equals(step.id())) {
            double rating = TutorialManager.liveDifficulty(screen);
            lines.add(Component.translatable("challengecraft.tutorial.difficulty.value",
                    String.format("%.1f", rating), (int) Math.round(rating * 100)));
        }

        // Wrap to the card's inner width. Without this, any sentence longer than the card simply
        // ran off both sides — the text was drawn as one unbroken line.
        int innerW = CARD_W - PAD * 2;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component c : lines) {
            wrapped.addAll(font.split(c, innerW));
        }

        // The hint is part of the body, so it is wrapped and measured like everything else.
        List<FormattedCharSequence> hint = step.clickToAdvance()
                ? font.split(Component.translatable("challengecraft.tutorial.click_hint"), innerW)
                : List.of();

        int lineStep = font.lineHeight + 3;
        int cardH = PAD * 2 + font.lineHeight + 4 + wrapped.size() * lineStep
                + (hint.isEmpty() ? 0 : hint.size() * lineStep + 4);
        int cardX = (sw - CARD_W) / 2;
        // Place the card where it provably does not cover the highlight, instead of guessing from
        // which half of the screen the target sits in. Card 11 pointed at the Difficulty Rating and
        // landed straight on top of it, which is exactly the failure that guess produces.
        int cardY = placeCard(cardH, sw, sh, hi, cardX);

        CraftUI.panel(ctx, cardX, cardY, CARD_W, cardH);

        // Title, with the counter beside it — the title is truncated rather than allowed to collide.
        Component prog = Component.translatable("challengecraft.tutorial.progress_counter",
                TutorialState.getStep() + 1, TutorialManager.stepCount());
        int progW = font.width(prog.getString());
        ctx.text(font, font.split(step.title(), innerW - progW - 6).get(0),
                cardX + PAD, cardY + PAD, CraftUI.GOLD);
        ctx.text(font, prog, cardX + CARD_W - PAD - progW, cardY + PAD, 0xFF7F8FA6, false);

        int ty = cardY + PAD + font.lineHeight + 4;
        for (FormattedCharSequence line : wrapped) {
            ctx.text(font, line, cardX + PAD, ty, 0xFFD6E1F4);
            ty += lineStep;
        }
        if (!hint.isEmpty()) {
            ty += 4;
            for (FormattedCharSequence line : hint) {
                ctx.text(font, line, cardX + PAD, ty, CraftUI.GOLD);
                ty += lineStep;
            }
        }
    }

    /**
     * A vertical position for the card that clears both the highlight and the skip button.
     *
     * <p>Tries below the highlight, then above it, then falls back to whichever side has more room —
     * a card that must overlap is at least pushed to the roomier side rather than dropped on top of
     * the thing the player is being asked to look at.
     */
    private static int placeCard(int cardH, int sw, int sh, int[] hi, int cardX) {
        int topLimit = 8;
        int bottomLimit = sh - cardH - 40;     // leaves the skip button clear

        if (hi == null) {
            return Math.max(topLimit, Math.min(bottomLimit, 40));
        }

        boolean overlapsX = cardX < hi[2] && cardX + CARD_W > hi[0];
        if (!overlapsX) {
            // Different columns entirely — vertical position cannot collide.
            return Math.max(topLimit, Math.min(bottomLimit, 40));
        }

        int below = hi[3] + 10;
        if (below + cardH <= sh - 40) return below;

        int above = hi[1] - cardH - 10;
        if (above >= topLimit) return above;

        int roomAbove = hi[1];
        int roomBelow = sh - hi[3];
        return roomAbove > roomBelow ? topLimit : Math.max(topLimit, bottomLimit);
    }

    private static void drawSkip(GuiGraphicsExtractor ctx, Font font, int sw, int sh, int mouseX, int mouseY) {
        skipW = 92;
        skipH = 20;
        skipX = sw - skipW - 12;
        skipY = sh - skipH - 12;

        boolean hover = isOverSkip(mouseX, mouseY);
        ctx.fill(skipX, skipY, skipX + skipW, skipY + skipH, hover ? 0xF0202B3A : 0xE0151C27);
        ctx.outline(skipX, skipY, skipW, skipH, hover ? CraftUI.GOLD : 0xFF41516A);
        Component label = Component.translatable("challengecraft.tutorial.skip");
        ctx.centeredText(font, label, skipX + skipW / 2, skipY + (skipH - font.lineHeight) / 2 + 1,
                hover ? 0xFFF6FFF9 : 0xFFD6E1F4);
    }

    public static boolean isOverSkip(double mouseX, double mouseY) {
        return mouseX >= skipX && mouseX < skipX + skipW && mouseY >= skipY && mouseY < skipY + skipH;
    }

    /**
     * Handles a click while the tutorial is up.
     *
     * @return true if the tutorial consumed it (skip pressed, or a "read this" step advanced)
     */
    public static boolean onClick(Screen screen, double mouseX, double mouseY) {
        TutorialStep step = TutorialManager.current(screen);
        if (step == null) return false;

        if (isOverSkip(mouseX, mouseY)) {
            TutorialManager.skip();
            return true;
        }

        // "Click anywhere to continue" means ANYWHERE, and the click stops here.
        //
        // Letting it fall through to the screen underneath was worse than it sounds: the card is a
        // full-width panel sitting over a menu, so a player following the instruction would advance
        // the tutorial AND press whatever button happened to be beneath their cursor — leaving a
        // world created or a screen switched by accident. Consuming the click makes the instruction
        // literally true and removes that whole class of misfire.
        if (step.clickToAdvance()) {
            TutorialManager.advanceManually();
            return true;
        }
        return false;
    }
}
