package net.kasax.challengecraft.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** In-world challenge editor used after a save already exists. */
public class ChallengeSelectionScreen extends Screen {
    private static final List<Integer> IDS = new ArrayList<>(List.of(
            1, 10, 16, 17, 18, 40, 45, 4, 5, 42, 6, 7, 37, 8, 13, 43, 11, 27, 12, 20, 26, 44, 21, 38, 41, 30, 24, 28, 31, 25, 46, 32, 9, 29, 33, 2, 3, 39, 34, 23, 14, 36, 15, 35, 19, 22
    ));

    private enum Category {
        DROPS("challengecraft.category.drops"),
        WORLD("challengecraft.category.world"),
        COMBAT("challengecraft.category.combat"),
        CHAOS("challengecraft.category.chaos");

        final String key;

        Category(String key) {
            this.key = key;
        }
    }

    private static Category categoryOf(int id) {
        return switch (id) {
            case 2, 3, 4, 14, 15, 34, 44 -> Category.DROPS;
            case 5, 7, 21, 23, 24, 27, 28, 29, 32, 35, 39, 41, 42, 43, 46 -> Category.COMBAT;
            case 10, 13, 16, 17, 18, 19, 20, 33, 36, 37 -> Category.CHAOS;
            default -> Category.WORLD;
        };
    }

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private final List<ChallengeCardWidget> perkCards = new ArrayList<>();
    private final Map<Integer, ChallengeCardWidget> cardById = new HashMap<>();
    private final Map<Integer, AbstractSliderButton> sliderById = new HashMap<>();

    private AbstractSliderButton maxHealthSlider;
    private AbstractSliderButton slotsSlider;
    private AbstractSliderButton mobHealthSlider;
    private AbstractSliderButton doubleTroubleSlider;
    private AbstractSliderButton gameSpeedSlider;
    private AbstractSliderButton fibMinutesSlider;

    private WidgetScrollPanel scrollPanel;
    private CraftButton saveButton;
    private CraftButton saveAndRestartButton;

    private final boolean[] categoryExpanded = {true, true, true, true};
    private boolean perksExpanded = true;
    private boolean needsRelayout;

    private int panelX;
    private int panelTop;
    private int panelWidth;

    // sliderValue is the raw 0.0–1.0 knob position; sliderTicks the quantized gameplay unit.
    private double sliderValue;
    private int sliderTicks;
    private double slotsSliderValue;
    private int slotsSliderTicks;
    private double mobHealthSliderValue;
    private int mobHealthMultiplier;
    private double doubleTroubleSliderValue;
    private int doubleTroubleMultiplier;
    private double gameSpeedSliderValue;
    private int gameSpeedMultiplier;
    private double fibMinutesSliderValue;
    private int fibMinutes;

    private Component difficultyText = Component.empty();
    private double currentDifficulty = -1;

    public ChallengeSelectionScreen() {
        super(Component.translatable("challengecraft.challenge_selection.title"));
    }

    private void updateDifficultyText() {
        List<Integer> activeIds = getActiveIds();
        List<Integer> activePerks = getActivePerks();

        if (net.kasax.challengecraft.ChallengeManager.hasConflict(activeIds, activePerks)) {
            this.currentDifficulty = -1;
            this.difficultyText = Component.translatable("challengecraft.warning.conflict");
        } else {
            int playerCount = 0;
            if (this.minecraft != null && this.minecraft.level != null) {
                playerCount = this.minecraft.level.players().size();
            }
            double total = net.kasax.challengecraft.ChallengeManager.calculateTotalDifficulty(activeIds, sliderTicks, slotsSliderTicks, mobHealthMultiplier, gameSpeedMultiplier, doubleTroubleMultiplier, playerCount, activePerks);
            this.currentDifficulty = total;
            this.difficultyText = Component.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", total));
        }
    }

    /** Projected lifetime XP payout on successful completion (matches the server formula). */
    private long projectedPayout() {
        return currentDifficulty <= 0 ? 0 : Math.round(100.0 * currentDifficulty);
    }

