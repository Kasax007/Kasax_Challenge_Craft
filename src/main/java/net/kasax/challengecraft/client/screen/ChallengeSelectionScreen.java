package net.kasax.challengecraft.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** In-world challenge editor used after a save already exists. */
public class ChallengeSelectionScreen extends Screen {
    private static final List<Integer> IDS = new ArrayList<>(List.of(
            1, 10, 16, 17, 18, 40, 4, 5, 6, 7, 37, 8, 13, 11, 27, 12, 20, 26, 21, 38, 30, 24, 28, 31, 25, 32, 9, 29, 33, 2, 3, 39, 34, 23, 14, 36, 15, 35, 19, 22
    ));

    private static final List<Component> TITLES = IDS.stream()
            .map(id -> (Component) Component.translatable("challengecraft.worldcreate.challenge" + id))
            .toList();

    private static final List<Component> DESCRIPTIONS = IDS.stream()
            .map(id -> (Component) Component.translatable("challengecraft.worldcreate.challenge" + id + ".desc"))
            .toList();

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private final List<ChallengeCardWidget> perkCards = new ArrayList<>();
    private AbstractSliderButton maxHealthSlider;
    private AbstractSliderButton slotsSlider;
    private AbstractSliderButton mobHealthSlider;
    private AbstractSliderButton doubleTroubleSlider;
    private AbstractSliderButton gameSpeedSlider;

    private WidgetScrollPanel scrollPanel;
    private Button saveButton;
    private Button saveAndRestartButton;

    // sliderValue is the raw 0.0–1.0 knob position
    private double sliderValue;
    // sliderTicks is 1–20 quantized half-heart steps
    private int sliderTicks;
    private double slotsSliderValue;
    private int slotsSliderTicks;
    private double mobHealthSliderValue;
    private int mobHealthMultiplier;
    private double doubleTroubleSliderValue;
    private int doubleTroubleMultiplier;
    private double gameSpeedSliderValue;
    private int gameSpeedMultiplier;

    public ChallengeSelectionScreen() {
        super(Component.translatable("challengecraft.challenge_selection.title"));
    }

    private Component difficultyText = Component.empty();

    private void updateDifficultyText() {
        List<Integer> activeIds = getActiveIds();
        List<Integer> activePerks = getActivePerks();

        if (net.kasax.challengecraft.ChallengeManager.hasConflict(activeIds, activePerks)) {
            this.difficultyText = Component.translatable("challengecraft.warning.conflict");
        } else {
            int playerCount = 0;
            if (this.minecraft != null && this.minecraft.level != null) {
                playerCount = this.minecraft.level.players().size();
            }
            double total = net.kasax.challengecraft.ChallengeManager.calculateTotalDifficulty(activeIds, sliderTicks, slotsSliderTicks, mobHealthMultiplier, gameSpeedMultiplier, doubleTroubleMultiplier, playerCount, activePerks);
            this.difficultyText = Component.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", total));
        }
    }

