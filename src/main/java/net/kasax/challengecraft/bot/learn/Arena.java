package net.kasax.challengecraft.bot.learn;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kasax.challengecraft.bot.Bot;
import net.kasax.challengecraft.bot.BotManager;
import net.kasax.challengecraft.bot.BotPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The arena: fights set up at random in the real world, many at once on one server, for the
 * trainer. Each of {@code n} bots has a stretch of the world of its own (far from the others);
 * each fight takes it to a spot there - out in the open, in a forest, by the water, down in a
 * cave - with random gear (nothing to a diamond sword, no armour to iron, a shield or none, blocks
 * or none, food or none) and random hearts, and puts random monsters round it, all after it: a
 * zombie, three skeletons and a creeper, a spider at night, pillagers, an angry enderman.
 *
 * <p>What the guardian does then is what is learnt: in the {@code explore} mode it picks its way
 * out at random among those possible there (so every way is seen in every kind of fight, not only
 * where the rules would take it - the data the model needs), in {@code eval} each fight is played
 * twice, once by the rules and once by a candidate model, from the same start (the trainer's test
 * before a new model is used), in {@code rule} or {@code model} as Bob would play.
 *
 * <p>Every fight is a line in the journal ({@code arena}: the setting, the policy, how it ended),
 * and the guardian's own record of the way it chose ({@code cover}). The server runs as fast as it
 * can ({@code sprint}) - the bots' searches count game ticks then (CHALLENGECRAFT_BOT_SIM=1).
 */
public final class Arena {
    public enum Mode { EXPLORE, EVAL, RULE, MODEL }

    private static final int SPACING = 1024, ROAM = 40, MAX_FIGHT = 1200, SETTLE = 20;
    private static Arena running;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Mode mode;
    private final String candidate;
    private final boolean sprint;
    private final Random random;
    private final List<Slot> slots = new ArrayList<>();
    private final long startedWall = System.currentTimeMillis();
    private final long startTick;
    private int fights, decided, deaths, pairs;
    private final int[] taken = new int[CoverOption.ALL.length], diedAfter = new int[CoverOption.ALL.length];
    /** Eval: per policy - fights, deaths, hearts lost, seconds. */
    private final double[][] eval = new double[2][4];

    private Arena(MinecraftServer server, Mode mode, String candidate, long seed, boolean sprint) {
        this.server = server;
        this.level = server.overworld();
        this.mode = mode;
        this.candidate = candidate;
        this.sprint = sprint;
        this.random = new Random(seed);
        this.startTick = server.getTickCount();
    }

    // ---- control --------------------------------------------------------------------------------

    /** Starts the arena with {@code n} bots; what it says back for the command. */
    public static String start(MinecraftServer server, int n, Mode mode, String candidate, long seed, boolean sprint) {
        if (running != null) stop(server);
        Arena a = new Arena(server, mode, candidate, seed, sprint);
        var rules = a.level.getGameRules();
        // Only the monsters a fight puts there; no patrols, phantoms or traders wandering in.
        rules.set(GameRules.SPAWN_MONSTERS, false, server);
        rules.set(GameRules.SPAWN_PATROLS, false, server);
        rules.set(GameRules.SPAWN_PHANTOMS, false, server);
        rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
        rules.set(GameRules.SHOW_DEATH_MESSAGES, false, server);
        server.setDifficulty(Difficulty.HARD, true);
        BlockPos spawn = a.level.getRespawnData().pos();
        for (int i = 0; i < n; i++) {
            // (Each one far from the others: a fight's monsters never wander into the next.)
            BlockPos anchor = new BlockPos(spawn.getX() + (i % 4) * SPACING + SPACING, 64, spawn.getZ() + (i / 4) * SPACING + SPACING);
            Bot bot = BotManager.spawn(server, "Arena" + (i + 1), a.level, net.minecraft.world.phys.Vec3.atBottomCenterOf(a.surface(anchor)));
            bot.arena = true;
            bot.setBrain(null);
            // (No world sense in the arena: the far structures are no help in a fight, and their
            // search costs the server time a fight needs.)
            bot.params = net.kasax.challengecraft.bot.BotParams.parse("worldsense=0");
            a.slots.add(a.new Slot(i, bot, anchor));
        }
        running = a;
        BotManager.LOG.info("[ARENA] start: {} bots, {}{}, seed {}, sprint {}", n, mode, candidate == null ? "" : " " + candidate, seed, sprint);
        return "arena: " + n + " bots, " + mode.name().toLowerCase(java.util.Locale.ROOT) + (candidate == null ? "" : " (" + candidate + ")");
    }

