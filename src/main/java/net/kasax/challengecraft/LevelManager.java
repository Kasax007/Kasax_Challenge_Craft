package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.network.LevelSyncPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LevelManager {
    public static final int MAX_LEVEL = 20;
    
    public static final TrackedData<Integer> INFINITY_STARS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> LEVEL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final int PERK_NIGHT_VISION = 101;
    public static final int PERK_SWIFT_FOOTING = 102;
    public static final int PERK_TOUGH_SKIN = 103;
    public static final int PERK_FIRE_RESISTANCE = 104;
    public static final int PERK_STRENGTH = 105;
    public static final int PERK_SCHOLAR = 106;
    public static final int PERK_RESISTANCE = 107;
    public static final int PERK_INFINITY_WEAPON = 108;

    public static final List<Integer> ALL_PERKS = List.of(
            PERK_NIGHT_VISION,
            PERK_SWIFT_FOOTING,
            PERK_TOUGH_SKIN,
            PERK_FIRE_RESISTANCE,
            PERK_STRENGTH,
            PERK_SCHOLAR,
            PERK_RESISTANCE,
            PERK_INFINITY_WEAPON
    );

    public static int getLevelForXp(long totalXp) {
        // TotalXP = 50 * L * (L-1) => L^2 - L - TotalXP/50 = 0
        // L = (1 + sqrt(1 + 4 * TotalXP / 50)) / 2
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

    public static XpResult addXp(ServerPlayerEntity player, long amount) {
        long currentXp = XpManager.getXp(player.getUuid());
        int oldLevel = getLevelForXp(currentXp);
        
        ChallengeSavedData data = ChallengeSavedData.get(player.getServer().getOverworld());
        List<Integer> activePerks = data.getActivePerks();

        XpManager.addXp(player.getUuid(), amount);
        long newXp = XpManager.getXp(player.getUuid());
        
        int newLevel = getLevelForXp(newXp);
        if (newLevel > oldLevel && newLevel <= MAX_LEVEL) {
            onLevelUp(player, newLevel);
        } else if (getStars(newXp) > getStars(currentXp)) {
            onStarGain(player, getStars(newXp), currentXp, newXp);
        }
        
        sync(player);
        return new XpResult(currentXp, newXp, amount);
    }

    private static void onLevelUp(ServerPlayerEntity player, int newLevel) {
        player.sendMessage(net.minecraft.text.Text.literal("§6§lLevel Up! §rYou are now level §b" + newLevel), false);
        player.playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        
        // Check for perk unlocks
        for (int perkId : ALL_PERKS) {
            if (getRequiredLevel(perkId) == newLevel) {
                String name = net.minecraft.text.Text.translatable("challengecraft.perk." + perkId).getString();
                player.sendMessage(net.minecraft.text.Text.literal("§aUnlocked Perk: §e" + name), false);
            }
        }

        if (newLevel == 20) {
            player.sendMessage(net.minecraft.text.Text.literal("§d§lMASTER ACHIEVED! §rYou have reached the maximum level!"), false);
        }
    }

    private static void onStarGain(ServerPlayerEntity player, int starCount, long oldXp, long newXp) {
        player.sendMessage(net.minecraft.text.Text.literal("§e§l+1 Infinity Star! §rTotal Stars: §6" + starCount), false);
        player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        
        // Handle new rewards
        if (starCount == 20) {
             player.sendMessage(net.minecraft.text.Text.literal("§d§lSECRET UNLOCKED! §rYou have unlocked the §6Infinity Weapon §rperk!"), false);
        }

        // Show overlay
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

    public static void sync(ServerPlayerEntity player) {
        long xp = XpManager.getXp(player.getUuid());
        int stars = getStars(xp);
        int level = getLevelForXp(xp);
        
        // Sync DataTracker for name coloring and chat
        if (player.getDataTracker().get(INFINITY_STARS) != stars) {
             player.getDataTracker().set(INFINITY_STARS, stars);
        }
        if (player.getDataTracker().get(LEVEL) != level) {
             player.getDataTracker().set(LEVEL, level);
        }

        ServerPlayNetworking.send(player, new LevelSyncPacket(xp));
    }

    public static boolean isChallengeUnlocked(int id, int level) {
        return level >= getRequiredLevel(id);
    }

    public static int getRequiredLevel(int id) {
        return switch (id) {
            case 1, 10, 16, 17, 18 -> 1;
            case 4, 5 -> 2;
            case 6, 7 -> 3;
            case 8, 13 -> 4;
            case 11, 12 -> 5;
            case 20 -> 6;
            case 21 -> 7;
            case 24 -> 8;
            case 25 -> 9;
            case 9 -> 10;
            case 2 -> 12;
            case 3 -> 13;
            case 14 -> 16;
            case 15 -> 17;
            case 19 -> 19;
            case 22 -> 20;
            case 23 -> 15;
            
            // Perks
            case PERK_NIGHT_VISION -> 3;
            case PERK_SWIFT_FOOTING -> 5;
            case PERK_TOUGH_SKIN -> 10;
            case PERK_FIRE_RESISTANCE -> 11;
            case PERK_STRENGTH -> 14;
            case PERK_SCHOLAR -> 15;
            case PERK_RESISTANCE -> 18;
            case PERK_INFINITY_WEAPON -> 999; // Special handling for Infinity Weapon (Star 20)

            default -> 1;
        };
    }
}
