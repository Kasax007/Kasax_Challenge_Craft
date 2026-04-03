package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LevelingScreen extends Screen {
    private final Screen parent;
    private WidgetScrollPanel scrollPanel;

    public LevelingScreen(Screen parent) {
        super(Text.literal("Leveling & Rewards"));
        this.parent = parent;
        
        // Ensure XP is synced from local storage if in Main Menu
        if (MinecraftClient.getInstance().player == null) {
            UUID uuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
            if (uuid != null) {
                ChallengeCraftClient.LOCAL_PLAYER_XP = XpManager.getXp(uuid);
            } else {
                ChallengeCraftClient.LOCAL_PLAYER_XP = XpManager.getTotalXp();
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = 300;
        int panelHeight = height - 100;
        int panelX = (width - panelWidth) / 2;
        int panelY = 70;

        scrollPanel = new WidgetScrollPanel(panelX, panelY, panelWidth, panelHeight, Text.empty());
        addDrawableChild(scrollPanel);

        // Populate levels
        int currentY = panelY + 5;
        long xp = ChallengeCraftClient.LOCAL_PLAYER_XP;
        int level = LevelManager.getLevelForXp(xp);

        // Show all levels up to MAX_LEVEL
        for (int l = 1; l <= LevelManager.MAX_LEVEL; l++) {
            LevelEntryWidget entry = new LevelEntryWidget(panelX + 5, currentY, panelWidth - 25, l, false);
            scrollPanel.addChild(entry);
            currentY += entry.getHeight() + 5;
        }

        // Only show infinity rewards if level 20 is reached
        if (level >= LevelManager.MAX_LEVEL) {
            // Show only infinity stars that have a reward, up to at least the player's current star count + next one
            int currentStars = LevelManager.getStars(xp);
            int maxStarToShow = currentStars + 1;

            for (int s = 1; s <= maxStarToShow; s++) {
                if (LevelManager.getStarReward(s) != null) {
                    // Hide star 20 until actually reached
                    if (s == 20 && currentStars < 20) continue;
                    
                    LevelEntryWidget entry = new LevelEntryWidget(panelX + 5, currentY, panelWidth - 25, s, true);
                    scrollPanel.addChild(entry);
                    currentY += entry.getHeight() + 5;
                }
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> client.setScreen(parent))
                .dimensions(width / 2 - 50, height - 25, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);

        // XP Bar
        long xp = ChallengeCraftClient.LOCAL_PLAYER_XP;
        int level = LevelManager.getLevelForXp(xp);
        long currentLevelXp = LevelManager.getXpForLevel(level);
        long nextLevelXp = LevelManager.getXpForLevel(level + 1);
        long progressXp = xp - currentLevelXp;
        long neededXp = nextLevelXp - currentLevelXp;
        float progress = level >= LevelManager.MAX_LEVEL ? 1.0f : (float) progressXp / neededXp;

        int barW = 200;
        int barH = 12;
        int barX = (width - barW) / 2;
        int barY = 30;

        context.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF000000);
        context.fill(barX, barY, barX + (int)(barW * progress), barY + barH, 0xFF00AA00);
        context.drawBorder(barX - 1, barY - 1, barW + 2, barH + 2, 0xFFAAAAAA);

        int stars = LevelManager.getStars(xp);
        String xpText;
        if (level >= LevelManager.MAX_LEVEL) {
            long maxXp = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL);
            long starProgress = (xp - maxXp) % 1000;
            xpText = "Infinity Star: " + starProgress + " / 1000 XP";
            progress = (float) starProgress / 1000;
        } else {
            xpText = progressXp + " / " + neededXp + " XP";
        }

        context.drawCenteredTextWithShadow(textRenderer, "Level " + level + (stars > 0 ? " §e(★" + stars + ")" : ""), width / 2, barY - 12, 0x00AAFF);
        context.drawCenteredTextWithShadow(textRenderer, xpText, width / 2, barY + 2, 0xFFFFFF);
        
        if (level < LevelManager.MAX_LEVEL) {
             context.drawCenteredTextWithShadow(textRenderer, "Next Level: " + (level + 1), width / 2, barY + barH + 4, 0xAAAAAA);
        } else {
             context.drawCenteredTextWithShadow(textRenderer, "§6§lINFINITY ROADMAP", width / 2, barY + barH + 4, 0xFFFFFF);
        }
    }

    private static class LevelEntryWidget extends net.minecraft.client.gui.widget.ClickableWidget {
        private final int level;
        private final boolean isStar;
        private final boolean unlocked;
        private final List<Reward> rewards = new ArrayList<>();

        public LevelEntryWidget(int x, int y, int width, int val, boolean isStar) {
            super(x, y, width, 0, Text.empty());
            this.level = val;
            this.isStar = isStar;
            long currentXp = ChallengeCraftClient.LOCAL_PLAYER_XP;
            
            if (!isStar) {
                this.unlocked = LevelManager.getLevelForXp(currentXp) >= level;
            } else {
                this.unlocked = LevelManager.getStars(currentXp) >= level;
            }

            // Determine rewards
            if (!isStar) {
                for (int perkId : LevelManager.ALL_PERKS) {
                    if (LevelManager.getRequiredLevel(perkId) == level) {
                        Text name = Text.translatable("challengecraft.perk." + perkId);
                        Text desc = Text.translatable("challengecraft.perk." + perkId + ".desc");
                        ItemStack icon = ChallengeIconProvider.getIcon(perkId);
                        rewards.add(new Reward(Text.literal("§aPerk: ").append(name), desc, icon, true, perkId));
                    }
                }

                for (int id = 1; id <= 37; id++) {
                    if (LevelManager.getRequiredLevel(id) == level) {
                        Text name = Text.translatable("challengecraft.worldcreate.challenge" + id);
                        Text desc = Text.translatable("challengecraft.worldcreate.challenge" + id + ".desc");
                        ItemStack icon = ChallengeIconProvider.getIcon(id);
                        rewards.add(new Reward(name, desc, icon, false, id));
                    }
                }
            } else {
                // Infinity Star rewards
                String rewardId = LevelManager.getStarReward(level);
                if (rewardId != null) {
                    if (rewardId.startsWith("perk_")) {
                        int perkId = LevelManager.PERK_INFINITY_WEAPON;
                        Text name = Text.translatable("challengecraft.perk." + perkId);
                        Text desc = Text.translatable("challengecraft.perk." + perkId + ".desc");
                        ItemStack icon = ChallengeIconProvider.getIcon(perkId);
                        rewards.add(new Reward(Text.literal("§dSECRET: ").append(name), desc, icon, true, perkId));
                    } else {
                        String colorCode = switch (rewardId) {
                            case "green" -> "§aGreen";
                            case "blue" -> "§9Blue";
                            case "red" -> "§cRed";
                            case "purple" -> "§5Purple";
                            case "gold" -> "§6Gold";
                            case "rainbow" -> "§b§lR§a§la§e§li§c§ln§d§lb§9§lo§b§lw";
                            default -> rewardId;
                        };
                        rewards.add(new Reward(Text.literal("§e" + colorCode + " Name Color"), 
                                Text.literal("Your name will be displayed in " + rewardId + "."), ItemStack.EMPTY, true, -1));
                    }
                }
            }
            
            // Calculate height
            int h = 20; // Level header
            for (Reward r : rewards) {
                h += 30; // standardized space for icon and description
            }
            if (rewards.isEmpty()) h += 15;
            this.height = Math.max(40, h);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int bgColor = unlocked ? 0x8000AA00 : 0x80555555;
            if (isHovered()) bgColor = unlocked ? 0xA000FF00 : 0xA0777777;

            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFFAAAAAA);

            var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            String label = isStar ? "Infinity Star " + level : "Level " + level;
            context.drawText(tr, label, getX() + 5, getY() + 5, unlocked ? 0xFFFFFF : 0xAAAAAA, true);
            
            if (unlocked) {
                context.drawText(tr, "§a✓ UNLOCKED", getX() + getWidth() - 65, getY() + 5, 0xFFFFFF, true);
            } else {
                long needed;
                if (!isStar) {
                    needed = LevelManager.getXpForLevel(level);
                } else {
                    needed = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL) + (long) level * 1000;
                }
                context.drawText(tr, "§cLocked (" + needed + " XP)", getX() + getWidth() - 95, getY() + 5, 0xFFFFFF, true);
            }

            int ry = getY() + 20;
            if (rewards.isEmpty()) {
                context.drawText(tr, "§8No specific unlocks", getX() + 15, ry, 0x888888, false);
            } else {
                for (Reward reward : rewards) {
                    int textX = getX() + 30;
                    if (!reward.icon.isEmpty()) {
                        ChallengeIconProvider.drawIcon(context, getX() + 10, ry, reward.id);
                    } else if (reward.isPerk) {
                        context.drawText(tr, "★", getX() + 12, ry, 0xFFFF55, true);
                    } else {
                        textX = getX() + 10;
                    }

                    context.drawText(tr, reward.name, textX, ry, reward.isPerk ? 0xFFFF55 : 0xFFFFFF, true);
                    ry += 10;
                    
                    String descStr = reward.description.getString();
                    int maxW = getWidth() - (textX - getX()) - 10;
                    if (tr.getWidth(descStr) > maxW) {
                        descStr = tr.trimToWidth(descStr, maxW - 10) + "...";
                    }
                    context.drawText(tr, "§7" + descStr, textX, ry, 0xAAAAAA, false);
                    ry += 20;
                }
            }
        }

        private static class Reward {
            final Text name;
            final Text description;
            final ItemStack icon;
            final boolean isPerk;
            final int id;

            Reward(Text name, Text description, ItemStack icon, boolean isPerk, int id) {
                this.name = name;
                this.description = description;
                this.icon = icon;
                this.isPerk = isPerk;
                this.id = id;
            }
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {}
    }
}
