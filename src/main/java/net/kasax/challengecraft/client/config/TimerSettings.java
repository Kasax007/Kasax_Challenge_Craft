package net.kasax.challengecraft.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.kasax.challengecraft.ChallengeCraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything the player can change about the run timer, persisted next to the game's other configs.
 *
 * <p>Client-only and per-installation on purpose: this is a preference about the player's own
 * screen, not world state, so it does not belong in {@code ChallengeSavedData} and must not travel
 * over the network. It follows {@code LevelJourneyLayoutStore}'s pattern — GSON into
 * {@link FabricLoader#getConfigDir()} — rather than inventing a second config mechanism.
 *
 * <p>The position is stored as a <b>fraction of the screen</b>, never as pixels. A player who moves
 * the timer clear of the held-item name at one window size would otherwise find it back on top of
 * it after resizing or changing the GUI scale, which is the complaint this whole screen exists to
 * answer.
 *
 * <p>Every field is read leniently: a missing key keeps the default and a broken file is reported
 * and ignored. A settings file must never be able to stop the timer from drawing.
 */
@Environment(EnvType.CLIENT)
public final class TimerSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("challengecraft_timer.json");

    /** How the text sits relative to the anchor point. Matters once the clock rolls over an hour. */
    public enum Align { LEFT, CENTER, RIGHT }

    /** How the digits are separated from whatever is behind them. */
    public enum Outline { NONE, SHADOW, FULL }

    public enum Format { AUTO, ALWAYS_HOURS, TENTHS }

    public boolean visible = true;
    /**
     * Whether the player has placed the timer themselves.
     *
     * <p>While this is false, {@code x}/{@code y} are ignored and the position is worked out from
     * the window every frame — just above the held-item name, wherever that lands. That is the only
     * way to be right at every GUI scale: the item name sits at a fixed pixel offset from the
     * bottom while a stored fraction does not, so any single default fraction is either too high at
     * one scale or straight back on top of the item name at another. Both were tried.
     *
     * <p>Set by the first drag or slider move, after which the stored fraction takes over and the
     * position is the player's. Reset puts it back to false.
     */
    public boolean customPosition = false;
    /** Fraction of the screen, 0..1. Only consulted once {@link #customPosition} is true. */
    public float x = 0.5f;
    public float y = 0.80f;
    public Align align = Align.CENTER;
    public float scale = 1.5f;
    /** RGB, no alpha. The old hard-coded gold. */
    public int color = 0xF2C23B;
    public boolean pulse = true;
    public boolean shine = true;
    public Outline outline = Outline.SHADOW;
    public boolean background = false;
    public float backgroundOpacity = 0.55f;
    public Format format = Format.AUTO;

    // ---- challenge cards (HudStack) ------------------------------------------------------
    // Stored in the same file as the timer: one "HUD settings" screen, one file.

    public boolean cardsVisible = true;
    /** Like {@link #customPosition}: false = centred at the top, as the cards always were. */
    public boolean cardsCustom = false;
    /** Top-centre of the card stack as a fraction of the screen. Only read when cardsCustom. */
    public float cardsX = 0.5f;
    public float cardsY = 0.02f;
    public float cardsScale = 1.0f;

    /**
     * Hidden with the "toggle HUD" key. Deliberately not saved: a HUD that is still gone after a
     * restart reads as a bug, not as a setting somebody chose weeks ago.
     */
    public static boolean hiddenByKey = false;

    private static TimerSettings instance;

    public static TimerSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Drops every stored value back to the defaults. Does not write — the caller decides that. */
    public void resetToDefaults() {
        TimerSettings d = new TimerSettings();
        visible = d.visible;
        customPosition = d.customPosition;
        x = d.x;
        y = d.y;
        align = d.align;
        scale = d.scale;
        color = d.color;
        pulse = d.pulse;
        shine = d.shine;
        outline = d.outline;
        background = d.background;
        backgroundOpacity = d.backgroundOpacity;
        format = d.format;
        cardsVisible = d.cardsVisible;
        cardsCustom = d.cardsCustom;
        cardsX = d.cardsX;
        cardsY = d.cardsY;
        cardsScale = d.cardsScale;
    }

    private static TimerSettings load() {
        TimerSettings s = new TimerSettings();
        if (!Files.exists(FILE)) {
            return s;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (root == null) {
                return s;
            }
            s.visible = bool(root, "visible", s.visible);
            s.customPosition = bool(root, "customPosition", s.customPosition);
            s.x = clamp(flt(root, "x", s.x), 0f, 1f);
            s.y = clamp(flt(root, "y", s.y), 0f, 1f);
            s.align = enumOf(Align.class, str(root, "align", s.align.name()), s.align);
            s.scale = clamp(flt(root, "scale", s.scale), 0.5f, 4.0f);
            s.color = root.has("color") ? root.get("color").getAsInt() & 0xFFFFFF : s.color;
            s.pulse = bool(root, "pulse", s.pulse);
            s.shine = bool(root, "shine", s.shine);
            s.outline = enumOf(Outline.class, str(root, "outline", s.outline.name()), s.outline);
            s.background = bool(root, "background", s.background);
            s.backgroundOpacity = clamp(flt(root, "backgroundOpacity", s.backgroundOpacity), 0f, 1f);
            s.format = enumOf(Format.class, str(root, "format", s.format.name()), s.format);
            s.cardsVisible = bool(root, "cardsVisible", s.cardsVisible);
            s.cardsCustom = bool(root, "cardsCustom", s.cardsCustom);
            s.cardsX = clamp(flt(root, "cardsX", s.cardsX), 0f, 1f);
            s.cardsY = clamp(flt(root, "cardsY", s.cardsY), 0f, 1f);
            s.cardsScale = clamp(flt(root, "cardsScale", s.cardsScale), 0.5f, 2.0f);
        } catch (Exception e) {
            ChallengeCraft.LOGGER.warn("Timer-Einstellungen nicht lesbar ({}), nutze Standardwerte",
                    FILE, e);
        }
        return s;
    }

    private boolean dirty;

    /**
     * Queues a write instead of performing one.
     *
     * <p>A slider calls its apply hook on every frame of a drag, and writing the file each time made
     * dragging visibly stutter — that is what "cannot smooth slide" was. Continuous controls mark
     * dirty and {@link #flush()} runs when the drag ends or the screen closes; one-shot controls
     * still call {@link #save()} directly.
     */
    public void markDirty() {
        dirty = true;
    }

    public void flush() {
        if (dirty) {
            dirty = false;
            save();
        }
    }

    public void save() {
        dirty = false;
        JsonObject root = new JsonObject();
        root.addProperty("visible", visible);
        root.addProperty("customPosition", customPosition);
        root.addProperty("x", x);
        root.addProperty("y", y);
        root.addProperty("align", align.name());
        root.addProperty("scale", scale);
        root.addProperty("color", color & 0xFFFFFF);
        root.addProperty("pulse", pulse);
        root.addProperty("shine", shine);
        root.addProperty("outline", outline.name());
        root.addProperty("background", background);
        root.addProperty("backgroundOpacity", backgroundOpacity);
        root.addProperty("format", format.name());
        root.addProperty("cardsVisible", cardsVisible);
        root.addProperty("cardsCustom", cardsCustom);
        root.addProperty("cardsX", cardsX);
        root.addProperty("cardsY", cardsY);
        root.addProperty("cardsScale", cardsScale);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ChallengeCraft.LOGGER.warn("Timer-Einstellungen nicht schreibbar: {}", FILE, e);
        }
    }

    private static boolean bool(JsonObject o, String k, boolean d) {
        return o.has(k) ? o.get(k).getAsBoolean() : d;
    }

    private static float flt(JsonObject o, String k, float d) {
        return o.has(k) ? o.get(k).getAsFloat() : d;
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
