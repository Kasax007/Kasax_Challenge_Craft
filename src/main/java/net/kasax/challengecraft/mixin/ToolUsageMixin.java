package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_31_CorrosiveTools;
import net.minecraft.world.item.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
/** Hooks item damage so corrosive tools can add extra durability loss. */
public abstract class ToolUsageMixin {

    private static final Map<Item, Item> DOWNGRADES = Map.ofEntries(
            Map.entry(Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE),
            Map.entry(Items.DIAMOND_PICKAXE, Items.GOLDEN_PICKAXE),
            Map.entry(Items.GOLDEN_PICKAXE, Items.IRON_PICKAXE),
            Map.entry(Items.IRON_PICKAXE, Items.STONE_PICKAXE),
            Map.entry(Items.STONE_PICKAXE, Items.WOODEN_PICKAXE),

            Map.entry(Items.NETHERITE_AXE, Items.DIAMOND_AXE),
            Map.entry(Items.DIAMOND_AXE, Items.GOLDEN_AXE),
            Map.entry(Items.GOLDEN_AXE, Items.IRON_AXE),
            Map.entry(Items.IRON_AXE, Items.STONE_AXE),
            Map.entry(Items.STONE_AXE, Items.WOODEN_AXE),

            Map.entry(Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL),
            Map.entry(Items.DIAMOND_SHOVEL, Items.GOLDEN_SHOVEL),
            Map.entry(Items.GOLDEN_SHOVEL, Items.IRON_SHOVEL),
            Map.entry(Items.IRON_SHOVEL, Items.STONE_SHOVEL),
            Map.entry(Items.STONE_SHOVEL, Items.WOODEN_SHOVEL),

            Map.entry(Items.NETHERITE_HOE, Items.DIAMOND_HOE),
            Map.entry(Items.DIAMOND_HOE, Items.GOLDEN_HOE),
            Map.entry(Items.GOLDEN_HOE, Items.IRON_HOE),
            Map.entry(Items.IRON_HOE, Items.STONE_HOE),
            Map.entry(Items.STONE_HOE, Items.WOODEN_HOE),

            Map.entry(Items.NETHERITE_SWORD, Items.DIAMOND_SWORD),
            Map.entry(Items.DIAMOND_SWORD, Items.GOLDEN_SWORD),
            Map.entry(Items.GOLDEN_SWORD, Items.IRON_SWORD),
            Map.entry(Items.IRON_SWORD, Items.STONE_SWORD),
            Map.entry(Items.STONE_SWORD, Items.WOODEN_SWORD)
    );

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void onDurabilityChange(int damage, ServerLevel world, @Nullable ServerPlayer player, Consumer<Item> breakCallback, CallbackInfo ci) {
        if (Chal_31_CorrosiveTools.isActive() && player != null) {
            ItemStack stack = (ItemStack) (Object) this;
            if (damage > 0) {
                if (player.getRandom().nextFloat() < 0.05f) {
                    Item currentItem = stack.getItem();
                    Item downgraded = DOWNGRADES.get(currentItem);
                    
                    if (downgraded != null) {
                        // Copy custom data without carrying tier-specific base attributes across materials.
                        ItemStack newStack = stack.transmuteCopy(downgraded, stack.getCount());
                        
                        int oldMax = stack.getMaxDamage();
                        int newMax = newStack.getMaxDamage();
                        
                        if (oldMax > 0 && newMax > 0) {
                            double damageRatio = (double) (stack.getDamageValue() + damage) / oldMax;
                            int newDamage = (int) Math.round(damageRatio * newMax);
                            newStack.setDamageValue(Math.min(newDamage, newMax - 1));
                        }
                        
                        boolean replaced = false;
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            if (player.getInventory().getItem(i) == stack) {
                                player.getInventory().setItem(i, newStack);
                                replaced = true;
                            }
                        }
                        
                        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                            if (player.getItemBySlot(slot) == stack) {
                                player.setItemSlot(slot, newStack);
                                replaced = true;
                            }
                        }
                        
                        // Some call sites hand us an equivalent stack instance rather than the inventory reference.
                        if (!replaced) {
                            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                                if (ItemStack.isSameItemSameComponents(player.getInventory().getItem(i), stack)) {
                                    player.getInventory().setItem(i, newStack);
                                    replaced = true;
                                    break;
                                }
                            }
                        }
                        
                        if (replaced) {
                            ci.cancel();
                        }
                    }
                }
            }
        }
    }
}
