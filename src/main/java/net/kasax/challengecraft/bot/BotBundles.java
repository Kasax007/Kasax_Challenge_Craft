package net.kasax.challengecraft.bot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Bundles, as a player uses them: odds and ends (a few flowers, saplings, seeds, a stray ingot)
 * share one slot instead of taking one each, so a full pack does not mean throwing things away.
 * What the plan wants again comes back out when there is room.
 */
public final class BotBundles {
    private BotBundles() {
    }

    /** The first bundle in the inventory, or null. */
    public static ItemStack bundle(BotPlayer body) {
        for (ItemStack s : body.getInventory().getNonEquipmentItems()) if (!s.isEmpty() && s.is(ItemTags.BUNDLES)) return s;
        return null;
    }

    public static int freeSlots(BotPlayer body) {
        int n = 0;
        for (ItemStack s : body.getInventory().getNonEquipmentItems()) if (s.isEmpty()) n++;
        return n;
    }

    /** How many of these are in its bundles. */
    public static int countInside(BotPlayer body, Set<Item> items) {
        int n = 0;
        for (ItemStack b : body.getInventory().getNonEquipmentItems()) {
            if (b.isEmpty() || !b.is(ItemTags.BUNDLES)) continue;
            BundleContents c = b.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            for (ItemStack s : c.itemCopies().toList()) if (items.contains(s.getItem())) n += s.getCount();
        }
        return n;
    }

    /**
     * Into the bundle with small stacks of things not wanted ({@code keep} stays out), until
     * {@code freeWanted} slots are free or the bundle is full. Returns the slots freed.
     */
    public static int stash(Bot bot, Set<Item> keep, int freeWanted) {
        BotPlayer body = bot.body();
        ItemStack bundle = bundle(body);
        if (bundle == null) return 0;
        var inv = body.getInventory().getNonEquipmentItems();
        BundleContents.Mutable m = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.isEmpty() || s == bundle || s.is(ItemTags.BUNDLES) || keep.contains(s.getItem())) continue;
            // Not tools, armour, food or what it builds with: small stacks of things stackable.
            if (s.getMaxStackSize() == 1 || s.get(DataComponents.FOOD) != null || BotActions.THROWAWAY.contains(s.getItem())) continue;
            if (!BundleContents.canItemBeInBundle(s)) continue;
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> inv.get(i).getCount() * (64 / Math.max(1, inv.get(i).getMaxStackSize()))));
        int freed = 0;
        for (int i : order) {
            if (freeSlots(body) >= freeWanted) break;
            ItemStack s = inv.get(i);
            int before = s.getCount();
            m.tryInsert(s);
            if (s.getCount() == before) continue;
            if (s.isEmpty()) {
                inv.set(i, ItemStack.EMPTY);
                freed++;
            }
        }
        bundle.set(DataComponents.BUNDLE_CONTENTS, m.toImmutable());
        body.getInventory().setChanged();
        if (freed > 0) bot.say("bundled up odds and ends: " + freed + " slots free");
        return freed;
    }

    /** Out of the bundles with what is wanted again, as far as there is room. Returns what came out. */
    public static int unpack(Bot bot, Set<Item> wanted) {
        BotPlayer body = bot.body();
        int out = 0;
        for (ItemStack b : body.getInventory().getNonEquipmentItems()) {
            if (b.isEmpty() || !b.is(ItemTags.BUNDLES)) continue;
            BundleContents c = b.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            if (c.isEmpty()) continue;
            List<ItemStack> stay = new ArrayList<>();
            for (ItemStack s : c.itemCopies().toList()) {
                if (wanted.contains(s.getItem()) && freeSlots(body) > 1) {
                    int n = s.getCount();
                    body.getInventory().add(s);
                    out += n - s.getCount();
                    if (!s.isEmpty()) stay.add(s);
                } else {
                    stay.add(s);
                }
            }
            b.set(DataComponents.BUNDLE_CONTENTS, c.copyWithContents(stay.stream()));
        }
        if (out > 0) {
            body.getInventory().setChanged();
            bot.say("took " + out + " things out of the bundle (wanted now)");
        }
        return out;
    }
}
