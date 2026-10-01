package net.kasax.challengecraft.bot.task;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotArmor;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.kasax.challengecraft.bot.BotWorld;
import net.kasax.challengecraft.bot.plan.ObtainPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Gold for piglin loot (ender pearls, obsidian, string, fire charges, ...), the safe way: in a
 * gold piece (piglins leave a player in gold alone), a hole two deep dug right beside it, a gold
 * ingot dropped in. A piglin goes for gold it sees, drops in after it and is stuck there: it
 * cannot get at the bot, and nothing else gets at the piglin. Then ingot after ingot handed down;
 * what it gives back it throws up at the bot, landing at its feet. No room for a hole (lava about,
 * bedrock, a ledge): trading in the open, next to the piglin.
 */
public final class BarterTask implements BotTask {
    private enum Stage { FIND, DIG, LURE, TRADE, OPEN }

    private final Set<Item> wanted;
    private final int count, maxTrades;
    private final Explorer explorer = new Explorer(6000).lookingFor(Set.of(EntityTypes.PIGLIN));
    private Stage stage = Stage.FIND;
    private Piglin piglin;
    private BlockPos rim, hole;
    /** Every pit dug so far (hole -> its rim): a piglin may go for the gold in an older one. */
    private final java.util.Map<BlockPos, BlockPos> pits = new java.util.LinkedHashMap<>();
    private int ticks, stageTicks, trades, holeTries;
    private boolean walking, admiredSeen;
    private int goldAtStart = -1, baitedAt = -1000;

    /**
     * Trades until it holds {@code count} of any of {@code wanted} (none: just {@code maxTrades}
     * trades), the gold runs out, or {@code maxTrades} trades are done.
     */
    public BarterTask(Set<Item> wanted, int count, int maxTrades) {
        this.wanted = Set.copyOf(wanted);
        this.count = count;
        this.maxTrades = maxTrades;
    }

    /** One trade, whatever comes of it. */
    public static BarterTask once() {
        return new BarterTask(Set.of(), 0, 1);
    }

    private boolean satisfied(BotPlayer body) {
        if (!wanted.isEmpty() && ObtainPlanner.countAny(body, wanted) >= count) return true;
        return trades >= maxTrades;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (goldAtStart < 0) goldAtStart = gold(body);
        if (satisfied(body) && (stage != Stage.TRADE && stage != Stage.OPEN || !admiring())) return done(bot);
        if (++ticks > 9600) return Result.FAILED;
        stageTicks++;
        // Without gold on, every piglin about is an enemy: not this way.
        if (!BotArmor.wearsGold(body)) {
            BotArmor.wearBest(bot);
            if (!BotArmor.wearsGold(body)) {
                bot.say("no gold to wear: no trading with piglins");
                return Result.FAILED;
            }
        }
        if (gold(body) == 0 && !admiring()) return trades > 0 ? done(bot) : Result.FAILED;
        if (piglin != null && (!piglin.isAlive() || angry(piglin, body))) {
            if (piglin.isAlive()) bot.say("the piglin is angry: away from it");
            piglin = null;
            stage = Stage.FIND;
            if (++holeTries > 4) return Result.FAILED;
        }
        return switch (stage) {
            case FIND -> find(bot, level);
            case DIG -> dig(bot, level);
            case LURE -> lure(bot, level);
            case TRADE -> trade(bot, true);
            case OPEN -> trade(bot, false);
        };
    }

    private Result done(Bot bot) {
        bot.navigator().stop();
        bot.body().stopInputs();
        bot.say("bartered " + trades + " times (" + (goldAtStart - gold(bot.body())) + " gold)");
        return trades > 0 ? Result.DONE : Result.FAILED;
    }

    private static int gold(BotPlayer body) {
        return BotInventory.count(body, Items.GOLD_INGOT);
    }

    private static boolean angry(Piglin p, BotPlayer body) {
        return p.getTarget() == body || p.getBrain().getMemory(MemoryModuleType.ANGRY_AT).map(body.getUUID()::equals).orElse(false);
    }

