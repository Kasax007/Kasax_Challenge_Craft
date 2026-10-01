package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** What the search may assume about the bot's abilities. */
    public record Abilities(boolean mayBreak, boolean mayPillar, BotTools tools) {
    }

    private static final int MAX_FALL = 3;
    private static final double WALK = 4.6, DIAGONAL = 6.5, JUMP_UP = 7.0, FALL_PER_BLOCK = 1.5,
            SWIM = 9.0, PILLAR = 14.0, BRIDGE = 12.0, DIG_EXTRA = 2.0, LEAP = 6.0;

    private final ServerLevel level;
    private final Abilities abilities;
    private final Map<Long, BlockState> cache = new HashMap<>();

    /** Spots where a step already failed: kept out of the search (as Baritone blacklists them). */
    private final java.util.Set<Long> avoid;

    public BotPathfinder(ServerLevel level, Abilities abilities) {
        this(level, abilities, java.util.Set.of());
    }

    public BotPathfinder(ServerLevel level, Abilities abilities, java.util.Set<Long> avoid) {
        this.level = level;
        this.abilities = abilities;
        this.avoid = avoid;
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

    /**
     * A path from {@code start} to a position {@code goal} accepts; {@code target} steers the
     * search. Returns null when no step at all brings the bot closer.
     */
    public List<Step> find(BlockPos start, Predicate<BlockPos> goal, BlockPos target, int budget) {
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        Node first = new Node(start);
        first.f = heuristic(start, target);
        nodes.put(start.asLong(), first);
        open.add(first);
        Node best = first;
        double bestH = first.f;
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            Node n = open.poll();
            if (n.closed) continue;
            n.closed = true;
            expanded++;
            if (goal.test(n.pos)) return build(n);
            double h = heuristic(n.pos, target);
            if (h < bestH) {
                bestH = h;
                best = n;
            }
            for (Step s : moves(n.pos, n.step != null && n.step.place() != null && n.step.place().equals(n.pos.below()))) {
                double cost = cost(n.pos, s) + (avoid.contains(s.to().asLong()) ? 400 : 0);
                Node m = nodes.computeIfAbsent(s.to().asLong(), k -> new Node(s.to()));
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
        return best == first ? null : build(best);
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

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) * WALK + Math.abs(dy) * WALK;
    }

    private double cost(BlockPos from, Step s) {
        int dx = s.to().getX() - from.getX(), dy = s.to().getY() - from.getY(), dz = s.to().getZ() - from.getZ();
        double c;
        if (s.leap()) c = WALK * Math.max(Math.abs(dx), Math.abs(dz)) + LEAP;
        else if (s.place() != null) c = s.place().getY() < s.to().getY() && s.place().getX() == from.getX() && s.place().getZ() == from.getZ() ? PILLAR : BRIDGE;
        else if (inWater(s.to()) || inWater(from)) c = SWIM;
        else if (dy > 0) c = JUMP_UP;
        else if (dy < 0 && dx == 0 && dz == 0) c = WALK;
        else if (dy < 0) c = WALK + FALL_PER_BLOCK * -dy;
        else c = dx != 0 && dz != 0 ? DIAGONAL : WALK;
        // Digging while afloat is five times slower (not on the ground), with the head under
        // water five times more.
        double digFactor = (inWater(from) && !solid(from.below()) ? 5 : 1) * (inWater(from.above()) ? 5 : 1);
        for (BlockPos b : s.breaks()) {
            double ticks = abilities.tools().breakTicks(state(b));
            // Stone by hand (seven seconds a block, and nothing to show for it): only if there is
            // no other way at all.
            c += ticks * digFactor * (ticks > 100 ? 4 : 1) + DIG_EXTRA;
            // Sand or gravel on top falls into the gap: every block of the column is dug too.
            BlockPos up = b.above();
            for (int i = 0; i < 12 && state(up).getBlock() instanceof net.minecraft.world.level.block.FallingBlock; i++, up = up.above()) {
                c += abilities.tools().breakTicks(state(up)) * digFactor + DIG_EXTRA + 4;
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
        return p.getY() >= level.getMinY() && p.getY() < level.getMaxY()
                && level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) != null;
    }

    BlockState state(BlockPos p) {
        long key = p.asLong();
        BlockState s = cache.get(key);
        if (s == null) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            s = chunk == null || p.getY() < level.getMinY() || p.getY() >= level.getMaxY()
                    ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(p);
            cache.put(key, s);
        }
        return s;
    }

    /** Room for a body: no collision and nothing that hurts or holds. */
    boolean clear(BlockPos p) {
        if (!loaded(p)) return false;
        BlockState s = state(p);
        if (s.getFluidState().is(FluidTags.LAVA) || dangerous(s)) return false;
        return s.getCollisionShape(level, p).isEmpty();
    }

    boolean inWater(BlockPos p) {
        return loaded(p) && state(p).getFluidState().is(FluidTags.WATER);
    }

    private boolean solid(BlockPos p) {
        if (!loaded(p)) return false;
        BlockState s = state(p);
        return !s.getCollisionShape(level, p).isEmpty() && !dangerous(s);
    }

    /** Whether feet at {@code p} stand on something (or swim). */
    boolean canStand(BlockPos p) {
        if (inWater(p)) return true;
        BlockPos below = p.below();
        if (!solid(below)) return false;
        BlockState s = state(below);
        return !s.is(Blocks.MAGMA_BLOCK) && !s.is(BlockTags.FENCES) && !s.is(BlockTags.WALLS) && !s.is(Blocks.CACTUS);
    }

    private static boolean dangerous(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.COBWEB)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.LAVA)
                // Traps: the desert temple's plate over the TNT, the jungle temple's tripwires.
                || s.is(BlockTags.PRESSURE_PLATES) || s.is(Blocks.TRIPWIRE);
    }

    boolean breakable(BlockPos p) {
        if (!loaded(p)) return false;
        BlockState s = state(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (s.is(Blocks.TRIPWIRE) || s.is(Blocks.TRIPWIRE_HOOK)) return false; // cutting it without shears sets it off
        float hardness = s.getDestroySpeed(level, p);
        if (hardness < 0 || hardness > 50) return false; // bedrock, obsidian and the like are walls
        return abilities.tools().breakTicks(s) < 400;
    }

    /** Breaking here would let a liquid in (from above or a side). */
    private boolean liquidAround(BlockPos p) {
        for (BlockPos q : new BlockPos[]{p.above(), p.north(), p.south(), p.east(), p.west()}) {
            if (loaded(q) && !state(q).getFluidState().isEmpty()) return true;
        }
        return false;
    }
}
