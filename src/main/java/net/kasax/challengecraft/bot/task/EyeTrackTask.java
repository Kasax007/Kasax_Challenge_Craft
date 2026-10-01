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
 * To a stronghold the way players find one: an eye of ender thrown, the way it flies watched,
 * a long walk that way, another eye; when one sinks into the ground close by instead of flying
 * on, the stronghold is under there: dug down to it.
 */
public final class EyeTrackTask implements BotTask {
    private enum Stage { THROW, WATCH, WALK, DIG }

    private final FarWalk far = new FarWalk();
    private Stage stage = Stage.THROW;
    private EyeOfEnder eye;
    private Vec3 eyeStart;
    private BlockPos walkTo, digAt;
    private int ticks, watch, throwsLeft = 24;
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
                // On it goes: a long way after it (shorter when it went a short way: closer then).
                double leg = moved > 9 ? 280 : 60;
                walkTo = BlockPos.containing(body.getX() + dx / moved * leg, body.getY(), body.getZ() + dz / moved * leg);
                bot.say("the eye flies towards " + Math.round(Math.toDegrees(Math.atan2(-dx, dz))) + "°: " + Math.round(leg) + " blocks on");
                stage = Stage.WALK;
                yield Result.RUNNING;
            }
            case WALK -> {
                FarWalk.Status s = far.tick(bot, walkTo, 8);
                if (s == FarWalk.Status.MOVING) yield Result.RUNNING;
                stage = Stage.THROW;
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
}
