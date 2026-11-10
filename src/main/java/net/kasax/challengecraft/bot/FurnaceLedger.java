package net.kasax.challengecraft.bot;

import net.kasax.challengecraft.bot.plan.BotKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The furnaces a bot loaded and walked off from while they cook ({@code smeltgo}): a player puts
 * the ore in and goes on working, and comes back for the ingots when he wants them - he does not
 * stand at the furnace for the twelve ingots the board will want some time, when the shield wants
 * one now. The planner counts what is in them (see {@code ObtainPlanner}), the brain picks it up in
 * passing, and an empty one is taken back along.
 *
 * <p>Only what the bot itself left counts: it reads the furnace's slots when asked (what is ready,
 * what still cooks), so what it holds is always as the world has it - taken by someone else, gone.
 */
public final class FurnaceLedger {
    /** Slots of a furnace (as in {@link AbstractFurnaceBlockEntity}). */
    private static final int IN = 0, FUEL = 1, OUT = 2;
    /** Ticks a furnace takes for one item. */
    public static final int COOK_TICKS = 200;
    /** Farther than this (blocks) a furnace is not worth the walk back (and its chunk may be gone). */
    public static final double FAR = 96;

    private record Left(BlockPos pos, ResourceKey<Level> dimension, long since) {
    }

    private final List<Left> left = new ArrayList<>();

    /** A furnace loaded and left cooking at {@code pos}. */
    public void add(ServerLevel level, BlockPos pos) {
        left.removeIf(l -> l.pos.equals(pos) && l.dimension == level.dimension());
        left.add(new Left(pos.immutable(), level.dimension(), level.getGameTime()));
    }

    public void remove(ServerLevel level, BlockPos pos) {
        left.removeIf(l -> l.pos.equals(pos) && l.dimension == level.dimension());
    }

    /** Whether the bot left this furnace cooking (it is not to be used for something else then). */
    public boolean isLeft(ServerLevel level, BlockPos pos) {
        for (Left l : left) if (l.pos.equals(pos) && l.dimension == level.dimension()) return true;
        return false;
    }

    /**
     * What one of its furnaces has of an item: {@code ready} in the output slot, {@code cooking}
     * the input that becomes it, {@code ticks} until the last of what is wanted is done.
     */
    public record Stock(BlockPos pos, int ready, int cooking, double distance) {
        public int all() {
            return ready + cooking;
        }

        /** Game ticks until {@code n} of it are out (the ready ones at once, each cooking one 10 s more). */
        public int ticksFor(int n) {
            return Math.max(0, Math.min(n, all()) - ready) * COOK_TICKS;
        }
    }

    /**
     * Its furnaces in this dimension within {@link #FAR} blocks with any of {@code items} in them
     * (out, or cooking into one), nearest first. Furnaces gone or emptied are forgotten on the way.
     */
    public List<Stock> stocks(ServerLevel level, BlockPos from, java.util.Collection<Item> items) {
        List<Stock> out = new ArrayList<>();
        if (left.isEmpty()) return out;
        BotKnowledge k = BotKnowledge.get(level.getServer());
        java.util.Iterator<Left> it = left.iterator();
        while (it.hasNext()) {
            Left l = it.next();
            if (l.dimension != level.dimension()) continue;
            if (!level.isLoaded(l.pos)) continue;
            if (!(level.getBlockEntity(l.pos) instanceof AbstractFurnaceBlockEntity f) || f.getItem(IN).isEmpty() && f.getItem(OUT).isEmpty()) {
                // (Broken, or emptied - by itself or anyone: nothing to come back for.)
                if (!(level.getBlockEntity(l.pos) instanceof AbstractFurnaceBlockEntity)) it.remove();
                continue;
            }
            double d = Math.sqrt(l.pos.distSqr(from));
            if (d > FAR) continue;
            ItemStack o = f.getItem(OUT), in = f.getItem(IN);
            int ready = !o.isEmpty() && items.contains(o.getItem()) ? o.getCount() : 0;
            int cooking = 0;
            if (!in.isEmpty() && (isLit(f) || !f.getItem(FUEL).isEmpty())) {
                for (Item i : items) {
                    if (makes(k, in, i) && (o.isEmpty() || o.is(i))) {
                        cooking = in.getCount();
                        break;
                    }
                }
            }
            if (ready + cooking > 0) out.add(new Stock(l.pos, ready, cooking, d));
        }
        out.sort(java.util.Comparator.comparingDouble(Stock::distance));
        return out;
    }

    /** All its furnaces with something to take out now within {@code radius} blocks (any item). */
    public List<BlockPos> readyNear(ServerLevel level, BlockPos from, double radius) {
        List<BlockPos> out = new ArrayList<>();
        for (Left l : left) {
            if (l.dimension != level.dimension() || l.pos.distSqr(from) > radius * radius || !level.isLoaded(l.pos)) continue;
            if (level.getBlockEntity(l.pos) instanceof AbstractFurnaceBlockEntity f && !f.getItem(OUT).isEmpty()) out.add(l.pos);
        }
        return out;
    }

    /** How many are on its list (in any dimension). */
    public int size() {
        return left.size();
    }

    /** Whether this input (a stack in a furnace) smelts into {@code item}. */
    public static boolean makes(BotKnowledge k, ItemStack input, Item item) {
        for (BotKnowledge.Route r : k.routesTo(item)) {
            if (r instanceof BotKnowledge.SmeltRoute s && s.recipe() instanceof net.minecraft.world.item.crafting.SmeltingRecipe && s.input().test(input)) return true;
        }
        return false;
    }

    private static boolean isLit(AbstractFurnaceBlockEntity furnace) {
        return furnace.getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
    }
}
