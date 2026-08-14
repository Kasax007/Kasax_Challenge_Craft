package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.network.LockoutBingoActionPacket;
import net.kasax.challengecraft.network.LockoutBingoSyncPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
/** Client board view for claimed tiles, team colors, and match state. */
public class LockoutBingoBoardScreen extends Screen {
    private static final int GRID_SIZE = 5;
    private static final int TILE_SIZE = 54;
    private static final int BOARD_TILE_COUNT = 25;
    private static final int SIDE_PANEL_WIDTH = 150;
    private static final int BOARD_TOP = 58;

    /**
     * The board is a fixed-size design: 6 + 150 panel + 12 gap + 270 board + 12 gap + 150 panel + 6.
     * It is drawn in this coordinate space and uniformly scaled to fit, because at GUI scale 4 on a
     * 1080p screen the usable area is only 480x270 — far too small for the natural 606x342 layout,
     * which previously ran off the bottom of the screen and overlapped the side panels.
     */
    private static final int DESIGN_WIDTH = 6 + SIDE_PANEL_WIDTH + 12 + (GRID_SIZE * TILE_SIZE) + 12 + SIDE_PANEL_WIDTH + 6;
    private static final int DESIGN_HEIGHT = BOARD_TOP + (GRID_SIZE * TILE_SIZE) + 14;
    /** Screen-space strip reserved at the bottom for the (unscaled) team button. */
    private static final int BOTTOM_BAR = 34;

    private CraftButton teamButton;
    private List<Component> pendingTooltip;
    private float layoutScale = 1f;
    private float layoutOffsetX;
    private float layoutOffsetY;

