package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Client-to-server challenge settings update plus restart intent. */
public class ChallengePacket implements CustomPayload {
    public final List<Integer> active;
    public final int           maxHearts;
    public final int          limitedInventorySlots;
    public final int          mobHealthMultiplier;
    public final int          doubleTroubleMultiplier;
    public final int          gameSpeedMultiplier;
    public final List<Integer> perks;
    public final boolean       restart;

    public static final Id<ChallengePacket> ID =
            new Id<>(Identifier.of("challengecraft", "update_challenges"));

    public static final PacketCodec<PacketByteBuf, ChallengePacket> CODEC =
            CustomPayload.codecOf(
                    new ValueFirstEncoder<PacketByteBuf, ChallengePacket>() {
                        @Override
                        public void encode(ChallengePacket pkt, PacketByteBuf buf) {
                            buf.writeVarInt(pkt.active.size());
                            for (int id : pkt.active) buf.writeVarInt(id);
                            buf.writeVarInt(pkt.maxHearts);
                            buf.writeVarInt(pkt.limitedInventorySlots);
                            buf.writeVarInt(pkt.mobHealthMultiplier);
                            buf.writeVarInt(pkt.doubleTroubleMultiplier);
                            buf.writeVarInt(pkt.gameSpeedMultiplier);
                            buf.writeVarInt(pkt.perks.size());
                            for (int id : pkt.perks) buf.writeVarInt(id);
                            buf.writeBoolean(pkt.restart);
                        }
                    },
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
                            int gameSpeed = buf.readVarInt();
                            int perkSize = buf.readVarInt();
                            List<Integer> perks = new ArrayList<>(perkSize);
                            for (int i = 0; i < perkSize; i++) {
                                perks.add(buf.readVarInt());
                            }
                            boolean restart = buf.readBoolean();
                            return new ChallengePacket(list, hearts, slots, mobHealth, doubleTrouble, gameSpeed, perks, restart);
                        }
                    }
            );

    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, int gameSpeed, List<Integer> perks, boolean restart) {
        this.active    = active;
        this.maxHearts = maxHearts;
        this.limitedInventorySlots = slots;
        this.mobHealthMultiplier = mobHealth;
        this.doubleTroubleMultiplier = doubleTrouble;
        this.gameSpeedMultiplier = gameSpeed;
        this.perks = perks;
        this.restart = restart;
    }

    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, int gameSpeed, List<Integer> perks) {
        this(active, maxHearts, slots, mobHealth, doubleTrouble, gameSpeed, perks, false);
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(active.size());
        for (int id : active) buf.writeVarInt(id);
        buf.writeVarInt(maxHearts);
        buf.writeVarInt(limitedInventorySlots);
        buf.writeVarInt(mobHealthMultiplier);
        buf.writeVarInt(doubleTroubleMultiplier);
        buf.writeVarInt(gameSpeedMultiplier);
        buf.writeVarInt(perks.size());
        for (int id : perks) buf.writeVarInt(id);
        buf.writeBoolean(restart);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
