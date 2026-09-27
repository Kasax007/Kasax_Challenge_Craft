package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.challenges.Chal_49_AllBiomes;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.ui.HudCard;
import net.kasax.challengecraft.network.AllBiomesSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HUD card and client copy of the All Biomes (49) checklist.
 *
 * <p>The card names the biome the player is standing in and whether it already counts — the one
 * thing worth knowing while exploring — with the overall count on the right. The data behind it is
 * this class's own copy, kept apart from the server's saved data (singleplayer shares statics).
 */
@Environment(EnvType.CLIENT)
public final class AllBiomesHUD {
    private static List<String> all = List.of();
    private static List<Integer> dimensions = List.of();
    private static Set<String> visited = new HashSet<>();

    private AllBiomesHUD() {
    }

    public static void update(AllBiomesSyncPacket packet) {
        all = packet.all;
        dimensions = packet.dimensions;
        visited = new HashSet<>(packet.visited);
    }

    public static void reset() {
        all = List.of();
        dimensions = List.of();
        visited = new HashSet<>();
    }

    public static List<String> all() {
        return all;
    }

    public static int dimensionOf(int index) {
        return index < dimensions.size() ? dimensions.get(index) : AllBiomesSyncPacket.OVERWORLD;
    }

    public static boolean isVisited(String id) {
        return visited.contains(id);
    }

    public static int foundCount() {
        int n = 0;
        for (String id : all) {
            if (visited.contains(id)) n++;
        }
        return n;
    }

    public static Component biomeName(String id) {
        return Component.translatable(Identifier.parse(id).toLanguageKey("biome"));
    }

    public static HudCard buildCard() {
        Minecraft mc = Minecraft.getInstance();
        if (!Chal_49_AllBiomes.isActive() || mc.player == null || mc.level == null) {
            return null;
        }
        int found = foundCount();
        int total = all.size();
        String here = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(k -> k.identifier().toString()).orElse(null);
        Component title;
        int accent;
        if (here == null || total == 0) {
            title = Component.translatable("challengecraft.worldcreate.challenge49");
            accent = CraftUI.GOLD;
        } else if (visited.contains(here)) {
            title = Component.translatable("challengecraft.all_biomes.hud.known", biomeName(here));
            accent = CraftUI.SUCCESS;
        } else {
            title = Component.translatable("challengecraft.all_biomes.hud.new", biomeName(here));
            accent = CraftUI.GOLD;
        }
        return new HudCard("all_biomes", new ItemStack(Items.COMPASS), title,
                Component.literal(found + " / " + total), total == 0 ? 0f : found / (float) total, accent, found);
    }
}