    public LockoutBingoBoardScreen() {
        super(Component.translatable("challengecraft.worldcreate.challenge40"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.REQUEST_SYNC, -1));
        teamButton = addRenderableWidget(new CraftButton(this.width / 2 - 70, this.height - 28, 140, 20,
                Component.translatable("challengecraft.lockout.board.open_team_screen"), CraftButton.Style.NEUTRAL,
                button -> this.minecraft.setScreenAndShow(new LockoutBingoTeamScreen())));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (pendingTooltip != null && !pendingTooltip.isEmpty()) {
            // Deferred until after the widgets are extracted, and positioned in raw screen space —
            // this runs outside the design-space transform applied in extractBackground.
            context.setComponentTooltipForNextFrame(this.font, pendingTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        this.pendingTooltip = null;

        int usableHeight = Math.max(1, this.height - BOTTOM_BAR);
        this.layoutScale = Math.min(1f, Math.min(this.width / (float) DESIGN_WIDTH,
                usableHeight / (float) DESIGN_HEIGHT));
        this.layoutOffsetX = (this.width - DESIGN_WIDTH * this.layoutScale) / 2f;
        this.layoutOffsetY = Math.max(0f, (usableHeight - DESIGN_HEIGHT * this.layoutScale) / 2f);

        // Hit testing happens in design space, so convert the cursor into it.
        int localMouseX = Math.round((mouseX - this.layoutOffsetX) / this.layoutScale);
        int localMouseY = Math.round((mouseY - this.layoutOffsetY) / this.layoutScale);

        // 26.2's GUI matrix is a 2D affine stack, so the old no-op z arguments simply drop away.
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(this.layoutOffsetX, this.layoutOffsetY);
        matrices.scale(this.layoutScale, this.layoutScale);
        try {
            drawBoard(context, localMouseX, localMouseY);
        } finally {
            matrices.popMatrix();
        }
    }

    /** Draws the whole board in fixed design coordinates; the caller applies the fit scale. */
    private void drawBoard(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        LockoutBingoSyncPacket state = LockoutBingoClientState.get();
        UUID playerUuid = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        LockoutBingoTeam localTeam = playerUuid != null ? LockoutBingoClientState.getTeam(playerUuid) : null;
        LockoutBingoTeam winner = LockoutBingoTeam.fromOrdinal(state.winnerTeamId());
        LockoutBingoTeam targetTeam = localTeam != null ? localTeam : LockoutBingoClientState.getLeadingTeam();
        int clinchTarget = LockoutBingoClientState.getClinchTarget(targetTeam);
        Component clinchText = clinchTarget > 25
                ? Component.translatable("challengecraft.placeholder.pending")
                : Component.nullToEmpty(Integer.toString(clinchTarget));

        int boardW = GRID_SIZE * TILE_SIZE;
        int boardLeft = DESIGN_WIDTH / 2 - boardW / 2;
        int boardTop = BOARD_TOP;

        // ---- Title ----
        context.centeredText(this.font, this.title, DESIGN_WIDTH / 2, 16, CraftUI.GOLD);

        // ---- Left info panel ---- (design space always has room; no clamping needed)
        int infoX = boardLeft - SIDE_PANEL_WIDTH - 12;
        int infoY = boardTop;
        int infoH = boardW;
        CraftUI.panelFloat(context, infoX, infoY, SIDE_PANEL_WIDTH, infoH, CraftUI.INFO);
        CraftUI.sectionHeader(context, this.font, Component.translatable("challengecraft.lockout.board.info_header"),
                infoX + 8, infoY + 8, SIDE_PANEL_WIDTH - 16, CraftUI.INFO);
        int infoRowY = infoY + 8 + this.font.lineHeight + 8;
        infoRowY = infoRow(context, infoX + 8, infoRowY, Component.translatable("challengecraft.lockout.board.needed_to_win", clinchText), CraftUI.GOLD);
        infoRowY = infoRow(context, infoX + 8, infoRowY, Component.translatable("challengecraft.lockout.board.time", formatTicks(LockoutBingoClientState.getElapsedTicks())), CraftUI.INFO);
        Component teamValue = localTeam == null ? Component.translatable("challengecraft.lockout.team.none") : localTeam.displayName();
        infoRowY = infoRow(context, infoX + 8, infoRowY, Component.translatable("challengecraft.lockout.board.current_team", teamValue),
                localTeam == null ? CraftUI.TEXT_MUTED : localTeam.color());
        if (winner != null) {
            infoRow(context, infoX + 8, infoRowY, Component.translatable("challengecraft.lockout.board.winner", winner.displayName()), winner.color());
        } else if (LockoutBingoClientState.isDraw()) {
            infoRow(context, infoX + 8, infoRowY, Component.translatable("challengecraft.lockout.board.draw"), CraftUI.WARNING);
        }

        // ---- Right scoreboard panel ----
        int scoreX = boardLeft + boardW + 12;
        CraftUI.panelFloat(context, scoreX, infoY, SIDE_PANEL_WIDTH, infoH, CraftUI.GOLD);
        CraftUI.sectionHeader(context, this.font, Component.translatable("challengecraft.lockout.board.scores_header"),
                scoreX + 8, infoY + 8, SIDE_PANEL_WIDTH - 16, CraftUI.GOLD);
        int scoreRowY = infoY + 8 + this.font.lineHeight + 10;
        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            // Colour swatch + name + score.
            context.fill(scoreX + 8, scoreRowY, scoreX + 8 + 8, scoreRowY + 8, team.color());
            context.outline(scoreX + 8, scoreRowY, 8, 8, CraftUI.darken(team.color(), 0.6f));
            context.text(this.font, team.displayName(), scoreX + 22, scoreRowY, team.color(), false);
            Component score = Component.nullToEmpty(Integer.toString(LockoutBingoClientState.getScore(team)));
            context.text(this.font, score, scoreX + SIDE_PANEL_WIDTH - this.font.width(score) - 10, scoreRowY, CraftUI.TEXT_PRIMARY, false);
            scoreRowY += 16;
        }

        teamButton.visible = !state.started();
        teamButton.active = !state.started();

        // ---- Board ----
        CraftUI.panelFloat(context, boardLeft - 6, boardTop - 6, boardW + 12, boardW + 12, CraftUI.ACCENT_HAIRLINE);

        if (state.boardGoalIds().isEmpty()) {
            context.centeredText(this.font, Component.translatable("challengecraft.lockout.board.waiting"),
                    DESIGN_WIDTH / 2, boardTop + boardW / 2, CraftUI.TEXT_SECONDARY);
            return;
        }

        LockoutBingoGoal hoveredGoal = null;
        Integer hoveredIndex = null;
        for (int index = 0; index < state.boardGoalIds().size() && index < BOARD_TILE_COUNT; index++) {
            int column = index % GRID_SIZE;
            int row = index / GRID_SIZE;
            int x = boardLeft + column * TILE_SIZE;
            int y = boardTop + row * TILE_SIZE;

            LockoutBingoGoal goal = LockoutBingoGoalPool.byId(state.boardGoalIds().get(index));
            LockoutBingoTeam claimedTeam = LockoutBingoTeam.fromOrdinal(index < state.claimedTeams().size() ? state.claimedTeams().get(index) : -1);
            String claimant = claimedTeam != null && index < state.claimedByNames().size() ? state.claimedByNames().get(index) : "";
            drawTile(context, x, y, mouseX, mouseY, goal, claimedTeam, claimant);

            if (mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= y && mouseY < y + TILE_SIZE) {
                hoveredGoal = goal;
                hoveredIndex = index;
            }
        }

        if (hoveredGoal != null && hoveredIndex != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hoveredGoal.title());
            tooltip.add(hoveredGoal.description());
            tooltip.add(hoveredGoal.condition());

            LockoutBingoTeam claimedTeam = LockoutBingoTeam.fromOrdinal(hoveredIndex < state.claimedTeams().size() ? state.claimedTeams().get(hoveredIndex) : -1);
            if (claimedTeam != null) {
                String name = hoveredIndex < state.claimedByNames().size() ? state.claimedByNames().get(hoveredIndex) : "";
                tooltip.add(Component.translatable("challengecraft.lockout.board.claimed_by", claimedTeam.displayName(), Component.nullToEmpty(name)));
            }
            this.pendingTooltip = tooltip;
        }
    }

