package net.kasax.challengecraft.client.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.DailyClientState;
import net.kasax.challengecraft.client.screen.ChallengeIconProvider;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.daily.DailyChallenges;
import net.kasax.challengecraft.daily.DailyEntry;
import net.kasax.challengecraft.daily.DailyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * The daily challenge, sitting on the title screen next to the button column.
 *
 * <p>This is how a player finds out dailies exist at all. Behind a command or a submenu nobody would
 * ever discover it; on the main menu it is the second thing the eye lands on after the buttons, and
 * it changes every day, which is the whole point.
 *
 * <p>Clicking it arms {@link DailyClientState} and opens ordinary world creation. Nothing is
 * bypassed: the player still names the world, picks their difficulty and presses Create like always.
 * The Challenges tab simply arrives pre-filled and locked.
 */
@Environment(EnvType.CLIENT)
public class DailyCardWidget extends AbstractWidget {
    public static final int WIDTH = 150;
    public static final int HEIGHT = 92;
    private static final int PAD = 8;
    private static final int ICON = 16;
    /** Sentinels for {@link #hoveredIcon}; anything >= 0 is an index into the ruleset. */
    private static final int NOTHING = -1;
    private static final int OVERFLOW = -2;
    private static final int UNKNOWN = -3;

    private final int dayIndex;
    private final DailyEntry entry;
    private final int bestTicks;
    /** What a click does. Opening world creation on the title screen, toggling in a selection screen. */
    private final Runnable onPress;
    /** Whether this card reads as chosen. Only meaningful where it is a selection. */
    private java.util.function.BooleanSupplier selected = () -> false;
    private java.util.function.BooleanSupplier enabled = () -> true;
    private int cardWidth = WIDTH;
    /** Which icon the cursor was on last frame, so the tooltip is only rebuilt when it moves. */
    private int hoveredIcon = UNKNOWN;

    public DailyCardWidget(int x, int y) {
        this(x, y, WIDTH, DailyCardWidget::open);
    }

    public DailyCardWidget(int x, int y, int width, Runnable onPress) {
        super(x, y, width, HEIGHT, Component.translatable("challengecraft.daily.card.label"));
        this.cardWidth = width;
        this.onPress = onPress;
        this.dayIndex = DailyManager.todayIndex();
        this.entry = DailyChallenges.get(dayIndex);
        var uuid = Minecraft.getInstance().getUser().getProfileId();
        this.bestTicks = uuid == null ? -1 : DailyManager.bestTicks(uuid, dayIndex);
    }

    /** Marks the card as the current choice — a gold frame instead of the neutral one. */
    public DailyCardWidget withSelected(java.util.function.BooleanSupplier selected) {
        this.selected = selected;
        return this;
    }

    /** Greys the card out when something else rules it out, e.g. challenges are ticked below. */
    public DailyCardWidget withEnabled(java.util.function.BooleanSupplier enabled) {
        this.enabled = enabled;
        return this;
    }

