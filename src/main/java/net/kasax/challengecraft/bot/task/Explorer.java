package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Walking out to see more of the world, for tasks that look for something not in sight: up to the
 * surface first if underground, then in legs of about 40 blocks in one direction (a player keeps
 * a direction rather than circling), turning when blocked. Each leg makes the bot look around again.
 */
public final class Explorer {
    private final int budget;
    private Direction heading;
    private boolean walking, climbed;
    private int ticks, legs;

    Explorer(int budgetTicks) {
        this.budget = budgetTicks;
    }

    private java.util.Set<net.minecraft.world.entity.EntityType<?>> habitat;
    private boolean habitatGiveUp;
    private final FarWalk toHabitat = new FarWalk();

    private boolean land;
    private int seaLegs;
    private BlockPos lastLand;

    /**
     * Exploring for something found on land (trees, stone, a village): headings with land in view
     * first, and back toward the coast after a few legs out at sea - a player looking for trees
     * does not swim on into the open ocean (seed 11: 1300 blocks out, no log found).
     */
    Explorer onLand(boolean land) {
        this.land = land;
        return this;
    }

    /** Exploring for these kinds of mob: their habitat first, when one is known. */
    Explorer lookingFor(java.util.Set<net.minecraft.world.entity.EntityType<?>> types) {
        this.habitat = types;
        return this;
    }

    /** One tick of exploring; FAILED once the time is up. */
    BotTask.Result tick(Bot bot) {
        if (++ticks > budget || legs > 60) return BotTask.Result.FAILED;
        if (!climbed) {
            climbed = true;
            if (SurfaceTask.underground(bot.body())) {
                bot.interject(new SurfaceTask());
                return BotTask.Result.RUNNING;
            }
        }
        // Looking for a kind of mob that lives somewhere in particular: to the nearest place of
        // that kind it knows (horses: the plains seen on the way), not off into the snow.
        if (habitat != null && !habitatGiveUp) {
            boolean in = false;
            BlockPos home = null;
            for (var t : habitat) {
                if (net.kasax.challengecraft.bot.plan.MobHabitats.inHabitat(bot, t)) in = true;
                BlockPos h = net.kasax.challengecraft.bot.plan.MobHabitats.nearestKnown(bot, t);
                if (h != null && (home == null || h.distSqr(bot.body().blockPosition()) < home.distSqr(bot.body().blockPosition()))) home = h;
            }
            if (!in && home != null) {
                if (walking) {
                    bot.navigator().stop();
                    walking = false;
                }
                FarWalk.Status fs = toHabitat.tick(bot, home, 6);
                // There (or no way there): on from here the usual way; the spot it remembered
                // may lie just off the edge of that country (a hilltop of another biome).
                if (fs != FarWalk.Status.MOVING) habitatGiveUp = true;
                return BotTask.Result.RUNNING;
            }
        }
        if (heading == null) heading = frontier(bot, land);
        bot.exploreHeading = heading;
        BotNavigator nav = bot.navigator();
        if (!walking) {
            legs++;
            if (land) {
                var lvl = (net.minecraft.server.level.ServerLevel) bot.body().level();
                if (!sea(lvl, nav.feet())) {
                    seaLegs = 0;
                    lastLand = nav.feet();
                } else if (++seaLegs >= 3) {
                    // Out at sea three legs: the heading with the most land in view, else back
                    // the way to the last land it stood on.
                    seaLegs = 0;
                    Direction d = landward(bot);
                    if (d == null && lastLand != null) d = Direction.getApproximateNearest(lastLand.getX() - nav.feet().getX(), 0, lastLand.getZ() - nav.feet().getZ());
                    if (d != null && d != heading) {
                        bot.say("out at sea: back toward land (" + d.getName() + ")");
                        heading = d;
                        bot.exploreHeading = heading;
                    }
                }
            }
            BlockPos p = nav.feet().relative(heading, 40).relative(heading.getClockWise(), bot.body().getRandom().nextInt(21) - 10);
            // Under a roof (the Nether) the height map is the bedrock ceiling: legs at about the
            // height it is at instead, on something to stand on (else it tunnels through the
            // netherrack under the roof, a pickaxe a minute).
            var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
            BlockPos leg = level.dimensionType().hasCeiling() ? legToward(level, nav.feet(), new BlockPos(p.getX(), nav.feet().getY(), p.getZ()), 40)
                    : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
            // (A leg ending far down - a chasm, the sea floor, the void - is no leg: another way.)
            if (leg.getY() < nav.feet().getY() - 24) {
                heading = bot.body().getRandom().nextBoolean() ? heading.getClockWise() : heading.getCounterClockWise();
                bot.exploreHeading = heading;
                return BotTask.Result.RUNNING;
            }
            nav.goNear(leg, 6);
            walking = true;
        }
        BotNavigator.Status s = nav.tick();
        if (s != BotNavigator.Status.MOVING) {
            walking = false;
            climbed = false; // (a leg that went into a cave: back up before the next one)
            bot.senses().refresh();
            if (s == BotNavigator.Status.FAILED) heading = bot.body().getRandom().nextBoolean() ? heading.getClockWise() : heading.getCounterClockWise();
        }
        return BotTask.Result.RUNNING;
    }

