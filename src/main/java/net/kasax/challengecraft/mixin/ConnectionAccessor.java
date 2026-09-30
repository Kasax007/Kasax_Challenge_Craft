package net.kasax.challengecraft.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a bot's in-memory connection carry a channel, which the player list asks for (address, logging). */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    void challengecraft$setChannel(Channel channel);
}
