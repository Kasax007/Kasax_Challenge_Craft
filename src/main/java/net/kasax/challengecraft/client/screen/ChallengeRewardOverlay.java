package net.kasax.challengecraft.client.screen;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.kasax.challengecraft.LevelManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;

/** Brief XP reward animation shown after completed challenge milestones. */
public class ChallengeRewardOverlay {
    private static long startTime = -1;
    private static long xpGained = 0;
    private static long oldXp = 0;
    private static boolean isGameComp = false;
    private static final long DELAY_MS = 5000;
    private static final long DURATION_MS = 8000;

    public static void register() {
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("challengecraft", "challenge_reward_overlay"), (context, tickCounter) -> render(context, 0.0f));
    }

    public static void start(long oldXpVal, long newXpVal, long gain, boolean gameComp) {
        startTime = System.currentTimeMillis() + DELAY_MS;
        xpGained = gain;
        oldXp = oldXpVal;
        isGameComp = gameComp;
    }

    private static void render(GuiGraphicsExtractor context, float delta) {
        if (startTime == -1) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime < startTime) {
            return;
        }

        float progress = (currentTime - startTime) / (float) DURATION_MS;
        if (progress > 1.0f) {
            startTime = -1;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font tr = client.font;
        int width = context.guiWidth();
        int height = context.guiHeight();

        int centerY = height / 4;
        float alpha = 1.0f;
        if (progress < 0.1f) {
            alpha = progress / 0.1f;
        } else if (progress > 0.9f) {
            alpha = (1.0f - progress) / 0.1f;
        }

        int baseAlpha = (int) (alpha * 255) << 24;

        Component titleText = isGameComp
                ? Component.translatable("challengecraft.overlay.game_completed").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                : Component.translatable("challengecraft.overlay.challenge_complete").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        context.centeredText(tr, titleText, width / 2, centerY - 40, baseAlpha | (isGameComp ? 0xFF55FF : 0xFFAA00));

        float countProgress = Mth.clamp(progress * 2.0f, 0, 1);
        long currentXpDisplay = oldXp + (long) (xpGained * countProgress);
        context.centeredText(tr, Component.translatable("challengecraft.overlay.xp_total", currentXpDisplay), width / 2, centerY - 15, baseAlpha | 0xFFFFFF);

        Component gainedText = Component.translatable("challengecraft.overlay.xp_gain", xpGained).withStyle(ChatFormatting.GREEN);
        context.centeredText(tr, gainedText, width / 2, centerY - 5, baseAlpha | 0x55FF55);

        int oldLevel = LevelManager.getLevelForXp(oldXp);
        int currentLevel = LevelManager.getLevelForXp(currentXpDisplay);

        int barWidth = 250;
        int barHeight = 12;
        int barX = width / 2 - barWidth / 2;
        int barY = centerY + 15;

        float barProgress;
        Component levelText;
        if (currentLevel >= LevelManager.MAX_LEVEL) {
            long maxXp = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL);
            long starProgress = (currentXpDisplay - maxXp) % 1000;
            barProgress = (float) starProgress / 1000.0f;
            int stars = LevelManager.getStars(currentXpDisplay);
            levelText = Component.translatable("challengecraft.overlay.level_with_stars", stars);
        } else {
            long levelStartXp = LevelManager.getXpForLevel(currentLevel);
            long xpNeeded = LevelManager.getXpNeededForNextLevel(currentLevel);
            barProgress = (float) (currentXpDisplay - levelStartXp) / xpNeeded;
            levelText = Component.translatable("challengecraft.overlay.level", currentLevel);
        }

        int bgAlpha = (int) (alpha * 0x80) << 24;
        context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, baseAlpha | 0x000000);
        context.fill(barX, barY, barX + barWidth, barY + barHeight, bgAlpha);
        int fillWidth = (int) (barWidth * barProgress);
        if (fillWidth > 0) {
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, baseAlpha | 0x00AA00);
        }
        context.outline(barX - 1, barY - 1, barWidth + 2, barHeight + 2, baseAlpha | 0xAAAAAA);

        context.centeredText(tr, levelText, width / 2, barY - 12, baseAlpha | 0x00AAFF);

        Component xpText;
        if (currentLevel >= LevelManager.MAX_LEVEL) {
            long maxXp = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL);
            long starProgress = (currentXpDisplay - maxXp) % 1000;
            xpText = Component.translatable("challengecraft.overlay.star_progress", starProgress);
        } else {
            long levelStartXp = LevelManager.getXpForLevel(currentLevel);
            long xpNeeded = LevelManager.getXpNeededForNextLevel(currentLevel);
            xpText = Component.translatable("challengecraft.overlay.level_progress", currentXpDisplay - levelStartXp, xpNeeded);
        }
        context.centeredText(tr, xpText, width / 2, barY + 2, baseAlpha | 0xFFFFFF);

        int oldStarsDisplay = LevelManager.getStars(oldXp);
        int currentStarsDisplay = LevelManager.getStars(currentXpDisplay);

        if (currentLevel > oldLevel || currentStarsDisplay > oldStarsDisplay) {
            float lvPulse = (float) Math.sin(progress * 20) * 0.1f + 1.0f;
            context.pose().pushMatrix();
            context.pose().translate(width / 2f, centerY + 55);
            context.pose().scale(lvPulse, lvPulse);
            context.pose().translate(-(width / 2f), -(centerY + 55));

            if (currentLevel > oldLevel && currentLevel <= LevelManager.MAX_LEVEL) {
                context.centeredText(tr, Component.translatable("challengecraft.overlay.level_up").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), width / 2, centerY + 50, baseAlpha | 0xFF55FF);
            } else if (currentStarsDisplay > oldStarsDisplay) {
                context.centeredText(tr, Component.translatable("challengecraft.overlay.infinity_star").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), width / 2, centerY + 50, baseAlpha | 0xFFFF55);
            }
            context.pose().popMatrix();

            List<Object> rewards = new ArrayList<>();
            for (int l = oldLevel + 1; l <= Math.min(currentLevel, LevelManager.MAX_LEVEL); l++) {
                for (int perkId : LevelManager.ALL_PERKS) {
                    if (LevelManager.getRequiredLevel(perkId) == l) {
                        rewards.add("PERK_" + perkId);
                    }
                }
                if (l == 20) {
                    rewards.add("MASTER");
                }

                for (int id = 1; id <= 40; id++) {
                    if (LevelManager.getRequiredLevel(id) == l) {
                        rewards.add(id);
                    }
                }
            }

            for (int s = oldStarsDisplay + 1; s <= currentStarsDisplay; s++) {
                String starReward = LevelManager.getStarReward(s);
                if (starReward != null) {
                    if (starReward.startsWith("perk_")) {
                        rewards.add("PERK_" + LevelManager.PERK_INFINITY_WEAPON);
                    } else {
                        rewards.add("STAR_COLOR_" + starReward);
                    }
                } else {
                    rewards.add("STAR");
                }
            }

            if (!rewards.isEmpty()) {
                context.centeredText(tr, Component.translatable("challengecraft.overlay.rewards_unlocked").withStyle(ChatFormatting.YELLOW), width / 2, centerY + 75, baseAlpha | 0xFFFF55);
                int itemsPerRow = 8;
                int totalRewards = rewards.size();

                for (int i = 0; i < totalRewards; i++) {
                    int row = i / itemsPerRow;
                    int col = i % itemsPerRow;
                    int rowSize = Math.min(itemsPerRow, totalRewards - row * itemsPerRow);

                    int x = width / 2 - (rowSize * 24) / 2 + col * 24;
                    int y = centerY + 90 + row * 24;

                    context.pose().pushMatrix();
                    float iconFloat = (float) Math.sin(progress * 10 + i) * 2;
                    context.pose().translate(0, iconFloat);

                    Object reward = rewards.get(i);
                    if (reward instanceof Integer cid) {
                        ChallengeIconProvider.drawIcon(context, x, y, cid);
                    } else if (reward instanceof String s) {
                        if (s.startsWith("PERK_")) {
                            int perkId = Integer.parseInt(s.substring(5));
                            ChallengeIconProvider.drawIcon(context, x, y, perkId);
                            context.text(tr, "*", x + 12, y + 8, baseAlpha | 0xFFFF55, true);
                        } else if (s.startsWith("STAR_COLOR_")) {
                            String color = s.substring(11);
                            int c = switch (color) {
                                case "green" -> 0x55FF55;
                                case "blue" -> 0x5555FF;
                                case "red" -> 0xFF5555;
                                case "purple" -> 0xAA00FF;
                                case "gold" -> 0xFFAA00;
                                case "rainbow" -> 0x55FFFF;
                                default -> 0xFFFFFF;
                            };
                            context.text(tr, "*", x + 4, y + 4, baseAlpha | c, true);
                        } else if (s.equals("STAR")) {
                            context.text(tr, "*", x + 4, y + 4, baseAlpha | 0x888888, true);
                        } else {
                            context.text(tr, "*", x + 4, y + 4, baseAlpha | 0xFFFF55, true);
                        }
                    }
                    context.pose().popMatrix();
                }
            }
        }
    }
}
