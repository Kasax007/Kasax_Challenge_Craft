package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Obsidian from a lava pool, the way players make it: pour water on the ground at the pool's edge
 * so it runs over the lava (every lava source it touches turns to obsidian), take the water back
 * up, then mine the obsidian (a diamond pickaxe, ten seconds a block).
 */
public final class MakeObsidianTask implements BotTask {
    private final FarWalk far = new FarWalk();
    private boolean triedWater;
    private int farFails;
    private enum Phase { FIND, POUR, WAIT, TAKE_BACK, MINE }

    private final int target;
    private final Set<BlockPos> skip = new HashSet<>();
    private Phase phase = Phase.FIND;
    private BlockPos lava, bank, water;
    private MineTask mine;
    private boolean walking;
    private int wait, total;

    public MakeObsidianTask(int target) {
        this.target = target;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (BotInventory.count(body, Items.OBSIDIAN) >= target) return Result.DONE;
        if (++total > 12000) return Result.FAILED;
        switch (phase) {
            case FIND -> {
                // Obsidian already made (or lying around) comes first.
                if (BotWorld.nearest(level, body.blockPosition(), 10, 6, s -> s.is(Blocks.OBSIDIAN), true, Set.of()) != null) {
                    phase = Phase.MINE;
                    return Result.RUNNING;
                }
                // Water first (a bucket of it from the nearest lake), if there is a bucket to fill.
                if (BotInventory.slotOf(body, Items.WATER_BUCKET) < 0) {
                    if (BotInventory.slotOf(body, Items.BUCKET) < 0 || triedWater) return Result.FAILED;
                    triedWater = true;
                    bot.interject(new FillBucketTask(FluidTags.WATER));
                    return Result.RUNNING;
                }
                lava = BotWorld.nearest(level, body.blockPosition(), 32, 12,
                        s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), true, skip);
                // None in sight: to the nearest lava it remembers, however far.
                if (lava == null) {
                    BlockPos known = bot.memory().nearest(level, body.blockPosition(), s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), skip);
                    if (known == null || farFails > 2 || known.distSqr(body.blockPosition()) > 400 * 400) return Result.FAILED;
                    FarWalk.Status fs = far.tick(bot, known, 8);
                    if (fs == FarWalk.Status.FAILED) {
                        farFails++;
                        skip.add(known);
                    }
                    return Result.RUNNING;
                }
                bank = null;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = lava.relative(d);
                    if (!level.getBlockState(n).getCollisionShape(level, n).isEmpty() && level.getBlockState(n.above()).isAir()
                            && level.getBlockState(lava.above()).isAir()) {
                        bank = n;
                        break;
                    }
                }
                if (bank == null) {
                    skip.add(lava);
                    return Result.RUNNING;
                }
                phase = Phase.POUR;
                walking = false;
            }
            case POUR -> {
                if (!bot.actions().inReach(bank.above())) {
                    if (!walking) {
                        bot.navigator().goNear(bank, 3.0);
                        walking = true;
                    }
                    BotNavigator.Status s = bot.navigator().tick();
                    if (s == BotNavigator.Status.FAILED || s == BotNavigator.Status.ARRIVED && !bot.actions().inReach(bank.above())) {
                        skip.add(lava);
                        phase = Phase.FIND;
                    }
                    return Result.RUNNING;
                }
                bot.navigator().stop();
                bot.tools().select(BotInventory.slotOf(body, Items.WATER_BUCKET));
                body.lookAt(Vec3.atCenterOf(bank).add(0, 0.5, 0));
                body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
                water = bank.above();
                wait = 0;
                phase = Phase.WAIT;
            }
            case WAIT -> {
                if (++wait > 25) phase = Phase.TAKE_BACK;
            }
            case TAKE_BACK -> {
                int slot = BotInventory.slotOf(body, Items.BUCKET);
                if (slot >= 0 && level.getFluidState(water).isSource()) {
                    bot.tools().select(slot);
                    body.lookAt(Vec3.atCenterOf(water));
                    body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
                }
                skip.add(lava);
                phase = Phase.MINE;
            }
            case MINE -> {
                if (mine == null && BotWorld.nearest(level, body.blockPosition(), 10, 6, s -> s.is(Blocks.OBSIDIAN), true, Set.of()) == null) {
                    phase = Phase.FIND; // nothing formed (the water ran elsewhere): the next pool
                    return Result.RUNNING;
                }
                if (mine == null) mine = new MineTask("obsidian", s -> s.is(Blocks.OBSIDIAN), Set.of(Items.OBSIDIAN),
                        BotInventory.count(body, Items.OBSIDIAN) + 1);
                Result r = mine.tick(bot);
                if (r != Result.RUNNING) {
                    mine = null;
                    phase = Phase.FIND;
                }
            }
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "make obsidian (" + target + ")";
    }
}
