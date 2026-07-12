package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
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

/** Toggle card shared by the create-world tab and the in-world challenge editor. */
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
    private final Anim.Tween hover = new Anim.Tween(0f);

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
        // Parent screens own click feedback so one interaction does not play twice.
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        CraftUI.CardState state = locked ? CraftUI.CardState.LOCKED
                : (active ? CraftUI.CardState.ACTIVE : CraftUI.CardState.IDLE);

        float g = hover.approach(!locked && isHovered() ? 1f : 0f, 12f);
        int lift = Math.round(g * 1.5f);
        int x = getX();
        int y = getY() - lift;
        int w = getWidth();
        int h = getHeight();

        if (g > 0.01f) {
            context.fill(x - 1, y - 1, x + w + 1, y + h + 1, CraftUI.applyAlpha(state.accent, 0.22f * g));
        }

        int fill = CraftUI.mix(state.fill, 0xCC243449, g * 0.45f);
        int border = isFocused() ? CraftUI.TEXT_PRIMARY : CraftUI.mix(state.border, state.accent, g);
        CraftUI.panel(context, x, y, w, h, fill, border, state.accent);

        int textX;
        if (locked) {
            Text label = challengeId == LevelManager.PERK_INFINITY_WEAPON
                    ? Text.translatable("challengecraft.challenge_card.locked_stars_short", 20)
                    : Text.translatable("challengecraft.challenge_card.locked_level_short", requiredLevel);
            int chipW = CraftUI.labelChip(context, tr, label, x + 4, y + (h - 12) / 2, CraftUI.DANGER);
            textX = x + 4 + chipW + 4;
        } else {
            CraftUI.iconTileItem(context, icon, x + 3, y + (h - 18) / 2, 18, state.accent);
            textX = x + 25;
        }

        int textColor = locked ? CraftUI.TEXT_MUTED : (active ? CraftUI.TEXT_PRIMARY : CraftUI.TEXT_SECONDARY);
        int maxTextWidth = w - (textX - x) - 6;
        String titleStr = CraftUI.trimToWidth(tr, title.getString(), maxTextWidth);

        if (pbTicks != null && !locked) {
            context.drawText(tr, Text.of(titleStr), textX, y + (h / 2) - 9, textColor, false);
            MutableText pbText = Text.translatable("challengecraft.challenge_card.completed_time", formatTicks(pbTicks));
            context.drawText(tr, CraftUI.trimToWidth(tr, pbText.getString(), maxTextWidth), textX, y + (h / 2) + 1, CraftUI.SUCCESS, false);
        } else {
            context.drawText(tr, Text.of(titleStr), textX, y + (h - tr.fontHeight) / 2, textColor, false);
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
