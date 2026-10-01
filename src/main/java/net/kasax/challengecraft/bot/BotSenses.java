package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the bot has noticed about its surroundings, the way a player takes in the landscape: the
 * biomes in view (sampled on the surface around it, and the cave biome it is in), structures it
 * can see (the parts that stand out of the ground, or the one it is right next to), and loot
 * chests in those structures that nobody has opened yet. Nothing buried far away: no X-ray.
 *
 * <p>Looked at every few seconds; kept across deaths (it is the bot's memory, not the body's).
 */
public final class BotSenses {
    /** A seen structure: which one, a spot in one of its parts, and that part's box. */
    public record SeenStructure(Identifier id, BlockPos spot, BoundingBox box) {
    }

    private static final int VIEW = 96, STEP = 8, NEAR = 20, STRUCTURE_VIEW = 160;

    /** The nearest known spot of each biome and structure in this dimension (from the atlas, as of the last look). */
    private final Map<Identifier, BlockPos> biomes = new HashMap<>();
    private final Map<Identifier, SeenStructure> structures = new HashMap<>();
    /**
     * The atlas: everything it has seen, per dimension, however far it has gone since (a player
     * remembers the village a thousand blocks back, the desert on the way, the fortress below).
     * A few spots per biome, a few of each structure.
     */
    private final Map<Identifier, Map<Identifier, List<BlockPos>>> biomeAtlas = new HashMap<>();
    private final Map<Identifier, Map<Identifier, List<SeenStructure>>> structureAtlas = new HashMap<>();
    private static final int ATLAS_PER_KIND = 8, BIOME_SPACING = 96, STRUCTURE_SPACING = 48;
    private final List<BlockPos> lootables = new ArrayList<>();
    private final Set<BlockPos> looted = new HashSet<>();
    private long lookedAt = -10_000;
    private Identifier dimension;
    /** Portals it went through, per dimension: the way back, however far it walked. */
    private final Map<Identifier, List<BlockPos>> portals = new HashMap<>();
    private BlockPos lastPos;
    private boolean wasInPortal;

