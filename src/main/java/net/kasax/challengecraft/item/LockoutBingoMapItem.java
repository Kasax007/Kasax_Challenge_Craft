package net.kasax.challengecraft.item;

import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Opens the lockout board (or the team screen before the game starts) when used. The item does it
 * itself rather than through a global use-item hook: a client does not know whether the challenge
 * is on (that state lives on the server), so it always sends the use and the server decides.
 */
public class LockoutBingoMapItem extends Item {
    public LockoutBingoMapItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            if (Chal_40_LockoutBingo.isActive()) {
                Chal_40_LockoutBingo.openScreenFor(serverPlayer);
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("challengecraft.lockout.map_inactive"));
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
