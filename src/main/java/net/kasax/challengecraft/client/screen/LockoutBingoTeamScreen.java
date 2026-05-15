package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.network.LockoutBingoActionPacket;
import net.kasax.challengecraft.network.LockoutBingoSyncPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
/** Lobby screen for team selection and ready state before a lockout run begins. */
public class LockoutBingoTeamScreen extends Screen {
    private ButtonWidget leaveButton;
    private ButtonWidget readyButton;

    public LockoutBingoTeamScreen() {
        super(Text.translatable("challengecraft.lockout.team.title"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.REQUEST_SYNC, -1));

        int sectionWidth = 150;
        int sectionHeight = 92;
        int left = this.width / 2 - sectionWidth - 10;
        int right = this.width / 2 + 10;
        int top = 50;
        int bottom = top + sectionHeight + 16;

        addDrawableChild(buildJoinButton(left + 8, top + 66, LockoutBingoTeam.RED));
        addDrawableChild(buildJoinButton(right + 8, top + 66, LockoutBingoTeam.BLUE));
        addDrawableChild(buildJoinButton(left + 8, bottom + 66, LockoutBingoTeam.GREEN));
        addDrawableChild(buildJoinButton(right + 8, bottom + 66, LockoutBingoTeam.YELLOW));

        leaveButton = addDrawableChild(ButtonWidget.builder(
                Text.translatable("challengecraft.lockout.team.leave"),
                button -> ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.LEAVE_TEAM, -1))
        ).dimensions(this.width / 2 - 104, this.height - 32, 100, 20).build());

        readyButton = addDrawableChild(ButtonWidget.builder(
                Text.translatable("challengecraft.lockout.team.ready"),
                button -> sendReadyToggle()
        ).dimensions(this.width / 2 + 4, this.height - 32, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFF55);

        LockoutBingoSyncPacket state = LockoutBingoClientState.get();
        UUID localUuid = this.client != null && this.client.player != null ? this.client.player.getUuid() : null;
        LockoutBingoTeam localTeam = localUuid != null ? LockoutBingoClientState.getTeam(localUuid) : null;
        boolean localReady = localUuid != null && LockoutBingoClientState.isReady(localUuid);

        if (readyButton != null) {
            readyButton.setMessage(Text.translatable(localReady
                    ? "challengecraft.lockout.team.unready"
                    : "challengecraft.lockout.team.ready"));
            readyButton.active = localTeam != null && !state.started();
        }
        if (leaveButton != null) {
            leaveButton.active = localTeam != null && !state.started();
        }

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

        drawRequirementLine(context, 26, "challengecraft.lockout.team.require_players", onlineParticipants >= 2, onlineParticipants, 2);
        drawRequirementLine(context, 38, "challengecraft.lockout.team.require_teams", onlineTeams >= 2, onlineTeams, 2);
        drawRequirementLine(context, 50, "challengecraft.lockout.team.require_ready", everyoneReady, readyCount(state), Math.max(onlineParticipants, 1));

        drawTeamSection(context, 50, this.width / 2 - 160, LockoutBingoTeam.RED, state.players(), localTeam);
        drawTeamSection(context, 50, this.width / 2 + 10, LockoutBingoTeam.BLUE, state.players(), localTeam);
        drawTeamSection(context, 158, this.width / 2 - 160, LockoutBingoTeam.GREEN, state.players(), localTeam);
        drawTeamSection(context, 158, this.width / 2 + 10, LockoutBingoTeam.YELLOW, state.players(), localTeam);

        if (state.started()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("challengecraft.lockout.team.started_hint"),
                    this.width / 2,
                    this.height - 56,
                    0xFFAAFF
            );
        } else {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("challengecraft.lockout.team.auto_start_hint"),
                    this.width / 2,
                    this.height - 56,
                    0xAAAAAA
            );
        }
    }

    private ButtonWidget buildJoinButton(int x, int y, LockoutBingoTeam team) {
        return ButtonWidget.builder(
                Text.translatable("challengecraft.lockout.team.join", team.displayName()),
                button -> ClientPlayNetworking.send(new LockoutBingoActionPacket(LockoutBingoActionPacket.Action.JOIN_TEAM, team.ordinal()))
        ).dimensions(x, y, 134, 20).build();
    }

    private void sendReadyToggle() {
        if (this.client == null || this.client.player == null) {
            return;
        }
        UUID uuid = this.client.player.getUuid();
        boolean ready = LockoutBingoClientState.isReady(uuid);
        ClientPlayNetworking.send(new LockoutBingoActionPacket(
                ready ? LockoutBingoActionPacket.Action.UNREADY : LockoutBingoActionPacket.Action.READY,
                -1
        ));
    }

    private void drawRequirementLine(DrawContext context, int y, String key, boolean met, int current, int required) {
        int color = met ? 0xFF55FF55 : 0xFFFF5555;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(key, current, required), this.width / 2, y, color);
    }

    private void drawTeamSection(
            DrawContext context,
            int y,
            int x,
            LockoutBingoTeam team,
            List<LockoutBingoSyncPacket.PlayerState> players,
            LockoutBingoTeam localTeam
    ) {
        int width = 150;
        int height = 92;
        int fill = localTeam == team ? 0x662A2A2A : 0x55141414;
        context.fill(x, y, x + width, y + height, fill);
        context.drawBorder(x, y, width, height, team.color());
        context.drawCenteredTextWithShadow(this.textRenderer, team.displayName(), x + width / 2, y + 8, team.color());

        List<LockoutBingoSyncPacket.PlayerState> teamPlayers = new ArrayList<>(players.stream()
                .filter(player -> player.teamId() == team.ordinal())
                .sorted(Comparator.comparing(LockoutBingoSyncPacket.PlayerState::name, String.CASE_INSENSITIVE_ORDER))
                .toList());

        if (teamPlayers.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("challengecraft.lockout.team.empty"), x + width / 2, y + 34, 0xFF777777);
            return;
        }

        int lineY = y + 24;
        for (LockoutBingoSyncPacket.PlayerState player : teamPlayers) {
            if (lineY > y + 58) {
                break;
            }

            Text status = player.online()
                    ? Text.translatable(player.ready() ? "challengecraft.lockout.team.member_ready" : "challengecraft.lockout.team.member_waiting", Text.of(player.name()))
                    : Text.translatable("challengecraft.lockout.team.member_offline", Text.of(player.name()));
            int color = player.online() ? (player.ready() ? 0xFF9DFF9D : 0xFFE8E8E8) : 0xFF888888;
            context.drawText(this.textRenderer, status, x + 8, lineY, color, false);
            lineY += 10;
        }
    }

    private int readyCount(LockoutBingoSyncPacket state) {
        return (int) state.players().stream()
                .filter(player -> player.teamId() >= 0 && player.online() && player.ready())
                .count();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
