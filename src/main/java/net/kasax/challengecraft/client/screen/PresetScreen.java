package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.ChallengeCodeClient;
import net.kasax.challengecraft.client.config.ChallengePresets;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.code.ChallengeCode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Saved challenge setups: load one, delete one, or save what is on screen under a name.
 *
 * <p>Same three entrances and the same {@code onApply} contract as {@link ChallengeCodeScreen} —
 * a preset is a code with a name, so loading one goes down exactly the path a pasted code does,
 * level locks included: a preset with something not yet unlocked says so and does not load.
 */
@Environment(EnvType.CLIENT)
public class PresetScreen extends Screen {
    private static final int W = 300;
    private static final int H = 228;
    private static final int PAD = 12;
    private static final int ROW_H = 22;

    private final Screen parent;
    private final ChallengeCode current;
    private final Component applyHint;
    private final Consumer<ChallengeCode> onApply;

    private WidgetScrollPanel list;
    private EditBox nameBox;
    private CraftButton saveButton;
    private boolean withSeed;
    private String armedDelete;
    private Component status;
    private int statusColor = CraftUI.TEXT_MUTED;
    private int px;
    private int py;

    /** @param current the setup on screen right now, or null where there is none (title screen) */
    public PresetScreen(Screen parent, ChallengeCode current, Component applyHint, Consumer<ChallengeCode> onApply) {
        super(Component.translatable("challengecraft.preset.title"));
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

        list = new WidgetScrollPanel(cx, py + 24, cw, 104, Component.empty());
        addRenderableWidget(list);
        fillList();

        if (current != null) {
            String previous = nameBox != null ? nameBox.getValue() : "";
            nameBox = new EditBox(font, cx, py + 160, 150, 18, Component.translatable("challengecraft.preset.name"));
            nameBox.setMaxLength(32);
            nameBox.setHint(Component.translatable("challengecraft.preset.name_hint"));
            nameBox.setValue(previous);
            nameBox.setResponder(v -> updateSave());
            addRenderableWidget(nameBox);
            if (current.hasSeed()) {
                addRenderableWidget(new CraftButton(cx + 154, py + 159, 60, 20, seedLabel(), CraftButton.Style.NEUTRAL, b -> {
                    withSeed = !withSeed;
                    b.setMessage(seedLabel());
                }));
            }
            saveButton = new CraftButton(cx + cw - 58, py + 159, 58, 20,
                    Component.translatable("challengecraft.preset.save"), CraftButton.Style.PRIMARY, b -> save());
            addRenderableWidget(saveButton);
            updateSave();
        }

        addRenderableWidget(new CraftButton(width / 2 - 60, py + H - 26, 120, 20,
                Component.translatable("challengecraft.code.back"), CraftButton.Style.NEUTRAL, b -> onClose()));
    }

    private Component seedLabel() {
        return Component.translatable(withSeed ? "challengecraft.preset.seed_on" : "challengecraft.preset.seed_off");
    }

    private void updateSave() {
        if (saveButton != null) {
            saveButton.active = nameBox != null && !nameBox.getValue().isBlank();
        }
    }

    private void save() {
        ChallengeCode setup = withSeed ? current : new ChallengeCode(current.challengeIds(), current.perkIds(),
                current.maxHearts(), current.inventorySlots(), current.mobHealth(), current.doubleTrouble(),
                current.gameSpeed(), current.fibMinutes(), null);
        ChallengePresets.save(nameBox.getValue(), setup.encode());
        setStatus(Component.translatable("challengecraft.preset.saved", nameBox.getValue().trim()), CraftUI.SUCCESS);
        nameBox.setValue("");
        fillList();
    }

