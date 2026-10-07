package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * The player's way out of a fight going badly (a skeleton shooting from afar, zombies all round):
 * three blocks straight down, a block over the head, eat, wait until the hearts are back, then up
 * again. Nothing can reach or shoot into a 1 x 1 hole with a lid.
 */
public final class HideTask implements BotTask {
    private static final int MAX_TICKS = 900;
    private int ticks, dug, lidTries;
    private BlockPos top;
    private boolean closed;

    /** Whether hiding right here works: solid ground two deep, nothing liquid, something to dig with. */
    public static boolean possible(Bot bot) {
        BotPlayer body = bot.body();
        if (!body.onGround() || body.isInWater() || !bot.actions().hasThrowaway()) return false;
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = body.blockPosition();
        for (int i = 1; i <= 4; i++) {
            BlockPos p = feet.below(i);
            var s = level.getBlockState(p);
            if (s.getCollisionShape(level, p).isEmpty() || s.getDestroySpeed(level, p) < 0) return false;
            if (i <= 3 && bot.tools().breakTicks(s) > 40) return false;
            for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return false;
            if (s.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) return false;
        }
        return true;
    }

    /**
     * The night's shelter for a bot come back to life with nothing (no weapon, no blocks) and the
     * monsters about the spawn: dug in with bare hands (the dug dirt is the lid), and out again in
     * the morning - instead of dying to them again every half minute (seed 77: twelve times).
     */
    private boolean untilDay;

    public static HideTask shelter() {
        HideTask t = new HideTask();
        t.untilDay = true;
        return t;
    }

    /** Whether a shelter can be dug here with bare hands: soft ground three deep that drops itself (dirt, grass). */
    public static boolean shelterPossible(Bot bot) {
        return shelterProblem(bot) == null;
    }

    /** Ground dug quickly with bare hands, that drops itself (the lid): dirt and its kind. */
    private static boolean soft(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || s.is(net.minecraft.world.level.block.Blocks.PODZOL) || s.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
                || s.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT) || s.is(net.minecraft.world.level.block.Blocks.ROOTED_DIRT)
                || s.is(net.minecraft.world.level.block.Blocks.DIRT);
    }

    /** Why no shelter can be dug here (null: it can). */
    public static String shelterProblem(Bot bot) {
        BotPlayer body = bot.body();
        if (!body.onGround() || body.isInWater()) return "not on dry ground";
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = body.blockPosition();
        for (int i = 1; i <= 4; i++) {
            BlockPos p = feet.below(i);
            var s = level.getBlockState(p);
            if (s.getCollisionShape(level, p).isEmpty() || s.getDestroySpeed(level, p) < 0) return "no floor " + i + " down";
            if (i <= 3 && !soft(s)) return s.getBlock() + " " + i + " down";
            for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return "water " + i + " down";
        }
        return null;
    }

    /** Up instead of down: three blocks on a pillar, out of reach of zombies (they hit on while one digs). */
    private boolean up;
    private int placed;

    public static HideTask upward() {
        HideTask t = new HideTask();
        t.up = true;
        return t;
    }

    /** Whether a pillar works here: room above the head for two more blocks, something to build with. */
    public static boolean pillarPossible(Bot bot) {
        BotPlayer body = bot.body();
        if (!body.onGround() || body.isInWater() || !bot.actions().hasThrowaway()) return false;
        ServerLevel level = (ServerLevel) body.level();
        BlockPos head = body.blockPosition().above();
        for (int i = 1; i <= 4; i++) if (!level.getBlockState(head.above(i)).getCollisionShape(level, head.above(i)).isEmpty()) return false;
        return true;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > (untilDay ? 13000 : MAX_TICKS) || !body.isAlive()) return Result.DONE;
        if (top == null) top = body.blockPosition();
        if (up) return pillar(bot, body, level);
        body.stopInputs();
        // Down: the block under the feet, three times (it drops into the gap), so the lid goes in
        // at ground level, held by the ground round it.
        dug = top.getY() - body.blockPosition().getY();
        if (dug < 3) {
            if (!body.onGround()) return Result.RUNNING;
            // Over the middle of the block first, or it would stay standing on the next one.
            if (!centred(body)) return Result.RUNNING;
            BlockPos below = body.blockPosition().below();
            bot.tools().equipFor(level.getBlockState(below));
            bot.actions().breakTick(below);
            return Result.RUNNING;
        }
        // The lid: a block where the feet were.
        if (!closed) {
            BlockPos lid = body.blockPosition().above(2);
            if (!body.onGround()) return Result.RUNNING; // (still dropping in: the head is in the way)
            if (!level.getBlockState(lid).getCollisionShape(level, lid).isEmpty()) closed = true;
            else if (!bot.actions().placeThrowaway(lid) && ++lidTries > (untilDay ? 400 : 20)) closed = true; // (it will not go: as it is)
            return Result.RUNNING;
        }
        // Safe: eat, and wait for the hearts (natural regeneration wants a full-ish stomach).
        if (body.getFoodData().getFoodLevel() < 20 && EatTask.bestFood(body) >= 0) {
            bot.interject(new EatTask());
            return Result.RUNNING;
        }
        // (The night's shelter: until the sun is up - the zombies and skeletons burn then.)
        if (untilDay) return level.isDarkOutside() ? Result.RUNNING : Result.DONE;
        if (body.getHealth() >= 16 || body.getFoodData().getFoodLevel() < 18 && ticks > 200) return Result.DONE;
        return Result.RUNNING;
    }

    private Result pillar(Bot bot, BotPlayer body, ServerLevel level) {
        int height = body.blockPosition().getY() - top.getY();
        // Knocked off (a hit sends it flying): start a new pillar from where it landed.
        if (body.onGround() && placed > 0 && height < placed && (body.blockPosition().getX() != top.getX() || body.blockPosition().getZ() != top.getZ())) {
            top = body.blockPosition();
            placed = 0;
            height = 0;
        }
        if (placed < 3 && height < 3) {
            // Jump, and at the top of the jump put a block where the feet were.
            body.jump = true;
            BlockPos below = body.blockPosition().below();
            if (!body.onGround() && level.getBlockState(below).canBeReplaced() && body.getY() - body.blockPosition().getY() < 0.6
                    && bot.actions().placeThrowaway(below)) placed++;
            return Result.RUNNING;
        }
        body.stopInputs();
        if (body.getFoodData().getFoodLevel() < 20 && EatTask.bestFood(body) >= 0) {
            bot.interject(new EatTask());
            return Result.RUNNING;
        }
        if (body.getHealth() >= 16 || body.getFoodData().getFoodLevel() < 18 && ticks > 300) return Result.DONE;
        return Result.RUNNING;
    }

    private static boolean centred(BotPlayer body) {
        BlockPos p = body.blockPosition();
        double dx = p.getX() + 0.5 - body.getX(), dz = p.getZ() + 0.5 - body.getZ();
        if (dx * dx + dz * dz < 0.04) return true;
        body.setYRot((float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f);
        body.forward = 0.3f;
        return false;
    }

    @Override
    public String describe() {
        return untilDay ? "dug in for the night" : "hide in the ground";
    }
}
