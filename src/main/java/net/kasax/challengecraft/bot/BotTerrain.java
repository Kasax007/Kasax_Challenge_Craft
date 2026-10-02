package net.kasax.challengecraft.bot;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The lie of the land in the large, as a player takes it in from a hilltop: the surface in cells
 * of four by four blocks, read from the height maps of the chunks in sight (ground, water and
 * how deep, lava and other harm, how rough, forest canopy). Over it a cheap search (Dijkstra)
 * gives, for a far goal, what is left of the way from every cell: the fine search ({@link
 * BotPathfinder}) is steered by that instead of the straight line, so it goes round the lake and
 * out of the bay at once rather than into them. The same, from where the bot stands, gives the
 * time to walk to any place on the surface, for the planner (see {@link #travelSeconds}).
 * Only the Overworld and the End (under the Nether's roof the height maps say nothing).
 */
public final class BotTerrain {
    public static final int CELL = 4;
    private static final int PER_CHUNK = 16 / CELL, CELLS = PER_CHUNK * PER_CHUNK;
    /** A chunk read again after this long (blocks dug and placed change little in the large). */
    private static final long REFRESH_TICKS = 1200;
    /** At most this many cells along a side (2048 blocks). */
    private static final int MAX_SIDE = 512;
    static final byte UNKNOWN = 1, HAZARD = 2, CANOPY = 4;

    /** Per chunk, per cell: the ground (feet height), the water's depth there, how rough, what else. */
    private record ChunkCells(short[] ground, byte[] water, byte[] rough, byte[] flags, long at) {
    }

    private final Long2ObjectOpenHashMap<ChunkCells> chunks = new Long2ObjectOpenHashMap<>();
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    /** Extra ticks on cells where a walk got stuck (a cliff the height map does not show). */
    private final Long2DoubleOpenHashMap penalty = new Long2DoubleOpenHashMap();

    /** Whether the large view works here (not under a roof). */
    public static boolean usable(ServerLevel level) {
        return !level.dimensionType().hasCeiling();
    }

    private static long cellKey(int cx, int cz) {
        return ChunkPos.pack(cx, cz);
    }

    /** Somewhere it got stuck: the cells around are believed dearer from now on. */
    public void penalize(BlockPos at, double ticks) {
        int cx = Math.floorDiv(at.getX(), CELL), cz = Math.floorDiv(at.getZ(), CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) penalty.addTo(cellKey(cx + dx, cz + dz), dx == 0 && dz == 0 ? ticks : ticks / 2);
        }
    }

    private ChunkCells read(LevelChunk c, long now) {
        short[] ground = new short[CELLS];
        byte[] water = new byte[CELLS], rough = new byte[CELLS], flags = new byte[CELLS];
        int[] col = new int[CELL * CELL];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x0 = c.getPos().getMinBlockX(), z0 = c.getPos().getMinBlockZ();
        for (int i = 0; i < PER_CHUNK; i++) {
            for (int j = 0; j < PER_CHUNK; j++) {
                int n = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int a = 0; a < CELL; a++) {
                    for (int b = 0; b < CELL; b++) {
                        // (The top block: water counts as ground here, it is walked over by swimming.)
                        int h = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, i * CELL + a, j * CELL + b) + 1;
                        col[n++] = h;
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                    }
                }
                java.util.Arrays.sort(col);
                int k = j * PER_CHUNK + i;
                ground[k] = (short) col[col.length / 2];
                rough[k] = (byte) Math.min(127, hi - lo);
                int lx = i * CELL + CELL / 2, lz = j * CELL + CELL / 2;
                int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                int floor = c.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
                BlockState surface = c.getBlockState(m.set(x0 + lx, top, z0 + lz));
                if (surface.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) water[k] = (byte) Math.min(127, Math.max(1, top - floor));
                int under = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                BlockState g = c.getBlockState(m.set(x0 + lx, under, z0 + lz));
                if (surface.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || harmful(g)) flags[k] |= HAZARD;
                if (c.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz) - under > 3) flags[k] |= CANOPY;
            }
        }
        return new ChunkCells(ground, water, rough, flags, now);
    }

    private static boolean harmful(BlockState s) {
        return s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(net.minecraft.tags.BlockTags.FIRE) || s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    /**
     * The land around {@code center} as far as it sees ({@code radiusChunks}), and on to
     * {@code goal} (unknown country beyond: plain ground, believed somewhat dearer). On the
     * server thread; the searches over it may run anywhere.
     */
    public Grid grid(ServerLevel level, BlockPos center, BlockPos goal, int radiusChunks) {
        if (level.dimension() != dimension) {
            chunks.clear();
            penalty.clear();
            dimension = level.dimension();
        }
        long now = level.getGameTime();
        int r = radiusChunks * PER_CHUNK;
        int ccx = Math.floorDiv(center.getX(), CELL), ccz = Math.floorDiv(center.getZ(), CELL);
        int minX = ccx - r, maxX = ccx + r, minZ = ccz - r, maxZ = ccz + r;
        if (goal != null) {
            int gx = Math.floorDiv(goal.getX(), CELL), gz = Math.floorDiv(goal.getZ(), CELL);
            minX = Math.min(minX, gx - 8);
            maxX = Math.max(maxX, gx + 8);
            minZ = Math.min(minZ, gz - 8);
            maxZ = Math.max(maxZ, gz + 8);
        }
        // (Too wide: the part towards the goal, from here.)
        if (maxX - minX + 1 > MAX_SIDE) {
            if (goal != null && goal.getX() < center.getX()) minX = maxX - MAX_SIDE + 1;
            else maxX = minX + MAX_SIDE - 1;
        }
        if (maxZ - minZ + 1 > MAX_SIDE) {
            if (goal != null && goal.getZ() < center.getZ()) minZ = maxZ - MAX_SIDE + 1;
            else maxZ = minZ + MAX_SIDE - 1;
        }
        // Chunk-aligned, so that each chunk's cells go in whole.
        minX = Math.floorDiv(minX, PER_CHUNK) * PER_CHUNK;
        minZ = Math.floorDiv(minZ, PER_CHUNK) * PER_CHUNK;
        maxX = Math.floorDiv(maxX, PER_CHUNK) * PER_CHUNK + PER_CHUNK - 1;
        maxZ = Math.floorDiv(maxZ, PER_CHUNK) * PER_CHUNK + PER_CHUNK - 1;
        Grid g = new Grid(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
        int refreshed = 0;
        java.util.Arrays.fill(g.flags, UNKNOWN);
        for (int chx = Math.floorDiv(minX, PER_CHUNK); chx <= Math.floorDiv(maxX, PER_CHUNK); chx++) {
            for (int chz = Math.floorDiv(minZ, PER_CHUNK); chz <= Math.floorDiv(maxZ, PER_CHUNK); chz++) {
                long key = ChunkPos.pack(chx, chz);
                ChunkCells cells = chunks.get(key);
                // (Stale ones a few hundred at a time: not the whole view in one tick.)
                if (cells == null || now - cells.at() > REFRESH_TICKS && refreshed < 300) {
                    refreshed++;
                    LevelChunk c = level.getChunkSource().getChunkNow(chx, chz);
                    // (Not loaded: what it saw of it before, if anything.)
                    if (c != null) {
                        cells = read(c, now);
                        chunks.put(key, cells);
                    }
                }
                if (cells == null) continue;
                for (int i = 0; i < PER_CHUNK; i++) {
                    for (int j = 0; j < PER_CHUNK; j++) {
                        int k = j * PER_CHUNK + i;
                        int gi = g.index(chx * PER_CHUNK + i, chz * PER_CHUNK + j);
                        g.ground[gi] = cells.ground()[k];
                        g.water[gi] = cells.water()[k];
                        g.rough[gi] = cells.rough()[k];
                        g.flags[gi] = cells.flags()[k];
                    }
                }
            }
        }
        for (var e : penalty.long2DoubleEntrySet()) {
            int cx = ChunkPos.getX(e.getLongKey()), cz = ChunkPos.getZ(e.getLongKey());
            if (g.inside(cx, cz)) g.extra[g.index(cx, cz)] = (float) e.getDoubleValue();
        }
        return g;
    }

    /** Ticks to walk {@code a}..{@code b} if no field covers it: the straight line at the speed it really makes, about. */
    public static double straightTicks(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) * BotPathfinder.SPRINT;
    }

    /** A snapshot of the land in cells, and the searches over it. */
    public static final class Grid {
        final int x0, z0, w, h;
        final short[] ground;
        final byte[] water, rough, flags;
        final float[] extra;

        Grid(int x0, int z0, int w, int h) {
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            ground = new short[w * h];
            water = new byte[w * h];
            rough = new byte[w * h];
            flags = new byte[w * h];
            extra = new float[w * h];
        }

        boolean inside(int cx, int cz) {
            return cx >= x0 && cz >= z0 && cx < x0 + w && cz < z0 + h;
        }

        int index(int cx, int cz) {
            return (cz - z0) * w + (cx - x0);
        }

        int cellOf(BlockPos p) {
            int cx = Math.floorDiv(p.getX(), CELL), cz = Math.floorDiv(p.getZ(), CELL);
            return inside(cx, cz) ? index(cx, cz) : -1;
        }

        /** Ticks from cell {@code a} into its neighbour {@code b}. */
        double cost(int a, int b, boolean diagonal) {
            double len = diagonal ? Math.sqrt(2) : 1;
            if ((flags[b] & UNKNOWN) != 0 || (flags[a] & UNKNOWN) != 0) return CELL * BotPathfinder.SPRINT * len * 1.3 + extra[b];
            double c = CELL * len * (water[b] >= 2 ? BotPathfinder.SWIM_SPRINT : BotPathfinder.SPRINT);
            int dh = ground[b] - ground[a];
            if (dh > 0) {
                // Up a step at a time (a jump each), or a wall: pillared up (or dug through).
                c += dh * 4.5;
                if (dh > CELL + 1) c += (dh - CELL - 1) * 18;
            } else if (dh < 0) {
                int d = -dh;
                c += d;
                // A drop it cannot just fall down (more than a few blocks, no water to land in):
                // steps dug down, or the long way round.
                if (water[b] == 0 && d > 6) c += (d - 6) * 15;
            }
            if (rough[b] > 3) c += (rough[b] - 3) * 1.5;
            if ((flags[b] & HAZARD) != 0) c += 200;
            if ((flags[b] & CANOPY) != 0) c += 4;
            return c + extra[b];
        }

        /**
         * Dijkstra over the cells from {@code start}: {@code toward} true gives what is left of
         * the way from each cell to {@code start} (the goal); false the way from {@code start}
         * (where it stands) to each cell. Ticks.
         */
        public Field field(BlockPos start, boolean toward) {
            float[] dist = new float[w * h];
            java.util.Arrays.fill(dist, Float.MAX_VALUE);
            int s = cellOf(start);
            if (s < 0) {
                // (Beyond what it knows: the nearest cell towards it, and the straight rest on top.)
                int cx = Math.clamp(Math.floorDiv(start.getX(), CELL), x0, x0 + w - 1);
                int cz = Math.clamp(Math.floorDiv(start.getZ(), CELL), z0, z0 + h - 1);
                s = index(cx, cz);
            }
            LongHeapPriorityQueue open = new LongHeapPriorityQueue(4096);
            dist[s] = 0;
            open.enqueue(((long) Float.floatToIntBits(0f) << 32) | s);
            while (!open.isEmpty()) {
                long e = open.dequeueLong();
                int i = (int) e;
                float d = Float.intBitsToFloat((int) (e >>> 32));
                if (d > dist[i]) continue;
                int ix = i % w, iz = i / w;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = ix + dx, nz = iz + dz;
                        if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                        int n = nz * w + nx;
                        boolean diag = dx != 0 && dz != 0;
                        float nd = (float) (d + (toward ? cost(n, i, diag) : cost(i, n, diag)));
                        if (nd < dist[n]) {
                            dist[n] = nd;
                            open.enqueue(((long) Float.floatToIntBits(nd) << 32) | n);
                        }
                    }
                }
            }
            int sx = s % w + x0, sz = s / w + z0;
            double beyond = Math.sqrt(Math.pow(start.getX() - (sx * CELL + CELL / 2.0), 2) + Math.pow(start.getZ() - (sz * CELL + CELL / 2.0), 2));
            return new Field(this, dist, beyond > CELL * 2 ? beyond * BotPathfinder.SPRINT : 0, start);
        }
    }

    /** Ticks of the way from (or to) one place, for every cell of a {@link Grid}. */
    public static final class Field {
        private final Grid grid;
        private final float[] dist;
        /** The straight bit from the edge of the grid to the place, when it lies beyond. */
        private final double beyond;
        public final BlockPos from;

        Field(Grid grid, float[] dist, double beyond, BlockPos from) {
            this.grid = grid;
            this.dist = dist;
            this.beyond = beyond;
            this.from = from;
        }

        /** Ticks to (or from) the cell of block column x, z; negative when it is outside the grid. */
        public double ticks(int x, int z) {
            int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
            if (!grid.inside(cx, cz)) return -1;
            float d = dist[grid.index(cx, cz)];
            return d == Float.MAX_VALUE ? -1 : d + beyond;
        }

        /**
         * The way through the cells from {@code p} down this field to its place (a field made
         * toward a goal): a point every {@code every} cells, on the ground.
         */
        public List<BlockPos> route(BlockPos p, int every) {
            List<BlockPos> out = new ArrayList<>();
            int i = grid.cellOf(p);
            if (i < 0) return out;
            for (int step = 0; step < grid.w * grid.h && dist[i] > 0; step++) {
                int ix = i % grid.w, iz = i / grid.w, best = i;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = ix + dx, nz = iz + dz;
                        if (nx < 0 || nz < 0 || nx >= grid.w || nz >= grid.h) continue;
                        int n = nz * grid.w + nx;
                        if (dist[n] < dist[best]) best = n;
                    }
                }
                if (best == i) break;
                i = best;
                if (step % every == every - 1 || dist[i] == 0) {
                    out.add(new BlockPos((i % grid.w + grid.x0) * CELL + CELL / 2, grid.ground[i], (i / grid.w + grid.z0) * CELL + CELL / 2));
                }
            }
            return out;
        }
    }

    // ---- travel times for the planner ----------------------------------------------------------

    /**
     * Seconds of walking from {@code from} to {@code to} (flat; height is the caller's): over the
     * land as the large view sees it when the walk starts where the bot is, on the surface, and
     * goes some way; else the straight line at about four blocks a second.
     */
    public static double walkSeconds(Bot bot, BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ(), flat = Math.sqrt(dx * dx + dz * dz);
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = body.blockPosition();
        double fx = from.getX() - feet.getX(), fz = from.getZ() - feet.getZ();
        if (flat < 24 || !usable(level) || fx * fx + fz * fz > 16 * 16 || net.kasax.challengecraft.bot.task.SurfaceTask.underground(body)) return flat / 4.0;
        return bot.navigator().terrain.travelSeconds(level, feet, to);
    }

    private Field fromHere;
    private long fromHereAt;
    /** Real ways against the large view's estimate: how much longer they take (a running mean). */
    private double slowness = 1.4;

    /** A walk is done: how long it really took against what the large view said, learnt. */
    public void walked(double ticks, double estimated) {
        if (estimated < 100 || ticks <= 0) return;
        double r = Math.clamp(ticks / estimated, 0.8, 4);
        slowness = 0.8 * slowness + 0.2 * r;
    }

    /**
     * Seconds to walk from where the bot stands ({@code from}) to {@code to} over the land: by
     * the large view where it reaches (round lakes, over hills), else the straight line; at the
     * speed it really makes. Height up and down is left to the caller.
     */
    public double travelSeconds(ServerLevel level, BlockPos from, BlockPos to) {
        if (!usable(level)) return straightTicks(from, to) * slowness / 20;
        long now = level.getGameTime();
        if (fromHere == null || now - fromHereAt > 200 || level.dimension() != dimension) {
            long t0 = System.nanoTime();
            fromHere = grid(level, from, null, BotWorld.viewChunks(level)).field(from, false);
            fromHereAt = now;
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (ms > 40) BotManager.LOG.info("[Bot] the lie of the land read in {} ms", ms);
        }
        double t = fromHere.ticks(to.getX(), to.getZ());
        if (t < 0) t = straightTicks(from, to) * 1.25;
        return Math.max(t, straightTicks(from, to)) * slowness / 20;
    }
}
