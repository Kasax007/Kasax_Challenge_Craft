package net.kasax.challengecraft.bot;

import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoal;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalPool;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A field test: board goals one at a time, each from the same start with the same kit (in the
 * Nether for Nether goals), the real Lockout brain playing for that one tile alone. For each, how
 * long it took to claim, the health it got down to and whether it died ({@code [FIELDTEST]} in the
 * log), then a summary - which tiles Bob can do and which he still has to learn.
 */
public final class BotFieldTest {
    private static final List<BotFieldTest> RUNNING = new ArrayList<>();
    private final Bot bot;
    private final List<LockoutBingoGoal> goals;
    private final int ticksEach;
    private int index = -1, ticks, deaths, done;
    private float low;
    private boolean wasAlive = true;
    private String lastStatus = "";
    private BlockPos netherStart, overworldStart;
    private final List<String> results = new ArrayList<>();

    private BotFieldTest(Bot bot, List<LockoutBingoGoal> goals, int ticksEach) {
        this.bot = bot;
        this.goals = goals;
        this.ticksEach = ticksEach;
    }

    /** {@code which}: a goal id, several joined by commas, or a category (NETHER, ...). */
    public static int start(Bot bot, String which, int secondsEach) {
        List<LockoutBingoGoal> goals = new ArrayList<>();
        Set<String> ids = Set.of(which.split(","));
        for (LockoutBingoGoal g : LockoutBingoGoalPool.all()) {
            if (!g.isImplemented() || !g.isSelectableOnNormalBoard()) continue;
            if (ids.contains(g.id()) || g.category().name().equalsIgnoreCase(which)) goals.add(g);
        }
        RUNNING.removeIf(t -> t.bot == bot);
        if (goals.isEmpty()) return 0;
        RUNNING.add(new BotFieldTest(bot, goals, secondsEach * 20));
        BotManager.LOG.info("[FIELDTEST] {}: {} goals, {} s each: {}", bot.name, goals.size(), secondsEach,
                goals.stream().map(LockoutBingoGoal::id).toList());
        return goals.size();
    }

    static void tickAll() {
        RUNNING.removeIf(BotFieldTest::tick);
    }

    /** Returns true when done. */
    private boolean tick() {
        var body = bot.body();
        if (index < 0 || finished(body)) {
            if (index >= 0) report(body);
            if (++index >= goals.size()) {
                BotManager.LOG.info("[FIELDTEST] summary: {} of {} claimed | {}", done, goals.size(), String.join(" | ", results));
                bot.setBrain(null);
                return true;
            }
            begin();
            return false;
        }
        ticks++;
        if (!body.isAlive() && wasAlive) {
            deaths++;
            var src = body.getLastDamageSource();
            BotManager.LOG.info("[FIELDTEST]   {} died at {} ({}), last doing: {}", goal().id(), body.blockPosition().toShortString(),
                    src == null ? "?" : src.getLocalizedDeathMessage(body).getString(), lastStatus);
        }
        if (body.isAlive() && ticks % 20 == 0) lastStatus = bot.status() + " | nav " + bot.navigator().debug();
        if (ticks % 400 == 0) BotManager.LOG.info("[FIELDTEST]   {} {} s: at {}, hp {}, doing{}", goal().id(), ticks / 20,
                body.blockPosition().toShortString(), Math.round(body.getHealth()), bot.status());
        wasAlive = body.isAlive();
        if (body.isAlive()) low = Math.min(low, body.getHealth());
        return false;
    }

    private LockoutBingoGoal goal() {
        return goals.get(index);
    }

    private boolean claimed() {
        for (Chal_40_LockoutBingo.BoardTile t : Chal_40_LockoutBingo.board(bot.server())) {
            if (t.goal().id().equals(goal().id())) return t.claimedBy() != null;
        }
        return false;
    }

    private boolean finished(BotPlayer body) {
        return claimed() || ticks >= ticksEach || deaths > 0 && body.isAlive() && ticks > 40 || noWay();
    }

