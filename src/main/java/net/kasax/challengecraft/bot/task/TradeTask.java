package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

import java.util.function.BiPredicate;

/**
 * One trade: to a villager whose offer it wants and can pay for, the payment out of the pack, the
 * goods in, and the trade told to the villager (which is what the game counts: stock, experience,
 * the trade advancement).
 */
public final class TradeTask implements BotTask {
    private final String what;
    /** Which villager and offer will do. */
    private final BiPredicate<AbstractVillager, MerchantOffer> wanted;
    private AbstractVillager villager;
    private boolean walking;
    private int ticks;

    public TradeTask(String what, BiPredicate<AbstractVillager, MerchantOffer> wanted) {
        this.what = what;
        this.wanted = wanted;
    }

    /** The first offer of this villager the bot wants and can pay for right now, or null. */
    public static MerchantOffer affordable(AbstractVillager v, net.kasax.challengecraft.bot.BotPlayer body, BiPredicate<AbstractVillager, MerchantOffer> wanted) {
        for (MerchantOffer o : v.getOffers()) {
            if (o.isOutOfStock() || !wanted.test(v, o)) continue;
            if (count(body, o.getCostA()) >= o.getCostA().getCount() && (o.getCostB().isEmpty() || count(body, o.getCostB()) >= o.getCostB().getCount())) return o;
        }
        return null;
    }

    private static int count(net.kasax.challengecraft.bot.BotPlayer body, ItemStack want) {
        int n = 0;
        for (ItemStack s : body.getInventory().getNonEquipmentItems()) if (ItemStack.isSameItem(s, want)) n += s.getCount();
        return n;
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        if (++ticks > 3600) return Result.FAILED;
        if (villager == null || !villager.isAlive()) {
            villager = body.level().getEntitiesOfClass(AbstractVillager.class, new AABB(body.blockPosition()).inflate(64),
                            v -> v.isAlive() && !v.isBaby() && affordable(v, body, wanted) != null)
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(body), b.distanceToSqr(body))).orElse(null);
            if (villager == null) return Result.FAILED;
            walking = false;
        }
        if (body.distanceTo(villager) > 3) {
            if (!walking || bot.navigator().status() != BotNavigator.Status.MOVING) {
                bot.navigator().goNear(villager.blockPosition(), 2.5);
                walking = true;
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED) walking = false;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.lookAt(villager.getEyePosition());
        MerchantOffer offer = affordable(villager, body, wanted);
        if (offer == null) return Result.FAILED;
        // Pay (cost A, then cost B), take the goods, tell the villager: what the trading screen does.
        pay(body, offer.getCostA());
        if (!offer.getCostB().isEmpty()) pay(body, offer.getCostB());
        ItemStack goods = offer.getResult().copy();
        villager.setTradingPlayer(body);
        villager.notifyTrade(offer);
        villager.setTradingPlayer(null);
        if (villager instanceof Villager v) v.setUnhappyCounter(0);
        CraftTask.give(body, goods);
        body.getInventory().setChanged();
        return Result.DONE;
    }

    private static void pay(net.kasax.challengecraft.bot.BotPlayer body, ItemStack cost) {
        int left = cost.getCount();
        for (ItemStack s : body.getInventory().getNonEquipmentItems()) {
            if (left <= 0) break;
            if (!ItemStack.isSameItem(s, cost)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
    }

    @Override
    public String describe() {
        return what;
    }
}
