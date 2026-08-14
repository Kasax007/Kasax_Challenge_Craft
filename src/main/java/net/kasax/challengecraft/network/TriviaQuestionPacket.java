package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Server question payload that opens the trivia answer screen. */
public record TriviaQuestionPacket(String question, List<String> answers, int correctIndex) implements CustomPacketPayload {
    public static final Type<TriviaQuestionPacket> ID = new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "trivia_question"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TriviaQuestionPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TriviaQuestionPacket::question,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TriviaQuestionPacket::answers,
            ByteBufCodecs.VAR_INT, TriviaQuestionPacket::correctIndex,
            TriviaQuestionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
