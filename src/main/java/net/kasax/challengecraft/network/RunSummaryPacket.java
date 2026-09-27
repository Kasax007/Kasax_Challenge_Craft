package net.kasax.challengecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the client needs to draw and save a run-summary card, sent once when a run ends.
 *
 * <p>Separate from {@link ChallengeRewardPacket} on purpose. That one is a reward *trigger* — it
 * fires on every XP change, including mid-run ones, and its own comment says the dragon is "not a
 * full run-completion screen". Overloading it would mean every level-up popped a summary card.
 *
 * <p>{@link Kind} exists because a run does not always end the same way. Six different places
 * finish a run, and three of them are competitions where the headline number is a placement or a
 * winning team, not a time. A card that always led with the clock would be showing the wrong thing
 * half the time.
 */
public class RunSummaryPacket implements CustomPacketPayload {
    public static final Type<RunSummaryPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "run_summary"));

    /** How the run ended. Ordinal is written on the wire, so only ever append to this list. */
    public enum Kind {
        DRAGON,
        ALL_ITEMS,
        ALL_ENTITIES,
        ALL_ADVANCEMENTS,
        LOCKOUT_BINGO,
        FORCE_ITEM_BATTLE,
        // Appended, never inserted: the kind travels as its ordinal.
        ALL_BIOMES;

        private static final Kind[] VALUES = values();

        public static Kind byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : DRAGON;
        }

        /** Translation key for the card's subtitle, e.g. "Ender Dragon defeated". */
        public String titleKey() {
            return "challengecraft.summary.kind." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Whether the headline number should be the clock rather than a placement. */
        public boolean isTimed() {
            return this != LOCKOUT_BINGO && this != FORCE_ITEM_BATTLE;
        }
    }

    public final Kind kind;
    public final long runTicks;
    public final double difficulty;
    public final boolean tainted;
    public final long xpGained;
    public final long oldXp;
    public final long newXp;
    public final boolean newBest;
    public final List<Integer> activeIds;
    /** Free text for competitive runs: winning team, or this player's placement. Empty otherwise. */
    public final String headline;
    /** 1-based placement, 0 when not a competition. */
    public final int placement;
    public final int participants;

    /** Whether this run was the daily challenge. */
    public final boolean daily;
    /** Rotation index, so the card can name the daily and look up its personal best. */
    public final int dailyIndex;
    /**
     * The UTC day the run belongs to, taken from the server at completion time.
     *
     * <p>Never recomputed on the client when the card is drawn: a player who finishes at 01:00 UTC
     * played yesterday's daily, and a card looked at the next morning must still say so.
     */
    public final long dailyEpochDay;

    public static final StreamCodec<FriendlyByteBuf, RunSummaryPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<FriendlyByteBuf, RunSummaryPacket>() {
                @Override
                public void encode(RunSummaryPacket pkt, FriendlyByteBuf buf) {
                    buf.writeVarInt(pkt.kind.ordinal());
                    buf.writeLong(pkt.runTicks);
                    buf.writeDouble(pkt.difficulty);
                    buf.writeBoolean(pkt.tainted);
                    buf.writeLong(pkt.xpGained);
                    buf.writeLong(pkt.oldXp);
                    buf.writeLong(pkt.newXp);
                    buf.writeBoolean(pkt.newBest);
                    buf.writeVarInt(pkt.activeIds.size());
                    for (int id : pkt.activeIds) {
                        buf.writeVarInt(id);
                    }
                    buf.writeUtf(pkt.headline, 256);
                    buf.writeVarInt(pkt.placement);
                    buf.writeVarInt(pkt.participants);
                    buf.writeBoolean(pkt.daily);
                    buf.writeVarInt(pkt.dailyIndex);
                    buf.writeLong(pkt.dailyEpochDay);
                }
            },
            new StreamDecoder<FriendlyByteBuf, RunSummaryPacket>() {
                @Override
                public RunSummaryPacket decode(FriendlyByteBuf buf) {
                    Kind kind = Kind.byId(buf.readVarInt());
                    long ticks = buf.readLong();
                    double difficulty = buf.readDouble();
                    boolean tainted = buf.readBoolean();
                    long gained = buf.readLong();
                    long oldXp = buf.readLong();
                    long newXp = buf.readLong();
                    boolean best = buf.readBoolean();
                    int count = buf.readVarInt();
                    List<Integer> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(buf.readVarInt());
                    }
                    String headline = buf.readUtf(256);
                    int placement = buf.readVarInt();
                    int participants = buf.readVarInt();
                    boolean daily = buf.readBoolean();
                    int dailyIndex = buf.readVarInt();
                    long dailyEpochDay = buf.readLong();
                    return new RunSummaryPacket(kind, ticks, difficulty, tainted, gained, oldXp, newXp,
                            best, ids, headline, placement, participants, daily, dailyIndex, dailyEpochDay);
                }
            }
    );

    /** Non-daily run. */
    public RunSummaryPacket(Kind kind, long runTicks, double difficulty, boolean tainted,
                            long xpGained, long oldXp, long newXp, boolean newBest,
                            List<Integer> activeIds, String headline, int placement, int participants) {
        this(kind, runTicks, difficulty, tainted, xpGained, oldXp, newXp, newBest, activeIds,
                headline, placement, participants, false, -1, 0L);
    }

    public RunSummaryPacket(Kind kind, long runTicks, double difficulty, boolean tainted,
                            long xpGained, long oldXp, long newXp, boolean newBest,
                            List<Integer> activeIds, String headline, int placement, int participants,
                            boolean daily, int dailyIndex, long dailyEpochDay) {
        this.kind = kind;
        this.runTicks = runTicks;
        this.difficulty = difficulty;
        this.tainted = tainted;
        this.xpGained = xpGained;
        this.oldXp = oldXp;
        this.newXp = newXp;
        this.newBest = newBest;
        this.activeIds = List.copyOf(activeIds);
        this.headline = headline == null ? "" : headline;
        this.placement = placement;
        this.participants = participants;
        this.daily = daily;
        this.dailyIndex = dailyIndex;
        this.dailyEpochDay = dailyEpochDay;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
