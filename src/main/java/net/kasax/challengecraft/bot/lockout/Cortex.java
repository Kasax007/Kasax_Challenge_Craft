package net.kasax.challengecraft.bot.lockout;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Bob's "big brain" over the deciders of {@link LockoutBrain} (plan 16, L3 with L4 and L6): what
 * each of them set out to do, kept in mind for the whole game, and a say over what may start.
 *
 * <ul>
 *   <li><b>Memory of failures</b>: every intent (food, the kit, a raid, a tile...) with its starts,
 *   failures, overruns and deaths. Each failure in a row makes the next try wait longer (one,
 *   two, four, eight minutes...), a death doing it longer still - the same thing is not walked
 *   into again and again.</li>
 *   <li><b>Loop breaker</b>: the same intent started three times in ten minutes without once
 *   coming off is a loop, whatever each decider thinks of its own chances: it is set aside for a
 *   good while, and the log says so.</li>
 *   <li><b>Modes</b>: surviving (hurt, starving, bare in the dark), preparing (no stone tools yet)
 *   or playing. While surviving, nothing that seeks out danger (raids, hunts, the Nether trip,
 *   a bastion) is started.</li>
 * </ul>
 */
final class Cortex {
    enum Mode { SURVIVE, PREPARE, PLAY }

    /** What is known of one intent. */
    private static final class Track {
        int starts, done, fails, deaths, inRow;
        long blockedUntil;
        /** Ticks spent on it, all tries. */
        long spent;
        /** When it was last started (for the loop breaker), and how it ended. */
        final ArrayDeque<Long> recent = new ArrayDeque<>();
        long lastDone = -1;
        int loops;
    }

    private static final long LOOP_WINDOW = 12000, BACKOFF = 1200, LOOP_REST = 6000;

    private final Map<String, Track> tracks = new HashMap<>();
    /** The intent running now and since when (null: none). */
    private String active;
    private long activeSince;
    private Mode mode = Mode.PLAY;
    /** For the benchmarks: loops broken, starts refused. */
    int loopsBroken, refused;

    private Track track(String key) {
        return tracks.computeIfAbsent(key, k -> new Track());
    }

    /** Whether this intent may start now (not resting after failures, not set aside as a loop). */
    boolean allowed(String key, long now) {
        Track t = tracks.get(key);
        return t == null || t.blockedUntil <= now;
    }

    /** How long this one still rests (ticks), for the log. */
    long restsFor(String key, long now) {
        Track t = tracks.get(key);
        return t == null ? 0 : Math.max(0, t.blockedUntil - now);
    }

    /** An intent starts: counted, and a loop broken if it is one. Returns false if it may not start. */
    boolean start(Bot bot, String key, long now) {
        Track t = track(key);
        if (t.blockedUntil > now) {
            refused++;
            return false;
        }
        while (!t.recent.isEmpty() && now - t.recent.peekFirst() > LOOP_WINDOW) t.recent.pollFirst();
        // Three starts in ten minutes, none of them come off since the first: a loop.
        if (t.recent.size() >= 3 && t.lastDone < t.recent.peekFirst()) {
            t.loops++;
            loopsBroken++;
            t.blockedUntil = now + (LOOP_REST << Math.min(3, t.loops - 1));
            t.recent.clear();
            bot.say("cortex: '" + key + "' started " + 3 + " times in ten minutes without success - set aside for "
                    + (t.blockedUntil - now) / 1200 + " min");
            BotManager.LOG.info("[Cortex] {}: loop on {} broken ({} loops so far)", bot.name, key, loopsBroken);
            refused++;
            return false;
        }
        t.recent.addLast(now);
        t.starts++;
        active = key;
        activeSince = now;
        return true;
    }

    /** The running intent has ended: done, failed (or ran over), or died at it. */
    void ended(Bot bot, String key, long now, boolean success, boolean died) {
        if (key == null) return;
        Track t = track(key);
        if (key.equals(active)) {
            t.spent += now - activeSince;
            active = null;
        }
        if (success) {
            t.done++;
            t.inRow = 0;
            t.lastDone = now;
            return;
        }
        t.fails++;
        t.inRow++;
        if (died) t.deaths++;
        // One, two, four, eight minutes... and after a death at it twice that.
        long wait = (BACKOFF << Math.min(4, t.inRow - 1)) * (died ? 2 : 1);
        t.blockedUntil = Math.max(t.blockedUntil, now + wait);
        if (t.inRow >= 2 || died) bot.say("cortex: '" + key + "' failed " + t.inRow + "x in a row" + (died ? " (died at it)" : "")
                + " - not before " + wait / 1200 + " min");
    }

    /** The intent running when the body died. */
    void died(Bot bot, long now) {
        if (active != null) ended(bot, active, now, false, true);
    }

    String active() {
        return active;
    }

    /** The mode for now: surviving, preparing or playing. */
    Mode mode(Bot bot) {
        var body = bot.body();
        var level = body.level();
        boolean food = net.kasax.challengecraft.bot.task.EatTask.bestFood(body) >= 0;
        boolean dark = level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.isDarkOutside()
                && !net.kasax.challengecraft.bot.task.SurfaceTask.underground(body);
        boolean bare = body.getArmorValue() < 5;
        Mode m;
        if (body.getHealth() < 8 || body.getFoodData().getFoodLevel() <= 6 && !food || dark && bare && body.getHealth() < 14) m = Mode.SURVIVE;
        else if (net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(net.minecraft.world.item.Items.STONE_PICKAXE,
                net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.NETHERITE_PICKAXE)) == 0) m = Mode.PREPARE;
        else m = Mode.PLAY;
        if (m != mode) {
            BotManager.LOG.info("[Cortex] {}: mode {} -> {} (hp {}, food {}, armour {})", bot.name, mode, m, Math.round(body.getHealth()),
                    body.getFoodData().getFoodLevel(), body.getArmorValue());
            mode = m;
        }
        return m;
    }

    /** One line for the benchmark report: the intents that cost most, with how they went. */
    String summary() {
        StringBuilder sb = new StringBuilder("loops broken " + loopsBroken + ", refused " + refused + ";");
        tracks.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue().spent, a.getValue().spent)).limit(12).forEach(e -> {
            Track t = e.getValue();
            sb.append(' ').append(e.getKey()).append(' ').append(t.starts).append("x/").append(t.done).append(" ok/")
                    .append(t.fails).append(" fail").append(t.deaths > 0 ? "/" + t.deaths + " died" : "").append(' ')
                    .append(t.spent / 20).append("s;");
        });
        return sb.toString();
    }
}
