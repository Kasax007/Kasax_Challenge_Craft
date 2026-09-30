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

    private final Map<Identifier, BlockPos> biomes = new HashMap<>();
    private final Map<Identifier, SeenStructure> structures = new HashMap<>();
    private final List<BlockPos> lootables = new ArrayList<>();
    private final Set<BlockPos> looted = new HashSet<>();
    private long lookedAt = -10_000;
    private Identifier dimension;
    /** Portals it went through, per dimension: the way back, however far it walked. */
    private final Map<Identifier, List<BlockPos>> portals = new HashMap<>();
    private BlockPos lastPos;

    public void tick(BotPlayer body) {
        ServerLevel level = (ServerLevel) body.level();
        long now = level.getGameTime();
        Identifier dim = level.dimension().identifier();
        if (!dim.equals(dimension)) {
            // Through a portal (or respawned): remember both ends if it was a portal.
            if (dimension != null && lastPos != null && level.getBlockState(body.blockPosition()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
                remember(dimension, lastPos);
                remember(dim, body.blockPosition());
            }
            dimension = dim;
            biomes.clear();
            structures.clear();
            lootables.clear();
            lookedAt = -10_000;
        }
        lastPos = body.blockPosition();
        if (now - lookedAt < 100) return;
        lookedAt = now;
        look(body, level);
    }

    private void look(BotPlayer body, ServerLevel level) {
        BlockPos c = body.blockPosition();
        biomes.clear();
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
                        SeenStructure known = structures.get(id);
                        if (known == null || spot.distSqr(c) < known.spot().distSqr(c)) structures.put(id, new SeenStructure(id, spot, box));
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
            BlockPos known = biomes.get(k.identifier());
            if (known == null || p.distSqr(from) < known.distSqr(from)) biomes.put(k.identifier(), p);
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
        if (list.stream().noneMatch(q -> q.distSqr(p) < 16)) list.add(p.immutable());
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
        for (SeenStructure st : structures.values()) if (st.box().isInside(p)) return st.id();
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
