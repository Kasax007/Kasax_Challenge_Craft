package net.kasax.challengecraft.challenges;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Block;

/** Maps mined block types to stable potion effects for the current world seed. */
public class Chal_19_MinePotionEffect {
    private static boolean active = false;
    private static final Map<Block, MobEffect> MAPPING = new HashMap<>();
    private static long currentSeed = -1;

    public static void register() {
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static boolean isActive() {
        return active;
    }

    public static void applyEffect(ServerPlayer player, Block block) {
        if (!active) return;
        
        if (!(player.level() instanceof ServerLevel serverWorld)) return;
        long worldSeed = serverWorld.getSeed();
        if (worldSeed != currentSeed) {
            currentSeed = worldSeed;
            MAPPING.clear();
        }

        MobEffect effect = MAPPING.computeIfAbsent(block, b -> {
            List<MobEffect> effects = new ArrayList<>();
            BuiltInRegistries.MOB_EFFECT.forEach(effects::add);
            Random random = new Random(currentSeed + BuiltInRegistries.BLOCK.getId(b));
            return effects.get(random.nextInt(effects.size()));
        });

        player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), 200, 0));
    }
}