    @Override
    protected void init() {
        super.init();
        cards.clear();
        perkCards.clear();
        cardById.clear();
        sliderById.clear();

        // Cards snapshot the player's level at construction; refresh first so opening this
        // screen right after joining (before the XP sync lands) doesn't show everything locked.
        ChallengeCraftClient.refreshLocalPlayerXp();

        Minecraft client = Minecraft.getInstance();
        MinecraftServer server = client.getSingleplayerServer();

        List<Integer> active;
        List<Integer> activePerks;
        int savedMaxHeartsTicks;
        int savedSlots;
        int savedMobHealthMult;
        int savedDoubleTroubleMult;
        int savedGameSpeedMult;
        int savedFibMinutes;

        if (server != null) {
            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            active = data.getActive();
            activePerks = data.getActivePerks();
            savedMaxHeartsTicks = data.getMaxHeartsTicks();
            savedSlots = data.getLimitedInventorySlots();
            savedMobHealthMult = data.getMobHealthMultiplier();
            savedDoubleTroubleMult = data.getDoubleTroubleMultiplier();
            savedGameSpeedMult = data.getGameSpeedMultiplier();
            savedFibMinutes = data.getForceItemBattleMinutes();
        } else {
            active = ChallengeCraftClient.LAST_CHOSEN;
            activePerks = ChallengeCraftClient.SELECTED_PERKS;
            savedMaxHeartsTicks = ChallengeCraftClient.SELECTED_MAX_HEARTS;
            savedSlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
            savedMobHealthMult = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;
            savedDoubleTroubleMult = ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER;
            savedGameSpeedMult = ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER;
            savedFibMinutes = ChallengeCraftClient.SELECTED_FIB_MINUTES;
        }

        if (savedMaxHeartsTicks <= 0) savedMaxHeartsTicks = 20;
        if (savedSlots <= 0) savedSlots = 36;
        if (savedMobHealthMult <= 0) savedMobHealthMult = 1;
        if (savedDoubleTroubleMult <= 0) savedDoubleTroubleMult = 2;
        if (savedGameSpeedMult <= 0) savedGameSpeedMult = 1;
        if (savedFibMinutes <= 0) savedFibMinutes = 60;

        sliderTicks = savedMaxHeartsTicks;
        sliderValue = (sliderTicks - 1) / 19.0;
        slotsSliderTicks = savedSlots;
        slotsSliderValue = (slotsSliderTicks - 1) / 35.0;
        mobHealthMultiplier = savedMobHealthMult;
        mobHealthSliderValue = (mobHealthMultiplier - 1) / 99.0;
        doubleTroubleMultiplier = savedDoubleTroubleMult;
        doubleTroubleSliderValue = (doubleTroubleMultiplier - 2) / 8.0;
        gameSpeedMultiplier = savedGameSpeedMult;
        gameSpeedSliderValue = (gameSpeedMultiplier - 1) / 9.0;
        fibMinutes = savedFibMinutes;
        fibMinutesSliderValue = (fibMinutes - 15) / 165.0;

        this.panelWidth = 300;
        this.panelX = width / 2 - panelWidth / 2;
        this.panelTop = 40;
        int panelBottomReserved = 48;
        int panelHeight = Math.max(60, height - panelTop - panelBottomReserved);

        this.scrollPanel = new WidgetScrollPanel(panelX, panelTop, panelWidth, panelHeight, Component.empty());
        addRenderableWidget(this.scrollPanel);

        buildSliders();

        for (int id : IDS) {
            boolean isOn = active.contains(id);
            ChallengeCardWidget card = new ChallengeCardWidget(0, 0, 139, 26, id, isOn, val -> {
                updateSaveButton();
                updateDifficultyText();
            });
            cards.add(card);
            cardById.put(id, card);
        }

        for (int perkId : LevelManager.ALL_PERKS) {
            if (perkId == LevelManager.PERK_INFINITY_WEAPON
                    && LevelManager.getStars(ChallengeCraftClient.LOCAL_PLAYER_XP) < 20) {
                continue;
            }
            boolean isOn = activePerks.contains(perkId);
            ChallengeCardWidget perkCard = new ChallengeCardWidget(0, 0, 139, 26, perkId, isOn, val -> {
                updateSaveButton();
                updateDifficultyText();
            });
            perkCards.add(perkCard);
        }

        int saveY = panelTop + panelHeight + 12;
        this.saveButton = new CraftButton(width / 2 - 125, saveY, 120, 20,
                Component.translatable("challengecraft.challenge_selection.save"), CraftButton.Style.PRIMARY,
                btn -> {
                    sendChallengePacket(false);
                    client.setScreenAndShow(null);
                });
        this.saveAndRestartButton = new CraftButton(width / 2 + 5, saveY, 120, 20,
                Component.translatable("challengecraft.challenge_selection.save_restart"), CraftButton.Style.NEUTRAL,
                btn -> client.setScreenAndShow(new ConfirmRestartScreen(this, () -> sendChallengePacket(true))));
        addRenderableWidget(this.saveButton);
        addRenderableWidget(this.saveAndRestartButton);

        layout();
        updateSaveButton();
        updateDifficultyText();
    }

