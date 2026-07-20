package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.network.ForceItemActionPacket;
import net.kasax.challengecraft.network.ForceItemResultsPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

@Environment(EnvType.CLIENT)
/**
 * Server-synced results ceremony. The stage (from the results packet) says how many placements
 * are open, counted from LAST place. Within the current placement the collected items appear
 * one by one (client-timed), then the place + name are revealed; the Continue button (any
 * player) asks the server to advance everyone together. The winner is revealed last.
 */
public class ForceItemResultsScreen extends Screen {
    private static final long ITEM_REVEAL_MS = 350;
    private static final long NAME_REVEAL_DELAY_MS = 600;

    private List<ForceItemResultsPacket.Entry> standings; // best first
    private int stage;                                    // 1 = last place open
    private long stageStartMillis;
    private int lastSoundedItems = 0;
    private CraftButton continueButton;

    public ForceItemResultsScreen(ForceItemResultsPacket packet) {
        super(Text.translatable("challengecraft.fib.results.title"));
        this.standings = packet.entries();
        this.stage = Math.max(1, packet.stage());
        this.stageStartMillis = System.currentTimeMillis();
    }

    /** Applied when a rebroadcast arrives while the screen is already open. */
    public void updateFromPacket(ForceItemResultsPacket packet) {
        this.standings = packet.entries();
        if (packet.stage() > this.stage) {
            this.stage = packet.stage();
            this.stageStartMillis = System.currentTimeMillis();
            this.lastSoundedItems = 0;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private ForceItemResultsPacket.Entry currentEntry() {
        int index = standings.size() - stage;
        return index >= 0 && index < standings.size() ? standings.get(index) : null;
    }

    private int currentItemsRevealed() {
        ForceItemResultsPacket.Entry entry = currentEntry();
        if (entry == null) return 0;
        long elapsed = System.currentTimeMillis() - stageStartMillis;
        return (int) Math.min(entry.itemIds().size(), elapsed / ITEM_REVEAL_MS + 1);
    }

    private boolean currentEntryFullyRevealed() {
        ForceItemResultsPacket.Entry entry = currentEntry();
        if (entry == null) return true;
        long elapsed = System.currentTimeMillis() - stageStartMillis;
        return currentItemsRevealed() >= entry.itemIds().size()
                && elapsed >= entry.itemIds().size() * ITEM_REVEAL_MS + NAME_REVEAL_DELAY_MS;
    }

    @Override
    protected void init() {
        super.init();
        this.continueButton = new CraftButton(width / 2 - 60, height - 30, 120, 20,
                Text.translatable("challengecraft.fib.results.continue"), CraftButton.Style.PRIMARY,
                btn -> {
                    if (stage >= standings.size()) {
                        close();
                    } else {
                        ClientPlayNetworking.send(new ForceItemActionPacket(ForceItemActionPacket.Action.NEXT_RESULT, -1));
                    }
                });
        addDrawableChild(continueButton);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean done = currentEntryFullyRevealed();
        continueButton.active = done;
        continueButton.setMessage(stage >= standings.size()
                ? Text.translatable("gui.done")
                : Text.translatable("challengecraft.fib.results.continue"));

        super.render(ctx, mouseX, mouseY, delta);

        int panelW = 320;
        int panelX = width / 2 - panelW / 2;
        int panelY = 20;
        int panelH = height - 60;
        CraftUI.frame(ctx, panelX - 4, panelY - 4, panelW + 8, panelH + 8);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 8, CraftUI.TEXT_PRIMARY);

        // Already fully revealed placements, compact, worst at the bottom of the block.
        int y = panelY + 26;
        for (int revealIndex = 1; revealIndex < stage; revealIndex++) {
            ForceItemResultsPacket.Entry entry = standings.get(standings.size() - revealIndex);
            int place = standings.size() - revealIndex + 1;
            LockoutBingoTeam team = LockoutBingoTeam.fromOrdinal(entry.teamId());
            int color = team != null ? team.color() : CraftUI.TEXT_SECONDARY;
            String line = "#" + place + "  " + entry.name() + "  ·  " + entry.score() + " ✦";
            ctx.drawText(textRenderer, Text.of(line), panelX + 10, y, color, false);
            y += 11;
        }

        // Current placement: items pop in one by one, then the place + name.
        ForceItemResultsPacket.Entry entry = currentEntry();
        if (entry != null) {
            y += 8;
            int itemsRevealed = currentItemsRevealed();
            if (itemsRevealed > lastSoundedItems) {
                lastSoundedItems = itemsRevealed;
                MinecraftClient.getInstance().getSoundManager()
                        .play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                                0.9f + 0.02f * (itemsRevealed % 12)));
            }

            int perRow = (panelW - 20) / 18;
            for (int i = 0; i < itemsRevealed; i++) {
                var item = Registries.ITEM.get(Identifier.of(entry.itemIds().get(i)));
                int gx = panelX + 10 + (i % perRow) * 18;
                int gy = y + (i / perRow) * 18;
                ctx.drawItem(new ItemStack(item), gx, gy);
            }
            int rows = Math.max(1, (itemsRevealed + perRow - 1) / perRow);
            y += rows * 18 + 8;

            if (currentEntryFullyRevealed()) {
                int place = standings.size() - stage + 1;
                boolean isWinner = place == 1;
                LockoutBingoTeam team = LockoutBingoTeam.fromOrdinal(entry.teamId());
                int nameColor = team != null ? team.color() : CraftUI.TEXT_PRIMARY;
                Text reveal = Text.translatable("challengecraft.fib.results.reveal", place, entry.name(), entry.score());
                int placeColor = isWinner
                        ? CraftUI.mix(CraftUI.GOLD, 0xFFFFFFFF, Anim.pulse(900) * 0.5f)
                        : nameColor;
                ctx.drawCenteredTextWithShadow(textRenderer, reveal, width / 2, y, placeColor);
                y += 14;

                if (isWinner) {
                    Text banner = Text.translatable("challengecraft.fib.results.winner", entry.name());
                    int bannerColor = CraftUI.mix(CraftUI.GOLD, 0xFFFFFFFF, Anim.pulse(1200) * 0.6f);
                    ctx.drawCenteredTextWithShadow(textRenderer, banner, width / 2, y, bannerColor);
                }
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable("challengecraft.fib.results.revealing"),
                        width / 2, y, CraftUI.TEXT_MUTED);
            }
        }
    }
}
