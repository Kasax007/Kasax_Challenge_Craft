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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The speedrunners' portal: cast in place by a lava pool with one lava bucket and one water bucket,
 * no diamond pickaxe. The trick is the mould: a few spare blocks put exactly where they make one
 * poured water source run past several frame spots at once, and as long as that source stays, the
 * water keeps flowing there - every lava bucket poured into such a spot sets to obsidian at once
 * (a lava source touching water beside or above it). So it takes three pours of water for all ten
 * frame blocks, all of it done standing on the ground:
 * <pre>
 *   y4   W2 L   L  W3     1. corners c0, c3; water W1 on c0 runs over x1 and x2 -> lava x1, x2
 *   y3   L  M   M  L      2. the mould M: two columns inside the frame, up to the top
 *   y2   L  M   M  L      3. water W2 in the top left corner falls down the left column -> lava
 *   y1   L  M   M  L         into it, each against the mould beside it; the top left mould block
 *   y0   c0 L   L  c3        out, W2 runs in -> lava there too
 *        x0 x1  x2 x3     4. the same on the right with W3; 5. the mould comes out, light it.
 * </pre>
 * Each water is scooped back as soon as its lava is in, so it never spreads far.
 */
public final class CastPortalTask implements BotTask {
    private enum Kind { BLOCK, LAVA, WATER, TAKE_WATER, BREAK }

    private record Op(Kind kind, BlockPos pos) {
    }

    private final ResourceKey<Level> from;
    private final Set<BlockPos> badPools = new HashSet<>();
    private List<Op> ops;
    /** The four columns of the frame, which the bot keeps out of while casting. */
    private final Set<Long> footprint = new HashSet<>();
    /** Spots it stood on and could not pour from after all. */
    private final Set<Long> badStands = new HashSet<>();
    private BlockPos inside, pool, planLava;
    private int index, wait, ticks, tries, walkFails;
    private boolean walking, lit;
    private BotTask refill;

    /** The lava pool it means to cast at (null: the nearest one). */
    private final BlockPos near;

    public CastPortalTask(Level level) {
        this(level, null);
    }

    public CastPortalTask(Level level, BlockPos near) {
        this.from = level.dimension();
        this.near = near;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (level.dimension() != from) return Result.DONE;
        if (++ticks > 12000) return Result.FAILED;
        if (ops == null && near != null && bot.body().blockPosition().distManhattan(near) > 24 && walkFails <= 8) {
            // Back to the pool it saw first.
            if (!walking) {
                bot.navigator().goNear(near, 12);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s != BotNavigator.Status.MOVING) walking = false;
            if (s == BotNavigator.Status.FAILED && ++walkFails > 8) return Result.FAILED;
            if (s != BotNavigator.Status.ARRIVED) return Result.RUNNING;
            walkFails = 0;
        }
        if (ops == null && !plan(bot, level)) {
            bot.say("no spot to cast a portal by a lava pool");
            if (near != null) bot.badCastPools.add(near);
            if (planLava != null) bot.badCastPools.add(planLava);
            return Result.FAILED;
        }
        Result r = index < ops.size() ? step(bot, level, ops.get(index)) : lightAndEnter(bot, level);
        // Could not get to the spot (before anything was poured there): another spot, a few times.
        if (r == Result.FAILED && index <= prepOps && respots < 3 && origin != null) {
            badOrigins.add(origin.asLong());
            respots++;
            bot.say("that spot is out of reach, another one");
            ops = null;
            index = 0;
            tries = 0;
            walkFails = 0;
            walking = false;
            footprint.clear();
            bot.navigator().stop();
            return Result.RUNNING;
        }
        if (r == Result.FAILED) {
            // (This pool does not work out: the next try another.)
            if (planLava != null) bot.badCastPools.add(planLava);
            if (near != null) bot.badCastPools.add(near);
            var b = bot.body();
            Op op = index < ops.size() ? ops.get(index) : null;
            bot.say("casting stopped at " + status() + " (tries " + tries + ", walk fails " + walkFails
                    + ", standing " + b.blockPosition().toShortString() + (op == null ? "" : ", op " + Math.round(Math.sqrt(op.pos().distSqr(b.blockPosition()))) + " blocks off")
                    + ", water " + BotInventory.slotOf(b, Items.WATER_BUCKET) + ", lava " + BotInventory.slotOf(b, Items.LAVA_BUCKET)
                    + ", bucket " + BotInventory.slotOf(b, Items.BUCKET) + ", pool " + (pool == null ? "-" : pool.toShortString()) + ")");
        }
        return r;
    }

