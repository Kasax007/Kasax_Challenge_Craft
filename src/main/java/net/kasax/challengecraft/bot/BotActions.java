package net.kasax.challengecraft.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The hands of a bot: breaking a block the way a player does (best tool in hand, looking at it,
 * swinging, the crack animation for everyone around, as long as the block takes) and putting
 * a block down against a neighbour.
 */
public final class BotActions {
    /** Blocks the bot happily spends on pillars and bridges. */
    public static final Set<net.minecraft.world.item.Item> THROWAWAY = Set.of(Items.DIRT, Items.COBBLESTONE,
            Items.COBBLED_DEEPSLATE, Items.NETHERRACK, Items.STONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
            Items.TUFF, Items.COARSE_DIRT, Items.BLACKSTONE, Items.END_STONE);

    /** Reach within which the bot works on a block (a player has 4.5). */
    public static final double REACH = 4.3;

    private final BotPlayer bot;
    private final BotTools tools;
    private BlockPos breaking;
    private float progress;
    private int lastStage = -1, ticks;

    public BotActions(BotPlayer bot, BotTools tools) {
        this.bot = bot;
        this.tools = tools;
    }

    public boolean inReach(BlockPos pos) {
        return bot.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= REACH;
    }

    /**
     * One tick of breaking {@code pos}. Returns true once the block is gone (also if it already was).
     */
    public boolean breakTick(BlockPos pos) {
        ServerLevel level = (ServerLevel) bot.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
            reset();
            return true;
        }
        if (!pos.equals(breaking)) {
            reset();
            breaking = pos.immutable();
            tools.equipFor(state);
        }
        bot.lookAt(pos);
        if (ticks++ % 5 == 0) bot.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        progress += state.getDestroyProgress(bot, level, pos);
        int stage = (int) (progress * 10f);
        if (stage != lastStage) {
            level.destroyBlockProgress(bot.getId(), pos, stage);
            lastStage = stage;
        }
        if (progress >= 1f) {
            level.destroyBlockProgress(bot.getId(), pos, -1);
            boolean done = bot.gameMode.destroyBlock(pos);
            reset();
            // Protected blocks (the casino booth, spawn protection) refuse: report them as done only
            // if they are really gone, so the caller can give up on this spot.
            return done || level.getBlockState(pos).isAir();
        }
        return false;
    }

    /** Whether a block is being broken right now (progress made this or the last tick). */
    public boolean isBreaking() {
        return breaking != null && progress > 0;
    }

    public void reset() {
        if (breaking != null) bot.level().destroyBlockProgress(bot.getId(), breaking, -1);
        breaking = null;
        progress = 0;
        lastStage = -1;
        ticks = 0;
    }

    /** Whether the bot carries a block it may spend on building. */
    public boolean hasThrowaway() {
        return throwawaySlot() >= 0;
    }

    /**
     * The block to spend: the least useful first (dirt, netherrack, the stone kinds) and the ones
     * tools and furnaces are made of (cobblestone, blackstone, cobbled deepslate) last, never the
     * last few of those.
     */
    private int throwawaySlot() {
        var items = bot.getInventory().getNonEquipmentItems();
        int best = -1, bestRank = Integer.MAX_VALUE;
        int crafting = 0;
        for (var s : items) if (CRAFTING_STONE.contains(s.getItem())) crafting += s.getCount();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i).getItem();
            if (!THROWAWAY.contains(item)) continue;
            boolean craftStone = CRAFTING_STONE.contains(item);
            if (craftStone && crafting <= KEEP_CRAFTING_STONE) continue;
            int rank = craftStone ? 2 : item == Items.DIRT || item == Items.NETHERRACK || item == Items.COARSE_DIRT ? 0 : 1;
            if (rank < bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }

    private static final Set<net.minecraft.world.item.Item> CRAFTING_STONE = Set.of(Items.COBBLESTONE, Items.BLACKSTONE, Items.COBBLED_DEEPSLATE);
    /** Enough for a furnace: a player keeps that much stone rather than build with it. */
    private static final int KEEP_CRAFTING_STONE = 8;

    /** Puts a throwaway block at {@code target}, against the block below it. */
    public boolean placeThrowaway(BlockPos target) {
        int slot = throwawaySlot();
        if (slot < 0) return false;
        tools.select(slot);
        return placeHeld(target);
    }

    /**
     * Puts down a block item (a crafting table, a furnace) next to the bot, on the ground and out of
     * its way. Returns where it went, or null when there is no spot within reach.
     */
    public BlockPos placeNearby(net.minecraft.world.item.Item item) {
        int slot = BotInventory.slotOf(bot, item);
        if (slot < 0) return null;
        ServerLevel level = (ServerLevel) bot.level();
        BlockPos feet = bot.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
            if (p.getX() == feet.getX() && p.getZ() == feet.getZ()) continue;
            if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()) continue;
            BlockPos below = p.below();
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) continue;
            if (!inReach(p)) continue;
            // Not where the bot is standing or about to stand.
            if (bot.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) continue;
            double d = p.distSqr(feet) + Math.abs(p.getY() - feet.getY()) * 2;
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        if (best == null) return null;
        tools.select(slot);
        return placeHeld(best) ? best : null;
    }

    /** Places the block item in hand at {@code target}, against any solid neighbour. */
    public boolean placeHeld(BlockPos target) {
        ItemStack held = bot.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem)) return false;
        ServerLevel level = (ServerLevel) bot.level();
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos against = target.relative(d);
            if (level.getBlockState(against).getCollisionShape(level, against).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3 hitAt = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            bot.lookAt(hitAt);
            InteractionResult r = bot.gameMode.useItemOn(bot, level, held, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hitAt, face, against, false));
            if (r.consumesAction()) {
                bot.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                return true;
            }
            return false;
        }
        return false;
    }
}
