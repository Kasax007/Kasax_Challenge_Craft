package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.client.ChallengeCodeClient;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The browsing half of both challenge pickers — the create-world tab and the in-world editor:
 * search, category and "show" filters, collapsible category sections, sliders under their card,
 * and the "does not combine with …" notes on the cards.
 *
 * <p>One class so the two screens cannot drift apart again. Each screen keeps what is its own —
 * which cards exist, what is ticked, how a change is saved — and hands its cards to
 * {@link #layout} whenever something changed. The ids' on-screen ORDER still comes from each
 * screen's {@code IDS} list (unlock order, checked by the consistency script).
 */
@Environment(EnvType.CLIENT)
public final class ChallengeBrowser {
    public enum Category {
        DROPS("challengecraft.category.drops"),
        WORLD("challengecraft.category.world"),
        COMBAT("challengecraft.category.combat"),
        CHAOS("challengecraft.category.chaos");

        public final String key;

        Category(String key) {
            this.key = key;
        }
    }

    /** Which section a challenge sorts into. A new challenge id must be added here. */
    public static Category categoryOf(int id) {
        return switch (id) {
            case 2, 3, 4, 14, 15, 34, 44 -> Category.DROPS;
            case 5, 7, 21, 23, 24, 27, 28, 29, 32, 35, 39, 41, 42, 43, 46, 47, 48 -> Category.COMBAT;
            case 10, 13, 16, 17, 18, 19, 20, 33, 36, 37 -> Category.CHAOS;
            default -> Category.WORLD;
        };
    }

    public enum Show {
        ALL("challengecraft.browser.show.all"),
        UNLOCKED("challengecraft.browser.show.unlocked"),
        ACTIVE("challengecraft.browser.show.active");

        final String key;

        Show(String key) {
            this.key = key;
        }
    }

    private final Runnable onChange;
    private String query = "";
    private Show show = Show.ALL;
    /** null = every category. */
    private Category only = null;
    private final boolean[] expanded = {true, true, true, true};
    private boolean perksExpanded = true;
    private int lastVisible;

    /** @param onChange re-lays-out the owning screen; called after every filter or section change */
    public ChallengeBrowser(Runnable onChange) {
        this.onChange = onChange;
    }

    // ---- controls --------------------------------------------------------------------------

    public EditBox searchBox(Font font, int x, int y, int width, int height) {
        EditBox box = new EditBox(font, x, y, width, height, Component.translatable("challengecraft.gui.search"));
        box.setHint(Component.translatable("challengecraft.browser.search_hint"));
        box.setMaxLength(40);
        box.setValue(query);
        box.setResponder(value -> {
            if (!value.equals(query)) {
                query = value;
                onChange.run();
            }
        });
        return box;
    }

    public CraftButton categoryButton(int x, int y, int width, int height) {
        CraftButton[] self = new CraftButton[1];
        self[0] = new CraftButton(x, y, width, height, categoryLabel(), CraftButton.Style.NEUTRAL, b -> {
            Category[] all = Category.values();
            only = only == null ? all[0] : (only.ordinal() + 1 < all.length ? all[only.ordinal() + 1] : null);
            self[0].setMessage(categoryLabel());
            onChange.run();
        });
        return self[0];
    }

    public CraftButton showButton(int x, int y, int width, int height) {
        CraftButton[] self = new CraftButton[1];
        self[0] = new CraftButton(x, y, width, height, showLabel(), CraftButton.Style.NEUTRAL, b -> {
            show = Show.values()[(show.ordinal() + 1) % Show.values().length];
            self[0].setMessage(showLabel());
            onChange.run();
        });
        return self[0];
    }

    private Component categoryLabel() {
        return only == null ? Component.translatable("challengecraft.browser.category.all")
                : Component.translatable(only.key);
    }

    private Component showLabel() {
        return Component.translatable(show.key);
    }

    public boolean isFiltering() {
        return !query.isBlank() || show != Show.ALL || only != null;
    }

    // ---- filtering -------------------------------------------------------------------------

    public boolean visible(ChallengeCardWidget card) {
        boolean perk = card.getChallengeId() > 100;
        if (only != null && (perk || categoryOf(card.getChallengeId()) != only)) {
            return false;
        }
        if (show == Show.UNLOCKED && card.isLevelLocked()) {
            return false;
        }
        if (show == Show.ACTIVE && !card.isActive()) {
            return false;
        }
        if (!query.isBlank()) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            return card.getTitle().getString().toLowerCase(Locale.ROOT).contains(q);
        }
        return true;
    }

    // ---- layout ----------------------------------------------------------------------------

    /**
     * Lays every visible card, its slider and the section headers into {@code panel} from {@code y}
     * down, two columns wide. Returns the y below the last row.
     */
    public int layout(WidgetScrollPanel panel, int x0, int y, int width, int gap, int cardH,
                      List<Integer> order, Map<Integer, ChallengeCardWidget> cards,
                      Map<Integer, ? extends AbstractWidget> sliders, List<ChallengeCardWidget> perks) {
        int cardW = (width - gap) / 2;
        int x1 = x0 + cardW + gap;
        lastVisible = 0;

        for (Category category : Category.values()) {
            if (only != null && category != only) {
                continue;
            }
            List<ChallengeCardWidget> inSection = new ArrayList<>();
            int active = 0;
            int total = 0;
            for (int id : order) {
                if (categoryOf(id) != category) continue;
                ChallengeCardWidget card = cards.get(id);
                total++;
                if (card.isActive()) active++;
                if (visible(card)) inSection.add(card);
            }
            // While filtering, an empty section is noise; without a filter every section shows.
            if (inSection.isEmpty() && isFiltering()) {
                continue;
            }
            int index = category.ordinal();
            panel.addChild(new SectionHeader(x0, y, width, sectionLabel(Component.translatable(category.key), active, total),
                    expanded[index] || isFiltering(), () -> {
                        expanded[index] = !expanded[index];
                        onChange.run();
                    }));
            y += 20;
            if (!expanded[index] && !isFiltering()) {
                y += 4;
                continue;
            }
            y = grid(panel, inSection, sliders, x0, x1, y, width, cardW, cardH, gap) + 8;
        }

        if (only == null) {
            List<ChallengeCardWidget> visiblePerks = new ArrayList<>();
            int active = 0;
            for (ChallengeCardWidget perk : perks) {
                if (perk.isActive()) active++;
                if (visible(perk)) visiblePerks.add(perk);
            }
            if (!visiblePerks.isEmpty() || !isFiltering()) {
                panel.addChild(new SectionHeader(x0, y, width,
                        sectionLabel(Component.translatable("challengecraft.challenge_selection.perks_header"), active, perks.size()),
                        perksExpanded || isFiltering(), () -> {
                            perksExpanded = !perksExpanded;
                            onChange.run();
                        }));
                y += 20;
                if (perksExpanded || isFiltering()) {
                    y = grid(panel, visiblePerks, Map.of(), x0, x1, y, width, cardW, cardH, gap);
                }
            }
        }

        if (lastVisible == 0) {
            panel.addChild(new Note(x0, y + 6, width, Component.translatable("challengecraft.browser.no_results")));
            y += 26;
        }
        return y;
    }

    private int grid(WidgetScrollPanel panel, List<ChallengeCardWidget> list, Map<Integer, ? extends AbstractWidget> sliders,
                     int x0, int x1, int y, int width, int cardW, int cardH, int gap) {
        int col = 0;
        for (ChallengeCardWidget card : list) {
            card.setX(col == 0 ? x0 : x1);
            card.setY(y);
            card.setWidth(cardW);
            card.setHeight(cardH);
            panel.addChild(card);
            lastVisible++;
            if (col == 1) {
                y += cardH + gap;
                col = 0;
            } else {
                col = 1;
            }
            AbstractWidget slider = sliders.get(card.getChallengeId());
            if (slider != null) {
                // A slider spans the full width, directly under its own card.
                if (col == 1) {
                    y += cardH + gap;
                    col = 0;
                }
                slider.setX(x0);
                slider.setY(y);
                slider.setWidth(width);
                slider.setHeight(20);
                panel.addChild(slider);
                y += 20 + gap;
            }
        }
        if (col == 1) {
            y += cardH + gap;
        }
        return y;
    }

    private static Component sectionLabel(Component name, int active, int total) {
        return name.copy().append(Component.literal("  " + active + "/" + total).withColor(CraftUI.TEXT_MUTED));
    }

    // ---- conflicts -------------------------------------------------------------------------

    /**
     * Marks every card that collides with what is switched on — an inactive one that could not be
     * added, an active one that is part of a clash — with the names it collides with.
     */
    public static void refreshConflicts(Collection<ChallengeCardWidget> all, List<Integer> ids, List<Integer> perks) {
        for (ChallengeCardWidget card : all) {
            List<Integer> with = ChallengeManager.conflictsWith(card.getChallengeId(), ids, perks);
            if (with.isEmpty()) {
                card.setConflictNote(null);
            } else {
                MutableComponent names = Component.empty();
                for (int i = 0; i < with.size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(ChallengeCodeClient.name(with.get(i)));
                }
                card.setConflictNote(Component.translatable("challengecraft.conflict.with", names));
            }
        }
    }

    /** "X does not combine with Y" for the first clash, or null. */
    public static Component conflictLine(List<Integer> ids, List<Integer> perks) {
        int[] pair = ChallengeManager.findConflict(ids, perks);
        return pair == null ? null : Component.translatable("challengecraft.conflict.pair",
                ChallengeCodeClient.name(pair[0]), ChallengeCodeClient.name(pair[1]));
    }

    // ---- widgets ---------------------------------------------------------------------------

    /** Collapsible section title with a caret, the section's "active/total" count and a gold rule. */
    public static final class SectionHeader extends AbstractWidget {
        private final boolean expanded;
        private final Runnable onToggle;
        private final Anim.Tween hover = new Anim.Tween(0f);

        SectionHeader(int x, int y, int width, Component label, boolean expanded, Runnable onToggle) {
            super(x, y, width, 16, label);
            this.expanded = expanded;
            this.onToggle = onToggle;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            Font font = Minecraft.getInstance().font;
            float g = hover.approach(isHovered() ? 1f : 0f, 12f);
            int color = CraftUI.mix(CraftUI.TEXT_SECONDARY, CraftUI.TEXT_PRIMARY, g);
            int caret = CraftUI.mix(CraftUI.GOLD, 0xFFF3D88A, g);
            int x = getX();
            int y = getY() + 3;
            for (int i = 0; i < 4; i++) {
                if (expanded) {
                    context.fill(x + i, y + i, x + 7 - i, y + i + 1, caret);
                } else {
                    context.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, caret);
                }
            }
            context.text(font, getMessage(), x + 12, y, color, false);
            int underlineY = y + font.lineHeight + 1;
            context.fill(x, underlineY, x + getWidth(), underlineY + 1, CraftUI.applyAlpha(CraftUI.GOLD, 0.6f));
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            onToggle.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }

    /** A muted line of text inside the panel, for "nothing matches". */
    private static final class Note extends AbstractWidget {
        Note(int x, int y, int width, Component text) {
            super(x, y, width, 14, text);
            this.active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + 3,
                    CraftUI.TEXT_MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }
    }
}