    private Result step(Bot bot, ServerLevel level, Op op) {
        BotPlayer body = bot.body();
        BlockPos p = op.pos();
        switch (op.kind()) {
            case BLOCK -> {
                if (!level.getBlockState(p).canBeReplaced()) return next();
                if (!bot.actions().inReach(p) || inTheWay(body, p)) return walkNear(bot, p);
                bot.navigator().stop();
                if (placeSoftest(bot, p)) return next();
                return ++tries > 60 ? Result.FAILED : Result.RUNNING;
            }
            case WATER -> {
                // A source there already (a retry): never a second one beside it, two sources make
                // water that never goes away.
                if (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource()) return next();
                // Lava bucket filled first, so the water runs only as long as it must.
                if (BotInventory.slotOf(body, Items.LAVA_BUCKET) < 0) return scoop(bot, level);
                if (BotInventory.slotOf(body, Items.WATER_BUCKET) < 0) return refill(bot);
                if (pourFace(level, body, body.getEyePosition(), p) == null) return walkToPour(bot, p);
                bot.navigator().stop();
                if (pourInto(bot, level, Items.WATER_BUCKET, p)) {
                    wait = 0;
                    return next();
                }
                return ++tries > 60 ? Result.FAILED : Result.RUNNING;
            }
            case LAVA -> {
                if (level.getBlockState(p).is(Blocks.OBSIDIAN)) return next();
                if (level.getFluidState(p).is(FluidTags.LAVA) && level.getFluidState(p).isSource()) {
                    // Poured, but it did not set (the water was not there yet): take it back.
                    if (BotInventory.slotOf(body, Items.BUCKET) < 0) return Result.FAILED;
                    if (!inReach(bot, p)) return walkNear(bot, p);
                    bot.navigator().stop();
                    use(bot, level, Items.BUCKET, Vec3.atCenterOf(p));
                    return ++tries > 60 ? Result.FAILED : Result.RUNNING;
                }
                if (BotInventory.slotOf(body, Items.LAVA_BUCKET) < 0) return scoop(bot, level);
                if (inTheWay(body, p) || pourFace(level, body, body.getEyePosition(), p) == null) return walkToPour(bot, p);
                bot.navigator().stop();
                // The water has to be running there first (it takes a moment to fall down).
                if (!wetBeside(level, p)) {
                    if (++wait > 200) {
                        bot.say("no water runs beside " + p.toShortString());
                        return Result.FAILED;
                    }
                    return Result.RUNNING;
                }
                pourInto(bot, level, Items.LAVA_BUCKET, p);
                return ++tries > 60 ? Result.FAILED : Result.RUNNING;
            }
            case TAKE_WATER -> {
                // Back in the bucket, up close, until no source is left there.
                BlockPos source = waterSourceNear(level, p);
                if (source == null) {
                    wait = 0;
                    return next();
                }
                // (Arrived where the path search thought it could, but off the middle of the block it
                // cannot quite: try anyway, and if that does not work, not from there again.)
                if (!canScoop(level, body, body.getEyePosition(), source, 4.4)) {
                    if (scoopArrived && ++scoopTries < 30) {
                        bot.navigator().stop();
                        use(bot, level, Items.BUCKET, Vec3.atCenterOf(source));
                        return Result.RUNNING;
                    }
                    if (scoopArrived) badScoops.add(body.blockPosition().asLong());
                    scoopArrived = false;
                    scoopTries = 0;
                    return walkToScoop(bot, source);
                }
                bot.navigator().stop();
                use(bot, level, Items.BUCKET, Vec3.atCenterOf(source));
                return ++tries > 200 ? Result.FAILED : Result.RUNNING;
            }
            case BREAK -> {
                if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return next();
                // A little more reach than to start on it: the water running past nudges the bot
                // about, and walking off every time would start the block over again.
                if (body.getEyePosition().distanceTo(Vec3.atCenterOf(p)) > 4.9) return walkNear(bot, p);
                bot.navigator().stop();
                if (bot.actions().breakTick(p)) return next();
                return Result.RUNNING;
            }
        }
        return Result.RUNNING;
    }

