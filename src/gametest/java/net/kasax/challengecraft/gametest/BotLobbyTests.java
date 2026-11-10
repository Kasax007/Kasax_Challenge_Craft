package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.network.LockoutBingoActionPacket;
import net.kasax.challengecraft.network.LockoutBingoActionPacket.Action;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.kasax.challengecraft.bot.BotParams;

import java.util.List;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/** Bob as an opponent from the Lockout lobby. */
public class BotLobbyTests {
    /**
     * A player adds Bob: he takes a team and says ready. The player joins that same team: Bob moves
     * to another. Then the player removes him again (the player, a bot without a Lockout brain, stays).
     */
    // (A batch of its own: Lockout switched on hands every player on the server a board map.)
    @GameTest(environment = "challengecraft:lockout_lobby", structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void addMoveRemove(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "lobby");
        var host = a.spawn(20, FEET, 20).body();
        var server = h.getLevel().getServer();
        Chal_40_LockoutBingo.setActive(true);
        Chal_40_LockoutBingo.handleAction(host, new LockoutBingoActionPacket(Action.ADD_BOT, 2));
        var bob = BotManager.byName(Chal_40_LockoutBingo.BOT_NAME);
        if (bob == null || !(bob.brain() instanceof LockoutBrain)) {
            Chal_40_LockoutBingo.setActive(false);
            h.fail("Bob not spawned with a Lockout brain");
            return;
        }
        LockoutBingoTeam[] first = new LockoutBingoTeam[1];
        h.runAfterDelay(20, () -> {
            first[0] = Chal_40_LockoutBingo.teamOf(server, bob.id);
            if (first[0] == null) fail(h, "Bob did not join a team");
            else Chal_40_LockoutBingo.handleAction(host, new LockoutBingoActionPacket(Action.JOIN_TEAM, first[0].ordinal()));
        });
        h.runAfterDelay(60, () -> {
            LockoutBingoTeam now = Chal_40_LockoutBingo.teamOf(server, bob.id);
            if (now == null || now == first[0]) {
                fail(h, "Bob stayed in the player's team (" + now + ")");
                return;
            }
            if (Chal_40_LockoutBingo.isRunning(server)) {
                fail(h, "the game started without the player ready");
                return;
            }
            Chal_40_LockoutBingo.handleAction(host, new LockoutBingoActionPacket(Action.REMOVE_BOT, -1));
        });
        h.runAfterDelay(80, () -> {
            Chal_40_LockoutBingo.setActive(false);
            if (BotManager.byName(Chal_40_LockoutBingo.BOT_NAME) != null) h.fail("Bob still there");
            else if (!BotManager.all().contains(a.bot())) h.fail("the player was removed too");
            else {
                BotManager.remove(server, a.bot());
                h.succeed();
            }
        });
    }

    /**
     * Bob with the look-ahead ({@code search=1}, quick tiles off so that the look-ahead decides) on a
     * board whose first tiles he can do right here: his first tile within a minute and a half - the
     * whole path from the board through the simulator to a goal and its claim.
     */
    // (A batch of its own as well: a Lockout game of its own, not to be switched off by the lobby test.)
    @GameTest(environment = "challengecraft:lockout_search", structure = STRUCTURE, maxTicks = 1900, skyAccess = true, padding = 8)
    public void searchClaimsFirstTile(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "search_first_tile");
        BotArena.day(h);
        var server = h.getLevel().getServer();
        var bob = a.spawn(20, FEET, 20,
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SHOVEL),
                new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 16), new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.OAK_PLANKS, 32),
                new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.BUNDLE));
        bob.params = BotParams.parse("search=1,quick=0");
        Chal_40_LockoutBingo.startDebugRun(List.of((net.minecraft.server.level.ServerPlayer) bob.body()),
                List.of("craft_bucket", "craft_shears", "craft_furnace", "craft_cauldron"));
        LockoutBrain brain = new LockoutBrain(LockoutBrain.Difficulty.HARD);
        bob.setBrain(brain);
        LockoutBingoTeam team = Chal_40_LockoutBingo.teamOf(server, bob.id);
        boolean[] ended = {false};
        h.succeedWhen(() -> {
            int tiles = Chal_40_LockoutBingo.score(server, team);
            if (h.getTick() >= 1880 && tiles < 1 && !ended[0]) {
                ended[0] = true;
                BotArena.LOG.info("[BOTTEST] search_first_tile FAILED: no tile, look-ahead decisions {}", brain.lookAheadDecisions());
                end(server, bob);
            }
            h.assertTrue(tiles >= 1, "no tile yet");
            h.assertTrue(brain.lookAheadDecisions() >= 1, "a tile, but the look-ahead never decided");
            BotArena.LOG.info("[BOTTEST] search_first_tile passed in {} ticks ({} look-ahead decisions)", h.getTick(), brain.lookAheadDecisions());
            end(server, bob);
        });
    }

    /** The game ended and the lobby open again (the lobby test adds Bob only to a game not started). */
    private static void end(net.minecraft.server.MinecraftServer server, net.kasax.challengecraft.bot.Bot bob) {
        bob.setBrain(null);
        Chal_40_LockoutBingo.endDebugRun(server);
        Chal_40_LockoutBingo.setActive(false);
        BotManager.remove(server, bob);
    }

    private static void fail(GameTestHelper h, String why) {
        Chal_40_LockoutBingo.setActive(false);
        var bob = BotManager.byName(Chal_40_LockoutBingo.BOT_NAME);
        if (bob != null) BotManager.remove(h.getLevel().getServer(), bob);
        h.fail(why);
    }
}
