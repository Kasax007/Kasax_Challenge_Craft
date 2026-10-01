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
    private static final int RADIUS_CHUNKS = 8, CAP = 160, RESCAN_TICKS = 6000, ANIMAL_TTL = 6000;

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
        if (now - lookedAtMobs >= 40) {
            lookedAtMobs = now;
            lookAtMobs(level, body, now);
        }
    }

    // ---- blocks ---------------------------------------------------------------------------------

    /** One chunk a tick: the nearest one not read yet (or not for a while), ring by ring. */
    private void scanNextChunk(ServerLevel level, BlockPos at, long now) {
        Map<Long, Long> done = scanned.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int tries = 0; tries < 64; tries++) {
            if (ring > RADIUS_CHUNKS) {
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
            return;
        }
    }

    private void scan(ServerLevel level, LevelChunk chunk, BlockPos at) {
        Map<Block, List<BlockPos>> known = blocks.computeIfAbsent(level.dimension(), k -> new IdentityHashMap<>());
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
                        add(known, s.getBlock(), new BlockPos(x0 + x, y0 + y, z0 + z), at);
                    }
                }
            }
        }
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
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(body.blockPosition()).inflate(64),
                e -> e.isAlive() && !(e instanceof Player))) {
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
        return (done == null ? 0 : done.size()) + " chunks read, " + (known == null ? 0 : known.size()) + " kinds of block remembered";
    }
}
