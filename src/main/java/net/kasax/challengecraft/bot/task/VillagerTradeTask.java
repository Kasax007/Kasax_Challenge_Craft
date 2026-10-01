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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
    private int ticks, waitTicks, offerWait, steps, nightTicks;
    private boolean triedVillage, placedSite;
    private static final int MAX_REROLLS = 8;
    private int rerolls;

    public VillagerTradeTask(TradeKnowledge.Trade trade, int want) {
        this.trade = trade;
        this.want = want;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (ObtainPlanner.countAny(body, Set.of(trade.gives())) >= want) return Result.DONE;
        if (++ticks - nightTicks > MAX_TICKS) return fail(bot, "it all took too long");
        if (steps > 120) return fail(bot, "too many trades and errands");
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(body.blockPosition()).inflate(64), v -> v.isAlive() && !v.isBaby());
        // To the village first.
        if (villagers.isEmpty()) {
            if (triedVillage) return Result.FAILED;
            triedVillage = true;
            interject(bot, new VisitStructureTask(level, "village"));
            return Result.RUNNING;
        }
        if (merchant == null || !merchant.isAlive() || !profession(merchant).equals(trade.profession())) {
            // (Not one whose job site is gone, about to lose the job.)
            merchant = villagers.stream().filter(v -> profession(v).equals(trade.profession()) && !losingJob(v))
                    .max(Comparator.comparingInt(v -> v.getVillagerData().level())).orElse(null);
        }
        // Nobody does that job: a jobless one gets the job site block put down beside it.
        if (merchant == null) {
            if (waitTicks > 0) {
                // (At night they rest and look for no work: the wait starts with the morning.)
                if (!level.isDarkOutside()) waitTicks--;
                else if (++nightTicks > 13000) return Result.FAILED;
                return Result.RUNNING;
            }
            if (placedSite) return fail(bot, "no villager took the " + trade.profession() + "'s job");
            Villager jobless = villagers.stream().filter(v -> profession(v).equals("none"))
                    .min(Comparator.comparingDouble(v -> v.distanceToSqr(body))).orElse(null);
            if (jobless == null && villagers.stream().anyMatch(VillagerTradeTask::losingJob) && ++offerWait < 600) return Result.RUNNING;
            if (jobless == null) return fail(bot, "no " + trade.profession() + " and no jobless villager here");
            Item site = TradeKnowledge.JOB_SITES.get(trade.profession()).asItem();
            if (ObtainPlanner.countAny(body, Set.of(site)) == 0) {
                interject(bot, new ObtainTask(Set.of(site), 1, planner));
                return Result.RUNNING;
            }
            if (body.distanceTo(jobless) > 3) return walkTo(bot, jobless.blockPosition());
            BlockPos at = bot.actions().placeNearby(site);
            if (at == null) return fail(bot, "no room for the job site");
            bot.say("a " + trade.profession() + " is needed: job site put down at " + at.toShortString());
            placedSite = true;
            waitTicks = 2400; // (it takes the job when it next looks for one: up to a couple of minutes)
            return Result.RUNNING;
        }
        // Its level: the goods are sold from this level on.
        MerchantOffer offer = offer(merchant, trade.gives());
        if (offer == null && merchant.getVillagerData().level() >= trade.level()) {
            // Levelled, but the new trades are not out yet (or not this one: two of a level's are drawn).
            if (++offerWait > 200) return fail(bot, "the " + trade.profession() + " (level " + merchant.getVillagerData().level() + ") does not sell " + ObtainPlanner.name(trade.gives()));
            return Result.RUNNING;
        }
        if (offer == null) return earn(bot, body, true);
        // Buying: the price first.
        int payA = offer.getCostA().getCount(), payB = offer.getCostB().isEmpty() ? 0 : offer.getCostB().getCount();
        if (ObtainPlanner.countAny(body, Set.of(offer.getCostA().getItem())) < payA) {
            if (offer.getCostA().is(Items.EMERALD)) return earn(bot, body, false);
            interject(bot, new ObtainTask(Set.of(offer.getCostA().getItem()), payA, planner));
            return Result.RUNNING;
        }
        if (payB > 0 && ObtainPlanner.countAny(body, Set.of(offer.getCostB().getItem())) < payB) {
            interject(bot, new ObtainTask(Set.of(offer.getCostB().getItem()), payB, planner));
            return Result.RUNNING;
        }
        Item goods = trade.gives();
        interject(bot, new TradeTask("buy " + ObtainPlanner.name(goods), (v, o) -> v == merchant && o.getResult().is(goods)));
        return Result.RUNNING;
    }

    /**
     * Trading with it to level it up (any trade it offers counts: each gives it experience; the
     * one cheapest per point of experience), or selling for emeralds (to it, or to any villager
     * about who buys something cheap). Neither possible with a fresh one, never traded with: its
     * job site broken and put down again, so it draws new trades (the librarian trick).
     */
    private Result earn(Bot bot, BotPlayer body, boolean levelUp) {
        Villager buyer = null;
        MerchantOffer best = null;
        double bestCost = Double.MAX_VALUE;
        boolean emeralds = false;
        List<Villager> about = ((ServerLevel) body.level()).getEntitiesOfClass(Villager.class, new AABB(body.blockPosition()).inflate(48), v -> v.isAlive() && !v.isBaby());
        for (Villager v : about) {
            for (MerchantOffer o : v.getOffers()) {
                if (o.isOutOfStock()) continue;
                boolean pays = o.getResult().is(Items.EMERALD);
                emeralds |= pays;
                // Levelling: the merchant's own trades; emeralds: anyone's.
                if (levelUp ? v != merchant : !pays) continue;
                double c = planner.estimate(bot, Set.of(o.getCostA().getItem()), o.getCostA().getCount());
                if (!o.getCostB().isEmpty()) c += planner.estimate(bot, Set.of(o.getCostB().getItem()), o.getCostB().getCount());
                if (levelUp) c /= Math.max(1, o.getXp());
                if (c < bestCost) {
                    bestCost = c;
                    best = o;
                    buyer = v;
                }
            }
        }
        // Without anything that pays emeralds, levelling up leads nowhere: new trades, then.
        if (best == null || bestCost >= 1e8 || !emeralds) {
            if (++offerWait <= 200) return Result.RUNNING; // (just given its job: the trades come a moment later)
            if (canReroll()) return reroll(bot, body);
            StringBuilder offers = new StringBuilder();
            for (MerchantOffer o : merchant.getOffers()) offers.append(' ').append(o.getCostA().getCount()).append(' ').append(ObtainPlanner.name(o.getCostA().getItem()))
                    .append("->").append(ObtainPlanner.name(o.getResult().getItem())).append(o.isOutOfStock() ? "(out)" : "");
            return fail(bot, "nobody here buys anything for emeralds (the " + trade.profession() + " offers:" + offers + ")");
        }
        for (ItemStack cost : List.of(best.getCostA(), best.getCostB())) {
            if (cost.isEmpty() || ObtainPlanner.countAny(body, Set.of(cost.getItem())) >= cost.getCount()) continue;
            interject(bot, new ObtainTask(Set.of(cost.getItem()), cost.getCount(), planner).keeping(Set.of(Items.EMERALD)));
            return Result.RUNNING;
        }
        MerchantOffer deal = best;
        Villager with = buyer;
        interject(bot, new TradeTask((levelUp ? "level up the " + trade.profession() + ": " : "emeralds: ") + "give " + ObtainPlanner.name(deal.getCostA().getItem())
                + " for " + ObtainPlanner.name(deal.getResult().getItem()), (v, o) -> v == with && o == deal));
        offerWait = 0;
        return Result.RUNNING;
    }

    /** A fresh one (first level, never traded with) whose job site is known draws new trades when it is put down again. */
    private boolean canReroll() {
        return rerolls < MAX_REROLLS && merchant.getVillagerData().level() <= 1 && merchant.getVillagerXp() == 0
                && merchant.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent();
    }

    private Result reroll(Bot bot, BotPlayer body) {
        BlockPos site = merchant.getBrain().getMemory(MemoryModuleType.JOB_SITE).get().pos();
        ServerLevel level = (ServerLevel) body.level();
        if (level.getBlockState(site).isAir()) return Result.RUNNING;
        if (!bot.actions().inReach(site)) return walkTo(bot, site);
        bot.tools().equipFor(level.getBlockState(site));
        if (bot.actions().breakTick(site)) {
            rerolls++;
            bot.say("the " + trade.profession() + " sells nothing worth it: job site taken up for new trades (" + rerolls + ")");
            // Its block back, then down again by the (now jobless) villager.
            interject(bot, new CollectDropsTask(site, 8, 200));
            merchant = null;
            placedSite = false;
            waitTicks = 0;
            offerWait = 0;
        }
        return Result.RUNNING;
    }

    private void interject(Bot bot, BotTask task) {
        steps++;
        bot.interject(task);
    }

    private static Result fail(Bot bot, String why) {
        bot.say("trading failed: " + why);
        return Result.FAILED;
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

    private static boolean losingJob(Villager v) {
        return !profession(v).equals("none") && v.getVillagerData().level() <= 1 && v.getVillagerXp() == 0
                && v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty();
    }

    private static String profession(Villager v) {
        return v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
    }

    @Override
    public String describe() {
        return "buy " + ObtainPlanner.name(trade.gives()) + " from a " + trade.profession() + " (level " + trade.level() + ")";
    }
}
