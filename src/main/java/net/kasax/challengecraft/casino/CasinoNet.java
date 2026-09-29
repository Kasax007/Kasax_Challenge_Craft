package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Every packet of the casino. Grouped in one file because they are small, only make sense together
 * and are all registered at once by {@link #register()}.
 *
 * <p>Server → client packets carry <b>results</b>, never secrets: the slot result is sent the moment
 * the spin starts (the client only animates towards it, the payout is credited when the animation
 * ends), but a crash point is sent only once the rocket has actually exploded, and the dealer's hole
 * card only once it is turned over.
 */
public final class CasinoNet {
    private CasinoNet() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, name));
    }

    private static void writeInts(RegistryFriendlyByteBuf buf, int[] values) {
        buf.writeVarInt(values.length);
        for (int v : values) buf.writeVarInt(v);
    }

    private static int[] readInts(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = buf.readVarInt();
        return out;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String s : values) buf.writeUtf(s);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }

    // ---- state -------------------------------------------------------------------------------

    /** One line of the team ledger shown on the HUD: who holds what and pays which share. */
    public record Member(String name, long balance, long share) {
    }

    /**
     * The player's own account plus the team picture, sent once a second while the challenge runs.
     * Amounts are centi-chips.
     */
    public record State(long balance, long total, long nextFee, int ticksToFee, int feeIndex, boolean bankrupt,
                        int betLevel, long betAmount, List<Member> members, List<String> unlocked,
                        int knownCount, boolean inLimbo) implements CustomPacketPayload {
        public static final Type<State> ID = payloadType("casino_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarLong(p.balance);
                    buf.writeVarLong(p.total);
                    buf.writeVarLong(p.nextFee);
                    buf.writeVarInt(p.ticksToFee);
                    buf.writeVarInt(p.feeIndex);
                    buf.writeBoolean(p.bankrupt);
                    buf.writeVarInt(p.betLevel + 1);
                    buf.writeVarLong(p.betAmount);
                    buf.writeVarInt(p.members.size());
                    for (Member m : p.members) {
                        buf.writeUtf(m.name());
                        buf.writeVarLong(m.balance());
                        buf.writeVarLong(m.share());
                    }
                    writeStrings(buf, p.unlocked);
                    buf.writeVarInt(p.knownCount);
                    buf.writeBoolean(p.inLimbo);
                },
                buf -> {
                    long balance = buf.readVarLong();
                    long total = buf.readVarLong();
                    long nextFee = buf.readVarLong();
                    int ticks = buf.readVarInt();
                    int index = buf.readVarInt();
                    boolean bankrupt = buf.readBoolean();
                    int betLevel = buf.readVarInt() - 1;
                    long betAmount = buf.readVarLong();
                    int n = buf.readVarInt();
                    List<Member> members = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        members.add(new Member(buf.readUtf(), buf.readVarLong(), buf.readVarLong()));
                    }
                    List<String> unlocked = readStrings(buf);
                    int known = buf.readVarInt();
                    boolean limbo = buf.readBoolean();
                    return new State(balance, total, nextFee, ticks, index, bankrupt, betLevel, betAmount,
                            members, unlocked, known, limbo);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Every device in the receiving player's dimension: {x, y, z, typeOrdinal} per entry. */
    public record Devices(int[] data) implements CustomPacketPayload {
        public static final Type<Devices> ID = payloadType("casino_devices");
        public static final StreamCodec<RegistryFriendlyByteBuf, Devices> CODEC = StreamCodec.ofMember(
                (p, buf) -> writeInts(buf, p.data), buf -> new Devices(readInts(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Opens the cashier: the catalogue of items this player may buy, with prices in chips. */
    public record Cashier(List<String> items, long[] prices, int tab) implements CustomPacketPayload {
        public static final Type<Cashier> ID = payloadType("casino_cashier");
        public static final StreamCodec<RegistryFriendlyByteBuf, Cashier> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    writeStrings(buf, p.items);
                    buf.writeVarInt(p.prices.length);
                    for (long v : p.prices) buf.writeVarLong(v);
                    buf.writeVarInt(p.tab);
                },
                buf -> {
                    List<String> items = readStrings(buf);
                    int n = buf.readVarInt();
                    long[] prices = new long[n];
                    for (int i = 0; i < n; i++) prices[i] = buf.readVarLong();
                    return new Cashier(items, prices, buf.readVarInt());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- slots -------------------------------------------------------------------------------

    /**
     * A finished spin, sent to everyone near the machine so they all watch the same reels.
     * {@code spins[0]} is the paid spin; the rest are free spins. Per spin: 5 stops, the win in
     * centi-chips and a bitmask of reels the lucky item expanded on.
     */
    public record SlotResult(long pos, String player, long bet, int[] stops, long[] spinWins, int[] expandMasks,
                             int luckySymbol, long totalWin, String payItem, int payCount, int serial)
            implements CustomPacketPayload {
        public static final Type<SlotResult> ID = payloadType("casino_slot_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotResult> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeLong(p.pos);
                    buf.writeUtf(p.player);
                    buf.writeVarLong(p.bet);
                    writeInts(buf, p.stops);
                    buf.writeVarInt(p.spinWins.length);
                    for (long v : p.spinWins) buf.writeVarLong(v);
                    writeInts(buf, p.expandMasks);
                    buf.writeVarInt(p.luckySymbol + 1);
                    buf.writeVarLong(p.totalWin);
                    buf.writeUtf(p.payItem);
                    buf.writeVarInt(p.payCount);
                    buf.writeVarInt(p.serial);
                },
                buf -> {
                    long pos = buf.readLong();
                    String player = buf.readUtf();
                    long bet = buf.readVarLong();
                    int[] stops = readInts(buf);
                    int n = buf.readVarInt();
                    long[] wins = new long[n];
                    for (int i = 0; i < n; i++) wins[i] = buf.readVarLong();
                    int[] masks = readInts(buf);
                    int lucky = buf.readVarInt() - 1;
                    long total = buf.readVarLong();
                    String payItem = buf.readUtf();
                    int payCount = buf.readVarInt();
                    int serial = buf.readVarInt();
                    return new SlotResult(pos, player, bet, stops, wins, masks, lucky, total, payItem, payCount, serial);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- plinko ------------------------------------------------------------------------------

    /**
     * A ball dropped on a plinko board, sent to everyone near it. The path is decided when the ball
     * is dropped (bit r = right at row r); the payout is credited when it lands.
     */
    public record PlinkoBall(long pos, String player, long bet, int path, long payout, int serial)
            implements CustomPacketPayload {
        public static final Type<PlinkoBall> ID = payloadType("casino_plinko");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlinkoBall> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeLong(p.pos);
                    buf.writeUtf(p.player);
                    buf.writeVarLong(p.bet);
                    buf.writeVarInt(p.path);
                    buf.writeVarLong(p.payout);
                    buf.writeVarInt(p.serial);
                },
                buf -> new PlinkoBall(buf.readLong(), buf.readUtf(), buf.readVarLong(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- roulette ----------------------------------------------------------------------------

    /** One chip pile on the felt. */
    public record RouletteBet(String player, int kind, int target, long amount) {
    }

    public record RouletteState(long pos, int phase, int ticksLeft, int result, List<RouletteBet> bets,
                                int[] history, int serial) implements CustomPacketPayload {
        public static final Type<RouletteState> ID = payloadType("casino_roulette");
        public static final StreamCodec<RegistryFriendlyByteBuf, RouletteState> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeLong(p.pos);
                    buf.writeVarInt(p.phase);
                    buf.writeVarInt(p.ticksLeft);
                    buf.writeVarInt(p.result + 1);
                    buf.writeVarInt(p.bets.size());
                    for (RouletteBet b : p.bets) {
                        buf.writeUtf(b.player());
                        buf.writeVarInt(b.kind());
                        buf.writeVarInt(b.target());
                        buf.writeVarLong(b.amount());
                    }
                    writeInts(buf, p.history);
                    buf.writeVarInt(p.serial);
                },
                buf -> {
                    long pos = buf.readLong();
                    int phase = buf.readVarInt();
                    int ticks = buf.readVarInt();
                    int result = buf.readVarInt() - 1;
                    int n = buf.readVarInt();
                    List<RouletteBet> bets = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        bets.add(new RouletteBet(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong()));
                    }
                    int[] history = readInts(buf);
                    int serial = buf.readVarInt();
                    return new RouletteState(pos, phase, ticks, result, bets, history, serial);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- crash -------------------------------------------------------------------------------

    /** A participant of the current crash round; {@code cashedAt} is the multiplier ×100, 0 = still flying. */
    public record CrashSeat(String player, long bet, int cashedAt) {
    }

    /**
     * {@code flightTicks} counts from lift-off; the client derives the multiplier from it with the
     * same curve as the server. {@code crashedAt} (×100) is 0 until the rocket has exploded.
     */
    public record CrashState(long pos, int phase, int ticksLeft, int flightTicks, int crashedAt,
                             List<CrashSeat> seats, int[] history, int serial) implements CustomPacketPayload {
        public static final Type<CrashState> ID = payloadType("casino_crash");
        public static final StreamCodec<RegistryFriendlyByteBuf, CrashState> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeLong(p.pos);
                    buf.writeVarInt(p.phase);
                    buf.writeVarInt(p.ticksLeft);
                    buf.writeVarInt(p.flightTicks);
                    buf.writeVarInt(p.crashedAt);
                    buf.writeVarInt(p.seats.size());
                    for (CrashSeat s : p.seats) {
                        buf.writeUtf(s.player());
                        buf.writeVarLong(s.bet());
                        buf.writeVarInt(s.cashedAt());
                    }
                    writeInts(buf, p.history);
                    buf.writeVarInt(p.serial);
                },
                buf -> {
                    long pos = buf.readLong();
                    int phase = buf.readVarInt();
                    int ticks = buf.readVarInt();
                    int flight = buf.readVarInt();
                    int crashed = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<CrashSeat> seats = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        seats.add(new CrashSeat(buf.readUtf(), buf.readVarLong(), buf.readVarInt()));
                    }
                    int[] history = readInts(buf);
                    int serial = buf.readVarInt();
                    return new CrashState(pos, phase, ticks, flight, crashed, seats, history, serial);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- blackjack ---------------------------------------------------------------------------

    /**
     * The revival table. Cards are 0..51 (rank = c % 13 with 0 = ace, suit = c / 13); -1 is a card
     * lying face down. {@code hands} is flattened: per hand {count, stake, finished, cards...}.
     */
    public record Blackjack(String player, int seat, int phase, int[] dealer, int[] hands, int active, int allowed,
                            int outcome, int ticksLeft, long lostChips, int serial) implements CustomPacketPayload {
        public static final Type<Blackjack> ID = payloadType("casino_blackjack");
        public static final StreamCodec<RegistryFriendlyByteBuf, Blackjack> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeUtf(p.player);
                    buf.writeVarInt(p.seat + 1);
                    buf.writeVarInt(p.phase);
                    buf.writeVarInt(p.dealer.length);
                    for (int c : p.dealer) buf.writeVarInt(c + 1);
                    writeInts(buf, p.hands);
                    buf.writeVarInt(p.active);
                    buf.writeVarInt(p.allowed);
                    buf.writeVarInt(p.outcome + 1);
                    buf.writeVarInt(p.ticksLeft);
                    buf.writeVarLong(p.lostChips);
                    buf.writeVarInt(p.serial);
                },
                buf -> {
                    String player = buf.readUtf();
                    int seat = buf.readVarInt() - 1;
                    int phase = buf.readVarInt();
                    int n = buf.readVarInt();
                    int[] dealer = new int[n];
                    for (int i = 0; i < n; i++) dealer[i] = buf.readVarInt() - 1;
                    int[] hands = readInts(buf);
                    int active = buf.readVarInt();
                    int allowed = buf.readVarInt();
                    int outcome = buf.readVarInt() - 1;
                    int ticks = buf.readVarInt();
                    long lost = buf.readVarLong();
                    int serial = buf.readVarInt();
                    return new Blackjack(player, seat, phase, dealer, hands, active, allowed, outcome, ticks, lost, serial);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- the cashier counter -----------------------------------------------------------------

    /**
     * The items the receiving player has laid on the counter and what each stack would fetch
     * (chips; 0 = the House does not buy it). {@code counter} is the packed position of the middle
     * counter they lie on.
     */
    public record Pending(long counter, List<net.minecraft.world.item.ItemStack> items, long[] values)
            implements CustomPacketPayload {
        public static final Type<Pending> ID = payloadType("casino_pending");
        public static final StreamCodec<RegistryFriendlyByteBuf, Pending> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeLong(p.counter);
                    net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, p.items);
                    buf.writeVarInt(p.values.length);
                    for (long v : p.values) buf.writeVarLong(v);
                },
                buf -> {
                    long counter = buf.readLong();
                    List<net.minecraft.world.item.ItemStack> items =
                            net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
                    int n = buf.readVarInt();
                    long[] values = new long[n];
                    for (int i = 0; i < n; i++) values[i] = buf.readVarLong();
                    return new Pending(counter, items, values);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- effects -----------------------------------------------------------------------------

    /** A one-off presentation event: win banner, fee collected, wave incoming, device unlocked. */
    public record Fx(int kind, long amount, String text, long pos) implements CustomPacketPayload {
        public static final int WIN = 0;
        public static final int FEE_PAID = 1;
        public static final int WAVE = 2;
        public static final int UNLOCK = 3;
        public static final int FEE_WARNING = 4;
        public static final int DEATH_TAX = 5;
        public static final int DEPOSIT = 6;
        public static final int BANKRUPT = 7;
        public static final int PURCHASE = 8;
        public static final int OPEN_ROULETTE = 9;
        public static final int REVIVED = 10;

        public static final Type<Fx> ID = payloadType("casino_fx");
        public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeVarLong(p.amount);
                    buf.writeUtf(p.text);
                    buf.writeLong(p.pos);
                },
                buf -> new Fx(buf.readVarInt(), buf.readVarLong(), buf.readUtf(), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // ---- client → server ---------------------------------------------------------------------

    /** Everything the player can ask of the House. {@code pos} is a packed BlockPos where relevant. */
    public record Action(int action, long pos, int a, int b, long amount, String text) implements CustomPacketPayload {
        public static final int DEPOSIT_SLOTS = 0;
        public static final int DEPOSIT_ALL = 1;
        public static final int BUY_ITEM = 2;
        public static final int BUY_DEVICE = 3;
        public static final int BET_UP = 4;
        public static final int BET_DOWN = 5;
        public static final int BET_ALL_IN = 6;
        public static final int ROULETTE_BET = 7;
        public static final int ROULETTE_CLEAR = 8;
        public static final int CRASH_CASH_OUT = 9;
        public static final int BJ_HIT = 10;
        public static final int BJ_STAND = 11;
        public static final int BJ_DOUBLE = 12;
        public static final int BJ_SPLIT = 13;
        public static final int CASHIER_TAB = 14;
        public static final int ROULETTE_LEAVE = 15;

        public static final Type<Action> ID = payloadType("casino_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeVarInt(p.action);
                    buf.writeLong(p.pos);
                    buf.writeVarInt(p.a + 1);
                    buf.writeVarInt(p.b + 1);
                    buf.writeVarLong(p.amount);
                    buf.writeUtf(p.text);
                },
                buf -> new Action(buf.readVarInt(), buf.readLong(), buf.readVarInt() - 1, buf.readVarInt() - 1,
                        buf.readVarLong(), buf.readUtf()));

        public static Action of(int action) {
            return new Action(action, 0L, 0, 0, 0L, "");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.ID, State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Devices.ID, Devices.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Cashier.ID, Cashier.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SlotResult.ID, SlotResult.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlinkoBall.ID, PlinkoBall.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RouletteState.ID, RouletteState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CrashState.ID, CrashState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Blackjack.ID, Blackjack.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Pending.ID, Pending.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Fx.ID, Fx.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.ID, Action.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Action.ID, (packet, context) -> {
            var server = context.server();
            var player = context.player();
            server.execute(() -> CasinoGames.handleAction(player, packet));
        });
    }
}
