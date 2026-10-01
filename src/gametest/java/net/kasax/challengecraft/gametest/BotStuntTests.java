package net.kasax.challengecraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.challengecraft.bot.task.FallTask;
import net.kasax.challengecraft.bot.task.NavGoalTask;
import net.kasax.challengecraft.bot.task.PlaceAndUseTask;
import net.kasax.challengecraft.bot.task.SequenceTask;
import net.kasax.challengecraft.bot.task.UseOnMobTask;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;

import java.util.List;

import static net.kasax.challengecraft.gametest.BotArena.FEET;
import static net.kasax.challengecraft.gametest.BotArena.STRUCTURE;

/** The things Lockout asks to do rather than to have. */
public class BotStuntTests {
    /** Pillar five up, jump off: hurt, not dead. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void takeFallDamage(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fall_damage");
        a.spawn(20, FEET, 20, new ItemStack(Items.DIRT, 10));
        a.run(new FallTask(5, false), 600, () -> a.bot().body().getHealth() < 20 && a.bot().body().onGround());
    }

    /** Twenty-two up and off without the water bucket (it carries one): badly hurt, alive. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void fallTwentyAndSurvive(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fall_20");
        a.spawn(20, FEET, 20, new ItemStack(Items.DIRT, 30), new ItemStack(Items.WATER_BUCKET));
        a.run(new FallTask(22, true), 1200, () -> a.bot().body().isAlive() && a.bot().body().getHealth() <= 5 && a.bot().body().onGround());
    }

    /** Down through the stone to stand on the bedrock at the bottom. */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void standOnBedrock(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "bedrock");
        a.fill(0, 0, 0, 39, 0, 39, Blocks.BEDROCK);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PICKAXE));
        var level = h.getLevel();
        a.run(new NavGoalTask("stand on the bedrock", (l, p) -> l.getBlockState(p.below()).is(Blocks.BEDROCK),
                b -> a.abs(20, 1, 20), 1600), 1600, () -> level.getBlockState(a.bot().body().blockPosition().below()).is(Blocks.BEDROCK));
    }

    /** TNT down, lit, and away from it. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void igniteTnt(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "ignite_tnt");
        a.spawn(20, FEET, 20, new ItemStack(Items.TNT), new ItemStack(Items.FLINT_AND_STEEL));
        a.run(new PlaceAndUseTask(Items.TNT, PlaceAndUseTask.Then.USE_ITEM, Items.FLINT_AND_STEEL, 8), 400, () -> {
            var tnt = h.getLevel().getEntitiesOfClass(PrimedTnt.class, h.getBounds().inflate(8));
            return !tnt.isEmpty() && tnt.get(0).distanceTo(a.bot().body()) > 5;
        });
    }

    /** A plate put down and stepped on. */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void pressurePlate(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "plate");
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PRESSURE_PLATE));
        a.run(new PlaceAndUseTask(Items.STONE_PRESSURE_PLATE, PlaceAndUseTask.Then.STEP_ON, null, 0), 300, () -> {
            for (BlockPos p : BlockPos.betweenClosed(a.abs(15, FEET, 15), a.abs(25, FEET, 25))) {
                var st = h.getLevel().getBlockState(p);
                if (st.is(Blocks.STONE_PRESSURE_PLATE) && st.getValue(PressurePlateBlock.POWERED)) return true;
            }
            return false;
        });
    }

    /** A bed put down and clicked: the spawn is set there. */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void setSpawnAtBed(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "bed_spawn");
        var bed = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_bed"));
        a.spawn(20, FEET, 20, new ItemStack(bed));
        a.run(new PlaceAndUseTask(bed, PlaceAndUseTask.Then.CLICK, null, 0), 300,
                () -> a.bot().body().getRespawnConfig() != null);
    }

    /** Two cows fed wheat: in love, a calf on the way. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void breedCows(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "breed");
        for (int i = 0; i < 2; i++) {
            var cow = EntityTypes.COW.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            cow.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(26 + i * 2, FEET, 20)));
            h.getLevel().addFreshEntity(cow);
        }
        a.spawn(14, FEET, 20, new ItemStack(Items.WHEAT, 2));
        java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> ready = e -> e instanceof Animal an && !an.isBaby() && !an.isInLove() && an.canFallInLove();
        a.run(new SequenceTask("breed", List.of(() -> new UseOnMobTask(EntityTypes.COW, Items.WHEAT, ready),
                () -> new UseOnMobTask(EntityTypes.COW, Items.WHEAT, ready))), 600,
                () -> h.getLevel().getEntitiesOfClass(Animal.class, h.getBounds().inflate(4), an -> an.isBaby() || an.isInLove()).size() >= 2);
    }

    /** Onto a horse (with an empty hand). */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void rideHorse(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "ride_horse");
        var horse = EntityTypes.HORSE.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        horse.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(28, FEET, 20)));
        h.getLevel().addFreshEntity(horse);
        a.spawn(16, FEET, 20, new ItemStack(Items.DIRT, 3));
        a.run(new UseOnMobTask(EntityTypes.HORSE, Items.AIR, e -> true), 400, () -> a.bot().body().isPassenger());
    }

    /** A bell rung. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void ringBell(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "ring_bell");
        a.fill(28, FEET, 20, 28, FEET, 20, Blocks.BELL);
        a.spawn(14, FEET, 20);
        boolean[] rung = {false};
        a.run(new net.kasax.challengecraft.bot.task.ClickBlockTask("ring a bell", st -> st.is(Blocks.BELL)), 400, () -> {
            if (h.getLevel().getBlockEntity(a.abs(28, FEET, 20)) instanceof net.minecraft.world.level.block.entity.BellBlockEntity bell && bell.shaking) rung[0] = true;
            return rung[0];
        });
    }

    /** An ender pearl thrown. */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void throwPearl(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "pearl");
        a.spawn(20, FEET, 20, new ItemStack(Items.ENDER_PEARL));
        a.run(new net.kasax.challengecraft.bot.task.UseItemTask(Items.ENDER_PEARL, -30f), 100,
                () -> a.bot().body().getInventory().countItem(Items.ENDER_PEARL) == 0);
    }

    /** A trade with a fletcher (emeralds for arrows, or the other way round). */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void tradeWithFletcher(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "trade");
        var v = EntityTypes.VILLAGER.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        var fletcher = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION)
                .getOrThrow(net.minecraft.world.entity.npc.villager.VillagerProfession.FLETCHER);
        v.setVillagerData(v.getVillagerData().withProfession(fletcher));
        v.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(26, FEET, 20)));
        v.setNoAi(true);
        h.getLevel().addFreshEntity(v);
        // (The offers are drawn at random: enough of the usual payments for any of them.)
        a.spawn(16, FEET, 20, new ItemStack(Items.EMERALD, 16), new ItemStack(Items.GRAVEL, 16), new ItemStack(Items.STICK, 64),
                new ItemStack(Items.FEATHER, 32), new ItemStack(Items.STRING, 32), new ItemStack(Items.FLINT, 32));
        a.run(new net.kasax.challengecraft.bot.task.TradeTask("trade", (vi, o) -> true), 400,
                () -> a.bot().body().getInventory().countItem(Items.EMERALD) != 16 || a.bot().body().getInventory().countItem(Items.ARROW) > 0);
    }

    /** A fish caught from a pond. */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void catchFish(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fish");
        a.fill(14, BotArena.GROUND - 3, 14, 26, BotArena.GROUND, 26, Blocks.WATER);
        a.spawn(20, FEET, 10, new ItemStack(Items.FISHING_ROD));
        a.run(new net.kasax.challengecraft.bot.task.FishTask(), 3600, () -> {
            var inv = a.bot().body().getInventory();
            return inv.countItem(Items.COD) + inv.countItem(Items.SALMON) + inv.countItem(Items.TROPICAL_FISH) + inv.countItem(Items.PUFFERFISH) > 0;
        });
    }

    /** A sword enchanted at its own table. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void enchantSword(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "enchant");
        a.spawn(20, FEET, 20, new ItemStack(Items.ENCHANTING_TABLE), new ItemStack(Items.LAPIS_LAZULI, 3), new ItemStack(Items.STONE_SWORD));
        a.bot().body().giveExperienceLevels(5);
        a.run(new net.kasax.challengecraft.bot.task.EnchantTask(java.util.Set.of(Items.STONE_SWORD)), 400, () -> {
            for (var st : a.bot().body().getInventory().getNonEquipmentItems()) if (st.is(Items.STONE_SWORD) && st.isEnchanted()) return true;
            return false;
        });
    }

    /** Three hearts left and a zombie next to it: away from it, alive. (The arena's edge can corner it: three tries.) */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8, maxAttempts = 3, requiredSuccesses = 1)
    public void retreatWhenLow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "retreat");
        var z = EntityTypes.ZOMBIE.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        z.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(22, FEET, 20)));
        h.getLevel().addFreshEntity(z);
        a.spawn(20, FEET, 20);
        a.bot().body().setHealth(6f);
        boolean[] away = {false}, died = {false};
        h.onEachTick(() -> {
            if (a.bot().body().isAlive() && a.bot().body().distanceTo(z) > 7) away[0] = true;
            if (!a.bot().body().isAlive()) died[0] = true;
        });
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(300), 300, () -> h.getTick() > 250 && away[0] && !died[0]);
    }

    /** Five hearts... no, two and a half, and a skeleton shooting from afar: into the ground, alive. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void hideFromSkeleton(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "hide");
        var sk = EntityTypes.SKELETON.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        sk.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(20, FEET, 32)));
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        h.getLevel().addFreshEntity(sk);
        a.spawn(20, FEET, 20, new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.DIRT, 8));
        a.bot().body().setHealth(5f);
        sk.setTarget(a.bot().body());
        boolean[] died = {false};
        h.onEachTick(() -> { if (!a.bot().body().isAlive()) died[0] = true; });
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(400), 400, () -> h.getTick() > 360 && !died[0]);
    }

    /** Three chickens about to lay: an egg picked up. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void egg(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "egg");
        for (int i = 0; i < 3; i++) {
            var c = EntityTypes.CHICKEN.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            c.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(28 + i, FEET, 28)));
            c.eggTime = 200 + 100 * i;
            h.getLevel().addFreshEntity(c);
        }
        a.spawn(8, FEET, 8);
        a.run(new net.kasax.challengecraft.bot.task.EggTask(), 600,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.EGG)) > 0);
    }

    /** A cod in a pool, a water bucket in hand: the fish in the bucket. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void bucketFish(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "bucket_fish");
        a.fill(24, BotArena.GROUND - 2, 24, 30, BotArena.GROUND, 30, Blocks.WATER);
        var cod = EntityTypes.COD.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        cod.setPos(net.minecraft.world.phys.Vec3.atCenterOf(a.abs(27, BotArena.GROUND - 1, 27)));
        h.getLevel().addFreshEntity(cod);
        a.spawn(20, FEET, 20, new ItemStack(Items.WATER_BUCKET));
        a.run(new UseOnMobTask(EntityTypes.COD, Items.WATER_BUCKET, e -> true), 600,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.COD_BUCKET)) > 0);
    }

    /** "Survive an explosion" open: a creeper going off at a distance, taken at full health. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void creeperWelcome(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "creeper_welcome");
        a.spawn(20, FEET, 20);
        var bot = a.bot();
        bot.welcomeExplosion = true;
        // (After the spawn protection has worn off.)
        var creeper = EntityTypes.CREEPER.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        creeper.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(22, FEET, 20)));
        h.runAfterDelay(80, () -> {
            h.getLevel().addFreshEntity(creeper);
            creeper.setTarget(bot.body());
        });
        boolean[] blast = {false}, died = {false};
        h.onEachTick(() -> {
            var src = bot.body().getLastDamageSource();
            if (src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) blast[0] = true;
            if (!bot.body().isAlive()) died[0] = true;
        });
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(400), 400, () -> blast[0] && !died[0] && h.getTick() > 140);
    }

    /** A bow and arrows, a pig a dozen blocks off: hit with an arrow. */
    @GameTest(structure = STRUCTURE, maxTicks = 500, skyAccess = true, padding = 8)
    public void shootBow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "shoot_bow");
        var pig = EntityTypes.PIG.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        pig.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(20, FEET, 32)));
        pig.setNoAi(true);
        h.getLevel().addFreshEntity(pig);
        a.spawn(20, FEET, 20, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 8));
        a.run(new net.kasax.challengecraft.bot.task.ShootTask(Items.BOW, e -> e == pig, 4), 500,
                () -> pig.getLastDamageSource() != null && pig.getLastDamageSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) || !pig.isAlive());
    }

    /** A crossbow and an arrow: loaded and fired at the ground ahead. */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void shootCrossbow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "shoot_crossbow");
        a.spawn(20, FEET, 20, new ItemStack(Items.CROSSBOW), new ItemStack(Items.ARROW, 2));
        a.run(new net.kasax.challengecraft.bot.task.ShootTask(Items.CROSSBOW, a.abs(20, BotArena.GROUND, 30), 1), 300,
                () -> !h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                        new net.minecraft.world.phys.AABB(a.abs(20, FEET, 20)).inflate(16)).isEmpty());
    }

    /** A target block ten blocks off: an arrow in it. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void hitTarget(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "hit_target");
        a.fill(20, FEET, 30, 20, FEET, 30, Blocks.TARGET);
        a.spawn(20, FEET, 20, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 4));
        a.run(net.kasax.challengecraft.bot.task.ShootTask.atBlock(Items.BOW, st -> st.is(Blocks.TARGET), 2), 400,
                () -> !h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                        new net.minecraft.world.phys.AABB(a.abs(20, FEET, 30)).inflate(0.6)).isEmpty());
    }
}
