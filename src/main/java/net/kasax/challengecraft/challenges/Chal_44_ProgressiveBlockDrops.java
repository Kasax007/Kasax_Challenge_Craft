package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ProgressiveBlocksSyncPacket;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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

    /** Called from the drop mixin — must stay cheap (one identity check + one set lookup). */
    public static boolean isDropSuppressed(Block block) {
        if (!active) return false;
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
                Block block = Registries.BLOCK.get(Identifier.of(id));
                if (!(block.asItem() instanceof BlockItem)) continue;
                // Unbreakable blocks (hardness < 0: bedrock, end portal frames...) survey fine
                // but can never be mined — they'd hard-stall the run until a skip command.
                // (Vanilla's getHardness ignores the world/pos parameters; it returns the cached field.)
                if (block.getDefaultState().getHardness(null, null) < 0) continue;
                ids.add(id);
            }
            if (!ids.isEmpty()) {
                ChallengeCraft.LOGGER.info("[Chal44] using surveyed dataset: {} of {} surveyed blocks are valid targets",
                        ids.size(), surveyed.size());
                return ids;
            }
        }

        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : Chal_22_AllItems.getSurvivalItems(server.getRegistryManager())) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                ids.add(Registries.BLOCK.getId(blockItem.getBlock()).toString());
            }
        }
        return new ArrayList<>(ids);
    }

    public static void onActivated(ServerWorld world) {
        MinecraftServer server = world.getServer();
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
        if (data.getProgressiveBlocksOrder().isEmpty()) {
            List<String> order = buildBlockOrder(server);
            Collections.shuffle(order, new Random(server.getOverworld().getSeed()));
            data.setProgressiveBlocksOrder(order);
            data.setProgressiveBlocksIndex(0);
            ChallengeCraft.LOGGER.info("[Chal44] generated block order ({} blocks)", order.size());
        }
        refreshCache(data);
    }

    private static void refreshCache(ChallengeSavedData data) {
        UNLOCKED.clear();
        List<String> order = data.getProgressiveBlocksOrder();
        int index = data.getProgressiveBlocksIndex();
        for (int i = 0; i < Math.min(index, order.size()); i++) {
            UNLOCKED.add(Registries.BLOCK.get(Identifier.of(order.get(i))));
        }
        currentTarget = index < order.size() ? Registries.BLOCK.get(Identifier.of(order.get(index))) : null;
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!active || world.isClient()) return;
            if (currentTarget == null || state.getBlock() != currentTarget) return;

            MinecraftServer server = ((ServerWorld) world).getServer();
            ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
            int newIndex = data.getProgressiveBlocksIndex() + 1;
            data.setProgressiveBlocksIndex(newIndex);
            Block unlocked = currentTarget;
            refreshCache(data);

            Text message;
            if (currentTarget != null) {
                message = Text.translatable("challengecraft.progressive_blocks.unlocked",
                        player.getName(), unlocked.getName(), currentTarget.getName()).formatted(Formatting.GOLD);
            } else {
                message = Text.translatable("challengecraft.progressive_blocks.all_unlocked",
                        player.getName(), unlocked.getName()).formatted(Formatting.GREEN, Formatting.BOLD);
            }
            server.getPlayerManager().broadcast(message, false);
            syncProgressToAll(server, data);
        });
    }

    public static void skipBlock(MinecraftServer server, int amount) {
        if (!active) return;
        ChallengeSavedData data = ChallengeSavedData.get(server.getOverworld());
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
        server.getPlayerManager().getPlayerList().forEach(player -> ServerPlayNetworking.send(player, packet));
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) {
            UNLOCKED.clear();
            currentTarget = null;
        }
    }

    public static boolean isActive() {
        return active;
    }
}
