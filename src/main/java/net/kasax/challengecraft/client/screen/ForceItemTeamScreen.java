package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.data.ForceItemBattleSavedData;
import net.kasax.challengecraft.network.ForceItemActionPacket;
import net.kasax.challengecraft.network.ForceItemSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

@Environment(EnvType.CLIENT)
/**
 * Force Item Battle game screen (opened by the tracker item): battle status, contextual
 * actions (teams + Start in the lobby, Joker while running, Show Results when ended), the
 * current target with its FULL name, and the icon history of everything collected so far.
 */
public class ForceItemTeamScreen extends Screen {
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int lastBuiltState = -1;

    public ForceItemTeamScreen() {
        super(Component.translatable("challengecraft.fib.team_screen.title"));
    }

    /** The integrated server must keep ticking (scans, joker scoring) while this is open. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.REQUEST_SYNC, -1));

        this.panelW = 340;
        this.panelH = Math.min(240, height - 60);
        this.panelX = width / 2 - panelW / 2;
        this.panelY = Math.max(18, height / 2 - panelH / 2 - 12);

        int state = ForceItemClientState.get().state();
        this.lastBuiltState = state;

        if (state == ForceItemBattleSavedData.STATE_IDLE) {
            int buttonW = (panelW - 16 - 12) / 4;
            int teamY = panelY + 40;
            LockoutBingoTeam[] teams = LockoutBingoTeam.values();
            for (int i = 0; i < teams.length; i++) {
                LockoutBingoTeam team = teams[i];
                addRenderableWidget(new CraftButton(panelX + 8 + i * (buttonW + 4), teamY, buttonW, 20,
                        team.displayName(), CraftButton.Style.NEUTRAL,
                        btn -> ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.JOIN_TEAM, team.ordinal()))));
            }
            addRenderableWidget(new CraftButton(panelX + 8, teamY + 24, (panelW - 20) / 2, 20,
                    Component.translatable("challengecraft.fib.team_screen.solo"), CraftButton.Style.NEUTRAL,
                    btn -> ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.LEAVE_TEAM, -1))));
            addRenderableWidget(new CraftButton(panelX + 12 + (panelW - 20) / 2, teamY + 24, (panelW - 20) / 2, 20,
                    Component.translatable("challengecraft.fib.team_screen.start"), CraftButton.Style.PRIMARY,
                    btn -> ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.START_BATTLE, -1))));
        } else if (state == ForceItemBattleSavedData.STATE_RUNNING) {
            addRenderableWidget(new CraftButton(panelX + 8, panelY + 40, panelW - 16, 20,
                    Component.translatable("challengecraft.fib.team_screen.use_joker"), CraftButton.Style.PRIMARY,
                    btn -> ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.USE_JOKER, -1))));
        } else {
            addRenderableWidget(new CraftButton(panelX + 8, panelY + 40, panelW - 16, 20,
                    Component.translatable("challengecraft.fib.team_screen.show_results"), CraftButton.Style.PRIMARY,
                    btn -> ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.TRIGGER_RESULTS, -1))));
        }

        addRenderableWidget(new CraftButton(width / 2 - 60, panelY + panelH + 8, 120, 20,
                Component.translatable("gui.done"), CraftButton.Style.NEUTRAL,
                btn -> onClose()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // The lobby → running → ended transitions swap the button row.
        if (ForceItemClientState.get().state() != lastBuiltState) {
            rebuildWidgets();
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        CraftUI.frame(ctx, panelX - 4, panelY - 4, panelW + 8, panelH + 8);
        ctx.centeredText(font, title, width / 2, panelY + 6, CraftUI.TEXT_PRIMARY);

        ForceItemSyncPacket state = ForceItemClientState.get();
        Component status = switch (state.state()) {
            case ForceItemBattleSavedData.STATE_RUNNING -> {
                long seconds = ForceItemClientState.remainingTicksNow() / 20L;
                yield Component.translatable("challengecraft.fib.team_screen.running",
                        String.format("%d:%02d", seconds / 60, seconds % 60));
            }
            case ForceItemBattleSavedData.STATE_ENDED -> Component.translatable("challengecraft.fib.team_screen.ended");
            default -> Component.translatable("challengecraft.fib.team_screen.idle");
        };
        ctx.centeredText(font, status, width / 2, panelY + 20, CraftUI.WARNING);

        var self = Minecraft.getInstance().player;
        ForceItemSyncPacket.PlayerEntry ownEntry = self == null ? null : ForceItemClientState.getEntry(self.getUUID());

        // Content must start BELOW the button block, which is taller in the lobby (two rows:
        // team buttons + Solo/Start) than while running/ended (one row). Anchoring the roster at
        // a fixed y made the lobby player list overlap the "Play Solo"/"Start" buttons.
        boolean idle = state.state() == ForceItemBattleSavedData.STATE_IDLE;
        int buttonsBottom = idle ? panelY + 84 : panelY + 60;
        int y = buttonsBottom + 8;

        if (ownEntry != null && !idle) {
            // Current target: icon + FULL name (this is where truncated HUD names are readable).
            Item current = ownEntry.itemId().isEmpty() ? Items.AIR : BuiltInRegistries.ITEM.getValue(Identifier.parse(ownEntry.itemId()));
            if (current != Items.AIR) {
                // 26.2 dropped the no-arg Item.getName(); the display name now comes off the stack
                // (ItemStack.getItemName() is exactly getItem().getName(stack)).
                ItemStack currentStack = new ItemStack(current);
                CraftUI.iconTileItem(ctx, currentStack, panelX + 8, y, 18, CraftUI.GOLD);
                ctx.text(font, Component.translatable("challengecraft.fib.team_screen.current", currentStack.getItemName()),
                        panelX + 30, y + 5, CraftUI.TEXT_PRIMARY, false);
                y += 24;
            }

            // Collected-item history as an icon grid, oldest first.
            ctx.text(font,
                    Component.translatable("challengecraft.fib.team_screen.history", ownEntry.collected().size()),
                    panelX + 8, y, CraftUI.TEXT_SECONDARY, false);
            y += 12;
            int perRow = (panelW - 16) / 18;
            int maxRows = 3;
            List<String> collected = ownEntry.collected();
            int shown = Math.min(collected.size(), perRow * maxRows);
            int skipped = collected.size() - shown;
            for (int i = 0; i < shown; i++) {
                String id = collected.get(skipped + i); // newest items always visible
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                int gx = panelX + 8 + (i % perRow) * 18;
                int gy = y + (i / perRow) * 18;
                ctx.item(new ItemStack(item), gx, gy);
            }
            y += Math.max(1, (shown + perRow - 1) / perRow) * 18 + 6;
        }

        // Players section, clearly separated from the buttons/history above with a header rule.
        CraftUI.sectionHeader(ctx, font, Component.translatable("challengecraft.fib.team_screen.players"),
                panelX + 8, y, panelW - 16, CraftUI.GOLD);
        y += font.lineHeight + 6;

        for (ForceItemSyncPacket.PlayerEntry entry : state.players()) {
            if (y > panelY + panelH - 10) break;
            LockoutBingoTeam team = LockoutBingoTeam.fromOrdinal(entry.teamId());
            int color = team != null ? team.color() : CraftUI.TEXT_SECONDARY;
            String line = entry.name() + "  ·  " + entry.score() + " ✦  ·  " + entry.jokers() + " ♣";
            if (self != null && entry.uuid().equals(self.getUUID())) {
                line = "> " + line;
            }
            ctx.text(font, Component.nullToEmpty(line), panelX + 12, y, color, false);
            y += 12;
        }
    }
}
