package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A night's hunt for several tiles at once, as a player goes out after dark: whichever of the
 * wanted monsters turns up first is fought, its tile ticked off, then on for the rest, walking on
 * (and seeing new country) in between. {@code wanted} says, each time, which kinds are still
 * open; the round ends when none are, or when the night (the cave, the time allowed) is over.
 */
public final class HuntRoundTask implements BotTask {
    private final Supplier<Set<EntityType<?>>> wanted;
    private final Supplier<Boolean> stillGood;
    private KillTask kill;
    private Set<EntityType<?>> hunting = Set.of();
    private int checks, kills;

    public HuntRoundTask(Supplier<Set<EntityType<?>>> wanted, Supplier<Boolean> stillGood) {
        this.wanted = wanted;
        this.stillGood = stillGood;
    }

    @Override
    public Result tick(Bot bot) {
        // Every second: what is still open (a tile claimed by a kill, or by the other team).
        if (kill == null || ++checks % 20 == 0) {
            Set<EntityType<?>> open = wanted.get();
            if (open.isEmpty()) return Result.DONE;
            if (!stillGood.get()) return kills > 0 ? Result.DONE : Result.FAILED;
            if (!open.equals(hunting)) {
                hunting = new HashSet<>(open);
                kill = new KillTask(hunting, Set.of(), 0, 1);
            }
        }
        Result r = kill.tick(bot);
        if (r == Result.DONE) {
            kills++;
            kill = null; // (the next one, after the board has been looked at again)
            hunting = Set.of();
            return Result.RUNNING;
        }
        return r;
    }

    @Override
    public String status() {
        return describe() + " [" + kills + " so far" + (kill == null ? "" : ", " + kill.status()) + "]";
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder("night hunt for");
        for (EntityType<?> t : hunting) sb.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath());
        return sb.toString();
    }
}
