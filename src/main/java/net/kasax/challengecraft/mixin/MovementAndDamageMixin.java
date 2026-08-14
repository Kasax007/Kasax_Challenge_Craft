package net.kasax.challengecraft.mixin;

import net.kasax.challengecraft.challenges.*;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
/** Central player hook for movement and shared damage challenges. */
public abstract class MovementAndDamageMixin {

    @Unique
    private double walkDistanceAccumulator = 0;
    @Unique
    private double walkDamageDistanceAccumulator = 0;
    @Unique
    private Vec3 lastPos = null;
    @Unique
    private float damageAccumulator = 0;
    @Unique
    private BlockPos lastBlockPos = null;
    @Unique
    private int standingTicks = 0;
    @Unique
    private static boolean sharingDamage = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        if (Chal_17_WalkRandomItem.isActive() || Chal_28_WalkDamage.isActive()) {
            Vec3 currentPos = player.position();
            if (lastPos != null) {
                double dist = currentPos.distanceTo(lastPos);
                
                if (Chal_17_WalkRandomItem.isActive()) {
                    walkDistanceAccumulator += dist;
                    while (walkDistanceAccumulator >= 500.0) {
                        walkDistanceAccumulator -= 500.0;
                        player.getInventory().add(Chal_17_WalkRandomItem.getRandomItem(player.getRandom()));
                    }
                }

                if (Chal_28_WalkDamage.isActive()) {
                    walkDamageDistanceAccumulator += dist;
                    while (walkDamageDistanceAccumulator >= 1.0) {
                        walkDamageDistanceAccumulator -= 1.0;
                        player.hurtServer(player.level(), player.level().damageSources().generic(), 2.0f);
                    }
                }
            }
            lastPos = currentPos;
        } else {
            lastPos = null;
            walkDistanceAccumulator = 0;
            walkDamageDistanceAccumulator = 0;
        }

        if (Chal_29_FloorIsLava.isActive() && !player.isCreative() && !player.isSpectator()) {
            BlockPos currentBlockPos = player.blockPosition();
            // The block list lives on the challenge class, where someone looking for it will look.
            boolean onNatural = Chal_29_FloorIsLava.isNaturalGround(
                    player.level().getBlockState(currentBlockPos.below()));

            if (currentBlockPos.equals(lastBlockPos)) {
                standingTicks++;
            } else {
                standingTicks = 0;
                lastBlockPos = currentBlockPos;
            }

            if (onNatural || standingTicks > 60) {
                player.igniteForSeconds(3);
                player.hurtServer(player.level(), player.level().damageSources().onFire(), 1.0f);
            }
        } else {
            standingTicks = 0;
            lastBlockPos = null;
        }

        if (Chal_30_HeavyPockets.isActive() && !player.isCreative() && !player.isSpectator()) {
            int filledSlots = 0;
            for (int i = 0; i < 36; i++) {
                if (!player.getInventory().getItem(i).isEmpty()) {
                    filledSlots++;
                }
            }

            if (filledSlots > 0) {
                if (filledSlots == 36) {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 5, false, false, true));
                } else {
                    int amplifier = (filledSlots / 6) - 1;
                    if (amplifier >= 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, amplifier, false, false, true));
                    }
                }
            }
        }
    }

    // Descriptor pinned deliberately: this used to be a bare method = "damage", which was
    // unambiguous only by luck. Verified against the mapped jar.
    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("RETURN"), cancellable = true)
    private void onDamage(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            ServerPlayer player = (ServerPlayer) (Object) this;

            if (Chal_32_SymbioticBond.isActive() && !sharingDamage) {
                sharingDamage = true;
                for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
                    if (other != player && !other.isCreative() && !other.isSpectator()) {
                        other.hurtServer(world, source, amount);
                    }
                }
                sharingDamage = false;
            }

            if (Chal_18_DamageRandomItem.isActive()) {
                damageAccumulator += amount;

                if (damageAccumulator >= 2.0f) {
                    int hearts = (int) (damageAccumulator / 2.0f);
                    damageAccumulator %= 2.0f;

                    ItemStack reward = Chal_18_DamageRandomItem.getRandomItem(player.getRandom());
                    reward.setCount(hearts);
                    player.getInventory().add(reward);
                }
            }
            if (Chal_25_DamageWorldBorder.isActive()) {
                double current = Chal_25_DamageWorldBorder.getDiameter();
                double next = current + amount;
                Chal_25_DamageWorldBorder.setDiameter(next);

                player.level().getServer().getAllLevels().forEach(w -> {
                    double currentSize = w.getWorldBorder().getSize();
                    if (next > currentSize) {
                        // Use interpolation so border growth remains readable while taking damage.
                        // 26.2 moved the border lerp onto GAME TIME: the duration is now in ticks
                        // (not milliseconds) and the current time is passed explicitly. Verified —
                        // MovingBorderExtent no longer references System.currentTimeMillis at all,
                        // and vanilla seeds it from levelData.getGameTime(). Keeping the old *1000
                        // would have stretched one second of growth into fifty.
                        w.getWorldBorder().lerpSizeBetween(currentSize, next, (long)((next - currentSize) * 20), w.getGameTime());
                    } else {
                        w.getWorldBorder().setSize(next);
                    }
                });

                ChallengeSavedData data = ChallengeSavedData.get(world.getServer().overworld());
                data.setDamageWorldBorderSize(next);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        if (Chal_21_Hardcore.isActive()) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            ChallengeSavedData data = ChallengeSavedData.get(player.level().getServer().overworld());

            if (data.getInitialDifficulty() > 0) {
                data.setInitialDifficulty(0);
                data.setTainted(true);

                Component title = Component.translatable("challengecraft.hardcore.failed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                Component subtitle = Component.translatable("challengecraft.hardcore.failed.desc").withStyle(ChatFormatting.GRAY);

                player.level().getServer().getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                player.level().getServer().getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));
                player.level().getServer().getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));

                player.level().getServer().getAllLevels().forEach(world -> {
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 1.0f, 1.0f);
                });
            }
        }
    }
}