    private boolean admiring() {
        return piglin != null && piglin.isAlive() && !piglin.getOffhandItem().isEmpty();
    }

    /** The nearest grown piglin that is not cross with it (babies do not trade). */
    private static Piglin nearest(ServerLevel level, BotPlayer body, double range) {
        return level.getEntitiesOfClass(Piglin.class, new AABB(body.blockPosition()).inflate(range),
                        p -> p.isAlive() && !p.isBaby() && !angry(p, body))
                .stream().min((a, b) -> Double.compare(a.distanceToSqr(body), b.distanceToSqr(body))).orElse(null);
    }

    private Result find(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        piglin = nearest(level, body, 48);
        if (piglin == null) {
            walking = false;
            return explorer.tick(bot);
        }
        explorer.pause(bot);
        // Close enough that it notices gold dropped by the bot (a piglin goes for gold within
        // about nine blocks): the hole right here.
        if (body.distanceTo(piglin) <= 6 && body.onGround()) {
            bot.navigator().stop();
            body.stopInputs();
            walking = false;
            // (No mob griefing on this server: piglins pick up nothing, lure or not.)
            boolean pickUp = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
            if (pickUp && holeTries < 3 && pickHole(bot, level)) {
                bot.say("a hole for the piglin at " + hole.toShortString());
                enter(Stage.DIG);
            } else {
                bot.say("no room for a hole: trading in the open");
                enter(Stage.OPEN);
            }
            return Result.RUNNING;
        }
        if (!walking || stageTicks % 40 == 0) {
            bot.navigator().goNear(piglin.blockPosition(), 4);
            walking = true;
        }
        if (bot.navigator().tick() == BotNavigator.Status.FAILED) {
            walking = false;
            if (body.distanceTo(piglin) <= 9) {
                enter(Stage.OPEN);
            }
        }
        return Result.RUNNING;
    }

    private void enter(Stage s) {
        stage = s;
        stageTicks = 0;
    }

    /**
     * A column beside the bot to dig two deep: rock that breaks (no bedrock, no obsidian), a floor
     * under it, nothing liquid next to it, and room above for a piglin to walk in.
     */
    private boolean pickHole(Bot bot, ServerLevel level) {
        BlockPos feet = bot.navigator().feet();
        if (!solid(level, feet.below())) return false;
        Direction toward = Direction.getApproximateNearest(piglin.getX() - bot.body().getX(), 0, piglin.getZ() - bot.body().getZ());
        Direction[] order = {toward, toward.getClockWise(), toward.getCounterClockWise(), toward.getOpposite()};
        for (Direction d : order) {
            BlockPos top = feet.relative(d);
            if (!passable(level, top) || !passable(level, top.above())) continue;
            BlockPos h1 = top.below(), h2 = top.below(2);
            if (!diggable(level, h1) || !diggable(level, h2) || !solid(level, h2.below())) continue;
            boolean dry = true;
            for (BlockPos c : new BlockPos[]{h1, h2}) {
                for (Direction n : Direction.values()) {
                    BlockPos q = c.relative(n);
                    if (!level.getFluidState(q).isEmpty()) dry = false;
                }
            }
            if (!dry) continue;
            rim = feet;
            hole = h2;
            pits.put(hole, rim);
            return true;
        }
        return false;
    }

