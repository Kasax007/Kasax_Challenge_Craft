package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.StatsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

public class ChallengeCardWidget extends ClickableWidget {
    private final int challengeId;
    private final ItemStack icon;
    private final Text title;
    private final Text description;
    private boolean active;
    private final Consumer<Boolean> onToggle;
    private final Integer pbTicks;
    private final boolean locked;
    private final int requiredLevel;

    public ChallengeCardWidget(int x, int y, int width, int height, int challengeId, boolean active, Consumer<Boolean> onToggle) {
        super(x, y, width, height, Text.empty());
        this.challengeId = challengeId;
        this.icon = ChallengeIconProvider.getIcon(challengeId);
        if (challengeId > 100) {
            this.title = Text.translatable("challengecraft.perk." + challengeId);
            this.description = Text.translatable("challengecraft.perk." + challengeId + ".desc");
        } else {
            this.title = Text.translatable("challengecraft.worldcreate.challenge" + challengeId);
            this.description = Text.translatable("challengecraft.worldcreate.challenge" + challengeId + ".desc");
        }
        this.active = active;
        this.onToggle = onToggle;
        
        String uuid = "global";
        if (MinecraftClient.getInstance().getSession() != null && MinecraftClient.getInstance().getSession().getUuidOrNull() != null) {
            uuid = MinecraftClient.getInstance().getSession().getUuidOrNull().toString();
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
            Text requirement = challengeId == LevelManager.PERK_INFINITY_WEAPON
                    ? Text.translatable("challengecraft.requirement.infinity_stars", 20)
                    : Text.translatable("challengecraft.requirement.level", requiredLevel);
            setTooltip(Tooltip.of(Text.translatable("challengecraft.challenge_card.locked", requirement).formatted(Formatting.RED).append(Text.of("\n")).append(description)));
        } else {
            setTooltip(Tooltip.of(description));
        }
    }

    @Override
    public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
        // We do not play sound here to avoid double sound feedback reported by users.
        // The sound is handled by the screen/panel or we intentionally skip it for the card itself.
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int textColor = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        int bgColor = active ? 0x6000FF00 : 0x60000000;
        if (locked) {
            textColor = 0xFF555555;
            bgColor = 0x80222222;
        } else if (isHovered()) {
            bgColor = active ? 0x9000FF00 : 0x90555555;
        }

        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), isFocused() ? 0xFFFFFFFF : 0xFFAAAAAA);

        if (locked) {
            Text label = challengeId == LevelManager.PERK_INFINITY_WEAPON
                    ? Text.translatable("challengecraft.challenge_card.locked_stars_short", 20).formatted(Formatting.RED)
                    : Text.translatable("challengecraft.challenge_card.locked_level_short", requiredLevel).formatted(Formatting.RED);
            context.drawText(MinecraftClient.getInstance().textRenderer, label, getX() + 4, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF, true);
        } else {
            ChallengeIconProvider.drawIcon(context, getX() + 4, getY() + (getHeight() - 16) / 2, challengeId);
        }

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        
        // Truncate text if it's too long
        Text renderedTitle = title;
        int xOffset = locked ? 48 : 24;
        if (tr.getWidth(title) > getWidth() - xOffset - 4) {
             String s = tr.trimToWidth(title.getString(), getWidth() - xOffset - 12) + "...";
             renderedTitle = Text.of(s);
        }

        if (pbTicks != null && !locked) {
            int titleY = getY() + (getHeight() / 2) - 9;
            context.drawText(tr, renderedTitle, getX() + xOffset, titleY, textColor, true);
            
            String timeStr = formatTicks(pbTicks);
            MutableText pbText = Text.translatable("challengecraft.challenge_card.completed_time", timeStr).formatted(Formatting.GREEN);
            
            int pbY = getY() + (getHeight() / 2) + 1;
            context.drawText(tr, pbText, getX() + xOffset, pbY, 0xFFFFFF, true);
        } else {
            int textY = getY() + (getHeight() - 8) / 2;
            context.drawText(tr, renderedTitle, getX() + xOffset, textY, textColor, true);
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
    public void onClick(double mouseX, double mouseY) {
        if (locked) return;
        active = !active;
        if (onToggle != null) {
            onToggle.accept(active);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
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
