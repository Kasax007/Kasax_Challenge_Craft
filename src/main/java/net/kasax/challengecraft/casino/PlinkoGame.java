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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the plinko board. Every right-click drops one ball for the player's current stake;
 * balls of any number of players fall side by side. The path is drawn when the ball is dropped and
 * sent to the clients, who animate it; the payout is credited when the ball reaches its bucket.
 */
public final class PlinkoGame {
    /** Ticks from the drop to the bucket — must match the client animation. */
    public static final int FALL_TICKS = 8 + PlinkoMath.ROWS * 4;
    private static final double VIEW_RADIUS = 48.0;
    /** Minimum ticks between two balls of one player, and balls one player may have in the air. */
    private static final int DROP_COOLDOWN = 4;
    private static final int MAX_IN_FLIGHT = 12;

    private record Ball(UUID player, String dimension, long pos, long stake, int path, long payout, long due) {
    }

    private static final List<Ball> BALLS = new ArrayList<>();
    private static final Map<UUID, Long> NEXT_DROP = new HashMap<>();
    private static int serial;

    private PlinkoGame() {
    }

    public static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long now = level.getServer().getTickCount();
        if (NEXT_DROP.getOrDefault(player.getUUID(), 0L) > now) return;
        int mine = 0;
        for (Ball b : BALLS) if (b.player.equals(player.getUUID())) mine++;
        if (mine >= MAX_IN_FLIGHT) return;
        CasinoAccount account = CasinoEconomy.account(player);
        long stake = CasinoGames.betAmount(account);
        if (stake < 10 * CasinoAccount.CENTI || account.balance < stake) {
            player.sendOverlayMessage(Component.translatable("challengecraft.casino.poor", CasinoEconomy.formatFull(stake))
                    .withStyle(ChatFormatting.RED));
            level.playSound(null, pos, CasinoSounds.LOSE, SoundSource.BLOCKS, 0.4f, 1.5f);
            return;
        }
        account.balance -= stake;
        int path = PlinkoMath.drop(level.getRandom());
        long payout = PlinkoMath.payout(stake, path);
        BALLS.add(new Ball(player.getUUID(), level.dimension().identifier().toString(), pos.asLong(), stake, path,
                payout, now + FALL_TICKS));
        NEXT_DROP.put(player.getUUID(), now + DROP_COOLDOWN);
        CasinoNet.PlinkoBall packet = new CasinoNet.PlinkoBall(pos.asLong(), player.getName().getString(), stake, path,
                payout, ++serial);
        for (ServerPlayer p : CasinoGames.near(level, pos, VIEW_RADIUS)) ServerPlayNetworking.send(p, packet);
        level.playSound(null, pos.above(), CasinoSounds.CHIP, SoundSource.BLOCKS, 0.8f, 1.3f);
        CasinoSavedData.get(level.getServer()).touch();
        CasinoEconomy.sync(player);
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Ball> it = BALLS.iterator(); it.hasNext(); ) {
            Ball b = it.next();
            if (b.due > now) continue;
            it.remove();
            land(server, b);
        }
    }

    private static void land(MinecraftServer server, Ball b) {
        CasinoAccount account = CasinoSavedData.get(server).existing(b.player);
        if (account != null) account.balance += b.payout;
        CasinoSavedData.get(server).touch();
        ServerPlayer player = server.getPlayerList().getPlayer(b.player);
        if (player != null) {
            CasinoGames.settle(player, b.stake, b.payout);
            CasinoEconomy.sync(player);
        }
        ServerLevel level = CasinoDevices.levelOf(server, b.dimension);
        if (level != null) {
            int mult = PlinkoMath.MULT100[PlinkoMath.bucket(b.path)];
            BlockPos pos = BlockPos.of(b.pos);
            if (mult >= 1000) {
                level.playSound(null, pos, CasinoSounds.WIN_BIG, SoundSource.BLOCKS, 0.9f, 1.0f);
            } else if (mult >= 200) {
                level.playSound(null, pos, CasinoSounds.WIN_SMALL, SoundSource.BLOCKS, 0.7f, 1.1f);
            } else if (mult >= 100) {
                level.playSound(null, pos, CasinoSounds.COINS, SoundSource.BLOCKS, 0.6f, 1.2f);
            }
        }
    }

    /** Pays every ball still falling — the server is stopping. */
    public static void flush(MinecraftServer server) {
        for (Ball b : BALLS) {
            CasinoAccount account = CasinoSavedData.get(server).existing(b.player);
            if (account != null) account.balance += b.payout;
        }
        BALLS.clear();
        NEXT_DROP.clear();
        CasinoSavedData.get(server).touch();
    }

    public static void reset() {
        BALLS.clear();
        NEXT_DROP.clear();
    }
}
