package net.kasax.challengecraft.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.block.InfiniteChestScreenHandler;
import net.kasax.challengecraft.network.InfiniteChestClickPayload;
import net.kasax.challengecraft.network.InfiniteChestSyncPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Client inventory screen for browsing and withdrawing infinite chest entries. */
public class InfiniteChestScreen extends AbstractContainerScreen<InfiniteChestScreenHandler> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/infinite_chest_gui.png");
    private EditBox searchField;
    private List<InfiniteChestSyncPayload.Entry> allEntries = new ArrayList<>();
    private List<InfiniteChestSyncPayload.Entry> filteredEntries = new ArrayList<>();
    private float scrollPosition = 0;
    private boolean scrolling = false;

    public InfiniteChestScreen(InfiniteChestScreenHandler handler, Inventory inventory, Component title) {
        // imageWidth/imageHeight are final in 26.2 — the size goes through the constructor now.
        super(handler, inventory, title, 194, 222);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.searchField = new EditBox(this.font, this.leftPos + 100, this.topPos + 6, 68, 10, Component.empty());
        this.searchField.setBordered(true);
        this.searchField.setTextColor(-1);
        this.searchField.setTextColorUneditable(-1);
        this.searchField.setCanLoseFocus(true);
        this.searchField.setHint(Component.translatable("challengecraft.gui.search"));
        this.searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchField);
    }

    private void onSearchChanged(String search) {
        updateFilteredEntries();
    }

    public void updateEntries(List<InfiniteChestSyncPayload.Entry> entries) {
        this.allEntries = new ArrayList<>(entries);
        this.allEntries.sort((e1, e2) -> {
            int cmp = Long.compare(e2.count(), e1.count());
            if (cmp == 0) {
                return BuiltInRegistries.ITEM.getKey(e1.stack().getItem()).toString().compareTo(BuiltInRegistries.ITEM.getKey(e2.stack().getItem()).toString());
            }
            return cmp;
        });
        updateFilteredEntries();
    }

    private void updateFilteredEntries() {
        String search = searchField.getValue().toLowerCase(Locale.ROOT);
        if (search.isEmpty()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            filteredEntries = allEntries.stream()
                    .filter(e -> e.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(search))
                    .toList();
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        context.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    /**
     * Replaces the old {@code renderBg}, which 26.2 removed. {@code extractContents} is not the
     * equivalent — disassembly shows it orchestrates labels, slot highlights and slots, so drawing
     * the panel there would paint over the items. {@code extractBackground} runs before all of
     * that, which is exactly where {@code renderBg} used to sit.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);

        context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        int i = (filteredEntries.size() + 8) / 9 - 6;
        int j = (int)(95.0F * this.scrollPosition);
        int u = 232 + (i > 0 ? 0 : 12);
        context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + 175, this.topPos + 17 + j, u, 0, 12, 15, 256, 256);

        int startEntry = (int) (scrollPosition * (Math.max(0, (filteredEntries.size() + 8) / 9 - 6)));
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int entryIndex = (startEntry + row) * 9 + col;
                if (entryIndex < filteredEntries.size()) {
                    InfiniteChestSyncPayload.Entry entry = filteredEntries.get(entryIndex);
                    int itemX = this.leftPos + 8 + col * 18;
                    int itemY = this.topPos + 18 + row * 18;

                    ItemStack stack = entry.stack();
                    context.item(stack, itemX, itemY);

                    String countStr = formatCount(entry.count());
                    // The old code nudged z by 200 to sit above the item. The GUI matrix is 2D now,
                    // so there is no z to nudge; the count is emitted after the item instead. If it
                    // ever renders behind, context.nextStratum() is the lever.
                    context.pose().pushMatrix();
                    float scale = 0.6f;
                    context.pose().scale(scale, scale);
                    context.text(this.font, countStr, (int) ((itemX + 17 - this.font.width(countStr) * scale) / scale), (int) ((itemY + 17 - 8 * scale) / scale), 0xFFFFFF, true);
                    context.pose().popMatrix();
                }
            }
        }
    }

    private String formatCount(long count) {
        if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000) return String.format("%.1fK", count / 1000.0);
        return String.valueOf(count);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.searchField.isFocused()) {
            if (event.isEscape()) {
                this.searchField.setFocused(false);
                return true;
            }
            // Swallow the inventory key while typing so "e" does not close the screen.
            if (this.minecraft.options.keyInventory.matches(event)) {
                return true;
            }
        }
        if (this.searchField.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchField.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (this.searchField.mouseClicked(event, doubleClick)) {
            this.setFocused(this.searchField);
            return true;
        }

        int scrollX = this.leftPos + 174;
        int scrollY = this.topPos + 17;
        if (mouseX >= scrollX && mouseX < scrollX + 14 && mouseY >= scrollY && mouseY < scrollY + 110) {
            this.scrolling = true;
            return true;
        }

        int relativeX = (int) (mouseX - this.leftPos);
        int relativeY = (int) (mouseY - this.topPos);

        if (relativeX >= 8 && relativeX < 8 + 9 * 18 && relativeY >= 18 && relativeY < 18 + 6 * 18) {
            if (!menu.getCarried().isEmpty()) {
                ClientPlayNetworking.send(new InfiniteChestClickPayload(menu.getPos(), ItemStack.EMPTY, -1, false));
                return true;
            }
            int col = (relativeX - 8) / 18;
            int row = (relativeY - 18) / 18;
            int startEntry = (int) (scrollPosition * (Math.max(0, (filteredEntries.size() + 8) / 9 - 6)));
            int entryIndex = (startEntry + row) * 9 + col;

            if (entryIndex < filteredEntries.size()) {
                InfiniteChestSyncPayload.Entry entry = filteredEntries.get(entryIndex);
                // Screen.hasShiftDown() is gone; the modifier now travels on the event itself
                // (MouseButtonEvent implements InputWithModifiers).
                boolean shift = event.hasShiftDown();
                ClientPlayNetworking.send(new InfiniteChestClickPayload(menu.getPos(), entry.stack(), event.button(), shift));
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.scrolling = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.scrolling) {
            this.scrollPosition = (float)((event.y() - (double)(this.topPos + 17)) / 95.0);
            this.scrollPosition = Math.max(0.0F, Math.min(1.0F, this.scrollPosition));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int i = (filteredEntries.size() + 8) / 9 - 6;
        if (i > 0) {
            scrollPosition = (float) ((double) scrollPosition - verticalAmount / (double) i);
            scrollPosition = Math.max(0, Math.min(1, scrollPosition));
        }
        return true;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        super.extractTooltip(context, mouseX, mouseY);

        int relativeX = mouseX - this.leftPos;
        int relativeY = mouseY - this.topPos;
        if (relativeX >= 8 && relativeX < 8 + 9 * 18 && relativeY >= 18 && relativeY < 18 + 6 * 18) {
            int col = (relativeX - 8) / 18;
            int row = (relativeY - 18) / 18;
            int startEntry = (int) (scrollPosition * (Math.max(0, (filteredEntries.size() + 8) / 9 - 6)));
            int entryIndex = (startEntry + row) * 9 + col;

            if (entryIndex < filteredEntries.size()) {
                InfiniteChestSyncPayload.Entry entry = filteredEntries.get(entryIndex);
                ItemStack stack = entry.stack();
                List<Component> tooltip = new ArrayList<>(getTooltipFromContainerItem(stack));
                tooltip.add(Component.translatable("challengecraft.infinite_chest.count", entry.count()).withStyle(net.minecraft.ChatFormatting.GRAY));
                context.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
            }
        }
    }
}
