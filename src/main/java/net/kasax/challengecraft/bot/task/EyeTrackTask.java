package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * To a stronghold the way experienced players find one (with a calculator like Ninjabrain Bot):
 * an eye of ender thrown and the line it flies along measured; a few dozen blocks to the side,
 * a second eye, its line measured; where the two lines cross is the stronghold. Straight there,
 * and dug down into it. (Should the lines run too close to parallel, or point apart, the old way:
 * after the eye, throw again, until one sinks into the ground close by.)
 */
public final class EyeTrackTask implements BotTask {
    private enum Stage { THROW, WATCH, PICKUP, SIDESTEP, WALK, TO_ESTIMATE, DIG }

    /** How far to the side for the second throw (with a clean reading, a short base line does). */
    private static final double SIDESTEP = 40;

    /** One reading: where an eye set out from, and which way it flew (flat, unit length). */
    public record Reading(Vec3 from, Vec3 dir) {}

    private final FarWalk far = new FarWalk();
    private final java.util.List<Reading> readings = new java.util.ArrayList<>();
    private Stage stage = Stage.THROW, next;
    private int pickupTicks;
    private EyeOfEnder eye;
    private Vec3 eyeStart;
    private BlockPos walkTo, digAt, estimate;
    private int ticks, watch, throwsLeft = 24, sidesteps;
    private BotTask dig;

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 20 * 60 * 25) return Result.FAILED;
        if (inStronghold(level, body.blockPosition())) {
            bot.navigator().stop();
            return Result.DONE;
        }
        return switch (stage) {
            case THROW -> {
                // Worked out before (two eyes earlier, then off to something else): straight there.
                if (estimate == null && readings.isEmpty() && bot.knownStronghold != null && sidesteps < 2) {
                    estimate = bot.knownStronghold;
                    bot.say("the stronghold is at " + estimate.toShortString() + " (from the eyes before)");
                    stage = Stage.TO_ESTIMATE;
                    yield Result.RUNNING;
                }
                int slot = BotInventory.slotOf(body, Items.ENDER_EYE);
                if (slot < 0 || throwsLeft-- <= 0) yield Result.FAILED;
                if (SurfaceTask.underground(body)) {
                    bot.interject(new SurfaceTask());
                    yield Result.RUNNING;
                }
                bot.navigator().stop();
                body.stopInputs();
                bot.tools().select(slot);
                body.setXRot(-20f);
                body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
                eye = null;
                watch = 0;
                stage = Stage.WATCH;
                yield Result.RUNNING;
            }
            case WATCH -> {
                if (eye == null) {
                    eye = level.getEntitiesOfClass(EyeOfEnder.class, new AABB(body.blockPosition()).inflate(4), e -> e.isAlive() && e.tickCount < 10)
                            .stream().findFirst().orElse(null);
                    if (eye != null) eyeStart = eye.position();
                    else if (++watch > 20) stage = Stage.THROW; // (none came out: again)
                    yield Result.RUNNING;
                }
                watch++;
                // Some way along: which way it goes, and whether it is still going on.
                if (watch < 30 && eye.isAlive()) yield Result.RUNNING;
                Vec3 now = eye.isAlive() ? eye.position() : eyeStart;
                double dx = now.x - eyeStart.x, dz = now.z - eyeStart.z, moved = Math.sqrt(dx * dx + dz * dz);
                if (moved < 3 || eye.isAlive() && eye.getDeltaMovement().y < -0.05 && moved < 9) {
                    // It sank right here: the stronghold is below.
                    digAt = BlockPos.containing(now.x, body.getY(), now.z);
                    bot.say("the eye sinks at " + digAt.toShortString() + ": the stronghold is down there");
                    stage = Stage.DIG;
                    yield Result.RUNNING;
                }
                Reading r = new Reading(eyeStart, new Vec3(dx / moved, 0, dz / moved));
                readings.add(r);
                bot.say(String.format("eye %d flies towards %.2f°", readings.size(), Math.toDegrees(Math.atan2(-r.dir().x, r.dir().z))));
                if (readings.size() >= 2) {
                    Vec3 cross = intersect(readings.get(readings.size() - 2), r);
                    if (cross != null) {
                        estimate = BlockPos.containing(cross.x, body.getY(), cross.z);
                        bot.knownStronghold = estimate;
                        bot.say(String.format("two eyes: the stronghold is at about %d, %d (%.0f blocks off)",
                                estimate.getX(), estimate.getZ(), Math.sqrt(estimate.distSqr(body.blockPosition()))));
                        afterPickup(Stage.TO_ESTIMATE);
                        yield Result.RUNNING;
                    }
                }
                // The first reading (or two lines that would not cross well): a step to the side,
                // a longer one the second time; after that, simply after the eye.
                if (sidesteps < 2) {
                    double side = SIDESTEP * (sidesteps == 0 ? 1 : 4);
                    sidesteps++;
                    walkTo = BlockPos.containing(body.getX() - r.dir().z * side, body.getY(), body.getZ() + r.dir().x * side);
                    bot.say("a step to the side for a second eye: " + Math.round(side) + " blocks");
                    afterPickup(Stage.SIDESTEP);
                    yield Result.RUNNING;
                }
                double leg = moved > 9 ? 280 : 60;
                walkTo = BlockPos.containing(body.getX() + r.dir().x * leg, body.getY(), body.getZ() + r.dir().z * leg);
                bot.say("after the eye: " + Math.round(leg) + " blocks on");
                afterPickup(Stage.WALK);
                yield Result.RUNNING;
            }
            case PICKUP -> {
                // The eye, if it did not break, drops where it ends up (a dozen blocks off): picked up.
                if (++pickupTicks > 260) {
                    stage = next;
                    yield Result.RUNNING;
                }
                if (eye != null && eye.isAlive()) yield Result.RUNNING;
                var drop = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(body.blockPosition()).inflate(20),
                        e -> e.isAlive() && e.getItem().is(Items.ENDER_EYE)).stream().findFirst().orElse(null);
                if (drop == null) {
                    if (pickupTicks > 100) stage = next;
                    yield Result.RUNNING;
                }
                if (bot.navigator().status() != net.kasax.challengecraft.bot.BotNavigator.Status.MOVING) bot.navigator().goPickUp(drop);
                if (bot.navigator().tick() == net.kasax.challengecraft.bot.BotNavigator.Status.FAILED) stage = next;
                yield Result.RUNNING;
            }
            case SIDESTEP, WALK -> {
                FarWalk.Status s = far.tick(bot, walkTo, 4);
                if (s == FarWalk.Status.MOVING) yield Result.RUNNING;
                stage = Stage.THROW;
                yield Result.RUNNING;
            }
            case TO_ESTIMATE -> {
                FarWalk.Status s = far.tick(bot, estimate, 6);
                if (s == FarWalk.Status.MOVING) yield Result.RUNNING;
                if (s == FarWalk.Status.FAILED) {
                    // (No way over there from here: one more eye on the way, the old way.)
                    readings.clear();
                    sidesteps = 2;
                    stage = Stage.THROW;
                    yield Result.RUNNING;
                }
                digAt = estimate;
                bot.say("at the crossing of the two lines: digging down to the stronghold");
                stage = Stage.DIG;
                yield Result.RUNNING;
            }
            case DIG -> {
                if (dig == null) {
                    BlockPos spot = digAt;
                    dig = new NavGoalTask("dig down into the stronghold", (lv, p) -> inStronghold(lv, p),
                            b -> new BlockPos(spot.getX(), Math.max(b.body().level().getMinY() + 8, b.body().getBlockY() - 24), spot.getZ()), 12000);
                }
                Result r = dig.tick(bot);
                if (r == Result.RUNNING) yield r;
                dig = null;
                yield inStronghold(level, body.blockPosition()) ? Result.DONE : Result.FAILED;
            }
        };
    }

    private void afterPickup(Stage then) {
        next = then;
        pickupTicks = 0;
        stage = Stage.PICKUP;
    }

    /**
     * Where the lines of two readings cross (flat), or null if they run too close to parallel for
     * a sure answer (under half a degree apart), cross behind either thrower, or absurdly far off.
     */
    public static Vec3 intersect(Reading a, Reading b) {
        // a.from + t a.dir = b.from + u b.dir, in x and z.
        double cross = a.dir().x * b.dir().z - a.dir().z * b.dir().x;
        if (Math.abs(cross) < Math.sin(Math.toRadians(0.5))) return null;
        double ex = b.from().x - a.from().x, ez = b.from().z - a.from().z;
        double t = (ex * b.dir().z - ez * b.dir().x) / cross;
        double u = (ex * a.dir().z - ez * a.dir().x) / cross;
        if (t <= 0 || u <= 0 || t > 8000) return null;
        return new Vec3(a.from().x + t * a.dir().x, 0, a.from().z + t * a.dir().z);
    }

    static boolean inStronghold(ServerLevel level, BlockPos p) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Structure s = registry.getValue(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("stronghold")));
        if (s == null) return false;
        var start = level.structureManager().getStructureWithPieceAt(p.getX(), p.getY(), p.getZ(), s);
        return start != null && start.isValid();
    }

    @Override
    public String describe() {
        return "find the stronghold with eyes of ender (" + stage.name().toLowerCase() + ")";
    }

    @Override
    public String status() {
        return describe() + (estimate == null ? "" : " to " + estimate.toShortString()) + ", " + readings.size() + " readings";
    }
}