    /** The brain has known no way to the tile for half a minute. */
    private int noWayTicks;

    private boolean noWay() {
        if (bot.brain() instanceof LockoutBrain b && b.noWay && bot.current() == null) noWayTicks++;
        else noWayTicks = 0;
        return noWayTicks > 600;
    }

    private void report(BotPlayer body) {
        boolean ok = claimed();
        if (ok) done++;
        String what = ok ? "CLAIMED" : deaths > 0 ? "DIED" : noWayTicks > 600 ? "NO-WAY" : "TIMEOUT";
        String line = String.format("%s %s in %d s, lowest hp %.0f, deaths %d", goal().id(), what, ticks / 20, low, deaths);
        results.add(goal().id() + " " + what + " " + ticks / 20 + "s");
        BotManager.LOG.info("[FIELDTEST] {} (last: {})", line, bot.status());
    }

    /** The same start for every goal: the kit, full health and food, the start spot. */
    private void begin() {
        var body = bot.body();
        var server = bot.server();
        LockoutBingoGoal g = goal();
        boolean nether = g.category() == net.kasax.challengecraft.challenges.lockout.LockoutBingoGoalCategory.NETHER;
        if (overworldStart == null) overworldStart = server.overworld().getRespawnData().pos();
        ServerLevel level = nether ? server.getLevel(Level.NETHER) : server.overworld();
        BlockPos at;
        if (nether) {
            if (netherStart == null) netherStart = safeSpot(level, new BlockPos(0, 64, 0));
            at = netherStart;
        } else {
            at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, overworldStart);
        }
        bot.clearTasks();
        bot.navigator().stop();
        body.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), body.getYRot(), 0f, true);
        kit(body);
        body.setHealth(body.getMaxHealth());
        body.getFoodData().setFoodLevel(20);
        body.getFoodData().setSaturation(10);
        body.clearFire();
        Chal_40_LockoutBingo.startSoloDebugRun(body, List.of(g.id()));
        bot.setBrain(new LockoutBrain(LockoutBrain.Difficulty.HARD).only(Set.of(g.id())));
        ticks = 0;
        deaths = 0;
        noWayTicks = 0;
        wasAlive = true;
        low = body.getMaxHealth();
        BotManager.LOG.info("[FIELDTEST] {} start ({}) at {} in {}", g.id(), g.title().getString(), at.toShortString(), level.dimension().identifier().getPath());
    }

    /** What a player would bring along at this stage: iron, a gold piece, a shield, blocks, food. */
    private static void kit(BotPlayer body) {
        body.getInventory().clearContent();
        body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        body.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        body.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        body.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        for (ItemStack st : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE),
                new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COOKED_BEEF, 24),
                new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 32), new ItemStack(Items.BUCKET),
                new ItemStack(Items.GOLD_INGOT, 8), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.OAK_PLANKS, 16),
                new ItemStack(Items.TORCH, 0))) {
            if (!st.isEmpty()) body.getInventory().add(st);
        }
    }

    /** Feet room on solid ground, no lava beside, near {@code around} (the Nether's ground is rough). */
    private static BlockPos safeSpot(ServerLevel level, BlockPos around) {
        for (int r = 0; r <= 96; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    level.getChunk((around.getX() + dx) >> 4, (around.getZ() + dz) >> 4);
                    for (int y = 40; y < 100; y++) {
                        BlockPos p = new BlockPos(around.getX() + dx, y, around.getZ() + dz);
                        if (standable(level, p)) return p;
                    }
                }
            }
        }
        return around;
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        var below = level.getBlockState(p.below());
        if (below.getCollisionShape(level, p.below()).isEmpty() || !below.getFluidState().isEmpty() || below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) return false;
        for (int i = 0; i < 2; i++) {
            var s = level.getBlockState(p.above(i));
            if (!s.isAir()) return false;
        }
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 1, 2))) {
            if (level.getFluidState(q).is(net.minecraft.tags.FluidTags.LAVA)) return false;
        }
        return true;
    }
}
