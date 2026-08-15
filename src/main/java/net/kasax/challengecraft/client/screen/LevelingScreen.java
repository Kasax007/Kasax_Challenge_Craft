package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Renders the progression journey view and its older list-layout fallback.
 * Manual milestone offsets are saved client-side so the artwork can be tuned without touching gameplay data.
 */
public class LevelingScreen extends Screen {
    private static final Identifier JOURNEY_TEXTURE = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/level_journey_map.png");
    private static final int JOURNEY_TEXTURE_WIDTH = 576;
    private static final int JOURNEY_TEXTURE_HEIGHT = 3072;

    private static final int FRAME_BG = 0xE0121620;
    private static final int FRAME_INNER = 0xD91D2431;
    private static final int FRAME_BORDER = 0xFF56657D;
    private static final int FRAME_ACCENT = 0xFFB4C7E7;
    private static final int PROGRESS_BG = 0xFF1A2230;
    private static final int PROGRESS_FILL = 0xFF6CD8A4;
    private static final int FUTURE_PATH = 0xAA56657D;
    private static final int COMPLETE_PATH = 0xFF85F2D1;
    private static final int CURRENT_PATH = 0xFFF1D16A;
    private static final int DETAIL_BG = 0xE619202C;
    private static final int DETAIL_ACCENT = 0xFFE3B35A;
    private static final int SPAWN_RIBBON_Y = 96;
    private static final int CAVERN_RIBBON_Y = 732;
    private static final int NETHER_RIBBON_Y = 1450;
    private static final int END_RIBBON_Y = 2174;
    private static final int INFINITY_RIBBON_Y = 2642;
    private static final int END_ART_BOTTOM_Y = 2628;
    private static final int LAYOUT_STEP = 2;

    private final Screen parent;
    private final List<Milestone> milestones = new ArrayList<>();
    private final Map<String, LevelJourneyLayoutStore.LayoutOffset> layoutOffsets = new HashMap<>(LevelJourneyLayoutStore.load());

    private JourneyPanel journeyPanel;
    private WidgetScrollPanel legacyScrollPanel;
    private Milestone hoveredMilestone;
    private Milestone pinnedMilestone;
    private Milestone currentMilestone;
    private Milestone nextMilestone;
    private Milestone draggedMilestone;
    private boolean showInfinityTrack;
    private boolean layoutEditMode;
    private boolean legacyLayoutMode;

    private long totalXp;
    private int currentLevel;
    private int currentStars;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;
    private int headerHeight;
    private int mapX;
    private int mapY;
    private int mapWidth;
    private int mapHeight;
    private float artScale;
    private float artWidthScale;
    private int renderedArtWidth;
    private int renderedArtHeight;
    private int detailX;
    private int detailY;
    private int detailWidth;
    private int detailHeight;

    private Button editLayoutButton;
    private Button resetLayoutButton;
    private LayoutModeWidget layoutModeButton;

    /**
     * The Journey/Legacy toggle, for code that needs to point at it.
     *
     * <p>Exposed because the widget's message is {@link Component#empty()} — "Journey" and "Legacy"
     * are painted inside its draw method — so nothing can find it by label. The tutorial step that
     * explains the toggle was looking for that text, found nothing, and highlighted nothing.
     */
    public net.minecraft.client.gui.components.AbstractWidget getLayoutModeButton() {
        return this.layoutModeButton;
    }
    private boolean draggingRadarThumb;
    private int radarRailY;
    private int radarViewportHeight;
    private int radarViewportTravel;

    public LevelingScreen(Screen parent) {
        super(Component.translatable("challengecraft.leveling.title"));
        this.parent = parent;
        this.legacyLayoutMode = ChallengeCraftClient.USE_LEGACY_LEVEL_SCREEN_LAYOUT;

        // Self-heals the post-disconnect/pre-sync zero value (see the accessor's javadoc);
        // LevelSyncHandler additionally rebuilds this screen in place if the authoritative
        // value arrives while it is open.
        ChallengeCraftClient.refreshLocalPlayerXp();
    }

    /** Lets a live XP correction (see {@code LevelSyncHandler}) rebuild this screen in place. */
    public Screen getParentScreen() {
        return this.parent;
    }

