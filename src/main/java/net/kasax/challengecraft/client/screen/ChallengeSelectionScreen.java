package net.kasax.challengecraft.client.screen;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ChallengeSelectionScreen extends Screen {
    private static final List<Integer> IDS = new ArrayList<>(List.of(
            1, 10, 16, 17, 18, 4, 5, 6, 7, 8, 13, 11, 27, 12, 20, 26, 21, 30, 24, 28, 31, 25, 32, 9, 29, 33, 2, 3, 34, 23, 14, 15, 35, 19, 22
    ));

    static {
        // IDS.sort(java.util.Comparator.comparingInt(net.kasax.challengecraft.LevelManager::getRequiredLevel).thenComparingInt(id -> id));
    }
    private static final List<Text> TITLES = IDS.stream()
            .map(id -> (Text) Text.translatable("challengecraft.worldcreate.challenge" + id))
            .toList();

    private static final List<Text> DESCRIPTIONS = IDS.stream()
            .map(id -> (Text) Text.translatable("challengecraft.worldcreate.challenge" + id + ".desc"))
            .toList();

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private final List<ChallengeCardWidget> perkCards = new ArrayList<>();
    private SliderWidget maxHealthSlider;
    private SliderWidget slotsSlider;
    private SliderWidget mobHealthSlider;
    private SliderWidget doubleTroubleSlider;

    private WidgetScrollPanel scrollPanel;
    private ButtonWidget saveButton;
    private ButtonWidget saveAndRestartButton;

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

    public ChallengeSelectionScreen() {
        super(Text.literal("Challenge Selection"));
    }

    @Override
    protected void init() {
        super.init();
        cards.clear();
        perkCards.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();

        List<Integer> active;
        List<Integer> activePerks;
        int savedMaxHeartsTicks;
        int savedSlots;
        int savedMobHealthMult;
        int savedDoubleTroubleMult;

        if (server != null) {
            ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
            active = data.getActive();
            activePerks = data.getActivePerks();
            savedMaxHeartsTicks = data.getMaxHeartsTicks();
            savedSlots = data.getLimitedInventorySlots();
            savedMobHealthMult = data.getMobHealthMultiplier();
            savedDoubleTroubleMult = data.getDoubleTroubleMultiplier();
        } else {
            // Client side on dedicated server
            active = ChallengeCraftClient.LAST_CHOSEN;
            activePerks = ChallengeCraftClient.SELECTED_PERKS;
            savedMaxHeartsTicks = ChallengeCraftClient.SELECTED_MAX_HEARTS;
            savedSlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
            savedMobHealthMult = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;
            savedDoubleTroubleMult = ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER;
        }

        if (savedMaxHeartsTicks <= 0) savedMaxHeartsTicks = 20;
        if (savedSlots <= 0) savedSlots = 36;
        if (savedMobHealthMult <= 0) savedMobHealthMult = 1;
        if (savedDoubleTroubleMult <= 0) savedDoubleTroubleMult = 2;

        // Convert saved ticks/slots -> slider knob value (0.0 .. 1.0)
        sliderTicks = savedMaxHeartsTicks;                  // 1..20
        sliderValue = (sliderTicks - 1) / 19.0;

        slotsSliderTicks = savedSlots;                      // 1..36
        slotsSliderValue = (slotsSliderTicks - 1) / 35.0;

        mobHealthMultiplier = savedMobHealthMult;
        mobHealthSliderValue = (mobHealthMultiplier - 1) / 99.0;

        doubleTroubleMultiplier = savedDoubleTroubleMult;
        doubleTroubleSliderValue = (doubleTroubleMultiplier - 2) / 8.0;

        int panelWidth = 260;
        int panelX = width / 2 - panelWidth / 2;
        int panelTop = 40;
        int panelBottomReserved = 48; // space for Save button
        int panelHeight = Math.max(60, height - panelTop - panelBottomReserved);

        this.scrollPanel = new WidgetScrollPanel(panelX, panelTop, panelWidth, panelHeight, Text.empty());
        addDrawableChild(this.scrollPanel);


        int cardWidth = 115;
        int cardHeight = 26;
        int spacing = 4;

        int x0 = panelX + 8;
        int x1 = x0 + cardWidth + spacing;
        int col = 0;
        int y = panelTop + 6;

        // Initialize sliders
        this.maxHealthSlider = new SliderWidget(0, 0, cardWidth, cardHeight, Text.literal(String.format("Health: %.1f❤", 0.5 + (sliderValue * 9.5))), sliderValue) {
            @Override protected void updateMessage() { setMessage(Text.literal(String.format("Health: %.1f❤", 0.5 + (this.value * 9.5)))); }
            @Override protected void applyValue() {
                sliderTicks = (int)(Math.round(this.value * 19) + 1);
                this.value = (sliderTicks - 1) / 19.0;
            }
        };
        this.slotsSlider = new SliderWidget(0, 0, cardWidth, cardHeight, Text.literal(String.format("Slots: %d", slotsSliderTicks)), slotsSliderValue) {
            @Override protected void updateMessage() { setMessage(Text.literal(String.format("Slots: %d", (int)(1 + (this.value * 35))))); }
            @Override protected void applyValue() {
                slotsSliderTicks = (int)(Math.round(this.value * 35) + 1);
                this.value = (slotsSliderTicks - 1) / 35.0;
            }
        };
        this.mobHealthSlider = new SliderWidget(0, 0, cardWidth, cardHeight, Text.literal(String.format("Mob Health: %dx", mobHealthMultiplier)), mobHealthSliderValue) {
            @Override protected void updateMessage() { setMessage(Text.literal(String.format("Mob Health: %.0fx", 1 + (this.value * 99)))); }
            @Override protected void applyValue() {
                mobHealthMultiplier = (int)(Math.round(this.value * 99) + 1);
                this.value = (mobHealthMultiplier - 1) / 99.0;
            }
        };
        this.doubleTroubleSlider = new SliderWidget(0, 0, cardWidth, cardHeight, Text.literal(String.format("Double Trouble: %dx", doubleTroubleMultiplier)), doubleTroubleSliderValue) {
            @Override protected void updateMessage() { setMessage(Text.literal(String.format("Double Trouble: %.0fx", 2 + (this.value * 8)))); }
            @Override protected void applyValue() {
                doubleTroubleMultiplier = (int)(Math.round(this.value * 8) + 2);
                this.value = (doubleTroubleMultiplier - 2) / 8.0;
            }
        };

        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);
            boolean isOn = active.contains(id);

            if ((id == 7 || id == 12 || id == 24 || id == 35) && col == 1) {
                y += cardHeight + spacing;
                col = 0;
            }

            int currentX = (col == 0) ? x0 : x1;
            ChallengeCardWidget card = new ChallengeCardWidget(currentX, y, cardWidth, cardHeight, id, isOn, val -> updateSaveButton());
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
        }
        if (col == 1) y += cardHeight + spacing;
        y += 15;
        Text perkTitle = Text.literal("--- Perks (-0.5 Difficulty each) ---");
        scrollPanel.addChild(new net.minecraft.client.gui.widget.ClickableWidget(panelX, y, panelWidth, 20, perkTitle) {
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 5, 0xFFFF55);
            }
            @Override
            protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {}
        });
        y += 20;

        col = 0;
        for (int perkId : LevelManager.ALL_PERKS) {
            // Hide Infinity Weapon perk if not unlocked (20 stars)
            if (perkId == LevelManager.PERK_INFINITY_WEAPON) {
                if (LevelManager.getStars(ChallengeCraftClient.LOCAL_PLAYER_XP) < 20) {
                    continue;
                }
            }
            boolean isOn = activePerks.contains(perkId);
            int currentX = (col == 0) ? x0 : x1;
            ChallengeCardWidget perkCard = new ChallengeCardWidget(currentX, y, cardWidth, cardHeight, perkId, isOn, val -> updateSaveButton());
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
        // Save and Restart button
        this.saveButton = new SaveButton(
                width / 2 - 125, saveY, 120, 20,
                Text.literal("Save"),
                btn -> {
                    sendChallengePacket(false);
                    client.setScreen(null);
                }
        );
        this.saveAndRestartButton = new SaveButton(
                width / 2 + 5, saveY, 120, 20,
                Text.literal("Save and Restart"),
                btn -> {
                    client.setScreen(new ConfirmRestartScreen(this, () -> {
                        sendChallengePacket(true);
                    }));
                }
        );
        addDrawableChild(this.saveButton);
        addDrawableChild(this.saveAndRestartButton);
        updateSaveButton();
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

        ChallengeCraft.LOGGER.info(
                "[Client:Selection] sending ChallengePacket → active = {} , perks = {}, maxHearts ticks = {}, slots = {}, mobHealth = {}, doubleTrouble = {}, restart = {}",
                newActive, newPerks, heartsTicks, slotticks, mobHealthMult, doubleMult, restart
        );

        ClientPlayNetworking.send(new ChallengePacket(newActive, heartsTicks, slotticks, mobHealthMult, doubleMult, newPerks, restart));
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
        this.client.setScreen(null);
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        // Title
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, width / 2, 10, 0xFFFF55);

        // Render the warning message
        if (net.kasax.challengecraft.ChallengeManager.hasConflict(getActiveIds(), getActivePerks())) {
            Text conflictWarning = Text.translatable("challengecraft.warning.conflict");
            ctx.drawCenteredTextWithShadow(this.textRenderer, conflictWarning, width / 2, 24, 0xFF5555);
        } else {
            Text warning = Text.translatable("challengecraft.warning.tainted");
            ctx.drawCenteredTextWithShadow(this.textRenderer, warning, width / 2, 24, 0xFF5555);
        }
    }

    private static class SaveButton extends ButtonWidget {
        public SaveButton(int x, int y, int w, int h,
                          Text msg, PressAction onPress) {
            super(x, y, w, h, msg, onPress,
                    ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        }
    }
}
