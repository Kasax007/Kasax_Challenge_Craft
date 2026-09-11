package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.config.TimerSettings;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;

/**
 * Customisation for the run timer, reached from Video Settings.
 *
 * <p>It exists because the timer sat on top of the held-item name and there was nothing a player
 * could do about it. Rather than only moving the default out of the way — which just picks a
 * different set of people to annoy, since every HUD mod puts something somewhere different — the
 * position, size, colour and styling are all the player's.
 *
 * <p>The timer itself is drawn live at its real screen position through
 * {@link TimerOverlay#draw}, the same method the HUD uses, and can be dragged straight to where it
 * should go. A preview that is the actual renderer cannot lie about the result, and dragging beats
 * two number sliders for a task that is inherently spatial. The sliders stay for precision.
 */
@Environment(EnvType.CLIENT)
public class TimerSettingsScreen extends Screen {
    /** A palette wide enough that "my own colour" is one click for most people. */
    private static final int[] SWATCHES = {
            0xF2C23B, 0xFFFFFF, 0x7BE0A4, 0x5FC9F0, 0xB388FF,
            0xFF7BC2, 0xF06A63, 0xFF9E4A, 0x9AA7B8, 0x1A1A1A
    };
    private static final String SAMPLE = "12:34";

    private final Screen parent;
    private final TimerSettings s = TimerSettings.get();

    private WidgetScrollPanel panel;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    /** Set while the player is dragging the preview, with the grab offset inside the box. */
    private boolean dragging;
    private int grabDx;
    private int grabDy;

