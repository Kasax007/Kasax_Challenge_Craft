package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
            1, 10, 16, 17, 18, 40, 45, 4, 5, 42, 6, 7, 37, 8, 13, 43, 11, 27, 12, 20, 26, 44, 21, 38, 41, 30, 24, 28, 31, 25, 46, 32, 9, 29, 33, 2, 3, 39, 34, 23, 14, 36, 15, 35, 19, 22
    ));

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private final List<ChallengeCardWidget> perkCards = new ArrayList<>();
    private final AbstractSliderButton maxHealthSlider;
    private final AbstractSliderButton inventorySlider;
    private final AbstractSliderButton mobHealthSlider;
    private final AbstractSliderButton doubleTroubleSlider;
    private final AbstractSliderButton gameSpeedSlider;
    private final AbstractSliderButton fibMinutesSlider;
    private Component difficultyText = Component.empty();
    private boolean hasConflict;
    private double currentDifficulty = -1;

    private WidgetScrollPanel scrollPanel;
    private AbstractWidget infoBar;

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
            ChallengeCardWidget card = new ChallengeCardWidget(0, 0, 100, 20, id, false, val -> updateDifficultyText());
            cards.add(card);
        }

        for (int perkId : net.kasax.challengecraft.LevelManager.ALL_PERKS) {
            ChallengeCardWidget perkCard = new ChallengeCardWidget(0, 0, 100, 20, perkId, false, val -> updateDifficultyText());
            perkCards.add(perkCard);
        }

        this.maxHealthSlider = new AbstractSliderButton(
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

        this.inventorySlider = new AbstractSliderButton(
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

        this.mobHealthSlider = new AbstractSliderButton(
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

        this.doubleTroubleSlider = new AbstractSliderButton(
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

        this.gameSpeedSlider = new AbstractSliderButton(
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

        this.fibMinutesSlider = new AbstractSliderButton(
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

        updateDifficultyText();
    }

    private void updateDifficultyText() {
        List<Integer> activeIds = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (cards.get(i).isActive()) {
                activeIds.add(IDS.get(i));
            }
        }
        List<Integer> activePerks = getSelectedPerks();

        if (net.kasax.challengecraft.ChallengeManager.hasConflict(activeIds, activePerks)) {
            this.hasConflict = true;
            this.currentDifficulty = -1;
            this.difficultyText = Component.translatable("challengecraft.warning.conflict");
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

    @Override
    public void doLayout(ScreenRectangle tabArea) {
        int padding = 6;

        int panelX = tabArea.left() + padding;
        int panelY = tabArea.top() + padding;
        int panelW = Math.max(60, tabArea.width() - padding * 2);
        int panelH = Math.max(60, tabArea.height() - padding * 2);

        int infoBarH = 20;
        this.infoBar.setX(panelX);
        this.infoBar.setY(panelY);
        this.infoBar.setWidth(panelW);
        this.infoBar.setHeight(infoBarH);

        int listY = panelY + infoBarH + 4;
        this.scrollPanel.setX(panelX);
        this.scrollPanel.setY(listY);
        this.scrollPanel.setWidth(panelW);
        this.scrollPanel.setHeight(Math.max(40, panelH - infoBarH - 4));

        // Child bounds depend on the current tab size, but the panel instance stays stable.
        this.scrollPanel.clearChildren();

        int cardW = (panelW - 24) / 2;
        int cardH = 26;
        int spacing = 4;
        int x0 = panelX + 8;
        int x1 = x0 + cardW + spacing;
        int col = 0;
        int y = listY + 4;

        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);

            if ((id == 7 || id == 12 || id == 24 || id == 35 || id == 37 || id == 45) && col == 1) {
                y += cardH + spacing;
                col = 0;
            }

            ChallengeCardWidget card = cards.get(i);
            card.setX((col == 0) ? x0 : x1);
            card.setY(y);
            card.setWidth(cardW);
            card.setHeight(cardH);
            this.scrollPanel.addChild(card);

            if (col == 1) {
                y += cardH + spacing;
                col = 0;
            } else {
                col = 1;
            }

            if (id == 7 && maxHealthSlider != null) {
                maxHealthSlider.setX(x1);
                maxHealthSlider.setY(y);
                maxHealthSlider.setWidth(cardW);
                maxHealthSlider.setHeight(cardH);
                this.scrollPanel.addChild(maxHealthSlider);
                y += cardH + spacing;
                col = 0;
            }

            if (id == 12 && inventorySlider != null) {
                inventorySlider.setX(x1);
                inventorySlider.setY(y);
                inventorySlider.setWidth(cardW);
                inventorySlider.setHeight(cardH);
                this.scrollPanel.addChild(inventorySlider);
                y += cardH + spacing;
                col = 0;
            }

            if (id == 24 && mobHealthSlider != null) {
                mobHealthSlider.setX(x1);
                mobHealthSlider.setY(y);
                mobHealthSlider.setWidth(cardW);
                mobHealthSlider.setHeight(cardH);
                this.scrollPanel.addChild(mobHealthSlider);
                y += cardH + spacing;
                col = 0;
            }

            if (id == 35 && doubleTroubleSlider != null) {
                doubleTroubleSlider.setX(x1);
                doubleTroubleSlider.setY(y);
                doubleTroubleSlider.setWidth(cardW);
                doubleTroubleSlider.setHeight(cardH);
                this.scrollPanel.addChild(doubleTroubleSlider);
                y += cardH + spacing;
                col = 0;
            }

            if (id == 37 && gameSpeedSlider != null) {
                gameSpeedSlider.setX(x1);
                gameSpeedSlider.setY(y);
                gameSpeedSlider.setWidth(cardW);
                gameSpeedSlider.setHeight(cardH);
                this.scrollPanel.addChild(gameSpeedSlider);
                y += cardH + spacing;
                col = 0;
            }

            if (id == 45 && fibMinutesSlider != null) {
                fibMinutesSlider.setX(x1);
                fibMinutesSlider.setY(y);
                fibMinutesSlider.setWidth(cardW);
                fibMinutesSlider.setHeight(cardH);
                this.scrollPanel.addChild(fibMinutesSlider);
                y += cardH + spacing;
                col = 0;
            }
        }
        
        if (col == 1) y += cardH + spacing;
        y += 15;
        Component perkTitle = Component.translatable("challengecraft.challenge_selection.perks_header");
        scrollPanel.addChild(new AbstractWidget(x0, y, panelW - 16, 20, perkTitle) {
            @Override
            protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
                context.centeredText(net.minecraft.client.Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + 5, 0xFFFF55);
            }
            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {}
        });
        y += 24;

        col = 0;
        for (ChallengeCardWidget perkCard : perkCards) {
            // Infinity Weapon is a star reward, so it should not appear before that milestone.
            if (perkCard.getChallengeId() == net.kasax.challengecraft.LevelManager.PERK_INFINITY_WEAPON) {
                if (net.kasax.challengecraft.LevelManager.getStars(ChallengeCraftClient.LOCAL_PLAYER_XP) < 20) {
                    continue;
                }
            }
            
            perkCard.setX((col == 0) ? x0 : x1);
            perkCard.setY(y);
            perkCard.setWidth(cardW);
            perkCard.setHeight(cardH);
            this.scrollPanel.addChild(perkCard);

            if (col == 1) {
                y += cardH + spacing;
                col = 0;
            } else {
                col = 1;
            }
        }
        if (col == 1) y += cardH + spacing;
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
                line = Component.translatable("challengecraft.warning.conflict");
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
        consumer.accept(this.infoBar);
        consumer.accept(this.scrollPanel);
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

    public List<Integer> getActive() {
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (cards.get(i).isActive()) {
                active.add(IDS.get(i));
            }
        }

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
