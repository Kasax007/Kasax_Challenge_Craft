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
    public final int          mobHealthMultiplier;
    public final int          doubleTroubleMultiplier;
    public final List<Integer> perks;
    public final boolean       restart;


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
                            buf.writeVarInt(pkt.mobHealthMultiplier);
                            buf.writeVarInt(pkt.doubleTroubleMultiplier);
                            buf.writeVarInt(pkt.perks.size());
                            for (int id : pkt.perks) buf.writeVarInt(id);
                            buf.writeBoolean(pkt.restart);
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
                            int mobHealth = buf.readVarInt();
                            int doubleTrouble = buf.readVarInt();
                            int perkSize = buf.readVarInt();
                            List<Integer> perks = new ArrayList<>(perkSize);
                            for (int i = 0; i < perkSize; i++) {
                                perks.add(buf.readVarInt());
                            }
                            boolean restart = buf.readBoolean();
                            return new ChallengePacket(list, hearts, slots, mobHealth, doubleTrouble, perks, restart);
                        }
                    }
            );

    /** Construct on the client when sending */
    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, List<Integer> perks, boolean restart) {
        this.active    = active;
        this.maxHearts = maxHearts;
        this.limitedInventorySlots = slots;
        this.mobHealthMultiplier = mobHealth;
        this.doubleTroubleMultiplier = doubleTrouble;
        this.perks = perks;
        this.restart = restart;
    }

    // Default constructor for standard update (no restart)
    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, List<Integer> perks) {
        this(active, maxHearts, slots, mobHealth, doubleTrouble, perks, false);
    }

    /** Called by Fabric when it needs to serialize */
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(active.size());
        for (int id : active) buf.writeVarInt(id);
        buf.writeVarInt(maxHearts);
        buf.writeVarInt(limitedInventorySlots);
        buf.writeVarInt(mobHealthMultiplier);
        buf.writeVarInt(doubleTroubleMultiplier);
        buf.writeVarInt(perks.size());
        for (int id : perks) buf.writeVarInt(id);
        buf.writeBoolean(restart);
    }

    /** Identify your channel */
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