    public void tick(BotPlayer body) {
        ServerLevel level = (ServerLevel) body.level();
        long now = level.getGameTime();
        Identifier dim = level.dimension().identifier();
        if (!dim.equals(dimension)) {
            // Through a portal (or respawned): remember both ends if it was a portal.
            // (It may already have stepped out of the frame: the nearest portal block about.)
            if (dimension != null && lastPos != null) {
                BlockPos here = net.kasax.challengecraft.bot.BotWorld.nearest(level, body.blockPosition(), 6, 4,
                        st -> st.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL), false, java.util.Set.of());
                // (Not found about, odd as that is: where it came out is where the portal is.)
                // (A portal crossing: it stood in a portal a moment ago on the other side. Not a
                // respawn at home.)
                BotManager.LOG.info("[Bot] crossed from {} at {} (in a portal: {}) to {} at {}, portal here: {}", dimension.getPath(), lastPos.toShortString(),
                        wasInPortal, dim.getPath(), body.blockPosition().toShortString(), here == null ? "none" : here.toShortString());
                if (here == null && wasInPortal && level.dimension() != net.minecraft.world.level.Level.END) {
                    here = body.blockPosition();
                    BotManager.LOG.info("[Bot] came out at {} but saw no portal there; remembering the spot", here.toShortString());
                }
                if (here != null) {
                    remember(dim, here);
                    ServerLevel old = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
                    BlockPos there = old == null ? null : net.kasax.challengecraft.bot.BotWorld.nearest(old, lastPos, 6, 4,
                            st -> st.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL), false, java.util.Set.of());
                    remember(dimension, there != null ? there : lastPos);
                }
            }
            dimension = dim;
            lootables.clear();
            lookedAt = -10_000;
        }
        lastPos = body.blockPosition();
        wasInPortal = level.getBlockState(lastPos).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                || level.getBlockState(lastPos.above()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL);
        // Standing in a portal (on the way in, and again on coming out at the other end): both
        // ends remembered that way, whatever tick the game moves it on.
        if (level.getBlockState(lastPos).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) remember(dim, lastPos);
        if (now - lookedAt < 100) return;
        lookedAt = now;
        look(body, level);
    }

    private void look(BotPlayer body, ServerLevel level) {
        BlockPos c = body.blockPosition();
        // The landscape: the surface around, as far as one sees.
        for (int dx = -VIEW; dx <= VIEW; dx += STEP) {
            for (int dz = -VIEW; dz <= VIEW; dz += STEP) {
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                note(level, p, c);
            }
        }
        // Underground: the cave around the bot.
        for (int dx = -24; dx <= 24; dx += STEP) {
            for (int dz = -24; dz <= 24; dz += STEP) {
                BlockPos p = c.offset(dx, 0, dz);
                if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) != null) note(level, p, c);
            }
        }

        // Structures: what it has a line of sight to within its view (a player spots the shipwreck's
        // mast, the village roofs, the ruined portal's obsidian), and what it is right next to.
        // Once seen, remembered.
        lootables.clear();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        net.minecraft.world.phys.Vec3 eye = body.getEyePosition();
        int view = Math.min(STRUCTURE_VIEW, level.getServer().getPlayerList().getViewDistance() * 16);
        int r = view >> 4;
        for (int cx = (c.getX() >> 4) - r; cx <= (c.getX() >> 4) + r; cx++) {
            for (int cz = (c.getZ() >> 4) - r; cz <= (c.getZ() >> 4) + r; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (Map.Entry<net.minecraft.world.level.levelgen.structure.Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                    StructureStart start = e.getValue();
                    if (start == null || !start.isValid()) continue;
                    Identifier id = registry.getKey(e.getKey());
                    if (id == null) continue;
                    int checked = 0;
                    for (StructurePiece piece : start.getPieces()) {
                        if (checked++ > 10) break;
                        BoundingBox box = piece.getBoundingBox();
                        if (distance(box, c) > view) continue;
                        BlockPos centre = box.getCenter();
                        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
                        // Within view distance it is known (as a player would spot it; no line of sight needed).
                        BlockPos spot = new BlockPos(centre.getX(), Math.max(box.minY(), Math.min(box.maxY(), surface)), centre.getZ());
                        addStructure(new SeenStructure(id, spot, box), c);
                    }
                }
            }
        }
        // Loot chests in what it sees (or right next to it) that nobody opened yet.
        for (int cx = (c.getX() >> 4) - r; cx <= (c.getX() >> 4) + r; cx++) {
            for (int cz = (c.getZ() >> 4) - r; cz <= (c.getZ() >> 4) + r; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof RandomizableContainerBlockEntity rc) || rc.getLootTable() == null) continue;
                    BlockPos p = be.getBlockPos();
                    if (looted.contains(p)) continue;
                    boolean seen = p.distSqr(c) < NEAR * NEAR || structures.values().stream().anyMatch(s -> s.box().isInside(p));
                    if (seen) lootables.add(p.immutable());
                }
            }
        }
        lootables.sort((a, b) -> Double.compare(a.distSqr(c), b.distSqr(c)));
        nearestFrom(c);
    }

    /** The nearest known of each kind, from {@code c}, out of the atlas of this dimension. */
    private void nearestFrom(BlockPos c) {
        biomes.clear();
        structures.clear();
        biomeAtlas.getOrDefault(dimension, Map.of()).forEach((id, spots) ->
                spots.stream().min((a, b) -> Double.compare(a.distSqr(c), b.distSqr(c))).ifPresent(p -> biomes.put(id, p)));
        structureAtlas.getOrDefault(dimension, Map.of()).forEach((id, seen) ->
                seen.stream().min((a, b) -> Double.compare(a.spot().distSqr(c), b.spot().distSqr(c))).ifPresent(st -> structures.put(id, st)));
    }

    private void addStructure(SeenStructure st, BlockPos from) {
        List<SeenStructure> list = structureAtlas.computeIfAbsent(dimension, k -> new HashMap<>()).computeIfAbsent(st.id(), k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            // The same one again: the part nearer to where it stands is the one to go to.
            if (list.get(i).spot().distSqr(st.spot()) < STRUCTURE_SPACING * STRUCTURE_SPACING) {
                if (st.spot().distSqr(from) < list.get(i).spot().distSqr(from)) list.set(i, st);
                return;
            }
        }
        addCapped(list, st, from, x -> x.spot());
    }

    /** Into a capped list: when full, in place of the one furthest from here. */
    private static <T> void addCapped(List<T> list, T item, BlockPos from, java.util.function.Function<T, BlockPos> at) {
        if (list.size() < ATLAS_PER_KIND) {
            list.add(item);
            return;
        }
        int far = 0;
        for (int i = 1; i < list.size(); i++) if (at.apply(list.get(i)).distSqr(from) > at.apply(list.get(far)).distSqr(from)) far = i;
        if (at.apply(item).distSqr(from) < at.apply(list.get(far)).distSqr(from)) list.set(far, item);
    }

    /** Everything known of a structure kind in this dimension, nearest to {@code from} first. */
    public List<SeenStructure> structuresOf(Identifier id, BlockPos from) {
        List<SeenStructure> out = new ArrayList<>(structureAtlas.getOrDefault(dimension, Map.of()).getOrDefault(id, List.of()));
        out.sort((a, b) -> Double.compare(a.spot().distSqr(from), b.spot().distSqr(from)));
        return out;
    }

    /**
     * A clear line from the eyes to the top or the middle of a structure part: the first block
     * the line hits is part of it (or nothing is in the way).
     */
    private static boolean inSight(ServerLevel level, BotPlayer body, net.minecraft.world.phys.Vec3 eye, BoundingBox box) {
        BlockPos centre = box.getCenter();
        for (net.minecraft.world.phys.Vec3 target : new net.minecraft.world.phys.Vec3[]{
                new net.minecraft.world.phys.Vec3(centre.getX() + 0.5, box.maxY() + 0.5, centre.getZ() + 0.5),
                net.minecraft.world.phys.Vec3.atCenterOf(centre)}) {
            var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, target, net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, body));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || box.isInside(hit.getBlockPos())
                    || hit.getLocation().distanceTo(target) < 1.5) return true;
        }
        return false;
    }

    private void note(ServerLevel level, BlockPos p, BlockPos from) {
        level.getBiome(p).unwrapKey().ifPresent(k -> {
            List<BlockPos> spots = biomeAtlas.computeIfAbsent(dimension, d -> new HashMap<>()).computeIfAbsent(k.identifier(), d -> new ArrayList<>());
            for (int i = 0; i < spots.size(); i++) {
                // Near one it knows: whichever is nearer to here stands for that stretch.
                if (spots.get(i).distSqr(p) < BIOME_SPACING * BIOME_SPACING) {
                    if (p.distSqr(from) < spots.get(i).distSqr(from)) spots.set(i, p.immutable());
                    return;
                }
            }
            addCapped(spots, p.immutable(), from, x -> x);
        });
    }

    private static double distance(BoundingBox box, BlockPos p) {
        double dx = Math.max(0, Math.max(box.minX() - p.getX(), p.getX() - box.maxX()));
        double dy = Math.max(0, Math.max(box.minY() - p.getY(), p.getY() - box.maxY()));
        double dz = Math.max(0, Math.max(box.minZ() - p.getZ(), p.getZ() - box.maxZ()));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void remember(Identifier dim, BlockPos p) {
        List<BlockPos> list = portals.computeIfAbsent(dim, k -> new ArrayList<>());
        if (list.stream().noneMatch(q -> q.distSqr(p) < 16)) {
            list.add(p.immutable());
            BotManager.LOG.info("[Bot] remembered a portal in {} at {}", dim.getPath(), p.toShortString());
        }
    }

    /** The nearest portal it knows of in the dimension it is in (it went through it), or null. */
    public BlockPos knownPortal(BlockPos from) {
        List<BlockPos> list = portals.getOrDefault(dimension, List.of());
        return list.stream().min((a, b) -> Double.compare(a.distSqr(from), b.distSqr(from))).orElse(null);
    }

    /** The nearest seen spot of this biome, or null. */
    public BlockPos biome(Identifier id) {
        return biomes.get(id);
    }

    public Map<Identifier, BlockPos> biomes() {
        return biomes;
    }

    public SeenStructure structure(Identifier id) {
        return structures.get(id);
    }

    public Map<Identifier, SeenStructure> structures() {
        return structures;
    }

    /** Unopened loot chests it knows of, nearest first. */
    /** The known structure (its id) a spot is inside of, or null. */
    public Identifier structureAt(BlockPos p) {
        for (List<SeenStructure> list : structureAtlas.getOrDefault(dimension, Map.of()).values())
            for (SeenStructure st : list) if (st.box().isInside(p)) return st.id();
        return null;
    }

    public List<BlockPos> lootables() {
        return lootables;
    }

    public void markLooted(BlockPos p) {
        looted.add(p.immutable());
        lootables.remove(p);
    }

    /** Look again at the next tick (after a long walk, a respawn). */
    public void refresh() {
        lookedAt = -10_000;
    }
}
