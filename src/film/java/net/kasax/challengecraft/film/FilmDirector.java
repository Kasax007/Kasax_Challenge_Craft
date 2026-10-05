package net.kasax.challengecraft.film;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.kasax.challengecraft.client.tutorial.TutorialManager;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Films the scenes for the shorts, frame by frame: the game is stepped one tick at a time and
 * every frame is rendered at its own point between two ticks, so the footage is smooth however
 * slowly this machine renders. Runs only when scenes are named: {@code ./gradlew runFilm
 * -Pfilm=casino_slot,red_light -PfilmOut=DIR}. Each shot lands in {@code DIR/<shot>/f00000.png...}
 * at 30 frames a second of game time, in the window's size (1080x1920, so the HUD is laid out
 * for a phone screen).
 */
public class FilmDirector implements FabricClientGameTest {
    public static final Logger LOG = LoggerFactory.getLogger("Film");
    public static final int FPS = 30;
    public static final int WIDTH = 1080, HEIGHT = 1920;

    /** The challenges the next world is created with (read by {@code ChallengeTabFilmMixin}). */
    public static volatile List<Integer> forcedChallenges;
    public static volatile int doubleTrouble = 10, gameSpeed = 1;

    public ClientGameTestContext ctx;
    public TestSingleplayerContext sp;
    public Path out;
    /** Game ticks since the current world was opened. */
    public int tick;
    /** Where the film's clock stood when the world opened (see {@link FilmClock#millis()}). */
    private long clockBase;
    public final Cam cam = new Cam(this);

    /** Something done once per game tick while a shot runs; {@code t} counts the shot's ticks from 1. */
    public interface Every {
        void tick(int t);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String scenes = System.getProperty("film.scene", "");
        if (scenes.isEmpty()) return;
        this.ctx = ctx;
        out = Path.of(System.getProperty("film.out", "film"));
        setupClient();
        for (String scene : scenes.split(",")) {
            LOG.info("[Film] scene {}", scene);
            long t0 = System.currentTimeMillis();
            boolean known = CasinoScenes.film(this, scene) || ChallengeScenes.film(this, scene) || UiScenes.film(this, scene);
            if (!known) LOG.warn("[Film] no scene called {}", scene);
            if (sp != null) closeWorld();
            LOG.info("[Film] scene {} done in {} s ({})", scene, (System.currentTimeMillis() - t0) / 1000, memory());
        }
    }

