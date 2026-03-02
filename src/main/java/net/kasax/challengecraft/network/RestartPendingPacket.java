package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RestartPendingPacket(String worldName) implements CustomPayload {
    public static final Id<RestartPendingPacket> ID = new Id<>(Identifier.of("challengecraft", "restart_pending"));
    public static final PacketCodec<RegistryByteBuf, RestartPendingPacket> CODEC = CustomPayload.codecOf(RestartPendingPacket::write, RestartPendingPacket::new);

    public RestartPendingPacket(RegistryByteBuf buf) {
        this(buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(worldName);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
