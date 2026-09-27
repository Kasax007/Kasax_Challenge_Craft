package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ChallengeCodeClient;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.code.ChallengeCode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.Consumer;

/**
 * Share and enter challenge codes.
 *
 * <p>One dialog for all three entrances — world creation, the in-world editor and the title screen —
 * so the thing a player learns once works everywhere. The top half shows the code of what is set up
 * right now (absent on the title screen, where nothing is), the bottom half takes a code. The input
 * is pre-filled from the clipboard when it holds one, because a code almost always arrives by being
 * copied out of a chat, and {@link ChallengeCode#find} also accepts the whole share post.
 *
 * <p>What applying means is the caller's business ({@code onApply}), including where to go next.
 */
@Environment(EnvType.CLIENT)
public class ChallengeCodeScreen extends Screen {
    private static final int W = 300;
    private static final int H = 214;
    private static final int PAD = 12;
    private static final int ICON = 18;

    private final Screen parent;
    private final ChallengeCode current;
    private final Consumer<ChallengeCode> onApply;
    private final Component applyHint;

    private EditBox input;
    private CraftButton applyButton;
    private ChallengeCode parsed;
    private ChallengeCode preview;
    private Component problem;
    private long copiedAt = -1;
    private int px;
    private int py;

    /**
     * @param current   the setup on screen right now, or null when there is nothing to share
     * @param applyHint one line on what happens with the seed in this place
     */
    public ChallengeCodeScreen(Screen parent, ChallengeCode current, Component applyHint,
                               Consumer<ChallengeCode> onApply) {
        super(Component.translatable("challengecraft.code.title"));
        this.parent = parent;
        this.current = current;
        this.applyHint = applyHint;
        this.onApply = onApply;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = Math.max(4, (height - H) / 2);
        int cx = px + PAD;
        int cw = W - PAD * 2;

        CraftButton copy = new CraftButton(px + W - PAD - 70, py + 24, 70, 14,
                Component.translatable("challengecraft.code.copy"), CraftButton.Style.NEUTRAL, b -> {
                    ChallengeCodeClient.copy(current.encode());
                    copiedAt = Util.getMillis();
                });
        copy.active = current != null;
        addRenderableWidget(copy);

        String previous = input != null ? input.getValue() : null;
        input = new EditBox(font, cx, py + 88, cw, 18, Component.translatable("challengecraft.code.input"));
        input.setMaxLength(400); // a whole pasted share post must fit, not just the code
        input.setHint(Component.translatable("challengecraft.code.input_hint"));
        if (previous != null) {
            input.setValue(previous);
        } else {
            // Only a code that is not this very setup — offering to "apply" what is already on
            // screen would just be noise.
            String clip = ChallengeCodeClient.clipboardCode();
            if (clip != null && (current == null || !clip.equalsIgnoreCase(current.encode()))) {
                input.setValue(clip);
            }
        }
        input.setResponder(this::parse);
        addRenderableWidget(input);

        int by = py + H - 28;
        applyButton = new CraftButton(width / 2 - 125, by, 120, 20,
                Component.translatable("challengecraft.code.apply"), CraftButton.Style.PRIMARY,
                b -> {
                    if (parsed != null) {
                        onApply.accept(parsed);
                    }
                });
        addRenderableWidget(applyButton);
        addRenderableWidget(new CraftButton(width / 2 + 5, by, 120, 20,
                Component.translatable("challengecraft.code.back"), CraftButton.Style.NEUTRAL,
                b -> onClose()));

        parse(input.getValue());
        setInitialFocus(input);
    }

