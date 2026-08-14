package net.kasax.challengecraft.client.tutorial;

import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeManager;
import net.kasax.challengecraft.client.screen.ChallengeCardWidget;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.client.screen.WidgetScrollPanel;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The first-run tutorial's step list and state machine.
 *
 * <p>Steps advance on OBSERVED state — "this screen is open", "these challenges are selected" —
 * never on "the player must have clicked by now". That is what makes it safe for the player to
 * wander: nothing gets stuck waiting for a click that already happened a different way, and the
 * tutorial never has to know whether the main menu leads to the world list directly or via a
 * button.
 */
public final class TutorialManager {
    /** Challenges the tutorial asks the player to switch on, in the order it explains them. */
    public static final int CHAL_DAMAGE_ITEM = 18;
    public static final int CHAL_WALK_ITEM = 17;
    public static final int CHAL_LEVEL_ITEM = 1;
    public static final int CHAL_RANDOM_ITEM = 10;

    private static List<TutorialStep> steps;
    private static boolean active = false;

    private TutorialManager() {
    }

    public static boolean isActive() {
        return active && TutorialState.shouldRun();
    }

    public static void start() {
        if (!TutorialState.shouldRun()) return;
        active = true;
        ChallengeCraft.LOGGER.info("[Tutorial] Active, resuming at step {}", TutorialState.getStep());
    }

    public static void skip() {
        active = false;
        TutorialState.complete();
    }

    public static void replay() {
        TutorialState.reset();
        active = true;
    }

    // ---- widget lookup -------------------------------------------------------------------

    /** Every {@link AbstractWidget} reachable from a screen, including inside containers. */
    private static List<AbstractWidget> allWidgets(Screen screen) {
        List<AbstractWidget> out = new ArrayList<>();
        collect(screen, out, 0);
        return out;
    }

    private static void collect(GuiEventListener node, List<AbstractWidget> out, int depth) {
        if (depth > 6) return;   // containers can nest; this is plenty and cannot loop forever
        if (node instanceof AbstractWidget w) out.add(w);

        // WidgetScrollPanel is where the challenge cards actually live, and it is an
        // AbstractWidget — NOT a ContainerEventHandler — so a plain children() walk stops dead at
        // it and never sees a single card. That is why the "open the Challenges tab" step never
        // completed: it was waiting for cards it structurally could not find. Its own visitWidgets
        // is the way in.
        if (node instanceof WidgetScrollPanel panel) {
            panel.visitWidgets(w -> collect(w, out, depth + 1));
        }

        if (node instanceof ContainerEventHandler c) {
            for (GuiEventListener child : c.children()) {
                if (child != node) collect(child, out, depth + 1);
            }
        }
    }

    /** First widget whose visible label contains the given translated text, case-insensitively. */
    private static Function<Screen, AbstractWidget> byLabel(String... needles) {
        return screen -> {
            for (AbstractWidget w : allWidgets(screen)) {
                String msg = w.getMessage() == null ? "" : w.getMessage().getString().toLowerCase();
                if (msg.isBlank()) continue;
                for (String needle : needles) {
                    if (msg.contains(needle.toLowerCase())) return w;
                }
            }
            return null;
        };
    }

    private static Function<Screen, AbstractWidget> byType(Class<?> type) {
        return screen -> allWidgets(screen).stream()
                .filter(type::isInstance)
                .findFirst().orElse(null);
    }

    /** The card for one challenge id, wherever it currently sits in the layout. */
    private static Function<Screen, AbstractWidget> challengeCard(int id) {
        return screen -> allWidgets(screen).stream()
                .filter(w -> w instanceof ChallengeCardWidget c && c.getChallengeId() == id)
                .findFirst().orElse(null);
    }

    private static List<ChallengeCardWidget> cards(Screen screen) {
        return allWidgets(screen).stream()
                .filter(w -> w instanceof ChallengeCardWidget)
                .map(w -> (ChallengeCardWidget) w)
                .toList();
    }

    private static boolean isOn(Screen screen, int id) {
        return cards(screen).stream().anyMatch(c -> c.getChallengeId() == id && c.isActive());
    }

    /** The rating the create-world screen is showing, recomputed from the same source it uses. */
    public static double liveDifficulty(Screen screen) {
        List<Integer> active = new ArrayList<>();
        List<Integer> perks = new ArrayList<>();
        for (ChallengeCardWidget c : cards(screen)) {
            if (!c.isActive()) continue;
            if (c.getChallengeId() >= 100) perks.add(c.getChallengeId());
            else active.add(c.getChallengeId());
        }
        // Same call the tab makes, so the number in the tutorial can never disagree with the
        // number next to it.
        return ChallengeManager.calculateTotalDifficulty(active, 20, 36, 1, 1, 2, 1, perks);
    }

    // ---- steps ---------------------------------------------------------------------------

    private static final Predicate<Screen> ON_TITLE = s -> s instanceof TitleScreen;
    private static final Predicate<Screen> ON_LEVELING = s -> s instanceof LevelingScreen;
    private static final Predicate<Screen> ON_WORLD_SELECT = s -> s instanceof SelectWorldScreen;
    private static final Predicate<Screen> ON_CREATE = s -> s instanceof CreateWorldScreen;

