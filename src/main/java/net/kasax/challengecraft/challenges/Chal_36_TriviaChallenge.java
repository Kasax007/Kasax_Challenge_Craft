package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.network.TriviaQuestionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.item.Items;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Schedules trivia questions and applies timed rewards or penalties per player answer. */
public class Chal_36_TriviaChallenge {
    private static boolean active = false;
    private static int globalTimer = 0;
    private static final int TRIVIA_INTERVAL = 2400;
    private static final int TIMEOUT_SECONDS = 60;
    private static final Map<UUID, TriviaQuestion> PENDING_QUESTIONS = new HashMap<>();
    private static final Map<UUID, Long> PENDING_TIMEOUTS = new HashMap<>();

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!active) return;

            globalTimer++;

            // Play countdown sounds 3, 2, 1 seconds before a question appears
            if (globalTimer == TRIVIA_INTERVAL - 60 || globalTimer == TRIVIA_INTERVAL - 40 || globalTimer == TRIVIA_INTERVAL - 20) {
                float pitch = 1.0f;
                if (globalTimer == TRIVIA_INTERVAL - 40) pitch = 1.2f;
                if (globalTimer == TRIVIA_INTERVAL - 20) pitch = 1.5f;

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING, SoundSource.MASTER, 1.0f, pitch);
                }
            }

            if (globalTimer >= TRIVIA_INTERVAL) {
                globalTimer = 0;
                if (!server.getPlayerList().getPlayers().isEmpty()) {
                    TriviaQuestion question = TriviaQuestions.getRandom();
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        triggerTrivia(player, question);
                    }
                }
            }

            long now = System.currentTimeMillis();
            for (UUID uuid : new java.util.HashSet<>(PENDING_TIMEOUTS.keySet())) {
                if (now > PENDING_TIMEOUTS.get(uuid)) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        handleTimeout(player);
                    } else {
                        PENDING_QUESTIONS.remove(uuid);
                        PENDING_TIMEOUTS.remove(uuid);
                    }
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING_QUESTIONS.remove(handler.player.getUUID());
            PENDING_TIMEOUTS.remove(handler.player.getUUID());
        });
    }

    private static void triggerTrivia(ServerPlayer player, TriviaQuestion question) {
        PENDING_QUESTIONS.put(player.getUUID(), question);
        PENDING_TIMEOUTS.put(player.getUUID(), System.currentTimeMillis() + (TIMEOUT_SECONDS * 1000L));
        ServerPlayNetworking.send(player, new TriviaQuestionPacket(question.question(), question.answers(), question.correctIndex()));
    }

    public static void handleAnswer(ServerPlayer player, int answerIndex) {
        TriviaQuestion question = PENDING_QUESTIONS.remove(player.getUUID());
        PENDING_TIMEOUTS.remove(player.getUUID());
        if (question == null) return;

        if (answerIndex == question.correctIndex()) {
            player.sendOverlayMessage(Component.translatable("challengecraft.trivia.correct").withStyle(ChatFormatting.GREEN));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.0f);
        } else {
            processWrongAnswer(player, question);
        }
    }

    private static void handleTimeout(ServerPlayer player) {
        TriviaQuestion question = PENDING_QUESTIONS.remove(player.getUUID());
        PENDING_TIMEOUTS.remove(player.getUUID());
        if (question == null) return;

        player.sendSystemMessage(Component.translatable("challengecraft.trivia.timeout").withStyle(ChatFormatting.RED));
        processWrongAnswer(player, question);
    }

    private static void processWrongAnswer(ServerPlayer player, TriviaQuestion question) {
        if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            player.level().broadcastEntityEvent(player, EntityEvent.PROTECTED_FROM_DEATH);

            if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                player.getMainHandItem().shrink(1);
            } else {
                player.getOffhandItem().shrink(1);
            }

            // Mirror the survival effects players expect after a normal totem save.
            player.setHealth(1.0f);
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));

            player.sendOverlayMessage(Component.translatable("challengecraft.trivia.wrong_totem").withStyle(ChatFormatting.GOLD));
        } else {
            player.sendSystemMessage(Component.translatable("challengecraft.trivia.wrong_answer", question.answers().get(question.correctIndex())).withStyle(ChatFormatting.RED));
            player.hurtServer(player.level(), player.damageSources().genericKill(), 1000.0f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    public static void setActive(boolean isActive) {
        active = isActive;
        if (!active) {
            PENDING_QUESTIONS.clear();
            PENDING_TIMEOUTS.clear();
            globalTimer = 0;
        }
        ChallengeCraft.LOGGER.info("[Chal36] {}", active ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }
}