    public TimerSettingsScreen(Screen parent) {
        super(Component.translatable("challengecraft.timer.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(320, this.width - 20);
        panelH = Math.min(this.height - 34 - 40, 208);
        panelX = 10;
        panelY = 34;

        this.panel = new WidgetScrollPanel(panelX, panelY, panelW, panelH, this.title);
        addRenderableWidget(this.panel);
        buildRows();

        addRenderableWidget(new CraftButton(this.width / 2 - 105, this.height - 28, 100, 20,
                Component.translatable("challengecraft.timer.reset"), CraftButton.Style.NEUTRAL,
                b -> {
                    s.resetToDefaults();
                    s.save();
                    rebuild();
                }));
        addRenderableWidget(new CraftButton(this.width / 2 + 5, this.height - 28, 100, 20,
                Component.translatable("gui.done"), CraftButton.Style.PRIMARY,
                b -> onClose()));
    }

    /** Rebuilt whenever a control changes something another control displays. */
    private void rebuild() {
        this.panel.clearChildren();
        buildRows();
    }

    private void buildRows() {
        int x = panelX + 8;
        int w = panelW - 22;
        int[] y = {panelY + 6};

        row(y, header("challengecraft.timer.section.placement"));
        row(y, toggle("challengecraft.timer.visible", x, w, () -> s.visible, v -> s.visible = v));
        float[] auto = TimerOverlay.autoFraction(this.font, SAMPLE, s, this.width, this.height);
        float curX = s.customPosition ? s.x : auto[0];
        float curY = s.customPosition ? s.y : auto[1];
        row(y, positionSlider("challengecraft.timer.x", x, w, curX, this.width, v -> {
            s.x = v;
            takeOverPosition();
        }));
        row(y, positionSlider("challengecraft.timer.y", x, w, curY, this.height, v -> {
            s.y = v;
            takeOverPosition();
        }));
        row(y, cycle("challengecraft.timer.align", x, w,
                () -> Component.translatable("challengecraft.timer.align." + s.align.name().toLowerCase()),
                () -> s.align = TimerSettings.Align.values()[
                        (s.align.ordinal() + 1) % TimerSettings.Align.values().length]));
        row(y, slider("challengecraft.timer.scale", x, w, (s.scale - 0.5f) / 3.5f, 0f, 1f,
                v -> s.scale = Math.round((0.5f + v * 3.5f) * 20f) / 20f, false));
        row(y, new CraftButton(x, 0, w, 20,
                Component.translatable("challengecraft.timer.recenter"),
                CraftButton.Style.NEUTRAL, b -> {
            s.customPosition = false;
            s.save();
            rebuild();
        }));

        row(y, header("challengecraft.timer.section.colour"));
        row(y, swatches(x, w));
        float[] hsb = Color.RGBtoHSB((s.color >> 16) & 0xFF, (s.color >> 8) & 0xFF, s.color & 0xFF, null);
        row(y, slider("challengecraft.timer.hue", x, w, hsb[0], 0f, 1f, v -> setHsb(0, v), true));
        row(y, slider("challengecraft.timer.saturation", x, w, hsb[1], 0f, 1f, v -> setHsb(1, v), true));
        row(y, slider("challengecraft.timer.brightness", x, w, hsb[2], 0f, 1f, v -> setHsb(2, v), true));

        row(y, header("challengecraft.timer.section.style"));
        row(y, toggle("challengecraft.timer.pulse", x, w, () -> s.pulse, v -> s.pulse = v));
        row(y, toggle("challengecraft.timer.shine", x, w, () -> s.shine, v -> s.shine = v));
        row(y, cycle("challengecraft.timer.outline", x, w,
                () -> Component.translatable("challengecraft.timer.outline." + s.outline.name().toLowerCase()),
                () -> s.outline = TimerSettings.Outline.values()[
                        (s.outline.ordinal() + 1) % TimerSettings.Outline.values().length]));
        row(y, toggle("challengecraft.timer.background", x, w, () -> s.background,
                v -> {
                    s.background = v;
                    rebuild();
                }));
        if (s.background) {
            row(y, slider("challengecraft.timer.background_opacity", x, w, s.backgroundOpacity,
                    0f, 1f, v -> s.backgroundOpacity = v, true));
        }
        row(y, cycle("challengecraft.timer.format", x, w,
                () -> Component.translatable("challengecraft.timer.format." + s.format.name().toLowerCase()),
                () -> s.format = TimerSettings.Format.values()[
                        (s.format.ordinal() + 1) % TimerSettings.Format.values().length]));
    }

    private void row(int[] y, AbstractWidget widget) {
        widget.setY(y[0]);
        this.panel.addChild(widget);
        y[0] += widget.getHeight() + 4;
    }

    /** A section rule. Its own widget only because scroll-panel rows have to be widgets. */
    private AbstractWidget header(String key) {
        int hx = panelX + 8;
        int hw = panelW - 22;
        return new AbstractWidget(hx, 0, hw, 16, Component.translatable(key)) {
            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mx, int my, float dt) {
                CraftUI.sectionHeader(ctx, TimerSettingsScreen.this.font, getMessage(),
                        getX(), getY(), getWidth(), CraftUI.GOLD);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                return false;
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
                out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
            }
        };
    }

    private AbstractWidget toggle(String key, int x, int w,
                                  java.util.function.BooleanSupplier read,
                                  java.util.function.Consumer<Boolean> write) {
        return new CraftButton(x, 0, w, 20, label(key, read.getAsBoolean()),
                CraftButton.Style.NEUTRAL, b -> {
            boolean next = !read.getAsBoolean();
            write.accept(next);
            s.save();
            b.setMessage(label(key, next));
        });
    }

    private AbstractWidget cycle(String key, int x, int w,
                                 java.util.function.Supplier<Component> value,
                                 Runnable advance) {
        return new CraftButton(x, 0, w, 20,
                Component.translatable(key).append(": ").append(value.get()),
                CraftButton.Style.NEUTRAL, b -> {
            advance.run();
            s.save();
            b.setMessage(Component.translatable(key).append(": ").append(value.get()));
        });
    }

    private Component label(String key, boolean on) {
        return Component.translatable(key).append(": ")
                .append(Component.translatable(on ? "options.on" : "options.off")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    /**
     * A position slider, shown in screen pixels and nudgeable one pixel at a time.
     *
     * <p>Percentages were the wrong unit for this: 1 % of a 480-wide screen is five pixels, so the
     * bar simply could not express "two pixels further left". The arrow keys move exactly one
     * screen pixel, which is as fine as the position can meaningfully get.
     *
     * @param span the screen dimension the fraction is measured against, for the pixel readout
     */
    private AbstractWidget positionSlider(String key, int x, int w, float initial, int span,
                                          java.util.function.Consumer<Float> write) {
        return new AbstractSliderButton(x, 0, w, 20, Component.empty(),
                Math.max(0f, Math.min(1f, initial))) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(key).append(": ")
                        .append(Math.round(this.value * span) + " px"));
            }

            @Override
            protected void applyValue() {
                write.accept((float) this.value);
                // Marked, not written: applyValue fires every frame of a drag.
                s.markDirty();
            }

            @Override
            public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                int step = event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
                        || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? -1
                        : event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
                        || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? 1 : 0;
                if (step == 0 || span <= 0) {
                    return super.keyPressed(event);
                }
                this.value = Math.max(0.0, Math.min(1.0, this.value + step / (double) span));
                applyValue();
                updateMessage();
                s.flush();
                return true;
            }
        };
    }

    /** The first deliberate move hands the position over from the automatic placement. */
    private void takeOverPosition() {
        s.customPosition = true;
    }

    /**
     * A slider over a normalised 0..1 value.
     *
     * @param percent whether to show the value as a percentage; scale shows its real multiplier
     */
    private AbstractWidget slider(String key, int x, int w, float initial, float lo, float hi,
                                  java.util.function.Consumer<Float> write, boolean percent) {
        return new AbstractSliderButton(x, 0, w, 20, Component.empty(),
                Math.max(lo, Math.min(hi, initial))) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                String shown = percent
                        ? Math.round(this.value * 100) + "%"
                        : String.format("%.2f×", 0.5 + this.value * 3.5);
                setMessage(Component.translatable(key).append(": ").append(shown));
            }

            @Override
            protected void applyValue() {
                write.accept((float) this.value);
                s.markDirty();
            }
        };
    }

    /** The palette row, drawn and hit-tested as one widget so it costs one row instead of ten. */
    private AbstractWidget swatches(int x, int w) {
        TimerSettingsScreen self = this;
        return new AbstractWidget(x, 0, w, 18, Component.translatable("challengecraft.timer.presets")) {
            private int cell() {
                return Math.max(8, (getWidth() - 2) / SWATCHES.length);
            }

            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mx, int my, float dt) {
                int cell = cell();
                for (int i = 0; i < SWATCHES.length; i++) {
                    int cx = getX() + i * cell;
                    boolean chosen = (SWATCHES[i] & 0xFFFFFF) == (s.color & 0xFFFFFF);
                    CraftUI.chip(ctx, cx, getY(), cell - 2, getHeight(),
                            0xFF000000 | SWATCHES[i], chosen ? CraftUI.GOLD : CraftUI.BORDER);
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (!isMouseOver(event.x(), event.y())) {
                    return false;
                }
                int index = (int) ((event.x() - getX()) / cell());
                if (index < 0 || index >= SWATCHES.length) {
                    return false;
                }
                s.color = SWATCHES[index];
                s.save();
                // The hue/saturation/brightness sliders below now show stale positions.
                self.rebuild();
                return true;
            }

            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
                out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
            }
        };
    }

    private void setHsb(int component, float value) {
        float[] hsb = Color.RGBtoHSB((s.color >> 16) & 0xFF, (s.color >> 8) & 0xFF,
                s.color & 0xFF, null);
        hsb[component] = value;
        // Full black has no hue to remember, so a brightness slider dragged to zero would lose the
        // colour entirely. A floor keeps the other two sliders meaningful.
        if (component == 2) {
            hsb[2] = Math.max(0.08f, hsb[2]);
        }
        s.color = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
        s.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] box = TimerOverlay.bounds(this.font, SAMPLE, s, this.width, this.height);
        boolean onTimer = event.x() >= box[0] && event.x() < box[0] + box[2]
                && event.y() >= box[1] && event.y() < box[1] + box[3];
        // The panel wins ties: the timer can be dragged over the controls, and clicking a slider
        // underneath it must still hit the slider.
        boolean overPanel = event.x() >= panelX && event.x() < panelX + panelW
                && event.y() >= panelY && event.y() < panelY + panelH;
        if (onTimer && !overPanel) {
            dragging = true;
            grabDx = (int) event.x() - box[0];
            grabDy = (int) event.y() - box[1];
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            int[] box = TimerOverlay.bounds(this.font, SAMPLE, s, this.width, this.height);
            int left = (int) event.x() - grabDx;
            int top = (int) event.y() - grabDy;
            // Convert the box's corner back to the anchor the settings actually store.
            int anchorX = switch (s.align) {
                case LEFT -> left;
                case RIGHT -> left + box[2];
                case CENTER -> left + box[2] / 2;
            };
            s.x = Math.max(0f, Math.min(1f, anchorX / (float) this.width));
            s.y = Math.max(0f, Math.min(1f, (top + box[3] / 2f) / this.height));
            takeOverPosition();
            s.markDirty();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            s.flush();
            rebuild();
            return true;
        }
        boolean handled = super.mouseReleased(event);
        // End of a slider drag: this is where the queued write actually happens.
        s.flush();
        return handled;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);

        // The line the timer has to clear, drawn where vanilla really puts the held-item name, so
        // the whole reason this screen exists can be checked here instead of back in a world.
        //
        // Scenery, therefore drawn in the BACKGROUND layer. The first version drew it on top after
        // super.extractRenderState and a fake hotbar rectangle landed squarely over the Reset and
        // Done buttons, blanking their labels. A preview must never be able to cover a control.
        // Vanilla's real geometry, read out of the 26.2 Hud bytecode: the name is centred at
        // guiHeight - 59 (14 lower in creative) and drawn with textWithBackdrop, not a shadow. The
        // first version used a shadow and ignored the creative offset, so the preview disagreed
        // with what the player actually saw.
        int nameY = TimerOverlay.heldItemNameY(this.height);
        Component fakeItem = Component.translatable("challengecraft.timer.sample_item");
        int nameW = this.font.width(fakeItem);
        ctx.textWithBackdrop(this.font, fakeItem, (this.width - nameW) / 2, nameY, nameW,
                0xFFFFFFFF);
        String note = Component.translatable("challengecraft.timer.sample_hint").getString();
        ctx.text(this.font, note, this.width / 2 - this.font.width(note) / 2,
                nameY - this.font.lineHeight - 4, CraftUI.TEXT_MUTED, false);

        // A solid bar under the buttons so nothing behind them can ever bleed through again.
        CraftUI.panel(ctx, 0, this.height - 34, this.width, 34);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // The timer goes UNDER the widgets for the same reason: it is draggable and can be moved
        // anywhere, including on top of the controls.
        if (s.visible) {
            TimerOverlay.draw(ctx, this.font, SAMPLE, s, this.width, this.height,
                    dragging ? 0.75f : 1.0f);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        CraftUI.drawCenteredScaled(ctx, this.font, this.title, this.width / 2, 16, 1.2f,
                CraftUI.GOLD);

        String hint = Component.translatable("challengecraft.timer.drag_hint").getString();
        ctx.text(this.font, hint, this.width - this.font.width(hint) - 8, 8,
                CraftUI.TEXT_MUTED, false);
    }

    @Override
    public void onClose() {
        s.flush();
        s.save();
        this.minecraft.setScreenAndShow(parent);
    }
}
