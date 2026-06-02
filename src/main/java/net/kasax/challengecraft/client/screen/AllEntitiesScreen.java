package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.challenges.Chal_23_AllEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Scrollable list view for the ordered all-entities run. */
public class AllEntitiesScreen extends Screen {
    private final List<EntityType<?>> entities;
    private final int currentIndex;
    private EntityListWidget list;

    public AllEntitiesScreen(List<EntityType<?>> entities, int currentIndex) {
        super(Component.translatable("challengecraft.all_entities_list.title"));
        this.entities = entities;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        this.list = new EntityListWidget();
        this.addRenderableWidget(this.list);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.list.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    class EntityListWidget extends ObjectSelectionList<EntityListWidget.Entry> {
        public EntityListWidget() {
            super(AllEntitiesScreen.this.minecraft, AllEntitiesScreen.this.width, AllEntitiesScreen.this.height - 60, 30, 20);
            for (int i = 0; i < entities.size(); i++) {
                this.addEntry(new net.kasax.challengecraft.client.screen.AllEntitiesScreen.EntityListWidget.Entry(i, entities.get(i)));
            }
            if (currentIndex >= 0 && currentIndex < this.getItemCount()) {
                this.setSelected(this.children().get(currentIndex));
            }
        }

        class Entry extends ObjectSelectionList.Entry<net.kasax.challengecraft.client.screen.AllEntitiesScreen.EntityListWidget.Entry> {
            private final int index;
            private final EntityType<?> type;
            private final ItemStack icon;

            public Entry(int index, EntityType<?> type) {
                this.index = index;
                this.type = type;
                this.icon = Chal_23_AllEntities.getIcon(type);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                int x = this.getContentX();
                int y = this.getContentY();
                int entryWidth = this.getContentWidth();
                boolean collected = this.index < currentIndex;
                boolean current = this.index == currentIndex;
                
                ChatFormatting color = collected ? ChatFormatting.GREEN : (current ? ChatFormatting.GOLD : ChatFormatting.GRAY);
                Component name = type.getDescription().copy().withStyle(color);
                
                if (current) name = Component.empty().append("> ").append(name);
                
                context.item(icon, x + 5, y);
                context.text(minecraft.font, name, x + 25, y + 5, 0xFFFFFF);
                
                if (collected) {
                    context.text(minecraft.font, Component.empty().append("✓").withStyle(ChatFormatting.GREEN), x + entryWidth - 20, y + 5, 0xFFFFFF);
                }
            }

            @Override
            public Component getNarration() {
                return type.getDescription();
            }
        }
    }
}
