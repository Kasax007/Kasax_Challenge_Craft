package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

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

    /** Something that hurts in the spot itself (a berry bush, a cactus beside it, fire): no shelter there. */
    static boolean hurtsHere(ServerLevel level, BlockPos feet) {
        for (BlockPos p : List.of(feet, feet.above())) {
            var s = level.getBlockState(p);
            if (s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) || s.is(net.minecraft.tags.BlockTags.FIRE)
                    || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || s.is(net.minecraft.world.level.block.Blocks.COBWEB)
                    // (Vines, a ladder: it hangs on them and never drops into its hole - seed 11
                    // was shot so, "dug in" at the surface.)
                    || s.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return true;
            for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(p.relative(d)).is(net.minecraft.world.level.block.Blocks.CACTUS)) return true;
        }
        return false;
    }

    /** Whether hiding right here works: solid ground two deep, nothing liquid, something to dig with. */
    public static boolean possible(Bot bot) {
        BotPlayer body = bot.body();
        if (!body.onGround() || body.isInWater()) return false;
        return possibleAt(bot, body.blockPosition());
    }

    /** Whether hiding with the feet at {@code feet} would work (see {@link #possible}). */
    public static boolean possibleAt(Bot bot, BlockPos feet) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (hurtsHere(level, feet)) return false;
        // (Nothing in the pack for the lid: what it digs out is the lid, if it drops - stone to a
        // pickaxe, dirt to a hand.)
        if (!bot.actions().hasThrowaway() && !bot.tools().canHarvest(level.getBlockState(feet.below()))) return false;
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
    private int dawnTicks;

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
        return shelterProblemAt((ServerLevel) body.level(), body.blockPosition());
    }

    /**
     * The nearest spot on the ground within {@code radius} (and not far up or down) where it can
     * dig in, either way, or null - to go to instead of a few steps anywhere: on seed 11 red sand
     * all about kept it from digging in, and the night went on with logs chopped in the dark.
     */
    public static BlockPos spotNear(Bot bot, int radius) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BlockPos at = bot.body().blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = dx * dx + dz * dz;
                if (d > radius * radius || d >= bestD) continue;
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(dx, 0, dz));
                if (Math.abs(top.getY() - at.getY()) > 6) continue;
                if (!level.getBlockState(top).getCollisionShape(level, top).isEmpty() || !level.getFluidState(top).isEmpty()) continue;
                if (possibleAt(bot, top) || shelterProblemAt(level, top) == null) {
                    best = top;
                    bestD = d;
                }
            }
        }
        return best;
    }

    static String shelterProblemAt(ServerLevel level, BlockPos feet) {
        if (hurtsHere(level, feet)) return "something that hurts here";
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
        if (!body.isAlive()) return Result.DONE;
        // (Out of time, but hurt with one still by the hole: a while longer - out at 11 hp into
        // the zombie that waited, it died on seed 44.)
        if (++ticks > (untilDay ? 13000 : MAX_TICKS) && (ticks > 3 * MAX_TICKS || untilDay || body.getHealth() >= 16 || !monsterBy(body))) return Result.DONE;
        if (top == null) top = body.blockPosition();
        if (up) return pillar(bot, body, level);
        body.stopInputs();
        // Down: the block under the feet, three times (it drops into the gap), so the lid goes in
        // at ground level, held by the ground round it.
        dug = top.getY() - body.blockPosition().getY();
        // (Not getting down at all - hanging on something, the block will not break: given up,
        // so somewhere else is tried, instead of standing in the open "dug in".)
        if (dug < 3 && ticks > 200 && dug <= 0) return Result.FAILED;
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
        // (And not out while one still stands by the hole: in the shade or not burning yet, a
        // zombie waiting at dawn finished it off as it climbed out on seed 66. A while longer.)
        if (untilDay) {
            if (level.isDarkOutside()) return Result.RUNNING;
            if (++dawnTicks < 2400 && monsterBy(body)) return Result.RUNNING;
            return Result.DONE;
        }
        if (body.getHealth() >= 16 || body.getFoodData().getFoodLevel() < 18 && ticks > 200 && !monsterBy(body)) return Result.DONE;
        return Result.RUNNING;
    }

    /**
     * A monster that would be waiting outside: within ten blocks (not an enderman, not a spider by
     * day), or a bowman within twenty-four - one in the shade of a tree at dawn shot it as it
     * climbed out with thirteen hearts and nothing to eat on seed 77.
     */
    private static boolean monsterBy(BotPlayer body) {
        boolean day = !body.level().isDarkOutside();
        return !body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                body.getBoundingBox().inflate(24, 10, 24), m -> m.isAlive()
                        && !(m instanceof net.minecraft.world.entity.monster.Enderman)
                        && !(day && m instanceof net.minecraft.world.entity.monster.spider.Spider)
                        && (m instanceof net.minecraft.world.entity.monster.RangedAttackMob || m.distanceToSqr(body) < 11 * 11)).isEmpty();
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
