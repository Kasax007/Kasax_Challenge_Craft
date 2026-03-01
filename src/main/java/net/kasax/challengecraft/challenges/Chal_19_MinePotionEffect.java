package net.kasax.challengecraft.challenges;

import net.minecraft.block.Block;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Chal_19_MinePotionEffect {
    private static boolean active = false;
    private static final Map<Block, StatusEffect> MAPPING = new HashMap<>();
    private static long currentSeed = -1;

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void applyEffect(ServerPlayerEntity player, Block block) {
        if (!active) return;
        
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;
        long worldSeed = serverWorld.getSeed();
        if (worldSeed != currentSeed) {
            currentSeed = worldSeed;
            MAPPING.clear();
        }

        StatusEffect effect = MAPPING.computeIfAbsent(block, b -> {
            List<StatusEffect> effects = new ArrayList<>();
            Registries.STATUS_EFFECT.forEach(effects::add);
            Random random = new Random(currentSeed + Registries.BLOCK.getRawId(b));
            return effects.get(random.nextInt(effects.size()));
        });

        player.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(effect), 200, 0));
    }
}
