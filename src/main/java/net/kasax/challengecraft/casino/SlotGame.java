package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the slot machine. The whole play (paid spin plus every free spin it triggers) is
 * decided the moment the lever is pulled and sent to every client nearby, who all watch the same
 * reels; the win is credited only when the presentation has finished, so the balance on the HUD
 * never gives the result away early. A machine runs one play at a time, a player may keep several
 * machines running at once.
 *
 * <h2>Item stakes</h2>
 * Right-clicking the machine with an item bets the whole stack at its Jeton value. The first click
 * only quotes the stake; a second click within three seconds pulls the lever — so a pickaxe in hand
 * is never gambled away by accident. Winnings of an item stake come back <b>as that item</b>, shot
 * out of the machine as a fountain; whatever cannot be paid in whole items goes to the wallet.
 */
public final class SlotGame {
    private static final double VIEW_RADIUS = 48.0;
    private static final int CONFIRM_TICKS = 60;
    /** More stacks than this would bury the machine; the rest is paid in chips. */
    private static final int MAX_FOUNTAIN_STACKS = 40;

    private record Quote(long pos, Item item, int count, long expires) {
    }

    private record Payout(UUID player, String dimension, long pos, long stake, long win, Item item, long unitCenti,
                          long due, boolean announce) {
    }

    private record Fountain(String dimension, long pos, Direction facing, Item item, int remaining) {
    }

    private static final Map<Long, Long> BUSY_UNTIL = new HashMap<>();
    private static final Map<UUID, Quote> QUOTES = new HashMap<>();
    private static final List<Payout> PAYOUTS = new ArrayList<>();
    private static final List<Fountain> FOUNTAINS = new ArrayList<>();
    private static int serial;

    private SlotGame() {
    }