    public static void stop(MinecraftServer server) {
        Arena a = running;
        if (a == null) return;
        running = null;
        for (Slot s : a.slots) {
            s.clearMobs();
            s.bot.cover.reset();
            s.bot.guardian.context = null;
            s.bot.guardian.onEpisode = null;
            BotManager.remove(server, s.bot);
        }
        if (a.sprint) server.tickRateManager().stopSprinting();
        BotManager.LOG.info("[ARENA] stopped: {}", a.summary());
    }

    public static void tick(MinecraftServer server) {
        Arena a = running;
        if (a == null || a.server != server) return;
        if (a.sprint && server.getTickCount() % 100 == 0 && !server.tickRateManager().isSprinting()) server.tickRateManager().requestGameToSprint(20 * 3600 * 24);
        for (Slot s : a.slots) s.tick();
        if ((server.getTickCount() - a.startTick) % 6000 == 0) BotManager.LOG.info("[ARENA] stats {}", a.summary());
    }

    /** The counts so far (for the status file and the command). */
    public static JsonObject stats() {
        Arena a = running;
        if (a == null) {
            JsonObject o = new JsonObject();
            o.addProperty("running", false);
            return o;
        }
        return a.summary();
    }

    private JsonObject summary() {
        JsonObject o = new JsonObject();
        o.addProperty("running", running == this);
        o.addProperty("mode", mode.name().toLowerCase(java.util.Locale.ROOT));
        if (candidate != null) o.addProperty("candidate", candidate);
        o.addProperty("slots", slots.size());
        o.addProperty("fights", fights);
        o.addProperty("decided", decided);
        o.addProperty("deaths", deaths);
        double minutes = (System.currentTimeMillis() - startedWall) / 60000.0;
        o.addProperty("minutes", Math.round(minutes * 10) / 10.0);
        o.addProperty("fights_per_min", minutes > 0 ? Math.round(fights / minutes * 10) / 10.0 : 0);
        o.addProperty("game_speed", minutes > 0 ? Math.round((server.getTickCount() - startTick) / (minutes * 60 * 20) * 100) / 100.0 : 0);
        JsonObject opts = new JsonObject();
        for (CoverOption c : CoverOption.ALL) {
            if (taken[c.ordinal()] == 0) continue;
            JsonArray v = new JsonArray();
            v.add(taken[c.ordinal()]);
            v.add(diedAfter[c.ordinal()]);
            opts.add(c.name(), v);
        }
        o.add("options", opts);
        if (mode == Mode.EVAL) {
            JsonObject e = new JsonObject();
            String[] names = {"rule", "model"};
            for (int p = 0; p < 2; p++) {
                JsonObject q = new JsonObject();
                q.addProperty("fights", (int) eval[p][0]);
                q.addProperty("deaths", (int) eval[p][1]);
                q.addProperty("hp_lost", Math.round(eval[p][2] * 10) / 10.0);
                q.addProperty("secs", Math.round(eval[p][3]));
                e.add(names[p], q);
            }
            e.addProperty("pairs", pairs);
            o.add("eval", e);
        }
        return o;
    }

    // ---- places ----------------------------------------------------------------------------------

    /** The ground at this column (generated if need be): where one stands. */
    private BlockPos surface(BlockPos at) {
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        return new BlockPos(at.getX(), y, at.getZ());
    }