    private void buildSliders() {
        this.maxHealthSlider = new AbstractSliderButton(0, 0, 139, 20, getHealthSliderText(0.5 + (sliderValue * 9.5)), sliderValue) {
            @Override protected void updateMessage() { setMessage(getHealthSliderText(0.5 + (this.value * 9.5))); }
            @Override protected void applyValue() {
                sliderTicks = (int) (Math.round(this.value * 19) + 1);
                this.value = (sliderTicks - 1) / 19.0;
                updateDifficultyText();
            }
        };
        this.slotsSlider = new AbstractSliderButton(0, 0, 139, 20, getSlotsSliderText(slotsSliderTicks), slotsSliderValue) {
            @Override protected void updateMessage() { setMessage(getSlotsSliderText((int) (1 + (this.value * 35)))); }
            @Override protected void applyValue() {
                slotsSliderTicks = (int) (Math.round(this.value * 35) + 1);
                this.value = (slotsSliderTicks - 1) / 35.0;
                updateDifficultyText();
            }
        };
        this.mobHealthSlider = new AbstractSliderButton(0, 0, 139, 20, getMobHealthSliderText(mobHealthMultiplier), mobHealthSliderValue) {
            @Override protected void updateMessage() { setMessage(getMobHealthSliderText(1 + (this.value * 99))); }
            @Override protected void applyValue() {
                mobHealthMultiplier = (int) (Math.round(this.value * 99) + 1);
                this.value = (mobHealthMultiplier - 1) / 99.0;
                updateDifficultyText();
            }
        };
        this.doubleTroubleSlider = new AbstractSliderButton(0, 0, 139, 20, getDoubleTroubleSliderText(doubleTroubleMultiplier), doubleTroubleSliderValue) {
            @Override protected void updateMessage() { setMessage(getDoubleTroubleSliderText(2 + (this.value * 8))); }
            @Override protected void applyValue() {
                doubleTroubleMultiplier = (int) (Math.round(this.value * 8) + 2);
                this.value = (doubleTroubleMultiplier - 2) / 8.0;
                updateDifficultyText();
            }
        };
        this.gameSpeedSlider = new AbstractSliderButton(0, 0, 139, 20, getGameSpeedSliderText(gameSpeedMultiplier), gameSpeedSliderValue) {
            @Override protected void updateMessage() { setMessage(getGameSpeedSliderText(1 + (this.value * 9))); }
            @Override protected void applyValue() {
                gameSpeedMultiplier = (int) (Math.round(this.value * 9) + 1);
                this.value = (gameSpeedMultiplier - 1) / 9.0;
                updateDifficultyText();
            }
        };

        // 15–180 minutes in 5-minute steps (33 steps over a 165-minute range).
        this.fibMinutesSlider = new AbstractSliderButton(0, 0, 139, 20, getFibMinutesSliderText(fibMinutes), fibMinutesSliderValue) {
            @Override protected void updateMessage() { setMessage(getFibMinutesSliderText(15 + (int) Math.round(this.value * 33) * 5)); }
            @Override protected void applyValue() {
                fibMinutes = 15 + (int) Math.round(this.value * 33) * 5;
                this.value = (fibMinutes - 15) / 165.0;
                updateDifficultyText();
            }
        };

        sliderById.put(7, maxHealthSlider);
        sliderById.put(12, slotsSlider);
        sliderById.put(24, mobHealthSlider);
        sliderById.put(35, doubleTroubleSlider);
        sliderById.put(37, gameSpeedSlider);
        sliderById.put(45, fibMinutesSlider);
    }

