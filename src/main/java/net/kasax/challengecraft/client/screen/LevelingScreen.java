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
        for (int l = 1; l <= LevelManager.MAX_LEVEL; l++) {
            LevelEntryWidget entry = new LevelEntryWidget(panelX + 5, currentY, panelWidth - 25, l);
            scrollPanel.addChild(entry);
            currentY += entry.getHeight() + 5;
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
        private final boolean unlocked;
        private final List<Reward> rewards = new ArrayList<>();

        public LevelEntryWidget(int x, int y, int width, int level) {
            super(x, y, width, 0, Text.empty());
            this.level = level;
            long currentXp = ChallengeCraftClient.LOCAL_PLAYER_XP;
            this.unlocked = LevelManager.getLevelForXp(currentXp) >= level;

            // Determine rewards
            for (int perkId : LevelManager.ALL_PERKS) {
                if (LevelManager.getRequiredLevel(perkId) == level) {
                    Text name = Text.translatable("challengecraft.perk." + perkId);
                    Text desc = Text.translatable("challengecraft.perk." + perkId + ".desc");
                    ItemStack icon = ChallengeIconProvider.getIcon(perkId);
                    rewards.add(new Reward(Text.literal("§aPerk: ").append(name), desc, icon, true));
                }
            }

            for (int id = 1; id <= 25; id++) {
                if (LevelManager.getRequiredLevel(id) == level) {
                    Text name = Text.translatable("challengecraft.worldcreate.challenge" + id);
                    Text desc = Text.translatable("challengecraft.worldcreate.challenge" + id + ".desc");
                    ItemStack icon = ChallengeIconProvider.getIcon(id);
                    rewards.add(new Reward(name, desc, icon, false));
                }
            }
            
            // Calculate height
            int h = 20; // Level header
            for (Reward r : rewards) {
                h += r.isPerk ? 25 : 30; // space for icon and description
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
            context.drawText(tr, "Level " + level, getX() + 5, getY() + 5, unlocked ? 0xFFFFFF : 0xAAAAAA, true);
            
            if (unlocked) {
                context.drawText(tr, "§a✓ UNLOCKED", getX() + getWidth() - 65, getY() + 5, 0xFFFFFF, true);
            } else {
                long needed = LevelManager.getXpForLevel(level);
                context.drawText(tr, "§cLocked (" + needed + " XP)", getX() + getWidth() - 95, getY() + 5, 0xFFFFFF, true);
            }

            int ry = getY() + 20;
            if (rewards.isEmpty()) {
                context.drawText(tr, "§8No specific unlocks", getX() + 15, ry, 0x888888, false);
            } else {
                for (Reward reward : rewards) {
                    if (reward.isPerk) {
                        context.drawText(tr, "★ " + reward.name.getString(), getX() + 10, ry, 0xFFFF55, true);
                        ry += 10;
                        context.drawText(tr, "  §7" + reward.description.getString(), getX() + 10, ry, 0xAAAAAA, false);
                        ry += 15;
                    } else {
                        context.drawItem(reward.icon, getX() + 10, ry);
                        context.drawText(tr, reward.name, getX() + 30, ry, 0xFFFFFF, true);
                        ry += 10;
                        
                        String descStr = reward.description.getString();
                        if (tr.getWidth(descStr) > getWidth() - 40) {
                            descStr = tr.trimToWidth(descStr, getWidth() - 50) + "...";
                        }
                        context.drawText(tr, "§7" + descStr, getX() + 30, ry, 0xAAAAAA, false);
                        ry += 20;
                    }
                }
            }
        }

        private static class Reward {
            final Text name;
            final Text description;
            final ItemStack icon;
            final boolean isPerk;

            Reward(Text name, Text description, ItemStack icon, boolean isPerk) {
                this.name = name;
                this.description = description;
                this.icon = icon;
                this.isPerk = isPerk;
            }
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {}
    }
}
