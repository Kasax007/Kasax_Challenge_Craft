package net.kasax.challengecraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server challenge settings update plus restart intent. */
public class ChallengePacket implements CustomPacketPayload {
    public final List<Integer> active;
    public final int           maxHearts;
    public final int          limitedInventorySlots;
    public final int          mobHealthMultiplier;
    public final int          doubleTroubleMultiplier;
    public final int          gameSpeedMultiplier;
    public final int          forceItemBattleMinutes;
    public final List<Integer> perks;
    public final boolean       restart;

    public static final Type<ChallengePacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "update_challenges"));

    public static final StreamCodec<FriendlyByteBuf, ChallengePacket> CODEC =
            CustomPacketPayload.codec(
                    new StreamMemberEncoder<FriendlyByteBuf, ChallengePacket>() {
                        @Override
                        public void encode(ChallengePacket pkt, FriendlyByteBuf buf) {
                            buf.writeVarInt(pkt.active.size());
                            for (int id : pkt.active) buf.writeVarInt(id);
                            buf.writeVarInt(pkt.maxHearts);
                            buf.writeVarInt(pkt.limitedInventorySlots);
                            buf.writeVarInt(pkt.mobHealthMultiplier);
                            buf.writeVarInt(pkt.doubleTroubleMultiplier);
                            buf.writeVarInt(pkt.gameSpeedMultiplier);
                            buf.writeVarInt(pkt.forceItemBattleMinutes);
                            buf.writeVarInt(pkt.perks.size());
                            for (int id : pkt.perks) buf.writeVarInt(id);
                            buf.writeBoolean(pkt.restart);
                        }
                    },
                    new StreamDecoder<FriendlyByteBuf, ChallengePacket>() {
                        @Override
                        public ChallengePacket decode(FriendlyByteBuf buf) {
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
                            int fibMinutes = buf.readVarInt();
                            int perkSize = buf.readVarInt();
                            List<Integer> perks = new ArrayList<>(perkSize);
                            for (int i = 0; i < perkSize; i++) {
                                perks.add(buf.readVarInt());
                            }
                            boolean restart = buf.readBoolean();
                            return new ChallengePacket(list, hearts, slots, mobHealth, doubleTrouble, gameSpeed, fibMinutes, perks, restart);
                        }
                    }
            );

    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, int gameSpeed, int forceItemBattleMinutes, List<Integer> perks, boolean restart) {
        this.active    = active;
        this.maxHearts = maxHearts;
        this.limitedInventorySlots = slots;
        this.mobHealthMultiplier = mobHealth;
        this.doubleTroubleMultiplier = doubleTrouble;
        this.gameSpeedMultiplier = gameSpeed;
        this.forceItemBattleMinutes = forceItemBattleMinutes;
        this.perks = perks;
        this.restart = restart;
    }

    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, int gameSpeed, int forceItemBattleMinutes, List<Integer> perks) {
        this(active, maxHearts, slots, mobHealth, doubleTrouble, gameSpeed, forceItemBattleMinutes, perks, false);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(active.size());
        for (int id : active) buf.writeVarInt(id);
        buf.writeVarInt(maxHearts);
        buf.writeVarInt(limitedInventorySlots);
        buf.writeVarInt(mobHealthMultiplier);
        buf.writeVarInt(doubleTroubleMultiplier);
        buf.writeVarInt(gameSpeedMultiplier);
        buf.writeVarInt(forceItemBattleMinutes);
        buf.writeVarInt(perks.size());
        for (int id : perks) buf.writeVarInt(id);
        buf.writeBoolean(restart);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
