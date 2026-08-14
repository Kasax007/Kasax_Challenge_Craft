package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client answer submission for the currently pending trivia question. */
public record TriviaAnswerPacket(int answerIndex) implements CustomPacketPayload {
    public static final Type<TriviaAnswerPacket> ID = new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "trivia_answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TriviaAnswerPacket> CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, TriviaAnswerPacket::answerIndex,
            TriviaAnswerPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
