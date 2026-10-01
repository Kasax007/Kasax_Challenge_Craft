package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** To a block it knows of (a village bell, a lectern, a bed) and a click on it with an empty hand. */
public final class ClickBlockTask implements BotTask {
    private final String what;
    private final Predicate<BlockState> match;
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos target;
    private boolean walking;
    private int ticks, clicks;

    private net.minecraft.world.item.Item tool;
    private Set<net.minecraft.world.item.Item> collect;
    private int collectTicks = -1;

    public ClickBlockTask(String what, Predicate<BlockState> match) {
        this.what = what;
        this.match = match;
    }

    /** Clicked with this item in hand (shears on a bee nest), and what drops then picked up. */
    public ClickBlockTask with(net.minecraft.world.item.Item tool, Set<net.minecraft.world.item.Item> collect) {
        this.tool = tool;
        this.collect = collect;
        return this;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (++ticks > 4800) return Result.FAILED;
        if (collect != null && net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(bot.body(), collect) > 0) return Result.DONE;
        // What fell: picked up.
        if (collectTicks >= 0) {
            if (++collectTicks > 200) return Result.FAILED;
            var drops = BotWorld.drops(level, bot.body().blockPosition(), 8, collect);
            if (drops.isEmpty()) return collectTicks > 40 ? Result.FAILED : Result.RUNNING;
            if (bot.navigator().status() != BotNavigator.Status.MOVING) bot.navigator().goPickUp(drops.get(0));
            bot.navigator().tick();
            return Result.RUNNING;
        }
        if (target == null || !match.test(level.getBlockState(target))) {
            target = BotWorld.nearest(level, bot.body().blockPosition(), 24, 12, match, false, skip);
            if (target == null) target = bot.memory().nearest(level, bot.body().blockPosition(), match, skip);
            if (target == null) return Result.FAILED;
            walking = false;
        }
        if (!bot.actions().inReach(target)) {
            if (!walking) {
                bot.navigator().goNear(target, 3.5);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(target)) {
                skip.add(target);
                target = null;
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        if (tool != null) {
            int slot = net.kasax.challengecraft.bot.BotInventory.slotOf(bot.body(), tool);
            if (slot < 0) return Result.FAILED;
            bot.tools().select(slot);
        } else bot.tools().selectEmptyHandPublic();
        // The face turned towards it (a bell only rings when struck on the side).
        Vec3 to = bot.body().getEyePosition().subtract(Vec3.atCenterOf(target));
        Direction face = Direction.getApproximateNearest(to.x, 0, to.z);
        // (A bell hangs one way: if that side does not ring it, the next click tries the others.)
        for (int i = 0; i < clicks; i++) face = face.getClockWise();
        Vec3 hit = Vec3.atCenterOf(target).add(face.getStepX() * 0.3, 0.1, face.getStepZ() * 0.3);
        bot.body().lookAt(hit);
        bot.body().gameMode.useItemOn(bot.body(), level, bot.body().getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, target, false));
        bot.body().swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        if (collect != null) {
            collectTicks = 0;
            return Result.RUNNING;
        }
        return ++clicks >= 4 ? Result.DONE : Result.RUNNING;
    }

    @Override
    public String describe() {
        return what;
    }
}
