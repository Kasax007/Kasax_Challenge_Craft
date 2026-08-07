package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Per-player movement-budget sync for the Würfel challenge. Sent to one player only — the budget
 * is personal, and the client needs it both for the HUD and for the movement clamp in
 * {@code DiceMovementMixin}.
 */
public class DiceSyncPacket implements CustomPayload {
    public static final Id<DiceSyncPacket> ID =
            new Id<>(Identifier.of("challengecraft", "dice_sync"));

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

    public static final PacketCodec<RegistryByteBuf, DiceSyncPacket> CODEC = PacketCodec.of(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
