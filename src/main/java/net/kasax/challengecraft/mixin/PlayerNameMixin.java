package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
/** Adds progression-based name colors on the client. */
public abstract class PlayerNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        try {
            Player player = (Player) (Object) this;
            if (player == null || player.getUUID() == null || player.level() == null) {
                return;
            }

            long xp = LevelManager.getPlayerXp(player);

            int stars = LevelManager.getStars(xp);
            int level = LevelManager.getLevelForXp(xp);

            Component original = cir.getReturnValue();
            if (original == null) {
                return;
            }

            if (original.getString().contains(Component.translatable("challengecraft.player_name.prefix_guard").getString())) {
                return;
            }

            Component colored = original;
            String colorName = LevelManager.getNameColor(stars);
            if (colorName != null) {
                colored = applyColor(original, colorName);
            }

            MutableComponent prefix = Component.translatable("challengecraft.player_name.prefix_level", level).withStyle(ChatFormatting.GRAY);
            if (stars > 0) {
                prefix.append(Component.translatable("challengecraft.player_name.prefix_stars", stars).withStyle(ChatFormatting.YELLOW));
            }
            prefix.append(Component.translatable("challengecraft.player_name.prefix_suffix").withStyle(ChatFormatting.GRAY));

            cir.setReturnValue(prefix.append(colored));
        } catch (Throwable t) {
            // Display decoration must never interfere with normal player-name resolution.
        }
    }

    @Unique
    private Component applyColor(Component text, String color) {
        if ("rainbow".equals(color)) {
            String name = text.getString();
            ChatFormatting[] rainbow = {
                    ChatFormatting.AQUA,
                    ChatFormatting.GREEN,
                    ChatFormatting.YELLOW,
                    ChatFormatting.RED,
                    ChatFormatting.LIGHT_PURPLE,
                    ChatFormatting.BLUE
            };
            MutableComponent result = Component.empty();
            for (int i = 0; i < name.length(); i++) {
                ChatFormatting f = rainbow[i % rainbow.length];
                result.append(Component.empty().append(String.valueOf(name.charAt(i))).withStyle(f, ChatFormatting.BOLD));
            }
            return result;
        }

        ChatFormatting formatting = switch (color) {
            case "green" -> ChatFormatting.GREEN;
            case "blue" -> ChatFormatting.BLUE;
            case "red" -> ChatFormatting.RED;
            case "purple" -> ChatFormatting.DARK_PURPLE;
            case "gold" -> ChatFormatting.GOLD;
            default -> null;
        };

        if (formatting != null) {
            return text.copy().withStyle(formatting);
        }
        return text;
    }
}
