package net.kasax.challengecraft;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.StatsManager;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.network.LevelSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Owns the level curve, perk unlock thresholds, and XP sync fan-out. */
public class LevelManager {
    public static final int MAX_LEVEL = 20;

    public static final int PERK_NIGHT_VISION = 101;
    public static final int PERK_SWIFT_FOOTING = 102;
    public static final int PERK_TOUGH_SKIN = 103;
    public static final int PERK_FIRE_RESISTANCE = 104;
    public static final int PERK_STRENGTH = 105;
    public static final int PERK_SCHOLAR = 106;
    public static final int PERK_RESISTANCE = 107;
    public static final int PERK_INFINITY_WEAPON = 108;
    public static final int PERK_INFINITE_CHEST = 109;

    public static final List<Integer> ALL_PERKS = List.of(
            PERK_NIGHT_VISION,
            PERK_SWIFT_FOOTING,
            PERK_TOUGH_SKIN,
            PERK_FIRE_RESISTANCE,
            PERK_STRENGTH,
            PERK_SCHOLAR,
            PERK_RESISTANCE,
            PERK_INFINITY_WEAPON,
            PERK_INFINITE_CHEST
    );

    public static int getLevelForXp(long totalXp) {
        // Inverts the triangular XP curve without walking every level boundary.
        int level = (int) ((1 + Math.sqrt(1 + 0.08 * totalXp)) / 2);
        return Math.min(MAX_LEVEL, Math.max(1, level));
    }

    public static long getXpForLevel(int level) {
        if (level <= 1) return 0;
        return 50L * level * (level - 1);
    }

    public static long getXpNeededForNextLevel(int level) {
        if (level >= MAX_LEVEL) return 1000L; // Stars
        return 100L * level;
    }

    public static int getStars(long totalXp) {
        long maxXp = getXpForLevel(MAX_LEVEL);
        if (totalXp <= maxXp) return 0;
        return (int) ((totalXp - maxXp) / 1000);
    }

    public static class XpResult {
        public final long oldXp;
        public final long newXp;
        public final long actualAmount;

        public XpResult(long oldXp, long newXp, long actualAmount) {
            this.oldXp = oldXp;
            this.newXp = newXp;
            this.actualAmount = actualAmount;
        }
    }

    public static XpResult addXp(ServerPlayer player, long amount) {
        long currentXp = XpManager.getXp(player.getUUID());
        int oldLevel = getLevelForXp(currentXp);
        
        XpManager.addXp(player.getUUID(), amount);
        long newXp = XpManager.getXp(player.getUUID());
        
        int newLevel = getLevelForXp(newXp);
        if (newLevel > oldLevel && newLevel <= MAX_LEVEL) {
            onLevelUp(player, newLevel);
        } else if (getStars(newXp) > getStars(currentXp)) {
            onStarGain(player, getStars(newXp), currentXp, newXp);
        }
        
        sync(player);
        return new XpResult(currentXp, newXp, amount);
    }

    private static void onLevelUp(ServerPlayer player, int newLevel) {
        player.sendSystemMessage(Component.translatable("challengecraft.level.up", newLevel).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        
        for (int perkId : ALL_PERKS) {
            if (getRequiredLevel(perkId) == newLevel) {
                Component name = Component.translatable("challengecraft.perk." + perkId).copy().withStyle(ChatFormatting.YELLOW);
                player.sendSystemMessage(Component.translatable("challengecraft.level.unlock_perk", name).withStyle(ChatFormatting.GREEN));
            }
        }

        if (newLevel == 20) {
            player.sendSystemMessage(Component.translatable("challengecraft.level.master").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
    }

    private static void onStarGain(ServerPlayer player, int starCount, long oldXp, long newXp) {
        player.sendSystemMessage(Component.translatable("challengecraft.level.infinity_star", starCount).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        
        if (starCount == 20) {
             player.sendSystemMessage(Component.translatable(
                     "challengecraft.level.secret_unlock",
                     Component.translatable("challengecraft.perk." + PERK_INFINITY_WEAPON).copy().withStyle(ChatFormatting.GOLD)
             ).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        }

        ServerPlayNetworking.send(player, new net.kasax.challengecraft.network.ChallengeRewardPacket(oldXp, newXp, newXp - oldXp, false));
    }

    public static String getStarReward(int stars) {
        return switch (stars) {
            case 1 -> "green";
            case 3 -> "blue";
            case 5 -> "red";
            case 8 -> "purple";
            case 10 -> "gold";
            case 15 -> "rainbow";
            case 20 -> "perk_infinity_weapon";
            default -> null;
        };
    }

    public static String getNameColor(int stars) {
        if (stars >= 15) return "rainbow";
        if (stars >= 10) return "gold";
        if (stars >= 8) return "purple";
        if (stars >= 5) return "red";
        if (stars >= 3) return "blue";
        if (stars >= 1) return "green";
        return null;
    }

    public static void sync(ServerPlayer player) {
        long xp = XpManager.getXp(player.getUUID());
        ChallengeCraft.LOGGER.info("[Server] Syncing XP for {} (UUID: {}): {}", player.getName().getString(), player.getUUID(), xp);
        
        // Other clients need this for name-tag styling, not just the local HUD.
        LevelSyncPacket pkt = new LevelSyncPacket(xp, player.getUUID());
        for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, pkt);
        }

        java.util.Map<Integer, Integer> times = StatsManager.getBestTimes(player.getStringUUID());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new net.kasax.challengecraft.network.StatsSyncPacket(times));
        ChallengeCraft.LOGGER.info("[Server] Synced Level ({}) and Stats ({}) to player {}", getLevelForXp(xp), times.size(), player.getName().getString());
    }

    public static boolean isChallengeUnlocked(int id, int level) {
        return level >= getRequiredLevel(id);
    }

    public static int getRequiredLevel(int id) {
        return switch (id) {
            case 1, 10, 16, 17, 18, 40 -> 1;
            case 4, 5 -> 2;
            case 6, 7, 37 -> 3;
            case 8, 13 -> 4;
            case 11, 12, 27 -> 5;
            case 20, 26 -> 6;
            case 21, 38 -> 7;
            case 24, 28, 30 -> 8;
            case 25, 31 -> 9;
            case 9, 32 -> 10;
            case 29 -> 11;
            case 2, 33 -> 12;
            case 3, 39 -> 13;
            case 34 -> 14;
            case 23 -> 15;
            case 14 -> 16;
            case 15 -> 17;
            case 36 -> 17;
            case 35 -> 18;
            case 19 -> 19;
            case 22 -> 20;
            
            case PERK_NIGHT_VISION -> 3;
            case PERK_SWIFT_FOOTING -> 5;
            case PERK_TOUGH_SKIN -> 10;
            case PERK_FIRE_RESISTANCE -> 11;
            case PERK_STRENGTH -> 14;
            case PERK_SCHOLAR -> 15;
            case PERK_RESISTANCE -> 18;
            case PERK_INFINITE_CHEST -> 20;
            case PERK_INFINITY_WEAPON -> 999; // This perk is unlocked by star count, not player level.

            default -> 1;
        };
    }

    public static long getPlayerXp(net.minecraft.world.entity.player.Player player) {
        if (player == null) return 0;
        return net.kasax.challengecraft.util.XpLookupProxy.getXp(player.getUUID());
    }
}
