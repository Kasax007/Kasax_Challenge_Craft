package net.kasax.challengecraft.bot.task;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotNavigator;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Digs up buried treasure from a treasure map: walks to the red X and digs at the middle of that
 * chunk (block 9, 9 of it), where the chest always is, straight down until it finds the chest.
 */
public final class TreasureTask implements BotTask {
    private final BlockPos column;
    private boolean walking;
    private int ticks;
    private BotTask loot;

    private TreasureTask(BlockPos column) {
        this.column = column;
    }

    /** The treasure spot of a treasure map the bot carries, or null. */
    public static BlockPos fromMap(BotPlayer body) {
        for (ItemStack s : body.getInventory().getNonEquipmentItems()) {
            if (!s.is(Items.FILLED_MAP)) continue;
            MapDecorations decorations = s.get(DataComponents.MAP_DECORATIONS);
            if (decorations == null) continue;
            for (MapDecorations.Entry e : decorations.decorations().values()) {
                if (e.type().value() == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X.value()) {
                    int x = (int) Math.floor(e.x()), z = (int) Math.floor(e.z());
                    // The chest sits at block 9, 9 of the chunk the X is in.
                    return new BlockPos((x & ~15) + 9, 0, (z & ~15) + 9);
                }
            }
        }
        return null;
    }

    public static TreasureTask of(BotPlayer body) {
        BlockPos p = fromMap(body);
        return p == null ? null : new TreasureTask(p);
    }

    public BlockPos column() {
        return column;
    }

    @Override
    public Result tick(Bot bot) {
        BotPlayer body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 6000) return Result.FAILED;
        if (loot != null) return loot.tick(bot);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        // Found it? A loot chest in the column, a few blocks down.
        for (int y = surface; y > surface - 14; y--) {
            BlockPos p = new BlockPos(column.getX(), y, column.getZ());
            if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity) {
                if (bot.actions().inReach(p)) {
                    loot = new LootTask(p);
                    return Result.RUNNING;
                }
                break;
            }
        }
        BlockPos top = new BlockPos(column.getX(), surface, column.getZ());
        double flat = Math.hypot(body.getX() - (column.getX() + 0.5), body.getZ() - (column.getZ() + 0.5));
        if (flat > 2.5) {
            if (!walking) {
                bot.navigator().goNear(top, 2);
                walking = true;
            }
            BotNavigator.Status s = bot.navigator().tick();
            if (s != BotNavigator.Status.MOVING) walking = false;
            return s == BotNavigator.Status.FAILED ? Result.FAILED : Result.RUNNING;
        }
        bot.navigator().stop();
        walking = false;
        // Dig the column down, from beside it (never stand in the hole: sand falls in).
        for (int y = surface - 1; y > surface - 14; y--) {
            BlockPos p = new BlockPos(column.getX(), y, column.getZ());
            if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity) {
                // In the column but out of reach: step into the hole towards it.
                bot.interject(new GoToTask(p.above(), 1));
                return Result.RUNNING;
            }
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                if (!bot.actions().inReach(p)) {
                    bot.interject(new GoToTask(p.above(), 1));
                    return Result.RUNNING;
                }
                bot.actions().breakTick(p);
                return Result.RUNNING;
            }
        }
        return Result.FAILED; // dug fourteen down and nothing: not here
    }

    @Override
    public String describe() {
        return "dig up the treasure at " + column.getX() + ", " + column.getZ();
    }
}
