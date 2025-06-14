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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ChallengeSelectionScreen extends Screen {
    private static final List<Integer> IDS = List.of(1,2,3,4,5,6,7,8,9,10,11,12);
    private static final List<Text> TITLES = IDS.stream()
            .map(id -> Text.of(Text.translatable("challengecraft.worldcreate.challenge" + id)))
            .toList();

    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();
    private SliderWidget maxHealthSlider;
    private SliderWidget slotsSlider;
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

        // initialize sliderValue & sliderTicks from the last saved hearts
        sliderValue  = ChallengeCraftClient.SELECTED_MAX_HEARTS > 0
                ? (ChallengeCraftClient.SELECTED_MAX_HEARTS - 1) / 19.0
                : 0.5;
        sliderTicks  = (int)(Math.round(sliderValue * 19) + 1);
        // initialize slots
        slotsSliderValue  = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY > 0
                ? (ChallengeCraftClient.SELECTED_LIMITED_INVENTORY - 1) / 35.0
                : 1;
        slotsSliderTicks  = (int)(Math.round(slotsSliderValue * 19) + 1);

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();
        List<Integer> active = server != null
                ? ChallengeSavedData.get(server.getOverworld()).getActive()
                : List.of();

        int y = height / 4;
        for (int i = 0; i < IDS.size(); i++) {
            int id     = IDS.get(i);
            boolean isOn = active.contains(id);

            if (id == 7) {
                // toggle for challenge #7
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .build(width/2 - 100, y, 200, 20,
                                TITLES.get(i), (btn,val)->{});
                addDrawableChild(toggle);
                toggles.add(toggle);
                y += 24;

                // slider under it
                maxHealthSlider = new SliderWidget(
                        width/2 - 100, y, 200, 20,
                        Text.literal("Max Health: 5.0❤"),
                        sliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double hearts = 0.5 + (this.value * 9.5);
                        setMessage(Text.literal(String.format("Max Health: %.1f❤", hearts)));
                    }

                    @Override
                    protected void applyValue() {
                        // quantize to half-heart steps
                        ChallengeSelectionScreen.this.sliderTicks =
                                (int)(Math.round(this.value * 19) + 1);
                        // snap the knob exactly onto that step
                        this.value = (ChallengeSelectionScreen.this.sliderTicks - 1) / 19.0;
                    }
                };
                addDrawableChild(maxHealthSlider);
                y += 24;

            } else if (id == 12) {
                // toggle for challenge #7
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .build(width/2 - 100, y, 200, 20,
                                TITLES.get(i), (btn,val)->{});
                addDrawableChild(toggle);
                toggles.add(toggle);
                y += 24;

                // slider under it
                slotsSlider = new SliderWidget(
                        width/2 - 100, y, 200, 20,
                        Text.literal("Inventory Slots: 18"),
                        slotsSliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double slots = 1 + (this.value * 35);
                        setMessage(Text.literal(String.format("Inventory Slots: %.1f", slots)));
                    }

                    @Override
                    protected void applyValue() {
                        // quantize to half-heart increments
                        slotsSliderTicks = (int)(Math.round(this.value * 35) + 1);
                        this.value  = (slotsSliderTicks - 1) / 35.0;
                    }
                };
                addDrawableChild(slotsSlider);
                y += 24;

            }

            else {
                // other toggles
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .build(width/2 - 100, y, 200, 20,
                                TITLES.get(i), (btn,val)->{});
                addDrawableChild(toggle);
                toggles.add(toggle);
                y += 24;
            }
        }

        // the Save button
        addDrawableChild(new SaveButton(
                width/2 - 50, y + 10, 100, 20,
                Text.literal("Save"),
                btn -> {
                    // 1) Gather active IDs
                    List<Integer> newActive = new ArrayList<>();
                    for (int j = 0; j < IDS.size(); j++) {
                        if (toggles.get(j).getValue()) {
                            newActive.add(IDS.get(j));
                        }
                    }

                    // 2) Prepare buffer
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeVarInt(newActive.size());
                    for (int id : newActive) buf.writeVarInt(id);

                    // 3) Grab quantized ticks from our field
                    int ticks = 0;
                    if (newActive.contains(7) && maxHealthSlider != null) {
                        ticks = this.sliderTicks;  // 1…20
                    }int slotticks = 0;
                    if (newActive.contains(12) && slotsSlider != null) {
                        slotticks = this.slotsSliderTicks;  // 1…20
                    }

                    ChallengeCraft.LOGGER.info(
                            "[Client:Selection] Save pressed → active = {} , maxHearts ticks = {}, slots = {}",
                            newActive, ticks, slotticks
                    );
                    buf.writeVarInt(ticks);
                    buf.writeVarInt(slotticks);

                    // 4) Send packet
                    ClientPlayNetworking.send(new ChallengePacket(newActive, ticks, slotticks));
                    ChallengeCraft.LOGGER.info("[Client:Selection] sent ChallengePacket");

                    // 5) Close the screen
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
    }

    private static class SaveButton extends ButtonWidget {
        public SaveButton(int x, int y, int w, int h,
                          Text msg, PressAction onPress) {
            super(x, y, w, h, msg, onPress,
                    ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        }
    }
}
