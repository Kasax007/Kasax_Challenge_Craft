package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.data.StatsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    private final Anim.Tween hover = new Anim.Tween(0f);

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
        Font tr = Minecraft.getInstance().font;
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
            Component label = challengeId == LevelManager.PERK_INFINITY_WEAPON
                    ? Component.translatable("challengecraft.challenge_card.locked_stars_short", 20)
                    : Component.translatable("challengecraft.challenge_card.locked_level_short", requiredLevel);
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
            context.text(tr, Component.nullToEmpty(titleStr), textX, y + (h / 2) - 9, textColor, false);
            MutableComponent pbText = Component.translatable("challengecraft.challenge_card.completed_time", formatTicks(pbTicks));
            context.text(tr, CraftUI.trimToWidth(tr, pbText.getString(), maxTextWidth), textX, y + (h / 2) + 1, CraftUI.SUCCESS, false);
        } else {
            context.text(tr, Component.nullToEmpty(titleStr), textX, y + (h - tr.lineHeight) / 2, textColor, false);
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
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (locked) return;
        active = !active;
        if (onToggle != null) {
            onToggle.accept(active);
        }
    }

    // ---- vanilla interaction guards --------------------------------------------------------
    // This widget publishes isActive() as "is the challenge toggled on" — that is what both
    // selection screens read it for. 26.2 changed AbstractWidget to route clicking, hover testing
    // and focus navigation through isActive() instead of the raw active/visible fields (1.21.5
    // read the fields directly), so vanilla would now read every un-toggled card as a *disabled*
    // widget and make it impossible to switch a challenge ON. The three overrides below are
    // vanilla's own logic re-based on the widget's enabled state, super.isActive() == visible &&
    // active. Note handleCursor() needs no such treatment: AbstractWidget never calls it, only
    // AbstractButton does.

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isActive()
                && mouseX >= getX() && mouseY >= getY()
                && mouseX < getRight() && mouseY < getBottom();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!super.isActive()
                || !isValidClickButton(event.buttonInfo())
                || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        onClick(event, doubleClick);
        return true;
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!super.isActive()) {
            return null;
        }
        return isFocused() ? null : ComponentPath.leaf(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }

    /**
     * The challenge's toggle state — <em>not</em> the widget's enabled state. This deliberately
     * shadows {@link AbstractWidget#isActive()}; see the interaction guards above for why that
     * needs compensating on 26.2.
     */
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
