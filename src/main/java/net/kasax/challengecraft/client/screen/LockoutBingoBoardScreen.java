package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.network.LockoutBingoActionPacket;
import net.kasax.challengecraft.network.LockoutBingoSyncPacket;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class LockoutBingoBoardScreen extends Screen {
    private static final int GRID_SIZE = 5;
    private static final int TILE_SIZE = 54;
    private ButtonWidget teamButton;

    public LockoutBingoBoardScreen() {
        super(Text.translatable("challengecraft.worldcreate.challenge40"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.REQUEST_SYNC, -1));
        teamButton = addDrawableChild(ButtonWidget.builder(
                Text.translatable("challengecraft.lockout.board.open_team_screen"),
                button -> this.client.setScreen(new LockoutBingoTeamScreen())
        ).dimensions(this.width / 2 - 70, this.height - 26, 140, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        LockoutBingoSyncPacket state = LockoutBingoClientState.get();
        UUID playerUuid = this.client != null && this.client.player != null ? this.client.player.getUuid() : null;
        LockoutBingoTeam localTeam = playerUuid != null ? LockoutBingoClientState.getTeam(playerUuid) : null;
        LockoutBingoTeam winner = LockoutBingoTeam.fromOrdinal(state.winnerTeamId());
        LockoutBingoTeam targetTeam = localTeam != null ? localTeam : LockoutBingoClientState.getLeadingTeam();
        int clinchTarget = LockoutBingoClientState.getClinchTarget(targetTeam);
        Text clinchTargetText = clinchTarget > 25
                ? Text.translatable("challengecraft.placeholder.pending")
                : Text.of(Integer.toString(clinchTarget));

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFF55);
        context.drawText(this.textRenderer, Text.translatable("challengecraft.lockout.board.needed_to_win", clinchTargetText), 18, 28, 0xFFD8D8D8, false);
        context.drawText(this.textRenderer, Text.translatable("challengecraft.lockout.board.time", formatTicks(LockoutBingoClientState.getElapsedTicks())), 18, 40, 0xFFD8D8D8, false);
        context.drawText(this.textRenderer, Text.translatable("challengecraft.lockout.board.current_team", localTeam == null ? Text.translatable("challengecraft.lockout.team.none") : localTeam.displayName()), 18, 52, 0xFFD8D8D8, false);
        if (winner != null) {
            context.drawText(this.textRenderer, Text.translatable("challengecraft.lockout.board.winner", winner.displayName()), 18, 64, winner.color(), false);
        } else if (LockoutBingoClientState.isDraw()) {
            context.drawText(this.textRenderer, Text.translatable("challengecraft.lockout.board.draw"), 18, 64, 0xFFFFAA, false);
        }

        int scoreX = this.width - 170;
        int scoreY = 28;
        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            context.drawText(
                    this.textRenderer,
                    Text.translatable("challengecraft.lockout.board.score_entry", team.displayName(), LockoutBingoClientState.getScore(team)),
                    scoreX,
                    scoreY,
                    team.color(),
                    false
            );
            scoreY += 12;
        }

        teamButton.visible = !state.started();
        teamButton.active = !state.started();

        if (state.boardGoalIds().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.lockout.board.waiting"), this.width / 2, this.height / 2, 0xFFFFFFFF);
            return;
        }

        int boardLeft = this.width / 2 - (GRID_SIZE * TILE_SIZE) / 2;
        int boardTop = 92;

        LockoutBingoGoal hoveredGoal = null;
        Integer hoveredIndex = null;
        for (int index = 0; index < state.boardGoalIds().size() && index < BOARD_TILE_COUNT; index++) {
            int column = index % GRID_SIZE;
            int row = index / GRID_SIZE;
            int x = boardLeft + column * TILE_SIZE;
            int y = boardTop + row * TILE_SIZE;

            LockoutBingoGoal goal = LockoutBingoGoalPool.byId(state.boardGoalIds().get(index));
            LockoutBingoTeam claimedTeam = LockoutBingoTeam.fromOrdinal(index < state.claimedTeams().size() ? state.claimedTeams().get(index) : -1);
            drawTile(context, x, y, mouseX, mouseY, goal, claimedTeam);

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

            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawTile(DrawContext context, int x, int y, int mouseX, int mouseY, LockoutBingoGoal goal, LockoutBingoTeam claimedTeam) {
        int fill = 0xAA1B1B1B;
        int border = 0xFF5A5A5A;
        if (claimedTeam != null) {
            fill = 0x66000000 | (claimedTeam.color() & 0x00FFFFFF);
            border = claimedTeam.color();
        }

        boolean hovered = mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= y && mouseY < y + TILE_SIZE;
        if (hovered && claimedTeam == null) {
            border = 0xFFFFFFFF;
        }

        context.fill(x, y, x + TILE_SIZE, y + TILE_SIZE, fill);
        context.drawBorder(x, y, TILE_SIZE, TILE_SIZE, border);

        if (goal == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.placeholder.unknown"), x + TILE_SIZE / 2, y + 23, 0xFFFFFFFF);
            return;
        }

        ItemStack icon = goal.createIconStack();
        context.drawItem(icon, x + 18, y + 6);

        String label = trimToWidth(goal.title().getString(), TILE_SIZE - 8);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.of(label), x + TILE_SIZE / 2, y + 28, 0xFFFFFFFF);
    }

    private String trimToWidth(String value, int maxWidth) {
        if (this.textRenderer.getWidth(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "...";
        int targetWidth = Math.max(0, maxWidth - this.textRenderer.getWidth(ellipsis));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (this.textRenderer.getWidth(builder.toString() + character) > targetWidth) {
                break;
            }
            builder.append(character);
        }
        return builder.append(ellipsis).toString();
    }

    private String formatTicks(long ticks) {
        long totalSeconds = ticks / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static final int BOARD_TILE_COUNT = 25;
}
