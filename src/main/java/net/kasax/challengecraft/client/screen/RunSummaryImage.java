package net.kasax.challengecraft.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Saves the summary card as a PNG in the screenshots folder.
 *
 * <p>The card is not drawn into an image pixel by pixel — it is <em>screenshotted</em>. Vanilla
 * already has a working framebuffer readback ({@link Screenshot#takeScreenshot}), and going through
 * it means the saved file contains exactly what the player sees, with real font rendering, without
 * a second drawing path that could drift out of sync with the screen.
 *
 * <p>The framebuffer covers the whole window, so the result is cropped down to the card. GUI
 * coordinates are not pixels: the conversion factor is derived from the framebuffer width over the
 * GUI width rather than read from the GUI-scale option, which keeps it correct on high-DPI displays
 * where the two disagree.
 */
@Environment(EnvType.CLIENT)
public final class RunSummaryImage {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChallengeCraft-Summary");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private RunSummaryImage() {
    }

    /**
     * Puts a saved card on the system clipboard as an image.
     *
     * <p>This is as close to "attach it to the tweet for me" as anything can get without becoming a
     * different kind of program. Neither X nor Reddit accepts an upload through a URL — a share link
     * can carry text and nothing else — so posting an image would need OAuth as the player, API
     * keys, and a server in between. Both sites do accept a pasted image, so the card goes on the
     * clipboard and the post is one Ctrl+V away.
     *
     * <p>Two routes, in this order. AWT is instant and in-process, but only works because
     * {@link net.kasax.challengecraft.client.AwtClipboardFix} settled the headless question before
     * Minecraft could — read that class before changing anything here. The platform's own clipboard
     * command is the fallback: slower (it spawns a process) but immune to the JVM's AWT state, and
     * on Windows it leaves the image on the clipboard even after the game is closed, which the AWT
     * route does not.
     *
     * <p>Runs off the render thread — a process spawn takes long enough to be a visible stutter —
     * and reports back on the main thread. Never fatal: the PNG is on disk either way.
     */
    public static void copyToClipboard(Path file, Consumer<Boolean> onDone) {
        Minecraft mc = Minecraft.getInstance();
        Thread worker = new Thread(() -> {
            boolean ok = copyViaAwt(file);
            if (!ok) {
                ok = copyViaPlatform(file);
            }
            boolean result = ok;
            if (!result) {
                LOGGER.warn("[Summary] Bild konnte nicht in die Zwischenablage gelegt werden");
            }
            mc.execute(() -> onDone.accept(result));
        }, "ChallengeCraft-Clipboard");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean copyViaAwt(Path file) {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                LOGGER.info("[Summary] AWT ist headless — nutze den Weg über das Betriebssystem");
                return false;
            }
            java.awt.Image image = javax.imageio.ImageIO.read(file.toFile());
            if (image == null) {
                LOGGER.warn("[Summary] PNG nicht lesbar: {}", file);
                return false;
            }
            java.awt.datatransfer.Transferable payload = new java.awt.datatransfer.Transferable() {
                @Override
                public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                    return new java.awt.datatransfer.DataFlavor[]{
                            java.awt.datatransfer.DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                    return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                    return image;
                }
            };
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(payload, null);
            LOGGER.info("[Summary] Karte in die Zwischenablage kopiert (AWT)");
            return true;
        } catch (Throwable t) {
            LOGGER.info("[Summary] AWT-Zwischenablage nicht verfügbar: {}", t.toString());
            return false;
        }
    }

    /** The OS's own clipboard tool. Verified by hand on Windows against a real card PNG. */
    private static boolean copyViaPlatform(Path file) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String path = file.toAbsolutePath().toString();
        java.util.List<java.util.List<String>> attempts = new java.util.ArrayList<>();
        if (os.contains("win")) {
            // -STA is not optional: the WinForms clipboard refuses to run on an MTA thread.
            attempts.add(java.util.List.of("powershell", "-NoProfile", "-STA", "-WindowStyle",
                    "Hidden", "-Command",
                    "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
                            + "$i=[System.Drawing.Image]::FromFile('" + path.replace("'", "''") + "'); "
                            + "[System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()"));
        } else if (os.contains("mac") || os.contains("darwin")) {
            attempts.add(java.util.List.of("osascript", "-e",
                    "set the clipboard to (read (POSIX file \"" + path.replace("\"", "\\\"")
                            + "\") as «class PNGf»)"));
        } else {
            attempts.add(java.util.List.of("wl-copy", "--type", "image/png", "--", path));
            attempts.add(java.util.List.of("xclip", "-selection", "clipboard", "-t", "image/png",
                    "-i", path));
        }
        for (java.util.List<String> command : attempts) {
            try {
                Process p = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    LOGGER.info("[Summary] {} antwortet nicht", command.get(0));
                    continue;
                }
                if (p.exitValue() == 0) {
                    LOGGER.info("[Summary] Karte in die Zwischenablage kopiert ({})", command.get(0));
                    return true;
                }
                LOGGER.info("[Summary] {} endete mit Code {}", command.get(0), p.exitValue());
            } catch (java.io.IOException e) {
                // Not installed. Expected on a bare Linux box; try the next one.
                LOGGER.info("[Summary] {} nicht vorhanden", command.get(0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve("challengecraft");
    }

    /**
     * Grabs the card and writes it.
     *
     * <p>Asynchronous by nature: the readback happens when the GPU is ready, so the result arrives
     * through {@code onDone} rather than being returned. {@code onDone} receives null on failure —
     * the caller shows that as a message instead of pretending a file exists.
     */
    public static void capture(int[] cardBounds, int guiWidth, Consumer<Path> onDone) {
        Minecraft mc = Minecraft.getInstance();
        var target = mc.gameRenderer.mainRenderTarget();
        if (target == null || guiWidth <= 0) {
            onDone.accept(null);
            return;
        }
        // Pixels per GUI unit, measured rather than assumed.
        double factor = target.width / (double) guiWidth;
        int x = (int) Math.round(cardBounds[0] * factor);
        int y = (int) Math.round(cardBounds[1] * factor);
        int w = (int) Math.round(cardBounds[2] * factor);
        int h = (int) Math.round(cardBounds[3] * factor);

        Screenshot.takeScreenshot(target, full -> {
            Path path = null;
            try (NativeImage source = full) {
                int cx = Math.max(0, Math.min(x, source.getWidth() - 1));
                int cy = Math.max(0, Math.min(y, source.getHeight() - 1));
                int cw = Math.max(1, Math.min(w, source.getWidth() - cx));
                int ch = Math.max(1, Math.min(h, source.getHeight() - cy));

                try (NativeImage card = new NativeImage(cw, ch, false)) {
                    source.copyRect(card, cx, cy, 0, 0, cw, ch, false, false);
                    Files.createDirectories(folder());
                    path = folder().resolve("challengecraft_"
                            + LocalDateTime.now().format(STAMP) + ".png");
                    card.writeToFile(path);
                    LOGGER.info("[Summary] Karte gespeichert: {} ({}x{})", path, cw, ch);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[Summary] Karte konnte nicht gespeichert werden", e);
                path = null;
            }
            Path result = path;
            mc.execute(() -> onDone.accept(result));
        });
    }
}
