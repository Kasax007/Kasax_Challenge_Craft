package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.BotKnowledge;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.List;

/**
 * Smelts {@code count} items in a furnace (one nearby, or its own put down): puts the input and
 * enough fuel in, waits beside it, takes out what is done — like a player standing at the
 * furnace, including the experience.
 */
public final class SmeltTask extends StationTask {
    private static final int IN = 0, FUEL = 1, OUT = 2;

    private final BotKnowledge.SmeltRoute route;
    private final int count;
    private int target = -1, waited, breakTicks;
    private boolean loaded, takingBack, tookBack;

    public SmeltTask(BotKnowledge.SmeltRoute route, int count) {
        super(Blocks.FURNACE, Items.FURNACE);
        this.route = route;
        this.count = count;
    }

    @Override
    protected boolean needsStation() {
        return !takingBack;
    }

    @Override
    protected Result work(Bot bot) {
        BotPlayer body = bot.body();
        bot0 = body;
        ServerLevel level = (ServerLevel) body.level();
        if (takingBack) return takeBack(bot, level);
        if (!(level.getBlockEntity(station) instanceof AbstractFurnaceBlockEntity furnace)) return Result.FAILED;
        if (target < 0) target = ObtainPlanner.countAny(body, java.util.Set.of(route.result())) + count;

        takeOutput(body, furnace);
        if (ObtainPlanner.countAny(body, java.util.Set.of(route.result())) >= target) return finish(bot, furnace);

        if (!loaded) {
            loaded = true;
            int in = load(body, furnace);
            if (in == 0 && furnace.getItem(IN).isEmpty()) {
                bot.say("nothing to smelt");
                return Result.FAILED;
            }
            loadFuel(body, furnace, furnace.getItem(IN).getCount());
        }
        // A batch: more furnaces beside it, the input shared out - three furnaces do eighteen
        // ores in a minute, one in three (eight cobblestone each, there are always some about).
        Result side = spread(bot, level, furnace);
        if (side != null) return side;
        List<AbstractFurnaceBlockEntity> all = new java.util.ArrayList<>();
        all.add(furnace);
        for (var e : extras) if (level.getBlockEntity(e) instanceof AbstractFurnaceBlockEntity f) all.add(f);
        for (var f : all) if (f != furnace) takeOutput(body, f);
        // Now and then: a furnace gone cold with ore still in it gets fuel - from the pack, from
        // another one's spare - or, with none anywhere, its ore goes into one still burning.
        if (all.size() > 1 && ++extraFuelCheck % 40 == 0) {
            for (var f : all) {
                if (f.getItem(IN).isEmpty() || !f.getItem(FUEL).isEmpty() || isLit(f)) continue;
                if (loadFuel(body, f, f.getItem(IN).getCount())) continue;
                for (var g : all) if (g != f && f.getItem(FUEL).isEmpty()) shareFuel(g, f, f.getItem(IN).getCount());
                if (!f.getItem(FUEL).isEmpty()) continue;
                for (var g : all) {
                    if (g == f || !isLit(g) && g.getItem(FUEL).isEmpty()) continue;
                    ItemStack in = f.getItem(IN), into = g.getItem(IN);
                    if (!into.isEmpty() && !ItemStack.isSameItemSameComponents(into, in)) continue;
                    int n = Math.min(in.getCount(), in.getMaxStackSize() - into.getCount());
                    if (n <= 0) continue;
                    if (into.isEmpty()) g.setItem(IN, in.copyWithCount(n));
                    else into.grow(n);
                    in.shrink(n);
                    f.setChanged();
                    g.setChanged();
                    break;
                }
            }
        }
        if (ObtainPlanner.countAny(body, java.util.Set.of(route.result())) >= target) return finish(bot, furnace);
        // Not stood beside it while it cooks: the kit's stone or wood from within a few blocks
        // (a player puts the meat in and mines the wall beside it), back for what is done.
        int left = 0;
        for (var f : all) left = Math.max(left, f.getItem(IN).getCount() * COOK_TICKS - COOK_TICKS / 2);
        // And a bite while waiting, if the stomach has room for a whole one: the eating reflex
        // would stop a walk for it a little later (only with nothing after it close by).
        if (!ateWaiting && left >= 40 && bot.params.on("meanwhile") && !bot.targeted(8)) {
            int slot = EatTask.bestFood(body);
            var food = slot < 0 ? null : body.getInventory().getNonEquipmentItems().get(slot).get(net.minecraft.core.component.DataComponents.FOOD);
            // (Proper food only: not the raw meat that is about to be cooked.)
            if (food != null && food.nutrition() >= 5 && body.getFoodData().getFoodLevel() <= 20 - food.nutrition()) {
                ateWaiting = true;
                bot.interject(new EatTask());
                return Result.RUNNING;
            }
        }
        if (meanwhileTries < 2 && bot.params.on("meanwhile")) {
            if (left >= MEANWHILE_MIN) {
                meanwhileTries++;
                BotTask work = meanwhileWork(bot, level);
                if (work != null) {
                    var furnaces = new java.util.ArrayList<net.minecraft.core.BlockPos>();
                    furnaces.add(station);
                    furnaces.addAll(extras);
                    var home = station;
                    bot.say("while it cooks (~" + left / 20 + " s): " + work.describe());
                    bot.interject(new MeanwhileTask(work, left, () -> cooked(level, furnaces)
                            || bot.body().blockPosition().distSqr(home) > MEANWHILE_REACH * MEANWHILE_REACH));
                    return Result.RUNNING;
                }
            }
        }
        // Keep it burning if the fuel was short; give up if it went out with input left.
        if (++waited % 40 == 0 && !furnace.getItem(IN).isEmpty() && furnace.getItem(FUEL).isEmpty()) {
            if (!loadFuel(body, furnace, furnace.getItem(IN).getCount()) && waited > 400 && !isLit(furnace)) {
                bot.say("out of fuel");
                return Result.FAILED;
            }
        }
        if (furnace.getItem(IN).isEmpty() && furnace.getItem(OUT).isEmpty() && extrasEmpty(level)) {
            return finish(bot, furnace); // all smelted and taken, even if fewer than planned
        }
        return waited > count * 220 + 600 ? Result.FAILED : Result.RUNNING;
    }