    public DailyEntry entry() {
        return entry;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered();
        boolean isSelected = selected.getAsBoolean();
        boolean isEnabled = enabled.getAsBoolean();
        int accent = isSelected ? CraftUI.GOLD
                : !isEnabled ? CraftUI.darken(CraftUI.BORDER, 0.6f)
                : hovered ? CraftUI.GOLD : CraftUI.BORDER;

        CraftUI.panelFloat(context, getX(), getY(), cardWidth, HEIGHT, accent);

        int x = getX() + PAD;
        int y = getY() + PAD;

        String date = LocalDate.ofEpochDay(DailyManager.epochDayUtc())
                .format(DateTimeFormatter.ofPattern("dd.MM."));
        Component label = Component.translatable("challengecraft.daily.card.label");
        context.text(mc.font, label.getString(), x, y, CraftUI.GOLD, false);
        context.text(mc.font, date, getX() + cardWidth - PAD - mc.font.width(date), y,
                CraftUI.TEXT_MUTED, false);
        y += mc.font.lineHeight + 3;

        context.fill(x, y, getX() + cardWidth - PAD, y + 1, CraftUI.applyAlpha(CraftUI.GOLD, 0.30f));
        y += 5;

        String name = CraftUI.trimToWidth(mc.font, Component.translatable(entry.nameKey()).getString(),
                cardWidth - 2 * PAD);
        context.text(mc.font, name, x, y, CraftUI.TEXT_PRIMARY, false);
        y += mc.font.lineHeight + 4;

        // The ruleset as icons: at this size the names would not fit and would not be read anyway,
        // while a row of icons says "this is a heavy one" at a glance. How many fit depends on the
        // card — the narrow one on the title screen truncates, the wide one in the selection screen
        // shows the lot. Hovering any of them names it, which is the only way to find out what you
        // are signing up for before you create the world.
        int total = entry.challengeIds().size();
        int room = Math.max(1, (cardWidth - 2 * PAD + 2) / (ICON + 2));
        int shown = total <= room
                ? total
                : Math.max(1, (cardWidth - 2 * PAD - 18) / (ICON + 2));
        int overflow = total - shown;
        int iconY = y;
        int ix = x;
        for (int i = 0; i < shown; i++) {
            ItemStack icon = ChallengeIconProvider.getIcon(entry.challengeIds().get(i));
            CraftUI.iconTileItem(context, icon, ix, iconY, ICON, CraftUI.BORDER);
            ix += ICON + 2;
        }
        if (overflow > 0) {
            String more = "+" + overflow;
            context.text(mc.font, more, ix + 2, iconY + 5, CraftUI.TEXT_MUTED, false);
        }
        updateIconTooltip(mc, mouseX, mouseY, x, iconY, shown, overflow, ix);
        y += ICON + 5;

        Component best = bestTicks < 0
                ? Component.translatable("challengecraft.daily.card.unplayed")
                : Component.translatable("challengecraft.daily.info.best",
                        net.kasax.challengecraft.util.ChallengeTimeUtil.formatTicks(bestTicks));
        String bs = CraftUI.trimToWidth(mc.font, best.getString(), cardWidth - 2 * PAD);
        context.text(mc.font, bs, x, y, bestTicks < 0 ? CraftUI.TEXT_MUTED : CraftUI.SUCCESS, false);

        Component cta = Component.translatable(isSelected
                ? "challengecraft.daily.card.selected"
                : "challengecraft.daily.card.play");
        String cs = cta.getString();
        context.text(mc.font, cs, getX() + (cardWidth - mc.font.width(cs)) / 2,
                getY() + HEIGHT - PAD - mc.font.lineHeight + 2,
                isSelected || hovered ? CraftUI.GOLD : CraftUI.TEXT_SECONDARY, false);
    }

    /**
     * Names whatever icon the cursor is on.
     *
     * <p>Recomputed every frame but only pushed when the answer changes — a fresh {@code Tooltip}
     * per frame would be pointless allocation.
     */
    private void updateIconTooltip(Minecraft mc, int mouseX, int mouseY,
                                   int rowX, int rowY, int shown, int overflow, int afterX) {
        int hit = NOTHING;
        if (mouseY >= rowY && mouseY < rowY + ICON) {
            for (int i = 0; i < shown; i++) {
                int bx = rowX + i * (ICON + 2);
                if (mouseX >= bx && mouseX < bx + ICON) {
                    hit = i;
                    break;
                }
            }
            if (hit == NOTHING && overflow > 0
                    && mouseX >= afterX && mouseX < afterX + mc.font.width("+" + overflow) + 4) {
                hit = OVERFLOW;
            }
        }
        if (hit == hoveredIcon) {
            return;
        }
        hoveredIcon = hit;
        if (hit >= 0) {
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    describe(entry.challengeIds().get(hit))));
        } else if (hit == OVERFLOW) {
            var text = Component.empty();
            for (int i = shown; i < entry.challengeIds().size(); i++) {
                if (i > shown) {
                    text.append(Component.literal("\n"));
                }
                text.append(name(entry.challengeIds().get(i))
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(text));
        } else {
            // Not nothing: the daily's own one-liner. It is the only place that text is shown, and
            // it answers "what is this run about" before the icons answer "out of what".
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable(entry.nameKey() + ".desc")));
        }
    }

    private static net.minecraft.network.chat.MutableComponent name(int id) {
        return Component.translatable("challengecraft.worldcreate.challenge" + id);
    }

    private static Component describe(int id) {
        return name(id).withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.literal("\n"))
                .append(Component.translatable("challengecraft.worldcreate.challenge" + id + ".desc")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible || !enabled.getAsBoolean()
                || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
        return true;
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        DailyClientState.arm();
        // Ordinary world creation from here on. The daily only pre-fills and locks the Challenges
        // tab and the seed; naming the world, difficulty and everything else stays the player's.
        CreateWorldScreen.openFresh(mc, () -> {
            DailyClientState.clear();
            mc.setScreenAndShow(new net.minecraft.client.gui.screens.TitleScreen());
        });
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                Component.translatable("challengecraft.daily.card.label"));
    }

    @Override
    public boolean isHovered() {
        return super.isHovered();
    }
}