    /** Rebuilds the scroll-panel contents from the current expanded/collapsed state. */
    private void layout() {
        this.scrollPanel.clearChildren();

        int spacing = 6;
        int headerW = panelWidth - 16;
        int cardW = (headerW - spacing) / 2;
        int x0 = panelX + 8;
        int x1 = x0 + cardW + spacing;
        int y = panelTop + 6;

        for (Category category : Category.values()) {
            boolean expanded = categoryExpanded[category.ordinal()];
            scrollPanel.addChild(new SectionHeaderWidget(x0, y, headerW, category.ordinal(),
                    Component.translatable(category.key), expanded));
            y += 20;

            if (!expanded) {
                y += 4;
                continue;
            }

            int col = 0;
            for (int id : IDS) {
                if (categoryOf(id) != category) {
                    continue;
                }
                ChallengeCardWidget card = cardById.get(id);
                card.setX(col == 0 ? x0 : x1);
                card.setY(y);
                card.setWidth(cardW);
                card.setHeight(26);
                scrollPanel.addChild(card);

                if (col == 1) {
                    y += 26 + spacing;
                    col = 0;
                } else {
                    col = 1;
                }

                AbstractSliderButton slider = sliderById.get(id);
                if (slider != null) {
                    if (col == 1) {
                        y += 26 + spacing;
                        col = 0;
                    }
                    slider.setX(x0);
                    slider.setY(y);
                    slider.setWidth(headerW);
                    slider.setHeight(20);
                    scrollPanel.addChild(slider);
                    y += 20 + spacing;
                }
            }
            if (col == 1) {
                y += 26 + spacing;
            }
            y += 8;
        }

        // Perks section.
        scrollPanel.addChild(new SectionHeaderWidget(x0, y, headerW, 4,
                Component.translatable("challengecraft.challenge_selection.perks_header"), perksExpanded));
        y += 20;
        if (perksExpanded) {
            int col = 0;
            for (ChallengeCardWidget perkCard : perkCards) {
                perkCard.setX(col == 0 ? x0 : x1);
                perkCard.setY(y);
                perkCard.setWidth(cardW);
                perkCard.setHeight(26);
                scrollPanel.addChild(perkCard);
                if (col == 1) {
                    y += 26 + spacing;
                    col = 0;
                } else {
                    col = 1;
                }
            }
        }
    }

    private void sendChallengePacket(boolean restart) {
        List<Integer> newActive = getActiveIds();
        List<Integer> newPerks = getActivePerks();

        int heartsTicks = newActive.contains(7) ? this.sliderTicks : 0;
        int slotticks = newActive.contains(12) ? this.slotsSliderTicks : 0;
        int mobHealthMult = newActive.contains(24) ? this.mobHealthMultiplier : 1;
        int doubleMult = newActive.contains(35) ? this.doubleTroubleMultiplier : 2;
        int gameSpeedMult = newActive.contains(37) ? this.gameSpeedMultiplier : 1;
        int fibMin = newActive.contains(45) ? this.fibMinutes : 60;

        ChallengeCraft.LOGGER.info(
                // gameSpeed was missing a placeholder, so every value after it printed shifted:
                // the log showed doubleTrouble's value under "restart" and dropped restart entirely.
                "[Client:Selection] sending ChallengePacket → active = {} , perks = {}, maxHearts ticks = {}, slots = {}, mobHealth = {}, doubleTrouble = {}, gameSpeed = {}, restart = {}",
                newActive, newPerks, heartsTicks, slotticks, mobHealthMult, doubleMult, gameSpeedMult, restart
        );

        ClientPlayNetworking.send(new ChallengePacket(newActive, heartsTicks, slotticks, mobHealthMult, doubleMult, gameSpeedMult, fibMin, newPerks, restart));
    }