    /**
     * Done here. The furnace it put down itself goes back into the pack (a pickaxe gets it back in
     * a moment), as a player carries it from one smelting to the next instead of making another.
     * One with input still in it stays where it is.
     */
    private Result finish(Bot bot, AbstractFurnaceBlockEntity furnace) {
        // The extra furnaces first, back into the pack (each one empty by now).
        var level = (ServerLevel) bot.body().level();
        while (!extras.isEmpty()) {
            var e = extras.get(0);
            if (!level.getBlockState(e).is(Blocks.FURNACE) || !bot.actions().inReach(e) || extraBreak > 200) {
                extras.remove(0);
                extraBreak = 0;
                continue;
            }
            if (level.getBlockEntity(e) instanceof AbstractFurnaceBlockEntity f) takeOutput(bot.body(), f);
            extraBreak++;
            if (bot.actions().breakTick(e)) {
                extras.remove(0);
                extraBreak = 0;
                bot.interject(new CollectDropsTask(e, 2, 60));
            }
            return Result.RUNNING;
        }
        if (tookBack || placedHere == null || !placedHere.equals(station) || !furnace.getItem(IN).isEmpty()) return Result.DONE;
        boolean pickaxe = false;
        for (ItemStack s : bot.body().getInventory().getNonEquipmentItems()) if (s.is(net.minecraft.tags.ItemTags.PICKAXES)) pickaxe = true;
        if (!pickaxe) return Result.DONE;
        takingBack = true;
        return Result.RUNNING;
    }

