package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Spawns a random mob near every player every thirty seconds, with vanilla-flavoured specials
 * (baby zombies, chicken jockeys, armored mobs). Passive mobs spawned by this challenge get an
 * attack goal so nothing that appears is harmless.
 */
public class Chal_42_RandomMobSpawn {
    /** Marks challenge-spawned passives so their attack goal survives a save/load cycle. */
    public static final String AGGRESSIVE_TAG = "challengecraft_aggressive";

    private static boolean active = false;
    private static int tickCounter = 0;
    private static final List<EntityType<?>> MOB_POOL = new ArrayList<>();

    private static void buildPoolIfNeeded() {
        if (!MOB_POOL.isEmpty()) return;
        Registries.ENTITY_TYPE.forEach(type -> {
            if (type.getSpawnGroup() == SpawnGroup.MISC) return;
            if (!type.isSummonable()) return;
            // The dragon is excluded per the challenge rules; the wither is excluded on top of
            // that because a free-roaming wither every few minutes levels the player's base.
            if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER) return;
            MOB_POOL.add(type);
        });
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter = (tickCounter + 1) % 600; // 600 ticks = 30s
            if (tickCounter != 0) return;

            buildPoolIfNeeded();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator() || player.isCreative()) continue;
                spawnRandomMobNear((ServerWorld) player.getWorld(), player);
            }
        });

        // Goals are not persisted with the entity, so re-attach after chunk/world reload.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!active) return;
            if (entity instanceof MobEntity mob && mob.getCommandTags().contains(AGGRESSIVE_TAG)) {
                attachAttackGoal(mob);
            }
        });
    }

    private static void spawnRandomMobNear(ServerWorld world, ServerPlayerEntity player) {
        Random random = world.random;
        BlockPos pos = findSpawnPos(world, player, random);

        if (random.nextFloat() < 0.05f) {
            spawnChickenJockey(world, pos, random);
            return;
        }

        EntityType<?> type = MOB_POOL.get(random.nextInt(MOB_POOL.size()));
        Entity entity = type.create(world, SpawnReason.EVENT);
        if (!(entity instanceof MobEntity mob)) {
            if (entity != null) entity.discard();
            return;
        }

        mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        // Vanilla natural-spawn setup: biome gear, natural baby chance, etc.
        mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);

        if (mob instanceof ZombieEntity zombie && random.nextFloat() < 0.10f) {
            zombie.setBaby(true);
        }
        if (random.nextFloat() < 0.10f) {
            equipRandomArmor(mob, random);
        }

        makeAggressiveIfPassive(mob);
        world.spawnEntity(mob);
    }

    private static void spawnChickenJockey(ServerWorld world, BlockPos pos, Random random) {
        ChickenEntity chicken = EntityType.CHICKEN.create(world, SpawnReason.EVENT);
        ZombieEntity zombie = EntityType.ZOMBIE.create(world, SpawnReason.EVENT);
        if (chicken == null || zombie == null) return;

        chicken.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        zombie.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, chicken.getYaw(), 0f);
        zombie.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);
        zombie.setBaby(true);
        chicken.setHasJockey(true);

        world.spawnEntity(chicken);
        world.spawnEntity(zombie);
        zombie.startRiding(chicken, true);
    }

    private static final Item[] HELMETS = {Items.LEATHER_HELMET, Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET};
    private static final Item[] CHESTPLATES = {Items.LEATHER_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE};
    private static final Item[] LEGGINGS = {Items.LEATHER_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS};
    private static final Item[] BOOTS = {Items.LEATHER_BOOTS, Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS};

    /** Same armor material across slots, each slot at 60% — reads like a vanilla natural spawn. */
    private static void equipRandomArmor(MobEntity mob, Random random) {
        int material = random.nextInt(HELMETS.length);
        if (random.nextFloat() < 0.6f) mob.equipStack(EquipmentSlot.HEAD, new ItemStack(HELMETS[material]));
        if (random.nextFloat() < 0.6f) mob.equipStack(EquipmentSlot.CHEST, new ItemStack(CHESTPLATES[material]));
        if (random.nextFloat() < 0.6f) mob.equipStack(EquipmentSlot.LEGS, new ItemStack(LEGGINGS[material]));
        if (random.nextFloat() < 0.6f) mob.equipStack(EquipmentSlot.FEET, new ItemStack(BOOTS[material]));
    }

    private static void makeAggressiveIfPassive(MobEntity mob) {
        if (mob instanceof HostileEntity || mob instanceof EnderDragonEntity || mob instanceof WitherEntity) return;
        if (!(mob instanceof PathAwareEntity)) return;
        mob.addCommandTag(AGGRESSIVE_TAG);
        attachAttackGoal(mob);
    }

    private static void attachAttackGoal(MobEntity mob) {
        if (!(mob instanceof PathAwareEntity pathAware)) return;
        ((MobEntityAccessor) mob).challengecraft$getGoalSelector().add(2, new AttackNearestPlayerGoal(pathAware));
    }

    private static BlockPos findSpawnPos(ServerWorld world, ServerPlayerEntity player, Random random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 5 + random.nextInt(6);
            int x = MathHelper.floor(player.getX() + Math.cos(angle) * dist);
            int z = MathHelper.floor(player.getZ() + Math.sin(angle) * dist);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos pos = new BlockPos(x, player.getBlockY() + dy, z);
                if (world.getBlockState(pos).isAir()
                        && world.getBlockState(pos.up()).isAir()
                        && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                    return pos;
                }
            }
        }
        return player.getBlockPos();
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) tickCounter = 0;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Chase-and-hurt goal for challenge-spawned passive mobs. Deliberately does NOT use
     * MeleeAttackGoal/tryAttack: passive mobs have no ATTACK_DAMAGE attribute, so the vanilla
     * attack path would throw. Damage is applied manually instead.
     */
    private static class AttackNearestPlayerGoal extends Goal {
        private final PathAwareEntity mob;
        private PlayerEntity target;
        private int attackCooldown;
        private int repathCooldown;

        private AttackNearestPlayerGoal(PathAwareEntity mob) {
            this.mob = mob;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!Chal_42_RandomMobSpawn.isActive()) return false;
            PlayerEntity nearest = mob.getWorld().getClosestPlayer(mob, 16.0);
            if (nearest == null || nearest.isCreative() || nearest.isSpectator() || !nearest.isAlive()) return false;
            target = nearest;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return Chal_42_RandomMobSpawn.isActive()
                    && target != null && target.isAlive()
                    && !target.isCreative() && !target.isSpectator()
                    && mob.squaredDistanceTo(target) < 24 * 24;
        }

        @Override
        public void stop() {
            target = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null) return;
            mob.getLookControl().lookAt(target);

            if (--repathCooldown <= 0) {
                repathCooldown = 10;
                mob.getNavigation().startMovingTo(target, 1.25);
            }

            if (attackCooldown > 0) attackCooldown--;
            if (attackCooldown <= 0 && mob.squaredDistanceTo(target) < 4.0 && mob.getWorld() instanceof ServerWorld serverWorld) {
                target.damage(serverWorld, serverWorld.getDamageSources().mobAttack(mob), 2.0f);
                mob.swingHand(Hand.MAIN_HAND);
                attackCooldown = 20;
            }
        }
    }
}
