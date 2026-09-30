package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the roulette table. One shared round per table: the first chip on the felt starts
 * a 25-second betting window, then the ball runs for eight seconds and every bet is settled by
 * {@link RouletteMath}. The winning number is decided when the ball is launched — bets are closed
 * by then, so sending it to the clients for the animation gives nothing away.
 *
 * <p>Bets are placed on the table itself: right-clicking a spot of the layout puts the player's
 * selected chip there, left-clicking it takes that player's chips back (see {@link DeviceLayouts}).
 */
public final class RouletteGame {
    public static final int WAITING = 0, BETTING = 1, SPINNING = 2, RESULT = 3;
    public static final int BETTING_TICKS = 500;
    public static final int SPIN_TICKS = 160;
    public static final int RESULT_TICKS = 90;
    private static final double VIEW_RADIUS = 32.0;
    private static final int MAX_BETS_PER_PLAYER = 40;

    private static final class Bet {
        final UUID player;
        final String name;
        final int kind;
        final int target;
        long stake;
        /** Each placement in chips, in order, so the stack on the felt is the chips really laid. */
        final List<Long> placed = new ArrayList<>();

        Bet(UUID player, String name, int kind, int target, long stake) {
            this.player = player;
            this.name = name;
            this.kind = kind;
            this.target = target;
            this.stake = stake;
        }
    }

    private static final class Table {
        final String dimension;
        final BlockPos pos;
        int phase = WAITING;
        int ticksLeft;
        int result = -1;
        final List<Bet> bets = new ArrayList<>();
        final List<Integer> history = new ArrayList<>();

        Table(String dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    private static final Map<String, Table> TABLES = new HashMap<>();
    private static int serial;

    private RouletteGame() {
    }

    private static Table table(ServerLevel level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        return TABLES.computeIfAbsent(dim + "|" + pos.asLong(), k -> new Table(dim, pos.immutable()));
    }

    public static void placeBet(ServerPlayer player, BlockPos pos, int kind, int target, long chips) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (CasinoDevices.at(level, pos) != DeviceType.ROULETTE) return;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12 * 12) return;
        Table t = table(level, pos);
        if (t.phase != WAITING && t.phase != BETTING) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.roulette.closed").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!RouletteMath.valid(kind, target) || chips < 1) return;
        long stake = chips * CasinoAccount.CENTI;
        CasinoAccount account = CasinoEconomy.account(player);
        if (account.balance < stake) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.poor", CasinoEconomy.formatFull(stake))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Bet existing = null;
        int mine = 0;
        for (Bet b : t.bets) {
            if (!b.player.equals(player.getUUID())) continue;
            mine++;
            if (b.kind == kind && b.target == target) existing = b;
        }
        if (existing == null && mine >= MAX_BETS_PER_PLAYER) return;
        account.balance -= stake;
        CasinoSavedData.get(level.getServer()).touch();
        if (existing == null) {
            existing = new Bet(player.getUUID(), player.getName().getString(), kind, target, 0);
            t.bets.add(existing);
        }
        existing.stake += stake;
        existing.placed.add(chips);
        if (t.phase == WAITING) {
            t.phase = BETTING;
            t.ticksLeft = BETTING_TICKS;
        }
        level.playSound(null, pos, CasinoSounds.CHIP, SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        CasinoEconomy.sync(player);
        broadcast(level, t);
    }

    /** Whether a player has chips on one spot of a table that can still be taken back. */
    public static boolean hasBet(ServerPlayer player, BlockPos pos, int kind, int target) {
        Table t = TABLES.get(player.level().dimension().identifier() + "|" + pos.asLong());
        if (t == null || t.phase != BETTING) return false;
        for (Bet b : t.bets) {
            if (b.player.equals(player.getUUID()) && b.kind == kind && b.target == target) return true;
        }
        return false;
    }

    /** Takes a player's chips off one spot of the layout. */
    public static void removeBet(ServerPlayer player, BlockPos pos, int kind, int target) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Table t = TABLES.get(level.dimension().identifier() + "|" + pos.asLong());
        if (t == null || t.phase != BETTING) return;
        long refund = 0;
        for (Iterator<Bet> it = t.bets.iterator(); it.hasNext(); ) {
            Bet b = it.next();
            if (b.player.equals(player.getUUID()) && b.kind == kind && b.target == target) {
                refund += b.stake;
                it.remove();
            }
        }
        if (refund > 0) {
            CasinoEconomy.account(player).balance += refund;
            CasinoSavedData.get(level.getServer()).touch();
            level.playSound(null, pos, CasinoSounds.CHIP, SoundSource.BLOCKS, 0.6f, 0.8f);
            CasinoEconomy.sync(player);
            broadcast(level, t);
        }
    }

