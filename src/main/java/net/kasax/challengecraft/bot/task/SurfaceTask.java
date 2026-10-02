package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Back up to the surface from a mine: straight up by jumping and putting a block under itself
 * (breaking what is above the head), or, without blocks to spare or with something liquid above,
 * up a staircase it digs. Stops as soon as it sees the sky.
 */
public final class SurfaceTask implements BotTask {
    private static final int STAGE = 16;
    private Direction heading;
    private BlockPos jumpedFrom, stepTo;
    private int ticks, stepTicks, bestY = Integer.MIN_VALUE, sinceBest, switches;
    private boolean stairs, navigating;
    private int navFails, navBestY = Integer.MIN_VALUE, navSince;
    private boolean failedPlace, triedEntry;

    /** Deep enough under the ground that walking about on the surface needs a climb first. */
    public static boolean underground(BotPlayer body) {
        return underground((ServerLevel) body.level(), body.blockPosition());
    }

    /**
     * The height of the ground itself at a column: the top, less any tree standing on it (trunks,
     * leaves, huge mushrooms). A forest floor is the surface, not a pit between the trunks.
     */
    static int ground(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y - 1, z);
        for (int i = 0; i < 40 && y > level.getMinY(); i++, y--, p.setY(y - 1)) {
            var st = level.getBlockState(p);
            if (!(st.is(net.minecraft.tags.BlockTags.LOGS) || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.world.level.block.Blocks.VINE)
                    || st.is(net.minecraft.world.level.block.Blocks.MUSHROOM_STEM) || st.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM_BLOCK)
                    || st.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM_BLOCK) || st.isAir())) break;
        }
        return y;
    }

    /**
     * Really under a roof: a cave or a tunnel (no sky light at the head, well below the ground
     * there). A valley, a ravine or a forest floor is not: the path search walks out of those.
     */
    public static boolean covered(BotPlayer body) {
        ServerLevel level = (ServerLevel) body.level();
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        BlockPos head = body.blockPosition().above();
        return level.getBrightness(net.minecraft.world.level.LightLayer.SKY, head) < 4 && head.getY() < ground(level, head.getX(), head.getZ()) - 3;
    }

    /** {@link #underground(BotPlayer)} for feet at {@code feet}. */
    public static boolean underground(ServerLevel level, BlockPos feet) {
        // The Nether and the End have no sky and no surface to climb to.
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        // Little sky light where the head is: a cave or a mine. (Under trees or next to a trunk
        // there is plenty; up an open shaft too, and that one needs no climbing either.)
        BlockPos head = feet.above();
        if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, head) < 6
                && head.getY() < ground(level, head.getX(), head.getZ())) return true;
        // Down a shaft or a ravine (the sky shines straight in): the ground a few blocks around is
        // mostly well above the feet. (One side high is only a cliff or a wall.)
        int feetY = feet.getY(), high = 0, deep = 0, samples = 0;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int x = feet.getX() + (int) Math.round(Math.cos(a) * 4);
            int z = feet.getZ() + (int) Math.round(Math.sin(a) * 4);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            samples++;
            int ground = ground(level, x, z);
            if (ground > feetY + 1) high++;
            if (ground > feetY + 3) deep++;
        }
        // Well down, the ground all round far above: a pit or a room dug out at a shaft's foot,
        // walls or not.
        if (samples > 0 && deep * 10 >= samples * 7) return true;
        // ... and it is hemmed in (a hole or a tunnel, not a valley floor).
        // (In water the walls at the first level above it count: rock round a flooded pit, open
        // air over a lake.)
        BlockPos at = feet.above();
        for (int i = 0; i < 6 && !level.getFluidState(at).isEmpty(); i++) at = at.above();
        int walls = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = at.relative(d);
            var wst = level.getBlockState(n);
            if (!wst.getCollisionShape(level, n).isEmpty() && !wst.is(net.minecraft.tags.BlockTags.LOGS)) walls++;
        }
        return samples > 0 && high * 10 >= samples * 7 && walls >= 2;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = bot.navigator().feet();
        // (Only judged standing: mid-jump the body is a block higher than where it stands.)
        if ((body.onGround() || body.isInWater()) && jumpedFrom == null && !underground(body)) {
            body.stopInputs();
            return Result.DONE;
        }
        if (++ticks > 2400) return Result.FAILED;
        // First choice: let the path search find the way up (cave passages, a staircase dug
        // through whatever is cheapest, pillars where there are blocks). The hand-made climb
        // below is only for when it finds nothing.
        // Came in through a cave: out the way it came, if that is not far.
        if (!retraced) {
            Result r = retrace(bot, feet);
            if (r != null) return r;
        }
        // Out of blocks (spent on a pillar, say): a stack from the rock round about first; every
        // way up from a ledge or a pillar top wants some.
        if (!fetchedBlocks && !bot.actions().hasThrowaway() && hasPickaxe(bot.body())) {
            fetchedBlocks = true;
            navigating = false;
            bot.interject(new ObtainTask(java.util.Set.of(net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE), 16,
                    new net.kasax.challengecraft.bot.plan.ObtainPlanner()));
            return Result.RUNNING;
        }
        if (navFails < 4) {
            if (!navigating) {
                // Deep down (ninety blocks of deepslate overhead) one search cannot see the whole
                // way up: a stage of sixteen blocks at a time, each easy to find, then the next.
                // (Not the height map: up an open shaft that is the shaft's own floor.)
                int stage = feet.getY() + STAGE;
                bot.navigator().setGoal(p -> !underground(level, p) || p.getY() >= stage,
                        new BlockPos(feet.getX(), stage, feet.getZ()));
                navigating = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            // (Searching on and on without getting any higher, bobbing in a flooded pit, say:
            // that counts as failing too.)
            if (feet.getY() > navBestY) {
                navBestY = feet.getY();
                navSince = 0;
            } else if (s == BotNavigator.Status.MOVING && ++navSince > 900) {
                bot.navigator().stop();
                s = BotNavigator.Status.FAILED;
                navSince = 0;
            }
            if (s == BotNavigator.Status.MOVING) return Result.RUNNING;
            navigating = false;
            if (s == BotNavigator.Status.FAILED) navFails++;
            else {
                // A stage done: the time allowed starts again (a long climb is not a stuck one).
                navFails = 0;
                ticks = Math.min(ticks, 600);
            }
            return Result.RUNNING; // arrived: judged again next tick
        }
        if (heading == null) heading = body.getDirection();
        // Not getting higher (gravel keeps falling in, water above, a ledge): change the way.
        if (feet.getY() > bestY) {
            bestY = feet.getY();
            sinceBest = 0;
        } else if (!bot.actions().isBreaking() && ++sinceBest > 300) {
            sinceBest = 0;
            stairs = !stairs;
            heading = heading.getClockWise();
            jumpedFrom = null;
            stepTo = null;
            if (++switches > 6) return Result.FAILED;
        }

        if (stepTo != null) return step(bot, feet);

        // Pillar: head room, then jump and set a block where the feet were.
        BlockPos head = feet.above(2);
        boolean headSafe = safe(level, head);
        if (!stairs && jumpedFrom == null && headSafe && bot.actions().hasThrowaway() && !failedPlace) {
            if (!clear(level, head)) {
                bot.actions().breakTick(head);
                return Result.RUNNING;
            }
            if (body.onGround()) {
                jumpedFrom = feet;
                body.jump = true;
            }
            return Result.RUNNING;
        }
        if (jumpedFrom != null) {
            body.jump = false;
            if (body.getY() > jumpedFrom.getY() + 1.05) {
                if (!bot.actions().placeThrowaway(jumpedFrom)) failedPlace = true; // stairs from now on
                jumpedFrom = null;
            } else if (body.onGround() && ++stepTicks > 20) {
                jumpedFrom = null; // did not get up (a low ceiling after all): try again
                stepTicks = 0;
            }
            return Result.RUNNING;
        }

        // Staircase up: head room here, then the two blocks ahead one higher.
        BlockPos ahead = feet.relative(heading);
        List<BlockPos> dig = List.of(feet.above(2), ahead.above(), ahead.above(2));
        boolean ok = !clear(level, ahead) && dig.stream().allMatch(b -> safe(level, b));
        if (!ok) {
            heading = heading.getClockWise();
            return Result.RUNNING;
        }
        for (BlockPos b : dig) {
            if (!clear(level, b)) {
                bot.actions().breakTick(b);
                return Result.RUNNING;
            }
        }
        stepTo = ahead.above();
        stepTicks = 0;
        return Result.RUNNING;
    }

    private boolean retraced, fetchedBlocks;

    private static boolean hasPickaxe(BotPlayer body) {
        for (var st : body.getInventory().getNonEquipmentItems()) if (st.is(net.minecraft.tags.ItemTags.PICKAXES)) return true;
        return false;
    }
    private int trailIndex = -1, trailFails;

    /**
     * Back the way it came: along its trail of crumbs in short stretches, when that is not much
     * longer than digging straight up. Null when that is over (out, or not worth it, or blocked).
     */
    private Result retrace(Bot bot, BlockPos feet) {
        var trail = bot.trail;
        if (trail.size() < 2) {
            retraced = true;
            return null;
        }
        if (trailIndex < 0) {
            int near = 0;
            for (int i = 1; i < trail.size(); i++) if (trail.get(i).distSqr(feet) < trail.get(near).distSqr(feet)) near = i;
            double walk = Math.sqrt(trail.get(near).distSqr(feet));
            for (int i = near; i > 0; i--) walk += Math.sqrt(trail.get(i).distSqr(trail.get(i - 1)));
            int depth = Math.max(8, trail.get(0).getY() - feet.getY());
            // (Digging up costs about three blocks of walking per block of height.)
            if (walk > depth * 4 + 60 || walk > 600) {
                retraced = true;
                return null;
            }
            trailIndex = near;
            navigating = false;
        }
        if (!navigating) {
            trailIndex = Math.max(0, trailIndex - 3);
            bot.navigator().goStandNear(trail.get(trailIndex), 2);
            navigating = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s == BotNavigator.Status.MOVING) return Result.RUNNING;
        navigating = false;
        if (s == BotNavigator.Status.FAILED && ++trailFails > 3) {
            retraced = true;
            return null;
        }
        if (s == BotNavigator.Status.ARRIVED) ticks = Math.min(ticks, 600); // (getting on: time again)
        if (trailIndex == 0 && s == BotNavigator.Status.ARRIVED) retraced = true;
        return Result.RUNNING;
    }

    private Result step(Bot bot, BlockPos feet) {
        BotPlayer body = bot.body();
        if (feet.equals(stepTo)) {
            body.stopInputs();
            stepTo = null;
            return Result.RUNNING;
        }
        body.lookAt(Vec3.atBottomCenterOf(stepTo).add(0, body.getEyeHeight(), 0));
        body.forward = 1f;
        body.jump = true;
        if (++stepTicks > 40) {
            body.stopInputs();
            stepTo = null;
            heading = heading.getClockWise();
        }
        return Result.RUNNING;
    }

    private static boolean clear(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
    }

    /** Nothing liquid there or next to it (above all: nothing that would pour down on the bot). */
    private static boolean safe(ServerLevel level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty() || level.getBlockState(p).getDestroySpeed(level, p) < 0) return false;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (!level.getFluidState(p.relative(d)).isEmpty()) return false;
        }
        // Gravel or sand above would just fall in again; fine, it gets dug again, but not lava.
        return true;
    }

    @Override
    public String status() {
        return describe() + " [jumpedFrom " + jumpedFrom + ", stepTo " + stepTo + ", heading " + heading + ", ticks " + ticks + "]";
    }

    @Override
    public String describe() {
        return "climb to the surface";
    }
}