    public static void use(ServerPlayer player, BlockPos pos, ItemStack held) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getServer().getTickCount();
        if (BUSY_UNTIL.getOrDefault(pos.asLong(), 0L) > now) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.slot.busy").withStyle(ChatFormatting.GRAY));
            return;
        }
        CasinoAccount account = CasinoEconomy.account(player);

        if (!held.isEmpty() && !CasinoEconomy.isWallet(held.getItem())) {
            long unit = EmcValues.unitValue(held);
            if (unit <= 0) {
                player.sendOverlayMessage(Component.translatable("challengecraft.casino.slot.worthless", held.getHoverName())
                        .withStyle(ChatFormatting.RED));
                return;
            }
            Quote q = QUOTES.get(player.getUUID());
            boolean confirmed = q != null && q.pos == pos.asLong() && q.item == held.getItem()
                    && q.count == held.getCount() && q.expires >= now;
            if (!confirmed) {
                QUOTES.put(player.getUUID(), new Quote(pos.asLong(), held.getItem(), held.getCount(), now + CONFIRM_TICKS));
                player.sendOverlayMessage(Component.translatable("challengecraft.casino.slot.quote", held.getCount(),
                        held.getHoverName(), CasinoEconomy.formatFull(unit * held.getCount() * CasinoAccount.CENTI))
                        .withStyle(ChatFormatting.GOLD));
                level.playSound(null, pos, CasinoSounds.CHIP, SoundSource.BLOCKS, 0.7f, 1.2f);
                return;
            }
            QUOTES.remove(player.getUUID());
            long stake = unit * held.getCount() * CasinoAccount.CENTI;
            Item item = held.getItem();
            held.setCount(0);
            spin(player, level, pos, stake, item, unit * CasinoAccount.CENTI, now);
            return;
        }

        long stake = CasinoGames.betAmount(account);
        if (stake < 10 * CasinoAccount.CENTI || account.balance < stake) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.poor", CasinoEconomy.formatFull(stake))
                    .withStyle(ChatFormatting.RED));
            level.playSound(null, pos, CasinoSounds.LOSE, SoundSource.BLOCKS, 0.4f, 1.5f);
            return;
        }
        account.balance -= stake;
        spin(player, level, pos, stake, null, 0L, now);
    }

    private static void spin(ServerPlayer player, ServerLevel level, BlockPos pos, long stake, Item item, long unitCenti, long now) {
        RandomSource random = level.getRandom();
        SlotMath.Play play = SlotMath.play(random);
        long win = SlotMath.pay(play.units(), stake);
        int ticks = SlotMath.presentationTicks(play);

        int n = play.spins().size();
        int[] stops = new int[n * SlotMath.REELS];
        long[] wins = new long[n];
        int[] masks = new int[n];
        for (int i = 0; i < n; i++) {
            SlotMath.Spin s = play.spins().get(i);
            System.arraycopy(s.stops(), 0, stops, i * SlotMath.REELS, SlotMath.REELS);
            wins[i] = SlotMath.pay(s.units(), stake);
            masks[i] = s.expandMask();
        }
        String itemId = item == null ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
        int payCount = item == null ? 0 : (int) Math.min(Integer.MAX_VALUE, win / unitCenti);
        CasinoNet.SlotResult packet = new CasinoNet.SlotResult(pos.asLong(), player.getName().getString(), stake,
                stops, wins, masks, play.lucky(), win, itemId, payCount, ++serial);
        for (ServerPlayer p : CasinoGames.near(level, pos, VIEW_RADIUS)) {
            ServerPlayNetworking.send(p, packet);
        }

        BUSY_UNTIL.put(pos.asLong(), now + ticks + 4);
        PAYOUTS.add(new Payout(player.getUUID(), level.dimension().identifier().toString(), pos.asLong(), stake, win,
                item, unitCenti, now + ticks, win >= stake * 50));
        level.playSound(null, pos, CasinoSounds.LEVER, SoundSource.BLOCKS, 0.9f, 1.0f);
        CasinoSavedData.get(level.getServer()).touch();
        CasinoEconomy.sync(player);
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Payout> it = PAYOUTS.iterator(); it.hasNext(); ) {
            Payout p = it.next();
            if (p.due > now) continue;
            it.remove();
            pay(server, p);
        }
        for (Iterator<Fountain> it = FOUNTAINS.iterator(); it.hasNext(); ) {
            Fountain f = it.next();
            ServerLevel level = CasinoDevices.levelOf(server, f.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            int remaining = f.remaining;
            // Four stacks a tick: fast enough to feel like a jackpot, slow enough to read as a fountain.
            for (int i = 0; i < 4 && remaining > 0; i++) {
                int max = Math.max(1, f.item.getDefaultMaxStackSize());
                int count = Math.min(max, remaining);
                remaining -= count;
                spawnFountainItem(level, BlockPos.of(f.pos), f.facing, new ItemStack(f.item, count));
            }
            it.remove();
            if (remaining > 0) FOUNTAINS.add(0, new Fountain(f.dimension, f.pos, f.facing, f.item, remaining));
            break; // one fountain per tick, the list is re-read next tick
        }
    }

    private static void pay(MinecraftServer server, Payout p) {
        ServerPlayer player = server.getPlayerList().getPlayer(p.player);
        CasinoSavedData data = CasinoSavedData.get(server);
        CasinoAccount account = data.existing(p.player);
        if (account == null) return;
        long toWallet = p.win;
        if (p.item != null && p.win > 0) {
            ServerLevel level = CasinoDevices.levelOf(server, p.dimension);
            long count = p.win / p.unitCenti;
            int max = Math.max(1, p.item.getDefaultMaxStackSize());
            long physical = Math.min(count, (long) MAX_FOUNTAIN_STACKS * max);
            if (level != null && physical > 0) {
                BlockState state = level.getBlockState(BlockPos.of(p.pos));
                Direction facing = state.getBlock() instanceof CasinoDeviceBlock
                        ? state.getValue(CasinoDeviceBlock.FACING) : Direction.NORTH;
                FOUNTAINS.add(new Fountain(p.dimension, p.pos, facing, p.item, (int) physical));
                toWallet = p.win - physical * p.unitCenti;
            }
        }
        account.balance += toWallet;
        data.touch();
        if (player != null) {
            CasinoGames.settle(player, p.stake, p.win);
            CasinoEconomy.sync(player);
            if (p.announce) {
                Component msg = Component.translatable("challengecraft.casino.slot.announce", player.getName(),
                        CasinoEconomy.formatFull(p.win), p.stake > 0 ? p.win / p.stake : 0).withStyle(ChatFormatting.GOLD);
                for (ServerPlayer other : server.getPlayerList().getPlayers()) other.sendSystemMessage(msg);
            }
        }
    }

    private static void spawnFountainItem(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack) {
        RandomSource r = level.getRandom();
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.55;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55;
        double y = pos.getY() + 0.9;
        ItemEntity e = new ItemEntity(level, x, y, z, stack);
        double spread = 0.12;
        e.setDeltaMovement(facing.getStepX() * 0.18 + (r.nextDouble() - 0.5) * spread,
                0.45 + r.nextDouble() * 0.25,
                facing.getStepZ() * 0.18 + (r.nextDouble() - 0.5) * spread);
        level.addFreshEntity(e);
        if (r.nextFloat() < 0.35f) {
            level.playSound(null, pos, CasinoSounds.COINS, SoundSource.BLOCKS, 0.5f, 0.9f + r.nextFloat() * 0.3f);
        }
    }

    /** Pays everything still pending — the server is stopping and nobody may lose a win to that. */
    public static void flush(MinecraftServer server) {
        for (Payout p : new ArrayList<>(PAYOUTS)) {
            CasinoAccount account = CasinoSavedData.get(server).existing(p.player);
            if (account != null) account.balance += p.win;
        }
        PAYOUTS.clear();
        FOUNTAINS.clear();
        BUSY_UNTIL.clear();
        QUOTES.clear();
        CasinoSavedData.get(server).touch();
    }

    public static void reset() {
        PAYOUTS.clear();
        FOUNTAINS.clear();
        BUSY_UNTIL.clear();
        QUOTES.clear();
    }

    /** Item id helper for the client. */
    public static Item itemById(String id) {
        if (id == null || id.isEmpty()) return Items.AIR;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        return item == null ? Items.AIR : item;
    }
}
