package net.kasax.challengecraft.bot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

/**
 * What a player wears: the best armour it has, slot by slot; in the Nether one piece of gold
 * among it (piglins leave alone whoever wears gold), as long as it has one.
 */
public final class BotArmor {
    private BotArmor() {
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Armour points (and a little for toughness) of a piece; 0 for anything else. */
    public static double value(ItemStack st) {
        if (st.isEmpty()) return 0;
        double v = 0;
        for (var e : st.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().is(Attributes.ARMOR)) v += e.modifier().amount();
            else if (e.attribute().is(Attributes.ARMOR_TOUGHNESS)) v += e.modifier().amount() * 0.5;
        }
        // (Nearly broken: worth less, it is gone soon.)
        if (st.isDamageableItem() && st.getDamageValue() > st.getMaxDamage() * 0.9) v *= 0.5;
        return v;
    }

    public static boolean gold(ItemStack st) {
        return st.is(net.minecraft.tags.ItemTags.PIGLIN_SAFE_ARMOR);
    }

    /** Wears gold now (a piece that keeps piglins calm). */
    public static boolean wearsGold(BotPlayer body) {
        for (EquipmentSlot slot : SLOTS) if (gold(body.getItemBySlot(slot))) return true;
        return false;
    }

    /**
     * One change towards the best: puts on one better piece (or the gold piece the Nether wants),
     * the way a player right-clicks armour on. Returns whether it changed something.
     */
    public static boolean wearBest(Bot bot) {
        BotPlayer body = bot.body();
        var inv = body.getInventory().getNonEquipmentItems();
        boolean nether = body.level().dimension() == Level.NETHER;
        // The Nether: a gold piece first, where it costs least (a golden helmet is as good as an
        // iron one; boots next).
        if (nether && !wearsGold(body)) {
            int best = -1;
            double loss = Double.MAX_VALUE;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.get(i);
                if (!gold(s)) continue;
                Equippable eq = s.get(DataComponents.EQUIPPABLE);
                if (eq == null || !isArmorSlot(eq.slot())) continue;
                double l = value(body.getItemBySlot(eq.slot())) - value(s);
                if (l < loss) {
                    loss = l;
                    best = i;
                }
            }
            if (best >= 0) {
                put(body, best);
                bot.say("gold on for the piglins");
                return true;
            }
        }
        // A shield in the off hand (raised against arrows and blows in a fight).
        if (body.getOffhandItem().isEmpty()) {
            for (int i = 0; i < inv.size(); i++) {
                if (!inv.get(i).is(net.minecraft.world.item.Items.SHIELD)) continue;
                body.setItemSlot(EquipmentSlot.OFFHAND, inv.get(i).split(1));
                body.getInventory().setChanged();
                bot.say("shield in the off hand");
                return true;
            }
        }
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.isEmpty()) continue;
            Equippable eq = s.get(DataComponents.EQUIPPABLE);
            if (eq == null || !isArmorSlot(eq.slot())) continue;
            ItemStack worn = body.getItemBySlot(eq.slot());
            // (The one gold piece stays on in the Nether.)
            if (nether && gold(worn) && !gold(s) && goldPieces(body) <= 1) continue;
            if (value(s) > value(worn) + 0.01) {
                put(body, i);
                return true;
            }
        }
        return false;
    }

    private static int goldPieces(BotPlayer body) {
        int n = 0;
        for (EquipmentSlot slot : SLOTS) if (gold(body.getItemBySlot(slot))) n++;
        return n;
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static void put(BotPlayer body, int index) {
        var inv = body.getInventory().getNonEquipmentItems();
        ItemStack s = inv.get(index);
        Equippable eq = s.get(DataComponents.EQUIPPABLE);
        ItemStack old = body.getItemBySlot(eq.slot());
        body.setItemSlot(eq.slot(), s.split(1));
        if (!old.isEmpty()) net.kasax.challengecraft.bot.task.CraftTask.give(body, old);
        body.getInventory().setChanged();
    }
}
