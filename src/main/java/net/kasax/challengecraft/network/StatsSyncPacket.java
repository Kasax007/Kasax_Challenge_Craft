package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public record StatsSyncPacket(Map<Integer, Integer> bestTimes) implements CustomPayload {
    public static final Id<StatsSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "stats_sync"));

    public static final PacketCodec<PacketByteBuf, StatsSyncPacket> CODEC = CustomPayload.codecOf(
            (pkt, buf) -> {
                buf.writeInt(pkt.bestTimes.size());
                pkt.bestTimes.forEach((id, ticks) -> {
                    buf.writeInt(id);
                    buf.writeInt(ticks);
                });
            },
            buf -> {
                int size = buf.readInt();
                Map<Integer, Integer> map = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    map.put(buf.readInt(), buf.readInt());
                }
                return new StatsSyncPacket(map);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
