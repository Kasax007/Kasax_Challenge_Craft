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

/** Full Force Item Battle snapshot: battle state, remaining ticks, everyone's target/score. */
public class ForceItemSyncPacket implements CustomPacketPayload {
    public record PlayerEntry(UUID uuid, String name, String itemId, int score, int jokers, int teamId,
                              List<String> collected) {
    }

    public static final Type<ForceItemSyncPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "force_item_sync"));

    public static final StreamCodec<FriendlyByteBuf, ForceItemSyncPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<>() {
                @Override
                public void encode(ForceItemSyncPacket packet, FriendlyByteBuf buf) {
                    buf.writeVarInt(packet.state);
                    buf.writeLong(packet.remainingTicks);
                    buf.writeVarInt(packet.players.size());
                    for (PlayerEntry entry : packet.players) {
                        buf.writeUUID(entry.uuid());
                        buf.writeUtf(entry.name());
                        buf.writeUtf(entry.itemId());
                        buf.writeVarInt(entry.score());
                        buf.writeVarInt(entry.jokers());
                        buf.writeVarInt(entry.teamId());
                        buf.writeVarInt(entry.collected().size());
                        for (String itemId : entry.collected()) {
                            buf.writeUtf(itemId);
                        }
                    }
                }
            },
            new StreamDecoder<>() {
                @Override
                public ForceItemSyncPacket decode(FriendlyByteBuf buf) {
                    int state = buf.readVarInt();
                    long remaining = buf.readLong();
                    int count = buf.readVarInt();
                    List<PlayerEntry> players = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        UUID uuid = buf.readUUID();
                        String name = buf.readUtf();
                        String itemId = buf.readUtf();
                        int score = buf.readVarInt();
                        int jokers = buf.readVarInt();
                        int teamId = buf.readVarInt();
                        int collectedCount = buf.readVarInt();
                        List<String> collected = new ArrayList<>(collectedCount);
                        for (int j = 0; j < collectedCount; j++) {
                            collected.add(buf.readUtf());
                        }
                        players.add(new PlayerEntry(uuid, name, itemId, score, jokers, teamId, collected));
                    }
                    return new ForceItemSyncPacket(state, remaining, players);
                }
            }
    );

    private final int state;
    private final long remainingTicks;
    private final List<PlayerEntry> players;

    public ForceItemSyncPacket(int state, long remainingTicks, List<PlayerEntry> players) {
        this.state = state;
        this.remainingTicks = remainingTicks;
        this.players = List.copyOf(players);
    }

    public int state() {
        return state;
    }

    public long remainingTicks() {
        return remainingTicks;
    }

    public List<PlayerEntry> players() {
        return players;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
