package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * What a player takes from a village besides its chests: what the board wants that stands about
 * (the bell, a hay bale), a bed to carry (the night skipped, the spawn set), ripe fields for
 * bread. Each only if one is in sight close by, each with a time limit.
 */
public final class VillageChoresTask implements BotTask {
    private final Set<net.minecraft.world.level.block.Block> wanted;
    private java.util.ArrayDeque<java.util.function.Supplier<BotTask>> chores;
    private BotTask chore;
    private int choreTicks;

    public VillageChoresTask(Set<net.minecraft.world.level.block.Block> wanted) {
        this.wanted = Set.copyOf(wanted);
    }

    @Override
    public Result tick(Bot bot) {
        if (chores == null) chores = villageChores(bot);
        if (chore != null) {
            Result r = ++choreTicks > 900 ? Result.FAILED : chore.tick(bot);
            if (r == Result.RUNNING) return r;
            chore = null;
            bot.navigator().stop();
        }
        while (!chores.isEmpty()) {
            chore = chores.poll().get();
            if (chore != null) {
                choreTicks = 0;
                bot.say("in the village: " + chore.describe());
                return Result.RUNNING;
            }
        }
        return Result.DONE;
    }

    private static final Set<net.minecraft.world.item.Item> BEDS = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .filter(i -> i.getDefaultInstance().is(net.minecraft.tags.ItemTags.BEDS)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<net.minecraft.world.item.Item> FOOD = Set.of(net.minecraft.world.item.Items.BREAD, net.minecraft.world.item.Items.CARROT,
            net.minecraft.world.item.Items.BAKED_POTATO, net.minecraft.world.item.Items.COOKED_BEEF, net.minecraft.world.item.Items.COOKED_PORKCHOP,
            net.minecraft.world.item.Items.COOKED_MUTTON, net.minecraft.world.item.Items.COOKED_CHICKEN, net.minecraft.world.item.Items.APPLE);

    private static boolean ripe(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.block.Block crop) {
        return s.is(crop) && s.getBlock() instanceof net.minecraft.world.level.block.CropBlock c && c.isMaxAge(s);
    }

    /** The village's things worth taking, each only if there is one in sight close by. */
    private java.util.ArrayDeque<java.util.function.Supplier<BotTask>> villageChores(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        var out = new java.util.ArrayDeque<java.util.function.Supplier<BotTask>>();
        java.util.function.Predicate<java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState>> near =
                p -> net.kasax.challengecraft.bot.BotWorld.nearest(level, bot.body().blockPosition(), 40, 12, p, true, bot.unreachable()) != null;
        for (net.minecraft.world.level.block.Block b : wanted) {
            net.minecraft.world.item.Item item = b.asItem();
            out.add(() -> ObtainPlanner.countAny(bot.body(), Set.of(item)) > 0 || !near.test(s -> s.is(b)) ? null
                    : new MineTask(ObtainPlanner.name(item), s -> s.is(b), Set.of(item), 1).knownOnly());
        }
        out.add(() -> ObtainPlanner.countAny(bot.body(), BEDS) > 0 || !near.test(s -> s.is(net.minecraft.tags.BlockTags.BEDS)) ? null
                : new MineTask("a bed", s -> s.is(net.minecraft.tags.BlockTags.BEDS), BEDS, 1).knownOnly());
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> wheat = s -> ripe(s, net.minecraft.world.level.block.Blocks.WHEAT);
        out.add(() -> ObtainPlanner.countAny(bot.body(), FOOD) >= 12 || !near.test(wheat) ? null
                : new MineTask("ripe wheat", wheat, Set.of(net.minecraft.world.item.Items.WHEAT),
                        ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.WHEAT)) + 15).knownOnly());
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> carrots = s -> ripe(s, net.minecraft.world.level.block.Blocks.CARROTS);
        out.add(() -> ObtainPlanner.countAny(bot.body(), FOOD) >= 12 || !near.test(carrots) ? null
                : new MineTask("ripe carrots", carrots, Set.of(net.minecraft.world.item.Items.CARROT),
                        ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.CARROT)) + 8).knownOnly());
        out.add(() -> {
            int wheatHeld = ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.WHEAT));
            return wheatHeld < 3 ? null : new ObtainTask(Set.of(net.minecraft.world.item.Items.BREAD),
                    ObtainPlanner.countAny(bot.body(), Set.of(net.minecraft.world.item.Items.BREAD)) + wheatHeld / 3);
        });
        return out;
    }

    @Override
    public String describe() {
        return "village chores";
    }
}
