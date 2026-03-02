package net.kasax.challengecraft;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent.Builder;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.List;

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

        // Sync on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LevelManager.sync(handler.player);
            // Grant Infinity Weapon on join if perk is active and player eligible, only if they don't already have it
            var overworld = server.getOverworld();
            ChallengeSavedData data = ChallengeSavedData.get(overworld);
            if (data.getActivePerks().contains(LevelManager.PERK_INFINITY_WEAPON)) {
                grantInfinityWeapon(handler.player);
            }
        });
    }

    private static void applyPerks(ServerPlayerEntity player) {
        ChallengeSavedData data = ChallengeSavedData.get(player.getServer().getOverworld());
        List<Integer> activePerks = data.getActivePerks();
        long totalXp = XpManager.getXp(player.getUuid());
        int level = LevelManager.getLevelForXp(totalXp);
        int stars = LevelManager.getStars(totalXp);
        
        // Sync level and stars for name coloring and chat
        if (player.getDataTracker().get(LevelManager.INFINITY_STARS) != stars) {
             player.getDataTracker().set(LevelManager.INFINITY_STARS, stars);
        }
        if (player.getDataTracker().get(LevelManager.LEVEL) != level) {
             player.getDataTracker().set(LevelManager.LEVEL, level);
        }

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

    public static void grantInfinityWeapon(ServerPlayerEntity player) {
        if (LevelManager.getStars(XpManager.getXp(player.getUuid())) < 20) return;
        
        // Grant once logic: only if they don't have it yet?
        // The user said "once", but let's check inventory to be safe against accidental double-grant.
        boolean hasWeapon = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isInfinityWeapon(stack, player.getServer())) {
                hasWeapon = true;
                break;
            }
        }
        
        if (!hasWeapon) {
            ItemStack weapon = createInfinityWeapon(player.getServer());
            if (!player.getInventory().insertStack(weapon)) {
                player.dropItem(weapon, false);
            }
            player.sendMessage(Text.literal("§d§lInfinity Weapon granted!"), false);
        }
    }

    private static boolean isInfinityWeapon(ItemStack stack, MinecraftServer server) {
        if (stack.isEmpty()) return false;
        
        // Check for custom name first
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (name != null && name.getString().contains("Infinity Weapon")) return true;

        if (!stack.isOf(Items.GOLDEN_SWORD)) return false;
        
        // Check for attribute modifier
        AttributeModifiersComponent attrs = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attrs != null) {
            for (AttributeModifiersComponent.Entry entry : attrs.modifiers()) {
                if (entry.modifier().id().equals(Identifier.of("challengecraft", "infinity_weapon_damage"))) {
                    return true;
                }
            }
        }

        // Check for enchantment
        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null) {
            var registry = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            var sharpnessEntry = registry.getOrThrow(Enchantments.SHARPNESS);
            if (enchants.getLevel(sharpnessEntry) >= 1000) return true;
        }
        
        return false;
    }

    public static ItemStack createInfinityWeapon(MinecraftServer server) {
        ItemStack stack = new ItemStack(Items.GOLDEN_SWORD);
        
        // Rainbow Name: Infinity Weapon
        Text rainbowName = Text.literal("§b§lI§a§ln§e§lf§c§li§d§ln§9§li§b§lt§a§ly §e§lW§c§le§d§la§9§lp§b§lo§a§ln");
        stack.set(DataComponentTypes.CUSTOM_NAME, rainbowName);
        
        // Add Attribute Modifier for "Infinite" damage
        AttributeModifiersComponent.Builder attrBuilder = AttributeModifiersComponent.builder();
        attrBuilder.add(EntityAttributes.ATTACK_DAMAGE, 
            new EntityAttributeModifier(Identifier.of("challengecraft", "infinity_weapon_damage"), 1000000.0, EntityAttributeModifier.Operation.ADD_VALUE),
            AttributeModifierSlot.MAINHAND);
        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, attrBuilder.build());

        var registry = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var sharpnessEntry = registry.getOrThrow(Enchantments.SHARPNESS);
        
        Builder builder = new Builder(ItemEnchantmentsComponent.DEFAULT);
        builder.add(sharpnessEntry, 1000);
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        
        return stack;
    }
}
