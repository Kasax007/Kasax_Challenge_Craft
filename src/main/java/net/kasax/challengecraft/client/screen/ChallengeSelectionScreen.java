package net.kasax.challengecraft.client.screen;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
    private static final List<Integer> IDS = List.of(1,2,3,4,5,6,7,8,9,10,11);
    private static final List<Text> TITLES = IDS.stream()
            .map(id -> Text.of(Text.translatable("challengecraft.worldcreate.challenge" + id)))
            .toList();

    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();
    private SliderWidget maxHealthSlider;
    private double sliderValue;

    public ChallengeSelectionScreen() {
        super(Text.literal("Modify active Challenges (CAUTION: may break world)"));
    }

    @Override
    protected void init() {
        super.init();
        toggles.clear();
        sliderValue = ChallengeCraftClient.SELECTED_MAX_HEARTS > 0
                ? (ChallengeCraftClient.SELECTED_MAX_HEARTS - 1) / 19.0
                : 0.5;

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();
        List<Integer> active = server != null
                ? ChallengeSavedData.get(server.getOverworld()).getActive()
                : List.of();

        int y = height / 4;
        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);
            boolean isOn = active.contains(id);

            if (id == 7) {
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .build(width/2 - 100, y, 200, 20, TITLES.get(i), (b,v)->{});
                addDrawableChild(toggle);
                toggles.add(toggle);
                y += 24;

                maxHealthSlider = new SliderWidget(
                        width/2 - 100, y, 200, 20,
                        Text.literal("Max Health: 5.0❤"),
                        sliderValue
                ) {
                    private int sliderTicks = (int) (Math.round(this.value * 19) + 1);

                    @Override
                    protected void updateMessage() {
                        double hearts = 0.5 + (this.value * 9.5);
                        setMessage(Text.literal(String.format("Max Health: %.1f❤", hearts)));
                    }

                    @Override
                    protected void applyValue() {
                        sliderTicks = (int) (Math.round(this.value * 19) + 1);
                        this.value  = (sliderTicks - 1) / 19.0;
                    }

                    public int getSliderTicks() {
                        return sliderTicks;
                    }
                };
                addDrawableChild(maxHealthSlider);


                y += 24;
            } else {
                var toggle = CyclingButtonWidget
                        .onOffBuilder(isOn)
                        .build(width/2 - 100, y, 200, 20, TITLES.get(i), (b,v)->{});
                addDrawableChild(toggle);
                toggles.add(toggle);
                y += 24;
            }
        }

        addDrawableChild(new SaveButton(
                width/2 - 50, y + 10, 100, 20,
                Text.literal("Save"),
                btn -> {
                    // 1) gather the toggles
                    List<Integer> newActive = new ArrayList<>();
                    for (int i = 0; i < IDS.size(); i++) {
                        if (toggles.get(i).getValue()) newActive.add(IDS.get(i));
                    }

                    // 2) build buf
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeVarInt(newActive.size());
                    for (int id : newActive) buf.writeVarInt(id);

                    // ← Right here you do:
                    int ticks = 0;
                    if (newActive.contains(7) && maxHealthSlider != null) {
                        // sliderValue runs from 0.0 → 1.0, so:
                        ticks = (int) (Math.round(sliderValue * 19) + 1);  // 1 … 20
                    }
                    buf.writeVarInt(ticks);

                    // 3) send
                    ClientPlayNetworking.send(new ChallengePacket(newActive, ticks));
                    client.setScreen(null);
                }
        ));

    }

    public boolean isPauseScreen() {
        return false;
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
        public SaveButton(int x, int y, int w, int h, Text msg, PressAction onPress) {
            super(x, y, w, h, msg, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        }
    }
}
