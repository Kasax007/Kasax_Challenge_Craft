package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kasax.challengecraft.mixin.MobEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        BuiltInRegistries.ENTITY_TYPE.forEach(type -> {
            if (type.getCategory() == MobCategory.MISC) return;
            if (!type.canSummon()) return;
            // The dragon is excluded per the challenge rules; the wither is excluded on top of
            // that because a free-roaming wither every few minutes levels the player's base.
            if (type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WITHER) return;
            MOB_POOL.add(type);
        });
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter = (tickCounter + 1) % 600; // 600 ticks = 30s
            if (tickCounter != 0) return;

            buildPoolIfNeeded();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSpectator() || player.isCreative()) continue;
                spawnRandomMobNear((ServerLevel) player.level(), player);
            }
        });

        // Goals are not persisted with the entity, so re-attach after chunk/world reload.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!active) return;
            if (entity instanceof Mob mob && mob.entityTags().contains(AGGRESSIVE_TAG)) {
                attachAttackGoal(mob);
            }
        });
    }

    private static void spawnRandomMobNear(ServerLevel world, ServerPlayer player) {
        RandomSource random = world.getRandom();
        BlockPos pos = findSpawnPos(world, player, random);

        if (random.nextFloat() < 0.05f) {
            spawnChickenJockey(world, pos, random);
            return;
        }

        EntityType<?> type = MOB_POOL.get(random.nextInt(MOB_POOL.size()));
        Entity entity = type.create(world, EntitySpawnReason.EVENT);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) entity.discard();
            return;
        }

        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        // Vanilla natural-spawn setup: biome gear, natural baby chance, etc.
        mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);

        if (mob instanceof Zombie zombie && random.nextFloat() < 0.10f) {
            zombie.setBaby(true);
        }
        if (random.nextFloat() < 0.10f) {
            equipRandomArmor(mob, random);
        }

        makeAggressiveIfPassive(mob);
        world.addFreshEntity(mob);
    }

    private static void spawnChickenJockey(ServerLevel world, BlockPos pos, RandomSource random) {
        Chicken chicken = EntityTypes.CHICKEN.create(world, EntitySpawnReason.EVENT);
        Zombie zombie = EntityTypes.ZOMBIE.create(world, EntitySpawnReason.EVENT);
        if (chicken == null || zombie == null) return;

        chicken.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, chicken.getYRot(), 0f);
        zombie.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        zombie.setBaby(true);
        chicken.setChickenJockey(true);

        world.addFreshEntity(chicken);
        world.addFreshEntity(zombie);
        zombie.startRiding(chicken, true, true);
    }

    private static final Item[] HELMETS = {Items.LEATHER_HELMET, Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET};
    private static final Item[] CHESTPLATES = {Items.LEATHER_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE};
    private static final Item[] LEGGINGS = {Items.LEATHER_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS};
    private static final Item[] BOOTS = {Items.LEATHER_BOOTS, Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS};

    /** Same armor material across slots, each slot at 60% — reads like a vanilla natural spawn. */
    private static void equipRandomArmor(Mob mob, RandomSource random) {
        int material = random.nextInt(HELMETS.length);
        if (random.nextFloat() < 0.6f) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HELMETS[material]));
        if (random.nextFloat() < 0.6f) mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CHESTPLATES[material]));
        if (random.nextFloat() < 0.6f) mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(LEGGINGS[material]));
        if (random.nextFloat() < 0.6f) mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(BOOTS[material]));
    }

    private static void makeAggressiveIfPassive(Mob mob) {
        if (mob instanceof Monster || mob instanceof EnderDragon || mob instanceof WitherBoss) return;
        if (!(mob instanceof PathfinderMob)) return;
        mob.addTag(AGGRESSIVE_TAG);
        attachAttackGoal(mob);
    }

    private static void attachAttackGoal(Mob mob) {
        if (!(mob instanceof PathfinderMob pathAware)) return;
        ((MobEntityAccessor) mob).challengecraft$getGoalSelector().addGoal(2, new AttackNearestPlayerGoal(pathAware));
    }

    private static BlockPos findSpawnPos(ServerLevel world, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 5 + random.nextInt(6);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos pos = new BlockPos(x, player.getBlockY() + dy, z);
                if (world.getBlockState(pos).isAir()
                        && world.getBlockState(pos.above()).isAir()
                        && world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below())) {
                    return pos;
                }
            }
        }
        return player.blockPosition();
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
        private final PathfinderMob mob;
        private Player target;
        private int attackCooldown;
        private int repathCooldown;

        private AttackNearestPlayerGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!Chal_42_RandomMobSpawn.isActive()) return false;
            Player nearest = mob.level().getNearestPlayer(mob, 16.0);
            if (nearest == null || nearest.isCreative() || nearest.isSpectator() || !nearest.isAlive()) return false;
            target = nearest;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return Chal_42_RandomMobSpawn.isActive()
                    && target != null && target.isAlive()
                    && !target.isCreative() && !target.isSpectator()
                    && mob.distanceToSqr(target) < 24 * 24;
        }

        @Override
        public void stop() {
            target = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null) return;
            mob.getLookControl().setLookAt(target);

            if (--repathCooldown <= 0) {
                repathCooldown = 10;
                mob.getNavigation().moveTo(target, 1.25);
            }

            if (attackCooldown > 0) attackCooldown--;
            if (attackCooldown <= 0 && mob.distanceToSqr(target) < 4.0 && mob.level() instanceof ServerLevel serverWorld) {
                target.hurtServer(serverWorld, serverWorld.damageSources().mobAttack(mob), 2.0f);
                mob.swing(InteractionHand.MAIN_HAND);
                attackCooldown = 20;
            }
        }
    }
}