    private List<Integer> getActiveIds() {
        List<Integer> ids = new ArrayList<>();
        for (ChallengeCardWidget card : cards) {
            if (card.isActive()) {
                ids.add(card.getChallengeId());
            }
        }
        return ids;
    }

    private List<Integer> getActivePerks() {
        List<Integer> ids = new ArrayList<>();
        for (ChallengeCardWidget card : perkCards) {
            if (card.isActive()) {
                ids.add(card.getChallengeId());
            }
        }
        return ids;
    }

    private void updateSaveButton() {
        boolean noConflict = !net.kasax.challengecraft.ChallengeManager.hasConflict(getActiveIds(), getActivePerks());
        if (saveButton != null) {
            saveButton.active = noConflict;
        }
        if (saveAndRestartButton != null) {
            saveAndRestartButton.active = noConflict;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        this.minecraft.setScreenAndShow(null);
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        // Frame behind the scrollable card list.
        CraftUI.frame(ctx, panelX - 4, panelTop - 4, panelWidth + 8,
                (scrollPanel != null ? scrollPanel.getHeight() : 0) + 8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (needsRelayout) {
            layout();
            needsRelayout = false;
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        ctx.centeredText(this.font, this.title, width / 2, 8, CraftUI.TEXT_PRIMARY);

        boolean conflict = net.kasax.challengecraft.ChallengeManager.hasConflict(getActiveIds(), getActivePerks());
        if (conflict) {
            ctx.centeredText(this.font, Component.translatable("challengecraft.warning.conflict"), width / 2, 24, CraftUI.DANGER);
        } else {
            Component combined = Component.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", currentDifficulty))
                    .copy().append(Component.literal("   •   "))
                    .append(Component.translatable("challengecraft.worldcreate.xp_payout", String.format(Locale.ROOT, "%,d", projectedPayout())));
            ctx.centeredText(this.font, combined, width / 2, 24, CraftUI.WARNING);
        }
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

    /** Collapsible category header row inside the scroll panel. */
    private final class SectionHeaderWidget extends AbstractWidget {
        private final int index; // 0-3 = category ordinal, 4 = perks
        private final boolean expanded;
        private final Anim.Tween hover = new Anim.Tween(0f);

        private SectionHeaderWidget(int x, int y, int width, int index, Component label, boolean expanded) {
            super(x, y, width, 16, label);
            this.index = index;
            this.expanded = expanded;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            float g = hover.approach(isHovered() ? 1f : 0f, 12f);
            int color = CraftUI.mix(CraftUI.TEXT_SECONDARY, CraftUI.TEXT_PRIMARY, g);
            drawCaret(context, getX(), getY() + 3, expanded, CraftUI.mix(CraftUI.GOLD, 0xFFF3D88A, g));
            context.text(ChallengeSelectionScreen.this.font, getMessage(), getX() + 12, getY() + 3, color, false);
            int underlineY = getY() + 3 + ChallengeSelectionScreen.this.font.lineHeight + 1;
            context.fill(getX(), underlineY, getX() + getWidth(), underlineY + 1, CraftUI.applyAlpha(CraftUI.GOLD, 0.6f));
        }

        private void drawCaret(GuiGraphicsExtractor context, int x, int y, boolean expanded, int color) {
            if (expanded) {
                for (int i = 0; i < 4; i++) {
                    context.fill(x + i, y + i, x + 7 - i, y + i + 1, color);
                }
            } else {
                for (int i = 0; i < 4; i++) {
                    context.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, color);
                }
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (index < 4) {
                categoryExpanded[index] = !categoryExpanded[index];
            } else {
                perksExpanded = !perksExpanded;
            }
            // Defer the rebuild so we do not mutate the panel's child list mid-dispatch.
            needsRelayout = true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }
}
