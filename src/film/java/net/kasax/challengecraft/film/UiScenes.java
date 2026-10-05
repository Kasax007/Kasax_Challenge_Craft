package net.kasax.challengecraft.film;

import net.kasax.challengecraft.client.screen.ChallengeCardWidget;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.client.screen.RunSummaryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The menus: the challenge cards in world creation (ticked one after another, the difficulty
 * climbing), the progress journey, a run summary card, the title screen hub. Filmed without a
 * world, straight from the title screen.
 */
final class UiScenes {
    private final FilmDirector d;

    private UiScenes(FilmDirector d) {
        this.d = d;
    }

    static boolean film(FilmDirector d, String scene) {
        UiScenes u = new UiScenes(d);
        switch (scene) {
            case "ui_probe" -> u.probe();
            case "ui_select" -> u.select();
            case "ui_journey" -> u.journey();
            case "ui_summary" -> u.summary();
            case "ui_title" -> u.title();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void scale(int gui) {
        d.client(mc -> {
            mc.options.guiScale().set(gui);
            mc.resizeGui();
        });
        d.run(2);
    }

    private void window(int w, int h) {
        d.ctx.getInput().resizeWindow(w, h);
        d.run(3);
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

    /** Stills of the menus at a few sizes, to choose how to film them. */
    private void probe() {
        for (int[] v : new int[][]{{1080, 1920, 3}, {1080, 1920, 4}, {1920, 1080, 4}}) {
            window(v[0], v[1]);
            scale(v[2]);
            String tag = v[0] + "x" + v[1] + "_g" + v[2];
            d.still("title_" + tag);
            openCreateWorld();
            d.still("select_" + tag);
            d.client(mc -> mc.setScreenAndShow(new LevelingScreen(null)));
            d.run(10);
            d.still("journey_" + tag);
            d.client(mc -> mc.setScreenAndShow(new RunSummaryScreen(demo(false, false))));
            d.run(10);
            d.still("summary_" + tag);
            d.client(mc -> mc.setScreenAndShow(new TitleScreen()));
            d.run(5);
        }
        window(FilmDirector.WIDTH, FilmDirector.HEIGHT);
        scale(4);
    }

    /** Ticking challenge cards one after another; the difficulty rating climbs. */
    private void select() {
        window(FilmDirector.WIDTH, FilmDirector.HEIGHT);
        scale(3);
        openCreateWorld();
        List<ChallengeCardWidget> cards = cards();
        FilmDirector.LOG.info("[Film] {} challenge cards on screen", cards.size());
        int[] picks = {0, 3, 5, 8, 10, 13};
        d.shoot("ui_select", 6.0, t -> {
            for (int i = 0; i < picks.length; i++) {
                if (t == 12 + i * 14 && picks[i] < cards.size()) {
                    ChallengeCardWidget c = cards.get(picks[i]);
                    d.client(mc -> {
                        double x = c.getX() + c.getWidth() / 2.0, y = c.getY() + c.getHeight() / 2.0;
                        c.onClick(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                    });
                }
            }
        });
        d.client(mc -> mc.setScreenAndShow(new TitleScreen()));
        scale(4);
    }

    private List<ChallengeCardWidget> cards() {
        return d.ctx.computeOnClient(mc -> {
            List<ChallengeCardWidget> out = new ArrayList<>();
            collect(mc.gui.screen(), out);
            out.sort((p, q) -> p.getY() != q.getY() ? Integer.compare(p.getY(), q.getY()) : Integer.compare(p.getX(), q.getX()));
            return out;
        });
    }

    private static void collect(Object node, List<ChallengeCardWidget> out) {
        if (node instanceof ChallengeCardWidget c) {
            if (c.visible) out.add(c);
            return;
        }
        if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler h) {
            for (GuiEventListener child : h.children()) collect(child, out);
        }
    }

    /** The progress journey, slowly scrolled. */
    private void journey() {
        window(FilmDirector.WIDTH, FilmDirector.HEIGHT);
        scale(3);
        d.client(mc -> mc.setScreenAndShow(new LevelingScreen(null)));
        d.run(10);
        d.shoot("ui_journey", 5.0, t -> {
            if (t % 4 == 0) d.client(mc -> mc.gui.screen().mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0,
                    mc.getWindow().getGuiScaledHeight() / 2.0, 0, -0.25));
        });
        d.client(mc -> mc.setScreenAndShow(new TitleScreen()));
        scale(4);
    }

    /** A run summary card (the demo one the game shows for layout checks). */
    private void summary() {
        window(FilmDirector.WIDTH, FilmDirector.HEIGHT);
        scale(3);
        d.client(mc -> mc.setScreenAndShow(new RunSummaryScreen(demo(false, false))));
        d.run(5);
        d.shoot("ui_summary", 4.0, null);
        d.client(mc -> mc.setScreenAndShow(new TitleScreen()));
        scale(4);
    }

    /** The title screen with the hub (level, next unlock, recent runs). */
    private void title() {
        window(FilmDirector.WIDTH, FilmDirector.HEIGHT);
        scale(3);
        d.client(mc -> mc.setScreenAndShow(new TitleScreen()));
        d.run(10);
        d.shoot("ui_title", 3.0, null);
        scale(4);
    }

    private static net.kasax.challengecraft.network.RunSummaryPacket demo(boolean daily, boolean versus) {
        try {
            Method m = net.kasax.challengecraft.client.ClientCommands.class.getDeclaredMethod("demoSummary", boolean.class, boolean.class);
            m.setAccessible(true);
            return (net.kasax.challengecraft.network.RunSummaryPacket) m.invoke(null, daily, versus);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
