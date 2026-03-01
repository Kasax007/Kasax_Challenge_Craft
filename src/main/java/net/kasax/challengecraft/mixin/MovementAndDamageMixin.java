package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.Chal_17_WalkRandomItem;
import net.kasax.challengecraft.challenges.Chal_18_DamageRandomItem;
import net.kasax.challengecraft.challenges.Chal_21_Hardcore;
import net.kasax.challengecraft.challenges.Chal_25_DamageWorldBorder;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MovementAndDamageMixin {

    @Unique
    private double walkDistanceAccumulator = 0;
    @Unique
    private Vec3d lastPos = null;
    @Unique
    private float damageAccumulator = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (Chal_17_WalkRandomItem.isActive()) {
            Vec3d currentPos = player.getPos();
            if (lastPos != null) {
                double dist = currentPos.distanceTo(lastPos);
                walkDistanceAccumulator += dist;

                while (walkDistanceAccumulator >= 500.0) {
                    walkDistanceAccumulator -= 500.0;
                    player.getInventory().insertStack(Chal_17_WalkRandomItem.getRandomItem(player.getRandom()));
                }
            }
            lastPos = currentPos;
        } else {
            lastPos = null;
            walkDistanceAccumulator = 0;
        }
    }

    @Inject(method = "damage", at = @At("RETURN"), cancellable = true)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            if (Chal_18_DamageRandomItem.isActive()) {
                ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
                damageAccumulator += amount;

                if (damageAccumulator >= 2.0f) {
                    int hearts = (int) (damageAccumulator / 2.0f);
                    damageAccumulator %= 2.0f;

                    ItemStack reward = Chal_18_DamageRandomItem.getRandomItem(player.getRandom());
                    reward.setCount(hearts);
                    player.getInventory().insertStack(reward);
                }
            }
            if (Chal_25_DamageWorldBorder.isActive()) {
                ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
                double current = Chal_25_DamageWorldBorder.getDiameter();
                double next = current + amount;
                Chal_25_DamageWorldBorder.setDiameter(next);

                // Sync to all world borders
                player.getServer().getWorlds().forEach(w -> {
                    double currentSize = w.getWorldBorder().getSize();
                    if (next > currentSize) {
                        // Gradual growth: 1 block per second
                        w.getWorldBorder().interpolateSize(currentSize, next, (long)((next - currentSize) * 1000));
                    } else {
                        w.getWorldBorder().setSize(next);
                    }
                });

                // Persist to saved data
                ChallengeSavedData data = ChallengeSavedData.get(world.getServer().getOverworld());
                data.setDamageWorldBorderSize(next);
            }
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        if (Chal_21_Hardcore.isActive()) {
            ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
            ChallengeSavedData data = ChallengeSavedData.get(player.getServer().getOverworld());

            // Only fail if not already failed (difficulty > 0)
            if (data.getInitialDifficulty() > 0) {
                data.setInitialDifficulty(0);
                data.setTainted(true);

                Text title = Text.translatable("challengecraft.hardcore.failed").formatted(Formatting.RED, Formatting.BOLD);
                Text subtitle = Text.translatable("challengecraft.hardcore.failed.desc").formatted(Formatting.GRAY);

                player.getServer().getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 70, 20));
                player.getServer().getPlayerManager().sendToAll(new TitleS2CPacket(title));
                player.getServer().getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));

                player.getServer().getWorlds().forEach(world -> {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 1.0f, 1.0f);
                });
            }
        }
    }
}
