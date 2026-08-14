package net.kasax.challengecraft.challenges;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.*;

/** Shuffles crafting outputs within each recipe type while preserving recipe inputs. */
public class Chal_20_RandomizedCrafting {
    private static boolean active = false;
    private static final Map<RecipeType<?>, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>>> SHUFFLED_BY_TYPE = new HashMap<>();
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
        long seed = server.overworld().getSeed();
        if (seed == currentSeed && !SHUFFLED_BY_TYPE.isEmpty()) return;
        currentSeed = seed;
        SHUFFLED_BY_TYPE.clear();

        RecipeManager manager = (RecipeManager) server.getRecipeManager();
        Collection<RecipeHolder<?>> allRecipes = manager.getRecipes();

        Map<RecipeType<?>, List<RecipeHolder<?>>> grouped = new HashMap<>();
        for (RecipeHolder<?> entry : allRecipes) {
            Identifier id = entry.id().identifier();
            if (id.getNamespace().equals("challengecraft")) continue;

            RecipeType<?> type = entry.value().getType();
            List<RecipeHolder<?>> group = grouped.get(type);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(type, group);
            }
            group.add(entry);
        }

        for (Map.Entry<RecipeType<?>, List<RecipeHolder<?>>> groupEntry : grouped.entrySet()) {
            List<RecipeHolder<?>> list = groupEntry.getValue();
            list.sort(Comparator.comparing(e -> e.id().identifier().toString()));

            List<RecipeHolder<?>> shuffled = new ArrayList<>(list);
            Collections.shuffle(shuffled, new Random(seed + BuiltInRegistries.RECIPE_TYPE.getId(groupEntry.getKey())));

            Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> typeMap = new HashMap<>();
            for (int i = 0; i < list.size(); i++) {
                typeMap.put(list.get(i).id(), shuffled.get(i));
            }
            SHUFFLED_BY_TYPE.put(groupEntry.getKey(), typeMap);
        }
    }

    public static <T extends Recipe<?>> Optional<RecipeHolder<T>> getShuffledEntry(RecipeType<T> type, Optional<RecipeHolder<T>> original, MinecraftServer server) {
        if (!active || original.isEmpty()) return original;
        shuffleRecipes(server);
        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> typeMap = SHUFFLED_BY_TYPE.get(type);
        if (typeMap != null) {
            RecipeHolder<?> shuffled = typeMap.get(original.get().id());
            if (shuffled != null) {
                // This cast is safe because we shuffle within the same RecipeType.
                return Optional.of((RecipeHolder<T>) shuffled);
            }
        }
        return original;
    }
}