    @Override
    protected void init() {
        super.init();
        cards.clear();
        perkCards.clear();

        Minecraft client = Minecraft.getInstance();
        MinecraftServer server = client.getSingleplayerServer();

        List<Integer> active;
        List<Integer> activePerks;
        int savedMaxHeartsTicks;
        int savedSlots;
        int savedMobHealthMult;
        int savedDoubleTroubleMult;
        int savedGameSpeedMult;

        if (server != null) {
            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            active = data.getActive();
            activePerks = data.getActivePerks();
            savedMaxHeartsTicks = data.getMaxHeartsTicks();
            savedSlots = data.getLimitedInventorySlots();
            savedMobHealthMult = data.getMobHealthMultiplier();
            savedDoubleTroubleMult = data.getDoubleTroubleMultiplier();
            savedGameSpeedMult = data.getGameSpeedMultiplier();
        } else {
            // Client side on dedicated server
            active = ChallengeCraftClient.LAST_CHOSEN;
            activePerks = ChallengeCraftClient.SELECTED_PERKS;
            savedMaxHeartsTicks = ChallengeCraftClient.SELECTED_MAX_HEARTS;
            savedSlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
            savedMobHealthMult = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;
            savedDoubleTroubleMult = ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER;
            savedGameSpeedMult = ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER;
        }

        if (savedMaxHeartsTicks <= 0) savedMaxHeartsTicks = 20;
        if (savedSlots <= 0) savedSlots = 36;
        if (savedMobHealthMult <= 0) savedMobHealthMult = 1;
        if (savedDoubleTroubleMult <= 0) savedDoubleTroubleMult = 2;
        if (savedGameSpeedMult <= 0) savedGameSpeedMult = 1;

        // Persisted values use gameplay units; sliders render them as normalized positions.
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

        int panelWidth = 260;
        int panelX = width / 2 - panelWidth / 2;
        int panelTop = 40;
        int panelBottomReserved = 48;
        int panelHeight = Math.max(60, height - panelTop - panelBottomReserved);

        this.scrollPanel = new WidgetScrollPanel(panelX, panelTop, panelWidth, panelHeight, Component.empty());
        addRenderableWidget(this.scrollPanel);


        int cardWidth = 115;
        int cardHeight = 26;
        int spacing = 4;

        int x0 = panelX + 8;
        int x1 = x0 + cardWidth + spacing;
        int col = 0;
        int y = panelTop + 6;

        this.maxHealthSlider = new AbstractSliderButton(0, 0, cardWidth, cardHeight, getHealthSliderText(0.5 + (sliderValue * 9.5)), sliderValue) {
            @Override protected void updateMessage() { setMessage(getHealthSliderText(0.5 + (this.value * 9.5))); }
            @Override protected void applyValue() {
                sliderTicks = (int)(Math.round(this.value * 19) + 1);
                this.value = (sliderTicks - 1) / 19.0;
                updateDifficultyText();
            }
        };
        this.slotsSlider = new AbstractSliderButton(0, 0, cardWidth, cardHeight, getSlotsSliderText(slotsSliderTicks), slotsSliderValue) {
            @Override protected void updateMessage() { setMessage(getSlotsSliderText((int)(1 + (this.value * 35)))); }
            @Override protected void applyValue() {
                slotsSliderTicks = (int)(Math.round(this.value * 35) + 1);
                this.value = (slotsSliderTicks - 1) / 35.0;
                updateDifficultyText();
            }
        };
        this.mobHealthSlider = new AbstractSliderButton(0, 0, cardWidth, cardHeight, getMobHealthSliderText(mobHealthMultiplier), mobHealthSliderValue) {
            @Override protected void updateMessage() { setMessage(getMobHealthSliderText(1 + (this.value * 99))); }
            @Override protected void applyValue() {
                mobHealthMultiplier = (int)(Math.round(this.value * 99) + 1);
                this.value = (mobHealthMultiplier - 1) / 99.0;
                updateDifficultyText();
            }
        };
        this.doubleTroubleSlider = new AbstractSliderButton(0, 0, cardWidth, cardHeight, getDoubleTroubleSliderText(doubleTroubleMultiplier), doubleTroubleSliderValue) {
            @Override protected void updateMessage() { setMessage(getDoubleTroubleSliderText(2 + (this.value * 8))); }
            @Override protected void applyValue() {
                doubleTroubleMultiplier = (int)(Math.round(this.value * 8) + 2);
                this.value = (doubleTroubleMultiplier - 2) / 8.0;
                updateDifficultyText();
            }
        };
        this.gameSpeedSlider = new AbstractSliderButton(0, 0, cardWidth, cardHeight, getGameSpeedSliderText(gameSpeedMultiplier), gameSpeedSliderValue) {
            @Override protected void updateMessage() { setMessage(getGameSpeedSliderText(1 + (this.value * 9))); }
            @Override protected void applyValue() {
                gameSpeedMultiplier = (int)(Math.round(this.value * 9) + 1);
                this.value = (gameSpeedMultiplier - 1) / 9.0;
                updateDifficultyText();
            }
        };

        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);
            boolean isOn = active.contains(id);

            if ((id == 7 || id == 12 || id == 24 || id == 35 || id == 37) && col == 1) {
                y += cardHeight + spacing;
                col = 0;
            }

            int currentX = (col == 0) ? x0 : x1;
            ChallengeCardWidget card = new ChallengeCardWidget(currentX, y, cardWidth, cardHeight, id, isOn, val -> {
                updateSaveButton();
                updateDifficultyText();
            });
            cards.add(card);
            scrollPanel.addChild(card);

            if (col == 1) {
                y += cardHeight + spacing;
                col = 0;
            } else {
                col = 1;
            }

