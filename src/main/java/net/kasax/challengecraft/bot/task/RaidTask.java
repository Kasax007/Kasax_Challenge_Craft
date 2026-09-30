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

    public RaidTask(ServerLevel level, String kind) {
        this.kind = kind;
        this.visit = new VisitStructureTask(level, kind);
    }

    @Override
    public Result tick(Bot bot) {
        if (!arrived) {
            Result r = visit.tick(bot);
            if (r == Result.RUNNING) return r;
            if (r == Result.FAILED) return Result.FAILED;
            arrived = true;
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
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "raid the " + kind + " for loot";
    }
}