    /** Water (running or still) above or beside {@code p}: lava poured there sets at once. */
    private static boolean wetBeside(ServerLevel level, BlockPos p) {
        for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (level.getFluidState(p.relative(d)).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** A water source at or right around {@code p}. */
    private static BlockPos waterSourceNear(ServerLevel level, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 1, 2))) {
            if (level.getFluidState(q).is(FluidTags.WATER) && level.getFluidState(q).isSource()) return q.immutable();
        }
        return null;
    }

    private Result next() {
        index++;
        tries = 0;
        wait = 0;
        walking = false;
        return Result.RUNNING;
    }

    /** The water got away (ran off, or was never taken back): fill the bucket again. */
    private Result refill(Bot bot) {
        if (refill == null) refill = new FillBucketTask(FluidTags.WATER);
        Result r = refill.tick(bot);
        if (r == Result.RUNNING) return r;
        refill = null;
        walking = false;
        return r == Result.DONE ? Result.RUNNING : Result.FAILED;
    }

    /** Lava from the pool into the empty bucket. */
    private Result scoop(Bot bot, ServerLevel level) {
        if (BotInventory.slotOf(bot.body(), Items.BUCKET) < 0) return Result.FAILED;
        if (pool == null || !level.getFluidState(pool).is(FluidTags.LAVA) || !level.getFluidState(pool).isSource()) {
            // (Round the pool the spot was planned by first, then round the spot.)
            pool = planLava != null && !badPools.contains(planLava) && level.getFluidState(planLava).is(FluidTags.LAVA) && level.getFluidState(planLava).isSource() ? planLava : null;
            if (pool == null) pool = BotWorld.nearest(level, inside, 28, 16, s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), true, badPools);
            // Run dry here: the nearest other lava it remembers, if not too far off.
            if (pool == null) {
                BlockPos known = bot.memory().nearest(level, inside, s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), badPools);
                if (known != null && known.distSqr(inside) < 96 * 96) pool = known;
            }
            if (pool == null) {
                bot.say("no lava left to scoop near " + inside.toShortString());
                return Result.FAILED;
            }
            walking = false;
        }
        if (!bot.actions().inReach(pool)) return walkNear(bot, pool);
        bot.navigator().stop();
        use(bot, level, Items.BUCKET, Vec3.atCenterOf(pool).add(0, 0.4, 0));
        if (BotInventory.slotOf(bot.body(), Items.LAVA_BUCKET) >= 0) {
            walking = false;
        } else if (++tries > 3) {
            badPools.add(pool);
            pool = null;
            tries = 0;
        }
        return Result.RUNNING;
    }

    /** Lava sources within six blocks of {@code p} (how many buckets the pool holds). */
    private static int sources(ServerLevel level, BlockPos p) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-6, -3, -6), p.offset(6, 3, 6)))
            if (level.getFluidState(q).is(FluidTags.LAVA) && level.getFluidState(q).isSource() && ++n >= 12) return n;
        return n;
    }

    private Result lightAndEnter(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        if (!lit) {
            // Anything left in the doorway (a scrap of water) first.
            for (int j = 0; j < 3; j++) {
                for (BlockPos q : new BlockPos[]{inside.above(j), inside.above(j).relative(along, 1)}) {
                    if (!level.getBlockState(q).isAir() && level.getFluidState(q).isEmpty()) {
                        if (!bot.actions().inReach(q)) return walkNear(bot, q);
                        bot.actions().breakTick(q);
                        return Result.RUNNING;
                    }
                    if (level.getFluidState(q).is(FluidTags.WATER)) return ++wait > 200 ? Result.FAILED : Result.RUNNING;
                }
            }
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
            if (lit) walking = false;
            else if (++tries > 60) return Result.FAILED;
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

    /** A spot for the frame near a lava pool, so every scoop is a short walk. */
    private boolean plan(Bot bot, ServerLevel level) {
        // (A source to scoop from: the pool it was sent to may show only its running edge.)
        BlockPos lava = near != null && level.getFluidState(near).is(FluidTags.LAVA) && level.getFluidState(near).isSource() ? near
                : BotWorld.nearest(level, near != null ? near : bot.body().blockPosition(), 40, 16,
                s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), true, Set.of());
        if (lava == null) return false;
        // Ten buckets of lava go into a frame: a puddle of two or three runs dry half way. A
        // bigger pool about, if there is one.
        if (sources(level, lava) < 6) {
            BlockPos from = bot.body().blockPosition(), better = null;
            for (BlockPos q : BlockPos.betweenClosed(from.offset(-40, -16, -40), from.offset(40, 16, 40))) {
                if (!level.isLoaded(q) || !level.getFluidState(q).is(FluidTags.LAVA) || !level.getFluidState(q).isSource()) continue;
                if (better != null && q.distSqr(from) >= better.distSqr(from)) continue;
                if (!BotWorld.exposed(level, q) || sources(level, q) < 6) continue;
                better = q.immutable();
            }
            if (better != null) lava = better;
        }
        planLava = lava;
        // Far enough that the water poured for casting cannot run into the pool and turn it to
        // stone, near enough for quick trips with the lava bucket.
        for (int r = 9; r <= 13; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -1; dy <= 2; dy++) {
                        for (Direction dir : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                            BlockPos o = lava.offset(dx, dy, dz);
                            if (!badOrigins.contains(o.asLong()) && fits(level, o, dir)) {
                                prepOps = 0;
                                build(o, dir);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        // No ready spot (a cave, a hillside): make one, as a player does - dig the room out and
        // put a floor where there is none. The one with the least work, with nothing liquid in it.
        BlockPos best = null;
        Direction bestDir = null;
        int bestWork = 60;
        for (int r = 7; r <= 14; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    // (Not below the pool: from down in a pit there is no pouring.)
                    for (int dy = 1; dy <= 3; dy++) {
                        for (Direction dir : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                            BlockPos o = lava.offset(dx, dy, dz);
                            if (badOrigins.contains(o.asLong())) continue;
                            // (Near where it stands counts too: a spot it can get to.)
                            int work = work(bot, level, o, dir) + (int) (Math.sqrt(o.distSqr(bot.body().blockPosition())) / 8);
                            if (work < bestWork) {
                                bestWork = work;
                                best = o;
                                bestDir = dir;
                            }
                        }
                    }
                }
            }
        }
        if (best == null) return false;
        bot.say("making room to cast a portal (" + bestWork + " blocks to dig or place)");
        ops = new ArrayList<>();
        Direction side = bestDir.getClockWise();
        for (int i = 0; i < 4; i++) {
            BlockPos col = best.relative(bestDir, i);
            for (int j = 5; j >= 0; j--) {
                BlockPos p = col.above(j);
                if (!level.getBlockState(p).canBeReplaced()) add(Kind.BREAK, p);
            }
            if (level.getBlockState(col.below()).getCollisionShape(level, col.below()).isEmpty()) add(Kind.BLOCK, col.below());
            // And a row to stand in in front of it, to pour from (high enough to pillar up in for
            // the top of the frame).
            for (int j = 4; j >= 0; j--) {
                BlockPos p = col.relative(side).above(j);
                if (!level.getBlockState(p).canBeReplaced()) add(Kind.BREAK, p);
            }
        }
        List<Op> prep = ops;
        prepOps = prep.size();
        build(best, bestDir);
        prep.addAll(ops);
        ops = prep;
        return true;
    }

    /** Blocks to dig or put down to make a casting spot at {@code o}; 99 when it cannot be made (liquid, bedrock, too hard). */
    private static int work(Bot bot, ServerLevel level, BlockPos o, Direction along) {
        int work = 0;
        for (int i = 0; i < 4; i++) {
            BlockPos col = o.relative(along, i);
            // The row in front to stand in: two high, on ground.
            BlockPos stand = col.relative(along.getClockWise());
            if (level.getBlockState(stand.below()).getCollisionShape(level, stand.below()).isEmpty()) return 99;
            for (int j = 0; j < 5; j++) {
                var st = level.getBlockState(stand.above(j));
                if (!st.getFluidState().isEmpty() || st.getDestroySpeed(level, stand.above(j)) < 0) return 99;
                if (!st.canBeReplaced()) work++;
            }
            BlockPos under = col.below();
            if (!level.getFluidState(under).isEmpty()) return 99;
            if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) work++;
            for (int j = 0; j < 6; j++) {
                BlockPos p = col.above(j);
                var st = level.getBlockState(p);
                if (!st.getFluidState().isEmpty()) return 99;
                for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return 99;
                if (st.canBeReplaced()) continue;
                if (st.getDestroySpeed(level, p) < 0 || bot.tools().breakTicks(st) > 40) return 99;
                work++;
            }
        }
        return work;
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

    private Direction along;
    private BlockPos origin;
    private int prepOps, respots;
    private final Set<Long> badOrigins = new HashSet<>();

    /** The casting order (see the picture above). */
    private void build(BlockPos o, Direction dir) {
        along = dir;
        origin = o;
        ops = new ArrayList<>();
        BlockPos c0 = o, x1 = o.relative(dir, 1), x2 = o.relative(dir, 2), c3 = o.relative(dir, 3);
        inside = x1.above();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 6; j++) footprint.add(o.relative(dir, i).above(j).asLong());
        }
        // 1. The bottom: water on the left corner runs across both bottom spots.
        add(Kind.BLOCK, c0);
        add(Kind.BLOCK, c3);
        add(Kind.WATER, c0.above());
        add(Kind.LAVA, x1);
        add(Kind.LAVA, x2);
        add(Kind.TAKE_WATER, c0.above());
        // 2. The mould: two columns inside the frame, as high as the top.
        for (int j = 1; j <= 4; j++) add(Kind.BLOCK, x1.above(j));
        for (int j = 1; j <= 4; j++) add(Kind.BLOCK, x2.above(j));
        // 3. The left side: water in the top left corner falls down the whole column; each lava
        // goes in against the mould beside it. The water stays for the top left spot: the mould
        // block there comes out, the water runs in, lava after it.
        add(Kind.WATER, c0.above(4));
        for (int j = 1; j <= 3; j++) add(Kind.LAVA, c0.above(j));
        add(Kind.BREAK, x1.above(4));
        add(Kind.LAVA, x1.above(4));
        add(Kind.TAKE_WATER, c0.above(4));
        // 4. The right side, the same the other way round.
        add(Kind.WATER, c3.above(4));
        for (int j = 1; j <= 3; j++) add(Kind.LAVA, c3.above(j));
        add(Kind.BREAK, x2.above(4));
        add(Kind.LAVA, x2.above(4));
        add(Kind.TAKE_WATER, c3.above(4));
        // 5. The mould comes out.
        for (int j = 3; j >= 1; j--) {
            add(Kind.BREAK, x1.above(j));
            add(Kind.BREAK, x2.above(j));
        }
    }

    private void add(Kind kind, BlockPos pos) {
        ops.add(new Op(kind, pos.immutable()));
    }

    /**
     * Puts a spare block at {@code p}, the one quickest to break again (dirt before cobblestone):
     * the mould comes out at the end.
     */
    private static boolean placeSoftest(Bot bot, BlockPos p) {
        var items = bot.body().getInventory().getNonEquipmentItems();
        int best = -1;
        float bestTime = Float.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            var s = items.get(i);
            if (!BotActions.THROWAWAY.contains(s.getItem()) || !(s.getItem() instanceof net.minecraft.world.item.BlockItem b)) continue;
            float t = b.getBlock().defaultDestroyTime();
            if (t < bestTime) {
                bestTime = t;
                best = i;
            }
        }
        if (best < 0) return false;
        bot.tools().select(best);
        return bot.actions().placeHeld(p);
    }

    /** The bot's body is in that block (a block cannot go there, lava would burn it). */
    private static boolean inTheWay(BotPlayer body, BlockPos p) {
        return body.getBoundingBox().intersects(new AABB(p));
    }

    /**
     * Walks to a spot outside the frame from where {@code p} is within reach (never into the frame,
     * nor into a water source: the water falling down in there would carry it up and away).
     */
    private Result walkNear(Bot bot, BlockPos p) {
        if (!walking) {
            Vec3 at = Vec3.atCenterOf(p);
            ServerLevel level = (ServerLevel) bot.body().level();
            bot.navigator().setGoal(f -> !footprint.contains(f.asLong()) && !badScoops.contains(f.asLong()) && !level.getFluidState(f).isSource()
                    && Vec3.atCenterOf(f).add(0, 1.12, 0).distanceTo(at) <= 3.8, p);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        // "There" by the walk's measure, but not by the step's (in the way, a little short):
        // again and again from the same spot means: another spot.
        if (s == BotNavigator.Status.ARRIVED && ++arrivals > 10) {
            badScoops.add(bot.navigator().feet().asLong());
            arrivals = 0;
        }
        if (s == BotNavigator.Status.FAILED && ++walkFails > 8) {
            bot.say("can't get near " + p.toShortString());
            return Result.FAILED;
        }
        return Result.RUNNING;
    }

    private int arrivals;

    /** Like {@link #walkNear}, to a spot from where a bucket can be poured into {@code p} (up a pillar if need be). */
    private Result walkToPour(Bot bot, BlockPos p) {
        if (!walking) {
            BotPlayer body = bot.body();
            ServerLevel level = (ServerLevel) body.level();
            // Got there last time and still could not pour (pushed off by the water): elsewhere.
            if (bot.navigator().status() == BotNavigator.Status.ARRIVED) badStands.add(bot.navigator().feet().asLong());
            bot.navigator().setGoal(f -> !footprint.contains(f.asLong()) && !badStands.contains(f.asLong()) && !level.getFluidState(f).isSource()
                    && pourFace(level, body, new Vec3(f.getX() + 0.5, f.getY() + body.getEyeHeight(), f.getZ() + 0.5), p, 0.3, 3.9) != null, p);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        if (s == BotNavigator.Status.FAILED && ++walkFails > 8) {
            bot.say("can't get to pour into " + p.toShortString());
            return Result.FAILED;
        }
        return Result.RUNNING;
    }

    /** Whether an empty bucket used from {@code eye} would take up the water source at {@code source}. */
    private static boolean canScoop(ServerLevel level, BotPlayer body, Vec3 eye, BlockPos source, double reach) {
        Vec3 at = Vec3.atCenterOf(source);
        if (eye.distanceTo(at) > reach) return false;
        BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, body));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(source);
    }

    private boolean scoopArrived;
    private int scoopTries;
    private final java.util.Set<Long> badScoops = new java.util.HashSet<>();

    private Result walkToScoop(Bot bot, BlockPos source) {
        if (!walking) {
            BotPlayer body = bot.body();
            ServerLevel level = (ServerLevel) body.level();
            bot.navigator().setGoal(f -> !footprint.contains(f.asLong()) && !badScoops.contains(f.asLong()) && !level.getFluidState(f).isSource()
                    && canScoop(level, body, new Vec3(f.getX() + 0.5, f.getY() + body.getEyeHeight(), f.getZ() + 0.5), source, 3.9), source);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        if (s == BotNavigator.Status.ARRIVED) scoopArrived = true;
        if (s == BotNavigator.Status.FAILED && ++walkFails > 8) {
            bot.say("can't get to the water at " + source.toShortString());
            return Result.FAILED;
        }
        return Result.RUNNING;
    }

    private static boolean inReach(Bot bot, BlockPos p) {
        return bot.body().getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= 4.2;
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

    /**
     * The face a player at {@code eye} would click to pour into {@code spot}: of a solid neighbour,
     * bordering the spot, turned towards the eye, in reach and really in sight (fluids are looked
     * through). Null when there is none from there - a spot high up needs the bot higher up.
     */
    private static Vec3 pourFace(ServerLevel level, BotPlayer body, Vec3 eye, BlockPos spot) {
        return pourFace(level, body, eye, spot, 0.08, 4.5);
    }

    private static Vec3 pourFace(ServerLevel level, BotPlayer body, Vec3 eye, BlockPos spot, double minCos, double reach) {
        for (Direction d : Direction.values()) {
            BlockPos n = spot.relative(d);
            if (level.getBlockState(n).getCollisionShape(level, n).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3 at = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            Vec3 to = eye.subtract(at);
            if (to.length() > reach) continue; // a player's reach for using items on blocks is 4.5
            if (to.x * face.getStepX() + to.y * face.getStepY() + to.z * face.getStepZ() < minCos * to.length()) continue; // seen from the side it faces
            Vec3 past = at.add(to.normalize().scale(-0.1));
            BlockHitResult hit = level.clip(new ClipContext(eye, past, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(n) && hit.getDirection() == face) return at;
        }
        return null;
    }

    /** Pours a bucket into {@code spot}, looking at the face {@link #pourFace} finds. */
    private static boolean pourInto(Bot bot, ServerLevel level, Item bucket, BlockPos spot) {
        int slot = BotInventory.slotOf(bot.body(), bucket);
        if (slot < 0) return false;
        Vec3 face = pourFace(level, bot.body(), bot.body().getEyePosition(), spot);
        if (face == null) return false;
        bot.tools().select(slot);
        bot.body().lookAt(face);
        bot.body().gameMode.useItem(bot.body(), level, bot.body().getMainHandItem(), InteractionHand.MAIN_HAND);
        return BotInventory.slotOf(bot.body(), bucket) < 0;
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
