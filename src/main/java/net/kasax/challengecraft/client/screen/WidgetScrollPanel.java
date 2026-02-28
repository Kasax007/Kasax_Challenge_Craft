package net.kasax.challengecraft.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class WidgetScrollPanel extends ScrollableWidget {
    private final List<ClickableWidget> children = new ArrayList<>();

    /**
     * Height of all content inside the panel, in pixels (relative to panel top).
     * Used to compute max scroll and scrollbar thumb size.
     */
    private int contentHeight = 0;

    public WidgetScrollPanel(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    public void clearChildren() {
        children.clear();
        contentHeight = 0;
        this.setScrollY(0);
    }

    /**
     * IMPORTANT: The widget's X/Y must be the "unscrolled" coordinates (normal screen coords).
     * This panel will render it at (y - scrollY) and also forward mouse events accordingly.
     */
    public void addChild(ClickableWidget widget) {
        children.add(widget);

        // Track the maximum bottom edge relative to our panel top
        int bottom = (widget.getY() + widget.getHeight()) - this.getY();
        contentHeight = Math.max(contentHeight, bottom);

        // Keep scroll clamped if content size changed
        this.refreshScroll();
    }

    public void forEachChild(Consumer<ClickableWidget> consumer) {
        children.forEach(consumer);
    }

    @Override
    protected int getContentsHeightWithPadding() {
        // small padding so the last widget isn't glued to the bottom
        return Math.max(this.contentHeight + 6, this.height);
    }

    @Override
    protected double getDeltaYPerScroll() {
        return 18.0;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Clip children rendering to the panel rect
        context.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());

        int scrollY = (int) Math.floor(this.getScrollY());

        for (ClickableWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            w.render(context, mouseX, mouseY, deltaTicks);
            w.setY(originalY);
        }

        context.disableScissor();

        // Draw vanilla scrollbar on top
        this.drawScrollbar(context);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) return false;

        // First: allow grabbing the scrollbar thumb
        if (this.checkScrollbarDragged(mouseX, mouseY, button)) {
            return true;
        }

        if (!this.isMouseOver(mouseX, mouseY)) return false;

        int scrollY = (int) Math.floor(this.getScrollY());

        for (ClickableWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            boolean handled = w.mouseClicked(mouseX, mouseY, button);
            w.setY(originalY);

            if (handled) return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.visible) return false;

        // Stop scrollbar dragging
        this.onRelease(mouseX, mouseY);

        int scrollY = (int) Math.floor(this.getScrollY());

        boolean any = false;
        for (ClickableWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            any |= w.mouseReleased(mouseX, mouseY, button);
            w.setY(originalY);
        }

        return any;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!this.visible) return false;

        // Let ScrollableWidget handle dragging the scrollbar thumb
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        int scrollY = (int) Math.floor(this.getScrollY());

        for (ClickableWidget w : children) {
            int originalY = w.getY();
            w.setY(originalY - scrollY);
            boolean handled = w.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        // optional
    }
}
