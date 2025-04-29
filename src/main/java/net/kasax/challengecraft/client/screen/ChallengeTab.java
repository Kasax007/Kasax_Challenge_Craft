package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ChallengeTab extends GridScreenTab {
    private static final Text TITLE = Text.literal("Challenges");
    private static final List<Integer> IDS = List.of(1,2,3,4,5,6,7,8,9,10,11);

    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();
    private final SliderWidget maxHealthSlider;

    // sliderValue from 0.0 to 1.0, sliderTicks from 1..20 (½♥ to 10♥)
    private double sliderValue = 0.5;
    private int sliderTicks = (int)(Math.round(sliderValue * 19) + 1);

    public ChallengeTab() {
        super(TITLE);

        GridWidget.Adder adder = this.grid.setRowSpacing(4).createAdder(1);

        SliderWidget slider7 = null;
        for (int id : IDS) {
            Text label = Text.translatable("challengecraft.worldcreate.challenge" + id);

            if (id == 7) {
                // challenge 7 toggle
                var toggle7 = CyclingButtonWidget
                        .onOffBuilder(false)
                        .build(0, 0, 210, 20, label, (b,v)->{});
                adder.add(toggle7, adder.copyPositioner().alignHorizontalCenter());
                toggles.add(toggle7);

                // challenge 7 slider
                slider7 = new SliderWidget(
                        0, 0, 210, 20,
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
                        // quantize to half-heart increments
                        sliderTicks = (int)(Math.round(this.value * 19) + 1);
                        this.value  = (sliderTicks - 1) / 19.0;
                    }
                };
                adder.add(slider7, adder.copyPositioner().alignHorizontalCenter());
            } else {
                var toggle = CyclingButtonWidget
                        .onOffBuilder(false)
                        .build(0, 0, 210, 20, label, (b,v)->{});
                adder.add(toggle, adder.copyPositioner().alignHorizontalCenter());
                toggles.add(toggle);
            }
        }

        this.maxHealthSlider = slider7;
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {
        toggles.forEach(consumer);
        if (maxHealthSlider != null) consumer.accept(maxHealthSlider);
    }

    public List<Integer> getActive() {
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (toggles.get(i).getValue()) {
                active.add(IDS.get(i));
            }
        }

        // if challenge 7 is enabled, store the quantized tick count
        if (maxHealthSlider != null && toggles.get(6).getValue()) {
            ChallengeCraftClient.SELECTED_MAX_HEARTS = sliderTicks;
        }

        return active;
    }
}
