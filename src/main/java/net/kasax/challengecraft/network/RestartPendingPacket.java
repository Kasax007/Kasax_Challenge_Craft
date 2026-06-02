package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Notifies a client that the current world is about to restart. */
public record RestartPendingPacket(String worldName) implements CustomPacketPayload {
    public static final Type<RestartPendingPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "restart_pending"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestartPendingPacket> CODEC = CustomPacketPayload.codec(RestartPendingPacket::write, RestartPendingPacket::new);

    public RestartPendingPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(worldName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
