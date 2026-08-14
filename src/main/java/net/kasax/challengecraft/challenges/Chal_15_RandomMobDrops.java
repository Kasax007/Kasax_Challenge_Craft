package net.kasax.challengecraft.challenges;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootTable;

/** Swaps mob loot tables through a stable per-seed entity mapping. */
public class Chal_15_RandomMobDrops {
    private static boolean active = false;
    private static List<EntityType<?>> ENTITY_LIST = null;
    private static final Map<EntityType<?>, ResourceKey<LootTable>> MAPPING_CACHE = new HashMap<>();
    private static long lastSeed = -1;
    
    private static final ThreadLocal<ServerLevel> currentWorld = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> bypassing = ThreadLocal.withInitial(() -> false);

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setCurrentWorld(ServerLevel world) {
        currentWorld.set(world);
    }

    public static ServerLevel getCurrentWorld() {
        return currentWorld.get();
    }

    public static boolean isBypassing() {
        return bypassing.get();
    }

    public synchronized static Optional<ResourceKey<LootTable>> getSwappedLootTableKey(EntityType<?> type, ServerLevel world) {
        if (bypassing.get()) {
            return type.getDefaultLootTable();
        }

        bypassing.set(true);
        try {
            long seed = world.getSeed();
            if (seed != lastSeed) {
                lastSeed = seed;
                MAPPING_CACHE.clear();
                ENTITY_LIST = null;
            }

            if (ENTITY_LIST == null) {
                ENTITY_LIST = BuiltInRegistries.ENTITY_TYPE.stream()
                        .filter(et -> et.getDefaultLootTable().isPresent())
                        .sorted(Comparator.comparing(et -> BuiltInRegistries.ENTITY_TYPE.getKey(et).toString()))
                        .collect(Collectors.toList());

                List<EntityType<?>> shuffled = new ArrayList<>(ENTITY_LIST);
                Random random = new Random(seed);
                Collections.shuffle(shuffled, random);

                for (int i = 0; i < ENTITY_LIST.size(); i++) {
                    MAPPING_CACHE.put(ENTITY_LIST.get(i), shuffled.get(i).getDefaultLootTable().get());
                }
            }

            ResourceKey<LootTable> swapped = MAPPING_CACHE.get(type);
            return swapped != null ? Optional.of(swapped) : type.getDefaultLootTable();
        } finally {
            bypassing.set(false);
        }
    }
}
