package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.ChallengeManager;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ChallengeTab extends GridScreenTab {
    private static final Text TITLE = Text.literal("Challenges");
    private static final List<Integer> IDS = List.of(1, 11, 9, 5, 6, 8, 7, 12, 24, 2, 3, 4, 14, 15, 16, 10, 13, 17, 18, 19, 20, 21, 22, 23);

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private final SliderWidget maxHealthSlider;
    private final SliderWidget inventorySlider;
    private final SliderWidget mobHealthSlider;
    private Text difficultyText = Text.empty();

    private WidgetScrollPanel scrollPanel;

    // sliderValue from 0.0 to 1.0, sliderTicks from 1..20 (½♥ to 10♥)
    private double sliderValue = 1.0;
    private int sliderTicks = (int)(Math.round(sliderValue * 19) + 1);

    // sliderValue from 0.0 to 1.0, sliderTicks from 1..36 (1..36 slots)
    private double inventorySliderValue = 1.0;
    private int inventorysliderTicks = (int)(Math.round(inventorySliderValue * 35) + 1);

    // sliderValue from 0.0 to 1.0, multiplier from 1..100
    private double mobHealthSliderValue = 0.0;
    private int mobHealthMultiplier = 1;

    public ChallengeTab() {
        super(TITLE);

        for (int id : IDS) {
            ChallengeCardWidget card = new ChallengeCardWidget(0, 0, 100, 20, id, false, val -> updateDifficultyText());
            cards.add(card);
        }

        // Challenge 7 is at index 6
        this.maxHealthSlider = new SliderWidget(
                0, 0, 210, 20,
                Text.literal(String.format("Health: %.1f❤", 0.5 + (sliderValue * 9.5))),
                sliderValue
        ) {
            @Override
            protected void updateMessage() {
                double hearts = 0.5 + (this.value * 9.5);
                setMessage(Text.literal(String.format("Health: %.1f❤", hearts)));
            }

            @Override
            protected void applyValue() {
                // quantize to half-heart increments
                sliderTicks = (int)(Math.round(this.value * 19) + 1);
                this.value  = (sliderTicks - 1) / 19.0;
                updateDifficultyText();
            }
        };

        // Challenge 12 is at index 7
        this.inventorySlider = new SliderWidget(
                0, 0, 210, 20,
                Text.literal("Slots: 36"),
                inventorySliderValue
        ) {
            @Override
            protected void updateMessage() {
                double slots = 1 + (this.value * 35);
                setMessage(Text.literal(String.format("Slots: %.0f", slots)));
            }

            @Override
            protected void applyValue() {
                inventorysliderTicks = (int)(Math.round(this.value * 35) + 1);
                this.value  = (inventorysliderTicks - 1) / 35.0;
                updateDifficultyText();
            }
        };

        // Challenge 24
        this.mobHealthSlider = new SliderWidget(
                0, 0, 210, 20,
                Text.literal("Mob Health: 1x"),
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
                updateDifficultyText();
            }
        };

        // Create ONCE. Do NOT replace this instance later, or CreateWorldScreen will keep the old reference.
        this.scrollPanel = new WidgetScrollPanel(0, 0, 1, 1, Text.empty());
        updateDifficultyText();
    }

    private void updateDifficultyText() {
        List<Integer> activeIds = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (cards.get(i).isActive()) {
                activeIds.add(IDS.get(i));
            }
        }
        double total = ChallengeManager.calculateTotalDifficulty(activeIds, sliderTicks, inventorysliderTicks, mobHealthMultiplier);
        this.difficultyText = Text.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", total));
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

        int cardW = (panelW - 24) / 2;
        int cardH = 26;
        int spacing = 4;
        int x0 = panelX + 8;
        int x1 = x0 + cardW + spacing;
        int col = 0;
        int y = panelY + 4;

        // Add Difficulty Text at the top
        this.scrollPanel.addChild(new ClickableWidget(x0, y, panelW - 16, 20, Text.empty()) {
            @Override
            public Text getMessage() {
                return difficultyText;
            }
            @Override
            protected void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
                context.drawCenteredTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFF55);
            }
            @Override
            protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {}
        });
        y += 24;

        for (int i = 0; i < IDS.size(); i++) {
            int id = IDS.get(i);

            if ((id == 7 || id == 12 || id == 24) && col == 1) {
                y += cardH + spacing;
                col = 0;
            }

            ChallengeCardWidget card = cards.get(i);
            card.setX((col == 0) ? x0 : x1);
            card.setY(y);
            card.setWidth(cardW);
            card.setHeight(cardH);
            this.scrollPanel.addChild(card);

            if (col == 1) {
                y += cardH + spacing;
                col = 0;
            } else {
                col = 1;
            }

            if (id == 7 && maxHealthSlider != null) {
                maxHealthSlider.setX(x1);
                maxHealthSlider.setY(y);
                maxHealthSlider.setWidth(cardW);
                maxHealthSlider.setHeight(cardH);
                this.scrollPanel.addChild(maxHealthSlider);
                y += cardH + spacing;
                col = 0;
            }

            if (id == 12 && inventorySlider != null) {
                inventorySlider.setX(x1);
                inventorySlider.setY(y);
                inventorySlider.setWidth(cardW);
                inventorySlider.setHeight(cardH);
                this.scrollPanel.addChild(inventorySlider);
                y += cardH + spacing;
                col = 0;
            }

            if (id == 24 && mobHealthSlider != null) {
                mobHealthSlider.setX(x1);
                mobHealthSlider.setY(y);
                mobHealthSlider.setWidth(cardW);
                mobHealthSlider.setHeight(cardH);
                this.scrollPanel.addChild(mobHealthSlider);
                y += cardH + spacing;
                col = 0;
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
            if (cards.get(i).isActive()) {
                active.add(IDS.get(i));
            }
        }

        if (maxHealthSlider != null && active.contains(7)) {
            ChallengeCraftClient.SELECTED_MAX_HEARTS = sliderTicks;
        } else {
            ChallengeCraftClient.SELECTED_MAX_HEARTS = 20; // Default if OFF
        }
        
        if (inventorySlider != null && active.contains(12)) {
            ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = inventorysliderTicks;
        } else {
            ChallengeCraftClient.SELECTED_LIMITED_INVENTORY = 36; // Default if OFF
        }

        if (mobHealthSlider != null && active.contains(24)) {
            ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = mobHealthMultiplier;
        } else {
            ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER = 1;
        }

        return active;
    }
}
