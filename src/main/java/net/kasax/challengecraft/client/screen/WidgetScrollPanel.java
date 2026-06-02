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
    private final List<AbstractWidget> children = new ArrayList<>();

    /**
     * Height of all content inside the panel, in pixels (relative to panel top).
     * Used to compute max scroll and scrollbar thumb size.
     */
    private int contentHeight = 0;

    public WidgetScrollPanel(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message, AbstractScrollArea.defaultSettings(6));
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

    @Override
    protected int contentHeight() {
        return Math.max(this.contentHeight + 20, this.height);
    }

    @Override
    protected double scrollRate() {
        return 18.0;
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.visible) return false;

        if (this.updateScrolling(event)) {
            return true;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        if (!this.isMouseOver(mouseX, mouseY)) return false;

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
