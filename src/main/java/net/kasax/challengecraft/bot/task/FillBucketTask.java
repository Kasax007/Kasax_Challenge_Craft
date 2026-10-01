package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Fills an empty bucket at a water or lava source it can see: walk up, look at it, use the bucket. */
public final class FillBucketTask implements BotTask {
    private final TagKey<Fluid> fluid;
    private final Item filled;
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos source;
    private boolean walking;
    private int tries;

    public FillBucketTask(TagKey<Fluid> fluid) {
        this.fluid = fluid;
        this.filled = fluid == FluidTags.LAVA ? Items.LAVA_BUCKET : Items.WATER_BUCKET;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (BotInventory.slotOf(body, filled) >= 0) return Result.DONE;
        if (BotInventory.slotOf(body, Items.BUCKET) < 0 || ++tries > 3000) return Result.FAILED;
        if (source == null || !level.getFluidState(source).isSource()) {
            // A source with open air above, so the bot can look at it from the side or above.
            source = BotWorld.nearest(level, body.blockPosition(), 32, 12,
                    s -> s.getFluidState().is(fluid) && s.getFluidState().isSource(), true, skip);
            // None in sight: the nearest it remembers (a lake passed on the way).
            if (source == null) source = bot.memory().nearest(level, body.blockPosition(), s -> s.getFluidState().is(fluid) && s.getFluidState().isSource(), skip);
            walking = false;
            if (source == null) return Result.FAILED;
        }
        if (!bot.actions().inReach(source)) {
            if (!walking) {
                bot.navigator().goNear(source, 3.2);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(source)) {
                skip.add(source);
                source = null;
            }
            return Result.RUNNING;
        }
        bot.navigator().stop();
        bot.tools().select(BotInventory.slotOf(body, Items.BUCKET));
        body.lookAt(Vec3.atCenterOf(source).add(0, 0.4, 0));
        body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
        if (BotInventory.slotOf(body, filled) >= 0) return Result.DONE;
        skip.add(source); // the look did not hit it (something in between): another one
        source = null;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "fill a bucket with " + (fluid == FluidTags.LAVA ? "lava" : "water");
    }
}
