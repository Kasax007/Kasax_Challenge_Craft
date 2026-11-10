package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

/**
 * Out of air under a roof of water (a tunnel dug into a flooded cave, a sea cave): to the nearest
 * spot where the head is in the air, before anything else. Swimming up is not enough there.
 * <p>
 * As a player goes about it: open water straight up is swum up at once; else the nearest air the
 * way finder knows of; not getting there, a thin roof with air over it is dug through, or else the
 * way it came is swum back (the staircase it dug down into the flood); only last of all is a thick
 * roof dug at.
 */
public final class AirTask implements BotTask {
    private enum Way { SEARCH, BACK, SWIM, DIG, RETRACE, SEAL }

    private Way way = Way.SEARCH;
    /** The way it dived, from its last breath (a copy of the bot's dive trail): swum back along. */
    private java.util.ArrayDeque<BlockPos> crumbs;
    private int crumbTicks, retraceFails;
    /** The ways that got it nowhere under this roof (not tried again for a while). */
    private final java.util.EnumSet<Way> spent = java.util.EnumSet.noneOf(Way.class);
    /** Digging up: when it last got higher or broke a block, and how many solid blocks were over the head then. */
    private int digIdle, digSolid = -1;
    private double digFromY;
    private float digBroke;
    private int sealTicks;
    private boolean started, backTried;
    /** Straight up did not get it any higher (a current, a waterfall): not again. */
    private boolean upBlocked;
    private int upTicks;
    private double upFrom;
    /** Ticks with the head out, catching breath. */
    private int breathing;
    private int ticks, fails, stuck;
    private net.minecraft.world.phys.Vec3 last;
    /** The spot on its trail it swims back to: the last one with air. */
    private BlockPos back;
    /** Columns swum for that did not get it up (a current, falling water): not again. */
    private final java.util.Set<BlockPos> triedColumns = new java.util.HashSet<>();
    private BlockPos swimColumn;
    /** Ticks in the dig with nothing over the head to dig, and not rising. */
    private int idleDig;
    private double idleDigFrom;

    /**
     * How many blocks of water there are over the head up to the air, when nothing but still
     * water is in between (open water: a lake, the sea); -1 under a roof, or falling water.
     */
    public static int waterAbove(net.kasax.challengecraft.bot.BotPlayer body) {
        ServerLevel level = (ServerLevel) body.level();
        BlockPos head = BlockPos.containing(body.getX(), body.getEyeY(), body.getZ());
        for (int up = 0; up <= 24; up++) {
            BlockPos q = head.above(up);
            if (blocksTheWayUp(level, q)) return -1;
            var fluid = level.getFluidState(q);
            if (fluid.isEmpty()) return up;
            if (!fluid.isSource() && up > 0) return -1;
        }
        return -1;
    }

