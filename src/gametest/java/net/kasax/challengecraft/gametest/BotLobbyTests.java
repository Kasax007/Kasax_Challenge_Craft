package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.lockout.LockoutBrain;
import net.kasax.challengecraft.challenges.Chal_40_LockoutBingo;
import net.kasax.challengecraft.challenges.lockout.LockoutBingoTeam;
import net.kasax.challengecraft.network.LockoutBingoActionPacket;
import net.kasax.challengecraft.network.LockoutBingoActionPacket.Action;
import net.minecraft.gametest.framework.GameTestHelper;

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

    private static void fail(GameTestHelper h, String why) {
        Chal_40_LockoutBingo.setActive(false);
        var bob = BotManager.byName(Chal_40_LockoutBingo.BOT_NAME);
        if (bob != null) BotManager.remove(h.getLevel().getServer(), bob);
        h.fail(why);
    }
}
