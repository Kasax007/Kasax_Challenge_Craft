package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.network.RunSummaryPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The card a player sees — and shares — when a run ends.
 *
 * <p>This is a poster, not a menu. It is meant to be screenshotted and posted, so it is laid out for
 * a thumbnail: one number dominates, everything else supports it. The active challenges appear as
 * their item icons rather than a list of names, because a row of icons tells you how brutal a run
 * was at a glance and a list of eleven names tells you nothing at that size.
 *
 * <p>Drawn in fixed design coordinates inside a scaled matrix, the same technique
 * {@code LockoutBingoBoardScreen} uses: the card is always {@link #CARD_W}x{@link #CARD_H} in its
 * own space and only shrinks when the window cannot fit it. That keeps the layout identical at
 * every GUI scale — which matters here more than usual, because the saved image should not depend
 * on the player's video settings.
 */
@Environment(EnvType.CLIENT)
public class RunSummaryScreen extends Screen {
    public static final int CARD_W = 340;
    public static final int CARD_H = 192;

    private static final int PAD = 12;
    private static final int ICON = 18;
    private static final int MAX_ICONS_PER_ROW = 13;
    private static final int MAX_ICON_ROWS = 2;

    private final RunSummaryPacket summary;
    private float scale = 1f;
    private int originX;
    private int originY;

    private java.nio.file.Path savedFile;
    private Component status = Component.empty();
    private net.minecraft.client.gui.components.Button folderButton;
    private net.minecraft.client.gui.components.Button redditButton;
    private net.minecraft.client.gui.components.Button xButton;

    public RunSummaryScreen(RunSummaryPacket summary) {
        super(Component.translatable("challengecraft.summary.title"));
        this.summary = summary;
    }

    /** The card's rectangle in real screen pixels, for cropping a screenshot down to just the card. */
    public int[] cardBoundsOnScreen() {
        return new int[]{originX, originY, Math.round(CARD_W * scale), Math.round(CARD_H * scale)};
    }

    @Override
    protected void init() {
        // Reserve a strip under the card for real widgets: they render outside the scaled matrix,
        // so they have to live in unscaled screen space.
        int usableH = Math.max(1, this.height - 34);
        this.scale = Math.min(1f, Math.min(this.width / (float) CARD_W, usableH / (float) CARD_H));
        int w = Math.round(CARD_W * scale);
        int h = Math.round(CARD_H * scale);
        this.originX = (this.width - w) / 2;
        this.originY = Math.max(4, (usableH - h) / 2);

        buildButtons();
    }

    /**
     * The share row.
     *
     * <p>Everything except "save" and "close" stays disabled until a file exists, because a share
     * link cannot carry the image: Reddit and X accept a prefilled title over URL, never an upload.
     * The player attaches the PNG themselves, so offering the link before the file is on disk would
     * hand them a form with nothing to attach.
     */
    private void buildButtons() {
        int y = this.height - 26;
        int bw = 74;
        int gap = 4;
        int total = bw * 5 + gap * 4;
        int x = (this.width - total) / 2;

        addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.translatable("challengecraft.summary.button.save"), b -> saveImage())
                .bounds(x, y, bw, 20).build());
        x += bw + gap;

        folderButton = addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.translatable("challengecraft.summary.button.folder"), b -> openFolder())
                .bounds(x, y, bw, 20).build());
        x += bw + gap;

        redditButton = addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.nullToEmpty("Reddit"), b -> openShare(
                        "https://www.reddit.com/submit?title=" + encoded()))
                .bounds(x, y, bw, 20).build());
        x += bw + gap;

        xButton = addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.nullToEmpty("X"), b -> openShare(
                        "https://twitter.com/intent/tweet?text=" + encoded()))
                .bounds(x, y, bw, 20).build());
        x += bw + gap;

        addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.translatable("challengecraft.summary.button.close"), b -> onClose())
                .bounds(x, y, bw, 20).build());

        updateButtonState();
    }

    private void updateButtonState() {
        boolean has = savedFile != null;
        if (folderButton != null) folderButton.active = has;
        // The share buttons carry the paste instruction themselves. The status line already says it
        // once, but that is read at save time and forgotten by the time the browser is open — the
        // reminder has to sit on the button that opens it.
        net.minecraft.client.gui.components.Tooltip paste = net.minecraft.client.gui.components.Tooltip
                .create(Component.translatable("challengecraft.summary.paste_hint"));
        if (redditButton != null) {
            redditButton.active = has;
            redditButton.setTooltip(has ? paste : null);
        }
        if (xButton != null) {
            xButton.active = has;
            xButton.setTooltip(has ? paste : null);
        }
    }

    private void saveImage() {
        RunSummaryImage.capture(cardBoundsOnScreen(), this.width, path -> {
            this.savedFile = path;
            if (path == null) {
                this.status = Component.translatable("challengecraft.summary.save_failed");
            } else {
                // Copying happens as part of saving rather than behind its own button: the share
                // links cannot carry the image, so the clipboard is the actual delivery mechanism
                // and hiding it behind a second click would just mean nobody finds it.
                //
                // Reported in two steps because the copy can take a moment. Saying "saved" right
                // away and upgrading to "saved and copied" beats a frozen screen.
                this.status = Component.translatable("challengecraft.summary.saved",
                        path.getFileName().toString());
                RunSummaryImage.copyToClipboard(path, copied -> {
                    if (this.savedFile == path) {
                        this.status = Component.translatable(
                                copied ? "challengecraft.summary.saved_copied"
                                        : "challengecraft.summary.saved",
                                path.getFileName().toString());
                        updateButtonState();
                    }
                });
            }
            updateButtonState();
        });
    }

    private void openFolder() {
        java.nio.file.Path dir = savedFile != null ? savedFile.getParent() : RunSummaryImage.folder();
        net.minecraft.util.Util.getPlatform().openPath(dir);
    }

    /** Always through the vanilla confirmation dialog — the mod never opens a browser unasked. */
    private void openShare(String url) {
        net.minecraft.client.gui.screens.ConfirmLinkScreen
                .confirmLinkNow(this, java.net.URI.create(url), false);
    }

    /**
     * Where a reader of the post can get the thing.
     *
     * <p>Part of the share text rather than a separate field, because both platforms turn a bare URL
     * in the body into a link and neither offers anywhere else to put one.
     */
    private static final String MODPACK_URL =
            "https://www.curseforge.com/minecraft/modpacks/challenge-craft/";

    /**
     * The prefilled post text. The image is never uploaded — the player attaches it.
     *
     * <p>Written as a challenge rather than a report: "beat this if you can" invites a reply, "run
     * finished" does not. The link closes it so anyone who takes the bait can act on it immediately.
     */
    private String shareText() {
        Component text;
        if (summary.daily) {
            text = Component.translatable("challengecraft.summary.share.daily",
                    dailyDateText(), formatTime(summary.runTicks), MODPACK_URL);
        } else if (summary.kind.isTimed()) {
            text = Component.translatable("challengecraft.summary.share.timed",
                    formatTime(summary.runTicks), String.valueOf(summary.activeIds.size()), MODPACK_URL);
        } else {
            text = Component.translatable("challengecraft.summary.share.competitive",
                    String.valueOf(summary.placement), String.valueOf(summary.participants), MODPACK_URL);
        }
        return text.getString() + " " + HASHTAGS;
    }

    /**
     * Appended in code rather than baked into the six translated strings: a hashtag is a handle, not
     * prose, and translating it would split the feed in two. Kept on one line because the Reddit
     * link fills the post TITLE, where a newline has nowhere to go.
     */
    private static final String HASHTAGS = "#ChallengeCraft #Minecraft";

    private String encoded() {
        return java.net.URLEncoder.encode(shareText(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        // 26.2's GUI matrix is a 2D affine stack — no z argument, and push/pop are named
        // pushMatrix/popMatrix. The try/finally matters: an exception mid-draw would otherwise
        // leave the whole GUI scaled for every screen after this one.
        org.joml.Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(originX, originY);
        matrices.scale(scale, scale);
        try {
            drawCard(context);
        } finally {
            matrices.popMatrix();
        }

        // Unscaled strip under the card: status line and the promise that nothing leaves the machine
        // on its own. Real widgets render outside the matrix anyway, so this belongs here too.
        Component note = status.getString().isEmpty()
                ? Component.translatable("challengecraft.summary.privacy")
                : status;
        String s = CraftUI.trimToWidth(this.font, note.getString(), this.width - 20);
        context.text(this.font, s, (this.width - this.font.width(s)) / 2, this.height - 40,
                savedFile != null ? CraftUI.SUCCESS : CraftUI.TEXT_MUTED, false);
    }

    /** Draws the whole card at (0,0) in design coordinates. */
    private void drawCard(GuiGraphicsExtractor context) {
        CraftUI.frame(context, 0, 0, CARD_W, CARD_H);

        int y = PAD;
        if (summary.daily) {
            y = drawDailyBanner(context);
        }
        y = drawPlayerRow(context, y);
        y = drawHeadline(context, y);
        y = drawBigNumber(context, y);
        y = drawChallengeIcons(context, y);
        drawChips(context, y);
        drawFooter(context);
    }

    /**
     * A gold band across the top, only for dailies.
     *
     * <p>A different sentence somewhere in the middle is not enough — the card is read as a picture,
     * usually as a thumbnail, and the first thing the eye takes in has to say what kind of run this
     * was. So the daily gets its own header: full-width gold, dark lettering, name and date. Nobody
     * has to read a word to see that this card is a different thing from an ordinary run.
     */
    private int drawDailyBanner(GuiGraphicsExtractor context) {
        int h = 18;
        context.fill(0, 0, CARD_W, h, CraftUI.GOLD);
        context.fill(0, 1, CARD_W, h - 1, 0xFFF2CE86);

        String left = Component.translatable("challengecraft.daily.card.label").getString();
        context.text(this.font, left, PAD, 6, 0xFF12161F, false);

        String name = summary.dailyIndex >= 0
                ? Component.translatable(
                        net.kasax.challengecraft.daily.DailyChallenges.get(summary.dailyIndex).nameKey())
                        .getString()
                : "";
        String right = name.isEmpty() ? dailyDateText() : name + "  ·  " + dailyDateText();
        right = CraftUI.trimToWidth(this.font, right, CARD_W - 2 * PAD - this.font.width(left) - 8);
        context.text(this.font, right, CARD_W - PAD - this.font.width(right), 6, 0xFF12161F, false);
        return h + CraftUI.S.SM;
    }

    /** Head icon, name, level and stars — the line that says whose run this was. */
    private int drawPlayerRow(GuiGraphicsExtractor context, int y) {
        int x = PAD;
        if (this.minecraft != null && this.minecraft.player != null) {
            // 26.2 renamed PlayerFaceRenderer to PlayerFaceExtractor and moved it behind the
            // extract/render split; older tutorials will not compile against this.
            PlayerFaceExtractor.extractRenderState(context, this.minecraft.player.getSkin(), x, y, 16);
            x += 16 + CraftUI.S.SM;
            context.text(this.font, this.minecraft.player.getName().getString(),
                    x, y + 4, CraftUI.TEXT_PRIMARY, false);
        }

        long xp = summary.newXp;
        int level = LevelManager.getLevelForXp(xp);
        int stars = LevelManager.getStars(xp);
        String badge = stars > 0
                ? String.format(Locale.ROOT, "Lv %d  %d★", level, stars)
                : String.format(Locale.ROOT, "Lv %d", level);
        int bw = this.font.width(badge) + 10;
        CraftUI.chip(context, CARD_W - PAD - bw, y, bw, 16, CraftUI.SURFACE_RAISED, CraftUI.GOLD);
        context.text(this.font, badge, CARD_W - PAD - bw + 5, y + 4, CraftUI.GOLD, false);

        int line = y + 16 + CraftUI.S.XS;
        context.fill(PAD, line, CARD_W - PAD, line + 1, CraftUI.applyAlpha(CraftUI.GOLD, 0.35f));
        return line + CraftUI.S.SM;
    }

    /** What kind of run this was — or, for a daily, which daily and from which day. */
    private int drawHeadline(GuiGraphicsExtractor context, int y) {
        Component line;
        if (summary.daily) {
            // The banner already carries name and date, so this line goes back to saying how the
            // run ended — otherwise the same words would appear twice on a small card.
            line = Component.translatable(summary.kind.titleKey());
        } else if (!summary.headline.isEmpty()) {
            line = Component.nullToEmpty(summary.headline);
        } else {
            line = Component.translatable(summary.kind.titleKey());
        }
        String s = CraftUI.trimToWidth(this.font, line.getString(), CARD_W - 2 * PAD);
        context.text(this.font, s, (CARD_W - this.font.width(s)) / 2, y, CraftUI.TEXT_SECONDARY, false);
        return y + this.font.lineHeight + CraftUI.S.XS;
    }

    /** The one number that has to survive being a thumbnail. */
    private int drawBigNumber(GuiGraphicsExtractor context, int y) {
        String big = summary.kind.isTimed()
                ? formatTime(summary.runTicks)
                : String.format(Locale.ROOT, "#%d / %d", summary.placement, summary.participants);
        Component number = Component.nullToEmpty(big);

        // drawCenteredScaled takes the CENTRE, not the top edge — passing the top made the number
        // ride up into the headline above it.
        final float scale = 2.8f;
        int blockHeight = Math.round(this.font.lineHeight * scale);
        int centreY = y + blockHeight / 2;

        // A one-pixel dark offset underneath: at this size flat text looks pasted on, and the card
        // has to hold up as a thumbnail where the number is the only thing anyone reads.
        CraftUI.drawCenteredScaled(context, this.font, number,
                CARD_W / 2 + 1, centreY + 1, scale, 0xFF0B0E16);
        CraftUI.drawCenteredScaled(context, this.font, number,
                CARD_W / 2, centreY, scale, summary.tainted ? CraftUI.DANGER : CraftUI.TEXT_PRIMARY);
        y += blockHeight + CraftUI.S.XS;

        if (summary.newBest) {
            Component rec = Component.translatable("challengecraft.summary.new_record");
            int w = this.font.width(rec) + 18;
            int x = (CARD_W - w) / 2;
            // Solid gold with dark lettering, not gold-on-gold: the first version drew gold text on
            // a gold fill and the band came out looking empty. A filled stamp also carries further
            // at thumbnail size than an outlined one.
            context.fill(x, y, x + w, y + 13, CraftUI.GOLD);
            context.fill(x + 1, y + 1, x + w - 1, y + 12, 0xFFF2CE86);
            context.text(this.font, rec.getString(), (CARD_W - this.font.width(rec)) / 2, y + 3,
                    0xFF12161F, false);
            y += 13;
        }
        return y + CraftUI.S.SM;
    }

    /**
     * The active challenges as icons. Capped rather than wrapped forever — a run with thirty
     * challenges would otherwise push everything else off the card, and "+18" carries the same
     * message as eighteen more tiles.
     */
    private int drawChallengeIcons(GuiGraphicsExtractor context, int y) {
        if (summary.activeIds.isEmpty()) {
            return y;
        }
        int capacity = MAX_ICONS_PER_ROW * MAX_ICON_ROWS;
        int shown = Math.min(summary.activeIds.size(), capacity);
        int overflow = summary.activeIds.size() - shown;
        if (overflow > 0) {
            shown--;                       // free one tile for the "+N" marker
            overflow++;
        }

        int index = 0;
        for (int row = 0; row < MAX_ICON_ROWS && index < shown; row++) {
            int inRow = Math.min(MAX_ICONS_PER_ROW, shown - index);
            boolean lastRow = (index + inRow >= shown);
            int cells = inRow + (lastRow && overflow > 0 ? 1 : 0);
            int rowW = cells * (ICON + CraftUI.S.XS) - CraftUI.S.XS;
            int x = (CARD_W - rowW) / 2;

            for (int i = 0; i < inRow; i++, index++) {
                ItemStack icon = ChallengeIconProvider.getIcon(summary.activeIds.get(index));
                CraftUI.iconTileItem(context, icon, x, y, ICON, CraftUI.BORDER);
                x += ICON + CraftUI.S.XS;
            }
            if (lastRow && overflow > 0) {
                CraftUI.iconTile(context, x, y, ICON, CraftUI.BORDER);
                String more = "+" + overflow;
                context.text(this.font, more, x + (ICON - this.font.width(more)) / 2, y + 5,
                        CraftUI.TEXT_SECONDARY, false);
            }
            y += ICON + CraftUI.S.XS;
        }
        return y + CraftUI.S.XS;
    }

    private void drawChips(GuiGraphicsExtractor context, int y) {
        String diff = String.format(Locale.ROOT, "%.1f", summary.difficulty);
        Component left = Component.translatable("challengecraft.summary.difficulty", diff);
        Component right = Component.translatable("challengecraft.summary.xp",
                String.format(Locale.ROOT, "%,d", summary.xpGained));

        int lw = this.font.width(left) + 12;
        int rw = this.font.width(right) + 12;
        int total = lw + rw + CraftUI.S.SM;
        int x = (CARD_W - total) / 2;

        CraftUI.chip(context, x, y, lw, 14, CraftUI.SURFACE_RAISED, CraftUI.BORDER);
        context.text(this.font, left.getString(), x + 6, y + 3, CraftUI.TEXT_SECONDARY, false);
        x += lw + CraftUI.S.SM;
        CraftUI.chip(context, x, y, rw, 14, CraftUI.SURFACE_RAISED,
                summary.xpGained > 0 ? CraftUI.GOLD : CraftUI.BORDER);
        context.text(this.font, right.getString(), x + 6, y + 3,
                summary.xpGained > 0 ? CraftUI.GOLD : CraftUI.TEXT_MUTED, false);
    }

    /** Date and mod name, so a screenshot passed around still says where it came from. */
    private void drawFooter(GuiGraphicsExtractor context) {
        String date = summary.daily ? dailyDateText()
                : LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        int y = CARD_H - PAD - this.font.lineHeight + 2;
        context.text(this.font, date, PAD, y, CraftUI.TEXT_MUTED, false);
        String brand = "Challenge Craft";
        context.text(this.font, brand, CARD_W - PAD - this.font.width(brand), y,
                CraftUI.TEXT_MUTED, false);
    }

    private String dailyDateText() {
        long day = summary.dailyEpochDay > 0 ? summary.dailyEpochDay
                : LocalDate.now(ZoneOffset.UTC).toEpochDay();
        return LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    /** Shared with the server-side commands so the same number never gets two formats. */
    public static String formatTime(long ticks) {
        return net.kasax.challengecraft.util.ChallengeTimeUtil.formatTicks(ticks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
