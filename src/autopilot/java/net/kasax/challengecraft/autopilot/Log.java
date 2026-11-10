package net.kasax.challengecraft.autopilot;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * What the autopilot says: on the console (the panel shows it) and in {@code autopilot.log}, a line
 * each with the time. The log starts over in a second file once it is past five MB.
 */
final class Log {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long ROTATE = 5L << 20;
    private final PrintStream console;
    private Path file;

    Log(PrintStream console) {
        this.console = console;
    }

    /** From now on into this file as well. */
    synchronized void to(Path file) {
        this.file = file;
    }

    synchronized void info(String text) {
        line("", text);
    }

    synchronized void warn(String text) {
        line("WARNUNG: ", text);
    }

    /** Text for the console only (a block like the status, without the time). */
    synchronized void plain(String text) {
        console.println(text);
        console.flush();
    }

    private void line(String prefix, String text) {
        String line = "[" + LocalTime.now().format(TIME) + "] [Bob-Autopilot] " + prefix + text;
        console.println(line);
        console.flush();
        if (file == null) return;
        try {
            if (Files.exists(file) && Files.size(file) > ROTATE) {
                Files.move(file, file.resolveSibling(file.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // (The console has it.)
        }
    }
}