    private void fillList() {
        list.clearChildren();
        int y = list.getY() + 2;
        for (ChallengePresets.Preset preset : ChallengePresets.all()) {
            list.addChild(new PresetRow(list.getX() + 2, y, list.getWidth() - 12, preset));
            y += ROW_H + 2;
        }
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
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
        if (ChallengePresets.all().isEmpty()) {
            ctx.centeredText(font, Component.translatable("challengecraft.preset.empty"), px + W / 2, py + 70, CraftUI.TEXT_MUTED);
        }
        if (status != null) {
            ctx.text(font, CraftUI.trimToWidth(font, status.getString(), cw), cx, py + 134, statusColor, false);
        }
        if (current != null) {
            ctx.text(font, Component.translatable("challengecraft.preset.save_label"), cx, py + 148, CraftUI.TEXT_SECONDARY, false);
        }
        if (applyHint != null) {
            CraftUI.drawWrapped(ctx, font, applyHint, cx, py + 184, cw, CraftUI.TEXT_MUTED, 2);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    /** One preset: name, what is in it, and "Load" / "×" on the right. */
    private final class PresetRow extends AbstractWidget {
        private final ChallengePresets.Preset preset;
        private final ChallengeCode code;
        private final Component problem;

        PresetRow(int x, int y, int width, ChallengePresets.Preset preset) {
            super(x, y, width, ROW_H, Component.literal(preset.name()));
            this.preset = preset;
            ChallengeCode decoded = null;
            Component why = null;
            try {
                decoded = ChallengeCode.decode(preset.code());
                why = ChallengeCodeClient.usabilityProblem(decoded);
            } catch (ChallengeCode.InvalidCodeException e) {
                why = ChallengeCodeClient.describe(e.problem());
            }
            this.code = decoded;
            this.problem = why;
        }

        private int loadX() {
            return getX() + getWidth() - 62;
        }

        private int deleteX() {
            return getX() + getWidth() - 16;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            Font tr = Minecraft.getInstance().font;
            boolean usable = problem == null;
            CraftUI.panel(ctx, getX(), getY(), getWidth(), getHeight(), CraftUI.SURFACE_RAISED,
                    isHovered() ? CraftUI.ACCENT_HAIRLINE : CraftUI.BORDER, usable ? CraftUI.GOLD : CraftUI.DANGER);
            String name = CraftUI.trimToWidth(tr, preset.name(), getWidth() - 150);
            ctx.text(tr, name, getX() + 6, getY() + 7, usable ? CraftUI.TEXT_PRIMARY : CraftUI.TEXT_MUTED, false);
            if (code != null) {
                Component summary = Component.translatable("challengecraft.preset.summary",
                        code.challengeIds().size(), code.perkIds().size());
                ctx.text(tr, summary, getX() + getWidth() - 140, getY() + 7, CraftUI.TEXT_MUTED, false);
            }
            int lx = loadX();
            CraftUI.chip(ctx, lx, getY() + 3, 42, 16, CraftUI.SURFACE_BG, usable ? CraftUI.SUCCESS : CraftUI.BORDER);
            ctx.centeredText(tr, Component.translatable("challengecraft.preset.load"), lx + 21, getY() + 7,
                    usable ? CraftUI.SUCCESS : CraftUI.TEXT_MUTED);
            boolean armed = preset.name().equals(armedDelete);
            int dx = deleteX();
            CraftUI.chip(ctx, dx, getY() + 3, 12, 16, armed ? CraftUI.DANGER : CraftUI.SURFACE_BG, CraftUI.DANGER);
            ctx.centeredText(tr, Component.literal("×"), dx + 6, getY() + 7, armed ? 0xFF12161F : CraftUI.DANGER);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            double x = event.x();
            if (x >= deleteX()) {
                if (preset.name().equals(armedDelete)) {
                    ChallengePresets.delete(preset.name());
                    armedDelete = null;
                    setStatus(Component.translatable("challengecraft.preset.deleted", preset.name()), CraftUI.TEXT_MUTED);
                    fillList();
                } else {
                    armedDelete = preset.name();
                    setStatus(Component.translatable("challengecraft.preset.confirm_delete"), CraftUI.DANGER);
                }
                return;
            }
            armedDelete = null;
            if (problem != null) {
                setStatus(problem, CraftUI.DANGER);
                return;
            }
            onApply.accept(code);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }
}
