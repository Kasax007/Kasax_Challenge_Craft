package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the crash pad. Every pad runs its own shared round: the first stake opens a
 * ten-second betting window, then the rocket lifts off and the multiplier climbs until the secret
 * crash point. Players cash out by right-clicking the pad or with the cash-out key from anywhere.
 * The crash point is drawn at lift-off and sent to clients only once the rocket has exploded.
 */
public final class CrashGame {
    public static final int WAITING = 0, BETTING = 1, FLYING = 2, CRASHED = 3;
    private static final int BETTING_TICKS = 200;
    private static final int AFTERMATH_TICKS = 70;
    private static final double VIEW_RADIUS = 96.0;

    private static final class Seat {
        final String name;
        final long stake;
        int cashedAt; // multiplier × 100, 0 = still aboard
        long payout;

        Seat(String name, long stake) {
            this.name = name;
            this.stake = stake;
        }
    }

    private static final class Round {
        final String dimension;
        final BlockPos pos;
        int phase = WAITING;
        int ticksLeft;
        int flightTicks;
        double crashPoint;
        int crashedAt;
        final Map<UUID, Seat> seats = new LinkedHashMap<>();
        final List<Integer> history = new ArrayList<>();

        Round(String dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    private static final Map<String, Round> ROUNDS = new HashMap<>();
    private static int serial;

    private CrashGame() {
    }

    private static String key(String dim, BlockPos pos) {
        return dim + "|" + pos.asLong();
    }

    private static Round round(ServerLevel level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        return ROUNDS.computeIfAbsent(key(dim, pos), k -> new Round(dim, pos.immutable()));
    }

    public static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Round r = round(level, pos);
        Seat seat = r.seats.get(player.getUUID());
        if (r.phase == FLYING) {
            if (seat != null && seat.cashedAt == 0) {
                cashOut(level.getServer(), r, player.getUUID(), player);
            } else {
                player.sendOverlayMessage(Component.translatable("challengecraft.casino.crash.next_round").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (r.phase == CRASHED) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.crash.next_round").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (seat != null) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.crash.already").withStyle(ChatFormatting.GRAY));
            return;
        }
        CasinoAccount account = CasinoEconomy.account(player);
        long stake = CasinoGames.betAmount(account);
        if (stake < 10 * CasinoAccount.CENTI || account.balance < stake) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.poor", CasinoEconomy.formatFull(stake))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        account.balance -= stake;
        CasinoSavedData.get(level.getServer()).touch();
        r.seats.put(player.getUUID(), new Seat(player.getName().getString(), stake));
        if (r.phase == WAITING) {
            r.phase = BETTING;
            r.ticksLeft = BETTING_TICKS;
        }
        level.playSound(null, pos, CasinoSounds.CHIP_STACK, SoundSource.BLOCKS, 0.9f, 1.0f);
        player.sendOverlayMessage(Component.translatable("challengecraft.casino.crash.joined", CasinoEconomy.formatFull(stake))
                .withStyle(ChatFormatting.GOLD));
        CasinoEconomy.sync(player);
        broadcast(level, r);
    }

    /** The cash-out key: leaves whichever rocket the player is riding. */
    public static void cashOutAnywhere(ServerPlayer player) {
        for (Round r : ROUNDS.values()) {
            Seat s = r.seats.get(player.getUUID());
            if (r.phase == FLYING && s != null && s.cashedAt == 0) {
                cashOut(player.level().getServer(), r, player.getUUID(), player);
                return;
            }
        }
    }

