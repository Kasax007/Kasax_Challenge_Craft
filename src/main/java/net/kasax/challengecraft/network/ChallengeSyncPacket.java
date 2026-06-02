package net.kasax.challengecraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client snapshot of active challenge settings. */
public class ChallengeSyncPacket implements CustomPacketPayload {
    public static final Type<ChallengeSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "sync_challenges"));
    
    public final List<Integer> active;
    public final List<Integer> perks;
    public final int maxHearts;
    public final int limitedInventorySlots;
    public final int mobHealthMultiplier;
    public final int doubleTroubleMultiplier;
    public final int gameSpeedMultiplier;

    public static final StreamCodec<FriendlyByteBuf, ChallengeSyncPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<FriendlyByteBuf, ChallengeSyncPacket>() {
                @Override
                public void encode(ChallengeSyncPacket pkt, FriendlyByteBuf buf) {
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
            new StreamDecoder<FriendlyByteBuf, ChallengeSyncPacket>() {
                @Override
                public ChallengeSyncPacket decode(FriendlyByteBuf buf) {
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
