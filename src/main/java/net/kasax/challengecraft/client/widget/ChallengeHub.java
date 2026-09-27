package net.kasax.challengecraft.client.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ChallengeCodeClient;
import net.kasax.challengecraft.client.RunHistory;
import net.kasax.challengecraft.client.screen.ChallengeCodeScreen;
import net.kasax.challengecraft.client.screen.LevelingScreen;
import net.kasax.challengecraft.client.screen.PresetScreen;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.util.ChallengeTimeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The title screen's Challenge Hub: one column right of the vanilla buttons with everything the mod
 * offers before a world is open — level and next unlocks, today's daily, continue the last run,
 * codes, presets, progress, and the last few runs.
 *
 * <p>It replaces three things that used to be scattered (the level button in the vanilla column,
 * the level badge in the corner, and the daily card with a code button under it). When the window
 * is too narrow for a column of at least {@link #MIN_WIDTH} — GUI scale 4 on 1080p — the title
 * screen keeps its old, compact arrangement instead; see {@code TitleScreenMixin}.
 */
@Environment(EnvType.CLIENT)
public final class ChallengeHub {
    public static final int MIN_WIDTH = 190;
    private static final int MAX_WIDTH = 260;
    private static final int GAP = 5;

    private ChallengeHub() {
    }

    /**
     * Lays the hub out at (x, y) and hands every widget to {@code add}. Returns false, adding
     * nothing, when there is not enough room — the caller then uses the compact layout.
     */
    public static boolean build(TitleScreen title, Consumer<AbstractWidget> add, int x, int y, int availableWidth,
                                int availableHeight) {
        int w = Math.min(MAX_WIDTH, availableWidth);
        if (w < MIN_WIDTH || availableHeight < LevelCard.HEIGHT + GAP + DailyCardWidget.HEIGHT) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        List<AbstractWidget> widgets = new ArrayList<>();
        int bottom = y + availableHeight;

        widgets.add(new LevelCard(x, y, w, () -> mc.setScreenAndShow(new LevelingScreen(title))));
        y += LevelCard.HEIGHT + GAP;
        widgets.add(new DailyCardWidget(x, y, w, DailyCardWidget::open));
        y += DailyCardWidget.HEIGHT + GAP;

        RunHistory.Entry resume = RunHistory.continuable();
        if (resume != null && y + ContinueButton.HEIGHT <= bottom) {
            widgets.add(new ContinueButton(x, y, w, resume, title));
            y += ContinueButton.HEIGHT + GAP;
        }

        if (y + 20 <= bottom) {
            int bw = (w - 2 * 4) / 3;
            widgets.add(new CraftButton(x, y, bw, 20, Component.translatable("challengecraft.hub.code"),
                    CraftButton.Style.NEUTRAL, b -> mc.setScreenAndShow(new ChallengeCodeScreen(title, null,
                    Component.translatable("challengecraft.code.hint.title"), code -> startWith(mc, code)))));
            widgets.add(new CraftButton(x + bw + 4, y, bw, 20, Component.translatable("challengecraft.preset.button"),
                    CraftButton.Style.NEUTRAL, b -> mc.setScreenAndShow(new PresetScreen(title, null,
                    Component.translatable("challengecraft.code.hint.title"), code -> startWith(mc, code)))));
            widgets.add(new CraftButton(x + 2 * (bw + 4), y, w - 2 * (bw + 4), 20,
                    Component.translatable("challengecraft.hub.progress"), CraftButton.Style.NEUTRAL,
                    b -> mc.setScreenAndShow(new LevelingScreen(title))));
            y += 20 + GAP;
        }

        List<RunHistory.Entry> runs = RunHistory.all();
        int rows = Math.min(3, runs.size());
        while (rows > 0 && y + RecentRuns.heightFor(rows) > bottom) {
            rows--;
        }
        if (rows > 0) {
            widgets.add(new RecentRuns(x, y, w, runs.subList(0, rows)));
        }

        widgets.forEach(add);
        return true;
    }

    /** A code or preset chosen on the title screen: world creation with everything filled in. */
    private static void startWith(Minecraft mc, net.kasax.challengecraft.code.ChallengeCode code) {
        ChallengeCodeClient.arm(code);
        CreateWorldScreen.openFresh(mc, () -> {
            ChallengeCodeClient.clear();
            mc.setScreenAndShow(new TitleScreen());
        });
    }

    private static long xp() {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.getUser() != null ? mc.getUser().getProfileId() : null;
        return uuid != null ? XpManager.getXp(uuid) : XpManager.getTotalXp();
    }

    // ---- widgets ---------------------------------------------------------------------------

    /**
     * Level, stars, progress to the next level (or star) and what that level unlocks. An
     * {@link AnimatedLevelButton} underneath, so the tutorial's "open your progress" step still
     * finds the level entry by type.
     */
    public static final class LevelCard extends AnimatedLevelButton {
        static final int HEIGHT = 40;
        private final Anim.Tween hover = new Anim.Tween(0f);

        LevelCard(int x, int y, int w, Runnable open) {
            super(x, y, w, HEIGHT, Component.translatable("challengecraft.mainmenu.leveling_button"), b -> open.run());
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            Font tr = Minecraft.getInstance().font;
            float g = hover.approach(isHovered() ? 1f : 0f, 12f);
            int x = getX();
            int y = getY();
            int w = getWidth();
            if (g > 0.01f) {
                ctx.fill(x - 2, y - 2, x + w + 2, y + HEIGHT + 2, CraftUI.applyAlpha(CraftUI.GOLD, 0.25f * g));
            }
            CraftUI.panel(ctx, x, y, w, HEIGHT, CraftUI.mix(CraftUI.SURFACE_BG, 0xEE1D2638, g), CraftUI.GOLD, CraftUI.GOLD);

            long xp = xp();
            int level = LevelManager.getLevelForXp(xp);
            int stars = LevelManager.getStars(xp);
            boolean maxed = level >= 20;
            long base = maxed ? LevelManager.getXpForLevel(20) + stars * 1000L : LevelManager.getXpForLevel(level);
            long need = LevelManager.getXpNeededForNextLevel(level);
            long have = Math.max(0, Math.min(need, xp - base));

            Component head = stars > 0
                    ? Component.translatable("challengecraft.hub.level_stars", level, stars)
                    : Component.translatable("challengecraft.hub.level", level);
            ctx.text(tr, head, x + 8, y + 6, CraftUI.TEXT_PRIMARY, false);
            String progress = String.format(Locale.ROOT, "%,d / %,d XP", have, need);
            ctx.text(tr, progress, x + w - 8 - tr.width(progress), y + 6, CraftUI.TEXT_SECONDARY, false);
            CraftUI.progressBar(ctx, x + 8, y + 18, w - 16, 5, need == 0 ? 1f : have / (float) need);
            String next = CraftUI.trimToWidth(tr, nextLine(level, maxed).getString(), w - 16);
            ctx.text(tr, next, x + 8, y + 27, CraftUI.TEXT_MUTED, false);
        }

        private static Component nextLine(int level, boolean maxed) {
            if (maxed) {
                return Component.translatable("challengecraft.hub.next_star");
            }
            List<String> names = new ArrayList<>();
            for (int id = 1; id <= 200; id++) {
                boolean exists = id > 100 ? LevelManager.ALL_PERKS.contains(id)
                        : net.kasax.challengecraft.client.screen.ChallengeTab.challengeIds().contains(id);
                if (exists && LevelManager.getRequiredLevel(id) == level + 1) {
                    names.add(ChallengeCodeClient.name(id).getString());
                }
            }
            if (names.isEmpty()) {
                return Component.translatable("challengecraft.hub.next_level_none", level + 1);
            }
            return Component.translatable("challengecraft.hub.next_level", level + 1, String.join(", ", names));
        }
    }

    /** "Continue: <challenges> · <time>" — opens the last unfinished run's world directly. */
    static final class ContinueButton extends Button {
        static final int HEIGHT = 28;
        private final RunHistory.Entry entry;
        private final Anim.Tween hover = new Anim.Tween(0f);

        ContinueButton(int x, int y, int w, RunHistory.Entry entry, Screen title) {
            super(x, y, w, HEIGHT, Component.translatable("challengecraft.hub.continue"), b -> {
                Minecraft mc = Minecraft.getInstance();
                mc.createWorldOpenFlows().openWorld(entry.worldId(), () -> mc.setScreenAndShow(new TitleScreen()));
            }, DEFAULT_NARRATION);
            this.entry = entry;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            Font tr = Minecraft.getInstance().font;
            float g = hover.approach(isHovered() ? 1f : 0f, 12f);
            int x = getX();
            int y = getY();
            int w = getWidth();
            CraftUI.panel(ctx, x, y, w, HEIGHT, CraftUI.mix(CraftUI.SURFACE_BG, 0xEE1D2638, g),
                    CraftUI.mix(CraftUI.BORDER, CraftUI.GOLD, g), CraftUI.ACCENT_HAIRLINE);
            ctx.text(tr, this.message, x + 8, y + 5, CraftUI.TEXT_PRIMARY, false);
            String sub = RunHistory.label(entry) + " · " + ChallengeTimeUtil.formatTicks(entry.ticks());
            ctx.text(tr, CraftUI.trimToWidth(tr, sub, w - 28), x + 8, y + 16, CraftUI.TEXT_MUTED, false);
            ctx.text(tr, "›", x + w - 12, y + 10, CraftUI.mix(CraftUI.GOLD, 0xFFF3D88A, g), false);
        }
    }

    /** The last runs: what was played, and how it ended. Read-only. */
    static final class RecentRuns extends AbstractWidget {
        private final List<RunHistory.Entry> runs;

        RecentRuns(int x, int y, int w, List<RunHistory.Entry> runs) {
            super(x, y, w, heightFor(runs.size()), Component.translatable("challengecraft.hub.recent"));
            this.runs = runs;
            this.active = false;
        }

        static int heightFor(int rows) {
            return 16 + rows * 11 + 4;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            Font tr = Minecraft.getInstance().font;
            int x = getX();
            int y = getY();
            int w = getWidth();
            CraftUI.panel(ctx, x, y, w, getHeight(), CraftUI.SURFACE_BG, CraftUI.BORDER, CraftUI.ACCENT_HAIRLINE);
            ctx.text(tr, getMessage(), x + 8, y + 5, CraftUI.TEXT_MUTED, false);
            int ry = y + 17;
            for (RunHistory.Entry e : runs) {
                String right;
                int color;
                if (e.state() == RunHistory.State.DONE) {
                    right = ChallengeTimeUtil.formatTicks(e.ticks())
                            + (e.xp() > 0 ? String.format(Locale.ROOT, " · +%,d XP", e.xp()) : "");
                    color = CraftUI.SUCCESS;
                } else {
                    right = Component.translatable("challengecraft.hub.open").getString() + " · "
                            + ChallengeTimeUtil.formatTicks(e.ticks());
                    color = CraftUI.TEXT_SECONDARY;
                }
                int rw = tr.width(right);
                ctx.text(tr, right, x + w - 8 - rw, ry, color, false);
                ctx.text(tr, CraftUI.trimToWidth(tr, RunHistory.label(e), w - 24 - rw), x + 8, ry, CraftUI.TEXT_PRIMARY, false);
                ry += 11;
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
