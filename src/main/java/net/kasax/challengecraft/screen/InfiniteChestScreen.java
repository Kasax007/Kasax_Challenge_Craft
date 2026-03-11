package net.kasax.challengecraft.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.block.InfiniteChestScreenHandler;
import net.kasax.challengecraft.network.InfiniteChestClickPayload;
import net.kasax.challengecraft.network.InfiniteChestSyncPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public class InfiniteChestScreen extends HandledScreen<InfiniteChestScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of("challengecraft", "textures/gui/infinite_chest_gui.png");
    private TextFieldWidget searchField;
    private List<InfiniteChestSyncPayload.Entry> allEntries = new ArrayList<>();
    private List<InfiniteChestSyncPayload.Entry> filteredEntries = new ArrayList<>();
    private float scrollPosition = 0;
    private boolean scrolling = false;

    public InfiniteChestScreen(InfiniteChestScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 194;
        this.backgroundHeight = 222;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.searchField = new TextFieldWidget(this.textRenderer, this.x + 100, this.y + 6, 68, 10, Text.literal(""));
        this.searchField.setDrawsBackground(true);
        this.searchField.setEditableColor(-1);
        this.searchField.setUneditableColor(-1);
        this.searchField.setFocusUnlocked(true);
        this.searchField.setPlaceholder(Text.translatable("challengecraft.gui.search"));
        this.searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(this.searchField);
    }

    private void onSearchChanged(String search) {
        updateFilteredEntries();
    }

    public void updateEntries(List<InfiniteChestSyncPayload.Entry> entries) {
        this.allEntries = new ArrayList<>(entries);
        this.allEntries.sort((e1, e2) -> {
            int cmp = Long.compare(e2.count(), e1.count());
            if (cmp == 0) {
                return Registries.ITEM.getId(e1.stack().getItem()).toString().compareTo(Registries.ITEM.getId(e2.stack().getItem()).toString());
            }
            return cmp;
        });
        updateFilteredEntries();
    }

    private void updateFilteredEntries() {
        String search = searchField.getText().toLowerCase(Locale.ROOT);
        if (search.isEmpty()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            filteredEntries = allEntries.stream()
                    .filter(e -> e.stack().getName().getString().toLowerCase(Locale.ROOT).contains(search))
                    .toList();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 4210752, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 4210752, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
        
        // Scrollbar
        int i = (filteredEntries.size() + 8) / 9 - 6;
        int j = (int)(95.0F * this.scrollPosition);
        int u = 232 + (i > 0 ? 0 : 12);
        context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, TEXTURE, this.x + 175, this.y + 17 + j, u, 0, 12, 15, 256, 256);

        // Items
        int startEntry = (int) (scrollPosition * (Math.max(0, (filteredEntries.size() + 8) / 9 - 6)));
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int entryIndex = (startEntry + row) * 9 + col;
                if (entryIndex < filteredEntries.size()) {
                    InfiniteChestSyncPayload.Entry entry = filteredEntries.get(entryIndex);
                    int itemX = this.x + 8 + col * 18;
                    int itemY = this.y + 18 + row * 18;
                    
                    ItemStack stack = entry.stack();
                    context.drawItem(stack, itemX, itemY);
                    
                    String countStr = formatCount(entry.count());
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, 200);
                    float scale = 0.6f;
                    context.getMatrices().scale(scale, scale, 1.0f);
                    context.drawText(this.textRenderer, countStr, (int) ((itemX + 17 - this.textRenderer.getWidth(countStr) * scale) / scale), (int) ((itemY + 17 - 8 * scale) / scale), 0xFFFFFF, true);
                    context.getMatrices().pop();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchField.isFocused()) {
            if (keyCode == 256) {
                this.searchField.setFocused(false);
                return true;
            }
            if (this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
                return true;
            }
        }
        if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.searchField.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.searchField);
            return true;
        }
        
        // Scrollbar logic
        int scrollX = this.x + 174;
        int scrollY = this.y + 17;
        if (mouseX >= scrollX && mouseX < scrollX + 14 && mouseY >= scrollY && mouseY < scrollY + 110) {
            this.scrolling = true;
            return true;
        }

        // Check if item was clicked
        int relativeX = (int) (mouseX - this.x);
        int relativeY = (int) (mouseY - this.y);
        
        if (relativeX >= 8 && relativeX < 8 + 9 * 18 && relativeY >= 18 && relativeY < 18 + 6 * 18) {
            if (!handler.getCursorStack().isEmpty()) {
                ClientPlayNetworking.send(new InfiniteChestClickPayload(handler.getPos(), ItemStack.EMPTY, -1, false));
                return true;
            }
            int col = (relativeX - 8) / 18;
            int row = (relativeY - 18) / 18;
            int startEntry = (int) (scrollPosition * (Math.max(0, (filteredEntries.size() + 8) / 9 - 6)));
            int entryIndex = (startEntry + row) * 9 + col;
            
            if (entryIndex < filteredEntries.size()) {
                InfiniteChestSyncPayload.Entry entry = filteredEntries.get(entryIndex);
                boolean shift = hasShiftDown();
                ClientPlayNetworking.send(new InfiniteChestClickPayload(handler.getPos(), entry.stack(), button, shift));
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.scrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.scrolling) {
            this.scrollPosition = (float)((mouseY - (double)(this.y + 17)) / 95.0);
            this.scrollPosition = Math.max(0.0F, Math.min(1.0F, this.scrollPosition));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
    protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
        super.drawMouseoverTooltip(context, mouseX, mouseY);
        
        int relativeX = mouseX - this.x;
        int relativeY = mouseY - this.y;
        if (relativeX >= 8 && relativeX < 8 + 9 * 18 && relativeY >= 18 && relativeY < 18 + 6 * 18) {
            int col = (relativeX - 8) / 18;
            int row = (relativeY - 18) / 18;
            int startEntry = (int) (scrollPosition * (Math.max(0, (filteredEntries.size() + 8) / 9 - 6)));
            int entryIndex = (startEntry + row) * 9 + col;
            
            if (entryIndex < filteredEntries.size()) {
                InfiniteChestSyncPayload.Entry entry = filteredEntries.get(entryIndex);
                ItemStack stack = entry.stack();
                List<Text> tooltip = new ArrayList<>(getTooltipFromItem(stack));
                tooltip.add(Text.literal("Count: " + entry.count()).formatted(net.minecraft.util.Formatting.GRAY));
                context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
            }
        }
    }
}