    private int infoRow(GuiGraphicsExtractor context, int x, int y, Component text, int color) {
        context.text(this.font, text, x, y, color, false);
        return y + this.font.lineHeight + 5;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawTile(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY,
                          LockoutBingoGoal goal, LockoutBingoTeam claimedTeam, String claimant) {
        boolean hovered = mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= y && mouseY < y + TILE_SIZE;

        int fill;
        int border;
        int accent;
        if (claimedTeam != null) {
            fill = CraftUI.mix(0xE0121620, claimedTeam.color(), 0.32f);
            border = claimedTeam.color();
            accent = claimedTeam.color();
        } else {
            fill = CraftUI.CardState.IDLE.fill;
            border = hovered ? CraftUI.TEXT_PRIMARY : CraftUI.BORDER;
            accent = hovered ? CraftUI.ACCENT_HAIRLINE : CraftUI.applyAlpha(CraftUI.ACCENT_HAIRLINE, 0.5f);
        }
        CraftUI.panel(context, x, y, TILE_SIZE, TILE_SIZE, fill, border, accent);

        if (claimedTeam != null) {
            // Inner glow so claimed tiles read at a glance.
            context.fill(x + 3, y + 3, x + TILE_SIZE - 3, y + TILE_SIZE - 3, CraftUI.applyAlpha(claimedTeam.color(), 0.14f));
        }

        if (goal == null) {
            context.centeredText(this.font, Component.translatable("challengecraft.placeholder.unknown"),
                    x + TILE_SIZE / 2, y + 22, CraftUI.TEXT_SECONDARY);
            return;
        }

        ItemStack icon = goal.createIconStack();
        context.item(icon, x + TILE_SIZE / 2 - 8, y + 6);

        String label = CraftUI.trimToWidth(this.font, goal.title().getString(), TILE_SIZE - 8);
        context.centeredText(this.font, Component.nullToEmpty(label), x + TILE_SIZE / 2, y + 28, CraftUI.TEXT_PRIMARY);

        if (claimedTeam != null && !claimant.isEmpty()) {
            // Small claimant-initial chip in the corner.
            String initial = claimant.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            CraftUI.chip(context, x + 3, y + 3, 11, 11, CraftUI.applyAlpha(claimedTeam.color(), 0.85f), claimedTeam.color());
            context.text(this.font, Component.nullToEmpty(initial), x + 6, y + 5, 0xFF10151F, false);
        }
    }

    private String formatTicks(long ticks) {
        long totalSeconds = ticks / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
