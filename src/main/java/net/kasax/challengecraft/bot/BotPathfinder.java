package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * A* over the positions a bot's feet can be at. The moves are the ones a careful player makes:
 * walk (also diagonally, without cutting corners), step up one block, drop down at most three
 * (any depth into water), swim, dig through what is in the way, dig straight down, and pillar up
 * on blocks it carries. Costs are in ticks, so a path through stone is only taken when walking
 * round is slower. The search reads only loaded chunks (unknown ground counts as blocked) and
 * stops after a node budget, returning the best partial path, so it never stalls the server.
 */
public final class BotPathfinder {
    /** One step of a path: the node to reach, what to break first, and where to put a block. */
    public record Step(BlockPos to, List<BlockPos> breaks, BlockPos place, boolean leap) {
        public Step(BlockPos to, List<BlockPos> breaks, BlockPos place) {
            this(to, breaks, place, false);
        }
    }

    /**
     * What the search may assume about the bot: whether it may dig and build, how long a block
     * takes it to break (from a copy of its tools, so the search can run off the server thread),
     * and whether it sprints (enough food).
     */
    public record Abilities(boolean mayBreak, boolean mayPillar, java.util.function.ToDoubleFunction<BlockState> breakTicks, boolean sprint) {
        public Abilities(boolean mayBreak, boolean mayPillar, BotTools tools) {
            this(mayBreak, mayPillar, tools::breakTicks, true);
        }
    }

    // ---- costs, in ticks, from the game's own movement (the numbers Baritone works out) -------

    private static final int MAX_FALL = 3;
    static final double WALK = 20 / 4.317, SPRINT = 20 / 5.612, WADE = 20 / 2.2, SWIM_SPRINT = 20 / 2.86,
            SNEAK = 20 / 1.3, WALK_OFF = WALK * 0.8, CENTRE = WALK - WALK_OFF;
    private static final double[] FALL = new double[257];
    static {
        for (int i = 0; i < FALL.length; i++) FALL[i] = distanceToTicks(i);
    }
    /** The rise of a jump onto a block: up 1.25 and back down 0.25. */
    static final double JUMP_ONE = distanceToTicks(1.25) - distanceToTicks(0.25);
    private static final double JUMP_PENALTY = 2, PLACE_PENALTY = 10, BREAK_PENALTY = 2, LEAP_EXTRA = 4;

    /** Ticks to fall {@code distance} blocks from standing (drag 0.98, gravity 0.08 a tick). */
    static double distanceToTicks(double distance) {
        if (distance <= 0) return 0;
        double left = distance;
        for (int tick = 0; ; tick++) {
            double v = (Math.pow(0.98, tick) - 1) * -3.92;
            if (left <= v) return tick + left / v;
            left -= v;
        }
    }

    /**
     * The chunks the search may read, collected on the server thread: the search itself then runs
     * on a worker thread without touching the chunk cache (as Baritone reads its own copy of the
     * world). Chunks not in it count as unknown, which the search treats as blocked.
     */
    public static final class WorldView {
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<LevelChunk> chunks = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        final int minY, maxY;

        private WorldView(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        /** The loaded chunks within {@code radius} chunks of {@code center}. */
        public static WorldView capture(ServerLevel level, BlockPos center, int radius) {
            WorldView v = new WorldView(level.getMinY(), level.getMaxY());
            int cx = center.getX() >> 4, cz = center.getZ() >> 4;
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    LevelChunk c = level.getChunkSource().getChunkNow(x, z);
                    if (c != null) v.chunks.put(net.minecraft.world.level.ChunkPos.pack(x, z), c);
                }
            }
            return v;
        }

