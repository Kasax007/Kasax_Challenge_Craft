package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a bot can find around it. By default it only "sees" blocks a player could see: blocks on
 * view from the open air (the surface, a ravine, a cave mouth: lit by the sky), and blocks in its
 * line of sight close by (the cave it is in). Ores in caves it never went into, a lake under the
 * hill, are found as a player finds them: by going there - no X-ray.
 */
public final class BotWorld {
    private BotWorld() {
    }

    /** The nearest matching block within {@code radius} (vertical ±{@code height}), or null. */
    public static BlockPos nearest(ServerLevel level, BlockPos center, int radius, int height, Predicate<BlockState> match,
                                   boolean exposedOnly, Set<BlockPos> ignore) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(center.getX() + 0.5, center.getY() + 1.62, center.getZ() + 0.5);
        int cx0 = (center.getX() - radius) >> 4, cx1 = (center.getX() + radius) >> 4;
        int cz0 = (center.getZ() - radius) >> 4, cz1 = (center.getZ() + radius) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) continue;
                int x0 = Math.max(cx << 4, center.getX() - radius), x1 = Math.min((cx << 4) + 15, center.getX() + radius);
                int z0 = Math.max(cz << 4, center.getZ() - radius), z1 = Math.min((cz << 4) + 15, center.getZ() + radius);
                int y0 = Math.max(level.getMinY(), center.getY() - height), y1 = Math.min(level.getMaxY() - 1, center.getY() + height);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int y = y0; y <= y1; y++) {
                            m.set(x, y, z);
                            BlockState s = level.getBlockState(m);
                            if (s.isAir() || !match.test(s)) continue;
                            double d = m.distSqr(center);
                            if (d >= bestD || ignore.contains(m)) continue;
                            if (exposedOnly && !(COMMON.contains(s.getBlock()) ? exposed(level, m) : seen(level, m, eye))) continue;
                            bestD = d;
                            best = m.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Rock and soil: wherever it digs there is some, no need to have seen it. */
    public static final Set<net.minecraft.world.level.block.Block> COMMON = Set.of(net.minecraft.world.level.block.Blocks.STONE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE, net.minecraft.world.level.block.Blocks.DIRT, net.minecraft.world.level.block.Blocks.GRASS_BLOCK,
            net.minecraft.world.level.block.Blocks.NETHERRACK, net.minecraft.world.level.block.Blocks.ANDESITE, net.minecraft.world.level.block.Blocks.DIORITE,
            net.minecraft.world.level.block.Blocks.GRANITE, net.minecraft.world.level.block.Blocks.TUFF, net.minecraft.world.level.block.Blocks.GRAVEL,
            net.minecraft.world.level.block.Blocks.BEDROCK, net.minecraft.world.level.block.Blocks.END_STONE, net.minecraft.world.level.block.Blocks.BLACKSTONE,
            net.minecraft.world.level.block.Blocks.BASALT, net.minecraft.world.level.block.Blocks.SOUL_SAND, net.minecraft.world.level.block.Blocks.SOUL_SOIL);

    /** How far it makes out a block in a cave around it (a torch-less player sees about that far). */
    public static final double SIGHT = 24;

    /**
     * Whether a player standing with its eyes at {@code eye} could have seen this block: on view
     * from the open air, or in its line of sight close by. (Null eye: from the open air only.)
     */
    public static boolean seen(ServerLevel level, BlockPos p, net.minecraft.world.phys.Vec3 eye) {
        return skyVisible(level, p) || eye != null && inSight(level, eye, p, SIGHT);
    }

    /** An open side (air, water) lit by the sky: in view from the surface, a ravine, a cave mouth. */
    public static boolean skyVisible(ServerLevel level, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = p.relative(d);
            if (open(level, n) && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, n) > 0) return true;
        }
        return false;
    }

    /** One of its open faces in a straight line from {@code eye}, within {@code range}. */
    public static boolean inSight(ServerLevel level, net.minecraft.world.phys.Vec3 eye, BlockPos p, double range) {
        net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(p);
        if (c.distanceToSqr(eye) > range * range) return false;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = p.relative(d);
            if (!open(level, n)) continue;
            // (Only faces turned towards the eye can be seen.)
            net.minecraft.world.phys.Vec3 face = c.add(d.getStepX() * 0.51, d.getStepY() * 0.51, d.getStepZ() * 0.51);
            if (face.subtract(c).dot(eye.subtract(c)) <= 0 && !n.equals(net.minecraft.core.BlockPos.containing(eye))) continue;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, face, net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(p)) return true;
        }
        return false;
    }

    private static boolean open(ServerLevel level, BlockPos n) {
        BlockState st = level.getBlockState(n);
        return st.isAir() || !st.getFluidState().isEmpty() && st.getCollisionShape(level, n).isEmpty() || st.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    /** Touches air or water on some side: visible from somewhere. */
    public static boolean exposed(ServerLevel level, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockState n = level.getBlockState(p.relative(d));
            if (n.isAir() || !n.getFluidState().isEmpty() && n.getCollisionShape(level, p.relative(d)).isEmpty()) return true;
        }
        return false;
    }

    /** Dropped items of a kind (or any, for null) near {@code center}, nearest first. */
    public static List<ItemEntity> drops(ServerLevel level, BlockPos center, double radius, Item item) {
        return drops(level, center, radius, item == null ? null : Set.of(item));
    }

    /** Dropped items of any of these kinds (or any, for null) near {@code center}, nearest first. */
    public static List<ItemEntity> drops(ServerLevel level, BlockPos center, double radius, Set<Item> items) {
        List<ItemEntity> out = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius),
                e -> e.isAlive() && (items == null || items.contains(e.getItem().getItem())));
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(center))));
        return out;
    }
}
