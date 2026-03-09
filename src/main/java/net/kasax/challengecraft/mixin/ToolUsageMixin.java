package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_31_CorrosiveTools;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
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

    @Inject(method = "Lnet/minecraft/item/ItemStack;onDurabilityChange(ILnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void onDurabilityChange(int damage, @Nullable ServerPlayerEntity player, Consumer<Item> breakCallback, CallbackInfo ci) {
        if (Chal_31_CorrosiveTools.isActive() && player != null) {
            // New logic: Only roll if durability is actually decreasing (damage is increasing)
            ItemStack stack = (ItemStack) (Object) this;
            if (damage > stack.getDamage()) {
                if (player.getRandom().nextFloat() < 0.05f) { // 5% chance
                    Item currentItem = stack.getItem();
                    Item downgraded = DOWNGRADES.get(currentItem);
                    
                    if (downgraded != null) {
                        // Use copyComponentsToNewStack to only copy CHANGES (enchantments, custom names)
                        // instead of ALL components (which might include tier-specific attributes)
                        ItemStack newStack = stack.copyComponentsToNewStack(downgraded, stack.getCount());
                        
                        // Calculate new damage proportionally to avoid "resetting" or "breaking immediately"
                        int oldMax = stack.getMaxDamage();
                        int newMax = newStack.getMaxDamage();
                        
                        if (oldMax > 0 && newMax > 0) {
                            double damageRatio = (double) damage / oldMax;
                            int newDamage = (int) Math.round(damageRatio * newMax);
                            // Ensure it's not breaking it immediately unless it was already at max damage
                            newStack.setDamage(Math.min(newDamage, newMax - 1));
                        }
                        
                        // Replace the stack in the inventory and all equipment slots
                        boolean replaced = false;
                        for (int i = 0; i < player.getInventory().size(); i++) {
                            if (player.getInventory().getStack(i) == stack) {
                                player.getInventory().setStack(i, newStack);
                                replaced = true;
                            }
                        }
                        
                        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
                            if (player.getEquippedStack(slot) == stack) {
                                player.equipStack(slot, newStack);
                                replaced = true;
                            }
                        }
                        
                        // Fallback: if not found by identity, check by equality (for safety)
                        if (!replaced) {
                            for (int i = 0; i < player.getInventory().size(); i++) {
                                if (ItemStack.areItemsAndComponentsEqual(player.getInventory().getStack(i), stack)) {
                                    player.getInventory().setStack(i, newStack);
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
