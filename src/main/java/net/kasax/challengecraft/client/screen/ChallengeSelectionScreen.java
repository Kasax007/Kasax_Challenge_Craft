package net.kasax.challengecraft.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChallengeSelectionScreen extends Screen {
    // IDs and display names for each challenge
    private static final List<Integer> IDS = List.of(1,2,3,4,5);
    private static final List<Text> TITLES = List.of(
            Text.literal("Challenge #1: The Lucky Stick"),
            Text.literal("Challenge #2: …"),
            Text.literal("Challenge #3: …"),
            Text.literal("Challenge #4: …"),
            Text.literal("Challenge #5: …")
    );

    // One on/off toggle per challenge:
    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();

    public ChallengeSelectionScreen() {
        super(Text.translatable("screen.challengecraft.select_challenges"));
    }

    @Override
    protected void init() {
        super.init();
        toggles.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();

        // if there is no local server (i.e. multiplayer), just start with nothing
        List<Integer> active = List.of();
        if (server != null) {
            active = ChallengeSavedData.get(server.getOverworld()).getActive();
        }

        // now build your toggles exactly as before
        int y = this.height / 4;
        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);
            boolean isOn = active.contains(id);
            CyclingButtonWidget<Boolean> toggle = CyclingButtonWidget.onOffBuilder(isOn)
                    .build(this.width/2 - 100, y, 200, 20, TITLES.get(i), (btn,val)->{});
            this.addDrawableChild(toggle);
            toggles.add(toggle);
            y += 24;
        }

        this.addDrawableChild(new SaveButton(
                this.width/2 - 50, y + 10, 100, 20,
                Text.translatable("button.challengecraft.save"),
                btn -> saveAndClose()
        ));
    }


    private void saveAndClose() {
        // Gather which IDs are toggled on
        List<Integer> newActive = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (toggles.get(i).getValue()) {
                newActive.add(IDS.get(i));
            }
        }

        // Send our packet to the server
        ClientPlayNetworking.send(new ChallengePacket(newActive));
        // close screen
        assert this.client != null;
        this.client.setScreen(null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // hitting ESC will also save & close
        saveAndClose();
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
    }

    // A tiny subclass just to expose ButtonWidget’s protected constructor
    private static class SaveButton extends ButtonWidget {
        public SaveButton(int x, int y, int w, int h, Text msg, PressAction onPress) {
            super(x, y, w, h, msg, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        }
    }
}
