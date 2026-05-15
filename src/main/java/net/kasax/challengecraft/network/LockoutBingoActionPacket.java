package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Lobby action sent from the lockout client UI to the server. */
public class LockoutBingoActionPacket implements CustomPayload {
    public enum Action {
        JOIN_TEAM,
        LEAVE_TEAM,
        READY,
        UNREADY,
        REQUEST_SYNC
    }

    public static final Id<LockoutBingoActionPacket> ID =
            new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "lockout_bingo_action"));

    public static final PacketCodec<PacketByteBuf, LockoutBingoActionPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<>() {
                @Override
                public void encode(LockoutBingoActionPacket packet, PacketByteBuf buf) {
                    buf.writeVarInt(packet.action.ordinal());
                    buf.writeVarInt(packet.teamId);
                }
            },
            new PacketDecoder<>() {
                @Override
                public LockoutBingoActionPacket decode(PacketByteBuf buf) {
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