    public static void clearBets(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Table t = table(level, pos);
        if (t.phase != BETTING) return;
        long refund = 0;
        for (Iterator<Bet> it = t.bets.iterator(); it.hasNext(); ) {
            Bet b = it.next();
            if (b.player.equals(player.getUUID())) {
                refund += b.stake;
                it.remove();
            }
        }
        if (refund > 0) {
            CasinoEconomy.account(player).balance += refund;
            CasinoSavedData.get(level.getServer()).touch();
            level.playSound(null, pos, CasinoSounds.CHIP_STACK, SoundSource.BLOCKS, 0.6f, 1.1f);
            CasinoEconomy.sync(player);
            broadcast(level, t);
        }
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Table> it = TABLES.values().iterator(); it.hasNext(); ) {
            Table t = it.next();
            ServerLevel level = CasinoDevices.levelOf(server, t.dimension);
            if (level == null) continue;
            if ((t.phase == WAITING || t.phase == BETTING) && CasinoDevices.gone(level, t.pos, DeviceType.ROULETTE)) {
                // The table was picked up before the ball was thrown: every stake goes back. (Once
                // the ball runs the number is drawn, and the round is settled as usual.)
                for (Bet b : t.bets) CasinoDevices.refund(server, b.player, b.stake);
                it.remove();
                continue;
            }
            switch (t.phase) {
                case WAITING -> {
                    if (t.bets.isEmpty() && CasinoGames.near(level, t.pos, VIEW_RADIUS).isEmpty()) {
                        it.remove();
                    } else if (server.getTickCount() % 40 == 0) {
                        broadcast(level, t); // keeps the history board current for players walking up
                    }
                }
                case BETTING -> {
                    t.ticksLeft--;
                    if (t.ticksLeft > 0 && t.ticksLeft <= 100 && t.ticksLeft % 20 == 0) {
                        level.playSound(null, t.pos, CasinoSounds.TICK, SoundSource.BLOCKS, 0.7f, 1.0f);
                    }
                    if (t.ticksLeft <= 0) {
                        t.phase = SPINNING;
                        t.ticksLeft = SPIN_TICKS;
                        t.result = CasinoTestHooks.rouletteNumber(level.getRandom());
                        level.playSound(null, t.pos, CasinoSounds.ROULETTE_SPIN, SoundSource.BLOCKS, 1.0f, 1.0f);
                        broadcast(level, t);
                    } else if (t.ticksLeft % 20 == 0) {
                        broadcast(level, t);
                    }
                }
                case SPINNING -> {
                    t.ticksLeft--;
                    if (t.ticksLeft <= 0) {
                        settle(server, level, t);
                        t.phase = RESULT;
                        t.ticksLeft = RESULT_TICKS;
                        broadcast(level, t);
                    }
                }
                case RESULT -> {
                    t.ticksLeft--;
                    if (t.ticksLeft <= 0) {
                        t.phase = WAITING;
                        t.bets.clear();
                        t.result = -1;
                        broadcast(level, t);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void settle(MinecraftServer server, ServerLevel level, Table t) {
        Map<UUID, long[]> perPlayer = new LinkedHashMap<>(); // {stake, payout}
        for (Bet b : t.bets) {
            long pay = RouletteMath.payout(b.kind, b.target, b.stake, t.result);
            long[] sums = perPlayer.computeIfAbsent(b.player, k -> new long[2]);
            sums[0] += b.stake;
            sums[1] += pay;
        }
        boolean anyWin = false;
        for (Map.Entry<UUID, long[]> e : perPlayer.entrySet()) {
            CasinoAccount a = CasinoSavedData.get(server).existing(e.getKey());
            if (a != null) a.balance += e.getValue()[1];
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                CasinoGames.settle(p, e.getValue()[0], e.getValue()[1]);
                CasinoEconomy.sync(p);
                long net = e.getValue()[1] - e.getValue()[0];
                p.sendOverlayMessage(Component.translatable(net > 0 ? "challengecraft.casino.roulette.won"
                                : net < 0 ? "challengecraft.casino.roulette.lost" : "challengecraft.casino.roulette.push",
                        t.result, CasinoEconomy.formatFull(Math.abs(net)))
                        .withStyle(net > 0 ? ChatFormatting.GREEN : net < 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
                if (net > 0) anyWin = true;
            }
        }
        CasinoSavedData.get(server).touch();
        t.history.add(0, t.result);
        while (t.history.size() > 12) t.history.remove(t.history.size() - 1);
        level.playSound(null, t.pos, CasinoSounds.ROULETTE_DROP, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (anyWin) level.playSound(null, t.pos, CasinoSounds.WIN_SMALL, SoundSource.BLOCKS, 0.9f, 1.0f);
    }

    private static CasinoNet.RouletteState state(Table t) {
        List<CasinoNet.RouletteBet> bets = new ArrayList<>();
        for (Bet b : t.bets) bets.add(new CasinoNet.RouletteBet(b.name, b.kind, b.target, b.stake,
                b.placed.stream().mapToLong(Long::longValue).toArray()));
        int[] history = t.history.stream().mapToInt(Integer::intValue).toArray();
        // The number travels with the spin so the ball can land on it; bets are already closed.
        int shown = t.phase == SPINNING || t.phase == RESULT ? t.result : -1;
        return new CasinoNet.RouletteState(t.pos.asLong(), t.phase, Math.max(0, t.ticksLeft), shown, bets, history, ++serial);
    }

    private static void broadcast(ServerLevel level, Table t) {
        CasinoNet.RouletteState packet = state(t);
        for (ServerPlayer p : CasinoGames.near(level, t.pos, VIEW_RADIUS)) ServerPlayNetworking.send(p, packet);
    }

    public static void refundAll(MinecraftServer server) {
        for (Table t : TABLES.values()) {
            if (t.phase == RESULT) continue; // already settled
            for (Bet b : t.bets) {
                CasinoAccount a = CasinoSavedData.get(server).existing(b.player);
                if (a != null) a.balance += b.stake;
            }
        }
        TABLES.clear();
        CasinoSavedData.get(server).touch();
    }

    public static void reset() {
        TABLES.clear();
    }
}