    private void setupClient() {
        ctx.getInput().resizeWindow(WIDTH, HEIGHT);
        ctx.runOnClient(mc -> {
            var o = mc.options;
            o.guiScale().set(4);
            o.chatVisibility().set(ChatVisiblity.HIDDEN);
            o.fov().set(75);
            o.renderDistance().set(6);
            o.simulationDistance().set(8);
            o.bobView().set(true);
            o.particles().set(ParticleStatus.ALL);
            o.entityShadows().set(true);
            o.ambientOcclusion().set(true);
            o.cloudStatus().set(CloudStatus.FANCY);
            o.showAutosaveIndicator().set(false);
            o.damageTiltStrength().set(0.5);
            mc.resizeGui();
        });
        TutorialManager.skip();
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------ worlds

    /** A fresh world with these challenges; commands allowed, no chat noise. */
    public void openWorld(String seed, boolean creative, Integer... challenges) {
        forcedChallenges = List.of(challenges);
        sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
            s.setSeed(seed);
            s.setGameMode(creative ? WorldCreationUiState.SelectedGameMode.CREATIVE : WorldCreationUiState.SelectedGameMode.SURVIVAL);
            s.setAllowCommands(true);
            s.setDifficulty(Difficulty.NORMAL);
        }).create();
        forcedChallenges = null;
        tick = 0;
        for (String rule : List.of("send_command_feedback false", "log_admin_commands false", "show_advancement_messages false",
                "advance_weather false", "locator_bar false")) {
            cmd("gamerule " + rule);
        }
        cmd("weather clear");
        hud(false);
        settle(600);
        FilmClock.gate(true);
        clockBase = System.currentTimeMillis() - tick * 50L;
        FilmClock.setMillis(clockBase + tick * 50L);
    }

    /** Lets the game run until the client has drawn every chunk in sight (or {@code maxTicks} pass). */
    public void settle(int maxTicks) {
        long t0 = System.currentTimeMillis();
        for (int i = 1; i <= maxTicks; i++) {
            step();
            if (i >= 40 && i % 10 == 0 && ctx.computeOnClient(mc -> mc.levelRenderer.hasRenderedAllSections())) {
                LOG.info("[Film] world drawn after {} ticks ({} s)", i, (System.currentTimeMillis() - t0) / 1000);
                return;
            }
        }
        LOG.info("[Film] world still drawing after {} ticks - going on", maxTicks);
    }

    public void closeWorld() {
        FilmClock.gate(false);
        FilmClock.setMillis(-1);
        cam.reset();
        sp.close();
        sp = null;
    }

    // ------------------------------------------------------------------ doing things

    /** A command as the server (permission 4). Use {@code execute as @p run ...} for player commands. */
    public void cmd(String command) {
        sp.getServer().runCommand(command);
    }

    public void server(FailableConsumer<MinecraftServer, RuntimeException> action) {
        sp.getServer().runOnServer(action);
    }

    public <T> T fromServer(FailableFunction<MinecraftServer, T, RuntimeException> f) {
        return sp.getServer().computeOnServer(f);
    }

    public void client(FailableConsumer<Minecraft, RuntimeException> action) {
        ctx.runOnClient(action);
    }

    public ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    /** Shows or hides the whole HUD (F1). */
    public void hud(boolean shown) {
        client(mc -> {
            if (mc.gui.hud.isHidden() == shown) mc.gui.hud.toggle();
        });
    }

    /** One game tick: the client's, and (gated) exactly one of the server's. */
    public void step() {
        FilmClock.allow();
        ctx.waitTick();
        tick++;
        if (FilmClock.gated()) FilmClock.setMillis(clockBase + tick * 50L);
    }

    /** Lets the game run without filming. */
    public void run(int ticks) {
        run(ticks, null);
    }

    public void run(int ticks, Every every) {
        for (int t = 1; t <= ticks; t++) {
            step();
            if (every != null) every.tick(t);
        }
    }

    /**
     * Films {@code seconds} of game time at 30 frames a second into {@code out/<name>/}. Before the
     * frames of each new tick, {@code every} is called once with the shot's tick count (1, 2, ...):
     * the place to move the camera and to act. A frame between two ticks shows the world part of
     * the way from the one to the other, as the game itself draws it.
     */
    public void shoot(String name, double seconds, Every every) {
        shoot(name, seconds, FPS, every);
    }

    /**
     * As {@link #shoot(String, double, Every)}, but {@code fps} frames a second of game time: fewer
     * for a time-lapse (10 fps played at 30 is three times as fast), more for slow motion.
     */
    public void shoot(String name, double seconds, int fps, Every every) {
        Path dir = out.resolve(name);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int frames = (int) Math.round(seconds * fps);
        int start = tick;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < frames; i++) {
            double t = i * 20.0 / fps;
            int whole = (int) Math.floor(t);
            if (i == 0 && every != null) every.tick(0);
            while (tick - start < whole) {
                step();
                if (every != null) every.tick(tick - start);
            }
            FilmClock.setMillis(clockBase + Math.round((tick + t - whole) * 50));
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format("f%05d", i)).withDeltaTicks((float) (t - whole))
                    .disableCounterPrefix().withDestinationDir(dir));
            if (i % 90 == 0) LOG.info("[Film] {} frame {}: {}", name, i, memory());
        }
        LOG.info("[Film] shot {}: {} frames in {} s", name, frames, (System.currentTimeMillis() - t0) / 1000);
    }

    /** One still frame (for checking a set before filming it). */
    public void still(String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out.resolve("stills")));
    }

    /** Heap and resident memory, for spotting a leak while filming. */
    public static String memory() {
        Runtime r = Runtime.getRuntime();
        String rss = "?";
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS:")) rss = line.substring(6).trim();
            }
        } catch (IOException ignored) {
        }
        return String.format("heap %d/%d MB, rss %s", (r.totalMemory() - r.freeMemory()) >> 20, r.maxMemory() >> 20, rss);
    }

    // ------------------------------------------------------------------ easing

    /** 0..1 over {@code from..to}, eased in and out. */
    public static double ease(double t, double from, double to) {
        double x = Math.max(0, Math.min(1, (t - from) / (to - from)));
        return x * x * (3 - 2 * x);
    }

    public static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }
}
