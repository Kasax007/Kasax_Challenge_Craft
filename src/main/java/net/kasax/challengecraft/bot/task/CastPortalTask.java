package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotActions;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The speedrunners' portal: cast in place next to a lava pool with a lava bucket and a water
 * bucket, no diamond pickaxe. Each of the ten frame spots gets lava from the pool, then water is
 * poured beside it so it runs onto the lava and turns it to obsidian, and the water is taken back.
 * Water needs a block to be poured against, so the mould grows with the frame: corners from spare
 * blocks, scaffold blocks inside the frame one row below the next (and on top of the upper
 * corners), all cleared away at the end. Then the frame is lit and the bot steps in.
 */
public final class CastPortalTask implements BotTask {
    private enum Kind { BLOCK, LAVA, WATER, TAKE_WATER, CLEAR }

    private record Op(Kind kind, BlockPos pos, BlockPos cell) {
    }

    private final ResourceKey<Level> from;
    private final Set<BlockPos> badPools = new HashSet<>();
    private List<Op> ops;
    private List<BlockPos> frame;
    private BlockPos inside, pool;
    private Direction along;
    private int index, wait, ticks, tries, retries;
    private boolean walking, lit;

    public CastPortalTask(Level level) {
        this.from = level.dimension();
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (level.dimension() != from) return Result.DONE;
        if (++ticks > 12000) return Result.FAILED;
        if (ops == null && !plan(bot, level)) {
            bot.say("no spot to cast a portal by a lava pool");
            return Result.FAILED;
        }
        Result r = index < ops.size() ? step(bot, level, ops.get(index)) : lightAndEnter(bot, level);
        if (r == Result.FAILED) bot.say("casting stopped at " + status());
        return r;
    }

    private Result step(Bot bot, ServerLevel level, Op op) {
        BotPlayer body = bot.body();
        switch (op.kind()) {
            case BLOCK -> {
                if (!level.getBlockState(op.pos()).canBeReplaced()) return next();
                // Up close (from the edge of reach a placement can miss), and step aside for a
                // mob standing in the spot.
                if (!close(bot, op.pos())) return walkNear(bot, op.pos());
                bot.navigator().stop();
                if (bot.actions().placeThrowaway(op.pos())) return next();
                if (++tries % 20 == 0) bot.say("can't place at " + op.pos().toShortString() + " (" + level.getBlockState(op.pos()).getBlock().getName().getString()
                        + ", " + level.getEntities(null, new net.minecraft.world.phys.AABB(op.pos())).size() + " in the way)");
                return tries > 60 ? Result.FAILED : Result.RUNNING;
            }
            case LAVA -> {
                if (level.getBlockState(op.cell()).is(Blocks.OBSIDIAN)) return next();
                if (BotInventory.slotOf(body, Items.LAVA_BUCKET) < 0) return scoop(bot, level);
                if (!close(bot, op.cell())) return walkNear(bot, op.cell());
                bot.navigator().stop();
                if (pourInto(bot, level, Items.LAVA_BUCKET, op.cell())) {
                    wait = 0;
                    return next();
                }
                return ++tries > 60 ? Result.FAILED : Result.RUNNING;
            }
            case WATER -> {
                if (level.getBlockState(op.cell()).is(Blocks.OBSIDIAN)) return next();
                // Lava bucket in hand first, so the water runs only as long as it must.
                if (BotInventory.slotOf(body, Items.LAVA_BUCKET) < 0) return scoop(bot, level);
                if (BotInventory.slotOf(body, Items.WATER_BUCKET) < 0) {
                    // The water got away (ran off before it was taken back): fill up again.
                    if (refill == null) refill = new FillBucketTask(FluidTags.WATER);
                    Result r = refill.tick(bot);
                    if (r == Result.RUNNING) return r;
                    refill = null;
                    return r == Result.DONE ? Result.RUNNING : Result.FAILED;
                }
                if (!close(bot, op.pos())) return walkNear(bot, op.pos());
                bot.navigator().stop();
                if (pourInto(bot, level, Items.WATER_BUCKET, op.pos())) {
                    wait = 0;
                    return next();
                }
                return ++tries > 60 ? Result.FAILED : Result.RUNNING;
            }
            case TAKE_WATER -> {
                if (++wait < 4) return Result.RUNNING;
                // The water has to be back in the bucket before anything else, or it spreads over
                // everything (and into the lava pool). Up close, until the source is gone.
                if (level.getFluidState(op.pos()).is(FluidTags.WATER) && level.getFluidState(op.pos()).isSource()) {
                    if (!close(bot, op.pos())) return walkNear(bot, op.pos());
                    bot.navigator().stop();
                    use(bot, level, Items.BUCKET, Vec3.atCenterOf(op.pos()));
                    if (level.getFluidState(op.pos()).isSource()) return ++tries > 40 ? Result.FAILED : Result.RUNNING;
                }
                if (!level.getBlockState(op.cell()).is(Blocks.OBSIDIAN)) {
                    // Did not set: back to this spot's lava (clearing what is in it) and try again.
                    if (++retries > 4) return Result.FAILED;
                    if (!level.getBlockState(op.cell()).isAir() && level.getFluidState(op.cell()).isEmpty()) {
                        bot.actions().breakTick(op.cell());
                        return Result.RUNNING;
                    }
                    index -= 2;
                    wait = 0;
                    return Result.RUNNING;
                }
                wait = 0;
                return next();
            }
            case CLEAR -> {
                if (level.getBlockState(op.pos()).isAir()) return next();
                if (!level.getFluidState(op.pos()).isEmpty()) {
                    if (level.getFluidState(op.pos()).isSource() && level.getFluidState(op.pos()).is(FluidTags.WATER)) {
                        use(bot, level, Items.BUCKET, Vec3.atCenterOf(op.pos()));
                    }
                    return ++wait > 60 ? next() : Result.RUNNING;
                }
                if (!bot.actions().inReach(op.pos())) return walkNear(bot, op.pos());
                bot.navigator().stop();
                if (bot.actions().breakTick(op.pos())) return next();
                return Result.RUNNING;
            }
        }
        return Result.RUNNING;
    }

