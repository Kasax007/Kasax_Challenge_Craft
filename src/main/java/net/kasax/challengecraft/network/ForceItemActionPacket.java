package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client action from the Force Item Battle UI (team join/leave, sync request, joker). */
public class ForceItemActionPacket implements CustomPacketPayload {
    public enum Action {
        JOIN_TEAM,
        LEAVE_TEAM,
        REQUEST_SYNC,
        USE_JOKER,
        START_BATTLE,
        TRIGGER_RESULTS,
        NEXT_RESULT
    }

    public static final Type<ForceItemActionPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "force_item_action"));

    public static final StreamCodec<FriendlyByteBuf, ForceItemActionPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<>() {
                @Override
                public void encode(ForceItemActionPacket packet, FriendlyByteBuf buf) {
                    buf.writeVarInt(packet.action.ordinal());
                    buf.writeVarInt(packet.teamId);
                }
            },
            new StreamDecoder<>() {
                @Override
                public ForceItemActionPacket decode(FriendlyByteBuf buf) {
                    Action action = Action.values()[buf.readVarInt()];
                    int teamId = buf.readVarInt();
                    return new ForceItemActionPacket(action, teamId);
                }
            }
    );

    private final Action action;
    private final int teamId;

    public ForceItemActionPacket(Action action, int teamId) {
        this.action = action;
        this.teamId = teamId;
    }

    public Action action() {
        return action;
    }

    public int teamId() {
        return teamId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
