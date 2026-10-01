package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What the bot knows is where, as if it had looked: every loaded chunk around it is read once (a
 * chunk a tick, nearest first) and the blocks worth remembering are kept (logs, ores, gravel,
 * sand, lava pools, crops, hay, ...), per dimension and kept across deaths. The animals it has
 * been near are remembered too. This replaces guessing: "I passed birch trees 60 blocks back" is
 * a walk, not a search.
 */
public final class BotMemory {
    private static final int CAP = 160, RESCAN_TICKS = 6000, ANIMAL_TTL = 6000;

    /** Blocks everywhere (or of no use): not worth remembering. */
    private static final Set<Block> BORING = Set.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.STONE, Blocks.DEEPSLATE,
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.BEDROCK, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF,
            Blocks.NETHERRACK, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.WATER, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.END_STONE, Blocks.KELP_PLANT, Blocks.BUBBLE_COLUMN);

    private static boolean interesting(BlockState s) {
        if (BORING.contains(s.getBlock())) return false;
        if (s.is(BlockTags.LEAVES)) return false;
        if (s.is(Blocks.LAVA)) return s.getFluidState().isSource();
        return true;
    }

    private record Seen(BlockPos pos, long time) {
    }

    private final Map<ResourceKey<Level>, Map<Block, List<BlockPos>>> blocks = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<Long, Long>> scanned = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<EntityType<?>, List<Seen>>> animals = new HashMap<>();
    private int ring, index;
    private long lookedAtMobs;

    public void tick(BotPlayer body) {
        ServerLevel level = (ServerLevel) body.level();
        long now = level.getGameTime();
        BlockPos at = body.blockPosition();
        scanNextChunk(level, at, now);
        // Down in a cave: what it sees around it now (the chunk reading only takes what the open
        // air shows, and what was in sight when that chunk came round).
        if (now - lookedAround >= 40 && level.dimensionType().hasSkyLight() && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at.above()) < 8) {
            lookedAround = now;
            lookAround(level, body.getEyePosition(), at);
        }
        if (now - lookedAtMobs >= 40) {
            lookedAtMobs = now;
            lookAtMobs(level, body, now);
        }
    }

    // ---- blocks ---------------------------------------------------------------------------------

    private long lookedAround;

    private static net.minecraft.world.phys.Vec3 caveEye(BlockPos at) {
        return new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY() + 1.62, at.getZ() + 0.5);
    }

    /** The cave around: the blocks worth remembering in its line of sight, and the cave itself. */
    private void lookAround(ServerLevel level, net.minecraft.world.phys.Vec3 eye, BlockPos at) {
        Map<Block, List<BlockPos>> known = blocks.computeIfAbsent(level.dimension(), k -> new IdentityHashMap<>());
        List<BlockPos> cave = caves.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        boolean nether = level.dimension() == Level.NETHER;
        int r = 16;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (level.getChunkSource().getChunkNow((at.getX() + dx) >> 4, (at.getZ() + dz) >> 4) == null) continue;
                for (int dy = -10; dy <= 10; dy++) {
                    m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir()) {
                        // (A spot to stand in, every fourth: the cave, for the way down.)
                        if (!nether && (m.getX() & 3) == 1 && (m.getZ() & 3) == 1 && (m.getY() & 3) == 2
                                && level.getBlockState(m.above()).isAir() && BotWorld.inSight(level, eye, m, BotWorld.SIGHT)) {
                            BlockPos p = m.immutable();
                            if (!cave.contains(p)) cave.add(p);
                        }
                        continue;
                    }
                    if (!interesting(s)) continue;
                    BlockPos p = m.immutable();
                    if (!BotWorld.inSight(level, eye, p, BotWorld.SIGHT)) continue;
                    List<BlockPos> list = known.get(s.getBlock());
                    if (list != null && list.contains(p)) continue;
                    add(known, s.getBlock(), p, at);
                }
            }
        }
    }

    /** One chunk a tick: the nearest one not read yet (or not for a while), ring by ring. */
    private void scanNextChunk(ServerLevel level, BlockPos at, long now) {
        Map<Long, Long> done = scanned.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int tries = 0; tries < 64; tries++) {
            if (ring > BotWorld.viewChunks(level)) {
                ring = 0;
                index = 0;
            }
            int side = ring * 2 + 1, count = ring == 0 ? 1 : side * 4 - 4;
            if (index >= count) {
                ring++;
                index = 0;
                continue;
            }
            int i = index++;
            int dx, dz;
            if (ring == 0) {
                dx = 0;
                dz = 0;
            } else if (i < side) {
                dx = -ring + i;
                dz = -ring;
            } else if (i < side * 2 - 1) {
                dx = ring;
                dz = -ring + (i - side + 1);
            } else if (i < side * 3 - 2) {
                dx = ring - (i - side * 2 + 2);
                dz = ring;
            } else {
                dx = -ring;
                dz = ring - (i - side * 3 + 3);
            }
            long key = ChunkPos.pack(cx + dx, cz + dz);
            Long when = done.get(key);
            if (when != null && now - when < RESCAN_TICKS) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
            if (chunk == null) continue;
            done.put(key, now);
            scan(level, chunk, at);
            scanCaves(level, chunk, at);
            return;
        }
    }

    private void scan(ServerLevel level, LevelChunk chunk, BlockPos at) {
        Map<Block, List<BlockPos>> known = blocks.computeIfAbsent(level.dimension(), k -> new IdentityHashMap<>());
        net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY() + 1.62, at.getZ() + 0.5);
        // Forget what was there: it is read afresh.
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (List<BlockPos> list : known.values()) list.removeIf(p -> (p.getX() >> 4) == (x0 >> 4) && (p.getZ() >> 4) == (z0 >> 4));
        LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section.hasOnlyAir() || !section.maybeHas(BotMemory::interesting)) continue;
            int y0 = level.getMinY() + si * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (!interesting(s)) continue;
                        BlockPos p = new BlockPos(x0 + x, y0 + y, z0 + z);
                        // Only what can be seen: next to air or water (a cave wall, the ground),
                        // not ore buried in the rock - that is what caves and tunnels are for.
                        // And only from where a player would see it: from the open air, or close
                        // by in its line of sight (not the cave forty blocks under its feet).
                        if (!exposed(level, chunk, p) || !BotWorld.seen(level, p, eye)) continue;
                        add(known, s.getBlock(), p, at);
                    }
                }
            }
        }
    }

    private static boolean exposed(ServerLevel level, LevelChunk chunk, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = p.relative(d);
            BlockState ns;
            if ((n.getX() >> 4) == chunk.getPos().x() && (n.getZ() >> 4) == chunk.getPos().z()) ns = chunk.getBlockState(n);
            else {
                LevelChunk other = level.getChunkSource().getChunkNow(n.getX() >> 4, n.getZ() >> 4);
                if (other == null) continue;
                ns = other.getBlockState(n);
            }
            if (ns.isAir() || !ns.getFluidState().isEmpty() || ns.is(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    /**
     * Caves it knows of: air under the ground (a few blocks below the surface there), one point per
     * 4 x 4 x 4 cell, per dimension. Where to look for ores before digging a tunnel.
     */
    private final Map<ResourceKey<Level>, List<BlockPos>> caves = new HashMap<>();
    private static final int CAVE_CAP = 1200;

    private void scanCaves(ServerLevel level, LevelChunk chunk, BlockPos at) {
        List<BlockPos> list = caves.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        list.removeIf(p -> (p.getX() >> 4) == (x0 >> 4) && (p.getZ() >> 4) == (z0 >> 4));
        boolean nether = level.dimension() == Level.NETHER;
        // Water at the surface, one column in sixteen (rivers, lakes, the sea): where a bucket is
        // filled. (All of it would be far too much to keep.)
        Map<Block, List<BlockPos>> known = blocks.computeIfAbsent(level.dimension(), k -> new IdentityHashMap<>());
        for (int x = 2; x < 16; x += 4) {
            for (int z = 2; z < 16; z += 4) {
                int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos w = new BlockPos(x0 + x, top, z0 + z);
                var fs = chunk.getBlockState(w).getFluidState();
                if (fs.isSource() && fs.is(net.minecraft.tags.FluidTags.WATER)) add(known, Blocks.WATER, w, at);
            }
        }
        for (int x = 1; x < 16; x += 4) {
            for (int z = 1; z < 16; z += 4) {
                int top = nether ? level.getMaxY() : chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 6;
                for (int y = level.getMinY() + 6; y < top; y += 4) {
                    BlockPos p = new BlockPos(x0 + x, y, z0 + z);
                    BlockState st = chunk.getBlockState(p);
                    // Room to stand: air here and above, something under it.
                    if (!st.isAir() || !chunk.getBlockState(p.above()).isAir()) continue;
                    if (nether) continue; // (the Nether is one big cave: no use)
                    // A cave it can know of: its mouth (lit by the sky), or the one it is in.
                    if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, p) == 0
                            && !BotWorld.inSight(level, caveEye(at), p, BotWorld.SIGHT)) continue;
                    list.add(p);
                }
            }
        }
        if (list.size() > CAVE_CAP * 2) {
            list.sort((a, b) -> Double.compare(a.distSqr(at), b.distSqr(at)));
            list.subList(CAVE_CAP, list.size()).clear();
        }
    }

    /**
     * The best cave point to look in next: near, and near the height {@code wantY} (where the ore
     * is common), none of {@code visited} within 10 blocks. Null when it knows of no such cave.
     */
    public BlockPos cave(ServerLevel level, BlockPos from, int wantY, double maxDistance, List<BlockPos> visited) {
        List<BlockPos> list = caves.get(level.dimension());
        if (list == null) return null;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : list) {
            // Only ones that get it a good way nearer the height it wants.
            if (Math.abs(p.getY() - wantY) + 8 >= Math.abs(from.getY() - wantY)) continue;
            double d = Math.sqrt(p.distSqr(from));
            if (d > maxDistance) continue;
            double score = d + 0.7 * Math.abs(p.getY() - wantY);
            if (score >= bestScore) continue;
            boolean seen = false;
            for (BlockPos v : visited) if (v.distSqr(p) < 100) { seen = true; break; }
            if (seen) continue;
            best = p;
            bestScore = score;
        }
        return best;
    }

    private static void add(Map<Block, List<BlockPos>> known, Block block, BlockPos p, BlockPos at) {
        List<BlockPos> list = known.computeIfAbsent(block, k -> new ArrayList<>());
        list.add(p);
        if (list.size() > CAP * 2) {
            // Keep the nearest (to where the bot is now).
            list.sort((a, b) -> Double.compare(a.distSqr(at), b.distSqr(at)));
            list.subList(CAP, list.size()).clear();
        }
    }

    /**
     * The nearest remembered block that matches (checked against the world where it is loaded:
     * gone ones are forgotten), not in {@code skip}; null when it knows of none.
     */
    public BlockPos nearest(ServerLevel level, BlockPos from, Predicate<BlockState> match, Set<BlockPos> skip) {
        Map<Block, List<BlockPos>> known = blocks.get(level.dimension());
        if (known == null) return null;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<Block, List<BlockPos>> e : known.entrySet()) {
            if (!match.test(e.getKey().defaultBlockState()) && !matchesAnyState(e.getKey(), match)) continue;
            for (Iterator<BlockPos> it = e.getValue().iterator(); it.hasNext(); ) {
                BlockPos p = it.next();
                double d = p.distSqr(from);
                if (d >= bestD || skip.contains(p)) continue;
                if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) != null && !match.test(level.getBlockState(p))) {
                    it.remove();
                    continue;
                }
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    private static boolean matchesAnyState(Block block, Predicate<BlockState> match) {
        for (BlockState s : block.getStateDefinition().getPossibleStates()) if (match.test(s)) return true;
        return false;
    }

    /** How many of this block it knows of in the dimension (for the planner: common or rare here). */
    public int count(ResourceKey<Level> dimension, Block block) {
        Map<Block, List<BlockPos>> known = blocks.get(dimension);
        List<BlockPos> list = known == null ? null : known.get(block);
        return list == null ? 0 : list.size();
    }

    /** The nearest known position of each remembered block (for the planner's costs). */
    public Map<Block, BlockPos> nearestOfEach(ResourceKey<Level> dimension, BlockPos from) {
        Map<Block, BlockPos> out = new IdentityHashMap<>();
        Map<Block, List<BlockPos>> known = blocks.get(dimension);
        if (known == null) return out;
        for (Map.Entry<Block, List<BlockPos>> e : known.entrySet()) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : e.getValue()) {
                double d = p.distSqr(from);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            if (best != null) out.put(e.getKey(), best);
        }
        return out;
    }

    // ---- animals --------------------------------------------------------------------------------

    private void lookAtMobs(ServerLevel level, BotPlayer body, long now) {
        Map<EntityType<?>, List<Seen>> known = animals.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        for (List<Seen> list : known.values()) list.removeIf(s -> now - s.time() > ANIMAL_TTL);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(body.blockPosition()).inflate(BotWorld.MOB_SIGHT),
                e -> e.isAlive() && !(e instanceof Player) && BotWorld.seesMob(body, e))) {
            List<Seen> list = known.computeIfAbsent(e.getType(), k -> new ArrayList<>());
            BlockPos p = e.blockPosition();
            list.removeIf(s -> s.pos().distSqr(p) < 64);
            list.add(new Seen(p, now));
            if (list.size() > 24) list.remove(0);
        }
    }

    /** Where it last saw an animal of this kind (the herd is likely still about), or null. */
    public BlockPos lastSeen(ServerLevel level, EntityType<?> type, BlockPos from) {
        Map<EntityType<?>, List<Seen>> known = animals.get(level.dimension());
        List<Seen> list = known == null ? null : known.get(type);
        if (list == null || list.isEmpty()) return null;
        Seen best = null;
        for (Seen s : list) if (best == null || s.pos().distSqr(from) < best.pos().distSqr(from)) best = s;
        return best.pos();
    }

    /** For the status command: how much it knows. */
    public String summary(ResourceKey<Level> dimension) {
        Map<Block, List<BlockPos>> known = blocks.get(dimension);
        Map<Long, Long> done = scanned.get(dimension);
        List<BlockPos> c = caves.get(dimension);
        return (done == null ? 0 : done.size()) + " chunks read, " + (known == null ? 0 : known.size()) + " kinds of block remembered, "
                + (c == null ? 0 : c.size()) + " cave spots";
    }
}
