package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A night in a village bed: the Lockout tile, and the night slept away with it. To a bed that
 * stands in a village (the game's own test: a piece of a village round it), the monsters that
 * would keep him from lying down dealt with (the game will not let one sleep with a monster
 * near), then into it - and only done once he really lies there. (A click with a zombie about
 * was "done" twice in a game on the user's laptop, and the tile never came: the old click task
 * did not look whether he lay down.)
 */
public final class VillageBedTask implements BotTask {
    private static final int MAX_TICKS = 20 * 300;
    /** How long it waits by the bed for the night (the game lets one lie down only in the dark). */
    private static final int MAX_WAIT = 20 * 120;
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos bed;
    private BotTask sub;
    private int ticks, waited, clicks, lay, clickedAt = -100;
    private boolean walking;

    /** Whether this spot is in a village, by the game's own test for the tile. */
    public static boolean inVillage(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, net.minecraft.tags.StructureTags.VILLAGE);
        return start != null && start != StructureStart.INVALID_START && start.isValid();
    }

    /** The nearest bed it knows of (in sight, else remembered) that stands in a village; null if none. */
    public static BlockPos nearestVillageBed(Bot bot, Set<BlockPos> skip) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return null;
        Set<BlockPos> tried = new HashSet<>(skip);
        BlockPos from = bot.body().blockPosition();
        for (int i = 0; i < 12; i++) {
            BlockPos p = BotWorld.nearest(level, from, 24, 12, s -> s.is(BlockTags.BEDS), false, tried);
            if (p == null) p = bot.memory().nearest(level, from, s -> s.is(BlockTags.BEDS), tried);
            if (p == null) return null;
            if (inVillage(level, p) || inVillage(level, headOf(level, p))) return p;
            tried.add(p);
        }
        return null;
    }

    /** The head half of a bed (where the sleeper lies, and what the tile's test looks at). */
    private static BlockPos headOf(ServerLevel level, BlockPos p) {
        var s = level.getBlockState(p);
        if (!s.is(BlockTags.BEDS) || !s.hasProperty(net.minecraft.world.level.block.BedBlock.PART)) return p;
        if (s.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) return p;
        return p.relative(s.getValue(net.minecraft.world.level.block.BedBlock.FACING));
    }

    /** The monsters that keep one from lying down in this bed (the game's own box round it). */
    public static List<Monster> keepingAwake(ServerLevel level, BotPlayer body, BlockPos bed) {
        Vec3 c = Vec3.atBottomCenterOf(bed);
        return level.getEntitiesOfClass(Monster.class, new AABB(c.x - 8, c.y - 5, c.z - 8, c.x + 8, c.y + 5, c.z + 8),
                m -> m.isAlive() && m.isPreventingPlayerRest(level, body));
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > MAX_TICKS) return Result.FAILED;
        // Lying in it: the tile is claimed while he lies there; the night passes (alone, or
        // with the others in bed) and the game wakes him - else up again after a while.
        if (body.isSleeping()) {
            bot.navigator().stop();
            if (++lay > 300) {
                body.stopSleepInBed(true, true);
                return Result.DONE;
            }
            return Result.RUNNING;
        }
        if (lay > 0) return Result.DONE; // (woken by the morning)
        if (sub != null) {
            Result r = sub.tick(bot);
            if (r == Result.RUNNING) return r;
            sub = null;
        }
        if (bed == null || !level.getBlockState(bed).is(BlockTags.BEDS)) {
            bed = nearestVillageBed(bot, skip);
            walking = false;
            if (bed == null) {
                bot.say("no bed known in a village");
                return Result.FAILED;
            }
        }
        if (!bot.actions().inReach(bed) || body.position().distanceTo(Vec3.atCenterOf(bed)) > 2.8) {
            if (!walking) {
                bot.navigator().goNear(bed, 1.8);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(bed)) {
                skip.add(bed);
                bed = null;
            }
            return Result.RUNNING;
        }
        walking = false;
        bot.navigator().stop();
        body.stopInputs();
        // The monsters about the bed: dealt with first (the game will not let him lie down).
        var awake = keepingAwake(level, body, bed);
        if (!awake.isEmpty()) {
            Monster m = awake.stream().min(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(body))).get();
            if (net.kasax.challengecraft.bot.Guardian.armed(body) && body.getHealth() >= 10 && !(m instanceof net.minecraft.world.entity.monster.Creeper)) {
                if (ticks % 100 == 0) bot.say("a " + m.getType().toShortString() + " by the bed: first that");
                sub = new KillTask(m).nearby(10);
                return Result.RUNNING;
            }
            // (Nothing to fight with, or a creeper: it waits indoors for it to go.)
            if (++waited > MAX_WAIT) return Result.FAILED;
            return Result.RUNNING;
        }
        // Not dark yet: by the bed till the game lets him lie down (only so long).
        if (!level.isDarkOutside() && !level.isThundering()) {
            if (++waited > MAX_WAIT) {
                bot.say("by the village bed, but the night does not come: on with something else");
                return Result.FAILED;
            }
            return Result.RUNNING;
        }
        if (ticks - clickedAt < 15) return Result.RUNNING;
        if (++clicks > 6) {
            skip.add(bed);
            bed = null;
            clicks = 0;
            if (skip.size() > 4) return Result.FAILED;
            return Result.RUNNING;
        }
        clickedAt = ticks;
        bot.tools().selectEmptyHandPublic();
        Vec3 to = body.getEyePosition().subtract(Vec3.atCenterOf(bed));
        Direction face = Direction.getApproximateNearest(to.x, 0, to.z);
        Vec3 hit = Vec3.atCenterOf(bed).add(face.getStepX() * 0.3, 0.05, face.getStepZ() * 0.3);
        body.lookAt(hit);
        body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, bed, false));
        body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "sleep in a village bed";
    }
}
