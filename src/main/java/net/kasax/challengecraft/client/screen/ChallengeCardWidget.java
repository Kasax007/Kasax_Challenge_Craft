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
    /**
     * A card the player may look at but not change.
     *
     * <p>Used by the daily challenge, whose ruleset is fixed. Deliberately separate both from
     * {@code active} (the card must still render as switched ON, it just must not respond to a
     * click) and from {@code locked}, which already means "you have not reached the level for this".
     * The two are different statements and a daily can show both at once.
     */
    private boolean ruleLocked = false;
    private final Consumer<Boolean> onToggle;
    private final Integer pbTicks;
    private final boolean locked;
    private final int requiredLevel;
    private final Anim.Tween hover = new Anim.Tween(0f);
    /** The tooltip without a conflict line, so the line can come and go. */
    private Component baseTooltip;
    /** "Does not combine with ...", or null. Set by {@code ChallengeBrowser.refreshConflicts}. */
    private Component conflictNote;

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
            baseTooltip = Component.translatable("challengecraft.challenge_card.locked", requirement).withStyle(ChatFormatting.RED).append(Component.nullToEmpty("\n")).append(description);
        } else {
            baseTooltip = description;
        }
        setTooltip(Tooltip.create(baseTooltip));
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        // Parent screens own click feedback so one interaction does not play twice.
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        // A locked challenge can still be ON when a daily or a challenge code switched it on —
        // both waive the level lock. It must then read as switched on, not as unavailable.
        boolean shownLocked = locked && !active;
        CraftUI.CardState state = shownLocked ? CraftUI.CardState.LOCKED
                : (active ? CraftUI.CardState.ACTIVE : CraftUI.CardState.IDLE);

        float g = hover.approach(!shownLocked && isHovered() ? 1f : 0f, 12f);
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
        boolean clash = conflictNote != null && !shownLocked;
        if (clash) {
            border = CraftUI.DANGER;
        }
        CraftUI.panel(context, x, y, w, h, fill, border, clash ? CraftUI.DANGER : state.accent);
        if (clash) {
            // A small "!" in the corner: this card collides with something that is switched on.
            context.fill(x + w - 9, y + 2, x + w - 2, y + 10, CraftUI.applyAlpha(CraftUI.DANGER, 0.9f));
            context.text(tr, "!", x + w - 7, y + 2, 0xFF12161F, false);
        }

        int textX;
        if (shownLocked) {
            Component label = challengeId == LevelManager.PERK_INFINITY_WEAPON
                    ? Component.translatable("challengecraft.challenge_card.locked_stars_short", 20)
                    : Component.translatable("challengecraft.challenge_card.locked_level_short", requiredLevel);
            int chipW = CraftUI.labelChip(context, tr, label, x + 4, y + (h - 12) / 2, CraftUI.DANGER);
            textX = x + 4 + chipW + 4;
        } else {
            CraftUI.iconTileItem(context, icon, x + 3, y + (h - 18) / 2, 18, state.accent);
            textX = x + 25;
        }

        int textColor = shownLocked ? CraftUI.TEXT_MUTED : (active ? CraftUI.TEXT_PRIMARY : CraftUI.TEXT_SECONDARY);
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
        // Switching a locked challenge OFF is always allowed (a code may have turned it on);
        // only switching it on needs the level.
        if (locked && !active) return;
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
        if (ruleLocked) return false;
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
    public void setRuleLocked(boolean ruleLocked) {
        this.ruleLocked = ruleLocked;
    }

    public boolean isRuleLocked() {
        return ruleLocked;
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

    /** Whether the player's level (or stars) does not unlock this yet. */
    public boolean isLevelLocked() {
        return locked;
    }

    public Component getTitle() {
        return title;
    }

    public void setConflictNote(Component note) {
        if (java.util.Objects.equals(note, conflictNote)) {
            return;
        }
        conflictNote = note;
        setTooltip(Tooltip.create(note == null ? baseTooltip
                : note.copy().withStyle(ChatFormatting.RED).append(Component.nullToEmpty("\n")).append(baseTooltip)));
    }
}
