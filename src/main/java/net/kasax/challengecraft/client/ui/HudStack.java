package net.kasax.challengecraft.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Auto-layout manager for the objective HUDs. Sources register a {@link HudCard} supplier (which
 * returns {@code null} while inactive); each frame the stack centres all active cards per row,
 * slides new cards in from above, and flashes a card when its progress advances. This removes the
 * old hand-tuned magic-number positioning where each HUD hard-coded the others' assumed offsets.
 */
@Environment(EnvType.CLIENT)
public final class HudStack {
    private HudStack() {
    }

    private static final int GAP = 6;
    private static final int TOP = 6;

    /**
     * Id of the single HUD element the whole stack draws through. 26.2's HUD is element-based
     * rather than a draw callback, but the stack still needs to see every card in ONE pass (it
     * centres a whole row against the screen), so it stays one element rather than one per card.
     */
    private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath("challengecraft", "hud_stack");

    private record Source(Supplier<HudCard> supplier, int row) {
    }

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final Map<String, State> STATES = new HashMap<>();

    private static final class State {
        final Anim.Tween appear = new Anim.Tween(0f);
        int lastKey = Integer.MIN_VALUE;
        long flashAt = 0L;
    }

    /** {@code row} 0 is the top objective row; row 1 sits just below it (e.g. the target HUD). */
    public static void addSource(Supplier<HudCard> supplier, int row) {
        SOURCES.add(new Source(supplier, row));
    }

    public static void register() {
        // addLast puts the stack after every vanilla element, which is where the old
        // HudRenderCallback drew — so the cards keep sitting on top of the rest of the HUD.
        HudElementRegistry.addLast(ELEMENT_ID, (context, tickCounter) -> extract(context));
    }

    private static void extract(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        // hideGui moved off Options and onto the Hud itself in 26.2.
        if (client.gui.hud.isHidden() || client.font == null) {
            return;
        }
        Font tr = client.font;

        // Bucket active cards by row.
        Map<Integer, List<HudCard>> rows = new HashMap<>();
        Set<String> activeIds = new HashSet<>();
        for (Source source : SOURCES) {
            HudCard card = source.supplier.get();
            if (card != null) {
                rows.computeIfAbsent(source.row, k -> new ArrayList<>()).add(card);
                activeIds.add(card.id);
            }
        }
        if (rows.isEmpty()) {
            resetInactive(activeIds);
            return;
        }

        int sw = client.getWindow().getGuiScaledWidth();
        long now = System.currentTimeMillis();
        // Cards grow with their content but never wider than the screen (minus a small margin).
        int maxCardWidth = sw - 12;

        for (Map.Entry<Integer, List<HudCard>> entry : rows.entrySet()) {
            List<HudCard> cards = entry.getValue();
            // Compute each card's width once so layout and render stay consistent.
            Map<HudCard, Integer> widths = new HashMap<>();
            int total = -GAP;
            for (HudCard card : cards) {
                int w = card.width(tr, maxCardWidth);
                widths.put(card, w);
                total += w + GAP;
            }
            int x = (sw - total) / 2;
            int rowY = TOP + entry.getKey() * (HudCard.HEIGHT + GAP);

            for (HudCard card : cards) {
                State state = STATES.computeIfAbsent(card.id, k -> new State());
                float appear = state.appear.approach(1f, 11f);

                if (card.progressKey != state.lastKey) {
                    if (state.lastKey != Integer.MIN_VALUE) {
                        state.flashAt = now;
                    }
                    state.lastKey = card.progressKey;
                }
                float flash = Math.max(0f, 1f - (now - state.flashAt) / 400f);

                int slide = Math.round((1f - appear) * -(HudCard.HEIGHT + 10));
                int w = widths.get(card);
                card.extractRenderState(context, tr, x, rowY + slide, flash, w);
                x += w + GAP;
            }
        }

        resetInactive(activeIds);
    }

    /** Cards that stopped being active reset so they slide in again next time they appear. */
    private static void resetInactive(Set<String> activeIds) {
        for (Map.Entry<String, State> entry : STATES.entrySet()) {
            if (!activeIds.contains(entry.getKey())) {
                entry.getValue().appear.set(0f);
                entry.getValue().lastKey = Integer.MIN_VALUE;
            }
        }
    }
}
