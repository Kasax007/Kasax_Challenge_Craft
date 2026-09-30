package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotActions;
import net.kasax.challengecraft.bot.BotInventory;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Nether portal and goes through: a frame of ten obsidian (the corners from any block it
 * can spare, they only hold the rest up while building), lit with flint and steel, then it steps
 * in and waits for the other side. Works from the Nether back to the Overworld the same way.
 */
public final class PortalTask implements BotTask {
    private record Part(BlockPos pos, boolean obsidian) {
    }

    private final ResourceKey<Level> from;
    private List<Part> frame;
    private BlockPos inside;
    private int index, ticks, standTicks;
    private boolean walking, lit;

    public PortalTask(Level level) {
        this.from = level.dimension();
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        if (body.level().dimension() != from) return Result.DONE;
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 6000) return Result.FAILED;

        if (frame == null) {
            if (BotInventory.count(body, Items.OBSIDIAN) < 10 || BotInventory.slotOf(body, Items.FLINT_AND_STEEL) < 0) {
                bot.say("need 10 obsidian and flint and steel for a portal");
                return Result.FAILED;
            }
            if (!findSpot(bot, level)) {
                // Nowhere flat enough right here: walk a bit and look again.
                if (ticks > 3000) return Result.FAILED;
                BlockPos p = body.blockPosition().offset(body.getRandom().nextInt(17) - 8, 0, body.getRandom().nextInt(17) - 8);
                bot.interject(new GoToTask(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p), 2));
                return Result.RUNNING;
            }
        }

        // Build, part by part.
        while (index < frame.size() && !level.getBlockState(frame.get(index).pos()).isAir()) index++;
        if (index < frame.size()) {
            Part part = frame.get(index);
            if (!bot.actions().inReach(part.pos())) return walkTo(bot, part.pos());
            bot.navigator().stop();
            walking = false;
            int slot = part.obsidian() ? BotInventory.slotOf(body, Items.OBSIDIAN) : throwaway(body);
            if (slot < 0) return Result.FAILED;
            bot.tools().select(slot);
            if (!bot.actions().placeHeld(part.pos())) {
                // In the way (the bot itself, a mob): step back and retry.
                if (++standTicks > 100) return Result.FAILED;
            }
            return Result.RUNNING;
        }

        // Light it.
        if (!lit) {
            BlockPos base = inside.below();
            if (!bot.actions().inReach(base)) return walkTo(bot, base);
            bot.navigator().stop();
            walking = false;
            bot.tools().select(BotInventory.slotOf(body, Items.FLINT_AND_STEEL));
            Vec3 hit = Vec3.atCenterOf(base).add(0, 0.5, 0);
            body.lookAt(hit);
            body.gameMode.useItemOn(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, Direction.UP, base, false));
            lit = level.getBlockState(inside).is(Blocks.NETHER_PORTAL);
            if (!lit && ++standTicks > 60) return Result.FAILED;
            return Result.RUNNING;
        }

        // Step in and wait (a player needs four seconds in the portal).
        if (!body.blockPosition().equals(inside)) {
            if (!walking) {
                bot.navigator().goTo(inside);
                walking = true;
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED) walking = false;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.stopInputs();
        return Result.RUNNING;
    }

    private Result walkTo(Bot bot, BlockPos near) {
        if (!walking) {
            bot.navigator().goNear(near, 3.5);
            walking = true;
        }
        BotNavigator.Status s = bot.navigator().tick();
        if (s != BotNavigator.Status.MOVING) walking = false;
        return s == BotNavigator.Status.FAILED ? Result.FAILED : Result.RUNNING;
    }

    private static int throwaway(BotPlayer body) {
        var inv = body.getInventory().getNonEquipmentItems();
        for (int i = 0; i < inv.size(); i++) if (BotActions.THROWAWAY.contains(inv.get(i).getItem())) return i;
        for (Item i : List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS)) {
            int s = BotInventory.slotOf(body, i);
            if (s >= 0) return s;
        }
        return -1;
    }

    /**
     * A 4×5 spot next to the bot: solid ground under all four columns, empty above, and room to
     * stand in front. The frame runs along x or z, whichever fits.
     */
    private boolean findSpot(Bot bot, ServerLevel level) {
        BlockPos c = bot.body().blockPosition();
        for (int r = 2; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (Direction along : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos o = c.offset(dx, dy, dz);
                            if (fits(level, o, along)) {
                                build(o, along);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean fits(ServerLevel level, BlockPos o, Direction along) {
        for (int i = 0; i < 4; i++) {
            BlockPos col = o.relative(along, i);
            BlockPos under = col.below();
            if (level.getBlockState(under).getCollisionShape(level, under).isEmpty() || !level.getFluidState(under).isEmpty()) return false;
            for (int j = 0; j < 5; j++) {
                if (!level.getBlockState(col.above(j)).canBeReplaced() || !level.getFluidState(col.above(j)).isEmpty()) return false;
            }
        }
        return true;
    }

    private void build(BlockPos o, Direction along) {
        frame = new ArrayList<>();
        BlockPos a = o, b = o.relative(along, 3);
        frame.add(new Part(a, false));
        frame.add(new Part(b, false));
        frame.add(new Part(o.relative(along, 1), true));
        frame.add(new Part(o.relative(along, 2), true));
        for (int j = 1; j <= 3; j++) {
            frame.add(new Part(a.above(j), true));
            frame.add(new Part(b.above(j), true));
        }
        frame.add(new Part(a.above(4), false));
        frame.add(new Part(b.above(4), false));
        frame.add(new Part(o.relative(along, 1).above(4), true));
        frame.add(new Part(o.relative(along, 2).above(4), true));
        inside = o.relative(along, 1).above();
    }

    @Override
    public String describe() {
        return "build a portal and go through";
    }
}
