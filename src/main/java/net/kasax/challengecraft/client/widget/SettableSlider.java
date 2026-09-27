package net.kasax.challengecraft.client.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A vanilla slider that code outside the widget can move.
 *
 * <p>{@link AbstractSliderButton#setValue} is protected, so the anonymous sliders in the challenge
 * screens could only ever be moved by a mouse. Loading a daily or a challenge code has to put them
 * where the ruleset says; going through {@code setValue} runs the slider's own {@code applyValue}
 * and label update, exactly as a drag would, so the screen's derived numbers follow by themselves.
 */
@Environment(EnvType.CLIENT)
public abstract class SettableSlider extends AbstractSliderButton {
    protected SettableSlider(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    /** Moves the slider to a normalised position (0..1), as if the player had dragged it there. */
    public void setSliderValue(double normalized) {
        setValue(Mth.clamp(normalized, 0.0, 1.0));
    }
}
