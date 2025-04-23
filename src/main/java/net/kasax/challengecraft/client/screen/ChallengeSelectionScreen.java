package net.kasax.challengecraft.client.screen;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ChallengeSelectionScreen extends Screen {
    private static final List<Integer> IDS = List.of(1,2,3,4,5,6);
    private static final List<Text> TITLES = IDS.stream()
            .map(id -> Text.of(Text.translatable("challengecraft.worldcreate.challenge" + id)))
            .toList();

    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();

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

        int y = height / 4;
        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);
            boolean isOn = active.contains(id);

            CyclingButtonWidget<Boolean> toggle = CyclingButtonWidget
                    .onOffBuilder(isOn)
                    .build(
                            width/2 - 100, y,
                            200, 20,
                            TITLES.get(i),
                            (btn,val)->{}  // no‐op
                    );
            addDrawableChild(toggle);
            toggles.add(toggle);
            y += 24;
        }

        addDrawableChild(new SaveButton(
                width/2 - 50, y + 10, 100, 20,
                Text.literal("Save"),
                btn -> {
                    // 1) Gather new list
                    List<Integer> newActive = new ArrayList<>();
                    for (int i = 0; i < IDS.size(); i++) {
                        if (toggles.get(i).getValue()) newActive.add(IDS.get(i));
                    }

                    // 2) Serialize into a PacketByteBuf
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeVarInt(newActive.size());
                    for (int id : newActive) {
                        buf.writeVarInt(id);
                    }

                    // 3) Send on the same channel your server handler is listening on:
                    ClientPlayNetworking.send(
                            new ChallengePacket(newActive)
                    );

                    // 4) Close the screen
                    client.setScreen(null);
                }
        ));
    }


    public boolean isPauseScreen() {
        return false;  // don’t pause the game
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