    private static void cashOut(MinecraftServer server, Round r, UUID uuid, ServerPlayer player) {
        Seat s = r.seats.get(uuid);
        double m = CrashMath.multiplierAt(r.flightTicks);
        if (m >= r.crashPoint) return; // too late: the rocket is already gone this tick
        s.cashedAt = (int) Math.floor(m * 100);
        s.payout = (long) Math.floor(s.stake * m);
        CasinoAccount a = CasinoSavedData.get(server).existing(uuid);
        if (a != null) a.balance += s.payout;
        CasinoSavedData.get(server).touch();
        ServerLevel level = CasinoDevices.levelOf(server, r.dimension);
        if (player != null) {
            CasinoGames.settle(player, s.stake, s.payout);
            CasinoEconomy.sync(player);
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.crash.cashed",
                    String.format(java.util.Locale.ROOT, "%.2f", m), CasinoEconomy.formatFull(s.payout)).withStyle(ChatFormatting.GREEN));
        }
        if (level != null) {
            level.playSound(null, r.pos, CasinoSounds.CASH_OUT, SoundSource.BLOCKS, 0.8f, 1.0f);
            broadcast(level, r);
        }
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Round> it = ROUNDS.values().iterator(); it.hasNext(); ) {
            Round r = it.next();
            ServerLevel level = CasinoDevices.levelOf(server, r.dimension);
            if (level == null) continue;
            switch (r.phase) {
                case WAITING -> {
                    if (r.seats.isEmpty()) {
                        // An idle pad with nobody around needs no state at all.
                        if (CasinoGames.near(level, r.pos, VIEW_RADIUS).isEmpty()) it.remove();
                    }
                }
                case BETTING -> {
                    r.ticksLeft--;
                    if (r.ticksLeft % 20 == 0 && r.ticksLeft > 0 && r.ticksLeft <= 60) {
                        level.playSound(null, r.pos, CasinoSounds.TICK, SoundSource.BLOCKS, 0.8f, 1.2f);
                    }
                    if (r.ticksLeft <= 0) {
                        r.phase = FLYING;
                        r.flightTicks = 0;
                        r.crashPoint = CrashMath.crashPoint(level.getRandom());
                        level.playSound(null, r.pos, CasinoSounds.ROCKET_LAUNCH, SoundSource.BLOCKS, 0.9f, 1.0f);
                        broadcast(level, r);
                    } else if (r.ticksLeft % 10 == 0) {
                        broadcast(level, r);
                    }
                }
                case FLYING -> {
                    r.flightTicks++;
                    double m = CrashMath.multiplierAt(r.flightTicks);
                    if (m >= r.crashPoint) {
                        explode(server, level, r);
                    } else if (r.flightTicks % 10 == 0) {
                        broadcast(level, r);
                    }
                }
                case CRASHED -> {
                    r.ticksLeft--;
                    if (r.ticksLeft <= 0) {
                        r.phase = WAITING;
                        r.seats.clear();
                        r.crashedAt = 0;
                        r.flightTicks = 0;
                        broadcast(level, r);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void explode(MinecraftServer server, ServerLevel level, Round r) {
        boolean space = r.crashPoint >= CrashMath.CAP;
        r.crashedAt = (int) Math.floor(Math.min(r.crashPoint, CrashMath.CAP) * 100);
        for (Map.Entry<UUID, Seat> e : r.seats.entrySet()) {
            Seat s = e.getValue();
            if (s.cashedAt != 0) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (space) {
                s.cashedAt = (int) (CrashMath.CAP * 100);
                s.payout = (long) (s.stake * CrashMath.CAP);
                CasinoAccount a = CasinoSavedData.get(server).existing(e.getKey());
                if (a != null) a.balance += s.payout;
                if (p != null) CasinoGames.settle(p, s.stake, s.payout);
            } else if (p != null) {
                CasinoGames.settle(p, s.stake, 0);
                p.sendOverlayMessage(Component.translatable("challengecraft.casino.crash.lost",
                        CasinoEconomy.formatFull(s.stake)).withStyle(ChatFormatting.RED));
            }
            if (p != null) CasinoEconomy.sync(p);
        }
        CasinoSavedData.get(server).touch();
        r.history.add(0, r.crashedAt);
        while (r.history.size() > 10) r.history.remove(r.history.size() - 1);
        r.phase = CRASHED;
        r.ticksLeft = AFTERMATH_TICKS;
        double height = Math.min(90.0, 1.2 * r.flightTicks);
        if (space) {
            level.playSound(null, r.pos, CasinoSounds.WIN_EPIC, SoundSource.BLOCKS, 1.0f, 1.0f);
        } else {
            level.playSound(null, r.pos.getX() + 0.5, r.pos.getY() + height, r.pos.getZ() + 0.5,
                    SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 2.0f, 0.8f);
            level.playSound(null, r.pos, CasinoSounds.CRASH, SoundSource.BLOCKS, 0.9f, 1.0f);
        }
        broadcast(level, r);
    }

    private static void broadcast(ServerLevel level, Round r) {
        List<CasinoNet.CrashSeat> seats = new ArrayList<>();
        for (Seat s : r.seats.values()) seats.add(new CasinoNet.CrashSeat(s.name, s.stake, s.cashedAt));
        int[] history = r.history.stream().mapToInt(Integer::intValue).toArray();
        CasinoNet.CrashState packet = new CasinoNet.CrashState(r.pos.asLong(), r.phase, Math.max(0, r.ticksLeft),
                r.flightTicks, r.phase == CRASHED ? r.crashedAt : 0, seats, history, ++serial);
        for (ServerPlayer p : CasinoGames.near(level, r.pos, VIEW_RADIUS)) ServerPlayNetworking.send(p, packet);
    }

    public static void refundAll(MinecraftServer server) {
        for (Round r : ROUNDS.values()) {
            if (r.phase != BETTING && r.phase != FLYING) continue;
            for (Map.Entry<UUID, Seat> e : r.seats.entrySet()) {
                if (e.getValue().cashedAt != 0) continue;
                CasinoAccount a = CasinoSavedData.get(server).existing(e.getKey());
                if (a != null) a.balance += e.getValue().stake;
            }
        }
        ROUNDS.clear();
        CasinoSavedData.get(server).touch();
    }

    public static void reset() {
        ROUNDS.clear();
    }
}