    /**
     * The way to new country: the direction with the most land it has never had in view, just
     * beyond what it sees now (a player heads off the edge of the map, not back over it). The
     * old heading is kept unless another is clearly better.
     */
    /** Whether the column at {@code p} is open water at the surface (or not loaded). */
    private static boolean sea(net.minecraft.server.level.ServerLevel level, BlockPos p) {
        if (!level.hasChunkAt(p)) return false;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
        return !level.getFluidState(top.below()).isEmpty() && level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, p).getY() < top.getY() - 2;
    }

    /** Land columns in view in direction {@code d} (sampled every other chunk, loaded ones only). */
    private static int landIn(net.minecraft.server.level.ServerLevel level, BlockPos from, Direction d, int view) {
        int n = 0;
        for (int r = 1; r < view; r += 2) {
            for (int side = -r / 2; side <= r / 2; side += 2) {
                BlockPos p = from.offset((d.getStepX() * r + d.getStepZ() * side) * 16, 0, (d.getStepZ() * r + d.getStepX() * side) * 16);
                if (level.hasChunkAt(p) && !sea(level, p)) n++;
            }
        }
        return n;
    }

    /** The heading with the most land in view; null if there is none in view at all. */
    private static Direction landward(Bot bot) {
        var level = (net.minecraft.server.level.ServerLevel) bot.body().level();
        int view = BotWorld.viewChunks(level);
        Direction best = null;
        int most = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int n = landIn(level, bot.body().blockPosition(), d, view);
            if (n > most) {
                most = n;
                best = d;
            }
        }
        return best;
    }

    private static Direction frontier(Bot bot, boolean land) {
        var level = bot.body().level();
        int cx = bot.body().getBlockX() >> 4, cz = bot.body().getBlockZ() >> 4;
        int view = BotWorld.viewChunks((net.minecraft.server.level.ServerLevel) level);
        Direction best = null;
        int bestScore = -1, oldScore = -1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int score = 0;
            for (int r = view - 4; r <= view + 8; r += 2) {
                for (int side = -r / 2; side <= r / 2; side += 2) {
                    int x = cx + d.getStepX() * r + d.getStepZ() * side, z = cz + d.getStepZ() * r + d.getStepX() * side;
                    if (!bot.memory().wasScanned(level.dimension(), x, z)) score++;
                }
            }
            // (Looking for land things: new country with land in it, not the open sea.)
            if (land && !level.dimensionType().hasCeiling()) score += 2 * landIn((net.minecraft.server.level.ServerLevel) level, bot.body().blockPosition(), d, view);
            if (d == bot.exploreHeading) oldScore = score;
            if (score > bestScore || score == bestScore && bot.body().getRandom().nextBoolean()) {
                bestScore = score;
                best = d;
            }
        }
        if (bot.exploreHeading != null && oldScore >= bestScore * 0.7) return bot.exploreHeading;
        return best;
    }

    /**
     * Where to stand at the column of {@code p}: the surface there, or under a roof (the Nether,
     * where the height map is the bedrock ceiling) a spot to stand on near the height {@code nearY}.
     */
    public static BlockPos ground(net.minecraft.server.level.ServerLevel level, BlockPos p, int nearY) {
        if (!level.dimensionType().hasCeiling()) return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
        for (int i = 0; i <= 24; i++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos q = new BlockPos(p.getX(), nearY + i * sign, p.getZ());
                if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                        && level.getBlockState(q.below()).isSolidRender() && level.getFluidState(q.below()).isEmpty()) return q;
            }
        }
        return new BlockPos(p.getX(), nearY, p.getZ());
    }

    /** The next leg toward {@code target}: at most {@code leg} blocks on, on the surface there. */
    static BlockPos legToward(net.minecraft.server.level.ServerLevel level, BlockPos from, BlockPos target, int leg) {
        double dx = target.getX() - from.getX(), dz = target.getZ() - from.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        BlockPos p = d <= leg ? target : BlockPos.containing(from.getX() + dx / d * leg, from.getY(), from.getZ() + dz / d * leg);
        if (!level.hasChunkAt(p)) return new BlockPos(p.getX(), Math.max(level.getSeaLevel(), from.getY()), p.getZ());
        // Under a roof (the Nether): no surface to aim at; a spot to stand on near the height on
        // the line from here to there, else that height itself (the path search digs its way).
        if (level.dimensionType().hasCeiling()) {
            int y = d <= leg ? target.getY() : (int) Math.round(from.getY() + (target.getY() - from.getY()) * leg / d);
            for (int i = 0; i <= 24; i++) {
                for (int sign : new int[] {1, -1}) {
                    BlockPos q = new BlockPos(p.getX(), y + i * sign, p.getZ());
                    if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
                            && level.getBlockState(q.below()).isSolidRender() && level.getFluidState(q.below()).isEmpty()) return q;
                }
            }
            return new BlockPos(p.getX(), y, p.getZ());
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
    }

    void pause(Bot bot) {
        if (walking) bot.navigator().stop();
        walking = false;
    }
}
