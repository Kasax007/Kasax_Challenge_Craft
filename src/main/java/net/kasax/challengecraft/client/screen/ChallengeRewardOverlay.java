package net.kasax.challengecraft.client.screen;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class ChallengeRewardOverlay {
    private static long startTime = -1;
    private static long xpGained = 0;
    private static long oldXp = 0;
    private static final long DELAY_MS = 5000;
    private static final long DURATION_MS = 8000;

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            render(context, 0.0f);
        });
    }

    public static void start(long oldXpVal, long newXpVal, long gain) {
        startTime = System.currentTimeMillis() + DELAY_MS;
        xpGained = gain;
        oldXp = oldXpVal;
    }

    private static void render(DrawContext context, float delta) {
        if (startTime == -1) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime < startTime) return;

        float progress = (currentTime - startTime) / (float) DURATION_MS;
        if (progress > 1.0f) {
            startTime = -1;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        int centerY = height / 4;
        // 1. Fade in/out factor
        float alpha = 1.0f;
        if (progress < 0.1f) alpha = progress / 0.1f;
        else if (progress > 0.9f) alpha = (1.0f - progress) / 0.1f;

        int baseAlpha = (int)(alpha * 255) << 24;

        // 2. Title: XP REWARDED
        context.drawCenteredTextWithShadow(tr, Text.literal("§6§l★ CHALLENGE COMPLETE ★"), width / 2, centerY - 40, baseAlpha | 0xFFAA00);

        // 3. XP Count animation
        float countProgress = MathHelper.clamp(progress * 2.0f, 0, 1);
        long currentXpDisplay = oldXp + (long) (xpGained * countProgress);
        context.drawCenteredTextWithShadow(tr, Text.literal("§b" + currentXpDisplay + " XP"), width / 2, centerY - 15, baseAlpha | 0xFFFFFF);
        
        Text gainedText = Text.literal("§a+" + xpGained + " XP").formatted();
        context.drawCenteredTextWithShadow(tr, gainedText, width / 2, centerY - 5, baseAlpha | 0x55FF55);

        // 4. Level Up logic & Rewards
        int oldLevel = LevelManager.getLevelForXp(oldXp);
        int currentLevel = LevelManager.getLevelForXp(currentXpDisplay);
        
        // XP Bar
        int barWidth = 250;
        int barHeight = 12;
        int barX = width / 2 - barWidth / 2;
        int barY = centerY + 15;
        
        float barProgress;
        String levelText;
        if (currentLevel >= LevelManager.MAX_LEVEL) {
            long maxXp = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL);
            long starProgress = (currentXpDisplay - maxXp) % 1000;
            barProgress = (float) starProgress / 1000.0f;
            int stars = LevelManager.getStars(currentXpDisplay);
            levelText = "Level 20 §e(★" + stars + ")";
        } else {
            long levelStartXp = LevelManager.getXpForLevel(currentLevel);
            long xpNeeded = LevelManager.getXpNeededForNextLevel(currentLevel);
            barProgress = (float) (currentXpDisplay - levelStartXp) / xpNeeded;
            levelText = "Level " + currentLevel;
        }

        // Draw Bar BG
        int bgAlpha = (int)(alpha * 0x80) << 24;
        context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, baseAlpha | 0x000000);
        context.fill(barX, barY, barX + barWidth, barY + barHeight, bgAlpha);
        // Draw Bar Fill
        int fillWidth = (int) (barWidth * barProgress);
        if (fillWidth > 0) {
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, baseAlpha | 0x00AA00);
        }
        context.drawBorder(barX - 1, barY - 1, barWidth + 2, barHeight + 2, baseAlpha | 0xAAAAAA);
        
        context.drawCenteredTextWithShadow(tr, Text.literal(levelText), width / 2, barY - 12, baseAlpha | 0x00AAFF);
        
        String xpText;
        if (currentLevel >= LevelManager.MAX_LEVEL) {
            long maxXp = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL);
            long starProgress = (currentXpDisplay - maxXp) % 1000;
            xpText = starProgress + " / 1000 XP";
        } else {
            long levelStartXp = LevelManager.getXpForLevel(currentLevel);
            long xpNeeded = LevelManager.getXpNeededForNextLevel(currentLevel);
            xpText = (currentXpDisplay - levelStartXp) + " / " + xpNeeded + " XP";
        }
        context.drawCenteredTextWithShadow(tr, Text.literal(xpText), width / 2, barY + 2, baseAlpha | 0xFFFFFF);

        if (currentLevel > oldLevel) {
            float lvPulse = (float) Math.sin(progress * 20) * 0.1f + 1.0f;
            context.getMatrices().push();
            context.getMatrices().translate(width / 2f, centerY + 55, 0);
            context.getMatrices().scale(lvPulse, lvPulse, 1);
            context.getMatrices().translate(-(width / 2f), -(centerY + 55), 0);
            context.drawCenteredTextWithShadow(tr, Text.literal("§d§lLEVEL UP!"), width / 2, centerY + 50, baseAlpha | 0xFF55FF);
            context.getMatrices().pop();

            // Unlocks - show all cumulative rewards
            List<Object> rewards = new ArrayList<>();
            for (int l = oldLevel + 1; l <= currentLevel; l++) {
                 // Perks
                 if (l == 5) rewards.add("SWIFT_FOOTING");
                 if (l == 10) rewards.add("TOUGH_SKIN");
                 if (l == 15) rewards.add("SCHOLAR");
                 if (l == 20) rewards.add("MASTER");
                 
                 // Challenges
                 for (int id = 1; id <= 25; id++) {
                     if (LevelManager.getRequiredLevel(id) == l) {
                         rewards.add(id);
                     }
                 }
            }
            
            if (!rewards.isEmpty()) {
                context.drawCenteredTextWithShadow(tr, Text.literal("§eRewards Unlocked:"), width / 2, centerY + 75, baseAlpha | 0xFFFF55);
                int itemsPerRow = 8;
                int totalRewards = rewards.size();
                
                for (int i = 0; i < totalRewards; i++) {
                    int row = i / itemsPerRow;
                    int col = i % itemsPerRow;
                    int rowSize = Math.min(itemsPerRow, totalRewards - row * itemsPerRow);
                    
                    int x = width / 2 - (rowSize * 24) / 2 + col * 24;
                    int y = centerY + 90 + row * 24;
                    
                    context.getMatrices().push();
                    float iconFloat = (float) Math.sin(progress * 10 + i) * 2;
                    context.getMatrices().translate(0, iconFloat, 0);
                    
                    Object reward = rewards.get(i);
                    if (reward instanceof Integer cid) {
                        ItemStack icon = ChallengeIconProvider.getIcon(cid);
                        context.drawItem(icon, x, y);
                    } else if (reward instanceof String s) {
                        // Draw Star for perks
                        context.drawCenteredTextWithShadow(tr, Text.literal("§e★"), x + 8, y + 4, baseAlpha | 0xFFFF55);
                    }
                    context.getMatrices().pop();
                }
            }
        }
    }
}
