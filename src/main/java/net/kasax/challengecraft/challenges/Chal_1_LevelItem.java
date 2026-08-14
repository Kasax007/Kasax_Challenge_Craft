package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.stream.Collectors;

/** Keeps the challenge stick available and rerolls it into random vanilla items on use. */
public class Chal_1_LevelItem {
    private static boolean active = false;
    private static final List<Item> ALL_ITEMS = BuiltInRegistries.ITEM.stream()
            .filter(item -> !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("challengecraft"))
            .collect(Collectors.toList());

    /** Event hooks are registered once at startup and remain dormant while the challenge is inactive. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            server.getPlayerList().getPlayers().forEach(player -> {
                boolean has = player.getInventory().getNonEquipmentItems().stream()
                        .anyMatch(s -> s.is(ModItems.CHALLENGE_STICK));
                if (!has) {
                    player.getInventory().add(new ItemStack(ModItems.CHALLENGE_STICK));
                }
            });
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!active || world.isClientSide() || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getMainHandItem();
            if (held.is(ModItems.CHALLENGE_STICK)) {
                int lv = player.experienceLevel;
                if (lv > 0) {
                    Item pick = ALL_ITEMS.get(world.getRandom().nextInt(ALL_ITEMS.size()));
                    ItemStack reward = new ItemStack(pick, lv);
                    if (!player.getInventory().add(reward)) {
                        player.drop(reward, false);
                    }
                    player.giveExperienceLevels(-lv);
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        });
    }

    public static void setActive(boolean flag) {
        active = flag;
    }
    public static boolean isActive() { return active; }
}