        boolean loaded(BlockPos p) {
            return p.getY() >= minY && p.getY() < maxY && chunks.containsKey(net.minecraft.world.level.ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4));
        }

        BlockState state(BlockPos p) {
            if (p.getY() < minY || p.getY() >= maxY) return Blocks.BEDROCK.defaultBlockState();
            LevelChunk c = chunks.get(net.minecraft.world.level.ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4));
            return c == null ? Blocks.BEDROCK.defaultBlockState() : c.getBlockState(p);
        }
    }

    private static final net.minecraft.world.level.BlockGetter NO_WORLD = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;

    private final WorldView world;
    private final Abilities abilities;
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BlockState> cache = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private final Map<BlockState, Double> breakCache = new java.util.IdentityHashMap<>();
    private final double flatCost;

    /** Spots where a step already failed: kept out of the search (as Baritone blacklists them). */
    private final java.util.Set<Long> avoid;
    /** The path being walked: staying on it costs half (no dithering between equal ways, no half-built bridges dropped). */
    private java.util.Set<Long> favoured = java.util.Set.of();

    public BotPathfinder(WorldView world, Abilities abilities, java.util.Set<Long> avoid) {
        this.world = world;
        this.abilities = abilities;
        this.avoid = avoid;
        this.flatCost = abilities.sprint() ? SPRINT : WALK;
    }

    public BotPathfinder favouring(java.util.Set<Long> path) {
        this.favoured = path;
        return this;
    }

    /**
     * The large view's way to the goal (see {@link BotTerrain}): what is left from each cell. Far
     * from the goal it steers the search instead of the straight line, so the best partial way
     * runs along the real way round, not into the bay the straight line crosses.
     */
    private BotTerrain.Field guide;
    /** How near the goal is good enough (reach): taken off the straight-line estimate. */
    private double near;

    public BotPathfinder guided(BotTerrain.Field guide) {
        this.guide = guide;
        return this;
    }

    /** Several places, any of which will do (the heuristic: the nearest of them). */
    private List<BlockPos> targets;

    public BotPathfinder toAnyOf(List<BlockPos> targets) {
        this.targets = targets == null || targets.size() < 2 ? null : List.copyOf(targets);
        return this;
    }

    public BotPathfinder near(double range) {
        this.near = Math.max(0, range - 1);
        return this;
    }

    /** Within this of the goal the large view is too coarse: the straight line steers. */
    private static final double GUIDE_NEAR = 32;
    /** Off the large view's figure: half a cell's walk and some (it is cell to cell, the search block to block). */
    private static final double GUIDE_SLACK = BotTerrain.CELL * SPRINT * 1.5;

    private double breakTicks(BlockState s) {
        return breakCache.computeIfAbsent(s, abilities.breakTicks()::applyAsDouble);
    }

    private static final class Node implements Comparable<Node> {
        final BlockPos pos;
        double g, f;
        Node parent;
        Step step;
        boolean closed;

        Node(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }
    }

    /** A search's outcome: the steps, and whether they end at the goal (else a partial way, to go on from). */
    public record Result(List<Step> steps, boolean complete, int expanded) {
    }

    /** Weights on the cost so far for picking a partial path (Baritone's): the first that gets far enough is taken. */
    private static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
    /** A partial path must get at least this far from the start (squared), or it is no progress. */
    private static final double MIN_PROGRESS_SQ = 5 * 5;

    /** As {@link #search}, with a node budget only (on the server thread, for small searches). */
    public List<Step> find(BlockPos start, Predicate<BlockPos> goal, BlockPos target, int budget) {
        Result r = search(start, goal, target, budget, Long.MAX_VALUE);
        return r == null ? null : r.steps();
    }

    /**
     * A* from {@code start} to a position {@code goal} accepts, steered by {@code target}; stops after
     * {@code maxNodes} nodes or {@code nanos} of time. Without reaching the goal, the best partial
     * way that gets at least five blocks away, or null.
     */
    public Result search(BlockPos start, Predicate<BlockPos> goal, BlockPos target, int maxNodes, long nanos) {
        // Standing in lava already (fallen in): out through it is the way, however dear - the
        // nearest ground clear of it.
        // (Or on magma, which burns as long as it is stood on: off it, whatever the way.)
        escapeLava = loaded(start) && state(start).getFluidState().is(FluidTags.LAVA)
                || loaded(start.below()) && state(start.below()).is(Blocks.MAGMA_BLOCK);
        long until = nanos == Long.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + nanos;
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Node> nodes = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        Node first = new Node(start);
        first.f = heuristic(start, target);
        nodes.put(start.asLong(), first);
        open.add(first);
        Node[] best = new Node[COEFFICIENTS.length];
        Node closest = first;
        double closestH = first.f;
        double[] bestScore = new double[COEFFICIENTS.length];
        java.util.Arrays.fill(bestScore, Double.MAX_VALUE);
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            if ((expanded & 255) == 0 && System.nanoTime() > until) break;
            Node n = open.poll();
            if (n.closed) continue;
            n.closed = true;
            expanded++;
            if (goal.test(n.pos)) return new Result(build(n), true, expanded);
            double h = heuristic(n.pos, target);
            if (h < closestH) {
                closestH = h;
                closest = n;
            }
            for (int i = 0; i < COEFFICIENTS.length; i++) {
                double score = h + n.g / COEFFICIENTS[i];
                if (score < bestScore[i]) {
                    bestScore[i] = score;
                    best[i] = n;
                }
            }
            for (Step s : moves(n.pos, n.step != null && n.step.place() != null && n.step.place().equals(n.pos.below()))) {
                double cost = cost(n.pos, s) + (avoid.contains(s.to().asLong()) ? 400 : 0);
                if (favoured.contains(s.to().asLong())) cost *= 0.5;
                long key = s.to().asLong();
                Node m = nodes.get(key);
                if (m == null) {
                    m = new Node(s.to());
                    nodes.put(key, m);
                }
                if (m.closed) continue;
                double g = n.g + cost;
                if (m.parent != null && g >= m.g) continue;
                m.g = g;
                m.f = g + heuristic(s.to(), target);
                m.parent = n;
                m.step = s;
                open.add(m);
            }
        }
        for (Node b : best) {
            if (b != null && b != first && b.pos.distSqr(start) >= MIN_PROGRESS_SQ) return new Result(build(b), false, expanded);
        }
        // (Nowhere five blocks away: whatever got nearest the target, if anything did.)
        return closest == first ? null : new Result(build(closest), false, expanded);
    }

    String debugMoves(BlockPos p) {
        StringBuilder sb = new StringBuilder();
        for (Step s : moves(p, false)) sb.append(s.to().subtract(p).toShortString()).append(s.breaks().isEmpty() ? "" : "b").append(' ');
        return sb.length() == 0 ? "no moves" : sb.toString().trim();
    }

    private static List<Step> build(Node end) {
        List<Step> steps = new ArrayList<>();
        for (Node n = end; n.parent != null; n = n.parent) steps.add(0, n.step);
        return steps;
    }

    private double heuristic(BlockPos a, BlockPos b) {
        if (targets != null) {
            double best = Double.MAX_VALUE;
            for (BlockPos t : targets) best = Math.min(best, estimate(a, t));
            return best;
        }
        return estimate(a, b);
    }

    private double estimate(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        // Up costs a jump a block, down a short fall (Baritone's way of weighing height).
        double straight = Math.max(0, flat - near) * flatCost + (dy < 0 ? -dy * (JUMP_ONE + 1) : dy * FALL[2] / 2);
        if (guide != null && flat > GUIDE_NEAR) {
            double g = guide.ticks(a.getX(), a.getZ());
            if (g >= 0) return Math.max(straight, 0.85 * g - GUIDE_SLACK);
        }
        return straight;
    }

    private double cost(BlockPos from, Step s) {
        int dx = s.to().getX() - from.getX(), dy = s.to().getY() - from.getY(), dz = s.to().getZ() - from.getZ();
        boolean diagonal = dx != 0 && dz != 0;
        double c;
        if (s.leap()) c = flatCost * Math.max(Math.abs(dx), Math.abs(dz)) + JUMP_ONE + LEAP_EXTRA;
        else if (s.place() != null) {
            boolean pillar = s.place().getY() < s.to().getY() && s.place().getX() == from.getX() && s.place().getZ() == from.getZ();
            c = pillar ? JUMP_ONE + WALK + PLACE_PENALTY : SNEAK + PLACE_PENALTY;
        } else if (inWater(s.to()) || inWater(from)) {
            // Deep water is swum (sprint-swimming); a shallow stream is waded.
            boolean deep = inWater(s.to()) && !solid(s.to().below()) || inWater(s.to().above());
            c = (deep && abilities.sprint() ? SWIM_SPRINT : WADE) * (diagonal ? Math.sqrt(2) : 1) + (dy > 0 ? JUMP_PENALTY : 0);
        } else if (dy > 0) c = Math.max(JUMP_ONE, flatCost) + WALK * 0.5 + JUMP_PENALTY;
        else if (dy < 0 && dx == 0 && dz == 0) c = FALL[Math.min(-dy, FALL.length - 1)] + CENTRE;
        else if (dy < 0) c = WALK_OFF + FALL[Math.min(-dy, FALL.length - 1)] + CENTRE;
        else c = flatCost * (diagonal ? Math.sqrt(2) : 1);
        // Digging while afloat is five times slower (not on the ground), with the head under
        // water five times more.
        // (Every step through lava burns: by far the dearest way, only to get out.)
        if (escapeLava && lava(s.to())) c += 40;
        // Out on magma (only when escaping): it burns at every step too.
        else if (escapeLava && loaded(s.to().below()) && state(s.to().below()).is(Blocks.MAGMA_BLOCK)) c += 20;
        // Along the edge of lava: a slip, a knock-back, and it is in. Kept a block off where it can.
        else if (!escapeLava && besideLava(s.to())) c += LAVA_EDGE;
        double digFactor = (inWater(from) && !solid(from.below()) ? 5 : 1) * (inWater(from.above()) ? 5 : 1);
        for (BlockPos b : s.breaks()) {
            double ticks = breakTicks(state(b));
            // Stone by hand (seven seconds a block, and nothing to show for it): only if there is
            // no other way at all.
            c += ticks * digFactor * (ticks > 100 ? 4 : 1) + BREAK_PENALTY;
            // Sand or gravel on top falls into the gap: every block of the column is dug too.
            BlockPos up = b.above();
            for (int i = 0; i < 12 && state(up).getBlock() instanceof net.minecraft.world.level.block.FallingBlock; i++, up = up.above()) {
                c += breakTicks(state(up)) * digFactor + BREAK_PENALTY + 4;
            }
        }
        return c;
    }

    // ---- moves -----------------------------------------------------------------------------

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** {@code placedBelow}: the step here put a block under the feet (the world does not show it yet). */
    private List<Step> moves(BlockPos p, boolean placedBelow) {
        List<Step> out = new ArrayList<>(12);
        boolean floor = placedBelow || solid(p.below());
        // Water with ground right under the feet (a shallow stream) is walked through, not swum.
        // (Head under water too is swimming, ground or not: up first.)
        boolean swimming = inWater(p) && (!floor || inWater(p.above()));
        for (int[] d : DIRS) {
            boolean diagonal = d[0] != 0 && d[1] != 0;
            BlockPos t = p.offset(d[0], 0, d[1]);
            if (diagonal) {
                // No corner cutting, no digging diagonally.
                if (clear(t) && clear(t.above()) && clear(p.offset(d[0], 0, 0)) && clear(p.offset(d[0], 1, 0))
                        && clear(p.offset(0, 0, d[1])) && clear(p.offset(0, 1, d[1])) && canStand(t)) {
                    out.add(new Step(t, List.of(), null));
                }
                continue;
            }
            // Flat, digging through if needed.
            List<BlockPos> flat = breaksFor(t, t.above());
            if (flat != null && canStand(t)) out.add(new Step(t, flat, null));
            // Bridge: nothing to stand on there, so put a block under it (sneaking at the edge).
            if (abilities.mayPillar() && !swimming && flat != null && flat.isEmpty() && !canStand(t) && (floor || canStand(p))
                    && clear(t.below()) && !inWater(t.below())) {
                out.add(new Step(t, List.of(), t.below()));
            }
            // One up: head room above us, and room at the target one higher.
            BlockPos up = t.above();
            if (!clear(t) || swimming) {
                List<BlockPos> climb = breaksFor(p.above(2), up, up.above());
                if (climb != null && solid(t)) out.add(new Step(up, climb, null));
            }
            // Down: walk off the edge and fall to the first floor below.
            if (clear(t) && clear(t.above()) && !canStand(t)) {
                for (int fall = 1; fall <= 24; fall++) {
                    BlockPos land = t.below(fall);
                    if (!loaded(land)) break;
                    if (inWater(land)) {
                        out.add(new Step(land, List.of(), null));
                        break;
                    }
                    if (!clear(land)) break;
                    if (canStand(land)) {
                        if (fall <= MAX_FALL) out.add(new Step(land, List.of(), null));
                        break;
                    }
                }
            }
        }
        // Leap over a gap one to three wide (sprint-jumping), landing level or one lower.
        if (!swimming && floor && clear(p.above(2))) {
            for (int i = 0; i < 4; i++) {
                int dx = DIRS[i][0], dz = DIRS[i][1];
                BlockPos first = p.offset(dx, 0, dz);
                if (canStand(first) || !clear(first) || !clear(first.above()) || !clear(first.above(2))) continue;
                for (int gap = 1; gap <= 3; gap++) {
                    BlockPos over = p.offset(dx * gap, 0, dz * gap);
                    if (!clear(over) || !clear(over.above()) || !clear(over.above(2)) || canStand(over)) break;
                    BlockPos land = p.offset(dx * (gap + 1), 0, dz * (gap + 1));
                    if (canStand(land) && clear(land.above()) && clear(land.above(2)) && !inWater(land)) {
                        out.add(new Step(land, List.of(), null, true));
                        break;
                    }
                    if (gap < 3 && clear(land) && clear(land.above()) && canStand(land.below()) && !inWater(land.below())) {
                        out.add(new Step(land.below(), List.of(), null, true));
                        break;
                    }
                }
            }
        }
        // Out of deep water where the bank is too high: set a block in the water against the bank
        // (or anything solid) and climb onto it. The search does not know the block is there
        // afterwards, so it ends its leg on it and plans on from there.
        // (With rock over that spot, as in a flooded pit: dug out first.)
        if (swimming && abilities.mayPillar() && clear(p.above()) && !inWater(p.above())) {
            for (int i = 0; i < 4; i++) {
                BlockPos t = p.offset(DIRS[i][0], 0, DIRS[i][1]);
                if (solid(t) || inWater(t.above())) continue;
                if (!inWater(t) && !clear(t)) continue;
                List<BlockPos> room = breaksFor(p.above(2), t.above(), t.above(2));
                if (room == null) continue;
                boolean against = false;
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos n = t.relative(d);
                    if (!n.equals(p) && solid(n) && !room.contains(n)) against = true;
                }
                if (against) out.add(new Step(t.above(), room, t));
            }
        }
        // Straight down: dig the block below and stand on the next one.
        BlockPos below = p.below();
        if (abilities.mayBreak() && breakable(below) && canStand(below) && !liquidAround(below)) {
            out.add(new Step(below, List.of(below), null));
        }
        // ... or through the floor into a hollow under it (a cave below): the block dug, and a
        // short fall to the cave floor.
        else if (abilities.mayBreak() && floor && breakable(below) && !liquidAround(below) && clear(below.below())) {
            for (int fall = 1; fall <= MAX_FALL; fall++) {
                BlockPos land = below.below(fall);
                if (!clear(land) || inWater(land)) break;
                if (canStand(land)) {
                    out.add(new Step(land, List.of(below), null));
                    break;
                }
            }
        }
        // Swim up and down.
        // (Up into the air over the water is no place to be: afloat, the feet stay in the water.)
        if (swimming && (inWater(p.above()) || canStand(p.above())) && clear(p.above())) out.add(new Step(p.above(), List.of(), null));
        if (swimming && inWater(below)) out.add(new Step(below, List.of(), null));
        // Pillar up: jump and put a block where the feet were.
        // (Not standing in water: a jump from there does not get high enough to set a block.)
        if (abilities.mayPillar() && !swimming && floor && (placedBelow || !inWater(p))) {
            List<BlockPos> head = breaksFor(p.above(2));
            if (head != null) out.add(new Step(p.above(), head, p));
        }
        // ... or afloat at the top of deep water with a wall beside: swimming against it lifts the
        // body out far enough (a flooded shaft or pit).
        if (abilities.mayPillar() && swimming && !inWater(p.above()) && clear(p.above())) {
            boolean wall = false, holds = solid(p.below());
            for (int i = 0; i < 4; i++) {
                BlockPos n = p.offset(DIRS[i][0], 0, DIRS[i][1]);
                if (solid(p.above().offset(DIRS[i][0], 0, DIRS[i][1]))) wall = true;
                // (The block needs something to go against: beside it, or a block set beside it first.)
                if (solid(n) || (inWater(n) || clear(n)) && supportable(n)) holds = true;
            }
            List<BlockPos> head = breaksFor(p.above(2));
            if (wall && holds && head != null) out.add(new Step(p.above(), head, p));
        }
        return out;
    }

    /** Whether a block set at {@code p} has something solid to go against. */
    boolean supportable(BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) if (solid(p.relative(d))) return true;
        return false;
    }

    /** The blocks to break so all these become clear, or null if one of them cannot be. */
    private List<BlockPos> breaksFor(BlockPos... cells) {
        List<BlockPos> out = null;
        for (BlockPos c : cells) {
            if (clear(c)) continue;
            if (!abilities.mayBreak() || !breakable(c) || liquidAround(c)) return null;
            if (out == null) out = new ArrayList<>(2);
            out.add(c);
        }
        return out == null ? List.of() : out;
    }

    // ---- the world ------------------------------------------------------------------------------

    private boolean loaded(BlockPos p) {
        return world.loaded(p);
    }

    BlockState state(BlockPos p) {
        long key = p.asLong();
        BlockState s = cache.get(key);
        if (s == null) {
            s = world.state(p);
            cache.put(key, s);
        }
        return s;
    }

    /** Room for a body: no collision and nothing that hurts or holds. */
    /** The search starts in lava: lava is crossed (at a high price) to get out of it. */
    private boolean escapeLava;

    private boolean lava(BlockPos p) {
        return loaded(p) && state(p).getFluidState().is(FluidTags.LAVA);
    }

    /** Extra ticks for a step with lava right beside it (at the feet or a block lower). */
    private static final double LAVA_EDGE = 8;

    private boolean besideLava(BlockPos p) {
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos q = p.relative(d);
            // (At the feet, a block lower, and at the head: a lavafall beside the way.)
            if (lava(q) || lava(q.below()) || lava(q.above())) return true;
        }
        // Lava overhead (a ceiling pocket, the foot of a lavafall): one broken block from a shower.
        return lava(p.above(2)) || lava(p.above(3));
    }

    boolean clear(BlockPos p) {
        if (!loaded(p)) return false;
        BlockState s = state(p);
        if (escapeLava && s.getFluidState().is(FluidTags.LAVA)) return true;
        if (s.getFluidState().is(FluidTags.LAVA) || dangerous(s)) return false;
        return s.getCollisionShape(NO_WORLD, p).isEmpty();
    }

    boolean inWater(BlockPos p) {
        return loaded(p) && state(p).getFluidState().is(FluidTags.WATER);
    }

    private boolean solid(BlockPos p) {
        if (!loaded(p)) return false;
        BlockState s = state(p);
        return !s.getCollisionShape(NO_WORLD, p).isEmpty() && (!dangerous(s) || escapeLava && s.is(Blocks.MAGMA_BLOCK));
    }

    /** Whether feet at {@code p} stand on something (or swim). */
    boolean canStand(BlockPos p) {
        if (inWater(p) || escapeLava && lava(p)) return true;
        BlockPos below = p.below();
        if (!solid(below)) return false;
        BlockState s = state(below);
        return (escapeLava || !s.is(Blocks.MAGMA_BLOCK)) && !s.is(BlockTags.FENCES) && !s.is(BlockTags.WALLS) && !s.is(Blocks.CACTUS);
    }

    private static boolean dangerous(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.COBWEB)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.LAVA)
                // Stalagmites: a fall onto one hurts double and more (dug away instead).
                || s.is(Blocks.POINTED_DRIPSTONE)
                // Traps: the desert temple's plate over the TNT, the jungle temple's tripwires.
                || s.is(BlockTags.PRESSURE_PLATES) || s.is(Blocks.TRIPWIRE);
    }

    boolean breakable(BlockPos p) {
        if (!loaded(p)) return false;
        BlockState s = state(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (s.is(Blocks.TRIPWIRE) || s.is(Blocks.TRIPWIRE_HOOK)) return false; // cutting it without shears sets it off
        float hardness = s.getDestroySpeed(NO_WORLD, p);
        if (hardness < 0 || hardness > 50) return false; // bedrock, obsidian and the like are walls
        return breakTicks(s) < 400;
    }

    /** Breaking here would let a liquid in (from above or a side). */
    private boolean liquidAround(BlockPos p) {
        for (BlockPos q : new BlockPos[]{p.above(), p.north(), p.south(), p.east(), p.west()}) {
            if (loaded(q) && !state(q).getFluidState().isEmpty()) return true;
        }
        return false;
    }
}
