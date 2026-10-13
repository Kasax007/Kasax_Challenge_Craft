package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotActions;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A deliberate fall: pillars up where it stands until a side of the pillar drops {@code height}
 * blocks, and steps off that side into the open (for "take fall damage", "fall 20 blocks and
 * survive": the water bucket reflex saves the long fall when it carries one).
 * The drop is measured on the side it steps off, not counted from where it started: a pillar
 * from the bottom of a hole (dug in for the night) fell three short of twenty onto the ground
 * beside it, and the tile was not had - and one beside a slope falls further than it was built.
 */
public final class FallTask implements BotTask {
    private final int height;
    private final boolean hurt;
    private int ticks;
    private BlockPos jumpedFrom;
    /** The side it steps off (null: still building up). */
    private Direction side;
    private boolean airborne;
    /** Whether it looked for the open sky to build under yet. */
    private boolean sited;

    /** {@code hurt}: land without the water bucket (the damage is the point). */
    public FallTask(int height, boolean hurt) {
        this.height = height;
        this.hurt = hurt;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 2400) return Result.FAILED;
        // (A fall of that height would kill: not a block built for it.)
        if (side == null && !airborne && Math.floor(height - 3 + 1e-6) >= body.getHealth()) return Result.FAILED;
        BlockPos feet = body.blockPosition();
        if (airborne) {
            if (body.onGround() || body.isInWater()) {
                body.stopInputs();
                bot.waterLandingAllowed = true;
                return Result.DONE;
            }
            return Result.RUNNING;
        }
        // Under the open sky: not in a tunnel, a hole with a roof, under a tree - a pillar there
        // digs its way up through what is overhead (half a minute of rock on a mountain, the
        // goal's whole minute gone with it).
        if (!sited) {
            sited = true;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()) > feet.getY()) {
                BlockPos spot = openSpot(level, feet);
                if (spot != null) {
                    bot.interject(new GoToTask(spot, 0));
                    return Result.RUNNING;
                }
            }
        }
        if (side == null) {
            // High enough: the side that drops the height wanted (the least of those), and one
            // the hearts survive.
            if (jumpedFrom == null && body.onGround()) {
                Side s = bestSide(level, body);
                if (s.side() != null) {
                    side = s.side();
                    return Result.RUNNING;
                }
                if (s.tooDeep()) return Result.FAILED;
            }
            // Pillar: jump, set a block where the feet were.
            if (!clear(level, feet.above(2))) {
                bot.actions().breakTick(feet.above(2));
                return Result.RUNNING;
            }
            if (jumpedFrom == null) {
                if (BotActions.buildingBlocks(body) == 0) return Result.FAILED;
                if (body.onGround()) {
                    jumpedFrom = feet;
                    body.jump = true;
                }
                return Result.RUNNING;
            }
            body.jump = false;
            if (body.getY() > jumpedFrom.getY() + 1.05) {
                // (The stone kept for a furnace too: the pillar is the point here.)
                bot.actions().placeAnyBlock(jumpedFrom);
                jumpedFrom = null;
            } else if (body.onGround() && body.getY() < jumpedFrom.getY() + 0.5 && ticks % 20 == 0) {
                jumpedFrom = null;
            }
            return Result.RUNNING;
        }
        // Off the top on that side.
        BlockPos n = feet.relative(side);
        if (!clear(level, n) || !clear(level, n.above())) {
            side = null;
            return Result.RUNNING;
        }
        body.setYRot(side.toYRot());
        body.forward = 0.6f;
        if (!body.onGround()) {
            body.stopInputs();
            airborne = true;
            if (hurt) bot.waterLandingAllowed = false;
        }
        return Result.RUNNING;
    }

    private record Side(Direction side, boolean tooDeep) {}

    /**
     * The side to step off: the least drop that is at least the height wanted and leaves a
     * heart's half. {@code tooDeep}: none, and building on makes it no better (every open side
     * already drops further than it survives).
     */
    private Side bestSide(ServerLevel level, BotPlayer body) {
        BlockPos feet = body.blockPosition();
        Direction best = null;
        double bestDrop = Double.MAX_VALUE;
        boolean deadly = false, more = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = feet.relative(d);
            if (!clear(level, n) || !clear(level, n.above())) {
                more = true; // (a wall beside the pillar: it rises above it)
                continue;
            }
            double drop = dropAt(level, n, body.getY());
            if (Double.isNaN(drop)) continue;
            if (drop < height - 1e-6) {
                more = true;
                continue;
            }
            // (Vanilla: the blocks fallen less three, rounded down.)
            int damage = (int) Math.floor(drop - 3 + 1e-6);
            if (damage >= body.getHealth()) {
                deadly = true;
                continue;
            }
            if (drop < bestDrop) {
                bestDrop = drop;
                best = d;
            }
        }
        return new Side(best, best == null && deadly && !more);
    }

    /**
     * How far it falls stepping off into the column {@code n}: to the top of what it lands on.
     * NaN where the fall would not hurt as wanted - into water, a cobweb, powder snow, onto a
     * ladder or vines, hay, slime, honey, a bed (or onto a dripstone point, which hurts double) -
     * or nothing for 64 blocks.
     */
    private static double dropAt(ServerLevel level, BlockPos n, double fromY) {
        for (int i = 1; i <= 64; i++) {
            BlockPos q = n.below(i);
            var s = level.getBlockState(q);
            if (!s.getFluidState().isEmpty() || s.is(Blocks.COBWEB) || s.is(Blocks.POWDER_SNOW) || s.is(BlockTags.CLIMBABLE)) return Double.NaN;
            var shape = s.getCollisionShape(level, q);
            if (shape.isEmpty()) continue;
            if (s.is(Blocks.HAY_BLOCK) || s.is(Blocks.SLIME_BLOCK) || s.is(Blocks.HONEY_BLOCK) || s.is(BlockTags.BEDS)
                    || s.is(Blocks.POINTED_DRIPSTONE)) return Double.NaN;
            return fromY - (q.getY() + shape.max(Direction.Axis.Y));
        }
        return Double.NaN;
    }

    /** The nearest spot under the open sky a few steps off, on dry ground that is not a tree's crown (null: none). */
    private static BlockPos openSpot(ServerLevel level, BlockPos feet) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX() + dx, feet.getZ() + dz);
                if (Math.abs(y - feet.getY()) > 8) continue;
                BlockPos p = new BlockPos(feet.getX() + dx, y, feet.getZ() + dz), below = p.below();
                var ground = level.getBlockState(below);
                if (!level.getFluidState(below).isEmpty() || ground.getCollisionShape(level, below).isEmpty() || ground.is(BlockTags.LEAVES)) continue;
                if (!clear(level, p) || !clear(level, p.above())) continue;
                double d = p.distSqr(feet);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private static boolean clear(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
    }

    @Override
    public String describe() {
        return "pillar up and fall " + height;
    }
}
