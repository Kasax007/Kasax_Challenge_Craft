package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.registry.Registries;
import net.minecraft.item.Item;

import java.util.List;
import java.util.stream.Collectors;

public class Challenge1Handler {
    private static boolean active = false;
    private static final List<Item> ALL_ITEMS = Registries.ITEM.stream().collect(Collectors.toList());

    /** Called once on mod init to hook the events (they’ll no‑op until active==true). */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            server.getPlayerManager().getPlayerList().forEach(player -> {
                boolean has = player.getInventory().getMainStacks().stream()
                        .anyMatch(s -> s.isOf(ModItems.CHALLENGE_STICK));
                if (!has) {
                    player.getInventory().insertStack(new ItemStack(ModItems.CHALLENGE_STICK));
                }
            });
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!active || world.isClient() || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            ItemStack held = player.getMainHandStack();
            if (held.isOf(ModItems.CHALLENGE_STICK)) {
                int lv = player.experienceLevel;
                if (lv > 0) {
                    Item pick = ALL_ITEMS.get(world.random.nextInt(ALL_ITEMS.size()));
                    ItemStack reward = new ItemStack(pick, lv);
                    if (!player.getInventory().insertStack(reward)) {
                        player.dropItem(reward, false);
                    }
                    player.addExperienceLevels(-lv);
                }
                return ActionResult.SUCCESS_SERVER;
            }
            return ActionResult.PASS;
        });
    }

    public static void setActive(boolean flag) {
        active = flag;
    }
}
