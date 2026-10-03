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
import net.minecraft.world.item.Item;
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

    /** Standing in a pit a block deep: the only spots for the plate are a step up; jumped onto. */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void pressurePlateStepUp(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "plate_step_up");
        a.fill(17, FEET, 17, 23, FEET, 23, Blocks.STONE);
        a.fill(20, FEET, 20, 20, FEET, 20, Blocks.AIR);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_PRESSURE_PLATE));
        a.run(new PlaceAndUseTask(Items.STONE_PRESSURE_PLATE, PlaceAndUseTask.Then.STEP_ON, null, 0), 300, () -> {
            for (BlockPos p : BlockPos.betweenClosed(a.abs(15, FEET, 15), a.abs(25, FEET + 2, 25))) {
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
        // (A helmet: other tests set the clock to day, and it would burn.)
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
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

    /** A pufferfish and a tropical fish in a deep pool: both killed in the water, both items picked up. */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void huntFish(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "hunt_fish");
        a.fill(24, BotArena.GROUND - 3, 24, 30, BotArena.GROUND, 30, Blocks.WATER);
        for (var type : java.util.List.of(EntityTypes.PUFFERFISH, EntityTypes.TROPICAL_FISH)) {
            var fish = type.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            fish.setPos(net.minecraft.world.phys.Vec3.atCenterOf(a.abs(27, BotArena.GROUND - 2, 27)));
            ((net.minecraft.world.entity.Mob) fish).setPersistenceRequired();
            h.getLevel().addFreshEntity(fish);
        }
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD));
        a.run(new net.kasax.challengecraft.bot.task.SequenceTask("two fish", java.util.List.of(
                () -> new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(EntityTypes.PUFFERFISH), java.util.Set.of(Items.PUFFERFISH), 1, 1),
                () -> new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(EntityTypes.TROPICAL_FISH), java.util.Set.of(Items.TROPICAL_FISH), 1, 1))), 1600,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.PUFFERFISH)) > 0
                        && net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.TROPICAL_FISH)) > 0);
    }

    /**
     * Bartering, the safe way: in a golden helmet, a hole dug beside it, gold dropped in; the
     * piglin goes in after it and is stuck; two trades, the loot picked up at the rim.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void barterInHole(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "barter_in_hole");
        var level = h.getLevel();
        // (A yard, so the piglin does not wander off out of sight of the gold.)
        a.fill(14, FEET, 14, 30, FEET + 2, 14, Blocks.COBBLESTONE);
        a.fill(14, FEET, 26, 30, FEET + 2, 26, Blocks.COBBLESTONE);
        a.fill(14, FEET, 14, 14, FEET + 2, 26, Blocks.COBBLESTONE);
        a.fill(30, FEET, 14, 30, FEET + 2, 26, Blocks.COBBLESTONE);
        var piglin = EntityTypes.PIGLIN.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        piglin.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(24, FEET, 20)));
        piglin.setImmuneToZombification(true);
        piglin.setCanPickUpLoot(true);
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, true, level.getServer());
        piglin.setPersistenceRequired();
        // (Nothing in its hands: the gold it is given goes to the off hand.)
        piglin.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        level.addFreshEntity(piglin);
        a.spawn(20, FEET, 20, new ItemStack(Items.GOLD_INGOT, 10), new ItemStack(Items.STONE_PICKAXE));
        a.bot().body().setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        a.run(new net.kasax.challengecraft.bot.task.BarterTask(java.util.Set.of(), 0, 2), 2400, () -> {
            var body = a.bot().body();
            h.assertTrue(piglin.getTarget() != body, "the piglin turned on the bot");
            int gold = net.kasax.challengecraft.bot.BotInventory.count(body, Items.GOLD_INGOT);
            int loot = 0;
            for (var st : body.getInventory().getNonEquipmentItems()) {
                if (!st.isEmpty() && !st.is(Items.GOLD_INGOT) && !st.is(Items.STONE_PICKAXE) && !st.is(Items.DIRT)
                        && !st.is(Items.COBBLESTONE) && !st.is(Items.STONE)) loot++;
            }
            return gold <= 8 && loot > 0 && piglin.getY() < a.abs(0, FEET, 0).getY() - 1 && a.bot().current() == null;
        });
    }

    /** A brewing stand close by: water bottle, nether wart, sugar, blaze powder: a potion of swiftness. */
    @GameTest(structure = STRUCTURE, maxTicks = 1400, skyAccess = true, padding = 8)
    public void brewSwiftness(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "brew_swiftness");
        a.fill(23, FEET, 20, 23, FEET, 20, Blocks.BREWING_STAND);
        a.spawn(20, FEET, 20, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER),
                new ItemStack(Items.NETHER_WART), new ItemStack(Items.SUGAR), new ItemStack(Items.BLAZE_POWDER));
        a.run(new net.kasax.challengecraft.bot.task.BrewTask(java.util.List.of(Items.NETHER_WART, Items.SUGAR), 1), 1400, () -> {
            for (var st : a.bot().body().getInventory().getNonEquipmentItems()) {
                var c = st.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                if (st.is(Items.POTION) && c != null && c.is(net.minecraft.world.item.alchemy.Potions.SWIFTNESS)) return true;
            }
            return false;
        });
    }

    /** A pig and a saddle: saddled, ridden a moment, and off again. */
    @GameTest(structure = STRUCTURE, maxTicks = 800, skyAccess = true, padding = 8)
    public void ridePig(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "ride_pig");
        var pig = EntityTypes.PIG.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        pig.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(26, FEET, 20)));
        h.getLevel().addFreshEntity(pig);
        a.spawn(20, FEET, 20, new ItemStack(Items.SADDLE));
        boolean[] rode = {false};
        a.run(new net.kasax.challengecraft.bot.task.RideTask(EntityTypes.PIG), 800, () -> {
            if (a.bot().body().getVehicle() == pig) rode[0] = true;
            return rode[0] && a.bot().body().getVehicle() == null && a.bot().current() == null;
        });
    }

    /** A ghast in the sky: its fireball hit back at it, and it dies of it. */
    // (A batch of its own: it turns the difficulty up, and wants room for the ghast.)
    @GameTest(environment = "challengecraft:ghast", structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 30)
    public void returnToSender(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "return_to_sender");
        var level = h.getLevel();
        // (Peaceful, the test world's default, takes ghasts away at once.)
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        var ghast = EntityTypes.GHAST.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        ghast.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(20, FEET + 3, 32)));
        ghast.setPersistenceRequired();
        h.getLevel().addFreshEntity(ghast);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.COBBLESTONE, 16));
        a.run(new net.kasax.challengecraft.bot.task.GhastDeflectTask(), 2400, () -> {
            if (h.getTick() % 10 == 0) BotArena.LOG.info("[BOTTEST] ghast hp {} at {} last {} hurtBy {}", ghast.getHealth(), ghast.blockPosition().subtract(a.abs(0, 0, 0)).toShortString(),
                    ghast.getLastDamageSource() == null ? "-" : ghast.getLastDamageSource().getMsgId(), ghast.getLastHurtByMob());
            if (ghast.isAlive()) return false;
            var src = ghast.getLastDamageSource();
            h.assertTrue(src != null && src.is(net.minecraft.world.damagesource.DamageTypes.FIREBALL), "the ghast died of " + src + " removed " + ghast.getRemovalReason() + " hp " + ghast.getHealth());
            return true;
        });
    }

    /** Three zombies, a stone sword and a shield: all three killed (critical hits, the shield up between blows), alive at the end. */
    // (A batch of its own: it sets the difficulty.)
    @GameTest(environment = "challengecraft:melee", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void fightZombies(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fight_zombies");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty normal");
        java.util.List<net.minecraft.world.entity.monster.zombie.Zombie> zombies = new java.util.ArrayList<>();
        for (int[] p : new int[][]{{28, 20}, {28, 24}, {24, 28}}) {
            var z = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            z.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(p[0], FEET, p[1])));
            z.setPersistenceRequired();
            level.addFreshEntity(z);
            zombies.add(z);
        }
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_AXE), new ItemStack(Items.SHIELD));
        a.run(new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(EntityTypes.ZOMBIE), java.util.Set.of(), 0, 3), 1600, () -> {
            BotArena.LOG.info("[BOTTEST] fight_zombies hp {} zombies {}", a.bot().body().getHealth(), zombies.stream().filter(z -> z.isAlive()).count());
            return zombies.stream().noneMatch(z -> z.isAlive());
        });
    }

    /** A creeper creeping up while it waits about, a sword in the pack: dealt with (killed, or left to go off far away), unhurt. */
    @GameTest(environment = "challengecraft:melee", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void creeperArmed(GameTestHelper h) {
        creeper(h, "creeper_armed", true);
    }

    /** A skeleton shooting at it: the arrows end in the shield (or the skeleton is dealt with), the bot whole. */
    // (A batch of its own: the arrows would find the bots of the tests next door.)
    @GameTest(environment = "challengecraft:archery", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 90)
    public void skeletonShield(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "skeleton_shield");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        var sk = EntityTypes.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        sk.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(32, FEET, 20)));
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        sk.setPersistenceRequired();
        level.addFreshEntity(sk);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.SHIELD));
        // (The arena is small: what is checked is the arrow in the shield, the bot whole.)
        var blocked = net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DAMAGE_BLOCKED_BY_SHIELD);
        int before = a.bot().body().getStats().getValue(blocked);
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(880), 880, () -> {
            var body = a.bot().body();
            if (body.getHealth() < 12) h.fail("shot: " + body.getHealth());
            return body.getStats().getValue(blocked) > before && body.getHealth() >= 14 || !sk.isAlive();
        });
    }

    /** A skeleton, no shield: run at it dodging the arrows, killed with the sword, the bot not badly hurt. */
    @GameTest(environment = "challengecraft:duel", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 90)
    public void skeletonDuel(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "skeleton_duel");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        var sk = EntityTypes.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        sk.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(24, FEET, 20)));
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        sk.setPersistenceRequired();
        level.addFreshEntity(sk);
        a.spawn(12, FEET, 20, new ItemStack(Items.STONE_SWORD));
        a.run(new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(EntityTypes.SKELETON), java.util.Set.of(), 0, 1), 900, () -> {
            var body = a.bot().body();
            if (body.getHealth() < 12) h.fail("shot: " + body.getHealth());
            if (!sk.isAlive()) BotArena.LOG.info("[BOTTEST] skeleton_duel hp left {}", body.getHealth());
            return !sk.isAlive();
        });
    }

    /** Gunpowder wanted: the creeper is killed (not let go off), the bot little hurt. */
    // (A batch of its own, well apart: an explosion would hurt the tests next door.)
    @GameTest(environment = "challengecraft:demolition", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 90)
    public void creeperKill(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "creeper_kill");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        var c = EntityTypes.CREEPER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        c.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(30, FEET, 20)));
        c.setPersistenceRequired();
        level.addFreshEntity(c);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD));
        a.run(new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(EntityTypes.CREEPER), java.util.Set.of(), 0, 1), 1200, () -> {
            if (a.bot().body().getHealth() < 12) h.fail("hurt by the creeper: " + a.bot().body().getHealth());
            if (!c.isAlive() && c.getHealth() > 0) h.fail("it went off");
            return !c.isAlive();
        });
    }

    /** The same with nothing to fight with: kept away from (alive at the end). */
    @GameTest(environment = "challengecraft:melee", structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void creeperUnarmed(GameTestHelper h) {
        creeper(h, "creeper_unarmed", false);
    }

    private static void creeper(GameTestHelper h, String name, boolean armed) {
        BotArena a = BotArena.flat(h, name);
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        var c = EntityTypes.CREEPER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        c.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(30, FEET, 20)));
        c.setPersistenceRequired();
        level.addFreshEntity(c);
        if (armed) a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD));
        else a.spawn(20, FEET, 20, new ItemStack(Items.DIRT, 4));
        // (Gone off far enough away to do no harm counts too: what matters is the bot unhurt.)
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(880), 880, () -> {
            if (a.bot().body().getHealth() < 14) h.fail("hurt by the creeper: " + a.bot().body().getHealth());
            return armed ? !c.isAlive() : h.getTick() > 600;
        });
    }

    /** A full pack of odds and ends and a bundle: room made by bundling them up, nothing thrown away. */
    @GameTest(structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void bundleOddsAndEnds(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "bundle_odds_and_ends");
        Item[] odds = {Items.DANDELION, Items.POPPY, Items.WHEAT_SEEDS, Items.OAK_SAPLING, Items.BIRCH_SAPLING, Items.BONE, Items.FEATHER,
                Items.FLINT, Items.CLAY_BALL, Items.PAPER, Items.SUGAR, Items.EGG, Items.ARROW, Items.STRING, Items.GUNPOWDER, Items.REDSTONE,
                Items.LAPIS_LAZULI, Items.QUARTZ, Items.GLOWSTONE_DUST, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET, Items.CORNFLOWER,
                Items.SPRUCE_SAPLING, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.BEETROOT_SEEDS, Items.COCOA_BEANS, Items.INK_SAC,
                Items.SLIME_BALL, Items.LEATHER, Items.RABBIT_HIDE, Items.SNOWBALL, Items.KELP, Items.SEAGRASS};
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        stacks.add(new ItemStack(Items.BUNDLE));
        for (Item i : odds) stacks.add(new ItemStack(i, 2));
        a.spawn(20, FEET, 20, stacks.toArray(new ItemStack[0]));
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(150), 200, () -> {
            var body = a.bot().body();
            h.assertTrue(net.kasax.challengecraft.bot.BotWorld.drops(h.getLevel(), body.blockPosition(), 8, (java.util.Set<Item>) null).isEmpty(), "threw something away");
            return net.kasax.challengecraft.bot.BotBundles.freeSlots(body) >= 2 && net.kasax.challengecraft.bot.BotBundles.countInside(body, java.util.Set.of(odds)) > 0;
        });
    }

    /** A skeleton sixty blocks off along a causeway: shot dead from over fifty blocks. */
    // (A batch of its own: it widens the server's view and simulation distance meanwhile.)
    @GameTest(environment = "challengecraft:sniper", structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 90)
    public void sniperDuel(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "sniper_duel");
        a.fill(40, BotArena.GROUND - 2, 16, 90, BotArena.GROUND, 24, Blocks.STONE);
        a.fill(40, FEET, 16, 40, FEET + 24, 24, Blocks.AIR); // (the test's barrier wall: opened)
        var level = h.getLevel();
        var sk = EntityTypes.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        sk.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(80, FEET, 20)));
        sk.setNoAi(true);
        sk.setPersistenceRequired();
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // (no burning in the sun)
        level.addFreshEntity(sk);
        // (The test server simulates only a chunk or two round a player: arrows would stop in the
        // air half way. A real server's simulation distance is ten.)
        var players = level.getServer().getPlayerList();
        int view = players.getViewDistance(), sim = players.getSimulationDistance();
        players.setSimulationDistance(10);
        players.setViewDistance(10);
        a.spawn(20, FEET, 20, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 48));
        a.run(new net.kasax.challengecraft.bot.task.SniperTask(), 3000, () -> {
            if (sk.isAlive()) return false;
            players.setViewDistance(view);
            players.setSimulationDistance(sim);
            return true;
        });
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

    /** A shield and a zombie: a hit blocked. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void shieldBlock(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "shield");
        a.spawn(20, FEET, 20, new ItemStack(Items.SHIELD));
        var z = EntityTypes.ZOMBIE.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        z.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(26, FEET, 20)));
        h.runAfterDelay(80, () -> {
            h.getLevel().addFreshEntity(z);
            z.setTarget(a.bot().body());
        });
        a.run(new net.kasax.challengecraft.bot.task.ShieldTask(), 600,
                () -> a.bot().body().getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DAMAGE_BLOCKED_BY_SHIELD)) > 0);
    }

    /** A skeleton about, full health: shot by it on purpose. */
    @GameTest(structure = STRUCTURE, maxTicks = 500, skyAccess = true, padding = 8)
    public void getShot(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "get_shot");
        a.spawn(20, FEET, 20);
        var sk = EntityTypes.SKELETON.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        sk.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(20, FEET, 30)));
        sk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        h.runAfterDelay(80, () -> {
            h.getLevel().addFreshEntity(sk);
            sk.setTarget(a.bot().body());
        });
        a.run(new net.kasax.challengecraft.bot.task.GetShotTask(), 500, () -> a.bot().current() == null && a.bot().body().getHealth() < 20);
    }

    /** A glass bottle by a pond: filled and drunk (a water bottle counts as a potion). */
    @GameTest(structure = STRUCTURE, maxTicks = 500, skyAccess = true, padding = 8)
    public void drinkWater(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "drink_water");
        a.fill(26, BotArena.GROUND - 1, 18, 29, BotArena.GROUND, 21, Blocks.WATER);
        a.spawn(20, FEET, 20, new ItemStack(Items.GLASS_BOTTLE));
        a.run(new net.kasax.challengecraft.bot.task.SequenceTask("drink", java.util.List.of(net.kasax.challengecraft.bot.task.FillBucketTask::bottle,
                        () -> new net.kasax.challengecraft.bot.task.ConsumeTask(java.util.Set.of(Items.POTION)))), 500,
                () -> a.bot().body().getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(Items.POTION)) > 0);
    }

    /** Shears and a bee nest full of honey: honeycomb. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void honeycomb(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "honeycomb");
        a.fill(28, FEET, 20, 28, FEET, 20, Blocks.BEE_NEST.defaultBlockState().setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5));
        a.spawn(16, FEET, 20, new ItemStack(Items.SHEARS));
        a.run(new net.kasax.challengecraft.bot.task.ClickBlockTask("shear a bee nest", st -> st.is(Blocks.BEE_NEST)
                        && st.getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL) >= 5).with(Items.SHEARS, java.util.Set.of(Items.HONEYCOMB)), 400,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.HONEYCOMB)) > 0);
    }

    /** Low on health with zombies close: up a three-block pillar, out of their reach, alive. */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void pillarFromZombies(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "pillar_up");
        a.spawn(20, FEET, 20, new ItemStack(Items.DIRT, 8));
        a.bot().body().setHealth(8f);
        boolean[] died = {false};
        h.onEachTick(() -> { if (!a.bot().body().isAlive()) died[0] = true; });
        h.runAfterDelay(30, () -> {
            for (int i = 0; i < 2; i++) {
                var z = EntityTypes.ZOMBIE.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                z.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(26 - 12 * i, FEET, 20)));
                h.getLevel().addFreshEntity(z);
                z.setTarget(a.bot().body());
            }
        });
        a.run(net.kasax.challengecraft.bot.task.HideTask.upward(), 400, () -> h.getTick() > 350 && !died[0] && a.feet().getY() >= FEET + 2);
    }

    /** A fletcher of the first level and a pile of sticks: levelled up by selling them, then a bow bought. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 72)
    public void buyBow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "buy_bow");
        var level = h.getLevel();
        a.fill(30, FEET, 20, 30, FEET, 20, Blocks.FLETCHING_TABLE);
        var v = EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        v.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(28, FEET, 20)));
        v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FLETCHER).withLevel(1));
        v.setVillagerXp(1); // (has traded before: keeps its job)
        level.addFreshEntity(v);
        // (Its first-level trades are drawn at random: the stick one, for certain.)
        v.getOffers().clear();
        v.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.STICK, 32),
                new ItemStack(Items.EMERALD), 16, 2, 0.05f));
        a.spawn(20, FEET, 20, new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64));
        var bowTrade = net.kasax.challengecraft.bot.plan.TradeKnowledge.get(level.getServer()).selling(Items.BOW).get(0);
        a.run(new net.kasax.challengecraft.bot.task.VillagerTradeTask(bowTrade, 1), 1200,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.BOW)) > 0);
    }

    /** Just "get a bow", with a fletcher about and sticks to sell: the planner buys it. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 72)
    public void planBuysBow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "plan_buys_bow");
        var level = h.getLevel();
        a.fill(30, FEET, 20, 30, FEET, 20, Blocks.FLETCHING_TABLE);
        var v = EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        v.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(28, FEET, 20)));
        v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FLETCHER).withLevel(1));
        v.setVillagerXp(1);
        level.addFreshEntity(v);
        v.getOffers().clear();
        v.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.STICK, 32),
                new ItemStack(Items.EMERALD), 16, 2, 0.05f));
        a.spawn(20, FEET, 20, new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64));
        h.runAfterDelay(60, () -> a.bot().doNow(new net.kasax.challengecraft.bot.task.ObtainTask(java.util.Set.of(Items.BOW), 1,
                new net.kasax.challengecraft.bot.plan.ObtainPlanner())));
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1), 1200,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.BOW)) > 0);
    }

    /** A jobless villager only: given a fletching table, it becomes a fletcher, and sells the bow. */
    @GameTest(structure = STRUCTURE, maxTicks = 3000, skyAccess = true, padding = 72)
    public void jobSiteBow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "job_site_bow");
        var level = h.getLevel();
        // (Daytime: at night villagers rest and take no job; Bob would wait for the morning.)
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "time set 1000");
        var v = EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        v.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(28, FEET, 20)));
        level.addFreshEntity(v);
        a.spawn(20, FEET, 20, new ItemStack(Items.FLETCHING_TABLE), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64),
                new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64));
        var bowTrade = net.kasax.challengecraft.bot.plan.TradeKnowledge.get(level.getServer()).selling(Items.BOW).get(0);
        a.run(new net.kasax.challengecraft.bot.task.VillagerTradeTask(bowTrade, 1), 3000,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.BOW)) > 0);
    }

    /** A free composter beside the only jobless villager: taken up (or the new farmer's taken away), so it becomes a fletcher. */
    @GameTest(structure = STRUCTURE, maxTicks = 6000, skyAccess = true, padding = 72)
    public void jobSiteRival(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "job_site_rival");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "time set 1000");
        a.fill(30, FEET, 21, 30, FEET, 21, Blocks.COMPOSTER);
        var v = EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        v.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(28, FEET, 20)));
        level.addFreshEntity(v);
        a.spawn(20, FEET, 20, new ItemStack(Items.FLETCHING_TABLE), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64),
                new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64), new ItemStack(Items.STICK, 64), new ItemStack(Items.WOODEN_AXE));
        var bowTrade = net.kasax.challengecraft.bot.plan.TradeKnowledge.get(level.getServer()).selling(Items.BOW).get(0);
        a.run(new net.kasax.challengecraft.bot.task.VillagerTradeTask(bowTrade, 1), 6000,
                () -> net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(a.bot().body(), java.util.Set.of(Items.BOW)) > 0);
    }

    /** A zombie and a spider wanted (two tiles): one round, both taken, one after the other. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void huntRound(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "hunt_round");
        var level = h.getLevel();
        var z = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        z.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(30, FEET, 20)));
        level.addFreshEntity(z);
        var sp = EntityTypes.SPIDER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        sp.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(10, FEET, 30)));
        level.addFreshEntity(sp);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, 8));
        a.run(new net.kasax.challengecraft.bot.task.HuntRoundTask(() -> {
            java.util.Set<net.minecraft.world.entity.EntityType<?>> open = new java.util.HashSet<>();
            if (z.isAlive()) open.add(EntityTypes.ZOMBIE);
            if (sp.isAlive()) open.add(EntityTypes.SPIDER);
            return open;
        }, () -> true), 1200, () -> !z.isAlive() && !sp.isAlive());
    }

    /** A strider out on a lava pool, out of reach of a sword: shot with the bow. */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void striderOnLava(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "strider_on_lava");
        var level = h.getLevel();
        a.fill(24, BotArena.GROUND - 1, 14, 34, BotArena.GROUND, 26, net.minecraft.world.level.block.Blocks.LAVA);
        var st = EntityTypes.STRIDER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        st.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(30, BotArena.GROUND, 20)));
        level.addFreshEntity(st);
        a.spawn(16, FEET, 20, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 32), new ItemStack(Items.STONE_SWORD));
        a.run(new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(EntityTypes.STRIDER), java.util.Set.of(), 0, 1), 1200,
                () -> !st.isAlive());
    }

    // ---- brawls on hard: how much it gets hurt (logged; the limits say what is good enough) ----

    /** Mobs of these kinds at these spots, the bot with a stone sword: all killed, losing at most {@code maxLoss} health. */
    private static void brawl(GameTestHelper h, String name, float maxLoss, Object... kindsAndSpots) {
        BotArena a = BotArena.flat(h, name);
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        java.util.List<net.minecraft.world.entity.Mob> mobs = new java.util.ArrayList<>();
        java.util.Set<net.minecraft.world.entity.EntityType<?>> kinds = new java.util.HashSet<>();
        for (int i = 0; i < kindsAndSpots.length; i += 2) {
            var type = (net.minecraft.world.entity.EntityType<?>) kindsAndSpots[i];
            int[] p = (int[]) kindsAndSpots[i + 1];
            var m = (net.minecraft.world.entity.Mob) type.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (m instanceof net.minecraft.world.entity.monster.zombie.Zombie z && p.length > 2) z.setBaby(true);
            if (m instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton) m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            // (No sun-burning: a helmet on the undead.)
            m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            m.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(p[0], FEET, p[1])));
            m.setPersistenceRequired();
            level.addFreshEntity(m);
            mobs.add(m);
            kinds.add(type);
        }
        a.spawn(16, FEET, 20, new ItemStack(Items.STONE_SWORD));
        var body = a.bot().body();
        float[] low = {body.getHealth()};
        a.run(new net.kasax.challengecraft.bot.task.KillTask(kinds, java.util.Set.of(), 0, mobs.size()), 1500, () -> {
            low[0] = Math.min(low[0], body.getHealth());
            if (20 - low[0] > maxLoss) h.fail(name + ": lost " + (20 - low[0]) + " health");
            boolean done = mobs.stream().noneMatch(net.minecraft.world.entity.LivingEntity::isAlive);
            if (done) BotArena.LOG.info("[BOTTEST] {} lost {} health", name, 20 - low[0]);
            return done;
        });
    }

    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void brawlZombie(GameTestHelper h) {
        brawl(h, "brawl_zombie", 2, EntityTypes.ZOMBIE, new int[]{26, 20});
    }

    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void brawlBabyZombie(GameTestHelper h) {
        brawl(h, "brawl_baby_zombie", 3, EntityTypes.ZOMBIE, new int[]{26, 20, 1});
    }

    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void brawlSpider(GameTestHelper h) {
        brawl(h, "brawl_spider", 3, EntityTypes.SPIDER, new int[]{26, 20});
    }

    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void brawlZombieTrio(GameTestHelper h) {
        brawl(h, "brawl_zombie_trio", 8, EntityTypes.ZOMBIE, new int[]{26, 18}, EntityTypes.ZOMBIE, new int[]{26, 22}, EntityTypes.ZOMBIE, new int[]{24, 26});
    }

    /** A piglin brute (golden axe, hard) on an iron sword: fought from a pillar, little lost. */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void brawlBrute(GameTestHelper h) {
        brawlFromPillar(h, "brawl_brute", EntityTypes.PIGLIN_BRUTE);
    }

    /** A hoglin (hard) on an iron sword: from a pillar too, its tusks do not reach up there. */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void brawlHoglin(GameTestHelper h) {
        brawlFromPillar(h, "brawl_hoglin", EntityTypes.HOGLIN);
    }

    /** An enderman provoked (stared at): from a pillar as well, where its arms do not reach. */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 24)
    public void brawlEnderman(GameTestHelper h) {
        brawlFromPillar(h, "brawl_enderman", EntityTypes.ENDERMAN);
    }

    private static void brawlFromPillar(GameTestHelper h, String name, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type) {
        BotArena a = BotArena.flat(h, name);
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        var brute = type.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (brute instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin p) p.setImmuneToZombification(true);
        if (brute instanceof net.minecraft.world.entity.monster.hoglin.Hoglin hg) hg.setImmuneToZombification(true);
        if (brute instanceof net.minecraft.world.entity.monster.piglin.PiglinBrute) brute.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
        brute.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(26, FEET, 20)));
        brute.setPersistenceRequired();
        level.addFreshEntity(brute);
        a.spawn(16, FEET, 20, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.COBBLESTONE, 16));
        var body = a.bot().body();
        // (An enderman only comes for one that has stared at it.)
        if (brute instanceof net.minecraft.world.entity.monster.Enderman) h.runAfterDelay(5, () -> brute.setTarget(body));
        float[] low = {body.getHealth()};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1500), 1500, () -> {
            if (body.getHealth() < low[0]) BotArena.LOG.info("[BOTTEST] " + name + " hit: hp {} bot {} y {} brute {} y {} by {}", body.getHealth(),
                    body.blockPosition().toShortString(), body.getY(), brute.blockPosition().toShortString(), brute.getY(),
                    body.getLastDamageSource() == null ? "-" : body.getLastDamageSource().getMsgId());
            low[0] = Math.min(low[0], body.getHealth());
            if (20 - low[0] > 7) h.fail(name + ": lost " + (20 - low[0]) + " health");
            // (An enderman that cannot get at it teleports away: safe is enough.)
            if (brute.isAlive() && !(brute instanceof net.minecraft.world.entity.monster.Enderman && h.getTick() > 1200)) return false;
            BotArena.LOG.info("[BOTTEST] {} lost {} health", name, 20 - low[0]);
            return true;
        });
    }

    /** A big magma cube coming for it while it stands about (iron on, an iron sword): it fights back and lives. */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 24)
    public void brawlMagmaCube(GameTestHelper h) {
        magmaBrawl(h, "brawl_magma_cube", 1);
    }

    /** Two big magma cubes at once (a basalt delta's welcome): it lives. */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 24)
    public void brawlMagmaPair(GameTestHelper h) {
        magmaBrawl(h, "brawl_magma_pair", 2);
    }

    private static void magmaBrawl(GameTestHelper h, String name, int cubes) {
        BotArena a = BotArena.flat(h, name);
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        for (int i = 0; i < cubes; i++) {
            var cube = EntityTypes.MAGMA_CUBE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            cube.setSize(4, true);
            cube.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(26, FEET, 18 + 4 * i)));
            cube.setPersistenceRequired();
            level.addFreshEntity(cube);
        }
        a.spawn(16, FEET, 20, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 16));
        var body = a.bot().body();
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        float[] low = {20};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1150), 1150, () -> {
            if (body.getHealth() < low[0]) BotArena.LOG.info("[BOTTEST] " + name + " hit: hp {} doing {}", body.getHealth(), a.bot().status());
            low[0] = Math.min(low[0], body.getHealth());
            h.assertTrue(body.isAlive(), name + ": dead");
            var left = level.getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.MagmaCube.class, body.getBoundingBox().inflate(30), net.minecraft.world.entity.LivingEntity::isAlive);
            // (All dead, or still alive at the end with what is left not after it any more.)
            if (!left.isEmpty() && h.getTick() < 1100) return false;
            h.assertTrue(20 - low[0] < 14, name + ": lost " + (20 - low[0]));
            BotArena.LOG.info("[BOTTEST] {} lost {} health", name, 20 - low[0]);
            return true;
        });
    }

    /** A fight with a zombie on a ledge over a deep drop: won without going over the edge. */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 24)
    public void brawlOnLedge(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "brawl_on_ledge");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        // A pit all round a 4 x 4 ledge, eight deep.
        a.fill(10, BotArena.GROUND - 8, 10, 30, BotArena.GROUND, 30, Blocks.AIR);
        a.fill(18, BotArena.GROUND - 8, 18, 21, BotArena.GROUND, 21, Blocks.STONE);
        var z = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        z.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(21, FEET, 21)));
        z.setPersistenceRequired();
        level.addFreshEntity(z);
        a.spawn(18, FEET, 18, new ItemStack(Items.IRON_SWORD));
        var body = a.bot().body();
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1150), 1150, () -> {
            // (Dug down after the zombie it knocked off, maybe: no matter. Fallen is what counts.)
            h.assertTrue(body.fallDistance < 4, "brawl_on_ledge: fell off");
            return !z.isAlive() || h.getTick() > 1100;
        });
    }

    /** A zombie coming at him with a lava pit at his back: the fight turned so no blow throws him in. */
    @GameTest(environment = "challengecraft:brawl", structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 24)
    public void brawlByLava(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "brawl_by_lava");
        var level = h.getLevel();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), "difficulty hard");
        a.fill(10, BotArena.GROUND, 10, 16, BotArena.GROUND, 30, Blocks.LAVA);
        var z = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        z.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(a.abs(24, FEET, 20)));
        z.setPersistenceRequired();
        level.addFreshEntity(z);
        a.spawn(18, FEET, 20, new ItemStack(Items.IRON_SWORD));
        var body = a.bot().body();
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(1150), 1150, () -> {
            h.assertTrue(!body.isInLava() && body.isAlive(), "brawl_by_lava: into the lava");
            return !z.isAlive() || h.getTick() > 1100;
        });
    }

    /** Killed once and back: as hurtable as before (not shielded for good like a player still loading). */
    @GameTest(structure = STRUCTURE, maxTicks = 700, skyAccess = true, padding = 8)
    public void hurtableAfterDeath(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "hurtable_after_death");
        var level = h.getLevel();
        a.spawn(20, FEET, 20);
        var first = a.bot().body();
        boolean[] killed = {false}, hit = {false};
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(650), 700, () -> {
            // (Once in for good, as a player that has played a while.)
            if (!killed[0]) {
                if (!first.connection.hasClientLoaded()) return false;
                killed[0] = true;
                first.kill(level);
                return false;
            }
            var body = a.bot().body();
            if (body == first || !body.isAlive()) return false;
            // (Three seconds after coming back, as the game allows a player to load the world.)
            if (!hit[0] && body.tickCount > 80) {
                hit[0] = true;
                body.hurtServer((net.minecraft.server.level.ServerLevel) body.level(), body.level().damageSources().generic(), 4f);
            }
            return hit[0] && body.getHealth() < body.getMaxHealth();
        });
    }

    /** On fire with a water bucket: water poured at its feet, the fire out, the water back in the bucket. */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void extinguishBucket(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "extinguish_bucket");
        a.spawn(20, FEET, 20, new ItemStack(Items.WATER_BUCKET));
        var body = a.bot().body();
        body.igniteForSeconds(8);
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(250), 300, () ->
                !body.isOnFire() && net.kasax.challengecraft.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(Items.WATER_BUCKET)) > 0
                        && body.getHealth() >= 18);
    }

    /** On fire, no bucket, a pond a few steps off: into the water. */
    @GameTest(structure = STRUCTURE, maxTicks = 300, skyAccess = true, padding = 8)
    public void extinguishPond(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "extinguish_pond");
        a.fill(24, BotArena.GROUND, 19, 25, BotArena.GROUND, 21, Blocks.WATER);
        a.spawn(20, FEET, 20);
        var body = a.bot().body();
        body.igniteForSeconds(8);
        a.run(new net.kasax.challengecraft.bot.task.WaitTask(250), 300, () -> !body.isOnFire() && body.getHealth() >= 16);
    }
}
