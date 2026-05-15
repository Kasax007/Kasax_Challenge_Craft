package net.kasax.challengecraft.mixin;

import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreenHandler.class)
/** Accessor used to inspect active villager trades for lockout goals. */
public interface MerchantScreenHandlerAccessor {
    @Accessor("merchant")
    Merchant challengecraft$getMerchant();
}