    private boolean standable(BlockPos feet, int height) {
        if (!level.isLoaded(feet)) return false;
        var below = feet.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        if (!level.getFluidState(below).isEmpty() && !level.getFluidState(below).is(net.minecraft.tags.FluidTags.WATER)) return false;
        for (int i = 0; i < height; i++) {
            BlockPos p = feet.above(i);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) return false;
        }
        return true;
    }

    /** A spot for a fight near {@code anchor}: on the ground, or (cave) in a hollow under it; null if none here. */
    private BlockPos spot(Random r, BlockPos anchor, boolean cave) {
        for (int tries = 0; tries < 24; tries++) {
            int x = anchor.getX() + r.nextInt(-ROAM, ROAM + 1), z = anchor.getZ() + r.nextInt(-ROAM, ROAM + 1);
            if (!level.isLoaded(new BlockPos(x, 64, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (cave) {
                for (int y = top - 8; y > level.getMinY() + 6; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (standable(p, 2) && !level.canSeeSky(p)) return p;
                }
                continue;
            }
            BlockPos p = new BlockPos(x, top, z);
            if (standable(p, 2)) return p;
        }
        return null;
    }

    /** A spot {@code min..max} blocks from {@code from} for a mob {@code height} tall, about level with it. */
    private BlockPos near(Random r, BlockPos from, int min, int max, int height) {
        for (int tries = 0; tries < 16; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = from.getX() + (int) Math.round(Math.cos(a) * d), z = from.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos p = new BlockPos(x, from.getY() + dy, z);
                if (standable(p, height)) return p;
            }
        }
        return null;
    }

    // ---- what a fight is made of ----------------------------------------------------------------

    private record Kind(EntityType<? extends Mob> type, int weight, boolean caveOnly) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind(EntityTypes.ZOMBIE, 24, false), new Kind(EntityTypes.HUSK, 4, false), new Kind(EntityTypes.DROWNED, 4, false),
            new Kind(EntityTypes.ZOMBIE_VILLAGER, 3, false), new Kind(EntityTypes.SKELETON, 16, false), new Kind(EntityTypes.STRAY, 3, false),
            new Kind(EntityTypes.SPIDER, 10, false), new Kind(EntityTypes.CAVE_SPIDER, 4, true), new Kind(EntityTypes.CREEPER, 10, false),
            new Kind(EntityTypes.ENDERMAN, 6, false), new Kind(EntityTypes.PILLAGER, 5, false), new Kind(EntityTypes.VINDICATOR, 3, false),
            new Kind(EntityTypes.WITCH, 3, false), new Kind(EntityTypes.SLIME, 3, false));

    private static EntityType<? extends Mob> pick(Random r, boolean cave) {
        int total = 0;
        for (Kind k : KINDS) if (cave || !k.caveOnly()) total += k.weight();
        int roll = r.nextInt(total);
        for (Kind k : KINDS) {
            if (!cave && k.caveOnly()) continue;
            roll -= k.weight();
            if (roll < 0) return k.type();
        }
        return EntityTypes.ZOMBIE;
    }

    private static <T> T oneOf(Random r, double[] weights, T[] values) {
        double total = 0;
        for (double w : weights) total += w;
        double roll = r.nextDouble() * total;
        for (int i = 0; i < values.length; i++) {
            roll -= weights[i];
            if (roll < 0) return values[i];
        }
        return values[values.length - 1];
    }

    private static final Item[][] ARMOUR = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}};
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Gear, hearts and hunger for a fight - and what they were, for the journal. */
    private static JsonObject outfit(Random r, BotPlayer body) {
        JsonObject o = new JsonObject();
        body.getInventory().clearContent();
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (s.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || s == EquipmentSlot.OFFHAND) body.setItemSlot(s, ItemStack.EMPTY);
        }
        Item weapon = oneOf(r, new double[]{15, 10, 30, 30, 10, 5},
                new Item[]{Items.AIR, Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.IRON_AXE});
        if (weapon != Items.AIR) body.getInventory().add(new ItemStack(weapon));
        o.addProperty("weapon", weapon == Items.AIR ? "none" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(weapon).getPath());
        int tier = oneOf(r, new double[]{30, 15, 5, 15, 30, 5}, new Integer[]{-1, 0, 1, 2, 3, 4});
        int pieces = 0;
        if (tier >= 0) {
            for (int i = 0; i < 4; i++) {
                if (r.nextDouble() < 0.7) {
                    body.setItemSlot(ARMOUR_SLOTS[i], new ItemStack(ARMOUR[tier][i]));
                    pieces++;
                }
            }
        }
        o.addProperty("armour_tier", tier);
        o.addProperty("armour_pieces", pieces);
        boolean shield = r.nextDouble() < 0.5;
        if (shield) body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        o.addProperty("shield", shield);
        int blocks = oneOf(r, new double[]{15, 35, 50}, new Integer[]{0, 16, 64});
        if (blocks > 0) body.getInventory().add(new ItemStack(r.nextBoolean() ? Items.COBBLESTONE : Items.DIRT, blocks));
        o.addProperty("blocks", blocks);
        Item pick = oneOf(r, new double[]{10, 70, 20}, new Item[]{Items.AIR, Items.STONE_PICKAXE, Items.IRON_PICKAXE});
        if (pick != Items.AIR) body.getInventory().add(new ItemStack(pick));
        if (r.nextDouble() < 0.3) body.getInventory().add(new ItemStack(Items.STONE_SHOVEL));
        int food = r.nextDouble() < 0.3 ? 0 : 4 + r.nextInt(13);
        if (food > 0) body.getInventory().add(new ItemStack(r.nextBoolean() ? Items.BREAD : Items.COOKED_BEEF, food));
        o.addProperty("food", food);
        boolean bow = r.nextDouble() < 0.2;
        if (bow) {
            body.getInventory().add(new ItemStack(Items.BOW));
            body.getInventory().add(new ItemStack(Items.ARROW, 16 + r.nextInt(33)));
        }
        o.addProperty("bow", bow);
        // (Hearts: a good share low - the fights that matter are the ones begun hurt.)
        float hp = r.nextDouble() < 0.4 ? 4 + r.nextInt(7) : 10 + r.nextInt(11);
        body.removeAllEffects();
        body.setRemainingFireTicks(0);
        body.resetFallDistance();
        body.setHealth(hp);
        int hunger = 6 + r.nextInt(15);
        body.getFoodData().setFoodLevel(hunger);
        body.getFoodData().setSaturation(Math.min(hunger, 5));
        o.addProperty("hp", hp);
        o.addProperty("hunger", hunger);
        return o;
    }

    // ---- one bot's fights -------------------------------------------------------------------------

    private final class Slot {
        final int index;
        final Bot bot;
        BlockPos anchor;
        int state, ticks, calm, misses;
        final List<Mob> mobs = new ArrayList<>();
        JsonObject scenario, cover;
        float hp0, minHp;
        String policy = "-";
        long pairSeed;
        int pairStep;
        boolean ruleFirst;

        Slot(int index, Bot bot, BlockPos anchor) {
            this.index = index;
            this.bot = bot;
            this.anchor = anchor;
            bot.guardian.onEpisode = o -> {
                cover = o;
                decided++;
            };
        }

        void clearMobs() {
            for (Mob m : mobs) if (m.isAlive()) m.discard();
            mobs.clear();
        }

        void tick() {
            BotPlayer body = bot.body();
            switch (state) {
                case 0 -> setUp(body);
                case 1 -> {
                    // (A moment to land and look round before the monsters come.)
                    if (++ticks >= SETTLE) {
                        if (spawnMobs(body)) {
                            state = 2;
                            ticks = 0;
                            bot.doNow(net.kasax.challengecraft.bot.task.WaitTask.pause(MAX_FIGHT + 200));
                        } else {
                            state = 0;
                        }
                    }
                }
                case 2 -> fight(body);
                case 3 -> {
                    // Dead: back once the new body is there (the arena's bots do not wait for dawn).
                    if (bot.body().isAlive() && ++ticks > 5) state = 0;
                }
                default -> state = 0;
            }
        }

        private Random scenarioRandom() {
            if (mode != Mode.EVAL) return new Random(random.nextLong());
            // Eval: each start played twice, by the rules and by the candidate (in turn first).
            if (pairStep == 0) {
                pairSeed = random.nextLong();
                ruleFirst = random.nextBoolean();
            }
            policy = (pairStep == 0) == ruleFirst ? "rule" : "model";
            return new Random(pairSeed);
        }

        private void setUp(BotPlayer body) {
            if (!body.isAlive()) return;
            Random r = scenarioRandom();
            boolean cave = r.nextDouble() < 0.25;
            BlockPos at = spot(r, anchor, cave);
            if (at == null && cave) at = spot(r, anchor, false);
            if (at == null) {
                // Nowhere to stand here (the sea, a lake): on to another stretch of land.
                if (++misses > 3) {
                    anchor = anchor.offset(r.nextBoolean() ? 160 : -160, 0, r.nextBoolean() ? 160 : -160);
                    bot.body().teleportTo(level, anchor.getX() + 0.5, surface(anchor).getY(), anchor.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
                    misses = 0;
                }
                return;
            }
            misses = 0;
            bot.clearTasks();
            bot.navigator().stop();
            bot.actions().reset();
            body.stopInputs();
            body.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), r.nextFloat() * 360 - 180, 0, true);
            scenario = new JsonObject();
            scenario.addProperty("arena", true);
            scenario.addProperty("slot", index);
            scenario.addProperty("cave", cave);
            scenario.addProperty("night", level.isDarkOutside());
            scenario.addProperty("biome", level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
            scenario.add("outfit", outfit(r, body));
            scenario.addProperty("mob_seed", r.nextLong());
            switch (mode) {
                case EXPLORE -> {
                    bot.cover.set(CoverPolicy.Mode.EXPLORE, 1, null);
                    policy = "explore";
                }
                case RULE -> {
                    bot.cover.set(CoverPolicy.Mode.RULE, 0, null);
                    policy = "rule";
                }
                case MODEL -> {
                    bot.cover.set(CoverPolicy.Mode.MODEL, 0, candidate);
                    policy = "model";
                }
                case EVAL -> {
                    bot.cover.set(policy.equals("rule") ? CoverPolicy.Mode.RULE : CoverPolicy.Mode.MODEL, 0, candidate);
                    scenario.addProperty("pair", Long.toHexString(pairSeed));
                }
            }
            scenario.addProperty("policy", policy);
            bot.guardian.context = scenario;
            cover = null;
            hp0 = body.getHealth();
            minHp = hp0;
            calm = 0;
            ticks = 0;
            state = 1;
        }

        private boolean spawnMobs(BotPlayer body) {
            Random r = new Random(scenario.get("mob_seed").getAsLong());
            boolean cave = scenario.get("cave").getAsBoolean();
            int n = oneOf(r, new double[]{40, 30, 15, 10, 5}, new Integer[]{1, 2, 3, 4, 5});
            JsonArray kinds = new JsonArray();
            BlockPos feet = body.blockPosition();
            for (int i = 0; i < n; i++) {
                EntityType<? extends Mob> type = pick(r, cave);
                BlockPos p = near(r, feet, 5, 14, type == EntityTypes.ENDERMAN ? 3 : 2);
                if (p == null) continue;
                Mob m = type.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (m == null) continue;
                m.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, r.nextFloat() * 360, 0);
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.MOB_SUMMONED, null);
                if (m instanceof net.minecraft.world.entity.monster.cubemob.Slime slime) slime.setSize(2 + r.nextInt(3), true);
                if (m instanceof net.minecraft.world.entity.monster.zombie.Drowned && r.nextDouble() < 0.3) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                if (m instanceof net.minecraft.world.entity.monster.zombie.Zombie z && !(m instanceof net.minecraft.world.entity.monster.zombie.Drowned) && r.nextDouble() < 0.1) z.setBaby(true);
                // (Half the time proof against the sun: a fight by day is not always won by it.)
                if (r.nextBoolean()) m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MAX_FIGHT * 2, 0, false, false));
                m.setPersistenceRequired();
                level.addFreshEntity(m);
                m.setTarget(body);
                mobs.add(m);
                kinds.add(type.toShortString());
            }
            if (mobs.isEmpty()) return false;
            scenario.add("mobs", kinds);
            return true;
        }

        private void fight(BotPlayer body) {
            ticks++;
            if (!body.isAlive()) {
                end(true, "died");
                return;
            }
            minHp = Math.min(minHp, body.getHealth());
            for (Mob m : mobs) if (m.isAlive() && m.getTarget() == null && m.distanceToSqr(body) < 24 * 24) m.setTarget(body);
            if (ticks % 20 == 0) {
                boolean after = false;
                for (Mob m : mobs) if (m.isAlive() && m.distanceToSqr(body) < 32 * 32) after = true;
                calm = after ? 0 : calm + 1;
                if (calm >= 3) end(false, "clear");
            }
            if (state == 2 && ticks > MAX_FIGHT) end(false, "timeout");
        }

        private void end(boolean died, String how) {
            BotPlayer body = bot.body();
            bot.guardian.endEpisode("arena_" + how);
            int kills = 0;
            for (Mob m : mobs) if (!m.isAlive()) kills++;
            JsonObject o = new JsonObject();
            o.addProperty("slot", index);
            o.addProperty("mode", mode.name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("policy", policy);
            if (candidate != null) {
                o.addProperty("candidate", candidate);
                CoverModel cm = ModelStore.cover(candidate);
                if (cm != null) o.addProperty("candidate_version", cm.version);
            }
            CoverModel used = ModelStore.cover();
            if (used != null) o.addProperty("model_version", used.version);
            o.add("scenario", scenario);
            o.addProperty("end", how);
            o.addProperty("died", died ? 1 : 0);
            o.addProperty("hp0", hp0);
            o.addProperty("hp_end", died ? 0 : body.getHealth());
            o.addProperty("hp_lost", died ? hp0 : Math.max(0, hp0 - minHp));
            o.addProperty("secs", ticks / 20.0);
            o.addProperty("kills", kills);
            o.addProperty("mobs", mobs.size());
            o.addProperty("decided", cover != null);
            if (cover != null) {
                o.addProperty("option", cover.get("option").getAsString());
                o.addProperty("cover_id", cover.get("id").getAsString());
            }
            Journal.write("arena", o);
            fights++;
            if (died) deaths++;
            if (cover != null) {
                CoverOption c = CoverOption.valueOf(cover.get("option").getAsString());
                taken[c.ordinal()]++;
                if (died) diedAfter[c.ordinal()]++;
            }
            if (mode == Mode.EVAL) {
                int p = policy.equals("rule") ? 0 : 1;
                eval[p][0]++;
                eval[p][1] += died ? 1 : 0;
                eval[p][2] += died ? hp0 : Math.max(0, hp0 - minHp);
                eval[p][3] += ticks / 20.0;
                if (++pairStep >= 2) {
                    pairStep = 0;
                    pairs++;
                }
            }
            clearMobs();
            // (What fell from the fight - drops, arrows, the dead's things - away, it piles up otherwise.)
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, body.getBoundingBox().inflate(32, 16, 32),
                    e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb
                            || e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow)) e.discard();
            bot.clearTasks();
            bot.guardian.context = null;
            ticks = 0;
            // (The next fight a little further on: new ground, now and then.)
            if (random.nextDouble() < 0.1) anchor = anchor.offset(random.nextInt(-48, 49), 0, random.nextInt(-48, 49));
            state = died ? 3 : 0;
        }
    }
}
