package net.kasax.challengecraft.challenges;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import java.util.*;

public class Chal_20_RandomizedCrafting {
    private static boolean active = false;
    private static final Map<RecipeType<?>, Map<RegistryKey<Recipe<?>>, RecipeEntry<?>>> SHUFFLED_BY_TYPE = new HashMap<>();
    private static long currentSeed = -1;

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void shuffleRecipes(MinecraftServer server) {
        long seed = server.getOverworld().getSeed();
        if (seed == currentSeed && !SHUFFLED_BY_TYPE.isEmpty()) return;
        currentSeed = seed;
        SHUFFLED_BY_TYPE.clear();

        ServerRecipeManager manager = (ServerRecipeManager) server.getRecipeManager();
        Collection<RecipeEntry<?>> allRecipes = manager.values();

        Map<RecipeType<?>, List<RecipeEntry<?>>> grouped = new HashMap<>();
        for (RecipeEntry<?> entry : allRecipes) {
            RecipeType<?> type = entry.value().getType();
            List<RecipeEntry<?>> group = grouped.get(type);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(type, group);
            }
            group.add(entry);
        }

        for (Map.Entry<RecipeType<?>, List<RecipeEntry<?>>> groupEntry : grouped.entrySet()) {
            List<RecipeEntry<?>> list = groupEntry.getValue();
            list.sort(Comparator.comparing(e -> e.id().getValue().toString()));

            List<RecipeEntry<?>> shuffled = new ArrayList<>(list);
            Collections.shuffle(shuffled, new Random(seed + Registries.RECIPE_TYPE.getRawId(groupEntry.getKey())));

            Map<RegistryKey<Recipe<?>>, RecipeEntry<?>> typeMap = new HashMap<>();
            for (int i = 0; i < list.size(); i++) {
                typeMap.put(list.get(i).id(), shuffled.get(i));
            }
            SHUFFLED_BY_TYPE.put(groupEntry.getKey(), typeMap);
        }
    }

    public static <T extends Recipe<?>> Optional<RecipeEntry<T>> getShuffledEntry(RecipeType<T> type, Optional<RecipeEntry<T>> original, MinecraftServer server) {
        if (!active || original.isEmpty()) return original;
        shuffleRecipes(server);
        Map<RegistryKey<Recipe<?>>, RecipeEntry<?>> typeMap = SHUFFLED_BY_TYPE.get(type);
        if (typeMap != null) {
            RecipeEntry<?> shuffled = typeMap.get(original.get().id());
            if (shuffled != null) {
                // This cast is safe because we shuffle within the same RecipeType.
                return Optional.of((RecipeEntry<T>) shuffled);
            }
        }
        return original;
    }
}
