package net.kasax.challengecraft.bot.learn;

import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotPlayer;
import net.kasax.challengecraft.bot.Guardian;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The situation a cover is chosen in, as numbers for the learnt model and the journal: Bob's
 * hearts and gear, the place, what is after him (how many of which kind, how near, how hard they
 * hit), the guardian's reckoning, and which ways out the ground allows. Each a number of about
 * nought to one. The names and their order are the model's inputs: only added to at the end, and
 * the trainer reads them from the journal (a model trained on another list is not loaded).
 */
public final class Situation {
    public static final String[] NAMES = {
            "hp", "absorption", "armour", "toughness", "shield", "blocking", "weapon_dps", "armed",
            "food_level", "food_packed", "blocks", "night", "nether", "end", "in_water", "on_ground",
            "sky", "light", "hard", "threats", "melee", "ranged", "reachless", "spiders",
            "endermen", "creepers", "cubes", "pillagers", "skeletons", "fireballs", "nearest", "mean_dist",
            "ticks_left", "fight_ticks", "box_possible", "box_taken", "pillar_possible", "dig_ticks", "room_to_run", "threat_dps",
            "threat_above", "threat_health",
            "creeper_swelling", "creeper_nearest", "witches", "drowned", "babies", "phantoms", "on_fire", "lava_near",
            "y_level", "air", "falling", "day_time"};