            if (id == 7 && maxHealthSlider != null) {
                maxHealthSlider.setX(x1);
                maxHealthSlider.setY(y);
                scrollPanel.addChild(maxHealthSlider);
                y += cardHeight + spacing;
                col = 0;
            }
            if (id == 12 && slotsSlider != null) {
                slotsSlider.setX(x1);
                slotsSlider.setY(y);
                scrollPanel.addChild(slotsSlider);
                y += cardHeight + spacing;
                col = 0;
            }
            if (id == 24 && mobHealthSlider != null) {
                mobHealthSlider.setX(x1);
                mobHealthSlider.setY(y);
                scrollPanel.addChild(mobHealthSlider);
                y += cardHeight + spacing;
                col = 0;
            }
            if (id == 35 && doubleTroubleSlider != null) {
                doubleTroubleSlider.setX(x1);
                doubleTroubleSlider.setY(y);
                scrollPanel.addChild(doubleTroubleSlider);
                y += cardHeight + spacing;
                col = 0;
            }
            if (id == 37 && gameSpeedSlider != null) {
                gameSpeedSlider.setX(x1);
                gameSpeedSlider.setY(y);
                scrollPanel.addChild(gameSpeedSlider);
                y += cardHeight + spacing;
                col = 0;
            }
        }
        if (col == 1) y += cardHeight + spacing;
        y += 15;
        Component perkTitle = Component.translatable("challengecraft.challenge_selection.perks_header");
        scrollPanel.addChild(new net.minecraft.client.gui.components.AbstractWidget(panelX, y, panelWidth, 20, perkTitle) {
            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
                context.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + 5, 0xFFFF55);
            }
            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {}
        });
        y += 20;

        col = 0;
        for (int perkId : LevelManager.ALL_PERKS) {
            // Infinity Weapon is a star reward, so it should not appear before that milestone.
            if (perkId == LevelManager.PERK_INFINITY_WEAPON) {
                if (LevelManager.getStars(ChallengeCraftClient.LOCAL_PLAYER_XP) < 20) {
                    continue;
                }
            }
            boolean isOn = activePerks.contains(perkId);
            int currentX = (col == 0) ? x0 : x1;
            ChallengeCardWidget perkCard = new ChallengeCardWidget(currentX, y, cardWidth, cardHeight, perkId, isOn, val -> {
                updateSaveButton();
                updateDifficultyText();
            });
            perkCards.add(perkCard);
            scrollPanel.addChild(perkCard);

            if (col == 1) {
                y += cardHeight + spacing;
                col = 0;
            } else {
                col = 1;
            }
        }
        if (col == 1) y += cardHeight + spacing;
        
        int saveY = panelTop + panelHeight + 10;
        this.saveButton = Button.builder(Component.translatable("challengecraft.challenge_selection.save"), btn -> {
                    sendChallengePacket(false);
                    client.setScreen(null);
                })
                .bounds(width / 2 - 125, saveY, 120, 20)
                .build();
        this.saveAndRestartButton = Button.builder(Component.translatable("challengecraft.challenge_selection.save_restart"), btn -> {
                    client.setScreen(new ConfirmRestartScreen(this, () -> {
                        sendChallengePacket(true);
                    }));
                })
                .bounds(width / 2 + 5, saveY, 120, 20)
                .build();
        addRenderableWidget(this.saveButton);
        addRenderableWidget(this.saveAndRestartButton);
        updateSaveButton();
        updateDifficultyText();
    }

    private void sendChallengePacket(boolean restart) {
        List<Integer> newActive = getActiveIds();
        List<Integer> newPerks = getActivePerks();

        int heartsTicks = 0;
        if (newActive.contains(7) && maxHealthSlider != null) {
            heartsTicks = this.sliderTicks;
        }

        int slotticks = 0;
        if (newActive.contains(12) && slotsSlider != null) {
            slotticks = this.slotsSliderTicks;
        }

        int mobHealthMult = 1;
        if (newActive.contains(24) && mobHealthSlider != null) {
            mobHealthMult = this.mobHealthMultiplier;
        }

        int doubleMult = 2;
        if (newActive.contains(35) && doubleTroubleSlider != null) {
            doubleMult = this.doubleTroubleMultiplier;
        }

        int gameSpeedMult = 1;
        if (newActive.contains(37) && gameSpeedSlider != null) {
            gameSpeedMult = this.gameSpeedMultiplier;
        }

        ChallengeCraft.LOGGER.info(
                "[Client:Selection] sending ChallengePacket → active = {} , perks = {}, maxHearts ticks = {}, slots = {}, mobHealth = {}, doubleTrouble = {}, restart = {}",
                newActive, newPerks, heartsTicks, slotticks, mobHealthMult, doubleMult, gameSpeedMult, restart
        );

        ClientPlayNetworking.send(new ChallengePacket(newActive, heartsTicks, slotticks, mobHealthMult, doubleMult, gameSpeedMult, newPerks, restart));
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
        this.minecraft.setScreen(null);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        ctx.centeredText(this.font, this.title, width / 2, 10, 0xFFFF55);

        if (net.kasax.challengecraft.ChallengeManager.hasConflict(getActiveIds(), getActivePerks())) {
            Component conflictWarning = Component.translatable("challengecraft.warning.conflict");
            ctx.centeredText(this.font, conflictWarning, width / 2, 24, 0xFF5555);
        } else {
            ctx.centeredText(this.font, this.difficultyText, width / 2, 24, 0xFFFF55);
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
}
