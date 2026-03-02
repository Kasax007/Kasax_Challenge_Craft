package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.LevelManager;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerNameMixin {
    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void onInitDataTracker(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(LevelManager.INFINITY_STARS, 0);
        builder.add(LevelManager.LEVEL, 1);
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        int stars = player.getDataTracker().get(LevelManager.INFINITY_STARS);
        int level = player.getDataTracker().get(LevelManager.LEVEL);
        
        Text original = cir.getReturnValue();
        // Avoid double adding if somehow called recursively or by other mods
        if (original.getString().contains("[Lvl ")) return;

        Text colored = original;
        String color = LevelManager.getNameColor(stars);
        if (color != null) {
            colored = applyColor(original, color);
        }

        MutableText prefix = Text.literal("§7[Lvl " + level + (stars > 0 ? " §e★" + stars : "") + "§7] §r");
        cir.setReturnValue(prefix.append(colored));
    }

    @Unique
    private Text applyColor(Text text, String color) {
        String code = switch (color) {
            case "green" -> "§a";
            case "blue" -> "§9";
            case "red" -> "§c";
            case "purple" -> "§5";
            case "gold" -> "§6";
            case "rainbow" -> null; // Special handling
            default -> "";
        };

        if (code != null) {
            return Text.literal(code).append(text);
        } else {
            // Rainbow
            String name = text.getString();
            String[] colors = {"§b", "§a", "§e", "§c", "§d", "§9"};
            MutableText result = Text.empty();
            for (int i = 0; i < name.length(); i++) {
                result.append(Text.literal(colors[i % colors.length] + "§l" + name.charAt(i)));
            }
            return result;
        }
    }
}
