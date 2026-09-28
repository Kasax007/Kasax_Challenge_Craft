package net.kasax.challengecraft.casino;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's standing with the House. Money is kept in <b>centi-chips</b> (1/100 of a chip) so
 * that line bets, item bets and proportional fee shares never lose anything to rounding — the
 * 100% RTP promise holds to the last chip. The UI only ever shows whole chips.
 */
public final class CasinoAccount {
    public static final long CENTI = 100L;

    public String name = "";
    /** Balance in centi-chips. */
    public long balance;
    /** Item ids this player has deposited at least once — only these can be bought back. */
    public final Set<String> known = new LinkedHashSet<>();
    /** Device ids bought from the croupier; unlocks their crafting recipe for this player. */
    public final Set<String> unlocked = new LinkedHashSet<>();
    public long wagered;
    public long won;
    public long biggestWin;
    public long feesPaid;
    public int deaths;
    public int revivalsWon;
    public int revivalsLost;
    /** Index into {@link CasinoGames#BET_LEVELS}; -1 = all-in. */
    public int betLevel = 0;

    // Runtime only, never saved: the "Pechkonto" of unanswered losses that turns into a mob wave.
    public transient long lossMeter;
    public transient long lossStartTick;
    public transient long lastBetTick;

    private record Stored(String name, long balance, List<String> known, List<String> unlocked, long wagered,
                          long won, long biggestWin, long feesPaid, int deaths, int revivalsWon,
                          int revivalsLost, int betLevel) {
    }

    private static final Codec<Stored> STORED_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(Stored::name),
            Codec.LONG.optionalFieldOf("balance", 0L).forGetter(Stored::balance),
            Codec.STRING.listOf().optionalFieldOf("known", List.of()).forGetter(Stored::known),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(Stored::unlocked),
            Codec.LONG.optionalFieldOf("wagered", 0L).forGetter(Stored::wagered),
            Codec.LONG.optionalFieldOf("won", 0L).forGetter(Stored::won),
            Codec.LONG.optionalFieldOf("biggestWin", 0L).forGetter(Stored::biggestWin),
            Codec.LONG.optionalFieldOf("feesPaid", 0L).forGetter(Stored::feesPaid),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(Stored::deaths),
            Codec.INT.optionalFieldOf("revivalsWon", 0).forGetter(Stored::revivalsWon),
            Codec.INT.optionalFieldOf("revivalsLost", 0).forGetter(Stored::revivalsLost),
            Codec.INT.optionalFieldOf("betLevel", 0).forGetter(Stored::betLevel)
    ).apply(i, Stored::new));

    public static final Codec<CasinoAccount> CODEC = STORED_CODEC.xmap(CasinoAccount::fromStored, CasinoAccount::toStored);

    private static CasinoAccount fromStored(Stored s) {
        CasinoAccount a = new CasinoAccount();
        a.name = s.name();
        a.balance = s.balance();
        a.known.addAll(s.known());
        a.unlocked.addAll(s.unlocked());
        a.wagered = s.wagered();
        a.won = s.won();
        a.biggestWin = s.biggestWin();
        a.feesPaid = s.feesPaid();
        a.deaths = s.deaths();
        a.revivalsWon = s.revivalsWon();
        a.revivalsLost = s.revivalsLost();
        a.betLevel = s.betLevel();
        return a;
    }

    private Stored toStored() {
        return new Stored(name, balance, new ArrayList<>(known), new ArrayList<>(unlocked), wagered, won,
                biggestWin, feesPaid, deaths, revivalsWon, revivalsLost, betLevel);
    }

    public long chips() {
        return balance / CENTI;
    }
}
