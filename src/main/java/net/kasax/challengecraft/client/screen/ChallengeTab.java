package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.SettableSlider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
/** Create-world challenge configuration tab shown before the first server boot. */
public class ChallengeTab extends GridLayoutTab {
    private static final Component TITLE = Component.translatable("challengecraft.challenge_tab.title");
    private static final List<Integer> IDS = new ArrayList<>(List.of(
            1, 10, 16, 17, 18, 40, 45, 4, 5, 42, 6, 7, 37, 8, 13, 43, 11, 27, 12, 20, 26, 44, 21, 38, 41, 48, 30, 24, 28, 31, 25, 46, 32, 9, 47, 29, 33, 2, 3, 39, 34, 23, 49, 14, 36, 15, 35, 19, 22
    ));

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private final List<ChallengeCardWidget> perkCards = new ArrayList<>();
    private final SettableSlider maxHealthSlider;
    private final SettableSlider inventorySlider;
    private final SettableSlider mobHealthSlider;
    private final SettableSlider doubleTroubleSlider;
    private final SettableSlider gameSpeedSlider;
    private final SettableSlider fibMinutesSlider;
    private Component difficultyText = Component.empty();
    private boolean hasConflict;
    private double currentDifficulty = -1;

    private WidgetScrollPanel scrollPanel;
    private AbstractWidget infoBar;
    private final net.kasax.challengecraft.client.widget.CraftButton codeButton;
    private final net.kasax.challengecraft.client.widget.CraftButton presetButton;
    /** Search, category/show filters and sections — shared with the in-world editor. */
    private final ChallengeBrowser browser = new ChallengeBrowser(this::relayout);
    private final net.minecraft.client.gui.components.EditBox searchBox;
    private final net.kasax.challengecraft.client.widget.CraftButton categoryButton;
    private final net.kasax.challengecraft.client.widget.CraftButton showButton;
    private final java.util.Map<Integer, ChallengeCardWidget> cardById = new java.util.HashMap<>();
    private final java.util.Map<Integer, AbstractWidget> sliderById = new java.util.HashMap<>();
    private ScreenRectangle lastArea;
    private Component conflictLine;
    private java.util.function.Supplier<String> seedGetter;
    private java.util.function.Consumer<String> seedSetter;
    private boolean reopenOnReturn;

    // Slider values are normalized for the widget; ticks keep the exact gameplay units.
    private double sliderValue = 1.0;
    private int sliderTicks = (int)(Math.round(sliderValue * 19) + 1);

    private double inventorySliderValue = 1.0;
    private int inventorysliderTicks = (int)(Math.round(inventorySliderValue * 35) + 1);

    private double mobHealthSliderValue = 0.0;
    private int mobHealthMultiplier = 1;

    private double doubleTroubleSliderValue = 0.0;
    private int doubleTroubleMultiplier = 2;

    private double gameSpeedSliderValue = 0.0;
    private int gameSpeedMultiplier = 1;

    // 15–180 min in 5-minute steps; 60 min default.
    private double fibMinutesSliderValue = (60 - 15) / 165.0;
    private int fibMinutes = 60;

