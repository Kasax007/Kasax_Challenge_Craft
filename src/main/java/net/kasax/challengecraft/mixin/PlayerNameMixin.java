package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
/** Adds progression-based name colors on the client. */
public abstract class PlayerNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        try {
            PlayerEntity player = (PlayerEntity) (Object) this;
            if (player == null || player.getUuid() == null || player.getWorld() == null) {
                return;
            }

            long xp = LevelManager.getPlayerXp(player);

            int stars = LevelManager.getStars(xp);
            int level = LevelManager.getLevelForXp(xp);

            Text original = cir.getReturnValue();
            if (original == null) {
                return;
            }

            if (original.getString().contains(Text.translatable("challengecraft.player_name.prefix_guard").getString())) {
                return;
            }

            Text colored = original;
            String colorName = LevelManager.getNameColor(stars);
            if (colorName != null) {
                colored = applyColor(original, colorName);
            }

            MutableText prefix = Text.translatable("challengecraft.player_name.prefix_level", level).formatted(Formatting.GRAY);
            if (stars > 0) {
                prefix.append(Text.translatable("challengecraft.player_name.prefix_stars", stars).formatted(Formatting.YELLOW));
            }
            prefix.append(Text.translatable("challengecraft.player_name.prefix_suffix").formatted(Formatting.GRAY));

            cir.setReturnValue(prefix.append(colored));
        } catch (Throwable t) {
            // Display decoration must never interfere with normal player-name resolution.
        }
    }

    @Unique
    private Text applyColor(Text text, String color) {
        if ("rainbow".equals(color)) {
            String name = text.getString();
            Formatting[] rainbow = {
                    Formatting.AQUA,
                    Formatting.GREEN,
                    Formatting.YELLOW,
                    Formatting.RED,
                    Formatting.LIGHT_PURPLE,
                    Formatting.BLUE
            };
            MutableText result = Text.empty();
            for (int i = 0; i < name.length(); i++) {
                Formatting f = rainbow[i % rainbow.length];
                result.append(Text.empty().append(String.valueOf(name.charAt(i))).formatted(f, Formatting.BOLD));
            }
            return result;
        }

        Formatting formatting = switch (color) {
            case "green" -> Formatting.GREEN;
            case "blue" -> Formatting.BLUE;
            case "red" -> Formatting.RED;
            case "purple" -> Formatting.DARK_PURPLE;
            case "gold" -> Formatting.GOLD;
            default -> null;
        };

        if (formatting != null) {
            return text.copy().formatted(formatting);
        }
        return text;
    }
}
