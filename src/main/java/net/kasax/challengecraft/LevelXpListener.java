package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;

import java.util.List;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class LevelXpListener {
    private static final Identifier HEALTH_BONUS_ID = Identifier.of(ChallengeCraft.MOD_ID, "level_health_bonus");
    private static final Identifier STRENGTH_BONUS_ID = Identifier.of(ChallengeCraft.MOD_ID, "level_strength_bonus");
    private static final Identifier RESISTANCE_BONUS_ID = Identifier.of(ChallengeCraft.MOD_ID, "level_resistance_bonus");

    public static void register() {
//        // XP for killing mobs
//        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
//            if (damageSource.getAttacker() instanceof ServerPlayerEntity player) {
//                long xp = 10;
//                if (entity.getMaxHealth() >= 100) xp = 100;
//                LevelManager.addXp(player, xp);
//            }
//        });
//
//        // XP for mining blocks
//        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
//            if (player instanceof ServerPlayerEntity serverPlayer) {
//                LevelManager.addXp(serverPlayer, 1);
//            }
//        });

        // XP for survivability & Perks application
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
//                if (server.getTicks() % 1200 == 0) { // Every minute (20 * 60)
//                    LevelManager.addXp(player, 5); // Passive XP for surviving
//                }
                
                applyPerks(player);
            }
        });
    }

    private static void applyPerks(ServerPlayerEntity player) {
        ChallengeSavedData data = ChallengeSavedData.get(player.getServerWorld());
        List<Integer> activePerks = data.getActivePerks();
        long totalXp = XpManager.getXp(player.getUuid());
        int level = LevelManager.getLevelForXp(totalXp);

        // Level 3: Night Vision
        if (activePerks.contains(LevelManager.PERK_NIGHT_VISION) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_NIGHT_VISION)) {
            if (!player.hasStatusEffect(StatusEffects.NIGHT_VISION) || player.getStatusEffect(StatusEffects.NIGHT_VISION).getDuration() < 1200) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1600, 0, true, false, true));
            }
        }

        // Level 5: Swift Footing (Speed I)
        if (activePerks.contains(LevelManager.PERK_SWIFT_FOOTING) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_SWIFT_FOOTING)) {
            if (!player.hasStatusEffect(StatusEffects.SPEED) || player.getStatusEffect(StatusEffects.SPEED).getDuration() < 200) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 0, true, false, true));
            }
        }

        // Level 10: Tough Skin (+2 Hearts = 4 health points)
        EntityAttributeInstance healthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) {
            EntityAttributeModifier modifier = healthAttr.getModifier(HEALTH_BONUS_ID);
            if (activePerks.contains(LevelManager.PERK_TOUGH_SKIN) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_TOUGH_SKIN)) {
                if (modifier == null) {
                    healthAttr.addPersistentModifier(new EntityAttributeModifier(
                            HEALTH_BONUS_ID,
                            4.0,
                            EntityAttributeModifier.Operation.ADD_VALUE
                    ));
                }
            } else {
                if (modifier != null) {
                    healthAttr.removeModifier(HEALTH_BONUS_ID);
                }
            }
        }

        // Level 11: Fire Resistance
        if (activePerks.contains(LevelManager.PERK_FIRE_RESISTANCE) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_FIRE_RESISTANCE)) {
            if (!player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) || player.getStatusEffect(StatusEffects.FIRE_RESISTANCE).getDuration() < 200) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 400, 0, true, false, true));
            }
        }

        // Level 14: Strength I
        EntityAttributeInstance strengthAttr = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
        if (strengthAttr != null) {
            EntityAttributeModifier modifier = strengthAttr.getModifier(STRENGTH_BONUS_ID);
            if (activePerks.contains(LevelManager.PERK_STRENGTH) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_STRENGTH)) {
                if (modifier == null) {
                    strengthAttr.addPersistentModifier(new EntityAttributeModifier(
                            STRENGTH_BONUS_ID,
                            3.0,
                            EntityAttributeModifier.Operation.ADD_VALUE
                    ));
                }
            } else {
                if (modifier != null) {
                    strengthAttr.removeModifier(STRENGTH_BONUS_ID);
                }
            }
        }

        // Level 18: Resistance I
        EntityAttributeInstance armorAttr = player.getAttributeInstance(EntityAttributes.ARMOR);
        if (armorAttr != null) {
            EntityAttributeModifier modifier = armorAttr.getModifier(RESISTANCE_BONUS_ID);
            if (activePerks.contains(LevelManager.PERK_RESISTANCE) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_RESISTANCE)) {
                if (modifier == null) {
                    armorAttr.addPersistentModifier(new EntityAttributeModifier(
                            RESISTANCE_BONUS_ID,
                            4.0,
                            EntityAttributeModifier.Operation.ADD_VALUE
                    ));
                }
            } else {
                if (modifier != null) {
                    armorAttr.removeModifier(RESISTANCE_BONUS_ID);
                }
            }
        }
    }
}
