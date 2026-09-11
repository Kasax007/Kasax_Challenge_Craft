package net.kasax.challengecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.kasax.challengecraft.client.screen.TimerSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Makes the timer settings reachable on video-settings screens this mod does not own.
 *
 * <p>{@code VideoSettingsScreenMixin} handles vanilla's screen and puts the entry inline in the
 * options list, which is the nicer result. But <b>Sodium replaces that screen outright</b> — in
 * 0.9.1 it swaps in its own {@code net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen} from
 * an {@code OptionsScreenMixin}, so vanilla's class is never constructed and the mixin never runs.
 * Anyone playing the modpack simply had no way in.
 *
 * <p>Sodium exposes no API for adding an entry to its screen, so this goes through Fabric's
 * {@link ScreenEvents}, which works on any {@link Screen} instance regardless of who wrote it. The
 * screen is recognised by class name rather than by {@code instanceof}: Sodium is not a compile
 * dependency here, and matching on the name also survives the package renames Sodium has already
 * been through once.
 *
 * <p>Placement is defensive. Sodium's layout is not ours to know, so the button starts in the
 * bottom-left corner and moves up until it overlaps none of the screen's own buttons. Worst case it
 * ends up somewhere unexpected; it can never end up underneath something.
 */
@Environment(EnvType.CLIENT)
public final class TimerSettingsEntry {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 20;
    private static final int MARGIN = 6;

    private TimerSettingsEntry() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!isForeignVideoScreen(screen)) {
                return;
            }
            Button button = Button.builder(
                            Component.translatable("challengecraft.timer.open"),
                            b -> Minecraft.getInstance().setScreenAndShow(new TimerSettingsScreen(screen)))
                    .bounds(MARGIN, freeY(screen, h), WIDTH, HEIGHT)
                    .build();
            Screens.getWidgets(screen).add(button);
        });
    }

    /**
     * A video-settings screen that is not vanilla's — i.e. one the mixin cannot have reached.
     *
     * <p>Vanilla's own screen is excluded so the entry never appears twice: the mixin already put a
     * row in its list.
     */
    private static boolean isForeignVideoScreen(Screen screen) {
        Class<?> type = screen.getClass();
        if (type == net.minecraft.client.gui.screens.options.VideoSettingsScreen.class) {
            return false;
        }
        String name = type.getName();
        return name.endsWith("VideoSettingsScreen") || name.endsWith("SodiumOptionsGUI");
    }

    /** Lowest free row in the bottom-left corner, so the button cannot land under another widget. */
    private static int freeY(Screen screen, int screenHeight) {
        int y = screenHeight - HEIGHT - MARGIN;
        for (int attempt = 0; attempt < 6; attempt++) {
            if (!collides(screen, y)) {
                return y;
            }
            y -= HEIGHT + 4;
        }
        return y;
    }

    private static boolean collides(Screen screen, int y) {
        for (AbstractWidget other : Screens.getWidgets(screen)) {
            if (!other.visible) {
                continue;
            }
            boolean overlapsX = MARGIN < other.getX() + other.getWidth()
                    && other.getX() < MARGIN + WIDTH;
            boolean overlapsY = y < other.getY() + other.getHeight()
                    && other.getY() < y + HEIGHT;
            if (overlapsX && overlapsY) {
                return true;
            }
        }
        return false;
    }
}
