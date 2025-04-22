package net.kasax.challengecraft.network;

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

    public static final Id<ChallengePacket> ID =
            new Id<>(Identifier.of("challengecraft", "update_challenges"));

    public static final PacketCodec<PacketByteBuf, ChallengePacket> CODEC =
            CustomPayload.codecOf(
                    // 1) encoder: write size + each challenge‑ID as a varint
                    new ValueFirstEncoder<PacketByteBuf, ChallengePacket>() {
                        @Override
                        public void encode(ChallengePacket value, PacketByteBuf buf) {

                        }

                        public void encode(PacketByteBuf buf, ChallengePacket pkt) {
                            buf.writeVarInt(pkt.active.size());
                            for (int id : pkt.active) {
                                buf.writeVarInt(id);
                            }
                        }
                    },
                    // 2) decoder: read back size + that many varints into your List
                    new PacketDecoder<PacketByteBuf, ChallengePacket>() {
                        @Override
                        public ChallengePacket decode(PacketByteBuf buf) {
                            int size = buf.readVarInt();
                            List<Integer> list = new ArrayList<>(size);
                            for (int i = 0; i < size; i++) {
                                list.add(buf.readVarInt());
                            }
                            return new ChallengePacket(list);
                        }
                    }
            );

    /** client or command code uses this to build the packet to send */
    public ChallengePacket(List<Integer> active) {
        this.active = List.copyOf(active);
    }

    /** used if you ever call ClientPlayNetworking.send(...) directly */
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(active.size());
        for (int id : active) buf.writeVarInt(id);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
