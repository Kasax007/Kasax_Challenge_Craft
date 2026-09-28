package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Keeps the croupier's booth exactly as the House built it. The booth's blocks are indestructible
 * by themselves ({@link CasinoBoothBlock}, the cashier counter); this closes the remaining gaps:
 * nobody — not even in creative mode — breaks anything inside the booth, and nobody places a block,
 * a bucket of water or lava, fire or anything else into it.
 *
 * <p>The booth volume is its 5×5 platform from the floor up to four blocks above the carpet. The
 * position comes from {@link CasinoSavedData}, so these checks only bite on the server; the client
 * is corrected by the server's answer.
 */
public final class BoothProtection {
    private BoothProtection() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !inBooth(level, pos));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!CasinoGames.active() || level.isClientSide()) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            if (!(held.getItem() instanceof BucketItem)) return InteractionResult.PASS;
            HitResult hit = player.pick(player.blockInteractionRange(), 1f, true);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                    && (inBooth(level, bhr.getBlockPos()) || inBooth(level, bhr.getBlockPos().relative(bhr.getDirection())))) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean inBooth(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || server != server.getServer().overworld()) return false;
        if (!CasinoGames.active()) return false;
        CasinoSavedData data = CasinoSavedData.get(server.getServer());
        if (!data.isBoothBuilt()) return false;
        BlockPos a = BlockPos.of(data.getBoothPos());
        return Math.abs(pos.getX() - a.getX()) <= 2 && Math.abs(pos.getZ() - a.getZ()) <= 2
                && pos.getY() >= a.getY() - 1 && pos.getY() <= a.getY() + 4;
    }

    /** Using an item on or next to the booth that would put something into it. */
    public static boolean denyUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || CasinoEconomy.isWallet(held.getItem())) return false;
        // The cashier counter itself is used, not built on.
        if (level.getBlockState(hit.getBlockPos()).getBlock() instanceof CasinoDeviceBlock) return false;
        return inBooth(level, hit.getBlockPos()) || inBooth(level, hit.getBlockPos().relative(hit.getDirection()));
    }

    public static boolean denyBreak(Player player, Level level, BlockPos pos) {
        return inBooth(level, pos);
    }

}
