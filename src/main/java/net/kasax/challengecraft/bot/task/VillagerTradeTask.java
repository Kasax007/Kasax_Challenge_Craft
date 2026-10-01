package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.kasax.challengecraft.bot.plan.TradeKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Buying something from a villager the way a player sets it up: to the village; a villager of the
 * right profession (a jobless one given the job site block if there is none); levelled up to the
 * level that sells it by selling it cheap things (32 sticks to a fletcher for an emerald, say);
 * emeralds earned the same way; then bought.
 */
public final class VillagerTradeTask implements BotTask {
    private static final int MAX_TICKS = 24000;
    private final TradeKnowledge.Trade trade;
    private final int want;
    private final ObtainPlanner planner = new ObtainPlanner();
    private Villager merchant;
    private int ticks, waitTicks, steps;
    private boolean triedVillage, placedSite;

    public VillagerTradeTask(TradeKnowledge.Trade trade, int want) {
        this.trade = trade;
        this.want = want;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (ObtainPlanner.countAny(body, Set.of(trade.gives())) >= want) return Result.DONE;
        if (++ticks > MAX_TICKS || ++steps > 400) return Result.FAILED;
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(body.blockPosition()).inflate(64), v -> v.isAlive() && !v.isBaby());
        // To the village first.
        if (villagers.isEmpty()) {
            if (triedVillage) return Result.FAILED;
            triedVillage = true;
            bot.interject(new VisitStructureTask(level, "village"));
            return Result.RUNNING;
        }
        if (merchant == null || !merchant.isAlive() || !profession(merchant).equals(trade.profession())) {
            merchant = villagers.stream().filter(v -> profession(v).equals(trade.profession()))
                    .max(Comparator.comparingInt(v -> v.getVillagerData().level())).orElse(null);
        }
        // Nobody does that job: a jobless one gets the job site block put down beside it.
        if (merchant == null) {
            if (waitTicks > 0) {
                waitTicks--;
                return Result.RUNNING;
            }
            if (placedSite) return Result.FAILED;
            Villager jobless = villagers.stream().filter(v -> profession(v).equals("none"))
                    .min(Comparator.comparingDouble(v -> v.distanceToSqr(body))).orElse(null);
            if (jobless == null) return Result.FAILED;
            Item site = TradeKnowledge.JOB_SITES.get(trade.profession()).asItem();
            if (ObtainPlanner.countAny(body, Set.of(site)) == 0) {
                bot.interject(new ObtainTask(Set.of(site), 1, planner));
                return Result.RUNNING;
            }
            if (body.distanceTo(jobless) > 3) return walkTo(bot, jobless.blockPosition());
            BlockPos at = bot.actions().placeNearby(site);
            if (at == null) return Result.FAILED;
            bot.say("a " + trade.profession() + " is needed: job site put down at " + at.toShortString());
            placedSite = true;
            waitTicks = 2400; // (it takes the job when it next looks for one: up to a couple of minutes)
            return Result.RUNNING;
        }
        // Its level: the goods are sold from this level on.
        MerchantOffer offer = offer(merchant, trade.gives());
        if (offer == null && merchant.getVillagerData().level() >= trade.level()) {
            // Levelled, but the new trades are not out yet (or not this one: two of a level's are drawn).
            if (++waitTicks > 200) return Result.FAILED;
            return Result.RUNNING;
        }
        if (offer == null) return earn(bot, body, true);
        // Buying: the price first.
        int payA = offer.getCostA().getCount(), payB = offer.getCostB().isEmpty() ? 0 : offer.getCostB().getCount();
        if (ObtainPlanner.countAny(body, Set.of(offer.getCostA().getItem())) < payA) {
            if (offer.getCostA().is(Items.EMERALD)) return earn(bot, body, false);
            bot.interject(new ObtainTask(Set.of(offer.getCostA().getItem()), payA, planner));
            return Result.RUNNING;
        }
        if (payB > 0 && ObtainPlanner.countAny(body, Set.of(offer.getCostB().getItem())) < payB) {
            bot.interject(new ObtainTask(Set.of(offer.getCostB().getItem()), payB, planner));
            return Result.RUNNING;
        }
        Item goods = trade.gives();
        bot.interject(new TradeTask("buy " + ObtainPlanner.name(goods), (v, o) -> v == merchant && o.getResult().is(goods)));
        return Result.RUNNING;
    }

    /**
     * Selling to it (or, for emeralds only, to whoever buys cheapest): what it takes is made or
     * gathered, then traded. Levels it up on the way.
     */
    private Result earn(Bot bot, BotPlayer body, boolean levelUp) {
        Villager buyer = merchant;
        MerchantOffer best = null;
        double bestCost = Double.MAX_VALUE;
        for (MerchantOffer o : buyer.getOffers()) {
            if (o.isOutOfStock() || !o.getResult().is(Items.EMERALD)) continue;
            double c = planner.estimate(bot, Set.of(o.getCostA().getItem()), o.getCostA().getCount());
            if (c < bestCost) {
                bestCost = c;
                best = o;
            }
        }
        if (best == null) return Result.FAILED;
        Item item = best.getCostA().getItem();
        int count = best.getCostA().getCount();
        if (ObtainPlanner.countAny(body, Set.of(item)) < count) {
            bot.interject(new ObtainTask(Set.of(item), count, planner).keeping(Set.of(Items.EMERALD)));
            return Result.RUNNING;
        }
        MerchantOffer sell = best;
        bot.interject(new TradeTask((levelUp ? "level up the " : "emeralds from the ") + trade.profession() + ": sell " + ObtainPlanner.name(item),
                (v, o) -> v == buyer && o == sell));
        waitTicks = 0;
        return Result.RUNNING;
    }

    private Result walkTo(Bot bot, BlockPos p) {
        if (bot.navigator().status() != BotNavigator.Status.MOVING) bot.navigator().goNear(p, 2.5);
        if (bot.navigator().tick() == BotNavigator.Status.FAILED) return Result.FAILED;
        return Result.RUNNING;
    }

    private static MerchantOffer offer(Villager v, Item goods) {
        for (MerchantOffer o : v.getOffers()) if (!o.isOutOfStock() && o.getResult().is(goods)) return o;
        return null;
    }

    private static String profession(Villager v) {
        return v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
    }

    @Override
    public String describe() {
        return "buy " + ObtainPlanner.name(trade.gives()) + " from a " + trade.profession() + " (level " + trade.level() + ")";
    }
}
