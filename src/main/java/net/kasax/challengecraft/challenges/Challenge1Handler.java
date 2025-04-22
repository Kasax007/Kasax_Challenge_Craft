package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.kasax.challengecraft.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.stream.Collectors;

public class Challenge1Handler {
    // your stick from your mod initializer

    // cache all items for random pick
    private static final List<Item> ALL_ITEMS = Registries.ITEM.stream().collect(Collectors.toList());

    public static void register() {
        // 1) Every server tick, give the stick if they don't already have it
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                boolean has = player.getInventory()
                        .getMainStacks()
                        .stream()
                        .anyMatch(s -> s.isOf(ModItems.CHALLENGE_STICK));
                if (!has) {
                    player.getInventory().insertStack(new ItemStack(ModItems.CHALLENGE_STICK));
                }
            }
        });

        // 2) On right‑click of the stick, consume integer levels and give random items
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient() && hand == Hand.MAIN_HAND) {
                ItemStack held = player.getMainHandStack();
                if (held.isOf(ModItems.CHALLENGE_STICK)) {
                    int lv = player.experienceLevel;
                    if (lv > 0) {
                        // pick random
                        Item pick = ALL_ITEMS.get(world.random.nextInt(ALL_ITEMS.size()));
                        ItemStack reward = new ItemStack(pick, lv);
                        // try insert, else drop
                        if (!player.getInventory().insertStack(reward)) {
                            player.dropItem(reward, false);
                        }
                        // subtract only the integer levels
                        player.addExperienceLevels(-lv);
                    }
                    // tell Fabric "I handled it on the server"
                    return ActionResult.SUCCESS_SERVER;
                }
            }
            return ActionResult.PASS;
        });
    }
}
