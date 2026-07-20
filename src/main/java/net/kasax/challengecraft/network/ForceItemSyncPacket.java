package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Full Force Item Battle snapshot: battle state, remaining ticks, everyone's target/score. */
public class ForceItemSyncPacket implements CustomPayload {
    public record PlayerEntry(UUID uuid, String name, String itemId, int score, int jokers, int teamId,
                              List<String> collected) {
    }

    public static final Id<ForceItemSyncPacket> ID =
            new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "force_item_sync"));

    public static final PacketCodec<PacketByteBuf, ForceItemSyncPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<>() {
                @Override
                public void encode(ForceItemSyncPacket packet, PacketByteBuf buf) {
                    buf.writeVarInt(packet.state);
                    buf.writeLong(packet.remainingTicks);
                    buf.writeVarInt(packet.players.size());
                    for (PlayerEntry entry : packet.players) {
                        buf.writeUuid(entry.uuid());
                        buf.writeString(entry.name());
                        buf.writeString(entry.itemId());
                        buf.writeVarInt(entry.score());
                        buf.writeVarInt(entry.jokers());
                        buf.writeVarInt(entry.teamId());
                        buf.writeVarInt(entry.collected().size());
                        for (String itemId : entry.collected()) {
                            buf.writeString(itemId);
                        }
                    }
                }
            },
            new PacketDecoder<>() {
                @Override
                public ForceItemSyncPacket decode(PacketByteBuf buf) {
                    int state = buf.readVarInt();
                    long remaining = buf.readLong();
                    int count = buf.readVarInt();
                    List<PlayerEntry> players = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        UUID uuid = buf.readUuid();
                        String name = buf.readString();
                        String itemId = buf.readString();
                        int score = buf.readVarInt();
                        int jokers = buf.readVarInt();
                        int teamId = buf.readVarInt();
                        int collectedCount = buf.readVarInt();
                        List<String> collected = new ArrayList<>(collectedCount);
                        for (int j = 0; j < collectedCount; j++) {
                            collected.add(buf.readString());
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