    public ChallengeTab() {
        super(TITLE);

        // The tab is built at the title screen, after the DISCONNECT handler has zeroed
        // LOCAL_PLAYER_XP — without this refresh every card constructed below reads level 1
        // and locks itself until something else (e.g. opening the leveling screen) refreshes it.
        ChallengeCraftClient.refreshLocalPlayerXp();

        for (int id : IDS) {
            ChallengeCardWidget card = new ChallengeCardWidget(0, 0, 100, 20, id, false, val -> onSelectionChanged());
            cards.add(card);
        }

        for (int perkId : net.kasax.challengecraft.LevelManager.ALL_PERKS) {
            ChallengeCardWidget perkCard = new ChallengeCardWidget(0, 0, 100, 20, perkId, false, val -> onSelectionChanged());
            perkCards.add(perkCard);
        }

        this.maxHealthSlider = new SettableSlider(
                0, 0, 210, 20,
                getHealthSliderText(0.5 + (sliderValue * 9.5)),
                sliderValue
        ) {
            @Override
            protected void updateMessage() {
                double hearts = 0.5 + (this.value * 9.5);
                setMessage(getHealthSliderText(hearts));
            }

            @Override
            protected void applyValue() {
                // Health is saved in half-heart ticks to match vanilla health math.
                sliderTicks = (int)(Math.round(this.value * 19) + 1);
                this.value  = (sliderTicks - 1) / 19.0;
                updateDifficultyText();
            }
        };

        this.inventorySlider = new SettableSlider(
                0, 0, 210, 20,
                getSlotsSliderText(36),
                inventorySliderValue
        ) {
            @Override
            protected void updateMessage() {
                double slots = 1 + (this.value * 35);
                setMessage(getSlotsSliderText(slots));
            }

            @Override
            protected void applyValue() {
                inventorysliderTicks = (int)(Math.round(this.value * 35) + 1);
                this.value  = (inventorysliderTicks - 1) / 35.0;
                updateDifficultyText();
            }
        };

        this.mobHealthSlider = new SettableSlider(
                0, 0, 210, 20,
                getMobHealthSliderText(1),
                mobHealthSliderValue
        ) {
            @Override
            protected void updateMessage() {
                double mult = 1 + (this.value * 99);
                setMessage(getMobHealthSliderText(mult));
            }

            @Override
            protected void applyValue() {
                mobHealthMultiplier = (int)(Math.round(this.value * 99) + 1);
                this.value = (mobHealthMultiplier - 1) / 99.0;
                updateDifficultyText();
            }
        };

        this.doubleTroubleSlider = new SettableSlider(
                0, 0, 210, 20,
                getDoubleTroubleSliderText(2),
                doubleTroubleSliderValue
        ) {
            @Override
            protected void updateMessage() {
                double mult = 2 + (this.value * 8);
                setMessage(getDoubleTroubleSliderText(mult));
            }

            @Override
            protected void applyValue() {
                doubleTroubleMultiplier = (int)(Math.round(this.value * 8) + 2);
                this.value = (doubleTroubleMultiplier - 2) / 8.0;
                updateDifficultyText();
            }
        };

        this.gameSpeedSlider = new SettableSlider(
                0, 0, 210, 20,
                getGameSpeedSliderText(1),
                gameSpeedSliderValue
        ) {
            @Override
            protected void updateMessage() {
                double mult = 1 + (this.value * 9);
                setMessage(getGameSpeedSliderText(mult));
            }

            @Override
            protected void applyValue() {
                gameSpeedMultiplier = (int)(Math.round(this.value * 9) + 1);
                this.value = (gameSpeedMultiplier - 1) / 9.0;
                updateDifficultyText();
            }
        };

        this.fibMinutesSlider = new SettableSlider(
                0, 0, 210, 20,
                getFibMinutesSliderText(fibMinutes),
                fibMinutesSliderValue
        ) {
            @Override
            protected void updateMessage() {
                setMessage(getFibMinutesSliderText(15 + (int) Math.round(this.value * 33) * 5));
            }

            @Override
            protected void applyValue() {
                fibMinutes = 15 + (int) Math.round(this.value * 33) * 5;
                this.value = (fibMinutes - 15) / 165.0;
                updateDifficultyText();
            }
        };

        // CreateWorldScreen keeps this widget reference, so later refreshes must reuse it.
        this.scrollPanel = new WidgetScrollPanel(0, 0, 1, 1, Component.empty());

        // Pinned info bar showing difficulty + projected XP payout at the top of the tab.
        this.infoBar = new DifficultyBar();

        this.codeButton = new net.kasax.challengecraft.client.widget.CraftButton(0, 0, 90, 20,
                Component.translatable("challengecraft.code.button"),
                net.kasax.challengecraft.client.widget.CraftButton.Style.NEUTRAL, b -> openCodeScreen());
        this.presetButton = new net.kasax.challengecraft.client.widget.CraftButton(0, 0, 90, 20,
                Component.translatable("challengecraft.preset.button"),
                net.kasax.challengecraft.client.widget.CraftButton.Style.NEUTRAL, b -> openPresetScreen());

        var font = net.minecraft.client.Minecraft.getInstance().font;
        this.searchBox = browser.searchBox(font, 0, 0, 100, 16);
        this.categoryButton = browser.categoryButton(0, 0, 90, 18);
        this.showButton = browser.showButton(0, 0, 90, 18);
        for (int i = 0; i < IDS.size(); i++) {
            cardById.put(IDS.get(i), cards.get(i));
        }
        sliderById.put(7, maxHealthSlider);
        sliderById.put(12, inventorySlider);
        sliderById.put(24, mobHealthSlider);
        sliderById.put(35, doubleTroubleSlider);
        sliderById.put(37, gameSpeedSlider);
        sliderById.put(45, fibMinutesSlider);

        applyDailyLockIfArmed();
        onSelectionChanged();
    }

