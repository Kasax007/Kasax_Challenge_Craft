package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Stay near a player until told otherwise. */
public final class FollowTask implements BotTask {
    private final UUID player;
    private final String name;
    private int repath;

    public FollowTask(ServerPlayer player) {
        this.player = player.getUUID();
        this.name = player.getGameProfile().name();
    }

    @Override
    public Result tick(Bot bot) {
        ServerPlayer p = bot.server().getPlayerList().getPlayer(player);
        if (p == null || p.level() != bot.body().level()) return Result.FAILED;
        double d = p.distanceTo(bot.body());
        if (d < 3.0) {
            bot.navigator().stop();
            bot.body().lookAt(p.getEyePosition());
            return Result.RUNNING;
        }
        if (repath-- <= 0 || bot.navigator().status() != net.kasax.challengecraft.bot.BotNavigator.Status.MOVING) {
            repath = 20;
            bot.navigator().goNear(p.blockPosition(), 2.5);
        }
        bot.navigator().tick();
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "follow " + name;
    }
}
