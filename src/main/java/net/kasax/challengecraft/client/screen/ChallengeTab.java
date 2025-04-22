package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.kasax.challengecraft.ChallengeCraft.LOGGER;

@Environment(EnvType.CLIENT)
public class ChallengeTab extends GridScreenTab {
    private static final Text TITLE = Text.literal("Challenges");
    private static final List<Integer> IDS = List.of(1, 2, 3, 4, 5);

    private final List<CyclingButtonWidget<Boolean>> toggles = new ArrayList<>();

    public ChallengeTab() {
        super(TITLE);

        // lay out one widget per row, 8px spacing
        GridWidget.Adder adder = this.grid.setRowSpacing(8).createAdder(1);

        for (int id : IDS) {
            // translation key: "challengecraft.worldcreate.challenge1", etc.
            Text label = Text.translatable("challengecraft.worldcreate.challenge" + id);

            CyclingButtonWidget<Boolean> toggle = CyclingButtonWidget
                    .onOffBuilder(false)  // default = off
                    .build(
                            0, 0,           // we'll let the GridWidget position it
                            210, 20,        // match other world‐create tab widths
                            label,
                            (btn, val) -> {
                                // no‐op; we just read getValue() later
                            }
                    );

            // center‐align each toggle
            adder.add(toggle, adder.copyPositioner().alignHorizontalCenter());
            toggles.add(toggle);
        }
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {
        // register all of our toggles so they receive mouse/key events
        for (var t : toggles) {
            consumer.accept(t);
        }
    }

    /**
     * @return a list of challenge‐IDs (1–5) that are currently toggled “on.”
     */
    public List<Integer> getActive() {
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            if (toggles.get(i).getValue()) {
                active.add(IDS.get(i));
            }
        }
        LOGGER.info("Challenge Tab getActive result " + active);
        return active;
    }
}
