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

/**
 * Sorted (best-first) Force Item Battle standings plus the server-driven ceremony stage
 * (1 = only the last place revealed; entries.size() = winner revealed). Every Continue press
 * re-broadcasts this packet with an incremented stage so all clients advance together.
 */
public class ForceItemResultsPacket implements CustomPayload {
    public record Entry(String name, int score, int teamId, boolean isTeam, List<String> itemIds) {
    }

    public static final Id<ForceItemResultsPacket> ID =
            new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "force_item_results"));

    public static final PacketCodec<PacketByteBuf, ForceItemResultsPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<>() {
                @Override
                public void encode(ForceItemResultsPacket packet, PacketByteBuf buf) {
                    buf.writeVarInt(packet.stage);
                    buf.writeVarInt(packet.entries.size());
                    for (Entry entry : packet.entries) {
                        buf.writeString(entry.name());
                        buf.writeVarInt(entry.score());
                        buf.writeVarInt(entry.teamId());
                        buf.writeBoolean(entry.isTeam());
                        buf.writeVarInt(entry.itemIds().size());
                        for (String itemId : entry.itemIds()) {
                            buf.writeString(itemId);
                        }
                    }
                }
            },
            new PacketDecoder<>() {
                @Override
                public ForceItemResultsPacket decode(PacketByteBuf buf) {
                    int stage = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String name = buf.readString();
                        int score = buf.readVarInt();
                        int teamId = buf.readVarInt();
                        boolean isTeam = buf.readBoolean();
                        int itemCount = buf.readVarInt();
                        List<String> itemIds = new ArrayList<>(itemCount);
                        for (int j = 0; j < itemCount; j++) {
                            itemIds.add(buf.readString());
                        }
                        entries.add(new Entry(name, score, teamId, isTeam, itemIds));
                    }
                    return new ForceItemResultsPacket(stage, entries);
                }
            }
    );

    private final int stage;
    private final List<Entry> entries;

    public ForceItemResultsPacket(int stage, List<Entry> entries) {
        this.stage = stage;
        this.entries = List.copyOf(entries);
    }

    public int stage() {
        return stage;
    }

    public List<Entry> entries() {
        return entries;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
