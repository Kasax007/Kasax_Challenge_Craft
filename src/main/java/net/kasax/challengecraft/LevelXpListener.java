package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent.Builder;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** Awards meta progression after completed runs and keeps perk side effects current. */
public class LevelXpListener {
    private static final Identifier HEALTH_BONUS_ID = Identifier.of(ChallengeCraft.MOD_ID, "level_health_bonus");
    private static final Identifier STRENGTH_BONUS_ID = Identifier.of(ChallengeCraft.MOD_ID, "level_strength_bonus");
    private static final Identifier RESISTANCE_BONUS_ID = Identifier.of(ChallengeCraft.MOD_ID, "level_resistance_bonus");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyPerks(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                LevelManager.sync(p);
            }
            
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
        
        // The perk is reapplied on join, so inventory inspection prevents duplicate rewards.
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
            player.sendMessage(Text.translatable("challengecraft.infinity_weapon.granted").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        }
    }

    private static boolean isInfinityWeapon(ItemStack stack, MinecraftServer server) {
        if (stack.isEmpty()) return false;

        if (!stack.isOf(Items.GOLDEN_SWORD)) return false;
        
        AttributeModifiersComponent attrs = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attrs != null) {
            for (AttributeModifiersComponent.Entry entry : attrs.modifiers()) {
                if (entry.modifier().id().equals(Identifier.of("challengecraft", "infinity_weapon_damage"))) {
                    return true;
                }
            }
        }

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
        
        Text rainbowName = createRainbowName(Text.translatable("challengecraft.item.infinity_weapon").getString());
        stack.set(DataComponentTypes.CUSTOM_NAME, rainbowName);
        
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

    private static Text createRainbowName(String value) {
        Formatting[] rainbow = {
                Formatting.AQUA,
                Formatting.GREEN,
                Formatting.YELLOW,
                Formatting.RED,
                Formatting.LIGHT_PURPLE,
                Formatting.BLUE
        };
        MutableText result = Text.empty();
        for (int i = 0; i < value.length(); i++) {
            result.append(Text.empty().append(String.valueOf(value.charAt(i))).formatted(rainbow[i % rainbow.length], Formatting.BOLD));
        }
        return result;
    }
}
