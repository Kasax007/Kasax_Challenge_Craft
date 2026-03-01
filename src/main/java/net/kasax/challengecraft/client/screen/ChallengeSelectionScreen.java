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
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ChallengeSelectionScreen extends Screen {
    private static final List<Integer> IDS = List.of(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20);
    private static final List<Text> TITLES = IDS.stream()
            .map(id -> (Text) Text.translatable("challengecraft.worldcreate.challenge" + id))
            .toList();

    private static final List<Text> DESCRIPTIONS = IDS.stream()
            .map(id -> (Text) Text.translatable("challengecraft.worldcreate.challenge" + id + ".desc"))
            .toList();

    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();
    private SliderWidget maxHealthSlider;
    private SliderWidget slotsSlider;

    private WidgetScrollPanel scrollPanel;

    // sliderValue is the raw 0.0–1.0 knob position
    private double sliderValue;
    // sliderTicks is 1–20 quantized half-heart steps
    private int sliderTicks;
    private double slotsSliderValue;
    private int slotsSliderTicks;

    public ChallengeSelectionScreen() {
        super(Text.literal("Modify active Challenges (CAUTION: may break world)"));
    }

    @Override
    protected void init() {
        super.init();
        toggles.clear();

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

        // Convert saved ticks/slots -> slider knob value (0.0 .. 1.0)
        sliderTicks = savedMaxHeartsTicks;                  // 1..20
        sliderValue = (sliderTicks - 1) / 19.0;

        slotsSliderTicks = savedSlots;                      // 1..36
        slotsSliderValue = (slotsSliderTicks - 1) / 35.0;

        int panelWidth = 240;
        int panelX = width / 2 - panelWidth / 2;
        int panelTop = 40;
        int panelBottomReserved = 44; // space for Save button
        int panelHeight = Math.max(60, height - panelTop - panelBottomReserved);

        this.scrollPanel = new WidgetScrollPanel(panelX, panelTop, panelWidth, panelHeight, Text.empty());
        addDrawableChild(this.scrollPanel);

        int x = panelX + (panelWidth / 2) - 100;
        int y = panelTop + 6;

        for (int i = 0; i < IDS.size(); i++) {
            final int index = i;
            int id = IDS.get(i);
            boolean isOn = active.contains(id);

            if (id == 7) {
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .tooltip(val -> Tooltip.of(DESCRIPTIONS.get(index)))
                        .build(x, y, 200, 20, TITLES.get(i), (btn, val) -> {});
                toggles.add(toggle);
                scrollPanel.addChild(toggle);
                y += 24;

                double initialHearts = 0.5 + (sliderValue * 9.5);
                maxHealthSlider = new SliderWidget(
                        x, y, 200, 20,
                        Text.literal(String.format("Max Health: %.1f❤", initialHearts)),
                        sliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double hearts = 0.5 + (this.value * 9.5);
                        setMessage(Text.literal(String.format("Max Health: %.1f❤", hearts)));
                    }

                    @Override
                    protected void applyValue() {
                        ChallengeSelectionScreen.this.sliderTicks =
                                (int)(Math.round(this.value * 19) + 1);
                        this.value = (ChallengeSelectionScreen.this.sliderTicks - 1) / 19.0;
                    }
                };
                scrollPanel.addChild(maxHealthSlider);
                y += 24;

            } else if (id == 12) {
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .tooltip(val -> Tooltip.of(DESCRIPTIONS.get(index)))
                        .build(x, y, 200, 20, TITLES.get(i), (btn, val) -> {});
                toggles.add(toggle);
                scrollPanel.addChild(toggle);
                y += 24;

                double initialSlots = 1 + (slotsSliderValue * 35);
                slotsSlider = new SliderWidget(
                        x, y, 200, 20,
                        Text.literal(String.format("Inventory Slots: %.0f", initialSlots)),
                        slotsSliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double slots = 1 + (this.value * 35);
                        setMessage(Text.literal(String.format("Inventory Slots: %.0f", slots)));
                    }

                    @Override
                    protected void applyValue() {
                        slotsSliderTicks = (int)(Math.round(this.value * 35) + 1);
                        this.value  = (slotsSliderTicks - 1) / 35.0;
                    }
                };
                scrollPanel.addChild(slotsSlider);
                y += 24;

            } else {
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .tooltip(val -> Tooltip.of(DESCRIPTIONS.get(index)))
                        .build(x, y, 200, 20, TITLES.get(i), (btn, val) -> {});
                toggles.add(toggle);
                scrollPanel.addChild(toggle);
                y += 24;
            }
        }

        // Save button anchored at bottom (not inside the scroll area)
        int saveY = panelTop + panelHeight + 10;
        addDrawableChild(new SaveButton(
                width / 2 - 50, saveY, 100, 20,
                Text.literal("Save"),
                btn -> {
                    List<Integer> newActive = new ArrayList<>();
                    for (int j = 0; j < IDS.size(); j++) {
                        if (toggles.get(j).getValue()) {
                            newActive.add(IDS.get(j));
                        }
                    }

                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeVarInt(newActive.size());
                    for (int cid : newActive) buf.writeVarInt(cid);

                    int ticks = 0;
                    if (newActive.contains(7) && maxHealthSlider != null) {
                        ticks = this.sliderTicks;
                    }

                    int slotticks = 0;
                    if (newActive.contains(12) && slotsSlider != null) {
                        slotticks = this.slotsSliderTicks;
                    }

                    ChallengeCraft.LOGGER.info(
                            "[Client:Selection] Save pressed → active = {} , maxHearts ticks = {}, slots = {}",
                            newActive, ticks, slotticks
                    );
                    buf.writeVarInt(ticks);
                    buf.writeVarInt(slotticks);

                    ClientPlayNetworking.send(new ChallengePacket(newActive, ticks, slotticks));
                    ChallengeCraft.LOGGER.info("[Client:Selection] sent ChallengePacket");

                    client.setScreen(null);
                }
        ));
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

        // Render the warning message
        Text warning = Text.translatable("challengecraft.warning.tainted");
        int textWidth = this.textRenderer.getWidth(warning);
        ctx.drawCenteredTextWithShadow(this.textRenderer, warning, width / 2, 20, 0xFF5555);
    }

    private static class SaveButton extends ButtonWidget {
        public SaveButton(int x, int y, int w, int h,
                          Text msg, PressAction onPress) {
            super(x, y, w, h, msg, onPress,
                    ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        }
    }
}
