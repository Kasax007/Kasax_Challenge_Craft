package net.kasax.challengecraft.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Stores arbitrarily large item counts while keeping item components part of the identity. */
public class InfiniteChestStorage {
    private static final Codec<PersistedEntry> PERSISTED_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(PersistedEntry::stack),
            Codec.LONG.fieldOf("count").forGetter(PersistedEntry::count)
    ).apply(instance, PersistedEntry::new));

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
                .filter(e -> e.key().item().getName(e.key().toStack(1)).getString().toLowerCase(Locale.ROOT).contains(lowerSearch))
                .collect(Collectors.toList());
    }

    public void read(ValueInput input) {
        storedItems.clear();
        for (PersistedEntry entry : input.listOrEmpty("Items", PERSISTED_ENTRY_CODEC)) {
            ItemStack stack = entry.stack();
            long count = entry.count();
            if (!stack.isEmpty() && count > 0) {
                storedItems.put(ItemStackKey.fromStack(stack), count);
            }
        }
        cachedSortedList = null;
    }

    public void write(ValueOutput output) {
        ValueOutput.TypedOutputList<PersistedEntry> list = output.list("Items", PERSISTED_ENTRY_CODEC);
        for (Map.Entry<ItemStackKey, Long> entry : storedItems.entrySet()) {
            list.add(new PersistedEntry(entry.getKey().toStack(1), entry.getValue()));
        }
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

    private record PersistedEntry(ItemStack stack, long count) {}
}