    /** The head would be out of the water standing (or swimming) here. */
    public static boolean breathable(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        return level.getFluidState(head).isEmpty() && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        if (ticks == 0 && breathing == 0 && body.isEyeInFluid(FluidTags.WATER)) {
            ServerLevel lv = (ServerLevel) body.level();
            BlockPos f = body.blockPosition();
            // The way it came, while it is known (the dive trail): back along it is the way a
            // player takes - the path finder's guess, steered straight up, led into the roof
            // over a flooded staircase three times in one game (seed -3882...).
            if (bot.params.on("airsafe") && bot.diveTrail.size() >= 2 && !openAbove(lv, f, 24)) {
                crumbs = new java.util.ArrayDeque<>(bot.diveTrail);
                way = Way.RETRACE;
            }
            bot.say("short of air at " + f.toShortString() + " (" + body.getAirSupply() + "/" + body.getMaxAirSupply()
                    + (openAbove(lv, f, 24) ? ", open water above" : thinRoof(lv, BlockPos.containing(body.getX(), body.getEyeY(), body.getZ())) ? ", a thin roof" : ", a roof over the water")
                    + (way == Way.RETRACE ? "): back the way I dived, " + crumbs.size() + " blocks" : ")"));
        }
        // Head out: there until the lungs are nearly full again - a breath and straight back
        // under (the way on leads through the water) and the next breath is shorter, until
        // there is none. Meanwhile still, afloat (head kept up) or standing.
        if (!body.isEyeInFluid(FluidTags.WATER)) {
            if (body.getAirSupply() >= body.getMaxAirSupply() * 9 / 10 || ++breathing > 200) return Result.DONE;
            bot.navigator().stop();
            body.stopInputs();
            if (body.isInWater()) body.jump = true;
            return Result.RUNNING;
        }
        if (breathing > 0 && body.getAirSupply() >= body.getMaxAirSupply() / 2) {
            // (Bobbing under for a moment while waiting at the surface: up again, no new search.)
            body.stopInputs();
            body.jump = true;
            return Result.RUNNING;
        }
        if (++ticks > (way == Way.DIG ? 1600 : 600)) return Result.FAILED;
        ServerLevel level = (ServerLevel) body.level();
        BlockPos feet = body.blockPosition();
        // Water straight up to the air (the commonest case: a dive for something on the bottom):
        // swum up, nothing to work out - as long as that gets it higher. (Not rising - a current,
        // water falling down a shaft - it drowned so twice on seed 77, swimming "up" in place.)
        if (!upBlocked && openAbove(level, feet, 24)) {
            if (upTicks++ == 0 || body.getY() > upFrom + 0.5) {
                upFrom = body.getY();
                if (upTicks > 1) upTicks = 1;
            }
            if (upTicks > 30) {
                upBlocked = true;
                bot.say("up for air: not getting higher here, another way");
            } else {
                bot.navigator().stop();
                body.stopInputs();
                body.jump = true;
                return Result.RUNNING;
            }
        }
        if (ticks % 20 == 1) {
            // (Measured over a second: swimming is slow, but not this slow.)
            stuck = last != null && body.position().distanceToSqr(last) < 0.25 ? stuck + 1 : 0;
            last = body.position();
        }
        // The way it is going about it gets it nowhere (two seconds without moving, or no way
        // found twice): the next one. (Once in a while, not for good after one slow second -
        // digging up through the rock of a flooded cave drowned it on seed 66, the staircase it
        // had come down by right behind it.)
        if ((stuck >= 2 || fails > 1) && way != Way.DIG && way != Way.RETRACE && way != Way.SEAL) {
            stuck = 0;
            fails = 0;
            started = false;
            bot.navigator().stop();
            Way was = way;
            if (bot.params.on("airsafe")) spent.add(was);
            way = next(bot, level, feet);
            if (way != was && way != Way.BACK) bot.say("up for air: " + way.name().toLowerCase() + " instead");
        }
        switch (way) {
            case RETRACE -> {
                if (retrace(bot, level, feet)) return Result.RUNNING;
                // (Lost the way - a current took it off, the way back filled in: another.)
                spent.add(Way.RETRACE);
                started = false;
                way = next(bot, level, feet);
                bot.say("up for air: off the way back, " + way.name().toLowerCase() + " instead");
                return Result.RUNNING;
            }
            case SEAL -> {
                if (seal(bot, level, body)) return Result.RUNNING;
                spent.add(Way.SEAL);
                way = next(bot, level, feet);
                bot.say("up for air: no pocket of air to be had here, " + way.name().toLowerCase() + " instead");
                return Result.RUNNING;
            }
            case DIG -> {
                BlockPos head = BlockPos.containing(body.getX(), body.getEyeY(), body.getZ());
                // Getting nowhere at it (nothing broken, no higher, for two seconds - rock too slow
                // to dig under water, a roof eight blocks thick): another way, not the same spot
                // till the air is gone (seed -3882..., three times in one game; seeds 2582... and
                // 4416... in the knowledge league).
                if (bot.params.on("airsafe")) {
                    int solid = 0;
                    for (int up = 1; up <= 3; up++) if (blocksTheWayUp(level, head.above(up))) solid++;
                    float broke = bot.actions().breakProgress();
                    if (digSolid < 0 || solid < digSolid || body.getY() > digFromY + 0.5 || broke > digBroke + 0.01f) {
                        digSolid = solid;
                        digFromY = body.getY();
                        digBroke = broke;
                        digIdle = 0;
                    }
                    // (Getting somewhere, but too slowly for the air there is: the roof would take
                    // longer than the breath lasts - stone with a wooden pickaxe, under water.)
                    boolean tooSlow = ticks % 20 == 0 && digTicksUp(bot, level, head) > body.getAirSupply() + 60 && spent.size() < Way.values().length - 2;
                    if (tooSlow || ++digIdle > 40) {
                        digIdle = 0;
                        digSolid = -1;
                        spent.add(Way.DIG);
                        Way was = way;
                        way = next(bot, level, feet);
                        if (way != was) {
                            bot.say("up for air: digging gets nowhere, " + way.name().toLowerCase() + " instead");
                            return Result.RUNNING;
                        }
                        spent.remove(Way.DIG);
                    }
                }
                // Nothing over the head to dig, and the swim up gets it no higher (water falling
                // down on it, a current): another column, not "up" into it till the air is gone -
                // so it drowned under a village's waterfall in a self-play league.
                boolean nothingToDig = true;
                for (BlockPos q : new BlockPos[]{head.above(), head.above(2)}) nothingToDig &= !blocksTheWayUp(level, q);
                if (nothingToDig) {
                    if (idleDig++ == 0 || body.getY() > idleDigFrom + 0.5) {
                        idleDigFrom = body.getY();
                        idleDig = 1;
                    }
                    if (idleDig > 30) {
                        idleDig = 0;
                        triedColumns.add(feet.atY(0));
                        BlockPos other = openColumn(level, feet, triedColumns);
                        if (other != null) {
                            way = Way.SWIM;
                            swimColumn = other;
                            bot.say("up for air: no way up here, to the still water at " + other.toShortString());
                        }
                    }
                } else idleDig = 0;
                for (BlockPos q : new BlockPos[]{head.above(), head.above(2)}) {
                    // (A waterlogged block - a fence, a slab in a flooded mineshaft - is in the way
                    // as much as a dry one: dug too. Left alone as "water", a fence over the head
                    // drowned it there. And a cobweb in the water: swimming up through it takes
                    // longer than any breath.)
                    if (blocksTheWayUp(level, q) && level.getBlockState(q).getDestroySpeed(level, q) >= 0) {
                        // Standing on the bottom while at it: afloat, digging is five times slower.
                        bot.navigator().stop();
                        body.stopInputs();
                        bot.sinkToDig = true;
                        bot.actions().breakTick(q);
                        return Result.RUNNING;
                    }
                }
                // Through: up.
                body.stopInputs();
                body.jump = true;
                return Result.RUNNING;
            }
            case SWIM -> {
                bot.navigator().stop();
                body.stopInputs();
                body.jump = true;
                BlockPos open = swimColumn != null && !triedColumns.contains(swimColumn.atY(0)) ? swimColumn : openColumn(level, feet, triedColumns);
                swimColumn = open;
                if (open != null && (open.getX() != feet.getX() || open.getZ() != feet.getZ())) {
                    body.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(open.atY(feet.getY())));
                    body.forward = 1f;
                }
                return Result.RUNNING;
            }
            case BACK -> {
                if (!started) {
                    bot.navigator().goTo(back);
                    bot.navigator().forAir = true;
                    started = true;
                }
            }
            default -> {
                if (!started) {
                    // (Steered to where it last breathed, when that is known: straight up is into
                    // the roof as often as not.)
                    BlockPos steer = bot.params.on("airsafe") && crumbs != null && !crumbs.isEmpty() ? crumbs.peekFirst() : feet.above(8);
                    bot.navigator().setGoal(p -> breathable(level, p), steer);
                    bot.navigator().forAir = true;
                    started = true;
                }
            }
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s == BotNavigator.Status.FAILED) {
            started = false;
            fails++;
        } else if (s == BotNavigator.Status.ARRIVED) {
            started = false;
        }
        return Result.RUNNING;
    }

    /**
     * What to try next: a roof of a block or two with air over it, dug through; the way it came,
     * back to the last spot of its trail with air; the nearest water open to the sky, swum to;
     * last, whatever is over the head, dug at.
     */
    private Way next(Bot bot, ServerLevel level, BlockPos feet) {
        if (bot.params.on("airsafe")) return nextSafe(bot, level, feet);
        if (thinRoof(level, BlockPos.containing(bot.body().getX(), bot.body().getEyeY(), bot.body().getZ()))) return Way.DIG;
        if (!backTried) {
            backTried = true;
            back = trailBack(bot, level, feet);
            if (back != null) {
                bot.say("no air above: back the way I came, to " + back.toShortString());
                return Way.BACK;
            }
        }
        if (way == Way.SWIM && swimColumn != null) triedColumns.add(swimColumn.atY(0));
        swimColumn = null;
        if (openColumn(level, feet, triedColumns) != null) return Way.SWIM;
        return Way.DIG;
    }

    /**
     * What to try next, the ways that got nowhere left out (all of them spent: from the top
     * again): a thin roof dug through; back along the dive; the way it came by the trail; still
     * water open to the sky close by; the water round its own two blocks walled off, so that what
     * fills them runs off and leaves air (a player's block in the flow); the path finder's search;
     * last, the roof dug at.
     */
    private Way nextSafe(Bot bot, ServerLevel level, BlockPos feet) {
        if (spent.size() >= Way.values().length - 1) spent.clear();
        BlockPos head = BlockPos.containing(bot.body().getX(), bot.body().getEyeY(), bot.body().getZ());
        boolean quickDig = digTicksUp(bot, level, head) < bot.body().getAirSupply() * 0.7;
        if (!spent.contains(Way.DIG) && thinRoof(level, head) && quickDig) return Way.DIG;
        if (!spent.contains(Way.RETRACE) && crumbs != null && !crumbs.isEmpty()) return Way.RETRACE;
        if (!spent.contains(Way.BACK) && !backTried) {
            backTried = true;
            back = trailBack(bot, level, feet);
            if (back != null) {
                bot.say("no air above: back the way I came, to " + back.toShortString());
                return Way.BACK;
            }
        }
        if (way == Way.SWIM && swimColumn != null) triedColumns.add(swimColumn.atY(0));
        swimColumn = null;
        if (!spent.contains(Way.SWIM) && openColumn(level, feet, triedColumns) != null) return Way.SWIM;
        if (!spent.contains(Way.SEAL) && sealable(bot, level, feet)) return Way.SEAL;
        if (!spent.contains(Way.SEARCH)) return Way.SEARCH;
        return Way.DIG;
    }

    /**
     * Ticks to dig up through what is over the head to the air (under water a block takes five
     * times as long, standing; the water above the roof swum at some eight ticks a block). A
     * lot when there is no air within eight blocks.
     */
    private static double digTicksUp(Bot bot, ServerLevel level, BlockPos head) {
        double ticks = 0;
        for (int up = 1; up <= 8; up++) {
            BlockPos q = head.above(up);
            if (blocksTheWayUp(level, q)) ticks += bot.tools().breakTicks(level.getBlockState(q)) * 5;
            else if (!level.getFluidState(q).isEmpty()) ticks += 8;
            else return ticks;
        }
        return 10_000;
    }

    /**
     * One tick along the dive's way back: to the next spot of it (the newest first), steered
     * straight there - each was a step of the way in, so the way back needs no search. False
     * when it gets nowhere (three seconds without reaching the next spot).
     */
    private boolean retrace(Bot bot, ServerLevel level, BlockPos feet) {
        var body = bot.body();
        while (!crumbs.isEmpty()) {
            BlockPos c = crumbs.peekLast();
            double dx = c.getX() + 0.5 - body.getX(), dz = c.getZ() + 0.5 - body.getZ();
            boolean there = feet.equals(c) || dx * dx + dz * dz < 0.3 && Math.abs(feet.getY() - c.getY()) <= 1 && crumbs.size() > 1;
            if (!there) break;
            crumbs.pollLast();
            crumbTicks = 0;
        }
        if (crumbs.isEmpty()) return false;
        if (++crumbTicks > 60) return false;
        BlockPos c = crumbs.peekLast();
        bot.navigator().stop();
        body.stopInputs();
        net.minecraft.world.phys.Vec3 aim = net.minecraft.world.phys.Vec3.atBottomCenterOf(c).add(0, 0.5, 0);
        body.lookAt(aim);
        double dx = aim.x - body.getX(), dz = aim.z - body.getZ();
        // (Straight above or below: no step forward, only up or down.)
        body.forward = dx * dx + dz * dz > 0.04 ? 1f : 0f;
        // Up where the way goes up (and level, the head kept up); down by letting itself sink.
        if (c.getY() >= feet.getY()) body.jump = true;
        else bot.sinkToDig = true;
        return true;
    }

    /**
     * Whether its own two blocks can be walled off from the water round them: blocks in the pack,
     * the water in them flowing in from beside or above (not still water - a lake, a sea - which
     * stays), and solid rock about to set the blocks against.
     */
    private static boolean sealable(Bot bot, ServerLevel level, BlockPos feet) {
        if (!bot.actions().hasThrowaway() && net.kasax.challengecraft.bot.BotActions.buildingBlocks(bot.body()) == 0) return false;
        BlockPos head = feet.above();
        var hf = level.getFluidState(head);
        if (hf.isEmpty() || hf.isSource()) return false;
        int water = 0;
        for (BlockPos q : sealCells(feet)) if (!level.getFluidState(q).isEmpty() && level.getBlockState(q).getCollisionShape(level, q).isEmpty()) water++;
        return water > 0 && water <= 5;
    }

    /** The blocks round its feet and head that water can come in by: the four sides of each, and over the head. */
    private static java.util.List<BlockPos> sealCells(BlockPos feet) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            out.add(feet.relative(d));
            out.add(feet.above().relative(d));
        }
        out.add(feet.above(2));
        return out;
    }

    /**
     * One tick of walling off: a block into each blocks-round with water in it (standing on the
     * bottom), then waiting for the water in its own two to run off. False when it cannot be done
     * or the air does not come (four seconds).
     */
    private boolean seal(Bot bot, ServerLevel level, net.kasax.challengecraft.bot.BotPlayer body) {
        if (++sealTicks > 80) return false;
        BlockPos feet = body.blockPosition();
        bot.navigator().stop();
        body.stopInputs();
        bot.sinkToDig = true;
        for (BlockPos q : sealCells(feet)) {
            if (level.getFluidState(q).isEmpty() || !level.getBlockState(q).getCollisionShape(level, q).isEmpty()) continue;
            if (!bot.actions().placeThrowaway(q) && !bot.actions().placeAnyBlock(q)) return sealTicks < 20;
            return true;
        }
        // All shut: the water left in its two blocks runs off (flowing water with nothing to feed it).
        return true;
    }

    /** Something over the head that keeps it from swimming up: a block (waterlogged or dry), a cobweb. */
    private static boolean blocksTheWayUp(ServerLevel level, BlockPos q) {
        var s = level.getBlockState(q);
        return !s.getCollisionShape(level, q).isEmpty() || s.is(net.minecraft.world.level.block.Blocks.COBWEB);
    }

    /** One or two solid blocks over the head, and air (not more water) right over them. */
    private static boolean thinRoof(ServerLevel level, BlockPos head) {
        for (int up = 1; up <= 3; up++) {
            BlockPos q = head.above(up);
            boolean solid = !level.getBlockState(q).getCollisionShape(level, q).isEmpty();
            if (!solid) return up > 1 && level.getFluidState(q).isEmpty();
        }
        return false;
    }

    /** The last spot on the way it came (its trail, newest first) where the head is in the air, not too far back. */
    private static BlockPos trailBack(Bot bot, ServerLevel level, BlockPos feet) {
        var trail = bot.trail;
        for (int i = trail.size() - 1, n = 0; i >= 0 && n < 12; i--, n++) {
            BlockPos p = trail.get(i);
            if (p.distSqr(feet) > 40 * 40) break;
            for (int up = 0; up <= 1; up++) if (breathable(level, p.above(up))) return p.above(up);
        }
        return null;
    }

    /** Nothing but still water from the feet up to the air, at most {@code max} blocks (water falling down a shaft is no way up). */
    private static boolean openAbove(ServerLevel level, BlockPos feet, int max) {
        for (int up = 1; up <= max; up++) {
            BlockPos q = feet.above(up);
            if (blocksTheWayUp(level, q)) return false;
            var fluid = level.getFluidState(q);
            // (Not water: the air - or a cobweb, which is no air - see blocksTheWayUp.)
            if (fluid.isEmpty()) return true;
            if (!fluid.isSource()) return false;
        }
        return false;
    }

    /**
     * The nearest column (within six) where nothing but still water stands between the feet's
     * level and the air above - not water falling down, which no one swims up - leaving out the
     * columns already tried.
     */
    private static BlockPos openColumn(ServerLevel level, BlockPos feet, java.util.Set<BlockPos> tried) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = dx * dx + dz * dz;
                if (d >= bestD) continue;
                BlockPos p = feet.offset(dx, 0, dz);
                if (tried.contains(p.atY(0))) continue;
                for (int up = 0; up <= 16; up++) {
                    BlockPos q = p.above(up);
                    if (blocksTheWayUp(level, q)) break;
                    var fluid = level.getFluidState(q);
                    if (fluid.isEmpty()) {
                        best = p;
                        bestD = d;
                        break;
                    }
                    if (!fluid.isSource()) break;
                }
            }
        }
        return best;
    }

    /**
     * The blocks round {@code feet} for the log, one layer a line from the top down, each layer
     * its rows (north to south) of columns (west to east), the feet's column in the middle:
     * {@code #} solid, {@code W} solid and waterlogged, {@code ~} still water, {@code ,} flowing
     * water, {@code L} lava, {@code .} air, {@code o} anything else one passes through, {@code F}
     * and {@code H} the bot's feet and head.
     */
    public static String scene(ServerLevel level, BlockPos feet, int r, int below, int above) {
        StringBuilder sb = new StringBuilder();
        for (int dy = above; dy >= -below; dy--) {
            sb.append(String.format("  y %d%s: ", feet.getY() + dy, dy == 0 ? " (feet)" : dy == 1 ? " (head)" : ""));
            for (int dz = -r; dz <= r; dz++) {
                if (dz > -r) sb.append('|');
                for (int dx = -r; dx <= r; dx++) {
                    BlockPos q = feet.offset(dx, dy, dz);
                    var st = level.getBlockState(q);
                    var fluid = level.getFluidState(q);
                    boolean solid = !st.getCollisionShape(level, q).isEmpty();
                    char c = solid ? (fluid.isEmpty() ? '#' : 'W')
                            : fluid.is(net.minecraft.tags.FluidTags.LAVA) ? 'L'
                            : !fluid.isEmpty() ? (fluid.isSource() ? '~' : ',')
                            : st.isAir() ? '.' : 'o';
                    // (The bot's own feet and head, where it can be: F and H.)
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == 1) && !solid) c = dy == 0 ? 'F' : 'H';
                    sb.append(c);
                }
            }
            if (dy > -below) sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String describe() {
        return "up for air";
    }
}
