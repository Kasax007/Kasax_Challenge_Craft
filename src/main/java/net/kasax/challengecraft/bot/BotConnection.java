package net.kasax.challengecraft.bot;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kasax.challengecraft.mixin.ConnectionAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * The connection of a bot: there is no client at the other end, so everything the server sends is
 * dropped and nothing ever arrives. The bot is ticked by {@link BotPlayer} itself, not through a
 * network handler, so this connection never needs to flush, time out or change protocol.
 */
final class BotConnection extends Connection {
    BotConnection() {
        super(PacketFlow.SERVERBOUND);
        ((ConnectionAccessor) (Object) this).challengecraft$setChannel(new EmbeddedChannel());
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }

    @Override
    public void flushChannel() {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocol) {
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public void disconnect(Component reason) {
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
    }

    @Override
    public void tick() {
    }
}
