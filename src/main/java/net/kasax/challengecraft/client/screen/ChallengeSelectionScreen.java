package net.kasax.challengecraft.client.screen;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeCraftClient;
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
    private static final List<Integer> IDS = List.of(1, 11, 9, 25, 5, 6, 4, 16, 10, 13, 7, 12, 24, 2, 14, 3, 15, 8, 20, 17, 18, 19, 21, 22, 23);
    private static final List<Text> TITLES = IDS.stream()
            .map(id -> (Text) Text.translatable("challengecraft.worldcreate.challenge" + id))
            .toList();

    private static final List<Text> DESCRIPTIONS = IDS.stream()
            .map(id -> (Text) Text.translatable("challengecraft.worldcreate.challenge" + id + ".desc"))
            .toList();

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private SliderWidget maxHealthSlider;
    private SliderWidget slotsSlider;
    private SliderWidget mobHealthSlider;

    private WidgetScrollPanel scrollPanel;
    private ButtonWidget saveButton;

    // sliderValue is the raw 0.0–1.0 knob position
    private double sliderValue;
    // sliderTicks is 1–20 quantized half-heart steps
    private int sliderTicks;
    private double slotsSliderValue;
    private int slotsSliderTicks;
    private double mobHealthSliderValue;
    private int mobHealthMultiplier;

    public ChallengeSelectionScreen() {
        super(Text.literal("Challenge Selection"));
    }

    @Override
    protected void init() {
        super.init();
        cards.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();

        List<Integer> active = server != null
                ? ChallengeSavedData.get(server.getOverworld()).getActive()
                : List.of();

        // Read the CURRENT saved slider settings from the world (fallback to sane defaults)
        int savedMaxHeartsTicks = server != null
                ? ChallengeSavedData.get(server.getOverworld()).getMaxHeartsTicks()
                : 0;
        if (savedMaxHeartsTicks <= 0) savedMaxHeartsTicks = 20; // default 10 hearts

        int savedSlots = server != null
                ? ChallengeSavedData.get(server.getOverworld()).getLimitedInventorySlots()
                : 0;
        if (savedSlots <= 0) savedSlots = 36; // default full inventory

        int savedMobHealthMult = server != null
                ? ChallengeSavedData.get(server.getOverworld()).getMobHealthMultiplier()
                : 0;
        if (savedMobHealthMult <= 0) savedMobHealthMult = 1;

        // Convert saved ticks/slots -> slider knob value (0.0 .. 1.0)
        sliderTicks = savedMaxHeartsTicks;                  // 1..20
        sliderValue = (sliderTicks - 1) / 19.0;

        slotsSliderTicks = savedSlots;                      // 1..36
        slotsSliderValue = (slotsSliderTicks - 1) / 35.0;

        mobHealthMultiplier = savedMobHealthMult;
        mobHealthSliderValue = (mobHealthMultiplier - 1) / 99.0;

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

        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);
            boolean isOn = active.contains(id);

            // If we have a special case (slider) and we are currently on the right (col == 1),
            // move to the next row to ensure the card starts on the left.
            if ((id == 7 || id == 12 || id == 24) && col == 1) {
                y += cardHeight + spacing;
                col = 0;
            }

            int currentX = (col == 0) ? x0 : x1;
            int currentY = y;

            ChallengeCardWidget card = new ChallengeCardWidget(currentX, currentY, cardWidth, cardHeight, id, isOn, val -> updateSaveButton());
            cards.add(card);
            scrollPanel.addChild(card);

            // Handle standard card col increment for the card we just added
            if (col == 1) {
                y += cardHeight + spacing;
                col = 0;
            } else {
                col = 1;
            }

            if (id == 7) {
                // Now col should be 1 (because 7 was at col 0), so the slider goes to the right of the card.
                double initialHearts = 0.5 + (sliderValue * 9.5);
                maxHealthSlider = new SliderWidget(
                        x1, y, cardWidth, cardHeight,
                        Text.literal(String.format("Health: %.1f❤", initialHearts)),
                        sliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double hearts = 0.5 + (this.value * 9.5);
                        setMessage(Text.literal(String.format("Health: %.1f❤", hearts)));
                    }

                    @Override
                    protected void applyValue() {
                        ChallengeSelectionScreen.this.sliderTicks =
                                (int)(Math.round(this.value * 19) + 1);
                        this.value = (ChallengeSelectionScreen.this.sliderTicks - 1) / 19.0;
                    }
                };
                scrollPanel.addChild(maxHealthSlider);
                
                // Since we manually filled col 1 with a slider, move to next row
                y += cardHeight + spacing;
                col = 0;
            } else if (id == 12) {
                double initialSlots = 1 + (slotsSliderValue * 35);
                slotsSlider = new SliderWidget(
                        x1, y, cardWidth, cardHeight,
                        Text.literal(String.format("Slots: %.0f", initialSlots)),
                        slotsSliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double slots = 1 + (this.value * 35);
                        setMessage(Text.literal(String.format("Slots: %.0f", slots)));
                    }

                    @Override
                    protected void applyValue() {
                        slotsSliderTicks = (int)(Math.round(this.value * 35) + 1);
                        this.value  = (slotsSliderTicks - 1) / 35.0;
                    }
                };
                scrollPanel.addChild(slotsSlider);
                
                y += cardHeight + spacing;
                col = 0;
            } else if (id == 24) {
                double initialMult = 1 + (mobHealthSliderValue * 99);
                mobHealthSlider = new SliderWidget(
                        x1, y, cardWidth, cardHeight,
                        Text.literal(String.format("Mob Health: %.0fx", initialMult)),
                        mobHealthSliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double mult = 1 + (this.value * 99);
                        setMessage(Text.literal(String.format("Mob Health: %.0fx", mult)));
                    }

                    @Override
                    protected void applyValue() {
                        mobHealthMultiplier = (int)(Math.round(this.value * 99) + 1);
                        this.value = (mobHealthMultiplier - 1) / 99.0;
                    }
                };
                scrollPanel.addChild(mobHealthSlider);

                y += cardHeight + spacing;
                col = 0;
            }
        }
        if (col == 1) y += cardHeight + spacing;

        // Save button anchored at bottom (not inside the scroll area)
        int saveY = panelTop + panelHeight + 10;
        this.saveButton = new SaveButton(
                width / 2 - 50, saveY, 100, 20,
                Text.literal("Save"),
                btn -> {
                    List<Integer> newActive = getActiveIds();
                    // ...
                    int ticks = 0;
                    if (newActive.contains(7) && maxHealthSlider != null) {
                        ticks = this.sliderTicks;
                    }

                    int slotticks = 0;
                    if (newActive.contains(12) && slotsSlider != null) {
                        slotticks = this.slotsSliderTicks;
                    }

                    int mobHealthMult = 1;
                    if (newActive.contains(24) && mobHealthSlider != null) {
                        mobHealthMult = this.mobHealthMultiplier;
                    }

                    ChallengeCraft.LOGGER.info(
                            "[Client:Selection] Save pressed → active = {} , maxHearts ticks = {}, slots = {}, mobHealth = {}",
                            newActive, ticks, slotticks, mobHealthMult
                    );

                    ClientPlayNetworking.send(new ChallengePacket(newActive, ticks, slotticks, mobHealthMult));
                    ChallengeCraft.LOGGER.info("[Client:Selection] sent ChallengePacket");

                    client.setScreen(null);
                }
        );
        addDrawableChild(this.saveButton);
        updateSaveButton();
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

    private void updateSaveButton() {
        if (saveButton != null) {
            saveButton.active = !net.kasax.challengecraft.ChallengeManager.hasConflict(getActiveIds());
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
        if (net.kasax.challengecraft.ChallengeManager.hasConflict(getActiveIds())) {
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
