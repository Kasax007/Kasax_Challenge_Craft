package net.kasax.challengecraft.network;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client snapshot of challenge personal-best times. */
public record StatsSyncPacket(Map<Integer, Integer> bestTimes) implements CustomPacketPayload {
    public static final Type<StatsSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "stats_sync"));

    public static final StreamCodec<FriendlyByteBuf, StatsSyncPacket> CODEC = CustomPacketPayload.codec(
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
