package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.StatsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/** Toggle card shared by the create-world tab and the in-world challenge editor. */
public class ChallengeCardWidget extends AbstractWidget {
    private final int challengeId;
    private final ItemStack icon;
    private final Component title;
    private final Component description;
    private boolean active;
    private final Consumer<Boolean> onToggle;
    private final Integer pbTicks;
    private final boolean locked;
    private final int requiredLevel;

    public ChallengeCardWidget(int x, int y, int width, int height, int challengeId, boolean active, Consumer<Boolean> onToggle) {
        super(x, y, width, height, Component.empty());
        this.challengeId = challengeId;
        this.icon = ChallengeIconProvider.getIcon(challengeId);
        if (challengeId > 100) {
            this.title = Component.translatable("challengecraft.perk." + challengeId);
            this.description = Component.translatable("challengecraft.perk." + challengeId + ".desc");
        } else {
            this.title = Component.translatable("challengecraft.worldcreate.challenge" + challengeId);
            this.description = Component.translatable("challengecraft.worldcreate.challenge" + challengeId + ".desc");
        }
        this.active = active;
        this.onToggle = onToggle;
        
        String uuid = "global";
        if (Minecraft.getInstance().getUser() != null && Minecraft.getInstance().getUser().getProfileId() != null) {
            uuid = Minecraft.getInstance().getUser().getProfileId().toString();
        }
        this.pbTicks = StatsManager.getBestTimes(uuid).get(challengeId);
        
        long currentXp = ChallengeCraftClient.LOCAL_PLAYER_XP;
        int currentLevel = LevelManager.getLevelForXp(currentXp);
        this.requiredLevel = LevelManager.getRequiredLevel(challengeId);
        
        if (challengeId == LevelManager.PERK_INFINITY_WEAPON) {
            this.locked = LevelManager.getStars(currentXp) < 20;
        } else {
            this.locked = currentLevel < requiredLevel;
        }
        
        if (locked) {
            Component requirement = challengeId == LevelManager.PERK_INFINITY_WEAPON
                    ? Component.translatable("challengecraft.requirement.infinity_stars", 20)
                    : Component.translatable("challengecraft.requirement.level", requiredLevel);
            setTooltip(Tooltip.create(Component.translatable("challengecraft.challenge_card.locked", requirement).withStyle(ChatFormatting.RED).append(Component.nullToEmpty("\n")).append(description)));
        } else {
            setTooltip(Tooltip.create(description));
        }
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        // Parent screens own click feedback so one interaction does not play twice.
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int textColor = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        int bgColor = active ? 0x6000FF00 : 0x60000000;
        if (locked) {
            textColor = 0xFF555555;
            bgColor = 0x80222222;
        } else if (isHovered()) {
            bgColor = active ? 0x9000FF00 : 0x90555555;
        }

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        context.outline(getX(), getY(), getWidth(), getHeight(), isFocused() ? 0xFFFFFFFF : 0xFFAAAAAA);

        if (locked) {
            Component label = challengeId == LevelManager.PERK_INFINITY_WEAPON
                    ? Component.translatable("challengecraft.challenge_card.locked_stars_short", 20).withStyle(ChatFormatting.RED)
                    : Component.translatable("challengecraft.challenge_card.locked_level_short", requiredLevel).withStyle(ChatFormatting.RED);
            context.text(Minecraft.getInstance().font, label, getX() + 4, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF, true);
        } else {
            ChallengeIconProvider.drawIcon(context, getX() + 4, getY() + (getHeight() - 16) / 2, challengeId);
        }

        Font tr = Minecraft.getInstance().font;
        
        Component renderedTitle = title;
        int xOffset = locked ? 48 : 24;
        if (tr.width(title) > getWidth() - xOffset - 4) {
             String s = tr.plainSubstrByWidth(title.getString(), getWidth() - xOffset - 12) + "...";
             renderedTitle = Component.nullToEmpty(s);
        }

        if (pbTicks != null && !locked) {
            int titleY = getY() + (getHeight() / 2) - 9;
            context.text(tr, renderedTitle, getX() + xOffset, titleY, textColor, true);
            
            String timeStr = formatTicks(pbTicks);
            MutableComponent pbText = Component.translatable("challengecraft.challenge_card.completed_time", timeStr).withStyle(ChatFormatting.GREEN);
            
            int pbY = getY() + (getHeight() / 2) + 1;
            context.text(tr, pbText, getX() + xOffset, pbY, 0xFFFFFF, true);
        } else {
            int textY = getY() + (getHeight() - 8) / 2;
            context.text(tr, renderedTitle, getX() + xOffset, textY, textColor, true);
        }
    }

    private String formatTicks(int totalTicks) {
        long totalSeconds = totalTicks / 20L;
        long hrs = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (hrs > 0) {
            return String.format("%d:%02d:%02d", hrs, mins, secs);
        } else {
            return String.format("%02d:%02d", mins, secs);
        }
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (locked) return;
        active = !active;
        if (onToggle != null) {
            onToggle.accept(active);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }

    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }

    public int getChallengeId() {
        return challengeId;
    }
}
