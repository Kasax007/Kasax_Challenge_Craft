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
    /**
     * -1 for an ordinary selection; otherwise the daily rotation index this world was created for.
     *
     * <p>The server does not take the challenge list on trust when this is set — it compares it
     * against its own copy of the rotation. That is what lets a daily hand out challenges above the
     * player's level without opening a hole through which any client could request any challenge.
     */
    public final int           dailyIndex;
    /**
     * The challenge code this selection was loaded from, or "" for none. Like {@link #dailyIndex} it
     * is checked, not trusted: the server decodes it and requires its challenges to be exactly the
     * ones in {@link #active} before it waives the level lock or uses the code's seed.
     */
    public final String        code;

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
                            // Shifted by one so -1 stays a valid VarInt. Leaving this out is what
                            // made every send a DecoderException and threw the player out of the
                            // world: the decoder below reads it unconditionally, so the two halves
                            // have to be edited together. Always.
                            buf.writeVarInt(pkt.dailyIndex + 1);
                            // 512, not 128: a code with most challenges on and a random 64-bit
                            // seed runs past 140 characters, and an over-long writeUtf throws on
                            // the sending client, which drops the player out of the world.
                            buf.writeUtf(pkt.code, 512);
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
                            int dailyIndex = buf.readVarInt() - 1;
                            String code = buf.readUtf(512);
                            return new ChallengePacket(list, hearts, slots, mobHealth, doubleTrouble, gameSpeed, fibMinutes, perks, restart, dailyIndex, code);
                        }
                    }
            );

    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, int gameSpeed, int forceItemBattleMinutes, List<Integer> perks, boolean restart) {
        this(active, maxHearts, slots, mobHealth, doubleTrouble, gameSpeed, forceItemBattleMinutes, perks, restart, -1);
    }

    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, int gameSpeed, int forceItemBattleMinutes, List<Integer> perks, boolean restart, int dailyIndex) {
        this(active, maxHearts, slots, mobHealth, doubleTrouble, gameSpeed, forceItemBattleMinutes, perks, restart, dailyIndex, "");
    }

    public ChallengePacket(List<Integer> active, int maxHearts, int slots, int mobHealth, int doubleTrouble, int gameSpeed, int forceItemBattleMinutes, List<Integer> perks, boolean restart, int dailyIndex, String code) {
        this.code = code == null ? "" : code;
        this.dailyIndex = dailyIndex;
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
