package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ChallengeSyncPacket implements CustomPayload {
    public static final Id<ChallengeSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "sync_challenges"));
    
    public final List<Integer> active;
    public final List<Integer> perks;
    public final int maxHearts;
    public final int limitedInventorySlots;
    public final int mobHealthMultiplier;
    public final int doubleTroubleMultiplier;
    public final int gameSpeedMultiplier;

    public static final PacketCodec<PacketByteBuf, ChallengeSyncPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<PacketByteBuf, ChallengeSyncPacket>() {
                @Override
                public void encode(ChallengeSyncPacket pkt, PacketByteBuf buf) {
                    buf.writeVarInt(pkt.active.size());
                    for (int id : pkt.active) buf.writeVarInt(id);
                    buf.writeVarInt(pkt.perks.size());
                    for (int id : pkt.perks) buf.writeVarInt(id);
                    buf.writeVarInt(pkt.maxHearts);
                    buf.writeVarInt(pkt.limitedInventorySlots);
                    buf.writeVarInt(pkt.mobHealthMultiplier);
                    buf.writeVarInt(pkt.doubleTroubleMultiplier);
                    buf.writeVarInt(pkt.gameSpeedMultiplier);
                }
            },
            new PacketDecoder<PacketByteBuf, ChallengeSyncPacket>() {
                @Override
                public ChallengeSyncPacket decode(PacketByteBuf buf) {
                    int size = buf.readVarInt();
                    List<Integer> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) list.add(buf.readVarInt());
                    int perkSize = buf.readVarInt();
                    List<Integer> perks = new ArrayList<>(perkSize);
                    for (int i = 0; i < perkSize; i++) perks.add(buf.readVarInt());
                    int maxHearts = buf.readVarInt();
                    int slots = buf.readVarInt();
                    int mobMult = buf.readVarInt();
                    int doubleTroubleMult = buf.readVarInt();
                    int gameSpeedMult = buf.readVarInt();
                    return new ChallengeSyncPacket(list, perks, maxHearts, slots, mobMult, doubleTroubleMult, gameSpeedMult);
                }
            }
    );

    public ChallengeSyncPacket(List<Integer> active, List<Integer> perks, int maxHearts, int slots, int mobMult, int doubleTroubleMult, int gameSpeedMult) {
        this.active = active;
        this.perks = perks;
        this.maxHearts = maxHearts;
        this.limitedInventorySlots = slots;
        this.mobHealthMultiplier = mobMult;
        this.doubleTroubleMultiplier = doubleTroubleMult;
        this.gameSpeedMultiplier = gameSpeedMult;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
