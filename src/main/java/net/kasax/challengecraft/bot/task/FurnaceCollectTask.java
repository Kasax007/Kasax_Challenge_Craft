package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.BotKnowledge;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.Set;

/**
 * Back at a furnace it left cooking ({@code smeltgo}, see {@link net.kasax.challengecraft.bot.FurnaceLedger}):
 * walks there, takes out what is done, waits for the rest it wants if that still cooks, and takes
 * the furnace back along once it is empty. Until it holds {@code target} of the items, or the
 * furnace has no more of them to give.
 */
public final class FurnaceCollectTask implements BotTask {
    private static final int IN = 0, FUEL = 1, OUT = 2;

    private final BlockPos pos;
    private final Set<Item> items;
    private final int target;
    private boolean walking, breaking;
    private int waited, walkTicks, breakTicks;

    public FurnaceCollectTask(BlockPos pos, Set<Item> items, int target) {
        this.pos = pos.immutable();
        this.items = Set.copyOf(items);
        this.target = target;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        int have = ObtainPlanner.countAny(bot.body(), items);
        if (breaking) return takeBack(bot, level);
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity f)) {
            bot.furnaces.remove(level, pos);
            return have >= target ? Result.DONE : Result.FAILED;
        }
        if (!bot.actions().inReach(pos)) {
            if (!walking) {
                bot.navigator().goNear(pos, 3.5);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s == BotNavigator.Status.IDLE) walking = false;
            if (s == BotNavigator.Status.FAILED || ++walkTicks > 2400) {
                bot.say("no way back to the furnace at " + pos.toShortString());
                return Result.FAILED;
            }
            return Result.RUNNING;
        }
        if (walking) {
            bot.navigator().stop();
            walking = false;
        }
        bot.body().lookAt(pos);
        take(bot, f);
        have = ObtainPlanner.countAny(bot.body(), items);
        boolean empty = f.getItem(IN).isEmpty() && f.getItem(OUT).isEmpty();
        if (empty) {
            bot.furnaces.remove(level, pos);
            breaking = true;
            return Result.RUNNING;
        }
        if (have >= target) return Result.DONE;
        // Still cooking what it wants: the fire kept going, and waited for (ten seconds an item).
        ItemStack in = f.getItem(IN);
        boolean wanted = false;
        BotKnowledge k = BotKnowledge.get(level.getServer());
        for (Item i : items) if (net.kasax.challengecraft.bot.FurnaceLedger.makes(k, in, i)) wanted = true;
        if (!wanted) return have > 0 ? Result.DONE : Result.FAILED;
        if (!lit(f) && f.getItem(FUEL).isEmpty() && !refuel(bot, f, in.getCount())) {
            bot.say("the furnace at " + pos.toShortString() + " went out, and nothing to burn");
            return Result.FAILED;
        }
        return ++waited > (target - have) * 220 + 200 ? Result.FAILED : Result.RUNNING;
    }

    /** What is done comes out (whatever it is: it is its own furnace). */
    private static void take(Bot bot, AbstractFurnaceBlockEntity f) {
        ItemStack out = f.getItem(OUT);
        if (out.isEmpty()) return;
        ItemStack taken = out.copy();
        f.setItem(OUT, ItemStack.EMPTY);
        taken.onCraftedBy(bot.body(), taken.getCount());
        CraftTask.give(bot.body(), taken);
        f.awardUsedRecipesAndPopExperience(bot.body());
        f.setChanged();
    }

    /** Fuel from the pack for what is left in it (the least useful first: coal, planks, logs, sticks). */
    private static boolean refuel(Bot bot, AbstractFurnaceBlockEntity f, int items) {
        ItemStack best = null;
        for (ItemStack s : bot.body().getInventory().getNonEquipmentItems()) {
            if (s.isEmpty() || BotKnowledge.fuelValue(s) <= 0) continue;
            if (best == null || rank(s) < rank(best)) best = s;
        }
        if (best == null) return false;
        int n = (int) Math.min(best.getCount(), Math.ceil(items / BotKnowledge.fuelValue(best)));
        f.setItem(FUEL, best.copyWithCount(n));
        best.shrink(n);
        f.setChanged();
        return true;
    }

    private static int rank(ItemStack s) {
        if (s.is(net.minecraft.world.item.Items.COAL) || s.is(net.minecraft.world.item.Items.CHARCOAL)) return 0;
        if (s.is(net.minecraft.tags.ItemTags.PLANKS)) return 1;
        if (s.is(net.minecraft.tags.ItemTags.LOGS)) return 2;
        if (s.is(net.minecraft.world.item.Items.STICK)) return 3;
        return 4;
    }

    private static boolean lit(AbstractFurnaceBlockEntity f) {
        return f.getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
    }

    /** The empty furnace back into the pack (a pickaxe gets it in a moment), as from every smelting. */
    private Result takeBack(Bot bot, ServerLevel level) {
        boolean pickaxe = false;
        for (ItemStack s : bot.body().getInventory().getNonEquipmentItems()) if (s.is(net.minecraft.tags.ItemTags.PICKAXES)) pickaxe = true;
        if (!pickaxe || !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FURNACE) || ++breakTicks > 200 || !bot.actions().inReach(pos)) {
            return ObtainPlanner.countAny(bot.body(), items) >= target ? Result.DONE : Result.FAILED;
        }
        if (!bot.actions().breakTick(pos)) return Result.RUNNING;
        bot.interject(new CollectDropsTask(pos, 2, 100));
        breakTicks = 201;
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "back to the furnace at " + pos.toShortString() + " for " + ObtainPlanner.names(items);
    }
}
