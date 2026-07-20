package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client action from the Force Item Battle UI (team join/leave, sync request, joker). */
public class ForceItemActionPacket implements CustomPayload {
    public enum Action {
        JOIN_TEAM,
        LEAVE_TEAM,
        REQUEST_SYNC,
        USE_JOKER,
        START_BATTLE,
        TRIGGER_RESULTS,
        NEXT_RESULT
    }

    public static final Id<ForceItemActionPacket> ID =
            new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "force_item_action"));

    public static final PacketCodec<PacketByteBuf, ForceItemActionPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<>() {
                @Override
                public void encode(ForceItemActionPacket packet, PacketByteBuf buf) {
                    buf.writeVarInt(packet.action.ordinal());
                    buf.writeVarInt(packet.teamId);
                }
            },
            new PacketDecoder<>() {
                @Override
                public ForceItemActionPacket decode(PacketByteBuf buf) {
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
