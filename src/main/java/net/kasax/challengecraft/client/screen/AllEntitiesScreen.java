package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.challenges.Chal_23_AllEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Scrollable list view for the ordered all-entities run. */
public class AllEntitiesScreen extends CollectionScreen {
    public AllEntitiesScreen(List<EntityType<?>> entities, int currentIndex) {
        super(Text.translatable("challengecraft.all_entities_list.title"), toRows(entities), currentIndex);
    }

    private static List<Row> toRows(List<EntityType<?>> entities) {
        List<Row> rows = new ArrayList<>();
        for (EntityType<?> type : entities) {
            rows.add(new Row(Chal_23_AllEntities.getIcon(type), type.getName(), List.of()));
        }
        return rows;
    }
}
