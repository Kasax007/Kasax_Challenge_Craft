package net.kasax.challengecraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Keeps AWT usable so the run summary can put its card on the system clipboard.
 *
 * <p>Minecraft's own {@code client.main.Main} sets {@code java.awt.headless=true} in a static
 * initialiser. AWT then refuses to hand out a clipboard, and Java offers no other way to put an
 * <em>image</em> there — GLFW, which Minecraft uses for its own copy/paste, does text only.
 *
 * <p>Undoing it later is impossible, which is the whole reason this class exists and runs when it
 * does. {@code GraphicsEnvironment} answers the headless question exactly once and caches it
 * forever; every later {@code setProperty} is inert. Measured on this JDK:
 *
 * <pre>
 *   setProperty(false); isHeadless() -> false;  setProperty(true);  isHeadless() -> false
 *   setProperty(true);  isHeadless() -> true;   setProperty(false); isHeadless() -> true
 * </pre>
 *
 * <p>So the answer has to be settled before Minecraft's class is even loaded, and a Fabric
 * {@code preLaunch} entrypoint is the only hook early enough. Reaching into the cached field by
 * reflection was the previous attempt; on JDK 25 {@code java.desktop} does not open {@code java.awt}
 * to unnamed modules and it throws {@code InaccessibleObjectException}.
 *
 * <p>Only the flag is decided here. No AWT class is touched beyond the one query, and the toolkit
 * is still created lazily on first real use, so nothing is paid for by players who never save a
 * card. macOS is left alone on purpose: a non-headless AWT there earns the process a second Dock
 * icon and can pull focus away from the game, so that platform uses {@code osascript} instead
 * (see {@code RunSummaryImage}).
 */
public final class AwtClipboardFix implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Clipboard");

    @Override
    public void onPreLaunch() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return;
        }
        try {
            System.setProperty("java.awt.headless", "false");
            // Deliberate: this call is what freezes the answer. Without it the property is still
            // just a property and Minecraft overwrites it a moment later.
            boolean headless = java.awt.GraphicsEnvironment.isHeadless();
            LOGGER.info("AWT-Zwischenablage vorbereitet (headless={})", headless);
        } catch (Throwable t) {
            // A JVM without java.desktop at all. The platform fallback still covers the feature.
            LOGGER.info("AWT nicht verfügbar ({}) — Zwischenablage läuft über das Betriebssystem",
                    t.toString());
        }
    }
}
