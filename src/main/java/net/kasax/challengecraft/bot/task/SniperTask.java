package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * "Sniper duel": a skeleton shot dead from fifty blocks and more. Keeps that far off (backing away
 * when it comes closer), aims along the arrow's real flight (drag and drop, worked out tick by
 * tick, ahead of where the skeleton walks) and lets fly at full draw until it falls.
 */
public final class SniperTask implements BotTask {
    private static final double MIN = 52, MAX = 70;
    private final Explorer explorer = new Explorer(6000);
    private Skeleton target;
    private int ticks, draw, shots;
    private boolean walking;
    private final Vec3[] track = new Vec3[6];
    private int trackAt;

    public SniperTask() {
        explorer.lookingFor(Set.of(EntityTypes.SKELETON));
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        var level = body.level();
        if (++ticks > 9600) return Result.FAILED;
        if (target != null && !target.isAlive()) return Result.DONE;
        int bow = BotInventory.slotOf(body, Items.BOW);
        if (bow < 0 || BotInventory.slotOf(body, Items.ARROW) < 0) return Result.FAILED;
        if (target == null) {
            target = level.getEntitiesOfClass(Skeleton.class, new AABB(body.blockPosition()).inflate(96),
                    e -> e.isAlive() && e.getType() == EntityTypes.SKELETON).stream()
                    .min((a, b) -> Double.compare(a.distanceToSqr(body), b.distanceToSqr(body))).orElse(null);
            if (target == null) return explorer.tick(bot);
            explorer.pause(bot);
        }
        double dx = target.getX() - body.getX(), dz = target.getZ() - body.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        boolean clear = level.clip(new ClipContext(body.getEyePosition(), target.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, body)).getType() == HitResult.Type.MISS;
        // Too close: back off along the line; too far or hidden: closer.
        if (d < MIN || d > MAX || !clear) {
            if (body.isUsingItem()) body.releaseUsingItem();
            draw = 0;
            if (!walking || ticks % 40 == 0) {
                double want = d < MIN ? MIN + 6 : MAX - 6;
                Vec3 from = target.position();
                Vec3 dir = d < 0.1 ? new Vec3(1, 0, 0) : new Vec3(-dx / d, 0, -dz / d);
                if (!clear && d >= MIN && d <= MAX) dir = new Vec3(dir.z, 0, -dir.x); // (round the obstacle)
                BlockPos spot = BlockPos.containing(from.add(dir.scale(want)));
                bot.navigator().goNear(Explorer.ground((net.minecraft.server.level.ServerLevel) level, spot, body.getBlockY()), 4);
                walking = true;
            }
            bot.navigator().tick();
            return Result.RUNNING;
        }
        if (walking) bot.navigator().stop();
        walking = false;
        body.stopInputs();
        bot.tools().select(bow);
        // Where it will be when the arrow gets there, and the pitch that puts the arrow there.
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int flight = flightTicks(body.getEyePosition(), aim);
        // (Its pace as seen over the last few ticks: what it is told to do, a knockback that
        // never came to anything, is not where it goes.)
        track[trackAt++ % track.length] = target.position();
        Vec3 pace = trackAt <= track.length ? Vec3.ZERO
                : target.position().subtract(track[trackAt % track.length]).scale(1.0 / (track.length - 1));
        Vec3 lead = aim.add(pace.multiply(flight, 0, flight));
        Vec3 eye = body.getEyePosition();
        double hx = lead.x - eye.x, hz = lead.z - eye.z;
        float yaw = (float) (Mth.atan2(hz, hx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = pitchFor(Math.sqrt(hx * hx + hz * hz), lead.y - eye.y);
        body.setYRot(yaw);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        body.setXRot(pitch);
        if (!body.isUsingItem()) {
            body.gameMode.useItem(body, (net.minecraft.server.level.ServerLevel) level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            draw = 0;
        } else if (++draw >= 22) {
            body.releaseUsingItem();
            shots++;
            if (shots <= 3) bot.say(String.format("shot %d: dist %.1f dy %.2f pitch %.2f (xRot %.2f)", shots, Math.sqrt(hx * hx + hz * hz), lead.y - eye.y, pitch, body.getXRot()));
        }
        return Result.RUNNING;
    }

    /** An arrow at full draw: 3 blocks a tick, slowed by 1% a tick, falling 0.05 a tick more each tick. */
    static float pitchFor(double dist, double dy) {
        float best = 0;
        double bestErr = Double.MAX_VALUE;
        // (The arrow leaves a little below the eyes.)
        dy += 0.1;
        for (float p = -30f; p <= 10f; p += 0.05f) { // (Minecraft pitch: negative is up)
            double rad = Math.toRadians(-p);
            double vx = 3.0 * Math.cos(rad), vy = 3.0 * Math.sin(rad), x = 0, y = 0, px = 0, py = 0;
            for (int t = 0; t < 100 && x < dist; t++) {
                px = x;
                py = y;
                x += vx;
                y += vy;
                vx *= 0.99;
                vy = vy * 0.99 - 0.05;
            }
            if (x < dist) continue;
            // (The height right at the distance, between the ticks either side of it.)
            double at = py + (y - py) * (dist - px) / Math.max(1e-6, x - px);
            double err = Math.abs(at - dy);
            if (err < bestErr) {
                bestErr = err;
                best = p;
            }
        }
        return best;
    }

    private static int flightTicks(Vec3 from, Vec3 to) {
        double dist = Math.sqrt(Math.pow(to.x - from.x, 2) + Math.pow(to.z - from.z, 2));
        double x = 0, v = 3.0;
        int t = 0;
        while (x < dist && t < 100) {
            x += v;
            v *= 0.99;
            t++;
        }
        return t;
    }

    @Override
    public String describe() {
        return "snipe a skeleton from fifty blocks (" + shots + " arrows)";
    }
}
