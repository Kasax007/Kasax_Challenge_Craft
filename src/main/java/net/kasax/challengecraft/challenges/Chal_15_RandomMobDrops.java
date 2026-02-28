package net.kasax.challengecraft.challenges;

import net.minecraft.entity.EntityType;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Collectors;

public class Chal_15_RandomMobDrops {
    private static boolean active = false;
    private static List<EntityType<?>> ENTITY_LIST = null;
    private static final Map<EntityType<?>, RegistryKey<LootTable>> MAPPING_CACHE = new HashMap<>();
    private static long lastSeed = -1;
    
    private static final ThreadLocal<ServerWorld> currentWorld = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> bypassing = ThreadLocal.withInitial(() -> false);

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setCurrentWorld(ServerWorld world) {
        currentWorld.set(world);
    }

    public static ServerWorld getCurrentWorld() {
        return currentWorld.get();
    }

    public static boolean isBypassing() {
        return bypassing.get();
    }

    public synchronized static Optional<RegistryKey<LootTable>> getSwappedLootTableKey(EntityType<?> type, ServerWorld world) {
        if (bypassing.get()) {
            return type.getLootTableKey();
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
                ENTITY_LIST = Registries.ENTITY_TYPE.stream()
                        .filter(et -> et.getLootTableKey().isPresent())
                        .sorted(Comparator.comparing(et -> Registries.ENTITY_TYPE.getId(et).toString()))
                        .collect(Collectors.toList());

                List<EntityType<?>> shuffled = new ArrayList<>(ENTITY_LIST);
                Random random = new Random(seed);
                Collections.shuffle(shuffled, random);

                for (int i = 0; i < ENTITY_LIST.size(); i++) {
                    MAPPING_CACHE.put(ENTITY_LIST.get(i), shuffled.get(i).getLootTableKey().get());
                }
            }

            RegistryKey<LootTable> swapped = MAPPING_CACHE.get(type);
            return swapped != null ? Optional.of(swapped) : type.getLootTableKey();
        } finally {
            bypassing.set(false);
        }
    }
}
