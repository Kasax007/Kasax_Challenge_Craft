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

/**
 * Sorted (best-first) Force Item Battle standings plus the server-driven ceremony stage
 * (1 = only the last place revealed; entries.size() = winner revealed). Every Continue press
 * re-broadcasts this packet with an incremented stage so all clients advance together.
 */
public class ForceItemResultsPacket implements CustomPacketPayload {
    public record Entry(String name, int score, int teamId, boolean isTeam, List<String> itemIds) {
    }

    public static final Type<ForceItemResultsPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "force_item_results"));

    public static final StreamCodec<FriendlyByteBuf, ForceItemResultsPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<>() {
                @Override
                public void encode(ForceItemResultsPacket packet, FriendlyByteBuf buf) {
                    buf.writeVarInt(packet.stage);
                    buf.writeVarInt(packet.entries.size());
                    for (Entry entry : packet.entries) {
                        buf.writeUtf(entry.name());
                        buf.writeVarInt(entry.score());
                        buf.writeVarInt(entry.teamId());
                        buf.writeBoolean(entry.isTeam());
                        buf.writeVarInt(entry.itemIds().size());
                        for (String itemId : entry.itemIds()) {
                            buf.writeUtf(itemId);
                        }
                    }
                }
            },
            new StreamDecoder<>() {
                @Override
                public ForceItemResultsPacket decode(FriendlyByteBuf buf) {
                    int stage = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String name = buf.readUtf();
                        int score = buf.readVarInt();
                        int teamId = buf.readVarInt();
                        boolean isTeam = buf.readBoolean();
                        int itemCount = buf.readVarInt();
                        List<String> itemIds = new ArrayList<>(itemCount);
                        for (int j = 0; j < itemCount; j++) {
                            itemIds.add(buf.readUtf());
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
