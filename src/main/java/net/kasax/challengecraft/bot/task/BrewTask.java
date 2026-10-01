package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;

/**
 * Brews at a brewing stand (one close by, or the one it carries put down): blaze powder for fuel,
 * water bottles in, then the ingredients one after the other (nether wart for an awkward potion,
 * then sugar, blaze powder, ...; gunpowder makes it a splash potion), each brewed through. The
 * potions are taken out through the stand's screen, as a player clicks them out.
 */
public final class BrewTask extends StationTask {
    private static final int INGREDIENT = 3, FUEL = 4;
    private final List<Item> ingredients;
    private final int bottles;
    private int next, waited;
    private boolean fuelled, loaded;

    public BrewTask(List<Item> ingredients, int bottles) {
        super(Blocks.BREWING_STAND, Items.BREWING_STAND);
        this.ingredients = List.copyOf(ingredients);
        this.bottles = Math.max(1, Math.min(3, bottles));
    }

    public static boolean waterBottle(ItemStack s) {
        if (!s.is(Items.POTION)) return false;
        PotionContents c = s.get(DataComponents.POTION_CONTENTS);
        return c != null && c.is(Potions.WATER);
    }

    @Override
    protected Result work(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (!(level.getBlockEntity(station) instanceof BrewingStandBlockEntity stand)) return Result.FAILED;
        if (++waited > 600 * (ingredients.size() + 1)) {
            bot.say("the brewing takes too long");
            return Result.FAILED;
        }
        // Fuel: a blaze powder (good for twenty brews).
        if (!fuelled) {
            if (stand.getItem(FUEL).isEmpty()) {
                if (!move(body, stand, FUEL, s -> s.is(Items.BLAZE_POWDER), 1)) {
                    bot.say("no blaze powder to fuel the brewing stand");
                    return Result.FAILED;
                }
            }
            fuelled = true;
        }
        // The bottles.
        if (!loaded) {
            int in = 0;
            for (int i = 0; i < 3 && in < bottles; i++) {
                if (!stand.getItem(i).isEmpty()) {
                    in++;
                    continue;
                }
                if (move(body, stand, i, BrewTask::waterBottle, 1)) in++;
            }
            if (in == 0) {
                bot.say("no water bottle to brew with");
                return Result.FAILED;
            }
            loaded = true;
            return Result.RUNNING;
        }
        // An ingredient still in the stand: brewing (it is used up when the brew is done).
        if (!stand.getItem(INGREDIENT).isEmpty()) return Result.RUNNING;
        if (next < ingredients.size()) {
            Item ingredient = ingredients.get(next);
            ItemStack sample = new ItemStack(ingredient);
            boolean mixes = false;
            for (int i = 0; i < 3; i++) if (!stand.getItem(i).isEmpty() && mixes(level, stand.getItem(i), sample)) mixes = true;
            if (!mixes) {
                bot.say(ObtainPlanner.name(ingredient) + " does not brew into these potions");
                return Result.FAILED;
            }
            if (!move(body, stand, INGREDIENT, s -> s.is(ingredient), 1)) {
                bot.say("no " + ObtainPlanner.name(ingredient) + " to brew with");
                return Result.FAILED;
            }
            next++;
            return Result.RUNNING;
        }
        // All brewed: out through the screen (that is what counts as having brewed it).
        body.openMenu(stand);
        var menu = body.containerMenu;
        for (int i = 0; i < 3; i++) {
            if (menu.getSlot(i).hasItem()) menu.clicked(i, 0, ContainerInput.QUICK_MOVE, body);
        }
        body.closeContainer();
        bot.say("brewed: " + body.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(Items.POTION) || s.is(Items.SPLASH_POTION))
                .map(s -> s.getHoverName().getString()).distinct().toList());
        return Result.DONE;
    }

    /** Whether the reagent brews this potion into something (the game's brewing recipes). */
    public static boolean mixes(ServerLevel level, ItemStack potion, ItemStack reagent) {
        return level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BREWING,
                new net.minecraft.world.item.crafting.BrewingInput(potion, reagent), level).isPresent();
    }

    /** Moves {@code n} of the first matching inventory stack into slot {@code slot} of the stand. */
    private static boolean move(BotPlayer body, BrewingStandBlockEntity stand, int slot, java.util.function.Predicate<ItemStack> match, int n) {
        var inv = body.getInventory().getNonEquipmentItems();
        for (ItemStack s : inv) {
            if (s.isEmpty() || !match.test(s)) continue;
            stand.setItem(slot, s.split(n));
            stand.setChanged();
            body.getInventory().setChanged();
            return true;
        }
        return false;
    }

    @Override
    public String describe() {
        return "brew with " + ingredients.stream().map(ObtainPlanner::name).toList();
    }

    /** Held now: water bottles (to take along into the Nether, where there is no water). */
    public static int waterBottles(BotPlayer body) {
        int n = 0;
        for (ItemStack s : body.getInventory().getNonEquipmentItems()) if (waterBottle(s)) n += s.getCount();
        return n;
    }

    static int count(BotPlayer body, Item item) {
        return BotInventory.count(body, item);
    }
}