    private static List<TutorialStep> steps() {
        if (steps != null) return steps;
        List<TutorialStep> s = new ArrayList<>();

        s.add(TutorialStep.observed("open_progress", ON_TITLE,
                byType(net.kasax.challengecraft.client.widget.AnimatedLevelButton.class),
                "challengecraft.tutorial.open_progress.title",
                List.of("challengecraft.tutorial.open_progress.body"),
                ON_LEVELING::test));

        s.add(TutorialStep.explain("progress_explained", ON_LEVELING, null,
                "challengecraft.tutorial.progress.title",
                List.of("challengecraft.tutorial.progress.body1", "challengecraft.tutorial.progress.body2")));

        s.add(TutorialStep.explain("layout_toggle", ON_LEVELING,
                byLabel("journey", "legacy"),
                "challengecraft.tutorial.toggle.title",
                List.of("challengecraft.tutorial.toggle.body")));

        s.add(TutorialStep.observed("back_to_menu", ON_LEVELING,
                byLabel("done", "back"),
                "challengecraft.tutorial.back.title",
                List.of("challengecraft.tutorial.back.body"),
                ON_TITLE::test));

        s.add(TutorialStep.observed("singleplayer", ON_TITLE,
                byLabel("singleplayer", "single player"),
                "challengecraft.tutorial.singleplayer.title",
                List.of("challengecraft.tutorial.singleplayer.body"),
                // Completes when the world list OR the create screen shows — however they got there.
                sc -> ON_WORLD_SELECT.test(sc) || ON_CREATE.test(sc)));

        s.add(TutorialStep.observed("create_world", ON_WORLD_SELECT,
                byLabel("create new world", "create"),
                "challengecraft.tutorial.create.title",
                List.of("challengecraft.tutorial.create.body"),
                ON_CREATE::test));

        s.add(TutorialStep.observed("challenges_tab", ON_CREATE,
                byLabel("challenges"),
                "challengecraft.tutorial.tab.title",
                List.of("challengecraft.tutorial.tab.body1", "challengecraft.tutorial.tab.body2"),
                // Completes as soon as any challenge card is on screen, i.e. the tab is open.
                sc -> !cards(sc).isEmpty()));

        s.add(TutorialStep.observed("pick_damage", ON_CREATE, challengeCard(CHAL_DAMAGE_ITEM),
                "challengecraft.tutorial.pick_damage.title",
                List.of("challengecraft.tutorial.pick_damage.body"),
                sc -> isOn(sc, CHAL_DAMAGE_ITEM)));

        s.add(TutorialStep.observed("pick_walk", ON_CREATE, challengeCard(CHAL_WALK_ITEM),
                "challengecraft.tutorial.pick_walk.title",
                List.of("challengecraft.tutorial.pick_walk.body"),
                sc -> isOn(sc, CHAL_WALK_ITEM)));

        s.add(TutorialStep.observed("pick_level", ON_CREATE, challengeCard(CHAL_LEVEL_ITEM),
                "challengecraft.tutorial.pick_level.title",
                List.of("challengecraft.tutorial.pick_level.body"),
                sc -> isOn(sc, CHAL_LEVEL_ITEM)));

        // By type, not by label: the bar's message is empty and its text is composed at draw time.
        s.add(TutorialStep.explain("difficulty", ON_CREATE,
                byType(net.kasax.challengecraft.client.screen.ChallengeTab.DifficultyBar.class),
                "challengecraft.tutorial.difficulty.title",
                List.of("challengecraft.tutorial.difficulty.body1", "challengecraft.tutorial.difficulty.body2")));

        s.add(TutorialStep.observed("helper", ON_CREATE, challengeCard(CHAL_RANDOM_ITEM),
                "challengecraft.tutorial.helper.title",
                List.of("challengecraft.tutorial.helper.body1", "challengecraft.tutorial.helper.body2"),
                sc -> isOn(sc, CHAL_RANDOM_ITEM)));

        s.add(TutorialStep.explain("finish", ON_CREATE, null,
                "challengecraft.tutorial.finish.title",
                List.of("challengecraft.tutorial.finish.body1",
                        "challengecraft.tutorial.finish.body2",
                        "challengecraft.tutorial.finish.body3")));

        steps = List.copyOf(s);
        return steps;
    }

    public static int stepCount() {
        return steps().size();
    }

    /**
     * The step to show on this screen right now, or null.
     *
     * <p>Advancing happens here rather than on a timer so that a step whose condition was satisfied
     * while the player was elsewhere (they opened the Progress screen before being asked to) is
     * consumed the moment it becomes observable, instead of asking for something already done.
     */
    public static TutorialStep current(Screen screen) {
        if (!isActive() || screen == null) return null;

        List<TutorialStep> all = steps();
        int idx = Math.max(0, Math.min(TutorialState.getStep(), all.size()));

        // Consume any steps this screen already satisfies.
        while (idx < all.size() && all.get(idx).isComplete().test(screen)) {
            idx++;
        }
        if (idx != TutorialState.getStep()) TutorialState.setStep(idx);

        if (idx >= all.size()) {
            ChallengeCraft.LOGGER.info("[Tutorial] All steps done");
            skip();     // same bookkeeping as pressing skip: mark complete, stop drawing
            return null;
        }

        TutorialStep step = all.get(idx);
        return step.appliesTo().test(screen) ? step : null;
    }

    /** Advances past a "read this" step that has no completion condition of its own. */
    public static void advanceManually() {
        TutorialState.setStep(TutorialState.getStep() + 1);
    }

    public static AbstractWidget resolveTarget(TutorialStep step, Screen screen) {
        return step.target() == null ? null : step.target().apply(screen);
    }

    /**
     * The scroll panel a target lives in, or null if it sits directly on the screen.
     *
     * <p>The overlay needs this for two reasons: a child's {@code getY()} is its layout position
     * rather than its on-screen position, and a card can be scrolled out of the visible band
     * entirely — highlighting it there would ring a piece of empty background.
     */
    public static WidgetScrollPanel panelOf(Screen screen, AbstractWidget target) {
        if (target == null) return null;
        for (AbstractWidget w : allWidgets(screen)) {
            if (w instanceof WidgetScrollPanel panel && panel.contains(target)) return panel;
        }
        return null;
    }
}
