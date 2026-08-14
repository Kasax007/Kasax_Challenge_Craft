package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ProgressiveBlocksSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Progressive Block Drops: <b>every</b> block drops nothing until it has been "unlocked" by mining
 * the current target block once. Shared world progression (any player's mine advances the single
 * target), mirroring the All Items pattern.
 *
 * The target ORDER is drawn from every survival-obtainable block (the same vetted pool as All
 * Items, filtered to block items) so it covers essentially every block that generates naturally or
 * in structures — no hardcoded list to maintain. Suppression, however, is NOT limited to that
 * order: until a block is unlocked (or is the current target), it drops nothing, so placed/crafted
 * blocks are gated too. The op {@code challengecraft_skip_block} command is the escape hatch for
 * any rare target that can't be found.
 */
public class Chal_44_ProgressiveBlockDrops {
    private static boolean active = false;

    /** Server-side caches rebuilt from saved data; read by ProgressiveBlockDropsMixin. */
    private static final Set<Block> UNLOCKED = new HashSet<>();
    private static Block currentTarget = null;
    /**
     * Index the caches above were built from, or -1 when they are stale.
     *
     * <p>This exists because the caches are NOT safe from the client. In singleplayer both logical
     * sides share these statics, and every {@code ChallengeSyncPacket} — one is sent on each player
     * join — makes the client handler call {@code ChallengeManager.setAllActive(false)} followed by
     * {@code applyActiveFlag(id, null, null)}. The re-enable passes a null world, so it cannot
     * rebuild anything. The result was that the first block break after joining was ignored even
     * when it was the right one, and nothing dropped at all, until a skip happened to rebuild the
     * cache as a side effect. Deriving from saved data on demand removes that whole failure mode.
     */
    private static int cachedIndex = -1;

    /** Called from the drop mixin — must stay cheap (one identity check + one set lookup). */
    public static boolean isDropSuppressed(Block block, ServerLevel world) {
        if (!active) return false;
        if (cachedIndex < 0 && world != null) {
            // Only after a sync/deactivate, so this costs one saved-data lookup per sync, not per drop.
            ensureCache(ChallengeSavedData.get(world.getServer().overworld()));
        }
        if (block == currentTarget) return false; // the unlocking mine itself always drops
        return !UNLOCKED.contains(block);          // everything else stays blocked until unlocked
    }

    /**
     * The target block ids for a fresh run. Prefers the surveyed dataset (blocks actually seen
     * generating in this world via {@link net.kasax.challengecraft.data.BlockSurvey} — guaranteed
     * findable), filtered to blocks with a real item so they're mineable/displayable. Falls back
     * to every survival-obtainable block when no survey has been gathered yet.
     */
    private static List<String> buildBlockOrder(MinecraftServer server) {
        List<String> surveyed = net.kasax.challengecraft.data.BlockSurvey.getCollectedIds();
        if (!surveyed.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (String id : surveyed) {
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
                if (!(block.asItem() instanceof BlockItem)) continue;
                // Unbreakable blocks (hardness < 0: bedrock, end portal frames...) survey fine
                // but can never be mined — they'd hard-stall the run until a skip command.
                // (Vanilla's getHardness ignores the world/pos parameters; it returns the cached field.)
                if (block.defaultBlockState().getDestroySpeed(null, null) < 0) continue;
                ids.add(id);
            }
            if (!ids.isEmpty()) {
                ChallengeCraft.LOGGER.info("[Chal44] using surveyed dataset: {} of {} surveyed blocks are valid targets",
                        ids.size(), surveyed.size());
                return ids;
            }
        }

        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : Chal_22_AllItems.getSurvivalItems(server.registryAccess())) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                ids.add(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
            }
        }
        return new ArrayList<>(ids);
    }

    public static void onActivated(ServerLevel world) {
        MinecraftServer server = world.getServer();
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        if (data.getProgressiveBlocksOrder().isEmpty()) {
            List<String> order = buildBlockOrder(server);
            Collections.shuffle(order, new Random(server.overworld().getSeed()));
            data.setProgressiveBlocksOrder(order);
            data.setProgressiveBlocksIndex(0);
            ChallengeCraft.LOGGER.info("[Chal44] generated block order ({} blocks)", order.size());
        }
        refreshCache(data);
    }

    /** Rebuilds the caches only when they no longer match what is persisted. */
    private static void ensureCache(ChallengeSavedData data) {
        if (cachedIndex != data.getProgressiveBlocksIndex()) refreshCache(data);
    }

    private static void refreshCache(ChallengeSavedData data) {
        UNLOCKED.clear();
        List<String> order = data.getProgressiveBlocksOrder();
        int index = data.getProgressiveBlocksIndex();
        for (int i = 0; i < Math.min(index, order.size()); i++) {
            UNLOCKED.add(BuiltInRegistries.BLOCK.getValue(Identifier.parse(order.get(i))));
        }
        currentTarget = index < order.size() ? BuiltInRegistries.BLOCK.getValue(Identifier.parse(order.get(index))) : null;
        cachedIndex = index;
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!active || world.isClientSide()) return;

            MinecraftServer server = ((ServerLevel) world).getServer();
            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            ensureCache(data);   // a join sync may have invalidated it since the last break
            if (currentTarget == null || state.getBlock() != currentTarget) return;

            int newIndex = data.getProgressiveBlocksIndex() + 1;
            data.setProgressiveBlocksIndex(newIndex);
            Block unlocked = currentTarget;
            refreshCache(data);

            Component message;
            if (currentTarget != null) {
                message = Component.translatable("challengecraft.progressive_blocks.unlocked",
                        player.getName(), unlocked.getName(), currentTarget.getName()).withStyle(ChatFormatting.GOLD);
            } else {
                message = Component.translatable("challengecraft.progressive_blocks.all_unlocked",
                        player.getName(), unlocked.getName()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            }
            server.getPlayerList().broadcastSystemMessage(message, false);
            syncProgressToAll(server, data);
        });
    }

    public static void skipBlock(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
        List<String> order = data.getProgressiveBlocksOrder();
        int index = data.getProgressiveBlocksIndex();
        int newIndex = Math.min(index + amount, order.size());
        if (newIndex > index) {
            data.setProgressiveBlocksIndex(newIndex);
            refreshCache(data);
            syncProgressToAll(server, data);
        }
    }

    public static void syncProgressToAll(MinecraftServer server, ChallengeSavedData data) {
        List<String> order = data.getProgressiveBlocksOrder();
        int index = data.getProgressiveBlocksIndex();
        String targetId = index < order.size() ? order.get(index) : "";
        ProgressiveBlocksSyncPacket packet = new ProgressiveBlocksSyncPacket(targetId, index, order.size());
        server.getPlayerList().getPlayers().forEach(player -> ServerPlayNetworking.send(player, packet));
    }

    public static void setActive(boolean v) {
        active = v;
        // Mark the caches stale rather than emptying them. Emptying is what broke the first break
        // after a join: an empty UNLOCKED set with a null target suppresses every drop in the world,
        // and the client-side re-enable path has no world to rebuild from. Everything that reads
        // them re-derives from saved data when this marker says they are out of date.
        if (!v) cachedIndex = -1;
    }

    public static boolean isActive() {
        return active;
    }
}
