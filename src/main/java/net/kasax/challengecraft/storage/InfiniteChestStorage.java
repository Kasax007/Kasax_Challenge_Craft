package net.kasax.challengecraft.storage;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Stores arbitrarily large item counts while keeping item components part of the identity. */
public class InfiniteChestStorage {
    private final Map<ItemStackKey, Long> storedItems = new HashMap<>();
    private List<StorageEntry> cachedSortedList = null;

    public void addStack(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStackKey key = ItemStackKey.fromStack(stack);
        long count = stack.getCount();
        storedItems.put(key, storedItems.getOrDefault(key, 0L) + count);
        cachedSortedList = null;
    }

    public void addItems(ItemStackKey key, long count) {
        if (count <= 0) return;
        storedItems.put(key, storedItems.getOrDefault(key, 0L) + count);
        cachedSortedList = null;
    }

    public long removeItems(ItemStackKey key, long count) {
        long current = storedItems.getOrDefault(key, 0L);
        long toRemove = Math.min(current, count);
        if (toRemove > 0) {
            long remaining = current - toRemove;
            if (remaining > 0) {
                storedItems.put(key, remaining);
            } else {
                storedItems.remove(key);
            }
            cachedSortedList = null;
        }
        return toRemove;
    }

    public List<StorageEntry> getSortedEntries() {
        if (cachedSortedList == null) {
            cachedSortedList = storedItems.entrySet().stream()
                    .map(e -> new StorageEntry(e.getKey(), e.getValue()))
                    .sorted((e1, e2) -> {
                        int cmp = Long.compare(e2.count(), e1.count());
                        if (cmp == 0) {
                            return BuiltInRegistries.ITEM.getKey(e1.key().item()).toString().compareTo(BuiltInRegistries.ITEM.getKey(e2.key().item()).toString());
                        }
                        return cmp;
                    })
                    .collect(Collectors.toList());
        }
        return cachedSortedList;
    }

    public List<StorageEntry> getFilteredEntries(String search) {
        List<StorageEntry> sorted = getSortedEntries();
        if (search == null || search.isEmpty()) return sorted;
        String lowerSearch = search.toLowerCase(Locale.ROOT);
        return sorted.stream()
                .filter(e -> new ItemStack(e.key().item()).getItemName().getString().toLowerCase(Locale.ROOT).contains(lowerSearch))
                .collect(Collectors.toList());
    }

    public void readNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        storedItems.clear();
        if (nbt == null) return;
        
        nbt.getList("Items").ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
                list.getCompound(i).ifPresent(entry -> {
                    long count = entry.getLong("count").orElse(0L);
                    entry.getCompound("stack").ifPresent(itemNbt -> {
                        // 26.2 removed ItemStack.parse(HolderLookup.Provider, Tag); the Codec is
                        // now the only entry point. RegistryOps supplies the registry context that
                        // the old overload took as an argument.
                        Optional<ItemStack> stack = ItemStack.CODEC
                                .parse(registries.createSerializationContext(NbtOps.INSTANCE), itemNbt)
                                .resultOrPartial(error -> ChallengeCraft.LOGGER.warn(
                                        "[InfiniteChest] Could not read stored stack: {}", error));
                        stack.ifPresent(s -> {
                            if (!s.isEmpty() && count > 0) {
                                storedItems.put(ItemStackKey.fromStack(s), count);
                            }
                        });
                    });
                });
            }
        });
        cachedSortedList = null;
    }

    public void writeNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<ItemStackKey, Long> entry : storedItems.entrySet()) {
            CompoundTag compound = new CompoundTag();
            ItemStack stack = entry.getKey().toStack(1);
            // 26.2 removed ItemStack.save(HolderLookup.Provider); encode through the Codec instead.
            ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                    .resultOrPartial(error -> ChallengeCraft.LOGGER.warn(
                            "[InfiniteChest] Could not write stored stack: {}", error))
                    .ifPresent(tag -> compound.put("stack", tag));
            compound.putLong("count", entry.getValue());
            list.add(compound);
        }
        nbt.put("Items", list);
    }

    public Map<ItemStackKey, Long> getStoredItems() {
        return Collections.unmodifiableMap(storedItems);
    }

    public void clear() {
        storedItems.clear();
        cachedSortedList = null;
    }

    public boolean isEmpty() {
        return storedItems.isEmpty();
    }

    public record ItemStackKey(Item item, DataComponentMap components) {
        public static ItemStackKey fromStack(ItemStack stack) {
            return new ItemStackKey(stack.getItem(), stack.getComponents());
        }

        public ItemStack toStack(int count) {
            ItemStack stack = new ItemStack(item, count);
            stack.applyComponents(components);
            return stack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemStackKey that = (ItemStackKey) o;
            return Objects.equals(item, that.item) && Objects.equals(components, that.components);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, components);
        }
    }

    public record StorageEntry(ItemStackKey key, long count) {}
}
