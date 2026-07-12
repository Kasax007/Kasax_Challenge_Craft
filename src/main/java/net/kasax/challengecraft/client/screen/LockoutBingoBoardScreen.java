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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

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

    private CraftButton teamButton;
    private List<Text> pendingTooltip;

    public LockoutBingoBoardScreen() {
        super(Text.translatable("challengecraft.worldcreate.challenge40"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.REQUEST_SYNC, -1));
        teamButton = addDrawableChild(new CraftButton(this.width / 2 - 70, this.height - 28, 140, 20,
                Text.translatable("challengecraft.lockout.board.open_team_screen"), CraftButton.Style.NEUTRAL,
                button -> this.client.setScreen(new LockoutBingoTeamScreen())));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (pendingTooltip != null && !pendingTooltip.isEmpty()) {
            context.drawTooltip(this.textRenderer, pendingTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        this.pendingTooltip = null;

        LockoutBingoSyncPacket state = LockoutBingoClientState.get();
        UUID playerUuid = this.client != null && this.client.player != null ? this.client.player.getUuid() : null;
        LockoutBingoTeam localTeam = playerUuid != null ? LockoutBingoClientState.getTeam(playerUuid) : null;
        LockoutBingoTeam winner = LockoutBingoTeam.fromOrdinal(state.winnerTeamId());
        LockoutBingoTeam targetTeam = localTeam != null ? localTeam : LockoutBingoClientState.getLeadingTeam();
        int clinchTarget = LockoutBingoClientState.getClinchTarget(targetTeam);
        Text clinchText = clinchTarget > 25
                ? Text.translatable("challengecraft.placeholder.pending")
                : Text.of(Integer.toString(clinchTarget));

        int boardW = GRID_SIZE * TILE_SIZE;
        int boardLeft = this.width / 2 - boardW / 2;
        int boardTop = 58;

        // ---- Title ----
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, CraftUI.GOLD);

        // ---- Left info panel ----
        int infoX = boardLeft - SIDE_PANEL_WIDTH - 12;
        if (infoX < 6) {
            infoX = 6;
        }
        int infoY = boardTop;
        int infoH = boardW;
        CraftUI.panelFloat(context, infoX, infoY, SIDE_PANEL_WIDTH, infoH, CraftUI.INFO);
        CraftUI.sectionHeader(context, this.textRenderer, Text.translatable("challengecraft.lockout.board.info_header"),
                infoX + 8, infoY + 8, SIDE_PANEL_WIDTH - 16, CraftUI.INFO);
        int infoRowY = infoY + 8 + this.textRenderer.fontHeight + 8;
        infoRowY = infoRow(context, infoX + 8, infoRowY, Text.translatable("challengecraft.lockout.board.needed_to_win", clinchText), CraftUI.GOLD);
        infoRowY = infoRow(context, infoX + 8, infoRowY, Text.translatable("challengecraft.lockout.board.time", formatTicks(LockoutBingoClientState.getElapsedTicks())), CraftUI.INFO);
        Text teamValue = localTeam == null ? Text.translatable("challengecraft.lockout.team.none") : localTeam.displayName();
        infoRowY = infoRow(context, infoX + 8, infoRowY, Text.translatable("challengecraft.lockout.board.current_team", teamValue),
                localTeam == null ? CraftUI.TEXT_MUTED : localTeam.color());
        if (winner != null) {
            infoRow(context, infoX + 8, infoRowY, Text.translatable("challengecraft.lockout.board.winner", winner.displayName()), winner.color());
        } else if (LockoutBingoClientState.isDraw()) {
            infoRow(context, infoX + 8, infoRowY, Text.translatable("challengecraft.lockout.board.draw"), CraftUI.WARNING);
        }

        // ---- Right scoreboard panel ----
        int scoreX = boardLeft + boardW + 12;
        if (scoreX + SIDE_PANEL_WIDTH > this.width - 6) {
            scoreX = this.width - 6 - SIDE_PANEL_WIDTH;
        }
        CraftUI.panelFloat(context, scoreX, infoY, SIDE_PANEL_WIDTH, infoH, CraftUI.GOLD);
        CraftUI.sectionHeader(context, this.textRenderer, Text.translatable("challengecraft.lockout.board.scores_header"),
                scoreX + 8, infoY + 8, SIDE_PANEL_WIDTH - 16, CraftUI.GOLD);
        int scoreRowY = infoY + 8 + this.textRenderer.fontHeight + 10;
        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            // Colour swatch + name + score.
            context.fill(scoreX + 8, scoreRowY, scoreX + 8 + 8, scoreRowY + 8, team.color());
            context.drawBorder(scoreX + 8, scoreRowY, 8, 8, CraftUI.darken(team.color(), 0.6f));
            context.drawText(this.textRenderer, team.displayName(), scoreX + 22, scoreRowY, team.color(), false);
            Text score = Text.of(Integer.toString(LockoutBingoClientState.getScore(team)));
            context.drawText(this.textRenderer, score, scoreX + SIDE_PANEL_WIDTH - this.textRenderer.getWidth(score) - 10, scoreRowY, CraftUI.TEXT_PRIMARY, false);
            scoreRowY += 16;
        }

        teamButton.visible = !state.started();
        teamButton.active = !state.started();

        // ---- Board ----
        CraftUI.panelFloat(context, boardLeft - 6, boardTop - 6, boardW + 12, boardW + 12, CraftUI.ACCENT_HAIRLINE);

        if (state.boardGoalIds().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.lockout.board.waiting"),
                    this.width / 2, boardTop + boardW / 2, CraftUI.TEXT_SECONDARY);
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
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(hoveredGoal.title());
            tooltip.add(hoveredGoal.description());
            tooltip.add(hoveredGoal.condition());

            LockoutBingoTeam claimedTeam = LockoutBingoTeam.fromOrdinal(hoveredIndex < state.claimedTeams().size() ? state.claimedTeams().get(hoveredIndex) : -1);
            if (claimedTeam != null) {
                String name = hoveredIndex < state.claimedByNames().size() ? state.claimedByNames().get(hoveredIndex) : "";
                tooltip.add(Text.translatable("challengecraft.lockout.board.claimed_by", claimedTeam.displayName(), Text.of(name)));
            }
            this.pendingTooltip = tooltip;
        }
    }

    private int infoRow(DrawContext context, int x, int y, Text text, int color) {
        context.drawText(this.textRenderer, text, x, y, color, false);
        return y + this.textRenderer.fontHeight + 5;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawTile(DrawContext context, int x, int y, int mouseX, int mouseY,
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
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.placeholder.unknown"),
                    x + TILE_SIZE / 2, y + 22, CraftUI.TEXT_SECONDARY);
            return;
        }

        ItemStack icon = goal.createIconStack();
        context.drawItem(icon, x + TILE_SIZE / 2 - 8, y + 6);

        String label = CraftUI.trimToWidth(this.textRenderer, goal.title().getString(), TILE_SIZE - 8);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.of(label), x + TILE_SIZE / 2, y + 28, CraftUI.TEXT_PRIMARY);

        if (claimedTeam != null && !claimant.isEmpty()) {
            // Small claimant-initial chip in the corner.
            String initial = claimant.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            CraftUI.chip(context, x + 3, y + 3, 11, 11, CraftUI.applyAlpha(claimedTeam.color(), 0.85f), claimedTeam.color());
            context.drawText(this.textRenderer, Text.of(initial), x + 6, y + 5, 0xFF10151F, false);
        }
    }

    private String formatTicks(long ticks) {
        long totalSeconds = ticks / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
