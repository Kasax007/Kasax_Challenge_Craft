package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.network.LockoutBingoActionPacket;
import net.kasax.challengecraft.network.LockoutBingoSyncPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
/** Lobby screen for team selection and ready state before a lockout run begins. */
public class LockoutBingoTeamScreen extends Screen {
    private static final int COL_WIDTH = 170;
    private static final int COL_GAP = 12;
    private static final int ROW_TOP = 58;

    private CraftButton leaveButton;
    private CraftButton readyButton;

    private final int[] panelX = new int[4];
    private final int[] panelY = new int[4];
    private int panelW;
    private int panelH;
    private final float[] rosterOffset = new float[4];
    private final int[] rosterMax = new int[4];

    public LockoutBingoTeamScreen() {
        super(Component.translatable("challengecraft.lockout.team.title"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.REQUEST_SYNC, -1));

        int totalW = COL_WIDTH * 2 + COL_GAP;
        int startX = this.width / 2 - totalW / 2;
        int bottomLimit = this.height - 44;
        int panelGap = 10;
        this.panelW = COL_WIDTH;
        this.panelH = (bottomLimit - ROW_TOP - panelGap) / 2;

        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            int i = team.ordinal();
            int col = i % 2;
            int rowIdx = i / 2;
            panelX[i] = startX + col * (COL_WIDTH + COL_GAP);
            panelY[i] = ROW_TOP + rowIdx * (panelH + panelGap);

            int joinW = 46;
            addRenderableWidget(new CraftButton(panelX[i] + panelW - joinW - 6, panelY[i] + 5, joinW, 14,
                    Component.translatable("challengecraft.lockout.team.join_short"), CraftButton.Style.NEUTRAL,
                    b -> ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.JOIN_TEAM, team.ordinal()))));
        }

        leaveButton = addRenderableWidget(new CraftButton(this.width / 2 - 124, this.height - 30, 120, 20,
                Component.translatable("challengecraft.lockout.team.leave"), CraftButton.Style.NEUTRAL,
                b -> ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.LEAVE_TEAM, -1))));

        readyButton = addRenderableWidget(new CraftButton(this.width / 2 + 4, this.height - 30, 120, 20,
                Component.translatable("challengecraft.lockout.team.ready"), CraftButton.Style.PRIMARY, b -> sendReadyToggle()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);

        LockoutBingoSyncPacket state = LockoutBingoClientState.get();
        UUID localUuid = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        LockoutBingoTeam localTeam = localUuid != null ? LockoutBingoClientState.getTeam(localUuid) : null;
        boolean localReady = localUuid != null && LockoutBingoClientState.isReady(localUuid);

        if (readyButton != null) {
            readyButton.setMessage(Component.translatable(localReady
                    ? "challengecraft.lockout.team.unready" : "challengecraft.lockout.team.ready"));
            readyButton.active = localTeam != null && !state.started();
        }
        if (leaveButton != null) {
            leaveButton.active = localTeam != null && !state.started();
        }

        context.centeredText(this.font, this.title, this.width / 2, 14, CraftUI.TEXT_PRIMARY);

        int onlineParticipants = 0;
        int onlineTeams = 0;
        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            boolean hasOnlineMember = false;
            for (LockoutBingoSyncPacket.PlayerState player : state.players()) {
                if (player.teamId() == team.ordinal() && player.online()) {
                    onlineParticipants++;
                    hasOnlineMember = true;
                }
            }
            if (hasOnlineMember) {
                onlineTeams++;
            }
        }
        boolean everyoneReady = onlineParticipants >= 2 && state.players().stream()
                .filter(player -> player.teamId() >= 0 && player.online())
                .allMatch(LockoutBingoSyncPacket.PlayerState::ready);

        drawRequirementChips(context, 30, onlineParticipants, onlineTeams, everyoneReady, readyCount(state));

        for (LockoutBingoTeam team : LockoutBingoTeam.values()) {
            drawTeamPanel(context, team, state.players(), localTeam);
        }

        boolean started = state.started();
        context.centeredText(this.font,
                Component.translatable(started ? "challengecraft.lockout.team.started_hint" : "challengecraft.lockout.team.auto_start_hint"),
                this.width / 2, this.height - 46, started ? CraftUI.INFO : CraftUI.TEXT_SECONDARY);
    }

    private void drawRequirementChips(GuiGraphicsExtractor context, int y, int players, int teams, boolean ready, int readyCount) {
        Component p = Component.translatable("challengecraft.lockout.team.require_players", players, 2);
        Component t = Component.translatable("challengecraft.lockout.team.require_teams", teams, 2);
        Component r = Component.translatable("challengecraft.lockout.team.require_ready", readyCount, Math.max(players, 1));
        int wp = this.font.width(p) + 10;
        int wt = this.font.width(t) + 10;
        int wr = this.font.width(r) + 10;
        int gap = 8;
        int total = wp + wt + wr + gap * 2;
        int x = this.width / 2 - total / 2;
        x += CraftUI.labelChip(context, this.font, p, x, y, players >= 2 ? CraftUI.SUCCESS : CraftUI.DANGER) + gap;
        x += CraftUI.labelChip(context, this.font, t, x, y, teams >= 2 ? CraftUI.SUCCESS : CraftUI.DANGER) + gap;
        CraftUI.labelChip(context, this.font, r, x, y, ready ? CraftUI.SUCCESS : CraftUI.DANGER);
    }

    private void drawTeamPanel(GuiGraphicsExtractor context, LockoutBingoTeam team,
                               List<LockoutBingoSyncPacket.PlayerState> players, LockoutBingoTeam localTeam) {
        int i = team.ordinal();
        int x = panelX[i];
        int y = panelY[i];
        boolean isLocal = localTeam == team;
        int fill = isLocal ? CraftUI.mix(CraftUI.SURFACE_RAISED, team.color(), 0.12f) : CraftUI.SURFACE_FLOAT;
        CraftUI.panel(context, x, y, panelW, panelH, fill, isLocal ? team.color() : CraftUI.BORDER, team.color());

        // Team-coloured header with an underline hairline and member count.
        context.text(this.font, team.displayName(), x + 8, y + 7, team.color(), false);
        int underlineY = y + 7 + this.font.lineHeight + 2;
        context.fill(x + 8, underlineY, x + panelW - 54, underlineY + 1, CraftUI.applyAlpha(team.color(), 0.7f));

        List<LockoutBingoSyncPacket.PlayerState> teamPlayers = new ArrayList<>(players.stream()
                .filter(player -> player.teamId() == team.ordinal())
                .sorted(Comparator.comparing(LockoutBingoSyncPacket.PlayerState::name, String.CASE_INSENSITIVE_ORDER))
                .toList());

        int rosterTop = underlineY + 6;
        int rosterBottom = y + panelH - 6;
        int visibleH = rosterBottom - rosterTop;
        int rowH = 12;
        int contentH = teamPlayers.size() * rowH;
        rosterMax[i] = Math.max(0, contentH - visibleH);
        rosterOffset[i] = Mth.clamp(rosterOffset[i], 0f, rosterMax[i]);

        if (teamPlayers.isEmpty()) {
            context.text(this.font, Component.translatable("challengecraft.lockout.team.empty"),
                    x + 8, rosterTop + 4, CraftUI.TEXT_MUTED, false);
            return;
        }

        context.enableScissor(x + 4, rosterTop, x + panelW - 4, rosterBottom);
        int lineY = rosterTop - Math.round(rosterOffset[i]);
        for (LockoutBingoSyncPacket.PlayerState player : teamPlayers) {
            Component badge;
            int accent;
            if (!player.online()) {
                badge = Component.translatable("challengecraft.lockout.team.badge_offline");
                accent = CraftUI.TEXT_MUTED;
            } else if (player.ready()) {
                badge = Component.translatable("challengecraft.lockout.team.badge_ready");
                accent = CraftUI.SUCCESS;
            } else {
                badge = Component.translatable("challengecraft.lockout.team.badge_online");
                accent = CraftUI.INFO;
            }
            int chipW = this.font.width(badge) + 10;
            int chipX = x + panelW - chipW - 8;
            CraftUI.labelChip(context, this.font, badge, chipX, lineY, accent);

            String name = CraftUI.trimToWidth(this.font, player.name(), chipX - (x + 8) - 4);
            int nameColor = player.online() ? CraftUI.TEXT_PRIMARY : CraftUI.TEXT_MUTED;
            context.text(this.font, Component.nullToEmpty(name), x + 8, lineY + 1, nameColor, false);
            lineY += rowH;
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int i = 0; i < 4; i++) {
            if (mouseX >= panelX[i] && mouseX <= panelX[i] + panelW && mouseY >= panelY[i] && mouseY <= panelY[i] + panelH) {
                rosterOffset[i] = Mth.clamp(rosterOffset[i] - (float) verticalAmount * 10f, 0f, rosterMax[i]);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void sendReadyToggle() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        UUID uuid = this.minecraft.player.getUUID();
        boolean ready = LockoutBingoClientState.isReady(uuid);
        ClientPlayNetworking.send(new LockoutBingoActionPacket(
                ready ? LockoutBingoActionPacket.Action.UNREADY : LockoutBingoActionPacket.Action.READY, -1));
    }

    private int readyCount(LockoutBingoSyncPacket state) {
        return (int) state.players().stream()
                .filter(player -> player.teamId() >= 0 && player.online() && player.ready())
                .count();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
