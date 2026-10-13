package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/**
 * The player's way out of a fight going badly (a skeleton shooting from afar, zombies all round):
 * three blocks straight down, a block over the head, eat, wait until the hearts are back, then up
 * again. Nothing can reach or shoot into a 1 x 1 hole with a lid.
 */
public final class HideTask implements BotTask {
    private static final int MAX_TICKS = 900;
    private int ticks, dug, lidTries, reopened, strays;
    private BlockPos top;
    private boolean closed;

    /** Something that hurts in the spot itself (a berry bush, a cactus beside it, fire): no shelter there. */
    static boolean hurtsHere(ServerLevel level, BlockPos feet) {
        for (BlockPos p : List.of(feet, feet.above())) {
            var s = level.getBlockState(p);
            if (s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) || s.is(net.minecraft.tags.BlockTags.FIRE)
                    || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || s.is(net.minecraft.world.level.block.Blocks.COBWEB)
                    // (Vines, a ladder: it hangs on them and never drops into its hole - seed 11
                    // was shot so, "dug in" at the surface.)
                    || s.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return true;
            for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(p.relative(d)).is(net.minecraft.world.level.block.Blocks.CACTUS)) return true;
        }
        return false;
    }

    /** Whether hiding right here works: solid ground two deep, nothing liquid, something to dig with. */
    public static boolean possible(Bot bot) {
        BlockPos feet = standing(bot);
        return feet != null && possibleAt(bot, feet);
    }

    /**
     * Where its feet are on the ground: where it stands, or - in the air for a moment, a jump in a
     * fight, thrown up by a blow - where it lands, a block or so below. Null in the water, or
     * falling further. (Asked mid-jump, every way out had been "no cover here" with a hundred
     * blocks in the pack - a dozen times in a self-play league.)
     */
    public static BlockPos standing(Bot bot) {
        BotPlayer body = bot.body();
        if (body.isInWater()) return null;
        BlockPos feet = body.blockPosition();
        if (body.onGround()) return feet;
        if (body.fallDistance > 2 || body.isPassenger()) return null;
        var level = body.level();
        for (int down = 1; down <= 2; down++) {
            BlockPos under = feet.below(down);
            if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty()) return under.above();
        }
        return null;
    }

    /** Whether hiding with the feet at {@code feet} would work (see {@link #possible}). */
    public static boolean possibleAt(Bot bot, BlockPos feet) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (hurtsHere(level, feet)) return false;
        // (Nothing in the pack for the lid: what it digs out is the lid, if it drops - stone to a
        // pickaxe, dirt to a hand.)
        if (!bot.actions().hasThrowaway() && !bot.tools().canHarvest(level.getBlockState(feet.below()))) return false;
        for (int i = 1; i <= 4; i++) {
            BlockPos p = feet.below(i);
            var s = level.getBlockState(p);
            if (s.getCollisionShape(level, p).isEmpty() || s.getDestroySpeed(level, p) < 0) return false;
            if (i <= 3 && bot.tools().breakTicks(s) > 40) return false;
            for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return false;
            if (s.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) return false;
        }
        return true;
    }

    /**
     * The night's shelter for a bot come back to life with nothing (no weapon, no blocks) and the
     * monsters about the spawn: dug in with bare hands (the dug dirt is the lid), and out again in
     * the morning - instead of dying to them again every half minute (seed 77: twelve times).
     */
    private boolean untilDay;
    private int dawnTicks;

    public static HideTask shelter() {
        HideTask t = new HideTask();
        t.untilDay = true;
        return t;
    }

    /** Whether a shelter can be dug here with bare hands: soft ground three deep that drops itself (dirt, grass). */
    public static boolean shelterPossible(Bot bot) {
        return shelterProblem(bot) == null;
    }

    /** Ground dug quickly with bare hands, that drops itself (the lid): dirt and its kind. */
    private static boolean soft(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || s.is(net.minecraft.world.level.block.Blocks.PODZOL) || s.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
                || s.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT) || s.is(net.minecraft.world.level.block.Blocks.ROOTED_DIRT)
                || s.is(net.minecraft.world.level.block.Blocks.DIRT);
    }

    /** Why no shelter can be dug here (null: it can). */
    public static String shelterProblem(Bot bot) {
        BotPlayer body = bot.body();
        BlockPos feet = standing(bot);
        if (feet == null) return "not on dry ground";
        return shelterProblemAt((ServerLevel) body.level(), feet);
    }

    /**
     * The nearest spot on the ground within {@code radius} (and not far up or down) where it can
     * dig in, either way, or null - to go to instead of a few steps anywhere: on seed 11 red sand
     * all about kept it from digging in, and the night went on with logs chopped in the dark.
     */
    public static BlockPos spotNear(Bot bot, int radius) {
        ServerLevel level = (ServerLevel) bot.body().level();
        BlockPos at = bot.body().blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = dx * dx + dz * dz;
                if (d > radius * radius || d >= bestD) continue;
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(dx, 0, dz));
                if (Math.abs(top.getY() - at.getY()) > 6) continue;
                if (!level.getBlockState(top).getCollisionShape(level, top).isEmpty() || !level.getFluidState(top).isEmpty()) continue;
                if (possibleAt(bot, top) || shelterProblemAt(level, top) == null) {
                    best = top;
                    bestD = d;
                }
            }
        }
        return best;
    }

    static String shelterProblemAt(ServerLevel level, BlockPos feet) {
        if (hurtsHere(level, feet)) return "something that hurts here";
        for (int i = 1; i <= 4; i++) {
            BlockPos p = feet.below(i);
            var s = level.getBlockState(p);
            if (s.getCollisionShape(level, p).isEmpty() || s.getDestroySpeed(level, p) < 0) return "no floor " + i + " down";
            if (i <= 3 && !soft(s)) return s.getBlock() + " " + i + " down";
            for (Direction d : Direction.values()) if (!level.getFluidState(p.relative(d)).isEmpty()) return "water " + i + " down";
        }
        return null;
    }

    /** Up instead of down: three blocks on a pillar, out of reach of zombies (they hit on while one digs). */
    private boolean up;
    private int placed;

    public static HideTask upward() {
        HideTask t = new HideTask();
        t.up = true;
        return t;
    }

    /**
     * Walled in where it stands, instead of dug in (stone underfoot and nothing to dig it with,
     * sand, water close below): the four sides at the feet and at the head, and a lid - as a
     * player boxes himself in. No arrow gets in, no spider, no zombie.
     */
    private boolean box;

    public static HideTask boxed() {
        HideTask t = new HideTask();
        t.box = true;
        return t;
    }

    /** The night's shelter as a box of blocks (see {@link #boxed()}), until the morning. */
    public static HideTask boxedShelter() {
        HideTask t = boxed();
        t.untilDay = true;
        return t;
    }

    /** A box round these feet: the four sides at the feet and at the head, the lid over the head. */
    private static List<BlockPos> boxBlocks(BlockPos feet) {
        List<BlockPos> out = new java.util.ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) out.add(feet.relative(d));
        for (Direction d : Direction.Plane.HORIZONTAL) out.add(feet.above().relative(d));
        out.add(feet.above(2));
        return out;
    }

    /** Whether a box works here: dry ground, nothing that hurts in the spot, blocks enough for the open sides. */
    public static boolean boxPossible(Bot bot) {
        return boxPossible(bot, false);
    }

    /** The same; in an emergency ({@code all}) the stone kept back for a furnace counts too. */
    public static boolean boxPossible(Bot bot, boolean all) {
        return boxProblem(bot, all) == null;
    }

    /** Why no box can go up here (null: it can) - for the log when no cover is to be had. */
    public static String boxProblem(Bot bot, boolean all) {
        BotPlayer body = bot.body();
        BlockPos feet = standing(bot);
        if (feet == null) return body.isInWater() ? "in the water" : "not on the ground";
        ServerLevel level = (ServerLevel) body.level();
        if (hurtsHere(level, feet)) return "something that hurts here";
        if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) return "no floor";
        int open = 0;
        for (BlockPos p : boxBlocks(feet)) {
            if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) return "lava beside";
            if (!shut(level, p)) open++;
        }
        // (And one on a wall for the lid to rest against, unless something is there already.)
        if (!shut(level, feet.above(2)) && !held(level, feet.above(2))) open++;
        int blocks = all ? net.kasax.challengecraft.bot.BotActions.buildingBlocks(body) : bot.actions().spendableBlocks();
        return blocks >= open ? null : "blocks " + blocks + " of " + open;
    }

    /** Whether a pillar works here: room above the head for two more blocks, something to build with. */
    public static boolean pillarPossible(Bot bot) {
        BotPlayer body = bot.body();
        BlockPos feet = standing(bot);
        if (feet == null || !bot.actions().hasThrowaway()) return false;
        ServerLevel level = (ServerLevel) body.level();
        BlockPos head = feet.above();
        for (int i = 1; i <= 4; i++) if (!level.getBlockState(head.above(i)).getCollisionShape(level, head.above(i)).isEmpty()) return false;
        return true;
    }

    /** The shield raised here (towards a bowman shooting in through a gap): lowered again on the way out. */
    private boolean shielding;

    @Override
    public Result tick(Bot bot) {
        Result r = step(bot);
        if (r != Result.RUNNING) lowerShield(bot.body());
        return r;
    }

    private Result step(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (!body.isAlive()) return Result.DONE;
        // (Out of time, but hurt with one still by the hole: a while longer - out at 11 hp into
        // the zombie that waited, it died on seed 44.)
        // (Nor out of the hole at night with them still waiting by it, healthy or not: out at
        // full health into three zombies and a skeleton, walled in again in the open, dead - a
        // while longer, as long as they stay, up to the limit.)
        if (++ticks > (untilDay ? 13000 : MAX_TICKS) && (ticks > 3 * MAX_TICKS || untilDay
                || body.getHealth() >= 16 && window == null && !(level.isDarkOutside() && monsterBy(body)) || !monsterBy(body))) return Result.DONE;
        // (Called for mid-jump, or thrown up by a blow: from where it lands.)
        if (top == null) {
            if (!body.onGround()) return Result.RUNNING;
            top = body.blockPosition();
        }
        if (up) return pillar(bot, body, level);
        body.stopInputs();
        // Shut in, well and armed, and something that only strikes close waiting at the box: struck
        // through a window at the feet (see windowFight).
        if (box && windowFight(bot, body, level)) return Result.RUNNING;
        // (Blown open since - a ghast's fireball on the lid, a creeper by the box: closed again.)
        if (closed && placed + dug > 0) {
            boolean open = false;
            if (box) {
                for (BlockPos p : boxBlocks(top)) open |= !shut(level, p);
            } else if (dug >= 3) {
                open = !shut(level, body.blockPosition().above(2));
            }
            if (open && reopened++ < 6) {
                closed = false;
                lidTries = 0;
            }
        }
        // The box not shut yet - a side that will not go in - and one at it through the gap: struck
        // from where it stands, not stood still under its blows (in a self-play game a spider bit
        // seven times through the one side that would not go in, and not one blow came back). A
        // gap clear of it gets its block first; a blow throws it out of the gap. (Into the ground,
        // the digging goes first: a few blows of the shovel and it is out of their reach.)
        boolean stuck = box && !sealed(level) && (closed || !sideClear(level, body)
                || net.kasax.challengecraft.bot.BotActions.buildingBlocks(body) == 0);
        if (stuck && defend(bot, body, level)) return Result.RUNNING;
        // The same with a bowman shooting in through the gap from afar: the shield up towards it
        // while no block can go in, as a player waits behind it (in a self-play game a skeleton
        // twelve blocks off put five arrows through the side that would not go in - the shield on
        // the arm, down). A side that keeps refusing to go in is tried again once a second.
        if ((stuck || box && !closed && lidTries > 10 && ticks % 20 != 0) && shieldAgainstShooter(body, level)) return Result.RUNNING;
        lowerShield(body);
        if (box) {
            if (!closed) return boxIn(bot, body, level);
        } else {
            // Down: the block under the feet, three times (it drops into the gap), so the lid goes in
            // at ground level, held by the ground round it.
            dug = top.getY() - body.blockPosition().getY();
            // (Not getting down at all - hanging on something, the block will not break: given up,
            // so somewhere else is tried, instead of standing in the open "dug in".)
            if (dug < 3 && ticks > 200 && dug <= 0) return Result.FAILED;
            if (dug < 3) {
                if (!body.onGround()) return Result.RUNNING;
                // Over the middle of the block first, or it would stay standing on the next one.
                if (!centred(body)) return Result.RUNNING;
                BlockPos below = body.blockPosition().below();
                bot.tools().equipFor(level.getBlockState(below));
                bot.actions().breakTick(below);
                return Result.RUNNING;
            }
            // The lid: a block where the feet were.
            if (!closed) {
                BlockPos lid = body.blockPosition().above(2);
                if (!body.onGround()) return Result.RUNNING; // (still dropping in: the head is in the way)
                if (!level.getBlockState(lid).getCollisionShape(level, lid).isEmpty()) closed = true;
                // (Stone, not dirt or netherrack, for the lid: a fireball on it does not tear it open.)
                else if (!bot.actions().placeSturdy(lid) && ++lidTries > (untilDay ? 400 : 20)) closed = true; // (it will not go: as it is)
                return Result.RUNNING;
            }
        }
        // Safe: eat, and wait for the hearts (natural regeneration wants a full-ish stomach).
        // (Not in a box with a side still open and one about: a meal takes the hands for seconds,
        // and the blows through the gap went unanswered - so died a self-play game, eating.)
        boolean exposed = box && !sealed(level) && !level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                body.getBoundingBox().inflate(6), m -> m.isAlive() && (m.getTarget() == body || body.getLastHurtByMob() == m)).isEmpty();
        if (body.getFoodData().getFoodLevel() < 20 && EatTask.bestFood(body) >= 0 && !exposed) {
            bot.interject(new EatTask());
            return Result.RUNNING;
        }
        // (The night's shelter: until the sun is up - the zombies and skeletons burn then.)
        // (And not out while one still stands by the hole: in the shade or not burning yet, a
        // zombie waiting at dawn finished it off as it climbed out on seed 66. A while longer.)
        if (untilDay) {
            if (level.isDarkOutside()) return Result.RUNNING;
            if (++dawnTicks < 2400 && monsterBy(body)) return Result.RUNNING;
            return Result.DONE;
        }
        // (The hearts back, but the bowmen still by the hole: a while longer, as long as they stay
        // and the time lasts - seed 88, hard: out at full health into the same patrol, and shot.)
        boolean waiting = monsterBy(body);
        if (body.getHealth() >= 16 && (!waiting || ticks > MAX_TICKS && !level.isDarkOutside()) || body.getFoodData().getFoodLevel() < 18 && ticks > 200 && !waiting) return Result.DONE;
        return Result.RUNNING;
    }

    /**
     * A monster that would be waiting outside: within ten blocks (not an enderman, not a spider by
     * day), or a bowman within twenty-four - one in the shade of a tree at dawn shot it as it
     * climbed out with thirteen hearts and nothing to eat on seed 77.
     */
    private static boolean monsterBy(BotPlayer body) {
        boolean day = !body.level().isDarkOutside();
        // (Few hearts left: anything within the twenty-four counts - two blows of a zombie that
        // stood fifteen blocks off in the shade of a ravine at dawn ended it at nine hp on seed 77.)
        double close = body.getHealth() < 12 ? 24 : 11;
        // (A ghast after him waits up there as long as it likes: out into its fireballs again it was
        // in the game against a player - it is no "monster" to the game, a mob of its own.)
        if (!body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Ghast.class, body.getBoundingBox().inflate(48, 24, 48),
                g -> g.isAlive() && g.getTarget() == body).isEmpty()) return true;
        return !body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                body.getBoundingBox().inflate(24, 10, 24), m -> m.isAlive()
                        && !(m instanceof net.minecraft.world.entity.monster.Enderman)
                        && !(day && m instanceof net.minecraft.world.entity.monster.spider.Spider)
                        && (net.kasax.challengecraft.bot.Guardian.shooter(m) || m.distanceToSqr(body) < close * close)).isEmpty();
    }

    /** The box going up, a block a tick: the sides at the feet, at the head, then the lid. */
    private Result boxIn(Bot bot, BotPlayer body, ServerLevel level) {
        if (!body.onGround()) return Result.RUNNING;
        // (Round where it stands now, until the first block is in: a dodge or a blow may have
        // moved it since the start.)
        if (placed == 0) top = body.blockPosition();
        else if (!body.blockPosition().equals(top)) {
            // Knocked out of the half-built box by a blow: back into it, a step or two away; else
            // a new box where it stands.
            double dx = top.getX() + 0.5 - body.getX(), dz = top.getZ() + 0.5 - body.getZ();
            if (body.blockPosition().getY() == top.getY() && dx * dx + dz * dz < 9 && ++strays < 80) {
                body.setYRot((float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f);
                body.forward = 0.6f;
                return Result.RUNNING;
            }
            top = body.blockPosition();
            placed = 0;
        }
        strays = 0;
        // Over the middle of the block first: a side block cannot go where a shoulder is.
        if (!centred(body)) return Result.RUNNING;
        boolean open = false;
        BlockPos lid = top.above(2);
        for (BlockPos p : boxBlocks(top)) {
            if (shut(level, p)) continue;
            open = true;
            // A flower, a sapling, a torch where a side goes: no wall, and nothing goes in where it
            // stands - broken first (a moment's work), as a player clears the spot.
            var there = level.getBlockState(p);
            if (!there.canBeReplaced() && there.getDestroySpeed(level, p) >= 0 && there.getDestroySpeed(level, p) < 1) {
                bot.actions().breakTick(p);
                return Result.RUNNING;
            }
            // The lid over the head touches nothing (the walls end at the head): a block on top
            // of a wall first, for it to be set against - else every box stayed open at the top,
            // and a spider came over the wall.
            if (p.equals(lid) && !held(level, lid)) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos rest = lid.relative(d);
                    if (level.getBlockState(rest).canBeReplaced() && !level.getBlockState(rest.below()).getCollisionShape(level, rest.below()).isEmpty()
                            && bot.actions().placeSturdy(rest)) {
                        placed++;
                        return Result.RUNNING;
                    }
                }
                continue;
            }
            // A side over a drop (at the edge of a ledge, a step down beside it): nothing to set
            // it against. A block under it first, against the floor it stands on - as a player
            // builds out over an edge.
            if (!held(level, p) && p.getY() == top.getY() && level.getBlockState(p.below()).canBeReplaced()
                    && held(level, p.below()) && bot.actions().placeSturdy(p.below())) {
                placed++;
                return Result.RUNNING;
            }
            if (bot.actions().placeSturdy(p)) {
                placed++;
                return Result.RUNNING;
            }
        }
        // (What will not go - a mob standing in the way, nothing to set it against yet - is
        // tried again a while; then the box is as it is.)
        if (!open || ++lidTries > (untilDay ? 400 : 60)) {
            closed = true;
            if (open) bot.say("box: not every side would go in (" + gaps(bot, level) + ")");
        }
        return Result.RUNNING;
    }

    /** The sides still open and why, for the log: "east feet: nothing to set it against", ... */
    private String gaps(Bot bot, ServerLevel level) {
        List<String> out = new java.util.ArrayList<>();
        for (BlockPos p : boxBlocks(top)) {
            if (shut(level, p)) continue;
            String where = p.getY() > top.getY() + 1 ? "lid"
                    : net.minecraft.core.Direction.getApproximateNearest(p.getX() - top.getX(), 0, p.getZ() - top.getZ()).getName()
                    + (p.getY() == top.getY() ? " feet" : " head");
            var in = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p),
                    e -> e.isAlive() && e != bot.body());
            var there = level.getBlockState(p);
            String why = !in.isEmpty() ? in.get(0).getType().toShortString() + " in it"
                    : !there.canBeReplaced() ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(there.getBlock()).getPath() + " in it"
                    : net.kasax.challengecraft.bot.BotActions.buildingBlocks(bot.body()) == 0 ? "no blocks left"
                    : !held(level, p) ? "nothing to set it against" : "would not go";
            out.add(where + ": " + why);
        }
        return String.join(", ", out);
    }

    /** Whether a block at {@code p} would have something beside it to be set against. */
    private static boolean held(ServerLevel level, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockPos q = p.relative(d);
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return true;
        }
        return false;
    }

    /** Whether the box is shut: every side in, and the lid. */
    private boolean sealed(ServerLevel level) {
        if (top == null) return false;
        for (BlockPos p : boxBlocks(top)) if (!shut(level, p)) return false;
        return true;
    }

    /**
     * Whether a side of the box (or the lid) at {@code p} is in: something solid there, at least
     * half a block high. A flower, a sapling, a torch is no wall - a spider walks in through it,
     * an arrow flies through - though nothing can be put where it stands until it is broken.
     */
    public static boolean shut(net.minecraft.world.level.BlockGetter level, BlockPos p) {
        var shape = level.getBlockState(p).getCollisionShape(level, p);
        return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) >= 0.5;
    }

    /**
     * Whether a side of the box still open can have its block now: nothing standing in it, and
     * something beside it to set the block against (the head's side over a gap at the feet has
     * nothing until the gap is shut).
     */
    private boolean sideClear(ServerLevel level, BotPlayer body) {
        if (top == null) return true;
        for (BlockPos p : boxBlocks(top)) {
            if (shut(level, p)) continue;
            // (The lid: once a wall at the head is in, a block on it to rest the lid on - see boxIn.)
            boolean against = held(level, p);
            if (!against && p.equals(top.above(2))) {
                for (Direction d : Direction.Plane.HORIZONTAL) against |= shut(level, top.above().relative(d));
            }
            if (against && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p),
                    e -> e.isAlive() && e != body).isEmpty()) return true;
        }
        return false;
    }

    /** The window at the feet of the box, opened to strike through (see windowFight); null while there is none. */
    private BlockPos window;
    private int windowIdle;

    private static boolean armed(BotPlayer body) {
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) return true;
        }
        return false;
    }

    /** One that only strikes close and is out for it: a zombie, a spider - not a bowman, not a creeper (a blow, and it may blow the box open). */
    private static boolean closeStriker(net.minecraft.world.entity.Mob m, BotPlayer body) {
        return m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy && !net.kasax.challengecraft.bot.Guardian.shooter(m)
                && !(m instanceof net.minecraft.world.entity.monster.Creeper) && !(m instanceof net.minecraft.world.entity.monster.Enderman)
                && (m.getTarget() == body || body.getLastHurtByMob() == m);
    }

    /**
     * The side to open towards {@code m}: the one it is at, else the other one it is towards (it
     * stands at a corner) - where there is room outside for it to come to; null when neither has.
     */
    private Direction windowSide(ServerLevel level, net.minecraft.world.entity.Entity m) {
        double dx = m.getX() - (top.getX() + 0.5), dz = m.getZ() - (top.getZ() + 0.5);
        Direction ew = dx > 0 ? Direction.EAST : Direction.WEST, ns = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        for (Direction d : Math.abs(dx) > Math.abs(dz) ? new Direction[]{ew, ns} : new Direction[]{ns, ew}) {
            BlockPos outside = top.relative(d, 2);
            if (!shut(level, outside) && !shut(level, outside.above())) return d;
        }
        return null;
    }

    /**
     * Shut in, well and armed, with something that only strikes close waiting at the box (two
     * zombies at a box in a tunnel kept it walled in fourteen times over in a self-play game,
     * five minutes): a window at the feet towards the nearest, and it is struck through that,
     * crouched - as players do. It cannot strike back: its eyes are a head higher than the hole
     * (it does not see in, and a mob strikes only what it sees), it cannot get into a hole a block
     * high, and its reach ends a step short. Shut again once nothing has shown in it a while, or
     * the one waiting stands at another side (a window there next). Whether the window is in use.
     */
    private boolean windowFight(Bot bot, BotPlayer body, ServerLevel level) {
        if (top == null || !body.onGround() || !body.blockPosition().equals(top)) {
            // (Out of the box - a blow, a dodge: the window is left as it is, the box sees to the gap.)
            window = null;
            return false;
        }
        net.minecraft.world.entity.Mob near = null;
        double best = Double.MAX_VALUE;
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(5, 2, 5), m -> closeStriker(m, body))) {
            double d = m.distanceToSqr(body);
            if (d < best) {
                best = d;
                near = m;
            }
        }
        boolean fit = armed(body) && body.getHealth() >= 12;
        if (window == null) {
            if (near == null || !fit || !sealed(level)) return false;
            Direction side = windowSide(level, near);
            if (side == null) return false;
            BlockPos w = top.relative(side);
            var st = level.getBlockState(w);
            // (Not through bedrock, nor a long dig; nothing liquid beyond that would run in.)
            if (st.getDestroySpeed(level, w) < 0 || bot.tools().breakTicks(st) > 40) return false;
            if (!level.getFluidState(w.relative(side)).isEmpty() || !level.getFluidState(w.above()).isEmpty()) return false;
            window = w;
            windowIdle = 0;
            bot.say("shut in, a " + near.getType().toShortString() + " waiting outside: a window at the feet to strike through");
        }
        // Crouched: the eyes low enough to see out through a hole at the feet.
        body.sneak = true;
        if (shut(level, window)) {
            if (!fit || near == null) {
                window = null;
                return false;
            }
            bot.actions().breakTick(window);
            return true;
        }
        // Open: whatever shows in it within reach, struck at full strength.
        net.minecraft.world.entity.Mob target = null;
        net.minecraft.world.phys.Vec3 aim = null;
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(4, 2, 4), m -> closeStriker(m, body))) {
            var p = seenThrough(body, level, m);
            if (p != null) {
                target = m;
                aim = p;
                break;
            }
        }
        if (target != null && fit) {
            windowIdle = 0;
            KillTask.equipWeapon(bot);
            body.lookAt(aim);
            if (body.getAttackStrengthScale(0.5f) >= 1f) {
                body.attack(target);
                body.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            }
            return true;
        }
        // Nothing in it: a while (a blow throws them back a step or two, they come again), then
        // shut - at once when no longer fit or nothing waits; soon when the one waiting stands at
        // another side.
        Direction now = near == null ? null : windowSide(level, near);
        boolean elsewhere = now != null && !top.relative(now).equals(window);
        if (++windowIdle > 80 || !fit || near == null || elsewhere && windowIdle > 20) {
            if (shut(level, window) || bot.actions().placeSturdy(window) || windowIdle > 200) window = null;
        }
        return true;
    }

    /** A point of {@code m} within reach that the eyes see through the window (low on its body: the hole is at the feet), or null. */
    private static net.minecraft.world.phys.Vec3 seenThrough(BotPlayer body, ServerLevel level, net.minecraft.world.entity.Mob m) {
        var eye = body.getEyePosition();
        var b = m.getBoundingBox();
        for (double up : new double[]{0.15, 0.4, 0.7}) {
            double y = b.minY + up;
            if (y > b.maxY) break;
            var p = new net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.clamp(eye.x, b.minX + 0.05, b.maxX - 0.05), y,
                    net.minecraft.util.Mth.clamp(eye.z, b.minZ + 0.05, b.maxZ - 0.05));
            if (eye.distanceTo(p) > 3.0) continue;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, p, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, body));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return p;
        }
        return null;
    }

    /**
     * A monster in reach and in sight, after it (or in the way of a side of the box): struck at
     * full strength with the best weapon, without a step out - the weapon held meanwhile, as the
     * arm recharges. Returns whether there was one.
     */
    private boolean defend(Bot bot, BotPlayer body, ServerLevel level) {
        net.minecraft.world.entity.Mob near = null;
        double best = 3.0;
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(4),
                m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy)) {
            // (Not an enderman or the like minding its own business: only what is after it, or
            // stands where a side of the box goes.)
            boolean after = m.getTarget() == body || body.getLastHurtByMob() == m;
            boolean inTheWay = false;
            if (box && top != null) {
                for (BlockPos p : boxBlocks(top)) {
                    if (!shut(level, p) && m.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) inTheWay = true;
                }
            }
            if (!after && !(inTheWay && !(m instanceof net.minecraft.world.entity.monster.Enderman))) continue;
            double d = KillTask.hitDistance(body, m);
            if (d < best && body.hasLineOfSight(m)) {
                best = d;
                near = m;
            }
        }
        if (near == null) return false;
        // (Not the block for the box in hand meanwhile: a change of item starts the arm's charge over.)
        KillTask.equipWeapon(bot);
        body.lookAt(near.getEyePosition());
        if (body.getAttackStrengthScale(0.5f) >= 1f) {
            // (The shield down first: no blow from behind it.)
            if (body.isUsingItem()) {
                body.releaseUsingItem();
                shielding = false;
                return true;
            }
            body.attack(near);
            body.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        } else if (body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)) {
            // Between blows the shield up towards it: its blows end in it, as a player trades blows.
            raiseShield(body, level);
        }
        return true;
    }

    /**
     * Something shooting at it (a bowman, a blaze, a ghast) within its range and in sight, while
     * the box stands open: the shield up towards the nearest. Whether there was one (and a shield
     * on the arm). (Not a witch: her potions go round a shield.)
     */
    private boolean shieldAgainstShooter(BotPlayer body, ServerLevel level) {
        if (!body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)) return false;
        net.minecraft.world.entity.Mob shooter = null;
        double best = Double.MAX_VALUE;
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(64, 24, 64),
                m -> m.isAlive() && m.getTarget() == body && net.kasax.challengecraft.bot.Guardian.shooter(m)
                        && !(m instanceof net.minecraft.world.entity.monster.Witch))) {
            double d = m.distanceTo(body);
            if (d < best && d <= net.kasax.challengecraft.bot.Guardian.shootingRange(m) && body.hasLineOfSight(m)) {
                best = d;
                shooter = m;
            }
        }
        if (shooter == null) return false;
        body.lookAt(shooter.getEyePosition());
        raiseShield(body, level);
        return true;
    }

    private void raiseShield(BotPlayer body, ServerLevel level) {
        if (!body.isUsingItem() || !body.getUseItem().is(net.minecraft.world.item.Items.SHIELD)) {
            if (body.isUsingItem()) body.releaseUsingItem();
            body.gameMode.useItem(body, level, body.getOffhandItem(), net.minecraft.world.InteractionHand.OFF_HAND);
        }
        shielding = true;
    }

    private void lowerShield(BotPlayer body) {
        if (!shielding) return;
        shielding = false;
        if (body.isUsingItem() && body.getUseItem().is(net.minecraft.world.item.Items.SHIELD)) body.releaseUsingItem();
    }

    private Result pillar(Bot bot, BotPlayer body, ServerLevel level) {
        int height = body.blockPosition().getY() - top.getY();
        // Knocked off (a hit sends it flying): start a new pillar from where it landed.
        if (body.onGround() && placed > 0 && height < placed && (body.blockPosition().getX() != top.getX() || body.blockPosition().getZ() != top.getZ())) {
            top = body.blockPosition();
            placed = 0;
            height = 0;
        }
        if (placed < 3 && height < 3) {
            // Jump, and at the top of the jump put a block where the feet were.
            body.jump = true;
            BlockPos below = body.blockPosition().below();
            if (!body.onGround() && level.getBlockState(below).canBeReplaced() && body.getY() - body.blockPosition().getY() < 0.6
                    && bot.actions().placeThrowaway(below)) placed++;
            return Result.RUNNING;
        }
        body.stopInputs();
        if (body.getFoodData().getFoodLevel() < 20 && EatTask.bestFood(body) >= 0) {
            bot.interject(new EatTask());
            return Result.RUNNING;
        }
        if (body.getHealth() >= 16 || body.getFoodData().getFoodLevel() < 18 && ticks > 300) return Result.DONE;
        return Result.RUNNING;
    }

    private static boolean centred(BotPlayer body) {
        BlockPos p = body.blockPosition();
        double dx = p.getX() + 0.5 - body.getX(), dz = p.getZ() + 0.5 - body.getZ();
        if (dx * dx + dz * dz < 0.04) return true;
        body.setYRot((float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f);
        body.forward = 0.3f;
        return false;
    }

    @Override
    public String describe() {
        if (box) return untilDay ? "boxed in for the night" : "boxed in";
        return untilDay ? "dug in for the night" : "hide in the ground";
    }
}
