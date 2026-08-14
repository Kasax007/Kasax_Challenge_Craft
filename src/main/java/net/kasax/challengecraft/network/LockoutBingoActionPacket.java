package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Lobby action sent from the lockout client UI to the server. */
public class LockoutBingoActionPacket implements CustomPacketPayload {
    public enum Action {
        JOIN_TEAM,
        LEAVE_TEAM,
        READY,
        UNREADY,
        REQUEST_SYNC
    }

    public static final Type<LockoutBingoActionPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "lockout_bingo_action"));

    public static final StreamCodec<FriendlyByteBuf, LockoutBingoActionPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<>() {
                @Override
                public void encode(LockoutBingoActionPacket packet, FriendlyByteBuf buf) {
                    buf.writeVarInt(packet.action.ordinal());
                    buf.writeVarInt(packet.teamId);
                }
            },
            new StreamDecoder<>() {
                @Override
                public LockoutBingoActionPacket decode(FriendlyByteBuf buf) {
                    Action action = Action.values()[buf.readVarInt()];
                    int teamId = buf.readVarInt();
                    return new LockoutBingoActionPacket(action, teamId);
                }
            }
    );

    private final Action action;
    private final int teamId;

    public LockoutBingoActionPacket(Action action, int teamId) {
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
