package net.kasax.challengecraft.gametest;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * A scenario for the bot: the empty 40 x 32 x 40 arena ({@code challengecraft:arena}) with a flat
 * floor (stone up to y 14, grass at y 15, so feet stand at y 16), a bot put into it with a task,
 * and a condition that makes the test pass. Every result is logged with the ticks it took
 * ({@code [BOTTEST]}), so runs can be compared.
 */
public final class BotArena {
    public static final String STRUCTURE = "challengecraft:arena";
    public static final int SIZE = 40, GROUND = 15, FEET = 16;
    static final Logger LOG = LoggerFactory.getLogger("ChallengeCraft-BotTest");
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final GameTestHelper helper;
    private final String name;
    private Bot bot;
    private boolean removed;

    private BotArena(GameTestHelper helper, String name) {
        this.helper = helper;
        this.name = name;
        LOG.info("[BOTTEST] {} origin {}", name, helper.absolutePos(BlockPos.ZERO).toShortString());
    }

    /** A new arena with the flat floor built. */
    public static BotArena flat(GameTestHelper helper, String name) {
        BotArena a = new BotArena(helper, name);
        // A normal world: blocks and mobs drop things (the test server turns that off).
        var rules = helper.getLevel().getGameRules();
        var server = helper.getLevel().getServer();
        rules.set(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS, true, server);
        rules.set(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS, true, server);
        rules.set(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS, true, server);
        // Only the monsters a scenario puts there (no zombie wandering in at night).
        rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS, false, server);
        a.fill(0, 0, 0, SIZE - 1, GROUND - 1, SIZE - 1, Blocks.STONE);
        a.fill(0, GROUND, 0, SIZE - 1, GROUND, SIZE - 1, Blocks.GRASS_BLOCK);
        return a;
    }

    public GameTestHelper helper() {
        return helper;
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, 2);
    }

    public BlockPos abs(int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    /** The bot, standing at this (relative) spot, carrying these items. */
    public Bot spawn(int x, int y, int z, ItemStack... items) {
        BlockPos p = abs(x, y, z);
        String botName = "T" + COUNTER.incrementAndGet();
        bot = BotManager.spawn(helper.getLevel().getServer(), botName, helper.getLevel(), Vec3.atBottomCenterOf(p));
        bot.body().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        bot.body().getInventory().clearContent();
        for (ItemStack s : items) bot.body().getInventory().add(s.copy());
        return bot;
    }

    public Bot bot() {
        return bot;
    }

    /** Gives the bot the task and passes when {@code done} holds (checked every tick). */
    public void run(BotTask task, int maxTicks, BooleanSupplier done) {
        bot.doNow(task);
        helper.succeedWhen(() -> {
            boolean ok = bot.body().isAlive() && done.getAsBoolean();
            if (helper.getTick() % 25 == 0 && !removed) LOG.info("[BOTTEST] {} t={} at {} y {} {}", name, helper.getTick(), feet().toShortString(),
                    String.format("%.2f hp %.0f dt %d", bot.body().getY() - abs(0, 0, 0).getY(), bot.body().getHealth(), bot.body().deathTime), bot.status() + " inv " + invSummary() + " nav " + bot.navigator().debug().replace(abs(0, 0, 0).toShortString(), "O"));
            if (!ok && helper.getTick() >= maxTicks - 2 && !removed) {
                LOG.info("[BOTTEST] {} FAILED after {} ticks: bot at {} ({})", name, helper.getTick(),
                        bot.body().blockPosition().subtract(abs(0, 0, 0)).toShortString(), bot.status());
                cleanup();
            }
            helper.assertTrue(ok, name + ": not yet (bot at " + bot.body().blockPosition().subtract(abs(0, 0, 0)).toShortString() + bot.status() + ")");
            LOG.info("[BOTTEST] {} passed in {} ticks", name, helper.getTick());
            cleanup();
        });
    }

    /** The bot's feet, relative to the arena. */
    public BlockPos feet() {
        return bot.body().blockPosition().subtract(abs(0, 0, 0));
    }

    public boolean near(int x, int y, int z, double range) {
        return Vec3.atBottomCenterOf(abs(x, y, z)).distanceTo(bot.body().position()) <= range && bot.body().onGround();
    }

    private String invSummary() {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : bot.body().getInventory().getNonEquipmentItems()) {
            if (!s.isEmpty()) sb.append(s.getCount()).append(' ').append(s.getItem().toString().replace("minecraft:", "")).append(',');
        }
        int drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bot.body().getBoundingBox().inflate(12)).size();
        return sb + " drops near " + drops;
    }

    private void cleanup() {
        if (removed) return;
        removed = true;
        BotManager.remove(helper.getLevel().getServer(), bot);
    }
}
