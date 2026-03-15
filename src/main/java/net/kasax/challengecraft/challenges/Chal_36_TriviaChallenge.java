package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.network.TriviaQuestionPacket;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Chal_36_TriviaChallenge {
    private static boolean active = false;
    private static int globalTimer = 0;
    private static final int TRIVIA_INTERVAL = 2400; // 2 minutes in ticks
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

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, pitch);
                }
            }

            if (globalTimer >= TRIVIA_INTERVAL) {
                globalTimer = 0;
                if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                    TriviaQuestion question = TriviaQuestions.getRandom();
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        triggerTrivia(player, question);
                    }
                }
            }

            // Check for timeouts
            long now = System.currentTimeMillis();
            for (UUID uuid : new java.util.HashSet<>(PENDING_TIMEOUTS.keySet())) {
                if (now > PENDING_TIMEOUTS.get(uuid)) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
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
            PENDING_QUESTIONS.remove(handler.player.getUuid());
            PENDING_TIMEOUTS.remove(handler.player.getUuid());
        });
    }

    private static void triggerTrivia(ServerPlayerEntity player, TriviaQuestion question) {
        PENDING_QUESTIONS.put(player.getUuid(), question);
        PENDING_TIMEOUTS.put(player.getUuid(), System.currentTimeMillis() + (TIMEOUT_SECONDS * 1000L));
        ServerPlayNetworking.send(player, new TriviaQuestionPacket(question.question(), question.answers(), question.correctIndex()));
    }

    public static void handleAnswer(ServerPlayerEntity player, int answerIndex) {
        TriviaQuestion question = PENDING_QUESTIONS.remove(player.getUuid());
        PENDING_TIMEOUTS.remove(player.getUuid());
        if (question == null) return;

        if (answerIndex == question.correctIndex()) {
            player.sendMessage(Text.literal("Correct!").formatted(Formatting.GREEN), true);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 1.0f);
        } else {
            processWrongAnswer(player, question);
        }
    }

    private static void handleTimeout(ServerPlayerEntity player) {
        TriviaQuestion question = PENDING_QUESTIONS.remove(player.getUuid());
        PENDING_TIMEOUTS.remove(player.getUuid());
        if (question == null) return;

        player.sendMessage(Text.literal("Time's up!").formatted(Formatting.RED), false);
        processWrongAnswer(player, question);
    }

    private static void processWrongAnswer(ServerPlayerEntity player, TriviaQuestion question) {
        if (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            // Pop totem animation and sound
            player.getServerWorld().sendEntityStatus(player, EntityStatuses.USE_TOTEM_OF_UNDYING);

            // Consume totem
            if (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                player.getMainHandStack().decrement(1);
            } else {
                player.getOffHandStack().decrement(1);
            }

            // Apply totem effects (approximate vanilla)
            player.setHealth(1.0f);
            player.clearStatusEffects();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));

            player.sendMessage(Text.literal("Wrong! But your Totem of Undying saved you!").formatted(Formatting.GOLD), true);
        } else {
            player.sendMessage(Text.literal("Wrong! The correct answer was: " + question.answers().get(question.correctIndex())).formatted(Formatting.RED), false);
            player.damage(player.getServerWorld(), player.getDamageSources().genericKill(), 1000.0f);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
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
