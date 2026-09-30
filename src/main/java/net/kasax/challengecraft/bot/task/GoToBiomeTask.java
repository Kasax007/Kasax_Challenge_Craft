package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Walks into a biome: to the nearest bit of it in view, or out exploring until one comes in view. */
public final class GoToBiomeTask implements BotTask {
    private final Identifier biome;
    private final Explorer explorer = new Explorer(6000);
    private BlockPos target;
    private boolean walking;

    public GoToBiomeTask(Identifier biome) {
        this.biome = biome;
    }

    public static boolean inBiome(Bot bot, Identifier biome) {
        return bot.body().level().getBiome(bot.body().blockPosition()).unwrapKey()
                .map(k -> k.identifier().equals(biome)).orElse(false);
    }

    @Override
    public Result tick(Bot bot) {
        if (inBiome(bot, biome)) {
            bot.navigator().stop();
            return Result.DONE;
        }
        BlockPos seen = bot.senses().biome(biome);
        if (seen == null) {
            walking = false;
            return explorer.tick(bot);
        }
        explorer.pause(bot);
        if (!walking || !seen.equals(target)) {
            target = seen;
            bot.navigator().goNear(seen, 2);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) {
            walking = false;
            bot.senses().refresh();
            if (s == BotNavigator.Status.FAILED) return explorer.tick(bot);
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "go to a " + biome.getPath().replace('_', ' ');
    }
}