    private Result takeBack(Bot bot, ServerLevel level) {
        if (tookBack) return Result.DONE;
        if (++breakTicks > 200 || !bot.actions().inReach(station)) return Result.DONE;
        if (!bot.actions().breakTick(station)) return Result.RUNNING;
        tookBack = true;
        bot.interject(new CollectDropsTask(station, 2, 100));
        return Result.RUNNING;
    }

    /** Ticks a furnace takes for one item. */
    private static final int COOK_TICKS = 200;
    /** Work in between only for a wait at least this long (ticks), and only this far from the furnace (blocks). */
    private static final int MEANWHILE_MIN = 160, MEANWHILE_REACH = 10;
    /** Cobblestone the kit keeps (spare stone and blocks to build with): mined while the furnace cooks, up to this. */
    private static final int MEANWHILE_COBBLE = 32;
    private int meanwhileTries;
    private boolean ateWaiting;

    /** Nothing left to cook in any of these furnaces (or they are gone). */
    private static boolean cooked(ServerLevel level, java.util.List<net.minecraft.core.BlockPos> furnaces) {
        for (var p : furnaces) if (level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f && !f.getItem(IN).isEmpty()) return false;
        return true;
    }

    /**
     * Work worth doing within a few blocks while a furnace cooks, or null: rock showing at body
     * height or in the ground beside (the kit's cobblestone), else a trunk right there (the spare
     * wood) - only what is in sight, never a search or a staircase away from the furnace.
     */
    static BotTask meanwhileWork(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        var stone = java.util.Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE);
        int cobble = ObtainPlanner.countAny(body, stone);
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> rock = st -> (st.is(Blocks.STONE) || st.is(Blocks.DEEPSLATE)
                || st.is(Blocks.COBBLESTONE) || st.is(Blocks.COBBLED_DEEPSLATE)) && bot.tools().canHarvest(st);
        if (cobble < MEANWHILE_COBBLE && net.kasax.challengecraft.bot.BotWorld.nearest(level, body.blockPosition(), 6, 2, rock, true, bot.unreachable()) != null)
            return new MineTask("cobblestone", rock, stone, Math.min(MEANWHILE_COBBLE, cobble + 16)).knownOnly();
        var logs = net.minecraft.tags.BlockTags.OVERWORLD_NATURAL_LOGS;
        int planks = 0;
        for (ItemStack st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.LOGS)) planks += 4 * st.getCount();
            else if (st.is(net.minecraft.tags.ItemTags.PLANKS)) planks += st.getCount();
        }
        if (planks < 20 && net.kasax.challengecraft.bot.BotWorld.nearest(level, body.blockPosition(), 8, 4, st -> st.is(logs), true, bot.unreachable()) != null) {
            java.util.Set<net.minecraft.world.item.Item> logItems = new java.util.HashSet<>();
            for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) if (b.defaultBlockState().is(logs)) logItems.add(b.asItem());
            return new MineTask("logs", st -> st.is(logs), logItems, ObtainPlanner.countAny(body, logItems) + 4).knownOnly();
        }
        return null;
    }

    private final java.util.List<net.minecraft.core.BlockPos> extras = new java.util.ArrayList<>();
    private int extraTries, extraBreak, extraFuelCheck;
    private boolean fetchingFurnace;

    private boolean extrasEmpty(ServerLevel level) {
        for (var e : extras) {
            if (level.getBlockEntity(e) instanceof AbstractFurnaceBlockEntity f && (!f.getItem(IN).isEmpty() || !f.getItem(OUT).isEmpty())) return false;
        }
        return true;
    }

    /**
     * Shares a big batch out over up to three furnaces: an extra one made (or taken from the pack)
     * and put down beside, half the waiting input moved into it with fuel for its share. Returns
     * a result while busy with that, else null.
     */
    private Result spread(Bot bot, ServerLevel level, AbstractFurnaceBlockEntity furnace) {
        BotPlayer body = bot.body();
        int waiting = furnace.getItem(IN).getCount();
        // (Every furnace lights a piece of fuel of its own, and what a piece has left when its
        // share is done is lost: no more furnaces than pieces, the shares whole pieces' worth.)
        ItemStack mainFuel = furnace.getItem(FUEL);
        double per = mainFuel.isEmpty() ? 0 : BotKnowledge.fuelValue(mainFuel);
        double smelts = mainFuel.isEmpty() ? 0 : per * mainFuel.getCount() + (isLit(furnace) ? per : 0);
        for (ItemStack st : body.getInventory().getNonEquipmentItems()) {
            if (!st.isEmpty() && !route.input().test(st) && !st.is(route.result()) && BotKnowledge.fuelValue(st) > 0) smelts += BotKnowledge.fuelValue(st) * st.getCount();
        }
        int pieces = per <= 0 ? 0 : (int) (smelts / per);
        int want = Math.min(Math.min(2, count / 6), Math.max(0, pieces - 2)); // (one piece to spare: the shares never come out even)
        if (extras.size() >= want || waiting < 6 || extraTries > 3) return null;
        if (body.getInventory().countItem(Items.FURNACE) == 0) {
            // (Eight cobblestone and a table: a few seconds. Without the stone, one furnace it is.)
            int stone = ObtainPlanner.countAny(body, java.util.Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.BLACKSTONE));
            if (stone < 8 || fetchingFurnace) {
                extraTries = 99;
                return null;
            }
            fetchingFurnace = true;
            extraTries++;
            bot.interject(new ObtainTask(java.util.Set.of(Items.FURNACE), 1, new ObtainPlanner(), true));
            return Result.RUNNING;
        }
        fetchingFurnace = false;
        net.minecraft.core.BlockPos at = bot.actions().placeNearby(Items.FURNACE);
        if (at == null || !(level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity extra)) {
            extraTries++;
            return null;
        }
        extras.add(at);
        // Its share: of what still waits, an even part for each furnace there will be.
        int share = waiting / (want + 1 - (extras.size() - 1));
        if (per >= 2) share = Math.max((int) per, (int) (share / per) * (int) per);
        if (share >= waiting) {
            extraTries = 99; // (the furnace stands empty; it is taken back along at the end)
            return null;
        }
        ItemStack in = furnace.getItem(IN);
        extra.setItem(IN, in.copyWithCount(share));
        in.shrink(share);
        furnace.setChanged();
        // (The fuel all went into the first one: its share moved across.)
        if (!loadFuel(body, extra, share)) shareFuel(furnace, extra, share);
        extra.setChanged();
        furnace.setChanged();
        bot.say("another furnace for the batch (" + share + " of " + count + " in it)");
        return Result.RUNNING;
    }

    /** Fuel for {@code items} moved from one furnace to another (the first keeps what it needs). */
    private static void shareFuel(AbstractFurnaceBlockEntity from, AbstractFurnaceBlockEntity to, int items) {
        ItemStack fuel = from.getItem(FUEL);
        double per = fuel.isEmpty() ? 0 : BotKnowledge.fuelValue(fuel);
        if (per <= 0 || !to.getItem(FUEL).isEmpty() && !ItemStack.isSameItemSameComponents(to.getItem(FUEL), fuel)) return;
        // (What the first still has to smelt keeps its share; a lit one has its fire going already.)
        int keep = (int) Math.ceil(from.getItem(IN).getCount() / per) - (isLit(from) ? 1 : 0);
        int n = Math.min(fuel.getCount() - Math.max(0, keep), (int) Math.ceil(items / per));
        if (n <= 0) return;
        if (to.getItem(FUEL).isEmpty()) to.setItem(FUEL, fuel.copyWithCount(n));
        else to.getItem(FUEL).grow(n);
        fuel.shrink(n);
        from.setChanged();
        to.setChanged();
    }

    private static boolean isLit(AbstractFurnaceBlockEntity furnace) {
        return furnace.getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
    }

    /** Moves up to {@code count} matching input items into the furnace. */
    private int load(BotPlayer body, AbstractFurnaceBlockEntity furnace) {
        int moved = 0;
        var inv = body.getInventory().getNonEquipmentItems();
        for (ItemStack s : inv) {
            if (moved >= count) break;
            if (s.isEmpty() || !route.input().test(s)) continue;
            ItemStack slot = furnace.getItem(IN);
            if (!slot.isEmpty() && !ItemStack.isSameItemSameComponents(slot, s)) continue;
            int n = Math.min(Math.min(count - moved, s.getCount()), s.getMaxStackSize() - slot.getCount());
            if (n <= 0) continue;
            if (slot.isEmpty()) furnace.setItem(IN, s.copyWithCount(n));
            else slot.grow(n);
            s.shrink(n);
            moved += n;
        }
        furnace.setChanged();
        return moved;
    }

    /** Adds fuel for {@code items} more items; the cheapest-to-spare fuel first. */
    private boolean loadFuel(BotPlayer body, AbstractFurnaceBlockEntity furnace, int items) {
        ItemStack slot = furnace.getItem(FUEL);
        double have = slot.isEmpty() ? 0 : BotKnowledge.fuelValue(slot) * slot.getCount();
        if (have >= items) return true;
        var inv = body.getInventory().getNonEquipmentItems();
        ItemStack best = null;
        for (ItemStack s : inv) {
            // Never the input, and never what is being made (the first charcoal would feed the fire).
            if (s.isEmpty() || route.input().test(s) || s.is(route.result()) || BotKnowledge.fuelValue(s) <= 0) continue;
            if (!slot.isEmpty() && !ItemStack.isSameItemSameComponents(slot, s)) continue;
            // Coal before wood before sticks: spend what is least useful for anything else.
            if (best == null || rank(s) < rank(best)) best = s;
        }
        if (best == null) return false;
        double per = BotKnowledge.fuelValue(best);
        int n = (int) Math.min(best.getCount(), Math.ceil((items - have) / per));
        n = Math.min(n, best.getMaxStackSize() - slot.getCount());
        if (n <= 0) return false;
        if (slot.isEmpty()) furnace.setItem(FUEL, best.copyWithCount(n));
        else slot.grow(n);
        best.shrink(n);
        furnace.setChanged();
        return true;
    }

    private static int rank(ItemStack s) {
        if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) return 0;
        if (s.is(net.minecraft.tags.ItemTags.PLANKS)) return 1;
        if (s.is(net.minecraft.tags.ItemTags.LOGS)) return 2;
        if (s.is(Items.STICK)) return 3;
        return 4;
    }

    private void takeOutput(BotPlayer body, AbstractFurnaceBlockEntity furnace) {
        ItemStack out = furnace.getItem(OUT);
        if (out.isEmpty()) return;
        ItemStack taken = out.copy();
        furnace.setItem(OUT, ItemStack.EMPTY);
        taken.onCraftedBy(body, taken.getCount());
        CraftTask.give(body, taken);
        furnace.awardUsedRecipesAndPopExperience(body);
        furnace.setChanged();
    }

    @Override
    public String status() {
        StringBuilder b = new StringBuilder(describe());
        if (station != null && bot0 != null && bot0.level().getBlockEntity(station) instanceof AbstractFurnaceBlockEntity f)
            b.append(" [main ").append(f.getItem(IN).getCount()).append('/').append(f.getItem(FUEL).getCount()).append('/').append(f.getItem(OUT).getCount());
        if (bot0 != null) for (var e : extras) if (bot0.level().getBlockEntity(e) instanceof AbstractFurnaceBlockEntity f)
            b.append(", ").append(e.toShortString()).append(' ').append(f.getItem(IN).getCount()).append('/').append(f.getItem(FUEL).getCount())
                    .append('/').append(f.getItem(OUT).getCount()).append(isLit(f) ? " lit" : "");
        return b.append(']').toString();
    }

    private BotPlayer bot0;

    @Override
    public String describe() {
        return "smelt " + ObtainPlanner.name(route.result()) + " x" + count;
    }
}
