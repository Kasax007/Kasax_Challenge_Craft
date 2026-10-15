package net.kasax.challengecraft.bot.learn;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Bob's learning, wired into the server: the journal opened and closed with it, the arena ticked,
 * and - for the trainer on the laptop ({@code scripts/train/bobtrain.py}) - a status file every
 * five seconds ({@code bob-status.json}: the pace, the bots, the arena's count) and a watchdog: a
 * server the trainer started ({@code BOB_PARENT_PID}) stops itself when the trainer is gone, so
 * no server is left running in the background after a crash or a closed window.
 */
public final class Learn {
    private static final long STATUS_MS = 5000;
    private static long statusAt, statusTick;
    private static Thread watchdog;
    private static volatile boolean stopping;

    private Learn() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            stopping = false;
            Journal.open();
            startWatchdog(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stopping = true;
            Arena.stop(server);
            Journal.close();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Arena.tick(server);
            long now = System.currentTimeMillis();
            if (now - statusAt >= STATUS_MS) status(server, now);
        });
    }

    // ---- the records ------------------------------------------------------------------------

    /** A death: what killed him, where, doing what - the guardian's fight record ends with it too. */
    public static void death(Bot bot, BotPlayer body) {
        if (!Journal.on()) return;
        var o = new JsonObject();
        o.addProperty("bot", bot.name);
        var src = body.getLastDamageSource();
        o.addProperty("cause", src == null ? "-" : src.getMsgId());
        o.addProperty("killer", src != null && src.getEntity() != null ? src.getEntity().getType().toShortString() : "-");
        o.addProperty("dim", body.level().dimension().identifier().getPath());
        o.addProperty("y", body.getBlockY());
        o.addProperty("night", body.level().isDarkOutside() ? 1 : 0);
        o.addProperty("armour", body.getArmorValue());
        o.addProperty("food", body.getFoodData().getFoodLevel());
        o.addProperty("food_packed", net.kasax.challengecraft.bot.task.EatTask.foodPoints(body));
        o.addProperty("underground", net.kasax.challengecraft.bot.task.SurfaceTask.underground(body) ? 1 : 0);
        BotTask root = bot.root();
        o.addProperty("doing", root == null ? "-" : root.describe());
        BotTask inner = BotTask.innermost(bot.current());
        o.addProperty("inner", inner == null ? "-" : inner.describe());
        o.addProperty("params", bot.params.toString());
        if (bot.guardian.context != null) o.add("ctx", bot.guardian.context.deepCopy());
        Journal.write("death", o);
    }

    // ---- for the trainer: status and watchdog ---------------------------------------------------

    private static void status(MinecraftServer server, long now) {
        int tick = server.getTickCount();
        double seconds = statusAt == 0 ? 0 : (now - statusAt) / 1000.0;
        double tps = seconds > 0 ? (tick - statusTick) / seconds : 0;
        statusAt = now;
        statusTick = tick;
        String dir = System.getenv("BOB_STATUS_DIR");
        if (dir == null && System.getenv("BOB_PARENT_PID") == null) return;
        var o = new JsonObject();
        o.addProperty("wall", now);
        o.addProperty("tick", tick);
        o.addProperty("tps", Math.round(tps * 10) / 10.0);
        o.addProperty("mspt", Math.round(server.getAverageTickTimeNanos() / 1e5) / 10.0);
        o.addProperty("sprinting", server.tickRateManager().isSprinting());
        o.addProperty("bots", BotManager.all().size());
        o.addProperty("players", server.getPlayerCount());
        o.addProperty("models", ModelStore.describe());
        o.addProperty("memory_mb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20);
        o.addProperty("max_memory_mb", Runtime.getRuntime().maxMemory() >> 20);
        o.add("arena", Arena.stats());
        try {
            Path target = Path.of(dir == null ? "." : dir).toAbsolutePath().resolve("bob-status.json");
            Path tmp = target.resolveSibling("bob-status.json.tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // (Only for the trainer's eyes: a missed one changes nothing.)
        }
    }

    /**
     * The trainer started this server ({@code BOB_PARENT_PID}): once the trainer's process is gone
     * (crashed, killed, the window closed), the server stops itself - and if it hangs on the way,
     * the process ends half a minute later regardless.
     */
    private static synchronized void startWatchdog(MinecraftServer server) {
        String pid = System.getenv("BOB_PARENT_PID");
        if (pid == null || pid.isBlank() || watchdog != null) return;
        long parent;
        try {
            parent = Long.parseLong(pid.trim());
        } catch (NumberFormatException e) {
            return;
        }
        watchdog = new Thread(() -> {
            while (!stopping) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
                boolean alive = ProcessHandle.of(parent).map(ProcessHandle::isAlive).orElse(false);
                if (alive) continue;
                BotManager.LOG.warn("[Learn] the trainer (pid {}) is gone: stopping this server", parent);
                server.execute(() -> server.halt(false));
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    return;
                }
                Runtime.getRuntime().halt(3);
            }
        }, "Bob-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        BotManager.LOG.info("[Learn] watching the trainer, pid {}", parent);
    }
}
