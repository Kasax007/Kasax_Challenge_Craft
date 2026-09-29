package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Selling to the House without a menu. A player lays stacks on the croupier's counter (right-click
 * it with the stack in hand), sees on the counter what each stack and the whole lot would fetch, and
 * rings the bell to take the deal: the croupier takes the goods and slides the chips over.
 *
 * <p>The goods stay the player's until the bell: a click on a stack takes it back, and whatever the
 * House does not buy (worthless items, filled shulker boxes and bundles) comes back at the deal.
 * Nothing can get lost — goods still on the counter return to the inventory (or drop at the player's
 * feet) when they leave, when the server stops and when the run ends; each player has their own pile.
 */
public final class CounterDeposit {
    private static final Map<UUID, List<ItemStack>> PENDING = new HashMap<>();

    private CounterDeposit() {
    }

    private static List<ItemStack> pile(ServerPlayer player) {
        return PENDING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
    }

    /** Lays the held stack on the counter. */
    public static void place(ServerPlayer player, ItemStack held) {
        if (held.isEmpty() || CasinoEconomy.isWallet(held.getItem())) return;
        if (!CasinoGames.atCroupier(player)) return;
        List<ItemStack> pile = pile(player);
        ItemStack moving = held.copy();
        // Top up stacks of the same item first, then open a new slot.
        for (ItemStack s : pile) {
            if (moving.isEmpty()) break;
            if (!ItemStack.isSameItemSameComponents(s, moving)) continue;
            int room = s.getMaxStackSize() - s.getCount();
            int n = Math.min(room, moving.getCount());
            s.grow(n);
            moving.shrink(n);
        }
        if (!moving.isEmpty()) {
            if (pile.size() >= DeviceLayouts.DEPOSIT_SLOTS) {
                player.sendOverlayMessage(Component.translatable("challengecraft.casino.counter.full").withStyle(ChatFormatting.RED));
                held.setCount(moving.getCount());
                sync(player);
                return;
            }
            pile.add(moving);
        }
        held.setCount(0);
        player.getInventory().setChanged();
        ItemStack shown = pile.get(pile.size() - 1);
        long value = EmcValues.stackValue(shown);
        if (value <= 0) {
            player.sendOverlayMessage(Component.translatable(EmcValues.hasContents(shown)
                    ? "challengecraft.casino.counter.has_contents" : "challengecraft.casino.counter.worthless",
                    shown.getHoverName()).withStyle(ChatFormatting.RED));
        }
        playAtCounter(player, CasinoSounds.CHIP, 0.5f, 0.8f);
        sync(player);
    }

    /** Takes one stack back from the counter. */
    public static void takeBack(ServerPlayer player, int slot) {
        List<ItemStack> pile = PENDING.get(player.getUUID());
        if (pile == null || slot < 0 || slot >= pile.size()) return;
        give(player, pile.remove(slot));
        playAtCounter(player, CasinoSounds.CHIP, 0.5f, 0.65f);
        sync(player);
    }

    /** The bell: the House buys everything of value, the rest goes back to the player. */
    public static void deal(ServerPlayer player) {
        List<ItemStack> pile = PENDING.get(player.getUUID());
        if (pile == null || pile.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.counter.empty").withStyle(ChatFormatting.GRAY));
            playAtCounter(player, CasinoSounds.TICK, 0.5f, 1.4f);
            return;
        }
        if (!CasinoGames.atCroupier(player)) return;
        long credited = 0;
        int sold = 0, refused = 0;
        for (ItemStack s : new ArrayList<>(pile)) {
            long centi = CasinoEconomy.depositStack(player, s); // empties the stack when it is bought
            if (centi > 0) {
                credited += centi;
                sold++;
            } else {
                refused++;
                give(player, s);
            }
        }
        pile.clear();
        MinecraftServer server = player.level().getServer();
        if (refused > 0) {
            player.sendSystemMessage(Component.translatable("challengecraft.casino.counter.returned", refused)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (credited > 0) {
            BlockPos middle = middleCounter(server);
            ServerPlayNetworking.send(player, new CasinoNet.Fx(CasinoNet.Fx.DEPOSIT, credited, Integer.toString(sold),
                    middle == null ? 0L : middle.asLong()));
            playAtCounter(player, CasinoSounds.REGISTER, 0.8f, 1.0f);
            CasinoBooth.gesture(server, CroupierEntity.GESTURE_TAKE, 1);
            CasinoBooth.gestureLater(server, CroupierEntity.GESTURE_PAY, 1, 14);
            CasinoEconomy.sync(player);
        } else {
            playAtCounter(player, CasinoSounds.LOSE, 0.4f, 1.3f);
        }
        sync(player);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            ServerLevel level = (ServerLevel) player.level();
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack));
        }
        player.getInventory().setChanged();
    }

    /** Everything back to its owner: they leave, the server stops, or the run is over. */
    public static void returnAll(ServerPlayer player) {
        List<ItemStack> pile = PENDING.remove(player.getUUID());
        if (pile == null) return;
        for (ItemStack s : pile) give(player, s);
    }

    public static void returnAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) returnAll(p);
        PENDING.clear();
    }

    public static boolean hasStack(ServerPlayer player, int slot) {
        List<ItemStack> pile = PENDING.get(player.getUUID());
        return pile != null && slot >= 0 && slot < pile.size();
    }

    public static boolean hasPile(ServerPlayer player) {
        List<ItemStack> pile = PENDING.get(player.getUUID());
        return pile != null && !pile.isEmpty();
    }

    public static BlockPos middleCounter(MinecraftServer server) {
        BlockPos a = CasinoBooth.anchorOf(server);
        return a == null ? null : CasinoBooth.counter(a, 1);
    }

    private static void playAtCounter(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        BlockPos c = middleCounter(player.level().getServer());
        if (c == null) {
            CasinoEconomy.playAt(player, sound, volume, pitch);
            return;
        }
        player.level().playSound(null, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static void sync(ServerPlayer player) {
        List<ItemStack> pile = PENDING.getOrDefault(player.getUUID(), List.of());
        List<ItemStack> copies = new ArrayList<>(pile.size());
        long[] values = new long[pile.size()];
        for (int i = 0; i < pile.size(); i++) {
            copies.add(pile.get(i).copy());
            values[i] = EmcValues.stackValue(pile.get(i));
        }
        BlockPos middle = middleCounter(player.level().getServer());
        ServerPlayNetworking.send(player, new CasinoNet.Pending(middle == null ? 0L : middle.asLong(), copies, values));
    }

    public static void reset() {
        PENDING.clear();
    }
}
