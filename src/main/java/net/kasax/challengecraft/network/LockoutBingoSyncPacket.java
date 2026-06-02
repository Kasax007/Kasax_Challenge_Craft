package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Full lockout match snapshot shared with every participating client. */
public class LockoutBingoSyncPacket implements CustomPacketPayload {
    public record PlayerState(UUID uuid, String name, int teamId, boolean ready, boolean online) {
    }

    public static final Type<LockoutBingoSyncPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "lockout_bingo_sync"));

    public static final StreamCodec<FriendlyByteBuf, LockoutBingoSyncPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<>() {
                @Override
                public void encode(LockoutBingoSyncPacket packet, FriendlyByteBuf buf) {
                    writeStrings(buf, packet.boardGoalIds);
                    writeInts(buf, packet.claimedTeams);
                    writeStrings(buf, packet.claimedByNames);

                    buf.writeVarInt(packet.players.size());
                    for (PlayerState player : packet.players) {
                        buf.writeUUID(player.uuid());
                        buf.writeUtf(player.name());
                        buf.writeVarInt(player.teamId());
                        buf.writeBoolean(player.ready());
                        buf.writeBoolean(player.online());
                    }

                    buf.writeBoolean(packet.started);
                    buf.writeBoolean(packet.ended);
                    buf.writeVarInt(packet.winnerTeamId);
                    buf.writeLong(packet.elapsedTicks);
                    buf.writeVarInt(packet.runId);
                }
            },
            new StreamDecoder<>() {
                @Override
                public LockoutBingoSyncPacket decode(FriendlyByteBuf buf) {
                    List<String> boardGoalIds = readStrings(buf);
                    List<Integer> claimedTeams = readInts(buf);
                    List<String> claimedByNames = readStrings(buf);

                    int playerCount = buf.readVarInt();
                    List<PlayerState> players = new ArrayList<>(playerCount);
                    for (int i = 0; i < playerCount; i++) {
                        players.add(new PlayerState(
                                buf.readUUID(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readBoolean(),
                                buf.readBoolean()
                        ));
                    }

                    return new LockoutBingoSyncPacket(
                            boardGoalIds,
                            claimedTeams,
                            claimedByNames,
                            players,
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            buf.readLong(),
                            buf.readVarInt()
                    );
                }
            }
    );

    private final List<String> boardGoalIds;
    private final List<Integer> claimedTeams;
    private final List<String> claimedByNames;
    private final List<PlayerState> players;
    private final boolean started;
    private final boolean ended;
    private final int winnerTeamId;
    private final long elapsedTicks;
    private final int runId;

    public LockoutBingoSyncPacket(
            List<String> boardGoalIds,
            List<Integer> claimedTeams,
            List<String> claimedByNames,
            List<PlayerState> players,
            boolean started,
            boolean ended,
            int winnerTeamId,
            long elapsedTicks,
            int runId
    ) {
        this.boardGoalIds = List.copyOf(boardGoalIds);
        this.claimedTeams = List.copyOf(claimedTeams);
        this.claimedByNames = List.copyOf(claimedByNames);
        this.players = List.copyOf(players);
        this.started = started;
        this.ended = ended;
        this.winnerTeamId = winnerTeamId;
        this.elapsedTicks = elapsedTicks;
        this.runId = runId;
    }

    public List<String> boardGoalIds() {
        return boardGoalIds;
    }

    public List<Integer> claimedTeams() {
        return claimedTeams;
    }

    public List<String> claimedByNames() {
        return claimedByNames;
    }

    public List<PlayerState> players() {
        return players;
    }

    public boolean started() {
        return started;
    }

    public boolean ended() {
        return ended;
    }

    public int winnerTeamId() {
        return winnerTeamId;
    }

    public long elapsedTicks() {
        return elapsedTicks;
    }

    public int runId() {
        return runId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }

    private static void writeInts(FriendlyByteBuf buf, List<Integer> values) {
        buf.writeVarInt(values.size());
        for (Integer value : values) {
            buf.writeVarInt(value);
        }
    }

    private static List<Integer> readInts(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Integer> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readVarInt());
        }
        return values;
    }
}
