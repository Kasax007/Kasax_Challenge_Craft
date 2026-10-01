package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/**
 * A bastion's gold: its chests and the blocks of gold in it. Taking either makes the piglins that
 * see it cross, gold armour or not, so only where few are about: nothing with a brute or a hoglin
 * close by, nor with a crowd of piglins round it. The ones that turn on it get fought (the
 * reflexes); low on health it leaves.
 */
public final class BastionLootTask implements BotTask {
    private static final int MAX_TARGETS = 8;
    private final VisitStructureTask visit;
    private final Set<BlockPos> tried = new HashSet<>();
    private boolean arrived, walking;
    private BotTask loot;
    private BlockPos block;
    private int done, ticks, blockTicks;

    public BastionLootTask(ServerLevel level) {
        this.visit = new VisitStructureTask(level, "bastion_remnant");
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 9600) return done > 0 ? Result.DONE : Result.FAILED;
        if (!arrived) {
            Result r = visit.tick(bot);
            if (r == Result.RUNNING) return r;
            if (r == Result.FAILED) return Result.FAILED;
            arrived = true;
            bot.senses().refresh();
            bot.senses().tick(body);
        }
        // Hurt: out of here (the gold so far is enough reason to have come).
        if (body.getHealth() < 9) {
            bot.say("too hot in the bastion: leaving with what I have");
            return done > 0 ? Result.DONE : Result.FAILED;
        }
        if (loot != null) {
            Result r = loot.tick(bot);
            if (r == Result.RUNNING) return r;
            if (r == Result.DONE) done++;
            loot = null;
        }
        if (block != null) return mineGold(bot, level);
        if (done >= MAX_TARGETS) return Result.DONE;
        // The next chest in the bastion, then the next gold block in sight; the nearest safe one.
        for (BlockPos c : bot.senses().lootables()) {
            if (tried.contains(c) || c.distSqr(body.blockPosition()) > 40 * 40) continue;
            Identifier in = bot.senses().structureAt(c);
            if (in == null || !in.getPath().startsWith("bastion")) continue;
            tried.add(c);
            if (!safe(level, body, c)) continue;
            loot = new LootTask(c);
            return Result.RUNNING;
        }
        boolean pick = bot.tools().canHarvest(Blocks.GOLD_BLOCK.defaultBlockState());
        if (pick) {
            for (int i = 0; i < 6; i++) {
                BlockPos g = BotWorld.nearest(level, body.blockPosition(), 32, 16, s -> s.is(Blocks.GOLD_BLOCK), true, tried);
                if (g == null) break;
                tried.add(g);
                if (!safe(level, body, g)) continue;
                block = g;
                blockTicks = 0;
                walking = false;
                return Result.RUNNING;
            }
        }
        return done > 0 ? Result.DONE : Result.FAILED;
    }

    /** Few enough about to take it: no brute, no hoglin close, not three piglins watching. */
    static boolean safe(ServerLevel level, BotPlayer body, BlockPos at) {
        AABB near = new AABB(at).inflate(12);
        if (!level.getEntitiesOfClass(PiglinBrute.class, near, LivingEntity::isAlive).isEmpty()) return false;
        if (!level.getEntitiesOfClass(Hoglin.class, new AABB(at).inflate(8), LivingEntity::isAlive).isEmpty()) return false;
        return level.getEntitiesOfClass(Piglin.class, near, p -> p.isAlive() && !p.isBaby()).size() < 3;
    }

    private Result mineGold(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        if (!level.getBlockState(block).is(Blocks.GOLD_BLOCK) || ++blockTicks > 1200) {
            if (!level.getBlockState(block).is(Blocks.GOLD_BLOCK)) {
                done++;
                // Picked up where it fell.
                bot.interject(new CollectDropsTask(block, 5, 100));
            }
            block = null;
            bot.actions().reset();
            return Result.RUNNING;
        }
        if (!bot.actions().inReach(block)) {
            if (!walking) {
                bot.navigator().goNear(block, 3.5);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(block)) block = null;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        bot.actions().breakTick(block);
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "loot a bastion (" + done + " taken)";
    }

    @Override
    public String status() {
        return describe() + " [" + (block != null ? "gold block " + block.toShortString() : loot != null ? loot.describe() : "-") + "]";
    }
}
