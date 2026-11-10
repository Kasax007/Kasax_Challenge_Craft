package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * To a structure and through its loot chests (a few at most): the shipwreck's supply and treasure
 * chests for iron, the village's for bread, iron and tools.
 */
public final class RaidTask implements BotTask {
    private static final int MAX_CHESTS = 3;
    private final String kind;
    private final VisitStructureTask visit;
    private final Set<BlockPos> tried = new HashSet<>();
    private BotTask loot;
    private boolean arrived;
    private int looted;
    /** Blocks the board wants that a village may have about (its bell, hay, a bed). */
    private final Set<net.minecraft.world.level.block.Block> wanted;
    private VillageChoresTask chores;

    public RaidTask(ServerLevel level, String kind) {
        this(level, kind, Set.of());
    }

    public RaidTask(ServerLevel level, String kind, Set<net.minecraft.world.level.block.Block> wanted) {
        this.kind = kind;
        this.visit = new VisitStructureTask(level, kind);
        this.ids = VisitStructureTask.resolve(level, kind);
        this.wanted = Set.copyOf(wanted);
    }

    /** The structure ids of the kind. */
    private final Set<Identifier> ids;
    /** Blocks from the spot it knows of the structure at which it is there. */
    private static final double ARRIVED = 8;

    @Override
    public Result tick(Bot bot) {
        if (!arrived) {
            // (There once close to the spot it knows: its chests are what it came for, not a
            // part to stand in - a buried treasure's one part is the chest itself, and the visit
            // never counted it as reached; the route to its iron failed every time.)
            Result r = VisitStructureTask.near(bot, ids, ARRIVED) ? Result.DONE : visit.tick(bot);
            if (r == Result.RUNNING) return r;
            if (r == Result.FAILED) return Result.FAILED;
            arrived = true;
            bot.navigator().stop();
            bot.senses().refresh();
            bot.senses().tick(bot.body());
        }
        if (loot != null) {
            Result r = loot.tick(bot);
            if (r == Result.RUNNING) return r;
            if (r == Result.DONE) looted++;
            loot = null;
        }
        if (looted >= MAX_CHESTS) return Result.DONE;
        for (BlockPos c : bot.senses().lootables()) {
            if (tried.contains(c) || c.distSqr(bot.body().blockPosition()) > 48 * 48) continue;
            Identifier in = bot.senses().structureAt(c);
            if (in == null || !in.getPath().startsWith(kind)) continue;
            tried.add(c);
            loot = new LootTask(c);
            return Result.RUNNING;
        }
        if (!kind.equals("village")) return Result.DONE;
        // A village is more than its chests: see VillageChoresTask.
        if (chores == null) chores = new VillageChoresTask(wanted);
        return chores.tick(bot);
    }

    @Override
    public String describe() {
        return "raid the " + kind + " for loot";
    }
}