    private static boolean solid(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    private static boolean passable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    private static boolean diggable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!s.getFluidState().isEmpty()) return false;
        if (s.getCollisionShape(level, p).isEmpty()) return true;
        float hardness = s.getDestroySpeed(level, p);
        return hardness >= 0 && hardness < 10;
    }

    private Result dig(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        dbg = "feet " + bot.navigator().feet().toShortString() + " rim " + rim.toShortString() + " breaking " + bot.actions().isBreaking()
                + " " + level.getBlockState(hole.above()).getBlock() + "/" + level.getBlockState(hole).getBlock();
        // (Off the rim: back onto it.)
        if (!bot.navigator().feet().equals(rim)) {
            if (stageTicks > 200) {
                enter(Stage.OPEN);
                return Result.RUNNING;
            }
            bot.navigator().goNear(rim, 0.4);
            bot.navigator().tick();
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.stopInputs();
        // One deep first: gold at the bottom of a shallow pit shows from some way off (at the
        // bottom of a deep hole a piglin sees it only from the edge). Deeper once one is in.
        for (BlockPos p : new BlockPos[]{hole.above()}) {
            if (passable(level, p)) continue;
            if (!bot.actions().breakTick(p)) {
                if (stageTicks > 600) {
                    bot.actions().reset();
                    enter(Stage.OPEN);
                }
                return Result.RUNNING;
            }
        }
        enter(Stage.LURE);
        return Result.RUNNING;
    }

    /** Down in the hole for good (two deep). */
    private boolean inHole(Piglin p) {
        return p != null && p.isAlive() && p.getY() < hole.getY() + 0.5 && p.getBoundingBox().intersects(new AABB(hole));
    }

    /** In the shallow pit (one deep): about to be trapped. */
    private boolean inPit(Piglin p) {
        return p != null && p.isAlive() && p.getBoundingBox().intersects(new AABB(hole.above()).deflate(0.05));
    }

    private Result lure(Bot bot, ServerLevel level) {
        BotPlayer body = bot.body();
        // A piglin in an older pit (gone for the gold left there): that pit is the one now.
        for (var e : pits.entrySet()) {
            if (e.getKey().equals(hole)) continue;
            BlockPos h = e.getKey();
            boolean taken = !level.getEntitiesOfClass(Piglin.class, new AABB(h.above()).deflate(0.05), p -> p.isAlive() && !p.isBaby()).isEmpty()
                    || !level.getEntitiesOfClass(Piglin.class, new AABB(h), p -> p.isAlive() && !p.isBaby() && p.getY() < h.getY() + 0.5).isEmpty();
            if (taken) {
                hole = h;
                rim = e.getValue();
                bot.say("a piglin went for the gold in the pit at " + h.above().toShortString());
                break;
            }
        }
        // (Off the rim of the pit that matters now: back onto it.)
        if (!bot.navigator().feet().equals(rim) && !bot.actions().inReach(hole)) {
            bot.navigator().goNear(rim, 0.4);
            bot.navigator().tick();
            return Result.RUNNING;
        }
        // One in the hole (the one it lured, or another that went for the gold): that one.
        for (Piglin p : level.getEntitiesOfClass(Piglin.class, new AABB(hole).inflate(2), p -> p.isAlive() && !p.isBaby())) {
            if (inHole(p)) {
                piglin = p;
                bot.actions().reset();
                bot.say("a piglin in the hole: trading");
                enter(Stage.TRADE);
                return Result.RUNNING;
            }
            // In the shallow pit (busy looking at the gold): the floor out from under it, and it
            // is two deep, stuck.
            if (inPit(p) && !passable(level, hole)) {
                bot.actions().breakTick(hole);
                return Result.RUNNING;
            }
        }
        dbg = "gold about " + level.getEntitiesOfClass(ItemEntity.class, new AABB(hole).inflate(4), e -> e.getItem().is(Items.GOLD_INGOT)).stream()
                .map(e -> String.format("%.1f/%.1f/%.1f", e.getX() - hole.getX(), e.getY() - hole.getY(), e.getZ() - hole.getZ())).toList();
        // Gold lying in the pit already (or in a held piglin's hands): waiting for one to come.
        boolean bait = !level.getEntitiesOfClass(ItemEntity.class, new AABB(hole).expandTowards(0, 1, 0),
                e -> e.getItem().is(Items.GOLD_INGOT)).isEmpty();
        // (One ingot at a time: it takes a moment to fall in.)
        if (!bait && stageTicks > 5 && ticks - baitedAt > 60) {
            baitedAt = ticks;
            int slot = BotInventory.slotOf(body, Items.GOLD_INGOT);
            if (slot < 0) return Result.FAILED;
            bot.tools().select(slot);
            body.lookAt(Vec3.atCenterOf(passable(level, hole) ? hole : hole.above()));
            var one = body.getInventory().getNonEquipmentItems().get(body.getInventory().getSelectedSlot()).split(1);
            body.getInventory().setChanged();
            body.drop(one, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        // None comes (wandered off): to the next piglin, a new hole there (the gold stays as bait).
        // (One still about the pit: a while longer.)
        boolean near = !level.getEntitiesOfClass(Piglin.class, new AABB(hole).inflate(8), p -> p.isAlive() && !p.isBaby()).isEmpty();
        if (stageTicks > (near ? 1200 : 600)) {
            holeTries++;
            enter(Stage.FIND);
        }
        return Result.RUNNING;
    }

    /** Ingot after ingot, each once the piglin has done with the last; drops picked up. */
    private Result trade(Bot bot, boolean fromRim) {
        BotPlayer body = bot.body();
        if (piglin == null || !piglin.isAlive()) {
            enter(Stage.FIND);
            return Result.RUNNING;
        }
        if (fromRim && !inHole(piglin)) {
            enter(Stage.LURE);
            return Result.RUNNING;
        }
        // Loot about (thrown up out of the hole, or next to it in the open): picked up.
        if (!fromRim || !bot.navigator().feet().equals(rim)) {
            var drops = BotWorld.drops((ServerLevel) body.level(), body.blockPosition(), 5, (Set<Item>) null).stream()
                    .filter(e -> !e.getItem().is(Items.GOLD_INGOT) || !fromRim).toList();
            if (!drops.isEmpty() && !admiring() && body.distanceTo(drops.get(0)) > 1.2) {
                if (!walking || stageTicks % 20 == 0) {
                    bot.navigator().goPickUp(drops.get(0));
                    walking = true;
                }
                bot.navigator().tick();
                return Result.RUNNING;
            }
        }
        if (fromRim && !bot.navigator().feet().equals(rim)) {
            bot.navigator().goNear(rim, 0.4);
            bot.navigator().tick();
            return Result.RUNNING;
        }
        walking = false;
        if (admiring()) {
            admiredSeen = true;
            body.lookAt(piglin.getEyePosition());
            return Result.RUNNING;
        }
        if (admiredSeen) {
            admiredSeen = false;
            trades++;
            if (satisfied(body)) return Result.RUNNING; // (done at the next tick, after the pick-up)
        }
        double reach = body.distanceTo(piglin);
        if (reach > 3.0) {
            if (fromRim) return Result.RUNNING;
            bot.navigator().goNear(piglin.blockPosition(), 2);
            bot.navigator().tick();
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.stopInputs();
        int slot = BotInventory.slotOf(body, Items.GOLD_INGOT);
        if (slot < 0) return Result.RUNNING;
        bot.tools().select(slot);
        body.lookAt(piglin.getEyePosition());
        var hit = new EntityHitResult(piglin, piglin.getEyePosition());
        var r = UseEntityCallback.EVENT.invoker().interact(body, body.level(), InteractionHand.MAIN_HAND, piglin, hit);
        if (r == net.minecraft.world.InteractionResult.PASS) r = body.interactOn(piglin, InteractionHand.MAIN_HAND, piglin.getEyePosition());
        if (r.consumesAction()) body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        // (It took none: busy, or just picked up something it likes better. A moment.)
        return Result.RUNNING;
    }

    private String dbg = "";

    @Override
    public String status() {
        return describe() + " [" + stage + " " + dbg + ", trades " + trades + ", hole " + (hole == null ? "-" : hole.toShortString())
                + ", piglin " + (piglin == null ? "-" : piglin.blockPosition().toShortString() + (admiring() ? " admiring" : "")) + "]";
    }

    @Override
    public String describe() {
        return "barter with a piglin" + (wanted.isEmpty() ? "" : " for " + ObtainPlanner.names(wanted));
    }
}
