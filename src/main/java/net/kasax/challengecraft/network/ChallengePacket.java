package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ChallengePacket implements CustomPayload {
    public final List<Integer> active;
    public final int           maxHearts;
    public final int          limitedInventorySlots;


    // your channel ID
    public static final Id<ChallengePacket> ID =
            new Id<>(Identifier.of("challengecraft", "update_challenges"));

    // the codec that Fabric will use if you register it via PayloadTypeRegistry
    public static final PacketCodec<PacketByteBuf, ChallengePacket> CODEC =
            CustomPayload.codecOf(
                    // 1) encoder writes list size, each id, then maxHearts
                    new ValueFirstEncoder<PacketByteBuf, ChallengePacket>() {
                        @Override
                        public void encode(ChallengePacket pkt, PacketByteBuf buf) {
                            buf.writeVarInt(pkt.active.size());
                            for (int id : pkt.active) buf.writeVarInt(id);
                            buf.writeVarInt(pkt.maxHearts);
                            buf.writeVarInt(pkt.limitedInventorySlots);
                        }
                    },
                    // 2) decoder reads them back in the same order
                    new PacketDecoder<PacketByteBuf, ChallengePacket>() {
                        @Override
                        public ChallengePacket decode(PacketByteBuf buf) {
                            int size = buf.readVarInt();
                            List<Integer> list = new ArrayList<>(size);
                            for (int i = 0; i < size; i++) {
                                list.add(buf.readVarInt());
                            }
                            int hearts = buf.readVarInt();
                            int slots = buf.readVarInt();
                            return new ChallengePacket(list, hearts, slots);
                        }
                    }
            );

    /** Construct on the client when sending */
    public ChallengePacket(List<Integer> active, int maxHearts, int slots) {
        this.active    = active;
        this.maxHearts = maxHearts;
        this.limitedInventorySlots = slots;
    }

    /** Called by Fabric when it needs to serialize */
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(active.size());
        for (int id : active) buf.writeVarInt(id);
        buf.writeVarInt(maxHearts);
        buf.writeVarInt(limitedInventorySlots);
    }

    /** Identify your channel */
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
