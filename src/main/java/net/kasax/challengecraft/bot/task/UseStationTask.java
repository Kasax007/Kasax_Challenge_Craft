package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Opens a work block (stonecutter, loom, grindstone, ...): one nearby, or its own put down. The
 * game counts the use when the block's screen opens, as for a player; the bot closes it again.
 */
public final class UseStationTask extends StationTask {
    private final Block block;

    public UseStationTask(Block block, Item item) {
        super(block, item);
        this.block = block;
    }

    @Override
    protected Result work(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        Vec3 hit = Vec3.atCenterOf(station).add(0, 0.5, 0);
        body.lookAt(hit);
        var r = body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, station, false));
        body.closeContainer();
        return r.consumesAction() ? Result.DONE : Result.FAILED;
    }

    @Override
    public String describe() {
        return "use " + block.getName().getString();
    }
}
