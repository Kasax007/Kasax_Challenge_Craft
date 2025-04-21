package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ChallengeTab extends GridScreenTab {
    private static final Text TITLE = Text.literal("Challenges");
    private int selectedValue = 1;
    private final ButtonWidget cycleButton;

    public ChallengeTab() {
        super(TITLE);

        // lay out one widget per row, 8px spacing
        GridWidget.Adder adder = this.grid.setRowSpacing(8).createAdder(1);

        // our cycle‑button, initial label "Challenge: 1"
        this.cycleButton = ButtonWidget.builder(
                        Text.literal("Challenge: " + selectedValue),
                        button -> {
                            selectedValue = selectedValue % 5 + 1;
                            button.setMessage(Text.literal("Challenge: " + selectedValue));
                        }
                )
                .dimensions(0, 0, 210, 20) // width=210 to match other tabs
                .build();

        // center it
        adder.add(this.cycleButton, adder.copyPositioner().alignHorizontalCenter());
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {
        consumer.accept(this.cycleButton);
    }

    /** Expose the 1–5 value so you can read it in your create‑world callback. */
    public int getSelectedValue() {
        return selectedValue;
    }
}
