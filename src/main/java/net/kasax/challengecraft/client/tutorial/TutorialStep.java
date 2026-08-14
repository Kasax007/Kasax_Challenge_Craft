package net.kasax.challengecraft.client.tutorial;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One tutorial step.
 *
 * <p>A step is deliberately described in terms of what is TRUE, not what the player must do:
 * {@link #appliesTo} says which screen it belongs on, {@link #isComplete} says when to move on.
 * Nothing here can tell the player "you pressed the wrong thing", because nothing here assumes an
 * order of presses — only observed state. That is what lets the tutorial survive a player who
 * navigates their own way, and it removes the need to know in advance whether e.g. the main menu
 * takes them to the world list automatically or via a button.
 */
public record TutorialStep(
        String id,
        /** Which screen this step is shown on. */
        Predicate<Screen> appliesTo,
        /** Locates the widget to highlight on that screen, or null for a message with no target. */
        Function<Screen, AbstractWidget> target,
        /** Title line. */
        String titleKey,
        /** Body lines; may be empty. */
        List<String> bodyKeys,
        /** True once the player has done whatever this step was asking for. */
        Predicate<Screen> isComplete,
        /**
         * True for pure-explanation steps, which have nothing to observe and are dismissed by
         * clicking. Those steps MUST say so on screen — a card that waits for a click without
         * mentioning one just looks stuck.
         */
        boolean clickToAdvance
) {
    /** Convenience for steps that complete by themselves when the player does something. */
    public static TutorialStep observed(String id, Predicate<Screen> appliesTo,
                                        Function<Screen, AbstractWidget> target,
                                        String titleKey, List<String> bodyKeys,
                                        Predicate<Screen> isComplete) {
        return new TutorialStep(id, appliesTo, target, titleKey, bodyKeys, isComplete, false);
    }

    /** Convenience for steps that only explain and are dismissed by a click. */
    public static TutorialStep explain(String id, Predicate<Screen> appliesTo,
                                       Function<Screen, AbstractWidget> target,
                                       String titleKey, List<String> bodyKeys) {
        return new TutorialStep(id, appliesTo, target, titleKey, bodyKeys, never(), true);
    }

    public Component title() {
        return Component.translatable(titleKey);
    }

    public List<Component> body() {
        return bodyKeys.stream().map(k -> (Component) Component.translatable(k)).toList();
    }

    /** Convenience for steps that simply wait for the player to arrive on another screen. */
    public static Predicate<Screen> never() {
        return s -> false;
    }
}
