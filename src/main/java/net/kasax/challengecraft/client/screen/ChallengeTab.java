package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ChallengeTab extends GridScreenTab {
    private static final Text TITLE = Text.literal("Challenges");
    private static final List<Integer> IDS = List.of(1,2,3,4,5,6,7,8,9,10,11,12,13,14);

    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();
    private final SliderWidget maxHealthSlider;
    private final SliderWidget inventorySlider;

    private WidgetScrollPanel scrollPanel;

    // sliderValue from 0.0 to 1.0, sliderTicks from 1..20 (½♥ to 10♥)
    private double sliderValue = 0.5;
    private int sliderTicks = (int)(Math.round(sliderValue * 19) + 1);

    // sliderValue from 0.0 to 1.0, sliderTicks from 1..36 (1..36 slots)
    private double invetorysliderValue = 1;
    private int inventorysliderTicks = (int)(Math.round(invetorysliderValue * 35) + 1);

    public ChallengeTab() {
        super(TITLE);

        SliderWidget slider7 = null;
        SliderWidget slider8 = null;

        for (int id : IDS) {
            Text label = Text.translatable("challengecraft.worldcreate.challenge" + id);

            if (id == 7) {
                // challenge 7 toggle
                var toggle7 = CyclingButtonWidget
                        .onOffBuilder(false)
                        .build(0, 0, 210, 20, label, (b, v) -> {});
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

            } else if (id == 12) {
                var toggle12 = CyclingButtonWidget
                        .onOffBuilder(false)
                        .build(0, 0, 210, 20, label, (b, v) -> {});
                toggles.add(toggle12);

                slider8 = new SliderWidget(
                        0, 0, 210, 20,
                        Text.literal("Inventory Slots: 18"),
                        invetorysliderValue
                ) {
                    @Override
                    protected void updateMessage() {
                        double slots = 1 + (this.value * 35);
                        setMessage(Text.literal(String.format("Inventory Slots: %.1f", slots)));
                    }

                    @Override
                    protected void applyValue() {
                        inventorysliderTicks = (int)(Math.round(this.value * 35) + 1);
                        this.value  = (inventorysliderTicks - 1) / 35.0;
                    }
                };

            } else {
                var toggle = CyclingButtonWidget
                        .onOffBuilder(false)
                        .build(0, 0, 210, 20, label, (b, v) -> {});
                toggles.add(toggle);
            }
        }

        this.maxHealthSlider = slider7;
        this.inventorySlider = slider8;

        // Create ONCE. Do NOT replace this instance later, or CreateWorldScreen will keep the old reference.
        this.scrollPanel = new WidgetScrollPanel(0, 0, 1, 1, Text.empty());
    }

    @Override
    public void refreshGrid(ScreenRect tabArea) {
        int padding = 6;

        int panelX = tabArea.getLeft() + padding;
        int panelY = tabArea.getTop() + padding;
        int panelW = Math.max(60, tabArea.width() - padding * 2);
        int panelH = Math.max(60, tabArea.height() - padding * 2);

        // Resize/reposition the EXISTING panel instance
        this.scrollPanel.setX(panelX);
        this.scrollPanel.setY(panelY);
        this.scrollPanel.setWidth(panelW);
        this.scrollPanel.setHeight(panelH);

        // Rebuild panel contents for this size
        this.scrollPanel.clearChildren();

        int widgetW = Math.min(210, panelW - 12); // room for scrollbar + padding
        int x = panelX + (panelW / 2) - (widgetW / 2);
        int y = panelY + 4;

        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);

            CyclingButtonWidget<Boolean> toggle = toggles.get(i);
            toggle.setX(x);
            toggle.setY(y);
            toggle.setWidth(widgetW);
            toggle.setHeight(20);
            this.scrollPanel.addChild(toggle);
            y += 24;

            if (id == 7 && maxHealthSlider != null) {
                maxHealthSlider.setX(x);
                maxHealthSlider.setY(y);
                maxHealthSlider.setWidth(widgetW);
                maxHealthSlider.setHeight(20);
                this.scrollPanel.addChild(maxHealthSlider);
                y += 24;
            }

            if (id == 12 && inventorySlider != null) {
                inventorySlider.setX(x);
                inventorySlider.setY(y);
                inventorySlider.setWidth(widgetW);
                inventorySlider.setHeight(20);
                this.scrollPanel.addChild(inventorySlider);
                y += 24;
            }
        }
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {
        consumer.accept(this.scrollPanel);
    }

    public List<Integer> getActive() {
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (toggles.get(i).getValue()) {
                active.add(IDS.get(i));
            }
        }

        if (maxHealthSlider != null && toggles.get(6).getValue()) {
            ChallengeCraftClient.SELECTED_MAX_HEARTS = sliderTicks;
        }
        if (inventorySlider != null && toggles.get(11).getValue()) {
            ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = inventorysliderTicks;
        }

        return active;
    }
}