    private Situation() {
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1.5, v));
    }

    /** The numbers for this moment (see {@link #NAMES}). */
    public static double[] cover(Bot bot, Guardian.Reckoning r, boolean boxPossible, boolean boxTaken, boolean pillarPossible,
                                 int digTicks, boolean roomToRun) {
        BotPlayer body = bot.body();
        Level level = body.level();
        double[] x = new double[NAMES.length];
        x[0] = body.getHealth() / 20;
        x[1] = body.getAbsorptionAmount() / 20;
        x[2] = body.getArmorValue() / 20.0;
        x[3] = body.getAttributeValue(Attributes.ARMOR_TOUGHNESS) / 10;
        x[4] = body.getOffhandItem().is(Items.SHIELD) || body.getMainHandItem().is(Items.SHIELD)
                || body.getInventory().getNonEquipmentItems().stream().anyMatch(s -> s.is(Items.SHIELD)) ? 1 : 0;
        x[5] = body.isBlocking() ? 1 : 0;
        double dps = 0;
        boolean armed = false;
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (!st.is(net.minecraft.tags.ItemTags.SWORDS) && !st.is(net.minecraft.tags.ItemTags.AXES)) continue;
            armed = true;
            dps = Math.max(dps, net.kasax.challengecraft.bot.task.KillTask.dps(st));
        }
        x[6] = clamp(dps / 10);
        x[7] = armed ? 1 : 0;
        x[8] = body.getFoodData().getFoodLevel() / 20.0;
        x[9] = clamp(net.kasax.challengecraft.bot.task.EatTask.foodPoints(body) / 40.0);
        x[10] = clamp(net.kasax.challengecraft.bot.BotActions.buildingBlocks(body) / 64.0);
        x[11] = level.dimension() == Level.OVERWORLD && level.isDarkOutside() ? 1 : 0;
        x[12] = level.dimension() == Level.NETHER ? 1 : 0;
        x[13] = level.dimension() == Level.END ? 1 : 0;
        x[14] = body.isInWater() ? 1 : 0;
        x[15] = body.onGround() ? 1 : 0;
        x[16] = level.canSeeSky(body.blockPosition().above()) ? 1 : 0;
        x[17] = level.getMaxLocalRawBrightness(body.blockPosition()) / 15.0;
        x[18] = level.getDifficulty() == Difficulty.HARD ? 1 : 0;
        int melee = 0, ranged = 0, reachless = 0, spiders = 0, endermen = 0, cubes = 0, pillagers = 0, skeletons = 0, fireballs = 0;
        double nearest = 24, sum = 0, dpsIn = 0, above = -8, health = 0;
        for (Guardian.Threat t : r.threats()) {
            Mob m = t.mob();
            if (t.ranged()) ranged++;
            else melee++;
            if (net.kasax.challengecraft.bot.task.PillarFightTask.reachless(m)) reachless++;
            if (m instanceof net.minecraft.world.entity.monster.spider.Spider) spiders++;
            if (m instanceof Enderman) endermen++;
            if (m instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob) cubes++;
            if (m instanceof net.minecraft.world.entity.monster.illager.AbstractIllager) pillagers++;
            if (m instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton) skeletons++;
            if (m instanceof net.minecraft.world.entity.monster.Ghast || m instanceof net.minecraft.world.entity.monster.Blaze) fireballs++;
            double d = m.distanceTo(body);
            nearest = Math.min(nearest, d);
            sum += d;
            dpsIn += t.perHit() * 20.0 / Math.max(1, t.every());
            above = Math.max(above, m.getY() - body.getY());
            health += m.getHealth();
        }
        int creepers = level.getEntitiesOfClass(Creeper.class, body.getBoundingBox().inflate(16), Creeper::isAlive).size();
        int n = r.threats().size();
        x[19] = clamp(n / 8.0);
        x[20] = clamp(melee / 8.0);
        x[21] = clamp(ranged / 8.0);
        x[22] = clamp(reachless / 8.0);
        x[23] = clamp(spiders / 4.0);
        x[24] = clamp(endermen / 4.0);
        x[25] = clamp(creepers / 4.0);
        x[26] = clamp(cubes / 4.0);
        x[27] = clamp(pillagers / 4.0);
        x[28] = clamp(skeletons / 4.0);
        x[29] = clamp(fireballs / 4.0);
        x[30] = Math.min(24, nearest) / 24;
        x[31] = n == 0 ? 1 : Math.min(24, sum / n) / 24;
        x[32] = r.ticksLeft() == Integer.MAX_VALUE ? 1 : Math.min(1, r.ticksLeft() / 200.0);
        x[33] = r.fightTicks() == Integer.MAX_VALUE ? 1.5 : clamp(r.fightTicks() / 400.0);
        x[34] = boxPossible ? 1 : 0;
        x[35] = boxTaken ? 1 : 0;
        x[36] = pillarPossible ? 1 : 0;
        x[37] = digTicks == Integer.MAX_VALUE ? 1.5 : clamp(digTicks / 200.0);
        x[38] = roomToRun ? 1 : 0;
        x[39] = clamp(dpsIn / 10);
        x[40] = clamp(above / 8);
        x[41] = clamp(health / 100);
        // (Added later: older journal lines have these as nought.)
        int swelling = 0, witches = 0, drowned = 0, babies = 0, phantoms = 0;
        double creeperNearest = 16;
        for (Creeper c : level.getEntitiesOfClass(Creeper.class, body.getBoundingBox().inflate(16), Creeper::isAlive)) {
            if (c.getSwellDir() > 0) swelling++;
            creeperNearest = Math.min(creeperNearest, c.distanceTo(body));
        }
        for (Guardian.Threat t : r.threats()) {
            Mob m = t.mob();
            if (m.getType() == EntityTypes.WITCH) witches++;
            if (m.getType() == EntityTypes.DROWNED) drowned++;
            if (m.getType() == EntityTypes.PHANTOM) phantoms++;
            if (m.isBaby()) babies++;
        }
        boolean lava = false;
        BlockPos at = body.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 1, 3))) {
            if (level.getFluidState(p).is(FluidTags.LAVA)) {
                lava = true;
                break;
            }
        }
        x[42] = clamp(swelling / 2.0);
        x[43] = creepers == 0 ? 1 : Math.min(16, creeperNearest) / 16;
        x[44] = clamp(witches / 4.0);
        x[45] = clamp(drowned / 4.0);
        x[46] = clamp(babies / 4.0);
        x[47] = clamp(phantoms / 4.0);
        x[48] = body.isOnFire() ? 1 : 0;
        x[49] = lava ? 1 : 0;
        x[50] = clamp((body.getY() - 64) / 128);
        x[51] = body.getAirSupply() / (double) Math.max(1, body.getMaxAirSupply());
        x[52] = clamp(body.fallDistance / 10);
        x[53] = (level.getOverworldClockTime() % 24000) / 24000.0;
        return x;
    }
}
