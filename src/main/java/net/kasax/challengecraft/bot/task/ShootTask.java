package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Shooting, the way a player does it: a bow drawn to full and let go, a crossbow loaded and fired,
 * aimed a little high for the arrow's drop, from a few blocks off and with a clear line. At a mob
 * (any that matches), or at a block (a target block). Done after the shots it was asked for.
 */
public final class ShootTask implements BotTask {
    private final Item weapon;
    private final Predicate<LivingEntity> mob;
    private BlockPos block;
    private final Predicate<net.minecraft.world.level.block.state.BlockState> blockMatch;
    private final int shots;
    private int fired, ticks, drawTicks;
    private boolean walking, lastClear;
    private double lastDist;

    @Override
    public String status() {
        return describe() + " [dist " + String.format("%.1f", lastDist) + ", clear " + lastClear + ", fired " + fired + ", using " + drawTicks + "]";
    }

    /** At a mob. */
    public ShootTask(Item weapon, Predicate<LivingEntity> mob, int shots) {
        this.weapon = weapon;
        this.mob = mob;
        this.block = null;
        this.blockMatch = null;
        this.shots = shots;
    }

    /** At a block. */
    public ShootTask(Item weapon, BlockPos block, int shots) {
        this.weapon = weapon;
        this.mob = null;
        this.block = block.immutable();
        this.blockMatch = null;
        this.shots = shots;
    }

    private ShootTask(Item weapon, Predicate<net.minecraft.world.level.block.state.BlockState> blockMatch, int shots, boolean unused) {
        this.weapon = weapon;
        this.mob = null;
        this.block = null;
        this.blockMatch = blockMatch;
        this.shots = shots;
    }

    /** At the nearest block of a kind (a target block it has just put down). */
    public static ShootTask atBlock(Item weapon, Predicate<net.minecraft.world.level.block.state.BlockState> blockMatch, int shots) {
        return new ShootTask(weapon, blockMatch, shots, true);
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (fired >= shots) {
            body.stopUsingItem();
            return Result.DONE;
        }
        if (++ticks > 2400) return Result.FAILED;
        int slot = BotInventory.slotOf(body, weapon);
        if (slot < 0 || BotInventory.slotOf(body, Items.ARROW) < 0 && !loaded(body)) return Result.FAILED;
        Vec3 aimAt;
        if (block == null && blockMatch != null) {
            block = net.kasax.challengecraft.bot.BotWorld.nearest(level, body.blockPosition(), 24, 12, blockMatch, false, java.util.Set.of());
            if (block == null) return Result.FAILED;
        }
        // At a block: the side of it that faces the bot (its middle is inside the block).
        if (block != null) {
            Vec3 c = Vec3.atCenterOf(block);
            // (A block in the ground: its top is what shows.)
            boolean topShows = level.getBlockState(block.above()).isAir() && body.getEyeY() > block.getY() + 1;
            aimAt = topShows ? c.add(0, 0.5, 0) : c.add(body.getEyePosition().subtract(c).normalize().scale(0.45));
        }
        else {
            LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(body.blockPosition()).inflate(32),
                            e -> e.isAlive() && !(e instanceof Player) && mob.test(e))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(body))).orElse(null);
            if (target == null) return ticks > 600 ? Result.FAILED : Result.RUNNING;
            aimAt = target.position().add(0, target.getBbHeight() * 0.6, 0);
        }
        double dist = body.getEyePosition().distanceTo(aimAt);
        var hit = level.clip(new ClipContext(body.getEyePosition(), aimAt, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        // (At a block, the ray ends on it or just short of it: that is a clear line too.)
        boolean clear = hit.getType() == HitResult.Type.MISS || block != null && hit.getLocation().distanceTo(aimAt) < 1.0;
        lastDist = dist;
        lastClear = clear;
        // Into range (not too close either: a mob in the face is no shot) with a clear line.
        if (dist > 16 || !clear) {
            if (!walking || ticks % 40 == 0) {
                bot.navigator().goNear(BlockPos.containing(aimAt), 10);
                walking = true;
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED) walking = false;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        walking = false;
        body.stopInputs();
        bot.tools().select(slot);
        // A little high for the drop of the arrow on its way.
        body.lookAt(aimAt.add(0, dist * dist * 0.0045, 0));
        if (weapon == Items.CROSSBOW) {
            if (loaded(body)) {
                body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
                fired++;
                drawTicks = 0;
                return Result.RUNNING;
            }
            if (!body.isUsingItem()) body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            else if (++drawTicks > CrossbowItem.getChargeDuration(body.getMainHandItem(), body) + 2) body.releaseUsingItem();
            return Result.RUNNING;
        }
        // The bow: drawn for a second (full strength), then let go.
        if (!body.isUsingItem()) {
            body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
            drawTicks = 0;
        } else if (++drawTicks >= 22) {
            body.releaseUsingItem();
            fired++;
        }
        return Result.RUNNING;
    }

    private boolean loaded(BotPlayer body) {
        int slot = BotInventory.slotOf(body, Items.CROSSBOW);
        return slot >= 0 && CrossbowItem.isCharged(body.getInventory().getItem(slot));
    }

    @Override
    public String describe() {
        return "shoot " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(weapon).getPath() + " at " + (block != null ? "a block" : "a mob");
    }
}
