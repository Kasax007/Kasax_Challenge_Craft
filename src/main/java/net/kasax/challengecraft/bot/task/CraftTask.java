package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.plan.BotKnowledge;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Crafts a recipe {@code times} times from what is in the inventory: at a crafting table for 3×3
 * recipes (using one nearby or putting one down), in the inventory grid for 2×2 ones. The grid is
 * filled with real items and checked against the recipe, so the game decides what comes out
 * (and awards the crafting statistic, which is what "craft X" goals count).
 */
public final class CraftTask extends StationTask {
    private static final int TICKS_PER_CRAFT = 6;

    private final BotKnowledge.CraftRoute route;
    private final int times;
    private int done, wait;
    private boolean missing;

    /** Whether it stopped for want of ingredients (the recipe is fine: plan the ingredients again). */
    public boolean missingIngredients() {
        return missing;
    }

    public CraftTask(BotKnowledge.CraftRoute route, int times) {
        super(Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE);
        this.route = route;
        this.times = times;
    }

    @Override
    protected boolean needsStation() {
        return route.table();
    }

    @Override
    protected Result work(Bot bot) {
        if (done >= times) return Result.DONE;
        if (wait-- > 0) return Result.RUNNING;
        wait = TICKS_PER_CRAFT;
        if (!craftOnce(bot.body())) {
            bot.say("missing ingredients for " + ObtainPlanner.name(route.result()));
            missing = true;
            return done > 0 ? Result.DONE : Result.FAILED;
        }
        done++;
        return done >= times ? Result.DONE : Result.RUNNING;
    }

    private boolean craftOnce(BotPlayer body) {
        NonNullList<ItemStack> inv = body.getInventory().getNonEquipmentItems();
        int[] taken = new int[inv.size()];
        List<ItemStack> grid = new ArrayList<>();
        List<Integer> from = new ArrayList<>();
        for (Optional<Ingredient> slot : route.grid()) {
            if (slot.isEmpty()) {
                grid.add(ItemStack.EMPTY);
                from.add(-1);
                continue;
            }
            int found = -1;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.get(i);
                if (s.getCount() - taken[i] > 0 && slot.get().test(s)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) return false;
            taken[found]++;
            grid.add(inv.get(found).copyWithCount(1));
            from.add(found);
        }
        CraftingInput input = CraftingInput.of(route.width(), route.height(), grid);
        if (!route.recipe().matches(input, body.level())) return false;
        ItemStack result = route.recipe().assemble(input);
        if (result.isEmpty()) return false;
        NonNullList<ItemStack> rest = route.recipe().getRemainingItems(input);
        for (int i = 0; i < inv.size(); i++) if (taken[i] > 0) inv.get(i).shrink(taken[i]);
        result.onCraftedBy(body, result.getCount());
        give(body, result);
        for (ItemStack r : rest) if (!r.isEmpty()) give(body, r);
        body.getInventory().setChanged();
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        return true;
    }

    static void give(BotPlayer body, ItemStack stack) {
        if (!body.getInventory().add(stack) && !stack.isEmpty()) body.spawnAtLocation((net.minecraft.server.level.ServerLevel) body.level(), stack);
    }

    @Override
    public String describe() {
        return "craft " + ObtainPlanner.name(route.result()) + " x" + times * route.yield();
    }
}
