package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.challenges.Chal_23_AllEntities;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class AllEntitiesScreen extends Screen {
    private final List<EntityType<?>> entities;
    private final int currentIndex;
    private EntityListWidget list;

    public AllEntitiesScreen(List<EntityType<?>> entities, int currentIndex) {
        super(Text.translatable("challengecraft.all_entities_list.title"));
        this.entities = entities;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        this.list = new EntityListWidget();
        this.addDrawableChild(this.list);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.list.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    class EntityListWidget extends AlwaysSelectedEntryListWidget<EntityListWidget.Entry> {
        public EntityListWidget() {
            super(AllEntitiesScreen.this.client, AllEntitiesScreen.this.width, AllEntitiesScreen.this.height - 60, 30, 20);
            for (int i = 0; i < entities.size(); i++) {
                this.addEntry(new Entry(i, entities.get(i)));
            }
            if (currentIndex >= 0 && currentIndex < this.getEntryCount()) {
                this.setSelected(this.getEntry(currentIndex));
            }
        }

        class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final int index;
            private final EntityType<?> type;
            private final ItemStack icon;

            public Entry(int index, EntityType<?> type) {
                this.index = index;
                this.type = type;
                this.icon = Chal_23_AllEntities.getIcon(type);
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                boolean collected = this.index < currentIndex;
                boolean current = this.index == currentIndex;
                
                Formatting color = collected ? Formatting.GREEN : (current ? Formatting.GOLD : Formatting.GRAY);
                Text name = type.getName().copy().formatted(color);
                
                if (current) name = Text.literal("> ").append(name);
                
                context.drawItem(icon, x + 5, y);
                context.drawTextWithShadow(client.textRenderer, name, x + 25, y + 5, 0xFFFFFF);
                
                if (collected) {
                    context.drawTextWithShadow(client.textRenderer, Text.literal("✓").formatted(Formatting.GREEN), x + entryWidth - 20, y + 5, 0xFFFFFF);
                }
            }

            @Override
            public Text getNarration() {
                return type.getName();
            }
        }
    }
}
