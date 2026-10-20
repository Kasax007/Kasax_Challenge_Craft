package net.kasax.challengecraft.film;

import net.kasax.challengecraft.client.RunHistory;
import net.kasax.challengecraft.client.config.ChallengePresets;
import net.kasax.challengecraft.client.screen.ChallengeCardWidget;
import net.kasax.challengecraft.client.screen.ChallengeCodeScreen;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.client.screen.PresetScreen;
import net.kasax.challengecraft.client.widget.ChallengeHub;
import net.kasax.challengecraft.code.ChallengeCode;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * The menu shots for the long film: the challenge cards ticked one after another, the title
 * screen's hub, the journey with its perks, the preset list and the code dialog. A mouse of the
 * film ({@link Mouse}) glides to what is shown and clicks it; {@link FilmCursor} draws it.
 */
final class MenuScenes {
    /** The profile of the film player: level 20 and five Infinity Stars, so nothing is locked. */
    static final long XP = 24_000;

    private final FilmDirector d;

    private MenuScenes(FilmDirector d) {
        this.d = d;
    }

    static boolean film(FilmDirector d, String scene) {
        MenuScenes m = new MenuScenes(d);
        switch (scene) {
            case "ui_probe2" -> m.probe();
            case "ui_stack" -> m.stack();
            case "ui_hub" -> m.hub();
            case "ui_perks" -> m.perks();
            case "ui_presets" -> m.presets();
            case "ui_code" -> m.code();
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ plumbing

    private void window() {
        d.ctx.getInput().resizeWindow(FilmDirector.WIDTH, FilmDirector.HEIGHT);
        d.run(3);
    }

    private void scale(int gui) {
        d.client(mc -> {
            mc.options.guiScale().set(gui);
            mc.resizeGui();
        });
        d.run(3);
    }

    private void show(Screen screen) {
        d.client(mc -> mc.setScreenAndShow(screen));
        d.run(8);
    }

    private void backToTitle() {
        d.client(mc -> mc.setScreenAndShow(new TitleScreen()));
        FilmCursor.shown = false;
        scale(4);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(o);
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no field " + name + " in " + o.getClass());
    }

    private static int num(Object o, String name) {
        return ((Number) get(o, name)).intValue();
    }

    private void openCreateWorld() {
        d.client(mc -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(new TitleScreen())));
        d.ctx.waitForScreen(CreateWorldScreen.class);
        d.run(5);
        d.client(mc -> {
            try {
                Screen s = mc.gui.screen();
                Field bar = CreateWorldScreen.class.getDeclaredField("tabNavigationBar");
                bar.setAccessible(true);
                Field idx = CreateWorldScreen.class.getDeclaredField("challengeTabIndex");
                idx.setAccessible(true);
                Object tabs = bar.get(s);
                Method select = tabs.getClass().getMethod("selectTab", int.class, boolean.class);
                select.invoke(tabs, idx.getInt(s), false);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        d.run(5);
    }

    // ------------------------------------------------------------------ the film's mouse

    /** A script of the pointer: glides, waits, clicks, side actions; played by tick inside a shot. */
    final class Mouse {
        private final class Step {
            final int from, to;
            Supplier<double[]> target;
            DoubleConsumer during;
            Runnable once;
            boolean click;
            double sx, sy;

            Step(int from, int to) {
                this.from = from;
                this.to = to;
            }
        }

        private final List<Step> steps = new ArrayList<>();
        private final double scale;
        private double x, y;
        private int at;

        Mouse(double gx, double gy) {
            this.scale = d.ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            this.x = gx;
            this.y = gy;
            FilmCursor.shown = true;
            apply();
        }

        int at() {
            return at;
        }

        private void apply() {
            d.ctx.getInput().setCursorPos(x * scale, y * scale);
        }

        Mouse glide(Supplier<double[]> target, int ticks) {
            Step s = new Step(at, at + ticks);
            s.target = target;
            steps.add(s);
            at += ticks;
            return this;
        }

        Mouse glide(double tx, double ty, int ticks) {
            return glide(() -> new double[]{tx, ty}, ticks);
        }

        /** Something that runs along {@code ticks} ticks from tick {@code from} (progress 0..1, eased), without moving the script on. */
        Mouse alongside(int from, int ticks, DoubleConsumer progress) {
            Step s = new Step(from, from + ticks);
            s.during = progress;
            steps.add(s);
            return this;
        }

        Mouse pause(int ticks) {
            at += ticks;
            return this;
        }

        Mouse click() {
            Step s = new Step(at, at);
            s.click = true;
            steps.add(s);
            at += 1;
            return this;
        }

        Mouse run(Runnable r) {
            Step s = new Step(at, at);
            s.once = r;
            steps.add(s);
            return this;
        }

        void tick(int t) {
            for (Step s : steps) {
                if (t < s.from || t > s.to) continue;
                if (t == s.from) {
                    s.sx = x;
                    s.sy = y;
                    if (s.once != null) s.once.run();
                    if (s.click) {
                        d.ctx.getInput().pressMouse(0);
                        FilmCursor.click();
                    }
                }
                double f = s.to == s.from ? 1.0 : ease((t - s.from) / (double) (s.to - s.from));
                if (s.during != null) s.during.accept(f);
                if (s.target != null) {
                    double[] tg = s.target.get();
                    x = s.sx + (tg[0] - s.sx) * f;
                    y = s.sy + (tg[1] - s.sy) * f;
                    apply();
                }
            }
        }
    }

    private static double ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // ------------------------------------------------------------------ scenes

    /** Stills of the menus at the GUI scales that might fit, to choose how to film them. */
    private void probe() {
        window();
        d.profileXp(XP);
        for (int gui : new int[]{4, 3}) {
            scale(gui);
            openCreateWorld();
            d.still("create_challenges_g" + gui);
            show(new LevelingScreen(new TitleScreen()));
            d.still("journey_g" + gui);
            show(new PresetScreen(new TitleScreen(), null, Component.empty(), c -> {
            }));
            d.still("presets_g" + gui);
            show(new ChallengeCodeScreen(new TitleScreen(), null, Component.empty(), c -> {
            }));
            d.still("code_g" + gui);
            show(new HubTitle());
            d.still("hub_g" + gui);
        }
        backToTitle();
    }

    private static final int[] STACK_PICKS = {29, 35, 48, 21};

    /** Ticking four challenge cards one after another; the difficulty rating and the XP payout climb. */
    private void stack() {
        window();
        d.profileXp(XP);
        scale(stackScale());
        openCreateWorld();
        Object tab = d.ctx.computeOnClient(mc -> get(mc.gui.screen(), "challengeTab"));
        AbstractScrollArea panel = get(tab, "scrollPanel");
        Map<Integer, ChallengeCardWidget> byId = get(tab, "cardById");
        double[] start = {panel.getX() + panel.getWidth() * 0.5, panel.getY() + panel.getHeight() * 0.85};
        Mouse m = new Mouse(start[0], start[1]);
        m.pause(24);
        for (int id : STACK_PICKS) {
            ChallengeCardWidget card = byId.get(id);
            double from = panel.scrollAmount();
            double[] scrollFrom = {from};
            int t0 = m.at();
            m.run(() -> scrollFrom[0] = panel.scrollAmount());
            double target = Math.max(0, Math.min(panel.maxScrollAmount(),
                    card.getY() + card.getHeight() / 2.0 - (panel.getY() + panel.getHeight() * 0.42)));
            m.alongside(t0 + 1, 34, f -> panel.setScrollAmount(scrollFrom[0] + (target - scrollFrom[0]) * f));
            m.glide(() -> new double[]{card.getX() + card.getWidth() * 0.5, card.getY() - panel.scrollAmount() + card.getHeight() * 0.5}, 38);
            m.pause(4).click().pause(34);
        }
        FilmDirector.LOG.info("[Film] ui_stack script is {} ticks long", m.at());
        d.shoot("ui_stack", 12.0, m::tick);
        backToTitle();
    }

    private int stackScale() {
        return 4;
    }

    /** The title screen's Challenge Hub on its own: level, next unlock, today's daily, codes, recent runs. */
    private void hub() {
        window();
        d.profileXp(XP);
        seedRuns();
        scale(4);
        show(new HubTitle());
        List<AbstractWidget> widgets = hubWidgets();
        FilmDirector.LOG.info("[Film] hub widgets: {}", widgets.size());
        AbstractWidget first = widgets.get(0);
        Mouse m = new Mouse(first.getX() + first.getWidth() * 0.5, first.getY() + first.getHeight() + 150);
        m.pause(10);
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget w = widgets.get(i);
            // The three code / preset / progress buttons are one row: touch the middle one only.
            m.glide(w.getX() + w.getWidth() * 0.5, w.getY() + w.getHeight() * 0.5, 22).pause(i == 0 ? 26 : 16);
        }
        m.glide(first.getX() + first.getWidth() * 0.55, first.getY() + first.getHeight() * 0.5, 30);
        d.shoot("ui_hub", 8.0, m::tick);
        backToTitle();
    }

    private List<AbstractWidget> hubWidgets() {
        return d.ctx.computeOnClient(mc -> {
            List<AbstractWidget> out = new ArrayList<>();
            for (GuiEventListener g : mc.gui.screen().children()) {
                if (g instanceof AbstractWidget w) out.add(w);
            }
            out.sort(Comparator.comparingInt(AbstractWidget::getY));
            return out;
        });
    }

    /** A few finished runs for the hub's list (the history file is the player's own, so it is seeded here). */
    private void seedRuns() {
        try {
            Field f = RunHistory.class.getDeclaredField("entries");
            f.setAccessible(true);
            long now = System.currentTimeMillis();
            List<RunHistory.Entry> runs = new ArrayList<>();
            runs.add(new RunHistory.Entry("film_a", "Lava Run", List.of(29, 35), -1, 21_600, RunHistory.State.DONE, 740, now - 3_600_000L));
            runs.add(new RunHistory.Entry("film_b", "Daily", List.of(48), 3, 9_300, RunHistory.State.DONE, 520, now - 86_400_000L));
            runs.add(new RunHistory.Entry("film_c", "Hardcore", List.of(21, 33), -1, 64_800, RunHistory.State.DONE, 1_260, now - 3 * 86_400_000L));
            f.set(null, runs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The title screen with only the hub on it (it does not fit beside the vanilla buttons in a phone-wide window). */
    static final class HubTitle extends TitleScreen {
        @Override
        protected void init() {
            super.init();
            this.clearWidgets();
            try {
                Field f = TitleScreen.class.getDeclaredField("challengecraft$hub");
                f.setAccessible(true);
                f.setBoolean(this, true);
            } catch (ReflectiveOperationException ignored) {
                // the corner badge stays: harmless
            }
            int w = Math.min(260, this.width - 8);
            int top = this.height / 4 + 12;
            ChallengeHub.build(this, this::addRenderableWidget, (this.width - w) / 2, top, w, this.height - top - 14);
        }
    }

    /** The journey, scrolled slowly down the early levels; the pointer rests on the milestones with perks. */
    private void perks() {
        window();
        d.profileXp(9_000);   // level 13: the early perks are earned, the next ones are ahead
        scale(3);
        show(new LevelingScreen(new TitleScreen()));
        Object screen = d.ctx.computeOnClient(mc -> mc.gui.screen());
        AbstractScrollArea panel = get(screen, "journeyPanel");
        List<Object> milestones = get(screen, "milestones");
        Mouse m = new Mouse(panel.getX() + panel.getWidth() * 0.5, panel.getY() + panel.getHeight() * 0.5);
        m.pause(6);
        int[] levels = {3, 5, 10, 11};
        for (int lv : levels) {
            Object ms = milestones.get(lv - 1);
            double[] from = {0};
            int t0 = m.at();
            m.run(() -> from[0] = panel.scrollAmount());
            double target = Math.max(0, Math.min(panel.maxScrollAmount(), num(ms, "anchorY") - panel.getHeight() * 0.42));
            m.alongside(t0 + 1, 22, f -> panel.setScrollAmount(from[0] + (target - from[0]) * f));
            m.glide(() -> new double[]{panel.getX() + num(ms, "anchorX"), panel.getY() + num(ms, "anchorY") - panel.scrollAmount()}, 26);
            m.pause(28);
        }
        FilmDirector.LOG.info("[Film] ui_perks script is {} ticks long", m.at());
        d.shoot("ui_perks", 8.0, m::tick);
        backToTitle();
    }

    private static final List<List<Integer>> PRESETS = List.of(List.of(29, 35), List.of(48, 21), List.of(33, 34, 14), List.of(46, 41));
    private static final String[] PRESET_NAMES = {"Lava party", "Nerves of steel", "Chaos run", "Dice & no jumping"};

    private ChallengeCode setup(List<Integer> ids) {
        return ChallengeCode.of(ids, List.of(), 20, 36, 1, 2, 1, 60, null);
    }

    /** The preset list: saved setups, loaded with one click, and saving the current one under a name. */
    private void presets() {
        window();
        d.profileXp(XP);
        for (int i = PRESETS.size() - 1; i >= 0; i--) {
            ChallengePresets.save(PRESET_NAMES[i], setup(PRESETS.get(i)).encode());
        }
        scale(3);
        show(new PresetScreen(new TitleScreen(), setup(List.of(11, 16, 20)), Component.translatable("challengecraft.code.hint.create_world"), c -> {
        }));
        Screen s = d.ctx.computeOnClient(mc -> mc.gui.screen());
        EditBox name = get(s, "nameBox");
        Mouse m = new Mouse(name.getX() + 20, name.getY() - 60);
        m.pause(10);
        m.glide(() -> new double[]{name.getX() + 40, name.getY() + 9}, 24).pause(4).click().pause(4);
        String typed = "Sky farm";
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            m.run(() -> d.client(mc -> mc.gui.screen().charTyped(new CharacterEvent(c)))).pause(4);
        }
        m.pause(30);
        FilmDirector.LOG.info("[Film] ui_presets script is {} ticks long", m.at());
        d.shoot("ui_presets", 6.0, m::tick);
        backToTitle();
    }

    /** The code dialog: the code of what is set up, and a friend's code typed in with its preview. */
    private void code() {
        window();
        d.profileXp(XP);
        scale(3);
        ChallengeCode mine = ChallengeCode.of(List.of(29, 35, 48), List.of(), 20, 36, 1, 4, 1, 60, 777L);
        ChallengeCode friends = setup(List.of(33, 34, 21, 46));
        show(new ChallengeCodeScreen(new TitleScreen(), mine, Component.translatable("challengecraft.code.hint.create_world"), c -> {
        }));
        Screen s = d.ctx.computeOnClient(mc -> mc.gui.screen());
        EditBox input = get(s, "input");
        d.client(mc -> input.setValue(""));
        Mouse m = new Mouse(input.getX() + 60, input.getY() - 70);
        m.pause(16);
        m.glide(() -> new double[]{input.getX() + 50, input.getY() + 9}, 22).pause(4).click().pause(6);
        String typed = friends.encode();
        FilmDirector.LOG.info("[Film] the friend's code is {}", typed);
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            m.run(() -> d.client(mc -> mc.gui.screen().charTyped(new CharacterEvent(c)))).pause(i % 2 == 0 ? 2 : 1);
        }
        m.pause(40);
        FilmDirector.LOG.info("[Film] ui_code script is {} ticks long", m.at());
        d.shoot("ui_code", 6.0, m::tick);
        backToTitle();
    }
}
