package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Per-player movement-budget sync for the Würfel challenge. Sent to one player only — the budget
 * is personal, and the client needs it both for the HUD and for the movement clamp in
 * {@code DiceMovementMixin}.
 */
public class DiceSyncPacket implements CustomPacketPayload {
    public static final Type<DiceSyncPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "dice_sync"));

    /** Remaining horizontal blocks the player may still walk. */
    public final float remaining;
    /** Pips of the last settled roll, purely informational for the HUD (0 = none yet). */
    public final int lastRoll;
    /** True while a die is airborne — the player may not throw another one. */
    public final boolean rolling;

    public DiceSyncPacket(float remaining, int lastRoll, boolean rolling) {
        this.remaining = remaining;
        this.lastRoll = lastRoll;
        this.rolling = rolling;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceSyncPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeFloat(pkt.remaining);
                buf.writeVarInt(pkt.lastRoll);
                buf.writeBoolean(pkt.rolling);
            },
            buf -> new DiceSyncPacket(
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