    private void onSelectionChanged() {
        updateDifficultyText();
        List<ChallengeCardWidget> all = new ArrayList<>(cards);
        all.addAll(perkCards);
        ChallengeBrowser.refreshConflicts(all, activeIds(), selectedPerks());
    }

    /** Filters or sections changed: lay the panel out again in the area the tab last had. */
    private void relayout() {
        if (lastArea != null) {
            double keep = scrollPanel.scrollAmount();
            doLayout(lastArea);
            scrollPanel.setScrollAmount(keep);
        }
    }

    /** The on-screen order of challenge ids; also the set of ids this build knows. */
    public static List<Integer> challengeIds() {
        return List.copyOf(IDS);
    }

    /**
     * Wires the seed field of the World tab, which this tab otherwise cannot see. Set by
     * {@code CreateWorldScreenMixin}; a code carries a seed and the share code needs the typed one.
     */
    public void bindSeed(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {
        this.seedGetter = getter;
        this.seedSetter = setter;
    }

    /** The setup on screen right now as a code, with the seed from the World tab if one is typed. */
    private net.kasax.challengecraft.code.ChallengeCode currentCode() {
        Long seed = null;
        if (seedGetter != null) {
            var parsed = net.minecraft.world.level.levelgen.WorldOptions.parseSeed(seedGetter.get());
            if (parsed.isPresent()) {
                seed = parsed.getAsLong();
            }
        }
        return net.kasax.challengecraft.code.ChallengeCode.of(activeIds(), selectedPerks(), sliderTicks, inventorysliderTicks,
                mobHealthMultiplier, doubleTroubleMultiplier, gameSpeedMultiplier, fibMinutes, seed);
    }

    private void openCodeScreen() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var parent = mc.gui.screen();
        // Back on this tab afterwards, whether a code was loaded or the dialog was just closed.
        reopenOnReturn = true;
        mc.setScreenAndShow(new ChallengeCodeScreen(parent, currentCode(),
                Component.translatable("challengecraft.code.hint.create_world"), code -> loadSetup(code, parent)));
    }

