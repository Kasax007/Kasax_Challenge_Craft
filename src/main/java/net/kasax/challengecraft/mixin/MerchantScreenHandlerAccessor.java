package net.kasax.challengecraft.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantMenu.class)
/** Accessor used to inspect active villager trades for lockout goals. */
public interface MerchantScreenHandlerAccessor {
    @Accessor("trader")
    Merchant challengecraft$getMerchant();
}