    private void parse(String text) {
        parsed = null;
        preview = null;
        problem = null;
        if (text != null && !text.isBlank()) {
            try {
                ChallengeCode code = ChallengeCode.decode(text);
                preview = code;
                problem = ChallengeCodeClient.usabilityProblem(code);
                if (problem == null) {
                    parsed = code;
                }
            } catch (ChallengeCode.InvalidCodeException e) {
                problem = ChallengeCodeClient.describe(e.problem());
            }
        }
        if (applyButton != null) {
            applyButton.active = parsed != null;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        CraftUI.frame(ctx, px, py, W, H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        int cx = px + PAD;
        int cw = W - PAD * 2;

        ctx.centeredText(font, title, px + W / 2, py + 9, CraftUI.TEXT_PRIMARY);

        // -- your code ---------------------------------------------------------------------------
        boolean copiedRecently = copiedAt >= 0 && Util.getMillis() - copiedAt < 1500;
        ctx.text(font, copiedRecently
                        ? Component.translatable("challengecraft.code.copied")
                        : Component.translatable("challengecraft.code.yours"),
                cx, py + 27, copiedRecently ? CraftUI.SUCCESS : CraftUI.TEXT_SECONDARY, false);
        CraftUI.chip(ctx, cx, py + 42, cw, 18, CraftUI.SURFACE_RAISED, current != null ? CraftUI.GOLD : CraftUI.BORDER);
        if (current != null) {
            Component code = Component.literal(current.encode());
            float scale = Math.min(1f, (cw - 8) / (float) font.width(code));
            CraftUI.drawCenteredScaled(ctx, font, code, px + W / 2, py + 51, scale, CraftUI.GOLD);
        } else {
            ctx.centeredText(font, Component.translatable("challengecraft.code.nothing_yet"),
                    px + W / 2, py + 47, CraftUI.TEXT_MUTED);
        }

        ctx.fill(cx, py + 68, cx + cw, py + 69, CraftUI.applyAlpha(CraftUI.BORDER, 0.9f));

        // -- enter a code ------------------------------------------------------------------------
        ctx.text(font, Component.translatable("challengecraft.code.enter"), cx, py + 76, CraftUI.TEXT_SECONDARY, false);

        int y = py + 112;
        if (problem != null) {
            CraftUI.drawWrapped(ctx, font, problem, cx, y, cw, CraftUI.DANGER, 2);
        } else if (preview != null) {
            Component seed = preview.hasSeed()
                    ? Component.translatable("challengecraft.code.seed", String.valueOf(preview.seed()))
                    : Component.translatable("challengecraft.code.no_seed");
            Component line = Component.translatable("challengecraft.code.summary",
                    preview.challengeIds().size(), preview.perkIds().size(), seed);
            ctx.text(font, CraftUI.trimToWidth(font, line.getString(), cw), cx, y, CraftUI.TEXT_PRIMARY, false);
            drawIcons(ctx, cx, y + 12, cw, mouseX, mouseY);
        } else {
            ctx.text(font, Component.translatable("challengecraft.code.paste_hint"), cx, y, CraftUI.TEXT_MUTED, false);
        }

        if (applyHint != null) {
            CraftUI.drawWrapped(ctx, font, applyHint, cx, py + H - 54, cw, CraftUI.TEXT_MUTED, 2);
        }
    }

    /** The code's challenges, then its perks, as icons, capped to one row; hovering one names it. */
    private void drawIcons(GuiGraphicsExtractor ctx, int x0, int y, int width, int mouseX, int mouseY) {
        var ids = new java.util.ArrayList<>(preview.challengeIds());
        ids.addAll(preview.perkIds());
        int perRow = Math.max(1, (width + 2) / (ICON + 2));
        int shown = Math.min(ids.size(), perRow);
        int overflow = ids.size() - shown;
        if (overflow > 0) {
            shown--;
            overflow++;
        }
        int x = x0;
        for (int i = 0; i < shown; i++) {
            int id = ids.get(i);
            CraftUI.iconTileItem(ctx, ChallengeIconProvider.getIcon(id), x, y, ICON, CraftUI.BORDER);
            if (mouseX >= x && mouseX < x + ICON && mouseY >= y && mouseY < y + ICON) {
                ctx.setTooltipForNextFrame(ChallengeCodeClient.name(id), mouseX, mouseY);
            }
            x += ICON + 2;
        }
        if (overflow > 0) {
            CraftUI.iconTile(ctx, x, y, ICON, CraftUI.BORDER);
            String more = "+" + overflow;
            ctx.text(font, more, x + (ICON - font.width(more)) / 2, y + 5, CraftUI.TEXT_SECONDARY, false);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
