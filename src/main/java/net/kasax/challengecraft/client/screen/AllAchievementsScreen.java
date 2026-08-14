package net.kasax.challengecraft.client.screen;

import net.kasax.challengecraft.network.AdvancementInfo;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Scrollable list view for the ordered all-achievements run. */
public class AllAchievementsScreen extends CollectionScreen {
    public AllAchievementsScreen(List<AdvancementInfo> advancements, int currentIndex) {
        super(Component.translatable("challengecraft.all_achievements_list.title"), toRows(advancements), currentIndex);
    }

    private static List<Row> toRows(List<AdvancementInfo> advancements) {
        List<Row> rows = new ArrayList<>();
        for (AdvancementInfo info : advancements) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(info.title());
            if (info.description() != null) {
                tooltip.add(info.description());
            }
            rows.add(new Row(info.icon(), info.title(), tooltip));
        }
        return rows;
    }
}
