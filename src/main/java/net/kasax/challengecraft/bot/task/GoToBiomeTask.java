package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Walks into a biome: to the nearest bit of it in view, or out exploring until one comes in view. */
public final class GoToBiomeTask implements BotTask {
    private final FarWalk far = new FarWalk();
    private int arrivals;
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
        // To the nearest spot it knows, however far (in legs).
        FarWalk.Status s = far.tick(bot, seen, 2);
        if (s != FarWalk.Status.MOVING) {
            bot.senses().refresh();
            // There and not in it (the edge moved), or no way: looking about from here.
            if (s == FarWalk.Status.FAILED || ++arrivals > 3) return explorer.tick(bot);
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return "go to a " + biome.getPath().replace('_', ' ');
    }
}
