package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.kasax.challengecraft.challenges.Chal_46_Dice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

@Environment(EnvType.CLIENT)
/**
 * Draws a deliberately understated ring around the blocks the player can still reach with the
 * movement budget left on the die.
 *
 * <p>Design constraints, both from the user: it must shrink live as the budget drains, and it must
 * <b>not</b> look intrusive. So this draws only the <i>outline of the reachable region</i> — never
 * a per-block fill or a grid, which would read as an F3+G-style wireframe.
 *
 * <p>It uses thin quad bands on {@link RenderLayer#getDebugQuads()} rather than
 * {@code RenderLayer.getLines()}: vanilla forces line width to at least 2.5 screen pixels (about 5
 * at 4K) and lines are fog-affected, which is exactly the heavy, washed-out look to avoid. A quad
 * band is specified in world units instead, so it stays perspective-correct and subtle.
 */
public final class DiceReachRenderer {
    /** Ring thickness in blocks. */
    private static final float BAND = 0.07f;
    /** Lift above the surface — debug quads have no Z-layering phase of their own. */
    private static final float LIFT = 0.02f;
    private static final int MAX_BUDGET = 8;
    private static final double DIAGONAL = 1.41421356;
    /** Minimum ticks between two recomputations of the reachable set. */
    private static final int REBUILD_COOLDOWN_TICKS = 5;

    private static final float R = 0.89f;
    private static final float G = 0.70f;
    private static final float B = 0.35f;
    private static final float A = 0.42f;

    /** Cached edges, recomputed only when the player's block position or budget changes. */
    private static final List<Edge> EDGES = new ArrayList<>();
    private static long cacheOrigin = Long.MIN_VALUE;
    private static int cacheBudgetTenths = -1;
    private static Vec3d cachePos = null;
    private static long lastRebuildTick = 0L;

    private record Edge(double x1, double z1, double x2, double z2, int y) {
    }

