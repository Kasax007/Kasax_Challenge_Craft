package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.challenges.Chal_51_VoidTrail;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.GROUND;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/**
 * Void Trail (challenge 51): every column a player leaves is gone, top to bottom, except End
 * portal frames and the End portal. One test in an environment of its own - the challenge acts on
 * every player on the server, and would cut holes into the arenas of tests running beside it.
 */
public class VoidTrailTests {
    private static final int Z = 20;

    @GameTest(environment = "challengecraft:void_trail", structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void voidTrail(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "void_trail");
        var level = h.getLevel();
        ServerPlayer body = a.spawn(5, FEET, Z).body();
        // Column 6 holds what must go (stone, bedrock, a chest) and what must stay (End portal frame and portal).
        a.fill(6, 17, Z, 6, 17, Z, Blocks.STONE);
        a.fill(6, 18, Z, 6, 18, Z, Blocks.END_PORTAL_FRAME);
        a.fill(6, 19, Z, 6, 19, Z, Blocks.END_PORTAL);
        a.fill(6, 20, Z, 6, 20, Z, Blocks.BEDROCK);
        a.fill(6, 21, Z, 6, 21, Z, Blocks.CHEST);
        boolean was = Chal_51_VoidTrail.isActive();
        Chal_51_VoidTrail.setActive(true);
        int[] t = {0};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(190), 200, () -> {
            t[0]++;
            // Every move in place of walking: a teleport by less than a block or a few, which the
            // challenge takes for a walk (a single tick's path).
            if (t[0] == 5) go(body, a, 6);       // leaves column 5
            if (t[0] == 10) go(body, a, 7);      // leaves column 6
            if (t[0] == 15) go(body, a, 10);     // three columns in one tick: 7, 8 and 9 are crossed
            if (t[0] == 20) go(body, a, 30);     // a 20-block teleport: only column 10, nothing in between
            if (t[0] == 25) body.setGameMode(GameType.CREATIVE);
            if (t[0] == 27) go(body, a, 31);     // creative: column 30 stays
            if (t[0] == 30) body.setGameMode(GameType.SURVIVAL);
            if (t[0] < 35) return false;
            Chal_51_VoidTrail.setActive(was);
            for (int x : new int[]{5, 7, 8, 9, 10}) {
                h.assertTrue(empty(a, x, Set.of()), "column " + x + " was left but is not empty: " + firstBlock(a, x, Set.of()));
            }
            Set<Integer> kept = Set.of(18, 19);
            h.assertTrue(empty(a, 6, kept), "column 6 still holds " + firstBlock(a, 6, kept));
            h.assertTrue(level.getBlockState(a.abs(6, 18, Z)).is(Blocks.END_PORTAL_FRAME), "the End portal frame is gone");
            h.assertTrue(level.getBlockState(a.abs(6, 19, Z)).is(Blocks.END_PORTAL), "the End portal is gone");
            for (int x : new int[]{11, 20, 29, 30, 31}) {
                h.assertTrue(level.getBlockState(a.abs(x, GROUND, Z)).is(Blocks.GRASS_BLOCK), "column " + x + " was taken but never left on foot");
            }
            return true;
        });
    }

    /** The player to the middle of column x (relative), on the arena's floor. */
    private static void go(ServerPlayer body, BotArena a, int x) {
        BlockPos p = a.abs(x, FEET, Z);
        body.teleportTo(a.helper().getLevel(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, Set.of(), body.getYRot(), 0f, true);
    }

    /** Whether column x (relative) is air from the top of the world to its bottom, but for these relative Ys. */
    private static boolean empty(BotArena a, int x, Set<Integer> except) {
        return firstBlock(a, x, except) == null;
    }

    private static String firstBlock(BotArena a, int x, Set<Integer> except) {
        var level = a.helper().getLevel();
        BlockPos base = a.abs(x, 0, Z);
        for (int y = level.getMinY(); y < level.getMinY() + level.getHeight(); y++) {
            if (except.contains(y - base.getY())) continue;
            var state = level.getBlockState(new BlockPos(base.getX(), y, base.getZ()));
            if (!state.isAir()) return state + " at y " + y;
        }
        return null;
    }
}
