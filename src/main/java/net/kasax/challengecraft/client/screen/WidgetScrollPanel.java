package net.kasax.challengecraft.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class WidgetScrollPanel extends AbstractScrollArea {
    /** Pixels scrolled per wheel notch. Also handed to the scrollbar settings below. */
    private static final int SCROLL_RATE = 18;

    private final List<AbstractWidget> children = new ArrayList<>();

    /**
     * Height of all content inside the panel, in pixels (relative to panel top).
     * Used to compute max scroll and scrollbar thumb size.
     */
    private int contentHeight = 0;

    public WidgetScrollPanel(int x, int y, int width, int height, Component message) {
        // 26.2 made the scrollbar's look and rate explicit constructor state. defaultSettings(int)
        // takes the SCROLL RATE (verified in the bytecode: its argument lands in the 6th component
        // of ScrollbarSettings, after the hardcoded width 6 and min-height 32) — not the width.
        super(x, y, width, height, message, AbstractScrollArea.defaultSettings(SCROLL_RATE));
    }

    public void clearChildren() {
        children.clear();
        contentHeight = 0;
        this.setScrollAmount(0);
    }

    /** Children keep their layout coordinates; scroll offset is applied only while rendering or dispatching input. */
    public void addChild(AbstractWidget widget) {
        children.add(widget);

        int bottom = (widget.getY() + widget.getHeight()) - this.getY();
        contentHeight = Math.max(contentHeight, bottom);
        this.refreshScrollAmount();
    }

    public void visitWidgets(Consumer<AbstractWidget> consumer) {
        children.forEach(consumer);
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     * How far the content is scrolled, in pixels.
     *
     * <p>Needed because a child's {@code getY()} is its LAYOUT position, not where it is on screen:
     * {@link #extractWidgetRenderState} shifts each child, draws it, and shifts it straight back, so
     * outside that method the scroll offset is invisible. Anything drawing over a child from another
     * component — the tutorial highlight, for one — has to subtract this itself or it points at empty
     * space as soon as the panel is scrolled.
     */
    public int scrollOffset() {
        return (int) Math.floor(this.scrollAmount());
    }

    public boolean contains(AbstractWidget widget) {
        return children.contains(widget);
    }

    /** Scrolls the panel, if needed, so the given child sits fully inside the visible band. */
    public void scrollIntoView(AbstractWidget widget) {
        if (!children.contains(widget)) return;

        int margin = 4;
        int top = widget.getY();                       // layout coordinates, i.e. scroll-independent
        int bottom = top + widget.getHeight();
        int scroll = scrollOffset();

        if (top - scroll < this.getY() + margin) {
            this.setScrollAmount(top - this.getY() - margin);
        } else if (bottom - scroll > this.getBottom() - margin) {
            this.setScrollAmount(bottom - this.getBottom() + margin);
        }
    }

    @Override
    protected int contentHeight() {
        return Math.max(this.contentHeight + 20, this.height);
    }

    @Override
    protected double scrollRate() {
        return SCROLL_RATE;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());

        int scrollY = (int) Math.floor(this.scrollAmount());

        for (AbstractWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            w.extractRenderState(context, mouseX, mouseY, deltaTicks);
            w.setY(originalY);
        }

        context.disableScissor();

        this.extractScrollbar(context, mouseX, mouseY);
    }

    // The mouse handlers below shift each child's Y rather than the cursor, so the event is passed
    // through to children untouched — same trick as before the port, just with the event object.

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.visible) return false;

        if (this.updateScrolling(event)) {
            return true;
        }

        if (!this.isMouseOver(event.x(), event.y())) return false;

        int scrollY = (int) Math.floor(this.scrollAmount());

        for (AbstractWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            boolean handled = w.mouseClicked(event, doubleClick);
            w.setY(originalY);

            if (handled) return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!this.visible) return false;

        this.onRelease(event);

        int scrollY = (int) Math.floor(this.scrollAmount());

        boolean any = false;
        for (AbstractWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            any |= w.mouseReleased(event);
            w.setY(originalY);
        }

        return any;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!this.visible) return false;

        if (super.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }

        int scrollY = (int) Math.floor(this.scrollAmount());

        for (AbstractWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            boolean handled = w.mouseDragged(event, deltaX, deltaY);
            w.setY(originalY);

            if (handled) return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.visible) return false;
        if (!this.isMouseOver(mouseX, mouseY)) return false;

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
    }
}