    private Result next() {
        index++;
        tries = 0;
        walking = false;
        return Result.RUNNING;
    }

    /** Lava from the pool into the empty bucket. */
    private Result scoop(Bot bot, ServerLevel level) {
        if (BotInventory.slotOf(bot.body(), Items.BUCKET) < 0) return Result.FAILED;
        if (pool == null || !level.getFluidState(pool).is(FluidTags.LAVA) || !level.getFluidState(pool).isSource()) {
            pool = BotWorld.nearest(level, inside, 20, 8, s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), true, badPools);
            if (pool == null) return Result.FAILED;
        }
        if (!bot.actions().inReach(pool)) return walkNear(bot, pool);
        bot.navigator().stop();
        use(bot, level, Items.BUCKET, Vec3.atCenterOf(pool).add(0, 0.4, 0));
        if (BotInventory.slotOf(bot.body(), Items.LAVA_BUCKET) < 0 && ++tries > 3) {
            badPools.add(pool);
            pool = null;
            tries = 0;
        }
        return Result.RUNNING;
    }

    private Result lightAndEnter(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        if (!lit) {
            BlockPos base = inside.below();
            if (!bot.actions().inReach(base)) return walkNear(bot, base);
            int slot = BotInventory.slotOf(body, Items.FLINT_AND_STEEL);
            if (slot < 0) return Result.FAILED;
            bot.navigator().stop();
            bot.tools().select(slot);
            Vec3 hit = Vec3.atCenterOf(base).add(0, 0.5, 0);
            body.lookAt(hit);
            body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, Direction.UP, base, false));
            lit = level.getBlockState(inside).is(Blocks.NETHER_PORTAL);
            if (!lit && ++tries > 60) return Result.FAILED;
            return Result.RUNNING;
        }
        if (!body.blockPosition().equals(inside)) {
            if (!walking) {
                bot.navigator().goTo(inside);
                walking = true;
            }
            if (bot.navigator().tick() != BotNavigator.Status.MOVING) walking = false;
        } else {
            bot.navigator().stop();
            body.stopInputs();
        }
        return Result.RUNNING;
    }

    /** A spot for the frame right by a lava pool, so every scoop is a step or two away. */
    private boolean plan(Bot bot, ServerLevel level) {
        BlockPos lava = BotWorld.nearest(level, bot.body().blockPosition(), 32, 12,
                s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), true, Set.of());
        if (lava == null) return false;
        // Far enough that the water poured for casting (taken back within a second) cannot run
        // into the pool and turn it to stone, near enough for quick trips with the lava bucket.
        for (int r = 9; r <= 13; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -1; dy <= 2; dy++) {
                        for (Direction dir : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                            BlockPos o = lava.offset(dx, dy, dz);
                            if (fits(level, o, dir)) {
                                build(o, dir);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean fits(ServerLevel level, BlockPos o, Direction along) {
        for (int i = 0; i < 4; i++) {
            BlockPos col = o.relative(along, i);
            BlockPos under = col.below();
            if (level.getBlockState(under).getCollisionShape(level, under).isEmpty() || !level.getFluidState(under).isEmpty()) return false;
            for (int j = 0; j < 6; j++) {
                BlockPos p = col.above(j);
                if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()) return false;
            }
        }
        return true;
    }

    /** The casting order: bottom row, the two columns row by row (the mould growing inside), the top. */
    private void build(BlockPos o, Direction dir) {
        along = dir;
        ops = new ArrayList<>();
        frame = new ArrayList<>();
        BlockPos a = o, b = o.relative(dir, 3), l = o.relative(dir, 1), r = o.relative(dir, 2);
        inside = l.above();
        ops.add(new Op(Kind.BLOCK, a, null));
        ops.add(new Op(Kind.BLOCK, b, null));
        cast(l, a.above());          // water on the left corner runs over the lava at l
        cast(r, b.above());
        for (int j = 1; j <= 3; j++) {
            if (j >= 2) {
                ops.add(new Op(Kind.BLOCK, l.above(j - 1), null));   // scaffold inside, to pour against
                ops.add(new Op(Kind.BLOCK, r.above(j - 1), null));
            }
            cast(a.above(j), l.above(j));
            cast(b.above(j), r.above(j));
        }
        ops.add(new Op(Kind.BLOCK, a.above(4), null));
        ops.add(new Op(Kind.BLOCK, b.above(4), null));
        ops.add(new Op(Kind.BLOCK, a.above(5), null));
        ops.add(new Op(Kind.BLOCK, b.above(5), null));
        cast(l.above(4), l.above(5));
        cast(r.above(4), r.above(5));
        for (int j = 1; j <= 3; j++) {
            ops.add(new Op(Kind.CLEAR, l.above(j), null));
            ops.add(new Op(Kind.CLEAR, r.above(j), null));
        }
    }

    /**
     * One frame spot, the speedrunners' way round: water first, so it runs past the spot, then the
     * lava straight into it - a lava source touching water sets to obsidian at once. The water is
     * scooped back up so it does not spread.
     */
    private void cast(BlockPos cell, BlockPos water) {
        frame.add(cell);
        ops.add(new Op(Kind.WATER, water, cell));
        ops.add(new Op(Kind.LAVA, cell, cell));
        ops.add(new Op(Kind.TAKE_WATER, water, cell));
    }

    private Result walkNear(Bot bot, BlockPos p) {
        if (!walking) {
            bot.navigator().goNear(p, 2.4);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        if (s == BotNavigator.Status.FAILED && ++walkFails > 5) {
            bot.say("can't get near " + p.toShortString());
            return Result.FAILED;
        }
        return Result.RUNNING;
    }

    private int walkFails;
    private BotTask refill;

    private static boolean close(Bot bot, BlockPos p) {
        return bot.body().getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= 3.4;
    }

    private static void use(Bot bot, ServerLevel level, Item item, Vec3 at) {
        int slot = BotInventory.slotOf(bot.body(), item);
        if (slot < 0) return;
        bot.tools().select(slot);
        bot.body().lookAt(at);
        bot.body().gameMode.useItem(bot.body(), level, bot.body().getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    public static boolean pourIntoForTest(Bot bot, ServerLevel level, Item bucket, BlockPos spot) {
        return pourInto(bot, level, bucket, spot);
    }

    /** Pours a bucket into {@code spot}: looks at the face of a solid neighbour that borders it. */
    private static boolean pourInto(Bot bot, ServerLevel level, Item bucket, BlockPos spot) {
        int slot = BotInventory.slotOf(bot.body(), bucket);
        if (slot < 0) return false;
        Vec3 eye = bot.body().getEyePosition();
        for (Direction d : Direction.values()) {
            BlockPos n = spot.relative(d);
            if (level.getBlockState(n).getCollisionShape(level, n).isEmpty()) continue;
            Vec3 face = Vec3.atCenterOf(n).add(-d.getStepX() * 0.5, -d.getStepY() * 0.5, -d.getStepZ() * 0.5);
            if (face.distanceTo(eye) > 4.5) continue; // a player's reach for using items on blocks
            bot.tools().select(slot);
            bot.body().lookAt(face);
            bot.body().gameMode.useItem(bot.body(), level, bot.body().getMainHandItem(), InteractionHand.MAIN_HAND);
            if (BotInventory.slotOf(bot.body(), bucket) < 0) return true;
        }
        return false;
    }

    @Override
    public String status() {
        return describe() + " [step " + index + "/" + (ops == null ? 0 : ops.size())
                + (ops != null && index < ops.size() ? " " + ops.get(index) : "") + "]";
    }

    @Override
    public String describe() {
        return "cast a portal at a lava pool and go through";
    }
}
