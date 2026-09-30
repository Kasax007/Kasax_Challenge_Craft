package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Puts a block down next to itself, then (optionally) uses an item on it or clicks it with an
 * empty hand, and (optionally) steps away: TNT lit with flint and steel (and run from), a bed
 * clicked to sleep or set the spawn, a pressure plate stepped on.
 */
public final class PlaceAndUseTask implements BotTask {
    public enum Then { NOTHING, USE_ITEM, CLICK, STEP_ON }

    private final Item block, tool;
    private final Then then;
    private final int runAway;
    private BlockPos placed;
    private int ticks, tries;
    private boolean used;

    public PlaceAndUseTask(Item block, Then then, Item tool, int runAway) {
        this.block = block;
        this.then = then;
        this.tool = tool;
        this.runAway = runAway;
    }

    @Override
    public Result tick(Bot bot) {
        if (++ticks > 600) return Result.FAILED;
        var body = bot.body();
        if (placed == null) {
            placed = bot.actions().placeNearby(block);
            if (placed == null && ++tries > 40) return Result.FAILED;
            if (placed == null) {
                bot.interject(new GoToTask(body.blockPosition().offset(body.getRandom().nextInt(5) - 2, 0, body.getRandom().nextInt(5) - 2), 1));
            }
            return then == Then.NOTHING && placed != null ? Result.DONE : Result.RUNNING;
        }
        if (!used) {
            switch (then) {
                case NOTHING -> {
                    return Result.DONE;
                }
                case USE_ITEM, CLICK -> {
                    if (then == Then.USE_ITEM) {
                        int slot = BotInventory.slotOf(body, tool);
                        if (slot < 0) return Result.FAILED;
                        bot.tools().select(slot);
                    } else {
                        bot.tools().selectEmptyHandPublic();
                    }
                    Vec3 hit = Vec3.atCenterOf(placed).add(0, 0.4, 0);
                    body.lookAt(hit);
                    body.gameMode.useItemOn(body, body.level(), body.getMainHandItem(), InteractionHand.MAIN_HAND,
                            new BlockHitResult(hit, Direction.UP, placed, false));
                    used = true;
                    if (runAway <= 0) return Result.DONE;
                    Vec3 away = body.position().subtract(Vec3.atCenterOf(placed)).normalize().scale(runAway);
                    bot.interject(new GoToTask(BlockPos.containing(body.position().add(away)), 2));
                    return Result.RUNNING;
                }
                case STEP_ON -> {
                    // The one walk the path search refuses: onto the plate.
                    double dx = placed.getX() + 0.5 - body.getX(), dz = placed.getZ() + 0.5 - body.getZ();
                    if (dx * dx + dz * dz < 0.09) {
                        body.stopInputs();
                        used = true;
                        return Result.DONE;
                    }
                    body.setYRot((float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f);
                    body.forward = 0.5f;
                    return Result.RUNNING;
                }
            }
        }
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "put down " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(block).getPath()
                + (then == Then.NOTHING ? "" : " and " + then.name().toLowerCase().replace('_', ' '));
    }
}