    private DiceReachRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!Chal_46_Dice.isActive() || client.world == null || client.player == null) return;
            if (client.player.isSpectator() || client.player.isCreative()) return;

            float budget = DiceClientState.getRemaining();
            if (budget <= 0.05f) return;

            BlockPos origin = client.player.getBlockPos();
            Vec3d exact = client.player.getPos();
            int tenths = Math.round(budget * 10f);
            // The ring now depends on the player's sub-block position, so a block-position cache
            // alone would leave it visibly stale while walking within one block.
            boolean moved = cachePos == null || cachePos.squaredDistanceTo(exact) > 0.01;
            boolean stale = origin.asLong() != cacheOrigin || tenths != cacheBudgetTenths || moved;

            // Throttle to at most one rebuild every REBUILD_COOLDOWN_TICKS. The ring is drawn from
            // the cached edges every frame regardless, so this only limits how often the reachable
            // set is recomputed — it stays smooth, just cheaper.
            long now = client.world.getTime();
            boolean firstBuild = cachePos == null;
            // `now < lastRebuildTick` catches switching to a world with a lower time, which would
            // otherwise make the cooldown look like it never expires.
            boolean cooledDown = now < lastRebuildTick || now - lastRebuildTick >= REBUILD_COOLDOWN_TICKS;

            if (stale && (firstBuild || cooledDown)) {
                cacheOrigin = origin.asLong();
                cacheBudgetTenths = tenths;
                cachePos = exact;
                lastRebuildTick = now;
                rebuild(client.world, origin, exact, budget);
            }
            if (EDGES.isEmpty()) return;

            Vec3d cam = context.camera().getPos();
            MatrixStack matrices = context.matrixStack();
            VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getDebugQuads());

            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);
            for (Edge e : EDGES) {
                VertexRendering.drawSide(matrices, consumer, Direction.UP,
                        (float) e.x1(), e.y() + LIFT, (float) e.z1(),
                        (float) e.x2(), e.y() + LIFT, (float) e.z2(),
                        R, G, B, A);
            }
            matrices.pop();
        });
    }

    /**
     * Weighted Dijkstra over walkable surface columns (orthogonal 1.0, diagonal ~1.414), then the
     * outline of the resulting set. Max budget is 6, so the region is at most ~15×15 columns —
     * a few hundred cheap block lookups, only on the ticks where something actually changed.
     */
    private static void rebuild(World world, BlockPos origin, Vec3d playerPos, double budget) {
        EDGES.clear();
        int radius = (int) Math.ceil(Math.min(budget, MAX_BUDGET)) + 1;

        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Integer> surface = new HashMap<>();
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Double.compare(
                Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));

        Integer startY = surfaceY(world, origin.getX(), origin.getZ(), origin.getY());
        if (startY == null) return;

        long startKey = key(0, 0);
        dist.put(startKey, 0.0);
        surface.put(startKey, startY);
        queue.add(new long[]{startKey, Double.doubleToLongBits(0.0)});

        // Seed the eight neighbours with the REAL distance from the player's exact position to the
        // nearest point of each cell, instead of the flat 1.0 centre-to-centre step. The player is
        // rarely standing in the middle of their block, so charging a full block to reach the next
        // one made e.g. "0.5 left" highlight nothing even though that step is clearly walkable.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int wx = origin.getX() + dx;
                int wz = origin.getZ() + dz;
                Integer ny = surfaceY(world, wx, wz, startY);
                if (ny == null || Math.abs(ny - startY) > 1) continue;

                double entry = nearestPointDistance(playerPos.x, playerPos.z, wx, wz);
                if (entry > budget + 1.5) continue;
                long k = key(dx, dz);
                if (entry < dist.getOrDefault(k, Double.MAX_VALUE)) {
                    dist.put(k, entry);
                    surface.put(k, ny);
                    queue.add(new long[]{k, Double.doubleToLongBits(entry)});
                }
            }
        }

        while (!queue.isEmpty()) {
            long[] head = queue.poll();
            long k = head[0];
            double d = Double.longBitsToDouble(head[1]);
            if (d > dist.getOrDefault(k, Double.MAX_VALUE)) continue;

            int cx = unpackX(k);
            int cz = unpackZ(k);
            int cy = surface.get(k);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = cx + dx;
                    int nz = cz + dz;
                    if (Math.abs(nx) > radius || Math.abs(nz) > radius) continue;

                    Integer ny = surfaceY(world, origin.getX() + nx, origin.getZ() + nz, cy);
                    if (ny == null) continue;
                    // A step that would need more than a jump/drop of one is not "walking there".
                    if (Math.abs(ny - cy) > 1) continue;

                    double step = (dx != 0 && dz != 0) ? DIAGONAL : 1.0;
                    double nd = d + step;
                    // Relax past the budget so the ring can be placed by interpolation rather
                    // than snapping cell-by-cell.
                    if (nd > budget + 1.5) continue;

                    long nk = key(nx, nz);
                    if (nd < dist.getOrDefault(nk, Double.MAX_VALUE)) {
                        dist.put(nk, nd);
                        surface.put(nk, ny);
                        queue.add(new long[]{nk, Double.doubleToLongBits(nd)});
                    }
                }
            }
        }

        // Outline: an in-budget cell with an orthogonal neighbour that is out of budget. Using
        // only orthogonal neighbours keeps the ring rectilinear and clean; testing height
        // differences here as well would emit interior edges all over natural terrain.
        for (Map.Entry<Long, Double> entry : dist.entrySet()) {
            if (entry.getValue() > budget) continue;
            long k = entry.getKey();
            int cx = unpackX(k);
            int cz = unpackZ(k);
            int y = surface.get(k);
            double bx = origin.getX() + cx;
            double bz = origin.getZ() + cz;

            if (outOfRange(dist, cx + 1, cz, budget)) {
                EDGES.add(new Edge(bx + 1 - BAND / 2, bz, bx + 1 + BAND / 2, bz + 1, y));
            }
            if (outOfRange(dist, cx - 1, cz, budget)) {
                EDGES.add(new Edge(bx - BAND / 2, bz, bx + BAND / 2, bz + 1, y));
            }
            if (outOfRange(dist, cx, cz + 1, budget)) {
                EDGES.add(new Edge(bx, bz + 1 - BAND / 2, bx + 1, bz + 1 + BAND / 2, y));
            }
            if (outOfRange(dist, cx, cz - 1, budget)) {
                EDGES.add(new Edge(bx, bz - BAND / 2, bx + 1, bz + BAND / 2, y));
            }
        }
    }

    /** Distance from a point to the nearest point of the 1×1 column at (cellX, cellZ). */
    private static double nearestPointDistance(double px, double pz, int cellX, int cellZ) {
        double dx = Math.max(Math.max(cellX - px, 0.0), px - (cellX + 1));
        double dz = Math.max(Math.max(cellZ - pz, 0.0), pz - (cellZ + 1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean outOfRange(Map<Long, Double> dist, int x, int z, double budget) {
        Double d = dist.get(key(x, z));
        return d == null || d > budget;
    }

    /**
     * The Y a player would stand at in this column, searched near {@code nearY}. Returns null when
     * there is nowhere sensible to stand (a wall, a big drop, or a ceiling in the way).
     */
    private static Integer surfaceY(World world, int x, int z, int nearY) {
        for (int y = nearY + 1; y >= nearY - 1; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos below = feet.down();
            if (!world.getBlockState(below).isSolidBlock(world, below)) continue;
            if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) continue;
            BlockPos head = feet.up();
            if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) continue;
            return y;
        }
        return null;
    }

    private static long key(int x, int z) {
        return ((long) (x + 512) << 20) | (z + 512);
    }

    private static int unpackX(long k) {
        return (int) (k >> 20) - 512;
    }

    private static int unpackZ(long k) {
        return (int) (k & 0xFFFFF) - 512;
    }
}