    private void openPresetScreen() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var parent = mc.gui.screen();
        reopenOnReturn = true;
        mc.setScreenAndShow(new PresetScreen(parent, currentCode(),
                Component.translatable("challengecraft.code.hint.create_world"), code -> loadSetup(code, parent)));
    }

    /** A code or preset was chosen: cards, sliders and seed, then back to world creation. */
    private void loadSetup(net.kasax.challengecraft.code.ChallengeCode code, net.minecraft.client.gui.screens.Screen parent) {
        applySetup(code);
        if (code.hasSeed() && seedSetter != null) {
            seedSetter.accept(Long.toString(code.seed()));
        }
        net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent);
    }

    /**
     * Returning from the code dialog re-runs {@code CreateWorldScreen.init}, which selects the first
     * tab. Read (and cleared) by the mixin so the player lands back here instead.
     */
    public boolean consumeReopen() {
        boolean r = reopenOnReturn;
        reopenOnReturn = false;
        return r;
    }

    /**
     * Puts a whole setup on the cards and sliders, as if the player had clicked it together. Used for
     * codes (perks included) and for the daily (no perks).
     */
    public void applySetup(net.kasax.challengecraft.code.ChallengeCode code) {
        for (int i = 0; i < IDS.size(); i++) {
            cards.get(i).setActive(code.challengeIds().contains(IDS.get(i)));
        }
        for (ChallengeCardWidget perkCard : perkCards) {
            perkCard.setActive(code.perkIds().contains(perkCard.getChallengeId()));
        }
        maxHealthSlider.setSliderValue((code.maxHearts() - 1) / 19.0);
        inventorySlider.setSliderValue((code.inventorySlots() - 1) / 35.0);
        mobHealthSlider.setSliderValue((code.mobHealth() - 1) / 99.0);
        doubleTroubleSlider.setSliderValue((code.doubleTrouble() - 2) / 8.0);
        gameSpeedSlider.setSliderValue((code.gameSpeed() - 1) / 9.0);
        fibMinutesSlider.setSliderValue((code.fibMinutes() - 15) / 165.0);
        onSelectionChanged();
    }

    /**
     * Pre-fills and freezes the tab when the player came here through the daily card.
     *
     * <p>Every card stays visible and readable — the daily's are switched on, the rest are switched
     * off, and none of them respond to a click. Hiding the others would make the tab look broken;
     * showing them locked makes it obvious that today's ruleset is fixed and what it consists of.
     * The sliders go with them, because a daily whose mob health you can dial down is not the same
     * run for everyone.
     *
     * <p>The sliders are also SET from the entry. They used to be only frozen, at their defaults, and
     * {@link #getActive()} sends what the sliders say — so a daily started from the title screen
     * lost its hearts, slots and multipliers ("Fragile" ran with ten hearts instead of three), while
     * the same daily started from {@code /challenges} had them.
     */
    private void applyDailyLockIfArmed() {
        var entry = net.kasax.challengecraft.client.DailyClientState.entry();
        if (entry == null) return;

        applySetup(net.kasax.challengecraft.client.ChallengeCodeClient.ofDaily(entry));
        for (int i = 0; i < IDS.size(); i++) {
            ChallengeCardWidget card = cards.get(i);
            card.setActive(entry.challengeIds().contains(IDS.get(i)));
            card.setRuleLocked(true);
        }
        // Perks are off for a daily: they differ per player and would make times incomparable.
        for (ChallengeCardWidget perkCard : perkCards) {
            perkCard.setActive(false);
            perkCard.setRuleLocked(true);
        }
        if (maxHealthSlider != null) maxHealthSlider.active = false;
        if (inventorySlider != null) inventorySlider.active = false;
        if (mobHealthSlider != null) mobHealthSlider.active = false;
        if (doubleTroubleSlider != null) doubleTroubleSlider.active = false;
        if (gameSpeedSlider != null) gameSpeedSlider.active = false;
        if (fibMinutesSlider != null) fibMinutesSlider.active = false;
    }

    private void updateDifficultyText() {
        List<Integer> activeIds = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (cards.get(i).isActive()) {
                activeIds.add(IDS.get(i));
            }
        }
        List<Integer> activePerks = getSelectedPerks();

        this.conflictLine = ChallengeBrowser.conflictLine(activeIds, activePerks);
        if (this.conflictLine != null) {
            this.hasConflict = true;
            this.currentDifficulty = -1;
            this.difficultyText = this.conflictLine;
        } else {
            this.hasConflict = false;
            int playerCount = 0;
            if (net.minecraft.client.Minecraft.getInstance().level != null) {
                playerCount = net.minecraft.client.Minecraft.getInstance().level.players().size();
            }
            double total = ChallengeManager.calculateTotalDifficulty(activeIds, sliderTicks, inventorysliderTicks, mobHealthMultiplier, gameSpeedMultiplier, doubleTroubleMultiplier, playerCount, activePerks);
            this.currentDifficulty = total;
            this.difficultyText = Component.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", total));
        }
    }

    /**
     * Two control rows above the list — search + category + show + code, then the difficulty bar —
     * and below them the challenges in collapsible category sections with their sliders directly
     * underneath, perks last. It used to be one long two-column wall of 48 cards in unlock order,
     * with the sliders squeezed into the second column wherever they happened to fall.
     */
    @Override
    public void doLayout(ScreenRectangle tabArea) {
        this.lastArea = tabArea;
        int padding = 6;
        int gap = 4;

        int panelX = tabArea.left() + padding;
        int panelY = tabArea.top() + padding;
        int panelW = Math.max(60, tabArea.width() - padding * 2);
        int panelH = Math.max(60, tabArea.height() - padding * 2);

        // Row 1: [ search ............ ][ category ][ show ][ code ]
        int buttonW = Math.min(90, Math.max(56, panelW / 6));
        int codeW = Math.min(70, Math.max(48, panelW / 8));
        int searchW = Math.max(60, panelW - 2 * buttonW - 2 * codeW - 4 * gap);
        this.searchBox.setX(panelX + 1);
        this.searchBox.setY(panelY + 1);
        this.searchBox.setWidth(searchW - 2);
        this.searchBox.setHeight(16);
        this.categoryButton.setX(panelX + searchW + gap);
        this.categoryButton.setY(panelY);
        this.categoryButton.setWidth(buttonW);
        this.showButton.setX(panelX + searchW + gap + buttonW + gap);
        this.showButton.setY(panelY);
        this.showButton.setWidth(buttonW);
        this.codeButton.setX(panelX + panelW - codeW);
        this.codeButton.setY(panelY);
        this.codeButton.setWidth(codeW);
        this.codeButton.setHeight(18);
        this.presetButton.setX(panelX + panelW - 2 * codeW - gap);
        this.presetButton.setY(panelY);
        this.presetButton.setWidth(codeW);
        this.presetButton.setHeight(18);
        this.presetButton.active = !net.kasax.challengecraft.client.DailyClientState.isArmed();
        // A daily's ruleset is fixed; loading a code over it would silently turn it into something else.
        this.codeButton.active = !net.kasax.challengecraft.client.DailyClientState.isArmed();

        // Row 2: difficulty and payout, full width.
        int infoBarH = 20;
        int infoY = panelY + 18 + gap;
        this.infoBar.setX(panelX);
        this.infoBar.setY(infoY);
        this.infoBar.setWidth(panelW);
        this.infoBar.setHeight(infoBarH);

        int listY = infoY + infoBarH + gap;
        this.scrollPanel.setX(panelX);
        this.scrollPanel.setY(listY);
        this.scrollPanel.setWidth(panelW);
        this.scrollPanel.setHeight(Math.max(40, panelY + panelH - listY));

        // Child bounds depend on the current tab size, but the panel instance stays stable.
        this.scrollPanel.clearChildren();

        int x0 = panelX + 8;
        int innerW = panelW - 24;
        int y = listY + 4;

        // When the player came here through the daily card, the tab has to SAY so. Everything below
        // is pre-ticked and frozen, and at low levels the untaken cards additionally render as
        // level-locked — without this header the screen reads as "nothing is selected and most of
        // it is unavailable", which is the opposite of what happened.
        if (net.kasax.challengecraft.client.DailyClientState.isArmed() && !browser.isFiltering()) {
            var header = new net.kasax.challengecraft.client.widget.DailyCardWidget(
                            x0, y, innerW, () -> { })
                    .withSelected(() -> true);
            // Read-only here. The choice was made on the title screen; this only reports it, and
            // the icons still answer "which challenges am I actually getting" on hover.
            header.active = false;
            scrollPanel.addChild(header);
            y += net.kasax.challengecraft.client.widget.DailyCardWidget.HEIGHT + gap + 4;
        }

        // Infinity Weapon is a star reward, so it should not appear before that milestone.
        List<ChallengeCardWidget> shownPerks = new ArrayList<>();
        for (ChallengeCardWidget perkCard : perkCards) {
            if (perkCard.getChallengeId() == net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON
                    && net.kasax.challengecraft.LevelManager.getStars(ChallengeCraftClient.LOCAL_PLAYER_XP) < 20) {
                continue;
            }
            shownPerks.add(perkCard);
        }
        browser.layout(scrollPanel, x0, y, innerW, gap, 26, IDS, cardById, sliderById, shownPerks);
    }

    /**
     * The pinned Difficulty Rating / XP payout bar.
     *
     * <p>A named class rather than an anonymous one purely so other code can FIND it. Its message is
     * {@link Component#empty()} — the text is composed at draw time — so any lookup by label misses
     * it; the tutorial step that explains the rating was pointing at nothing and dropping its card on
     * top of the bar as a result. A type is stable in a way a rendered English string is not.
     */
    public class DifficultyBar extends AbstractWidget {
        DifficultyBar() {
            super(0, 0, 1, 20, Component.empty());
        }

        @Override
        protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            CraftUI.panelFloat(context, getX(), getY(), getWidth(), getHeight(), CraftUI.GOLD);
            var tr = net.minecraft.client.Minecraft.getInstance().font;
            Component line;
            int color;
            if (hasConflict) {
                line = conflictLine != null ? conflictLine : Component.translatable("challengecraft.warning.conflict");
                color = CraftUI.DANGER;
            } else {
                long payout = currentDifficulty <= 0 ? 0 : Math.round(100.0 * currentDifficulty);
                line = Component.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", currentDifficulty))
                        .copy().append(Component.literal("   •   "))
                        .append(Component.translatable("challengecraft.worldcreate.xp_payout", String.format(Locale.ROOT, "%,d", payout)));
                color = CraftUI.WARNING;
            }
            context.centeredText(tr, line, getX() + getWidth() / 2, getY() + (getHeight() - tr.lineHeight) / 2 + 1, color);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
        }
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        consumer.accept(this.searchBox);
        consumer.accept(this.categoryButton);
        consumer.accept(this.showButton);
        consumer.accept(this.presetButton);
        consumer.accept(this.codeButton);
        consumer.accept(this.infoBar);
        consumer.accept(this.scrollPanel);
    }

    private List<Integer> selectedPerks() {
        List<Integer> active = new ArrayList<>();
        for (ChallengeCardWidget card : perkCards) {
            if (card.isActive()) {
                active.add(card.getChallengeId());
            }
        }
        return active;
    }

    public List<Integer> getSelectedPerks() {
        List<Integer> active = new ArrayList<>();
        for (ChallengeCardWidget card : perkCards) {
            if (card.isActive()) {
                active.add(card.getChallengeId());
            }
        }
        ChallengeCraftClient.SELECTED_PERKS = active;
        return active;
    }

    /** The ticked challenge ids, without {@link #getActive()}'s side effect on the client statics. */
    private List<Integer> activeIds() {
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (cards.get(i).isActive()) {
                active.add(IDS.get(i));
            }
        }
        return active;
    }

    public List<Integer> getActive() {
        List<Integer> active = activeIds();

        if (maxHealthSlider != null && active.contains(7)) {
            ChallengeCraftClient.SELECTED_MAX_HEARTS = sliderTicks;
        } else {
            ChallengeCraftClient.SELECTED_MAX_HEARTS = 20;
        }
        
        if (inventorySlider != null && active.contains(12)) {
            ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = inventorysliderTicks;
        } else {
            ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = 36;
        }

        if (mobHealthSlider != null && active.contains(24)) {
            ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = mobHealthMultiplier;
        } else {
            ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = 1;
        }

        if (doubleTroubleSlider != null && active.contains(35)) {
            ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER = doubleTroubleMultiplier;
        } else {
            ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER = 2;
        }

        if (gameSpeedSlider != null && active.contains(37)) {
            ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER = gameSpeedMultiplier;
        } else {
            ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER = 1;
        }

        if (fibMinutesSlider != null && active.contains(45)) {
            ChallengeCraftClient.SELECTED_FIB_MINUTES = fibMinutes;
        } else {
            ChallengeCraftClient.SELECTED_FIB_MINUTES = 60;
        }

        return active;
    }

    private static Component getHealthSliderText(double hearts) {
        return Component.translatable("challengecraft.slider.health", String.format(Locale.ROOT, "%.1f", hearts));
    }

    private static Component getSlotsSliderText(double slots) {
        return Component.translatable("challengecraft.slider.slots", String.format(Locale.ROOT, "%.0f", slots));
    }

    private static Component getMobHealthSliderText(double multiplier) {
        return Component.translatable("challengecraft.slider.mob_health", String.format(Locale.ROOT, "%.0f", multiplier));
    }

    private static Component getDoubleTroubleSliderText(double multiplier) {
        return Component.translatable("challengecraft.slider.double_trouble", String.format(Locale.ROOT, "%.0f", multiplier));
    }

    private static Component getGameSpeedSliderText(double multiplier) {
        return Component.translatable("challengecraft.slider.game_speed", String.format(Locale.ROOT, "%.0f", multiplier));
    }

    private static Component getFibMinutesSliderText(int minutes) {
        return Component.translatable("challengecraft.slider.fib_minutes", minutes);
    }
}
