package net.kasax.challengecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;
import java.util.List;

/** Awards meta progression after completed runs and keeps perk side effects current. */
public class LevelXpListener {
    private static final Identifier HEALTH_BONUS_ID = Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "level_health_bonus");
    private static final Identifier STRENGTH_BONUS_ID = Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "level_strength_bonus");
    private static final Identifier RESISTANCE_BONUS_ID = Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "level_resistance_bonus");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                applyPerks(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                LevelManager.sync(p);
            }
            
            var overworld = server.overworld();
            ChallengeSavedData data = ChallengeSavedData.get(overworld);
            if (data.getActivePerks().contains(LevelManager.PERK_INFINITY_WEAPON)) {
                grantInfinityWeapon(handler.player);
            }
        });
    }

    private static void applyPerks(ServerPlayer player) {
        ChallengeSavedData data = ChallengeSavedData.get(player.level().getServer().overworld());
        List<Integer> activePerks = data.getActivePerks();
        long totalXp = XpManager.getXp(player.getUUID());
        int level = LevelManager.getLevelForXp(totalXp);
        
        // Level 3: Night Vision
        if (activePerks.contains(LevelManager.PERK_NIGHT_VISION) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_NIGHT_VISION)) {
            if (!player.hasEffect(MobEffects.NIGHT_VISION) || player.getEffect(MobEffects.NIGHT_VISION).getDuration() < 1200) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1600, 0, true, false, true));
            }
        }

        // Level 5: Swift Footing (Speed I)
        if (activePerks.contains(LevelManager.PERK_SWIFT_FOOTING) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_SWIFT_FOOTING)) {
            if (!player.hasEffect(MobEffects.SPEED) || player.getEffect(MobEffects.SPEED).getDuration() < 200) {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0, true, false, true));
            }
        }

        // Level 10: Tough Skin (+2 Hearts = 4 health points)
        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            AttributeModifier modifier = healthAttr.getModifier(HEALTH_BONUS_ID);
            if (activePerks.contains(LevelManager.PERK_TOUGH_SKIN) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_TOUGH_SKIN)) {
                if (modifier == null) {
                    healthAttr.addPermanentModifier(new AttributeModifier(
                            HEALTH_BONUS_ID,
                            4.0,
                            AttributeModifier.Operation.ADD_VALUE
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
            if (!player.hasEffect(MobEffects.FIRE_RESISTANCE) || player.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() < 200) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0, true, false, true));
            }
        }

        // Level 14: Strength I
        AttributeInstance strengthAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (strengthAttr != null) {
            AttributeModifier modifier = strengthAttr.getModifier(STRENGTH_BONUS_ID);
            if (activePerks.contains(LevelManager.PERK_STRENGTH) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_STRENGTH)) {
                if (modifier == null) {
                    strengthAttr.addPermanentModifier(new AttributeModifier(
                            STRENGTH_BONUS_ID,
                            3.0,
                            AttributeModifier.Operation.ADD_VALUE
                    ));
                }
            } else {
                if (modifier != null) {
                    strengthAttr.removeModifier(STRENGTH_BONUS_ID);
                }
            }
        }

        // Level 18: Resistance I
        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            AttributeModifier modifier = armorAttr.getModifier(RESISTANCE_BONUS_ID);
            if (activePerks.contains(LevelManager.PERK_RESISTANCE) && level >= LevelManager.getRequiredLevel(LevelManager.PERK_RESISTANCE)) {
                if (modifier == null) {
                    armorAttr.addPermanentModifier(new AttributeModifier(
                            RESISTANCE_BONUS_ID,
                            4.0,
                            AttributeModifier.Operation.ADD_VALUE
                    ));
                }
            } else {
                if (modifier != null) {
                    armorAttr.removeModifier(RESISTANCE_BONUS_ID);
                }
            }
        }
    }

    public static void grantInfinityWeapon(ServerPlayer player) {
        if (LevelManager.getStars(XpManager.getXp(player.getUUID())) < 20) return;
        
        // The perk is reapplied on join, so inventory inspection prevents duplicate rewards.
        boolean hasWeapon = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isInfinityWeapon(stack, player.level().getServer())) {
                hasWeapon = true;
                break;
            }
        }
        
        if (!hasWeapon) {
            ItemStack weapon = createInfinityWeapon(player.level().getServer());
            if (!player.getInventory().add(weapon)) {
                player.drop(weapon, false);
            }
            player.sendSystemMessage(Component.translatable("challengecraft.infinity_weapon.granted").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
    }

    private static boolean isInfinityWeapon(ItemStack stack, MinecraftServer server) {
        if (stack.isEmpty()) return false;

        if (!stack.is(Items.GOLDEN_SWORD)) return false;
        
        ItemAttributeModifiers attrs = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attrs != null) {
            for (ItemAttributeModifiers.Entry entry : attrs.modifiers()) {
                if (entry.modifier().id().equals(Identifier.fromNamespaceAndPath("challengecraft", "infinity_weapon_damage"))) {
                    return true;
                }
            }
        }

        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants != null) {
            var registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var sharpnessEntry = registry.getOrThrow(Enchantments.SHARPNESS);
            if (enchants.getLevel(sharpnessEntry) >= 1000) return true;
        }
        
        return false;
    }

    public static ItemStack createInfinityWeapon(MinecraftServer server) {
        ItemStack stack = new ItemStack(Items.GOLDEN_SWORD);
        
        Component rainbowName = createRainbowName(Component.translatable("challengecraft.item.infinity_weapon").getString());
        stack.set(DataComponents.CUSTOM_NAME, rainbowName);
        
        ItemAttributeModifiers.Builder attrBuilder = ItemAttributeModifiers.builder();
        attrBuilder.add(Attributes.ATTACK_DAMAGE, 
            new AttributeModifier(Identifier.fromNamespaceAndPath("challengecraft", "infinity_weapon_damage"), 1000000.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrBuilder.build());

        var registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var sharpnessEntry = registry.getOrThrow(Enchantments.SHARPNESS);
        
        Mutable builder = new Mutable(ItemEnchantments.EMPTY);
        builder.upgrade(sharpnessEntry, 1000);
        stack.set(DataComponents.ENCHANTMENTS, builder.toImmutable());
        
        return stack;
    }

    private static Component createRainbowName(String value) {
        ChatFormatting[] rainbow = {
                ChatFormatting.AQUA,
                ChatFormatting.GREEN,
                ChatFormatting.YELLOW,
                ChatFormatting.RED,
                ChatFormatting.LIGHT_PURPLE,
                ChatFormatting.BLUE
        };
        MutableComponent result = Component.empty();
        for (int i = 0; i < value.length(); i++) {
            result.append(Component.empty().append(String.valueOf(value.charAt(i))).withStyle(rainbow[i % rainbow.length], ChatFormatting.BOLD));
        }
        return result;
    }
}