    @Override
    protected void init() {
        super.init();
        refreshProgress();
        computeLayout();
        buildMilestones();
        addLayoutModeButton();

        if (this.legacyLayoutMode) {
            initLegacyLayout();
        } else {
            initJourneyLayout();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.layoutEditMode && this.pinnedMilestone != null) {
            // 26.2 folded key/scancode/modifiers into KeyEvent; the shift check moved onto it too.
            int step = event.hasShiftDown() ? LAYOUT_STEP * 2 : LAYOUT_STEP;
            boolean handled = switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT -> nudgePinnedMilestone(-step, 0);
                case GLFW.GLFW_KEY_RIGHT -> nudgePinnedMilestone(step, 0);
                case GLFW.GLFW_KEY_UP -> nudgePinnedMilestone(0, -step);
                case GLFW.GLFW_KEY_DOWN -> nudgePinnedMilestone(0, step);
                case GLFW.GLFW_KEY_R -> {
                    resetMilestoneOffset(this.pinnedMilestone);
                    yield true;
                }
                default -> false;
            };
            if (handled) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.legacyLayoutMode && event.button() == 0 && isMouseOverRadarThumb(event.x(), event.y())) {
            this.draggingRadarThumb = true;
            updateRadarScroll(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.draggingRadarThumb && event.button() == 0) {
            updateRadarScroll(event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingRadarThumb && event.button() == 0) {
            this.draggingRadarThumb = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean nudgePinnedMilestone(int dx, int dy) {
        if (this.pinnedMilestone == null) {
            return false;
        }
        this.pinnedMilestone.offsetArtX += dx;
        this.pinnedMilestone.offsetArtY += dy;
        applyManualOffset(this.pinnedMilestone);
        if (this.journeyPanel != null) {
            this.journeyPanel.recomputeGeometry();
        }
        saveLayoutOffset(this.pinnedMilestone);
        return true;
    }

    private boolean isMouseOverRadarThumb(double mouseX, double mouseY) {
        if (this.journeyPanel == null || this.legacyLayoutMode || this.radarViewportHeight <= 0) {
            return false;
        }

        int panelWidth = Math.min(112, Math.max(92, this.mapWidth / 5));
        int panelX = this.mapX + 10;
        int railX = panelX + 16;
        int scrollRange = Math.max(1, this.journeyPanel.contentHeight - this.journeyPanel.getHeight());
        int viewportTop = this.radarRailY + Math.round((float) this.journeyPanel.scrollAmount() / scrollRange * this.radarViewportTravel);
        return mouseX >= railX - 5 && mouseX <= railX + 11 && mouseY >= viewportTop && mouseY <= viewportTop + this.radarViewportHeight;
    }

    private void updateRadarScroll(double mouseY) {
        if (this.journeyPanel == null) {
            return;
        }
        int centerY = Mth.clamp((int) Math.round(mouseY) - this.radarViewportHeight / 2, this.radarRailY, this.radarRailY + this.radarViewportTravel);
        float ratio = this.radarViewportTravel <= 0 ? 0.0f : (centerY - this.radarRailY) / (float) this.radarViewportTravel;
        this.journeyPanel.setScrollRatio(ratio);
    }

    private Component getLayoutButtonText() {
        return Component.translatable(this.layoutEditMode
                ? "challengecraft.leveling.layout_edit.on"
                : "challengecraft.leveling.layout_edit.off");
    }

    private Component getLayoutModeButtonText() {
        return Component.translatable(this.legacyLayoutMode
                ? "challengecraft.leveling.layout_mode.legacy"
                : "challengecraft.leveling.layout_mode.journey");
    }

    private void updateLayoutButtons() {
        if (this.editLayoutButton != null) {
            this.editLayoutButton.setMessage(getLayoutButtonText());
        }
        if (this.resetLayoutButton != null) {
            this.resetLayoutButton.active = this.layoutEditMode;
        }
    }

    private void addLayoutModeButton() {
        int buttonWidth = 140;
        int buttonX = this.frameX + this.frameWidth - buttonWidth - 16;
        int buttonY = this.frameY + 12;
        this.layoutModeButton = addRenderableWidget(new LayoutModeWidget(buttonX, buttonY, buttonWidth, 20));
    }

    private void toggleLayoutMode() {
        ChallengeCraftClient.USE_LEGACY_LEVEL_SCREEN_LAYOUT = !this.legacyLayoutMode;
        this.layoutEditMode = false;
        this.draggedMilestone = null;
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(new LevelingScreen(this.parent));
        }
    }

    private void initJourneyLayout() {
        this.legacyScrollPanel = null;
        this.journeyPanel = addRenderableWidget(new JourneyPanel(
                this.mapX,
                this.mapY,
                this.mapWidth,
                this.mapHeight,
                this.milestones,
                this.totalXp,
                milestone -> this.hoveredMilestone = milestone,
                milestone -> {
                    this.pinnedMilestone = milestone;
                    if (this.journeyPanel != null) {
                        this.journeyPanel.setPinnedMilestone(milestone);
                    }
                }
        ));
        this.journeyPanel.setPinnedMilestone(this.pinnedMilestone);
        if (this.currentMilestone != null && this.nextMilestone != null && this.currentMilestone != this.nextMilestone) {
            this.journeyPanel.centerOnY((this.currentMilestone.centerY() + this.nextMilestone.centerY()) / 2);
        } else {
            this.journeyPanel.centerOn(this.currentMilestone != null ? this.currentMilestone : this.pinnedMilestone);
        }

        int buttonY = this.height - 26;
        int startX = this.width / 2 - 222;
        addRenderableWidget(Button.builder(Component.translatable("challengecraft.ui.back"), button -> onClose())
                .bounds(startX, buttonY, 102, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("challengecraft.leveling.center_current"), button -> {
                    if (this.journeyPanel != null) {
                        this.journeyPanel.centerOn(this.currentMilestone != null ? this.currentMilestone : this.pinnedMilestone);
                    }
                })
                .bounds(startX + 108, buttonY, 102, 20)
                .build());
        this.editLayoutButton = addRenderableWidget(Button.builder(getLayoutButtonText(), button -> {
                    this.layoutEditMode = !this.layoutEditMode;
                    this.draggedMilestone = null;
                    updateLayoutButtons();
                })
                .bounds(startX + 216, buttonY, 102, 20)
                .build());
        this.resetLayoutButton = addRenderableWidget(Button.builder(Component.translatable("challengecraft.leveling.reset_layout"), button -> {
                    this.layoutOffsets.clear();
                    this.layoutOffsets.putAll(LevelJourneyLayoutStore.defaultOffsets());
                    LevelJourneyLayoutStore.clear();
                    buildMilestones();
                    if (this.journeyPanel != null) {
                        this.journeyPanel.refreshMilestones(this.milestones, this.totalXp);
                        this.journeyPanel.setPinnedMilestone(this.pinnedMilestone);
                    }
                })
                .bounds(startX + 324, buttonY, 120, 20)
                .build());
        updateLayoutButtons();
    }

    private void initLegacyLayout() {
        this.journeyPanel = null;
        this.editLayoutButton = null;
        this.resetLayoutButton = null;
        int panelWidth = Math.min(350, this.mapWidth - 28);
        int panelHeight = Math.max(120, this.mapHeight - 12);
        int panelX = this.frameX + (this.frameWidth - panelWidth) / 2;
        int panelY = this.mapY + 6;
        this.legacyScrollPanel = addRenderableWidget(new WidgetScrollPanel(panelX, panelY, panelWidth, panelHeight, Component.empty()));

        int currentY = panelY + 6;
        for (Milestone milestone : this.milestones) {
            LegacyMilestoneEntryWidget entry = new LegacyMilestoneEntryWidget(panelX + 6, currentY, panelWidth - 24, milestone);
            this.legacyScrollPanel.addChild(entry);
            currentY += entry.getHeight() + 6;
        }

        addRenderableWidget(Button.builder(Component.translatable("challengecraft.ui.back"), button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // No explicit background call any more: 26.2's extractRenderStateWithTooltipAndSubtitles
        // runs extractBackground() in its own stratum right before this method.
        drawFrame(context, this.frameX, this.frameY, this.frameWidth, this.frameHeight, FRAME_BG, FRAME_BORDER, FRAME_ACCENT);
        drawFrame(context, this.frameX + 6, this.frameY + 6, this.frameWidth - 12, this.frameHeight - 12, FRAME_INNER, 0xFF2C3444, 0xFF7FA1D0);
        if (this.legacyLayoutMode && this.legacyScrollPanel != null) {
            drawPanel(context, this.legacyScrollPanel.getX() - 6, this.legacyScrollPanel.getY() - 6, this.legacyScrollPanel.getWidth() + 12, this.legacyScrollPanel.getHeight() + 12, 0xA2141A23, 0xFF364255, 0xFF8BA6D8);
        }

        // The header MUST be drawn before super.extractRenderState, because
        // super is what emits the widgets — and the layout-mode toggle sits inside the header band
        // (button at frameY + 12, header panel from frameY + 10). Drawing the header afterwards
        // painted its opaque 0xF0151C27 panel straight over the button, which is why the
        // Journey/Legacy switch was invisible. The header even reserves space for the button
        // (`reservedRight`), so sitting behind it is the intended arrangement — only the order was
        // wrong. Every other screen in the mod already draws its chrome before super.
        drawHeader(context);

        super.extractRenderState(context, mouseX, mouseY, delta);

        // These two are side panels, laid out beside the widgets rather than over them, so they
        // stay after super — they read hover state and must not be covered by the scroll panel.
        if (!this.legacyLayoutMode) {
            drawProgressRadar(context);
            drawDetailCard(context, mouseX, mouseY);
        }
    }

    private void refreshProgress() {
        this.totalXp = ChallengeCraftClient.LOCAL_PLAYER_XP;
        this.currentLevel = LevelManager.getLevelForXp(this.totalXp);
        this.currentStars = LevelManager.getStars(this.totalXp);
    }

    private void computeLayout() {
        this.frameX = 4;
        this.frameY = 4;
        this.frameWidth = Math.max(320, this.width - 8);
        this.frameHeight = Math.max(180, this.height - 32);

        this.headerHeight = 80;
        this.detailHeight = this.legacyLayoutMode ? 0 : 110;

        this.mapX = this.frameX + 10;
        this.mapY = this.frameY + this.headerHeight + 6;
        this.mapWidth = this.frameWidth - 20;
        this.detailX = this.mapX;
        this.detailWidth = this.mapWidth;
        this.detailY = this.frameY + this.frameHeight - this.detailHeight - 10;
        int mapBottom = this.legacyLayoutMode ? this.frameY + this.frameHeight - 18 : this.detailY;
        this.mapHeight = Math.max(70, mapBottom - this.mapY);

        float idealScale = Mth.clamp((this.mapWidth - 18.0f) / (float) JOURNEY_TEXTURE_WIDTH, 0.75f, 1.75f);
        float quantizedScale = Math.max(0.75f, Math.round(idealScale * 4.0f) / 4.0f);
        while (quantizedScale > 0.75f && JOURNEY_TEXTURE_WIDTH * quantizedScale > this.mapWidth - 8) {
            quantizedScale -= 0.25f;
        }
        this.artScale = quantizedScale;

        this.artWidthScale = this.mapWidth / (float) JOURNEY_TEXTURE_WIDTH;

        this.renderedArtWidth = Math.round(JOURNEY_TEXTURE_WIDTH * this.artWidthScale);
        this.renderedArtHeight = Math.round(JOURNEY_TEXTURE_HEIGHT * this.artScale);
    }

    private void buildMilestones() {
        this.milestones.clear();
        this.showInfinityTrack = false;

        int index = 0;
        for (int level = 1; level <= LevelManager.MAX_LEVEL; level++) {
            List<Reward> rewards = buildLevelRewards(level);
            Milestone milestone = new Milestone(index++, level, false, LevelManager.getXpForLevel(level), rewards);
            milestone.title = tr("challengecraft.leveling.level_value", level);
            milestone.summary = buildLevelSummary(rewards);
            milestone.unlocked = this.currentLevel >= level;
            this.milestones.add(milestone);
        }

        if (this.currentLevel >= LevelManager.MAX_LEVEL) {
            int maxStarToShow = this.currentStars + 1;
            for (int star = 1; star <= maxStarToShow; star++) {
                if (star == 20 && this.currentStars < 20) {
                    continue;
                }

                List<Reward> rewards = buildStarRewards(star);
                if (rewards.isEmpty()) {
                    continue;
                }

                Milestone milestone = new Milestone(index++, star, true, LevelManager.getXpForLevel(LevelManager.MAX_LEVEL) + (long) star * 1000L, rewards);
                milestone.title = tr("challengecraft.leveling.star_value", star);
                milestone.summary = buildStarSummary(rewards);
                milestone.unlocked = this.currentStars >= star;
                this.milestones.add(milestone);
                this.showInfinityTrack = true;
            }
        }

        int center = this.mapWidth / 2;
        int artInset = 18;
        int artMinCenter = artInset;
        int artMaxCenter = this.mapWidth - artInset;
        for (int i = 0; i < this.milestones.size(); i++) {
            Milestone milestone = this.milestones.get(i);
            double wave = Math.sin(i * 0.78) + 0.32 * Math.sin(i * 0.31 + 1.35);
            int amplitude = Math.min(this.renderedArtWidth / 2 - 72, Math.max(124, this.renderedArtWidth / 2 - 88));
            int localAmplitude = milestone.isStar ? (int) (amplitude * 0.48f) : amplitude;
            milestone.cardWidth = milestone.isStar ? 118 : 128;
            milestone.cardHeight = milestone.isStar ? 44 : 56;
            int x = center + (int) Math.round(wave * localAmplitude);
            milestone.baseAnchorX = Mth.clamp(x, artMinCenter + milestone.cardWidth / 2, artMaxCenter - milestone.cardWidth / 2);
            int artY = milestone.isStar ? getStarAnchorY(milestone.value) : getLevelAnchorY(milestone.value);
            milestone.baseAnchorY = scaleArtY(compressMilestoneArtY(artY));
            LevelJourneyLayoutStore.LayoutOffset offset = this.layoutOffsets.get(milestone.layoutKey());
            milestone.offsetArtX = offset != null ? offset.x() : 0.0f;
            milestone.offsetArtY = offset != null ? offset.y() : 0.0f;
            applyManualOffset(milestone);
        }

        this.currentMilestone = findCurrentMilestone();
        this.nextMilestone = findNextMilestone();
        this.pinnedMilestone = this.nextMilestone != null ? this.nextMilestone : this.currentMilestone;
    }

    private int getLevelAnchorY(int level) {
        if (level <= 5) {
            return interpolate(level - 1, 4, 136, 656);
        }
        if (level <= 10) {
            return interpolate(level - 6, 4, 796, 1336);
        }
        if (level <= 15) {
            return interpolate(level - 11, 4, 1456, 1896);
        }
        return interpolate(level - 16, 4, 2238, 2510);
    }

    private int getStarAnchorY(int star) {
        return 2638 + Math.round((star - 1) * (204.0f / 19.0f));
    }

    private int scaleArtY(int y) {
        return Math.round(y * this.artScale);
    }

    private int compressMilestoneArtY(int y) {
        int origin = 136;
        return origin + Math.round((y - origin) * 0.95f);
    }

    private void applyManualOffset(Milestone milestone) {
        milestone.anchorX = milestone.baseAnchorX + scaleArtXDistance(milestone.offsetArtX);
        milestone.anchorY = milestone.baseAnchorY + scaleArtYDistance(milestone.offsetArtY);
    }

    private int scaleArtXDistance(float delta) {
        return Math.round(delta * this.artWidthScale);
    }

    private int scaleArtYDistance(float delta) {
        return Math.round(delta * this.artScale);
    }

    private void saveLayoutOffset(Milestone milestone) {
        if (Math.abs(milestone.offsetArtX) < 0.05f && Math.abs(milestone.offsetArtY) < 0.05f) {
            this.layoutOffsets.remove(milestone.layoutKey());
        } else {
            this.layoutOffsets.put(milestone.layoutKey(), new LevelJourneyLayoutStore.LayoutOffset(milestone.offsetArtX, milestone.offsetArtY));
        }
        LevelJourneyLayoutStore.save(this.layoutOffsets);
    }

    private void resetMilestoneOffset(Milestone milestone) {
        if (milestone == null) {
            return;
        }
        milestone.offsetArtX = 0.0f;
        milestone.offsetArtY = 0.0f;
        applyManualOffset(milestone);
        if (this.journeyPanel != null) {
            this.journeyPanel.recomputeGeometry();
        }
        saveLayoutOffset(milestone);
    }

    private int interpolate(int index, int maxIndex, int start, int end) {
        if (maxIndex <= 0) {
            return start;
        }
        return start + Math.round((end - start) * (index / (float) maxIndex));
    }

    private Milestone findCurrentMilestone() {
        Milestone latest = this.milestones.isEmpty() ? null : this.milestones.get(0);
        for (Milestone milestone : this.milestones) {
            if (this.totalXp >= milestone.requiredXp) {
                latest = milestone;
            } else {
                break;
            }
        }
        return latest;
    }

    private Milestone findNextMilestone() {
        for (Milestone milestone : this.milestones) {
            if (this.totalXp < milestone.requiredXp) {
                return milestone;
            }
        }
        return this.milestones.isEmpty() ? null : this.milestones.get(this.milestones.size() - 1);
    }

    private List<Reward> buildLevelRewards(int level) {
        List<Reward> rewards = new ArrayList<>();
        for (int perkId : LevelManager.ALL_PERKS) {
            if (LevelManager.getRequiredLevel(perkId) == level) {
                rewards.add(Reward.perk(
                        Component.translatable("challengecraft.perk." + perkId),
                        Component.translatable("challengecraft.perk." + perkId + ".desc"),
                        perkId
                ));
            }
        }

        for (int challengeId = 1; challengeId <= 46; challengeId++) {
            if (LevelManager.getRequiredLevel(challengeId) == level) {
                rewards.add(Reward.challenge(
                        Component.translatable("challengecraft.worldcreate.challenge" + challengeId),
                        Component.translatable("challengecraft.worldcreate.challenge" + challengeId + ".desc"),
                        challengeId
                ));
            }
        }

        if (level == LevelManager.MAX_LEVEL) {
            rewards.add(Reward.special(
                    Component.translatable("challengecraft.leveling.infinity_road"),
                    Component.translatable("challengecraft.leveling.infinity_road.desc"),
                    0xFFE3B35A
            ));
        }
        return rewards;
    }

    private List<Reward> buildStarRewards(int star) {
        List<Reward> rewards = new ArrayList<>();
        String rewardId = LevelManager.getStarReward(star);
        if (rewardId == null) {
            return rewards;
        }

        if (rewardId.startsWith("perk_")) {
            int perkId = LevelManager.PERK_INFINITY_WEAPON;
            rewards.add(Reward.perk(
                    Component.translatable("challengecraft.perk." + perkId),
                    Component.translatable("challengecraft.perk." + perkId + ".desc"),
                    perkId
            ));
            return rewards;
        }

        int color = switch (rewardId) {
            case "green" -> 0xFF5ED37B;
            case "blue" -> 0xFF67A8FF;
            case "red" -> 0xFFF06A63;
            case "purple" -> 0xFFC273FF;
            case "gold" -> 0xFFFFCF6B;
            case "rainbow" -> 0xFF6DE7E1;
            default -> 0xFFFFFFFF;
        };
        Component label = switch (rewardId) {
            case "green" -> Component.translatable("challengecraft.leveling.aura.green");
            case "blue" -> Component.translatable("challengecraft.leveling.aura.blue");
            case "red" -> Component.translatable("challengecraft.leveling.aura.red");
            case "purple" -> Component.translatable("challengecraft.leveling.aura.purple");
            case "gold" -> Component.translatable("challengecraft.leveling.aura.gold");
            case "rainbow" -> Component.translatable("challengecraft.leveling.aura.rainbow");
            default -> Component.nullToEmpty(rewardId);
        };
        rewards.add(Reward.color(label, Component.translatable("challengecraft.leveling.aura.desc"), color));
        return rewards;
    }

    private String buildLevelSummary(List<Reward> rewards) {
        int perkCount = 0;
        int challengeCount = 0;
        for (Reward reward : rewards) {
            if (reward.kind == RewardKind.PERK) {
                perkCount++;
            } else if (reward.kind == RewardKind.CHALLENGE) {
                challengeCount++;
            }
        }

        if (perkCount == 0) {
            return challengeCount == 1
                    ? tr("challengecraft.leveling.summary.challenge_single")
                    : tr("challengecraft.leveling.summary.challenge_multiple", challengeCount);
        }
        if (challengeCount == 0) {
            return perkCount == 1
                    ? tr("challengecraft.leveling.summary.perk_single")
                    : tr("challengecraft.leveling.summary.perk_multiple", perkCount);
        }
        return tr("challengecraft.leveling.summary.mixed", challengeCount, perkCount, perkCount == 1 ? tr("challengecraft.leveling.summary.perk_word_single") : tr("challengecraft.leveling.summary.perk_word_multiple"));
    }

    private String buildStarSummary(List<Reward> rewards) {
        Reward reward = rewards.get(0);
        if (reward.kind == RewardKind.COLOR) {
            return tr("challengecraft.leveling.summary.aura");
        }
        return tr("challengecraft.leveling.summary.infinity");
    }

    private void drawHeader(GuiGraphicsExtractor context) {
        int headerX = this.mapX;
        int headerY = this.frameY + 10;
        int headerWidth = this.mapWidth;
        int reservedRight = (this.layoutModeButton != null ? this.layoutModeButton.getWidth() : 118) + 26;
        int headerInnerX = headerX + 12;
        int topTextWidth = Math.max(240, headerWidth - reservedRight - 24);
        int fullRowWidth = headerWidth - 24;

        drawPanel(context, headerX, headerY, headerWidth, this.headerHeight - 4, 0xF0151C27, 0xFF3A465A, 0xFF8BA6D8);
        if (this.legacyLayoutMode) {
            drawLegacyHeader(context, headerInnerX, headerY + 7, topTextWidth, fullRowWidth);
        } else {
            drawJourneyHeader(context, headerInnerX, headerY + 7, topTextWidth, fullRowWidth);
        }
    }

    private void drawJourneyHeader(GuiGraphicsExtractor context, int x, int y, int topWidth, int rowWidth) {
        context.text(this.font, Component.translatable("challengecraft.leveling.header.journey"), x, y, 0xFFF4F8FF, false);
        String subtitle = trimToWidth(tr("challengecraft.leveling.header.journey.desc"), topWidth);
        context.text(this.font, Component.nullToEmpty(subtitle), x, y + 11, 0xFF9FB0CA, false);

        float progress;
        String progressText;
        if (this.currentLevel >= LevelManager.MAX_LEVEL) {
            long maxXp = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL);
            long starProgress = Math.max(0L, this.totalXp - maxXp) % 1000L;
            progress = starProgress / 1000.0f;
            progressText = tr("challengecraft.leveling.header.infinity_progress", formatXp(starProgress));
        } else {
            long currentLevelXp = LevelManager.getXpForLevel(this.currentLevel);
            long nextLevelXp = LevelManager.getXpForLevel(this.currentLevel + 1);
            long neededXp = Math.max(1L, nextLevelXp - currentLevelXp);
            long progressXp = Math.max(0L, this.totalXp - currentLevelXp);
            progress = Mth.clamp(progressXp / (float) neededXp, 0.0f, 1.0f);
            progressText = tr("challengecraft.leveling.header.progress", formatXp(progressXp), formatXp(neededXp));
        }

        String left = tr("challengecraft.leveling.level_value", this.currentLevel);
        String right = this.currentLevel < LevelManager.MAX_LEVEL
                ? tr("challengecraft.leveling.header.next_level", this.currentLevel + 1)
                : (this.currentStars > 0 ? tr("challengecraft.leveling.header.stars", this.currentStars) : tr("challengecraft.leveling.header.infinity_unlocked"));
        int labelY = y + 24;
        context.text(this.font, Component.nullToEmpty(left), x, labelY, 0xFFDAE7FF, false);
        context.text(this.font, Component.nullToEmpty(right), x + rowWidth - this.font.width(right), labelY, 0xFFE3B35A, false);

        int progressPanelY = y + 35;
        int progressPanelWidth = Math.max(200, rowWidth);
        drawPanel(context, x, progressPanelY, progressPanelWidth, 16, 0xEE0F1520, 0xFF324052, 0xFF6D8DBB);
        int barX = x + 6;
        int barY = progressPanelY + 3;
        int barWidth = progressPanelWidth - 12;
        int barHeight = 10;
        drawBar(context, barX, barY, barWidth, barHeight, progress);

        String footer = this.layoutEditMode
                ? tr("challengecraft.leveling.header.edit_mode")
                : progressText;
        int footerTop = progressPanelY + 16;
        int footerBottom = y + this.headerHeight - 13;
        int footerY = footerTop + Math.max(0, (footerBottom - footerTop - this.font.lineHeight) / 2);
        context.text(this.font, Component.nullToEmpty(trimToWidth(footer, rowWidth)), x, footerY, this.layoutEditMode ? 0xFFF7DF8A : 0xFFB9C5DA, false);
    }

    private void drawLegacyHeader(GuiGraphicsExtractor context, int x, int y, int topWidth, int rowWidth) {
        context.text(this.font, Component.translatable("challengecraft.mainmenu.leveling_button"), x, y, 0xFFF4F8FF, false);
        String subtitle = trimToWidth(tr("challengecraft.leveling.header.legacy.desc"), topWidth);
        context.text(this.font, Component.nullToEmpty(subtitle), x, y + 11, 0xFF9FB0CA, false);

        float progress;
        String progressText;
        if (this.currentLevel >= LevelManager.MAX_LEVEL) {
            long maxXp = LevelManager.getXpForLevel(LevelManager.MAX_LEVEL);
            long starProgress = Math.max(0L, this.totalXp - maxXp) % 1000L;
            progress = starProgress / 1000.0f;
            progressText = tr("challengecraft.leveling.header.infinity_progress", formatXp(starProgress));
        } else {
            long currentLevelXp = LevelManager.getXpForLevel(this.currentLevel);
            long nextLevelXp = LevelManager.getXpForLevel(this.currentLevel + 1);
            long neededXp = Math.max(1L, nextLevelXp - currentLevelXp);
            long progressXp = Math.max(0L, this.totalXp - currentLevelXp);
            progress = Mth.clamp(progressXp / (float) neededXp, 0.0f, 1.0f);
            progressText = tr("challengecraft.leveling.header.progress", formatXp(progressXp), formatXp(neededXp));
        }

        String left = tr("challengecraft.leveling.level_value", this.currentLevel);
        String right = this.currentLevel < LevelManager.MAX_LEVEL
                ? tr("challengecraft.leveling.header.next_level", this.currentLevel + 1)
                : (this.currentStars > 0 ? tr("challengecraft.leveling.header.stars", this.currentStars) : tr("challengecraft.leveling.header.infinity_unlocked"));
        int labelY = y + 24;
        context.text(this.font, Component.nullToEmpty(left), x, labelY, 0xFFDAE7FF, false);
        context.text(this.font, Component.nullToEmpty(right), x + rowWidth - this.font.width(right), labelY, 0xFFE3B35A, false);

        int progressPanelY = y + 35;
        int progressPanelWidth = Math.max(200, rowWidth);
        drawPanel(context, x, progressPanelY, progressPanelWidth, 16, 0xEE0F1520, 0xFF324052, 0xFF6D8DBB);
        int barX = x + 6;
        int barY = progressPanelY + 3;
        int barWidth = progressPanelWidth - 12;
        int barHeight = 10;
        drawBar(context, barX, barY, barWidth, barHeight, progress);
        int footerTop = progressPanelY + 16;
        int footerBottom = y + this.headerHeight - 13;
        int footerY = footerTop + Math.max(0, (footerBottom - footerTop - this.font.lineHeight) / 2);
        context.text(this.font, Component.nullToEmpty(trimToWidth(progressText, rowWidth)), x, footerY, 0xFFB9C5DA, false);
    }

    private void drawProgressRadar(GuiGraphicsExtractor context) {
        if (this.journeyPanel == null) {
            return;
        }

        int panelWidth = Math.min(112, Math.max(92, this.mapWidth / 5));
        int panelX = this.mapX + 10;
        int panelY = this.mapY + 24;
        int panelHeight = Math.min(this.mapHeight - 32, 220);
        drawPanel(context, panelX, panelY, panelWidth, panelHeight, 0xAA111923, 0xFF364255, 0xFF8BA6D8);

        context.text(this.font, Component.translatable("challengecraft.leveling.radar.title"), panelX + 10, panelY + 8, 0xFFF2F6FF, false);
        String count = tr("challengecraft.leveling.radar.count", getCompletedMilestoneCount(), this.milestones.size());
        context.text(this.font, Component.nullToEmpty(count), panelX + 10, panelY + 19, 0xFFB9C5DA, false);

        int railX = panelX + 16;
        int railY = panelY + 34;
        int railHeight = panelHeight - 70;
        context.fill(railX, railY, railX + 4, railY + railHeight, 0xFF233041);
        context.fill(railX + 1, railY + 1, railX + 3, railY + railHeight - 1, 0xFF55657B);

        int contentHeight = Math.max(1, this.journeyPanel.contentHeight);
        int viewportHeight = Math.max(8, Math.round(this.journeyPanel.getHeight() / (float) contentHeight * railHeight));
        int scrollRange = Math.max(1, contentHeight - this.journeyPanel.getHeight());
        int viewportTravel = Math.max(0, railHeight - viewportHeight);
        int viewportTop = railY + Math.round((float) this.journeyPanel.scrollAmount() / scrollRange * viewportTravel);
        this.radarRailY = railY;
        this.radarViewportHeight = Math.min(viewportHeight, railHeight);
        this.radarViewportTravel = viewportTravel;
        drawChip(context, railX - 3, viewportTop, 10, Math.min(viewportHeight, railHeight), 0x66232D3A, 0xFFB3D5FF);

        for (Milestone milestone : this.milestones) {
            float ratio = Mth.clamp(milestone.centerY() / (float) contentHeight, 0.0f, 1.0f);
            int dotY = railY + Math.round(ratio * railHeight);
            int color = milestone.unlocked ? 0xFF7BE0A4 : 0xFF75839A;
            if (milestone == this.currentMilestone) {
                color = CURRENT_PATH;
            } else if (milestone == this.nextMilestone) {
                color = 0xFFB3D5FF;
            } else if (milestone.isStar) {
                color = 0xFFE3B35A;
            }
            context.fill(railX + 7, dotY - 1, railX + 11, dotY + 3, color);
        }

        String currentText = this.currentMilestone != null ? this.currentMilestone.title : tr("challengecraft.leveling.none");
        String nextText = this.nextMilestone != null ? this.nextMilestone.title : tr("challengecraft.leveling.done");
        context.text(this.font, Component.nullToEmpty(trimToWidth(tr("challengecraft.leveling.radar.now", currentText), panelWidth - 34)), panelX + 24, panelY + panelHeight - 32, 0xFFE8EEF9, false);
        context.text(this.font, Component.nullToEmpty(trimToWidth(tr("challengecraft.leveling.radar.next", nextText), panelWidth - 34)), panelX + 24, panelY + panelHeight - 19, 0xFFB3D5FF, false);
    }

    private void drawUpcomingPanel(GuiGraphicsExtractor context) {
        if (this.journeyPanel == null) {
            return;
        }

        int panelWidth = Math.min(172, Math.max(138, this.mapWidth / 3));
        int panelX = this.mapX + this.mapWidth - panelWidth - 10;
        int panelY = this.mapY + 10;
        int panelHeight = Math.min(this.mapHeight - 20, 168);
        drawPanel(context, panelX, panelY, panelWidth, panelHeight, 0xAA111923, 0xFF364255, 0xFFE3B35A);

        context.text(this.font, Component.translatable("challengecraft.leveling.ledger.title"), panelX + 10, panelY + 8, 0xFFF2F6FF, false);
        context.text(this.font, Component.nullToEmpty(trimToWidth(tr("challengecraft.leveling.ledger.pinned", this.pinnedMilestone != null ? this.pinnedMilestone.title : "-"), panelWidth - 18)), panelX + 10, panelY + 20, 0xFFF7DF8A, false);

        List<Milestone> preview = getPreviewMilestones(4);
        int textY = panelY + 38;
        for (Milestone milestone : preview) {
            int color = milestone == this.nextMilestone ? 0xFFB3D5FF : (milestone.unlocked ? 0xFF8FE2B1 : 0xFFD8DFEC);
            String prefix = milestone == this.currentMilestone
                    ? tr("challengecraft.leveling.ledger.now")
                    : (milestone == this.nextMilestone ? tr("challengecraft.leveling.ledger.up") : tr("challengecraft.leveling.ledger.soon"));
            String line = tr("challengecraft.leveling.ledger.line", prefix, milestone.title, formatXp(Math.max(0L, milestone.requiredXp - this.totalXp)));
            context.text(this.font, Component.nullToEmpty(trimToWidth(line, panelWidth - 18)), panelX + 10, textY, color, false);
            textY += 12;
        }

        if (this.layoutEditMode && this.pinnedMilestone != null) {
            String coords = tr("challengecraft.leveling.ledger.offsets", String.format(Locale.ROOT, "%.1f", this.pinnedMilestone.offsetArtX), String.format(Locale.ROOT, "%.1f", this.pinnedMilestone.offsetArtY));
            context.text(this.font, Component.nullToEmpty(trimToWidth(coords, panelWidth - 18)), panelX + 10, panelY + panelHeight - 24, 0xFFF7DF8A, false);
            context.text(this.font, Component.translatable("challengecraft.leveling.ledger.drag_hint"), panelX + 10, panelY + panelHeight - 13, 0xFFB9C5DA, false);
        } else {
            context.text(this.font, Component.translatable("challengecraft.leveling.ledger.select_hint"), panelX + 10, panelY + panelHeight - 13, 0xFFB9C5DA, false);
        }
    }

    private int getCompletedMilestoneCount() {
        int count = 0;
        for (Milestone milestone : this.milestones) {
            if (milestone.unlocked) {
                count++;
            }
        }
        return count;
    }

    private List<Milestone> getPreviewMilestones(int count) {
        List<Milestone> preview = new ArrayList<>();
        if (this.currentMilestone != null) {
            preview.add(this.currentMilestone);
        }
        for (Milestone milestone : this.milestones) {
            if (preview.size() >= count) {
                break;
            }
            if (!preview.contains(milestone) && milestone.requiredXp >= this.totalXp) {
                preview.add(milestone);
            }
        }
        return preview;
    }

    private int getArtLeft() {
        return this.mapX + (this.mapWidth - this.renderedArtWidth) / 2;
    }

    private int getArtRight() {
        return getArtLeft() + this.renderedArtWidth;
    }

    private void drawDetailCard(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        Milestone focus = this.pinnedMilestone != null ? this.pinnedMilestone : this.currentMilestone;
        if (focus == null) {
            return;
        }

        drawPanel(context, this.detailX, this.detailY, this.detailWidth, this.detailHeight, DETAIL_BG, 0xFF384356, DETAIL_ACCENT);

        String status = focus.unlocked ? tr("challengecraft.leveling.status.unlocked") : (focus == this.nextMilestone ? tr("challengecraft.leveling.status.up_next") : tr("challengecraft.leveling.status.locked"));
        int statusColor = focus.unlocked ? 0xFF7BE0A4 : (focus == this.nextMilestone ? DETAIL_ACCENT : 0xFF9FAAC1);

        context.text(this.font, Component.nullToEmpty(focus.title), this.detailX + 12, this.detailY + 10, 0xFFF4F8FF, false);
        int statusWidth = this.font.width(status);
        drawChip(context, this.detailX + this.detailWidth - statusWidth - 20, this.detailY + 8, statusWidth + 10, 12, 0x44222A34, statusColor);
        context.text(this.font, Component.nullToEmpty(status), this.detailX + this.detailWidth - statusWidth - 15, this.detailY + 10, statusColor, false);

        String requirement = focus.isStar
                ? tr("challengecraft.leveling.requirement.star", formatXp(Math.max(0L, focus.requiredXp - LevelManager.getXpForLevel(LevelManager.MAX_LEVEL))))
                : tr("challengecraft.leveling.requirement.level", formatXp(focus.requiredXp));
        context.text(this.font, Component.nullToEmpty(requirement), this.detailX + 12, this.detailY + 24, 0xFFB9C5DA, false);

        String summary = trimToWidth(focus.summary, this.detailWidth - 24);
        context.text(this.font, Component.nullToEmpty(summary), this.detailX + 12, this.detailY + 37, 0xFFD8DFEC, false);

        int chipX = this.detailX + 12;
        int chipY = this.detailY + 48;
        int availableWidth = this.detailWidth - 24;
        int maxVisible = Math.max(1, availableWidth / 96);
        Reward hoveredReward = null;

        if (focus.rewards.isEmpty()) {
            drawRewardChip(context, chipX, chipY, 118, Component.translatable("challengecraft.leveling.reward.momentum"), null, 0xFF95A3BC, RewardKind.SPECIAL, false);
            drawRewardDescription(context, Component.translatable("challengecraft.leveling.reward.checkpoint"), Component.translatable("challengecraft.leveling.reward.checkpoint.desc"), null);
            return;
        }

        boolean hasOverflow = focus.rewards.size() > maxVisible;
        int visibleRewards = hasOverflow ? Math.max(1, maxVisible - 1) : Math.min(maxVisible, focus.rewards.size());
        for (int i = 0; i < visibleRewards; i++) {
            Reward reward = focus.rewards.get(i);
            int rewardX = chipX + i * 96;
            boolean hovered = isInside(mouseX, mouseY, rewardX, chipY, 90, 26);
            drawRewardChip(context, rewardX, chipY, 90, reward.name, reward, reward.accentColor, reward.kind, hovered);
            if (hovered) {
                hoveredReward = reward;
            }
        }

        if (hasOverflow) {
            int remaining = focus.rewards.size() - visibleRewards;
            int moreX = chipX + visibleRewards * 96;
            boolean hoveredMore = isInside(mouseX, mouseY, moreX, chipY, 74, 26);
            drawRewardChip(context, moreX, chipY, 74, Component.translatable("challengecraft.leveling.reward.more", remaining), null, 0xFFAAB4C8, RewardKind.SPECIAL, hoveredMore);
        }

        if (hoveredReward != null) {
            drawRewardDescription(context, hoveredReward.name, hoveredReward.description, hoveredReward);
        } else if (focus.rewards.size() == 1) {
            Reward reward = focus.rewards.get(0);
            drawRewardDescription(context, reward.name, reward.description, reward);
        } else {
            drawRewardDescription(context, Component.translatable("challengecraft.leveling.reward.inspector"), Component.translatable("challengecraft.leveling.reward.inspector.desc"), null);
        }
    }

    private void drawRewardChip(GuiGraphicsExtractor context, int x, int y, int width, Component label, Reward reward, int accent, RewardKind kind, boolean hovered) {
        int fill = hovered ? 0xCC1B2534 : 0xAA101722;
        int border = hovered ? 0xFFE2EDF9 : 0xFF334155;
        int drawAccent = hovered ? 0xFFFFFFFF : accent;
        drawPanel(context, x, y, width, 26, fill, border, drawAccent);
        if (reward != null && reward.shouldDrawItem()) {
            context.item(reward.icon(), x + 4, y + 5);
        } else {
            drawGem(context, x + 11, y + 13, drawAccent);
        }

        int textX = x + 24;
        String trimmed = trimToWidth(label.getString(), width - 30);
        context.text(this.font, Component.nullToEmpty(trimmed), textX, y + 9, kind == RewardKind.PERK ? 0xFFFFEAA6 : 0xFFDCE7F8, false);
    }

    private void drawRewardDescription(GuiGraphicsExtractor context, Component title, Component description, Reward reward) {
        int boxX = this.detailX + 12;
        int boxY = this.detailY + this.detailHeight - 30;
        int boxWidth = this.detailWidth - 24;
        int boxHeight = 26;
        int accent = reward != null ? reward.accentColor : 0xFF95A3BC;

        drawPanel(context, boxX, boxY, boxWidth, boxHeight, 0xAA101722, 0xFF334155, accent);
        context.text(this.font, Component.nullToEmpty(trimToWidth(title.getString(), boxWidth - 16)), boxX + 8, boxY + 4, 0xFFF2F6FF, false);
        drawWrappedLines(context, description, boxX + 8, boxY + 13, boxWidth - 16, 0xFFB9C5DA, 2);
    }

    private void drawBar(GuiGraphicsExtractor context, int x, int y, int width, int height, float progress) {
        context.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF060A10);
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        context.fill(x, y, x + width, y + height, 0xFF162231);
        int fill = Math.max(0, (int) ((width - 2) * progress));
        if (fill > 0) {
            context.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, PROGRESS_FILL);
        }
        context.outline(x - 1, y - 1, width + 2, height + 2, 0xFF6A7991);
    }

    private void drawFrame(GuiGraphicsExtractor context, int x, int y, int width, int height, int fill, int border, int accent) {
        context.fill(x + 2, y, x + width - 2, y + height, border);
        context.fill(x, y + 2, x + width, y + height - 2, border);
        context.fill(x + 5, y + 5, x + width - 5, y + height - 5, fill);
        context.fill(x + 8, y + 2, x + width - 8, y + 4, accent);
        context.fill(x + 2, y + 8, x + 4, y + height - 8, accent);
        context.fill(x + width - 4, y + 8, x + width - 2, y + height - 8, accent);
        context.fill(x + 8, y + height - 4, x + width - 8, y + height - 2, accent);
    }

    private void drawPanel(GuiGraphicsExtractor context, int x, int y, int width, int height, int fill, int border, int accent) {
        context.fill(x + 1, y, x + width - 1, y + height, border);
        context.fill(x, y + 1, x + width, y + height - 1, border);
        context.fill(x + 3, y + 3, x + width - 3, y + height - 3, fill);
        context.fill(x + 5, y + 1, x + width - 5, y + 2, accent);
        context.fill(x + 1, y + 5, x + 2, y + height - 5, accent);
        context.fill(x + width - 2, y + 5, x + width - 1, y + height - 5, accent);
    }

    private void drawChip(GuiGraphicsExtractor context, int x, int y, int width, int height, int fill, int accent) {
        context.fill(x + 1, y, x + width - 1, y + height, accent);
        context.fill(x, y + 1, x + width, y + height - 1, accent);
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
    }

    private void drawGem(GuiGraphicsExtractor context, int centerX, int centerY, int color) {
        int shadow = darken(color, 0.45f);
        context.fill(centerX - 1, centerY - 4, centerX + 1, centerY - 2, shadow);
        context.fill(centerX - 3, centerY - 2, centerX + 3, centerY, color);
        context.fill(centerX - 2, centerY, centerX + 2, centerY + 3, color);
        context.fill(centerX - 1, centerY + 3, centerX + 1, centerY + 5, shadow);
    }

    private static int darken(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * factor);
        int g = (int) (((color >>> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String trimToWidth(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(8, width - this.font.width("..."))) + "...";
    }

    private void drawWrappedLines(GuiGraphicsExtractor context, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        int lineCount = Math.min(maxLines, lines.size());
        for (int i = 0; i < lineCount; i++) {
            context.text(this.font, lines.get(i), x, y + i * (this.font.lineHeight - 1), color, false);
        }
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String formatXp(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    private enum RewardKind {
        CHALLENGE,
        PERK,
        COLOR,
        SPECIAL
    }

    private static final class Reward {
        private final Component name;
        private final Component description;
        /**
         * Challenge or perk id whose icon this reward shows, or 0 for none.
         *
         * <p>An id, not an {@link ItemStack}, because the stack cannot always be built when the
         * rewards are assembled. {@code new ItemStack(item)} reads data components off the item's
         * registry holder and throws "Components not bound yet" until the client has loaded a
         * datapack — which on a fresh launch has not happened at the title screen. Snapshotting the
         * result there stored {@link ItemStack#EMPTY} permanently, so the Progress screen showed no
         * icons at all until the player had opened world creation once (that flow binds the
         * components) and come back. Resolving through {@link ChallengeIconProvider} at draw time
         * costs a cached map lookup and fills itself in the moment binding happens.
         */
        private final int iconId;
        private final RewardKind kind;
        private final int accentColor;

        private Reward(Component name, Component description, int iconId, RewardKind kind, int accentColor) {
            this.name = name;
            this.description = description;
            this.iconId = iconId;
            this.kind = kind;
            this.accentColor = accentColor;
        }

        private ItemStack icon() {
            return this.iconId == 0 ? ItemStack.EMPTY : ChallengeIconProvider.getIcon(this.iconId);
        }

        private static Reward challenge(Component name, Component description, int challengeId) {
            return new Reward(name, description, challengeId, RewardKind.CHALLENGE, 0xFF87C7FF + challengeId % 2);
        }

        private static Reward perk(Component name, Component description, int perkId) {
            return new Reward(name, description, perkId, RewardKind.PERK, 0xFFF1D16A + perkId % 2);
        }

        private static Reward color(Component name, Component description, int color) {
            return new Reward(name, description, 0, RewardKind.COLOR, color);
        }

        private static Reward special(Component name, Component description, int accentColor) {
            return new Reward(name, description, 0, RewardKind.SPECIAL, accentColor);
        }

        private boolean shouldDrawItem() {
            // Asks whether an icon can be drawn RIGHT NOW, so the coloured gem stays as the
            // fallback while item components are still unbound. Testing only the id drew an empty
            // stack instead — nothing at all, which is worse than a placeholder.
            return this.iconId != 0
                    && (this.kind == RewardKind.CHALLENGE || this.kind == RewardKind.PERK)
                    && !icon().isEmpty();
        }
    }

    private static final class Milestone {
        private final int index;
        private final int value;
        private final boolean isStar;
        private final long requiredXp;
        private final List<Reward> rewards;

        private String title;
        private String summary;
        private boolean unlocked;
        private int baseAnchorX;
        private int baseAnchorY;
        private int anchorX;
        private int anchorY;
        private int cardWidth;
        private int cardHeight;
        private float offsetArtX;
        private float offsetArtY;

        private Milestone(int index, int value, boolean isStar, long requiredXp, List<Reward> rewards) {
            this.index = index;
            this.value = value;
            this.isStar = isStar;
            this.requiredXp = requiredXp;
            this.rewards = rewards;
        }

        private int left() {
            return this.anchorX - this.cardWidth / 2;
        }

        private int top() {
            return this.anchorY - this.cardHeight / 2;
        }

        private int centerX() {
            return this.anchorX;
        }

        private int centerY() {
            return this.anchorY;
        }

        private String layoutKey() {
            return this.isStar ? "star_" + this.value : "level_" + this.value;
        }
    }

    private static int getLegacyEntryHeight(Milestone milestone) {
        int rewardRows = Math.max(1, milestone.rewards.size());
        return Math.max(56, 34 + rewardRows * 22);
    }

    private final class LayoutModeWidget extends AbstractWidget {
        private LayoutModeWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            boolean hovering = isHovered();
            int outerFill = hovering ? 0xEE1B2431 : 0xDD131A24;
            drawPanel(context, getX(), getY(), getWidth(), getHeight(), outerFill, 0xFF41516A, 0xFF9CB7E8);

            int segmentWidth = (getWidth() - 6) / 2;
            int leftX = getX() + 3;
            int rightX = leftX + segmentWidth;
            boolean journeySelected = !legacyLayoutMode;

            drawModeSegment(context, leftX, getY() + 3, segmentWidth, getHeight() - 6, Component.translatable("challengecraft.leveling.mode.journey"), journeySelected, hovering && mouseX < rightX);
            drawModeSegment(context, rightX, getY() + 3, segmentWidth, getHeight() - 6, Component.translatable("challengecraft.leveling.mode.legacy"), !journeySelected, hovering && mouseX >= rightX);
        }

        private void drawModeSegment(GuiGraphicsExtractor context, int x, int y, int width, int height, Component label, boolean selected, boolean hovered) {
            int fill = selected ? 0xFF314A38 : (hovered ? 0xCC243345 : 0x99182230);
            int border = selected ? 0xFF74D7A0 : 0xFF506179;
            int accent = selected ? 0xFFBFF3D0 : 0xFF9CB7E8;
            drawPanel(context, x, y, width, height, fill, border, accent);
            int color = selected ? 0xFFF6FFF9 : 0xFFD6E1F4;
            context.centeredText(font, label, x + width / 2, y + 4, color);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!this.active || !this.visible || event.button() != 0 || !isMouseOver(event.x(), event.y())) {
                return false;
            }

            boolean clickLegacy = event.x() >= this.getX() + this.getWidth() / 2.0;
            if (clickLegacy != legacyLayoutMode) {
                toggleLayoutMode();
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }
    }

    private final class LegacyMilestoneEntryWidget extends AbstractWidget {
        private final Milestone milestone;

        private LegacyMilestoneEntryWidget(int x, int y, int width, Milestone milestone) {
            super(x, y, width, getLegacyEntryHeight(milestone), Component.empty());
            this.milestone = milestone;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            int fill = this.milestone.unlocked ? 0x8E193126 : 0x8E292F39;
            int accent = this.milestone.unlocked ? 0xFF74D7A0 : 0xFF8BA6D8;
            if (this.milestone == nextMilestone) {
                fill = 0x8E233244;
                accent = 0xFFE3B35A;
            }
            if (isHovered()) {
                fill = this.milestone.unlocked ? 0xB1234434 : 0xB1353C48;
            }

            drawPanel(context, getX(), getY(), getWidth(), getHeight(), fill, 0xFF3C495C, accent);

            String label = this.milestone.isStar ? tr("challengecraft.leveling.infinity_star_value", this.milestone.value) : this.milestone.title;
            context.text(font, Component.nullToEmpty(label), getX() + 10, getY() + 8, 0xFFF4F8FF, false);

            String state = this.milestone.unlocked ? tr("challengecraft.leveling.state.unlocked") : (this.milestone == nextMilestone ? tr("challengecraft.leveling.state.up_next") : tr("challengecraft.leveling.state.locked"));
            int stateColor = this.milestone.unlocked ? 0xFF7BE0A4 : (this.milestone == nextMilestone ? 0xFFE3B35A : 0xFFB3C0D3);
            int chipWidth = font.width(state) + 10;
            drawChip(context, getX() + getWidth() - chipWidth - 8, getY() + 6, chipWidth, 12, 0x55222A34, stateColor);
            context.text(font, Component.nullToEmpty(state), getX() + getWidth() - chipWidth - 3, getY() + 8, stateColor, false);

            String summary = trimToWidth(this.milestone.summary, getWidth() - 20);
            context.text(font, Component.nullToEmpty(summary), getX() + 10, getY() + 20, 0xFFB9C5DA, false);

            int rewardY = getY() + 33;
            if (this.milestone.rewards.isEmpty()) {
                context.text(font, Component.translatable("challengecraft.leveling.reward.momentum_checkpoint"), getX() + 26, rewardY, 0xFFD8DFEC, false);
                drawGem(context, getX() + 13, rewardY + 5, 0xFF95A3BC);
            } else {
                for (Reward reward : this.milestone.rewards) {
                    int iconX = getX() + 10;
                    int iconY = rewardY - 2;
                    if (reward.shouldDrawItem()) {
                        context.item(reward.icon(), iconX, iconY);
                    } else {
                        drawGem(context, iconX + 7, rewardY + 5, reward.accentColor);
                    }

                    int textX = getX() + 30;
                    int maxWidth = getWidth() - 38;
                    String rewardName = trimToWidth(reward.name.getString(), maxWidth);
                    context.text(font, Component.nullToEmpty(rewardName), textX, rewardY, reward.kind == RewardKind.PERK ? 0xFFFFEAA6 : 0xFFE8EEF9, false);

                    String rewardDesc = trimToWidth(reward.description.getString(), maxWidth);
                    context.text(font, Component.nullToEmpty(rewardDesc), textX, rewardY + 10, 0xFF9FB0CA, false);
                    rewardY += 22;
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }
    }

    private final class JourneyPanel extends AbstractScrollArea {
        /** Pixels scrolled per wheel notch; also handed to the scrollbar settings below. */
        private static final int SCROLL_RATE = 24;

        private List<Milestone> milestones;
        private final Consumer<Milestone> hoverConsumer;
        private final Consumer<Milestone> selectConsumer;
        private long playerXp;

        private Milestone hovered;
        private Milestone pinnedMilestone;
        private ProgressMarker progressMarker;
        private final int visibleArtSourceHeight;
        private final int visibleArtHeight;
        private int contentHeight;

        private JourneyPanel(int x, int y, int width, int height, List<Milestone> milestones, long playerXp,
                             Consumer<Milestone> hoverConsumer, Consumer<Milestone> selectConsumer) {
            // 26.2 made the scrollbar look/rate explicit constructor state; defaultSettings(int)
            // takes the SCROLL RATE, not the scrollbar width.
            super(x, y, width, height, Component.empty(), AbstractScrollArea.defaultSettings(SCROLL_RATE));
            this.milestones = milestones;
            this.playerXp = playerXp;
            this.hoverConsumer = hoverConsumer;
            this.selectConsumer = selectConsumer;
            this.visibleArtSourceHeight = showInfinityTrack ? JOURNEY_TEXTURE_HEIGHT : END_ART_BOTTOM_Y;
            this.visibleArtHeight = Math.round(this.visibleArtSourceHeight * artScale);
            recomputeGeometry();
        }

        private void refreshMilestones(List<Milestone> milestones, long playerXp) {
            this.milestones = milestones;
            this.playerXp = playerXp;
            this.hovered = null;
            recomputeGeometry();
        }

        private void recomputeGeometry() {
            int maxBottom = this.visibleArtHeight;
            for (Milestone milestone : this.milestones) {
                maxBottom = Math.max(maxBottom, milestone.centerY() + Math.round(140 * artScale));
            }
            this.contentHeight = Math.max(this.height, maxBottom);
            this.progressMarker = computeProgressMarker();
        }

        private void setPinnedMilestone(Milestone pinnedMilestone) {
            this.pinnedMilestone = pinnedMilestone;
        }

        private void centerOn(Milestone milestone) {
            if (milestone == null) {
                return;
            }
            centerOnY(milestone.centerY());
        }

        private void centerOnY(int y) {
            double target = y - (double) this.height * 0.38;
            this.setScrollAmount(Mth.clamp(target, 0.0, Math.max(0.0, this.maxScrollAmount())));
        }

        private void setScrollRatio(float ratio) {
            this.setScrollAmount(Mth.clamp(this.maxScrollAmount() * Mth.clamp(ratio, 0.0f, 1.0f), 0.0, Math.max(0.0, this.maxScrollAmount())));
        }

        @Override
        protected int contentHeight() {
            return this.contentHeight + 16;
        }

        @Override
        protected double scrollRate() {
            return SCROLL_RATE;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            int scrollY = (int) Math.floor(this.scrollAmount());

            Milestone hoveredNow = this.isMouseOver(mouseX, mouseY) ? findMilestone(mouseX, mouseY, scrollY) : null;
            if (hoveredNow != this.hovered) {
                this.hovered = hoveredNow;
                this.hoverConsumer.accept(hoveredNow);
            }

            context.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
            int artX = this.getX() + (this.width - renderedArtWidth) / 2;
            int artY = this.getY() - scrollY;
            context.blit(
                    // 26.2 takes a RenderPipeline where 1.21.5 took a RenderType factory.
                    RenderPipelines.GUI_TEXTURED,
                    JOURNEY_TEXTURE,
                    artX,
                    artY,
                    0,
                    0,
                    renderedArtWidth,
                    this.visibleArtHeight,
                    JOURNEY_TEXTURE_WIDTH,
                    this.visibleArtSourceHeight,
                    JOURNEY_TEXTURE_WIDTH,
                    JOURNEY_TEXTURE_HEIGHT
            );

            drawSectionRibbon(context, Component.translatable("challengecraft.leveling.ribbon.spawn"), scaleArtY(SPAWN_RIBBON_Y) - scrollY, 0xFF88D38E, true, artX, artX + renderedArtWidth);
            drawSectionRibbon(context, Component.translatable("challengecraft.leveling.ribbon.cavern"), scaleArtY(CAVERN_RIBBON_Y) - scrollY, 0xFF81A5D9, true, artX, artX + renderedArtWidth);
            drawSectionRibbon(context, Component.translatable("challengecraft.leveling.ribbon.nether"), scaleArtY(NETHER_RIBBON_Y) - scrollY, 0xFFE07A62, true, artX, artX + renderedArtWidth);
            drawSectionRibbon(context, Component.translatable("challengecraft.leveling.ribbon.end"), scaleArtY(END_RIBBON_Y) - scrollY, 0xFFC9A6FF, true, artX, artX + renderedArtWidth);
            if (showInfinityTrack) {
                drawSectionRibbon(context, Component.translatable("challengecraft.leveling.ribbon.infinity"), scaleArtY(INFINITY_RIBBON_Y) - scrollY, 0xFF6DE7E1, true, artX, artX + renderedArtWidth);
            }

            for (int i = 0; i < this.milestones.size() - 1; i++) {
                Milestone a = this.milestones.get(i);
                Milestone b = this.milestones.get(i + 1);
                drawSegment(context, a, b, scrollY);
            }

            drawProgressMarker(context, scrollY);

            for (Milestone milestone : this.milestones) {
                drawMilestone(context, milestone, scrollY);
            }

            context.disableScissor();
        }

        private ProgressMarker computeProgressMarker() {
            if (this.milestones.size() == 1) {
                Milestone only = this.milestones.get(0);
                return new ProgressMarker(only.centerX(), only.centerY(), -1, 1.0f);
            }

            if (this.playerXp >= this.milestones.get(this.milestones.size() - 1).requiredXp) {
                Milestone last = this.milestones.get(this.milestones.size() - 1);
                return new ProgressMarker(last.centerX(), last.centerY(), this.milestones.size() - 1, 1.0f);
            }

            for (int i = 0; i < this.milestones.size() - 1; i++) {
                Milestone from = this.milestones.get(i);
                Milestone to = this.milestones.get(i + 1);
                if (this.playerXp < to.requiredXp) {
                    float progress = Mth.clamp((float) (this.playerXp - from.requiredXp) / (float) (to.requiredXp - from.requiredXp), 0.0f, 1.0f);
                    int markerX = Mth.lerpInt(progress, from.centerX(), to.centerX());
                    int markerY = Mth.lerpInt(progress, from.centerY(), to.centerY());
                    return new ProgressMarker(markerX, markerY, i, progress);
                }
            }

            Milestone last = this.milestones.get(this.milestones.size() - 1);
            return new ProgressMarker(last.centerX(), last.centerY(), this.milestones.size() - 1, 1.0f);
        }

        private Milestone findMilestone(double mouseX, double mouseY, int scrollY) {
            for (Milestone milestone : this.milestones) {
                int left = this.getX() + milestone.left();
                int top = this.getY() + milestone.top() - scrollY;
                if (mouseX >= left && mouseX <= left + milestone.cardWidth && mouseY >= top && mouseY <= top + milestone.cardHeight) {
                    return milestone;
                }
            }
            return null;
        }

        private void drawSectionRibbon(GuiGraphicsExtractor context, Component label, int y, int color, boolean rightAligned, int artLeft, int artRight) {
            if (y < -24 || y > this.height + 24) {
                return;
            }
            int width = LevelingScreen.this.font.width(label) + 26;
            int x = rightAligned ? artRight - width - 14 : artLeft + 14;
            int border = darken(color, 0.55f);
            drawPanel(context, x, this.getY() + y - 9, width, 18, 0x88202835, border, color);
            context.text(LevelingScreen.this.font, label, x + 12, this.getY() + y - 4, 0xFFF7FAFF, false);
        }

        private void drawSegment(GuiGraphicsExtractor context, Milestone a, Milestone b, int scrollY) {
            int ax = this.getX() + a.centerX();
            int ay = this.getY() + a.centerY() - scrollY;
            int bx = this.getX() + b.centerX();
            int by = this.getY() + b.centerY() - scrollY;

            if ((ay < this.getY() - 40 && by < this.getY() - 40) || (ay > this.getBottom() + 40 && by > this.getBottom() + 40)) {
                return;
            }

            drawPixelLine(context, ax, ay, bx, by, 5, 0xCC10141C);

            if (this.progressMarker.segmentIndex < 0) {
                drawPixelLine(context, ax, ay, bx, by, 3, FUTURE_PATH);
                return;
            }

            if (this.progressMarker.segmentIndex > a.index) {
                drawPixelLine(context, ax, ay, bx, by, 3, COMPLETE_PATH);
                return;
            }

            if (this.progressMarker.segmentIndex == a.index) {
                drawPixelLine(context, ax, ay, this.getX() + this.progressMarker.x, this.getY() + this.progressMarker.y - scrollY, 3, CURRENT_PATH);
                drawPixelLine(context, this.getX() + this.progressMarker.x, this.getY() + this.progressMarker.y - scrollY, bx, by, 3, FUTURE_PATH);
                return;
            }

            drawPixelLine(context, ax, ay, bx, by, 3, FUTURE_PATH);
        }

        private void drawProgressMarker(GuiGraphicsExtractor context, int scrollY) {
            int centerX = this.getX() + this.progressMarker.x;
            int centerY = this.getY() + this.progressMarker.y - scrollY;
            if (centerY < this.getY() - 18 || centerY > this.getBottom() + 18) {
                return;
            }

            float pulse = 0.72f + 0.28f * (float) Math.sin(System.currentTimeMillis() / 170.0);
            int glow = (int) (50 + pulse * 90);
            int glowColor = (glow << 24) | 0x00FFF4C7;
            context.fill(centerX - 8, centerY - 8, centerX + 8, centerY + 8, glowColor);
            context.fill(centerX - 4, centerY - 4, centerX + 4, centerY + 4, 0xFFF7F0A2);
            context.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFF2D2511);
        }

        private void drawMilestone(GuiGraphicsExtractor context, Milestone milestone, int scrollY) {
            int x = this.getX() + milestone.left();
            int y = this.getY() + milestone.top() - scrollY;
            if (y + milestone.cardHeight < this.getY() - 18 || y > this.getBottom() + 18) {
                return;
            }

            boolean isHover = milestone == this.hovered;
            boolean isPinned = milestone == this.pinnedMilestone;
            boolean isCurrent = milestone == currentMilestone;
            boolean isNext = milestone == nextMilestone;

            int border = milestone.unlocked ? 0xFF4EA97E : 0xFF67758E;
            int fill = milestone.unlocked ? 0xCC173126 : 0xC9212733;
            int accent = milestone.isStar ? 0xFFE3B35A : 0xFF84B2F5;

            if (isCurrent) {
                border = CURRENT_PATH;
                accent = 0xFFF3D88A;
            } else if (isNext) {
                border = 0xFFB3D5FF;
                accent = 0xFFB3D5FF;
            }

            if (isHover || isPinned) {
                context.fill(x - 4, y - 4, x + milestone.cardWidth + 4, y + milestone.cardHeight + 4, 0x55384961);
            }

            if (isPinned) {
                context.fill(x - 5, y - 5, x + milestone.cardWidth + 5, y - 3, 0xFFF7DF8A);
                context.fill(x - 5, y + milestone.cardHeight + 3, x + milestone.cardWidth + 5, y + milestone.cardHeight + 5, 0xFFF7DF8A);
                context.fill(x - 5, y - 5, x - 3, y + milestone.cardHeight + 5, 0xFFF7DF8A);
                context.fill(x + milestone.cardWidth + 3, y - 5, x + milestone.cardWidth + 5, y + milestone.cardHeight + 5, 0xFFF7DF8A);
                drawChip(context, x + 8, y - 12, 56, 12, 0xCC271B0E, 0xFFF7DF8A);
                context.text(LevelingScreen.this.font, Component.translatable("challengecraft.leveling.selected"), x + 13, y - 10, 0xFFF9F4D1, false);
            }

            drawPanel(context, x, y, milestone.cardWidth, milestone.cardHeight, fill, border, accent);

            String header = milestone.isStar ? tr("challengecraft.leveling.header.star", milestone.value) : tr("challengecraft.leveling.header.level", milestone.value);
            context.text(LevelingScreen.this.font, Component.nullToEmpty(header), x + 8, y + 7, milestone.isStar ? 0xFFFFE39B : 0xFFE4EEFF, false);

            String state = milestone.unlocked ? tr("challengecraft.leveling.state.cleared") : (isNext ? tr("challengecraft.leveling.state.next") : tr("challengecraft.leveling.state.locked"));
            int stateColor = milestone.unlocked ? 0xFF8FE2B1 : (isNext ? 0xFFB3D5FF : 0xFFA1ADC2);
            int stateWidth = LevelingScreen.this.font.width(state);
            context.text(LevelingScreen.this.font, Component.nullToEmpty(state), x + milestone.cardWidth - stateWidth - 8, y + 7, stateColor, false);

            int rewardY = y + (milestone.isStar && milestone.rewards.isEmpty() ? 18 : 24);
            if (milestone.rewards.isEmpty()) {
                drawGem(context, x + 12, rewardY, accent);
                context.text(LevelingScreen.this.font, Component.translatable("challengecraft.leveling.reward.checkpoint"), x + 22, rewardY - 4, 0xFFB8C6DD, false);
                return;
            }

            int iconX = x + 8;
            int visibleRewards = Math.min(3, milestone.rewards.size());
            for (int i = 0; i < visibleRewards; i++) {
                Reward reward = milestone.rewards.get(i);
                if (reward.shouldDrawItem()) {
                    context.item(reward.icon(), iconX + i * 18, rewardY);
                } else {
                    drawGem(context, iconX + 8 + i * 18, rewardY + 8, reward.accentColor);
                }
            }

            String rewardLabel = milestone.rewards.size() == 1
                    ? milestone.rewards.get(0).name.getString()
                    : tr("challengecraft.leveling.reward.unlocks", milestone.rewards.size());
            rewardLabel = trimToWidth(rewardLabel, milestone.cardWidth - 70);
            context.text(LevelingScreen.this.font, Component.nullToEmpty(rewardLabel), x + 64, rewardY + 5, 0xFFDCE7F8, false);
        }

        private void drawPixelLine(GuiGraphicsExtractor context, int startX, int startY, int endX, int endY, int size, int color) {
            int steps = Math.max(Math.abs(endX - startX), Math.abs(endY - startY)) / 3 + 1;
            for (int i = 0; i <= steps; i++) {
                float t = steps == 0 ? 0.0f : (float) i / (float) steps;
                int x = Mth.lerpInt(t, startX, endX);
                int y = Mth.lerpInt(t, startY, endY);
                context.fill(x - size / 2, y - size / 2, x + size / 2 + 1, y + size / 2 + 1, color);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!this.visible) {
                return false;
            }

            Milestone clicked = findMilestone(event.x(), event.y(), (int) Math.floor(this.scrollAmount()));
            if (layoutEditMode && clicked != null) {
                pinnedMilestone = clicked;
                selectConsumer.accept(clicked);
                if (event.button() == 1) {
                    resetMilestoneOffset(clicked);
                    recomputeGeometry();
                    return true;
                }
                if (event.button() == 0) {
                    draggedMilestone = clicked;
                    return true;
                }
            }

            if (this.updateScrolling(event)) {
                return true;
            }

            if (!this.isMouseOver(event.x(), event.y())) {
                return false;
            }

            if (clicked != null) {
                this.selectConsumer.accept(clicked);
                return true;
            }

            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (!this.visible) {
                return false;
            }
            if (draggedMilestone != null && event.button() == 0) {
                saveLayoutOffset(draggedMilestone);
                draggedMilestone = null;
                recomputeGeometry();
                return true;
            }
            this.onRelease(event);
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            if (!this.visible) {
                return false;
            }
            if (layoutEditMode && draggedMilestone != null && event.button() == 0) {
                draggedMilestone.offsetArtX += (float) (deltaX / artWidthScale);
                draggedMilestone.offsetArtY += (float) (deltaY / artScale);
                applyManualOffset(draggedMilestone);
                recomputeGeometry();
                return true;
            }
            return super.mouseDragged(event, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (!this.visible || !this.isMouseOver(mouseX, mouseY)) {
                return false;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }
    }

    private record ProgressMarker(int x, int y, int segmentIndex, float segmentProgress) {
    }
}
