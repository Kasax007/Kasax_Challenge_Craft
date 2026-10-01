package net.kasax.challengecraft.bot;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * One bot: its current body (a new one after every respawn), the tools that act through that
 * body, and what it is doing — a stack of {@link BotTask}s, the top one running. A brain (see
 * {@link BotBrain}) decides what to push when the stack runs empty.
 */
public final class Bot {
    public final UUID id;
    public final String name;
    private BotPlayer body;
    private BotTools tools;
    private BotActions actions;
    private BotNavigator navigator;
    private final Deque<BotTask> tasks = new ArrayDeque<>();
    private BotBrain brain;
    /** What it has noticed around it; kept across deaths. */
    private final BotSenses senses = new BotSenses();
    /** Where things are, as read from the world around it; kept across deaths. */
    private final BotMemory memory = new BotMemory();
    /** The crafting table it last put down itself (taken along when it moves on). */
    public net.minecraft.core.BlockPos ownTable;
    /**
     * Blocks it set out for and found no way to (a tree on top of a mesa, ore behind lava), with
     * when: left alone for a few minutes, whichever task wants them next.
     */
    private final java.util.Map<net.minecraft.core.BlockPos, Long> unreachable = new java.util.HashMap<>();

    public void markUnreachable(net.minecraft.core.BlockPos p) {
        unreachable.put(p.immutable(), body.level().getGameTime());
    }

    /** The ones that still count as out of reach (five minutes after the try). */
    public java.util.Set<net.minecraft.core.BlockPos> unreachable() {
        long now = body.level().getGameTime();
        unreachable.values().removeIf(t -> now - t > 6000);
        return unreachable.keySet();
    }

    /** Set by a task for the tick: under water, not swimming up (digging out from the bottom). */
    public boolean sinkToDig;

    /** Where two eyes of ender said the stronghold is (triangulated), once worked out. */
    public net.minecraft.core.BlockPos knownStronghold;

    /** Work blocks (tables, furnaces) it could not get to: not counted on again. */
    public final java.util.Set<net.minecraft.core.BlockPos> unreachableStations = new java.util.HashSet<>();
    /** For benchmarks: ticks with nothing to do, tasks that failed. */
    public int idleTicks, failures;
    /** Off while a fall is meant to hurt (a Lockout goal wants the damage). */
    public boolean waterLandingAllowed = true;
    /** The way it last walked out exploring: kept, so a fresh search does not turn back on itself. */
    public net.minecraft.core.Direction exploreHeading;
    /** Biomes it has set out for, far off, with what grows there (so it keeps to one, see ObtainPlanner). */
    public record FarLead(String dimension, net.minecraft.core.BlockPos pos) {
    }

    public final java.util.Map<FarLead, java.util.List<String>> farLeads = new java.util.HashMap<>();

    /** Where and when it last died, and whether its things can still be there (not in lava, not in the void). */
    public record Death(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, net.minecraft.core.BlockPos pos, long time, boolean recoverable) {
    }

    public Death lastDeath;
    /** Where it last went down into a cave from the surface: the way back out. */
    public net.minecraft.core.BlockPos caveEntry;
    /**
     * The way it came down, whatever brought it there (a mine, a hunt through the caves): a spot
     * every few blocks since it left the surface, the first being where it left it. The way out
     * is that trail backwards, as a player retraces his steps.
     */
    public final java.util.ArrayList<net.minecraft.core.BlockPos> trail = new java.util.ArrayList<>();
    /** Lava pools where casting a portal failed (no room, no way there): not chosen again. */
    public final java.util.Set<net.minecraft.core.BlockPos> badCastPools = new java.util.HashSet<>();
    private net.minecraft.core.BlockPos lastSurface;
    private Object trailDimension;
    /** Set by the brain while an explosion survived would claim a tile: a creeper is then welcome. */
    public boolean welcomeExplosion;
    /** When the way back through a portal last failed (game time). */
    public long portalBackFailedAt = -100_000;
    /** Chat what it is doing (for testing). */
    public boolean verbose = true;

    Bot(BotPlayer body) {
        this.id = body.getUUID();
        this.name = body.getGameProfile().name();
        attach(body);
    }

    void attach(BotPlayer body) {
        this.body = body;
        this.tools = new BotTools(body);
        this.actions = new BotActions(body, tools);
        this.navigator = new BotNavigator(body, tools, actions);
        // Whatever was going on was about the old body's position and inventory.
        tasks.clear();
    }

    public BotPlayer body() {
        return body;
    }

    public BotTools tools() {
        return tools;
    }

    public BotActions actions() {
        return actions;
    }

    public BotSenses senses() {
        return senses;
    }

    public BotMemory memory() {
        return memory;
    }

    public BotNavigator navigator() {
        return navigator;
    }

    public MinecraftServer server() {
        return body.level().getServer();
    }

    public BotBrain brain() {
        return brain;
    }

    public void setBrain(BotBrain brain) {
        this.brain = brain;
        tasks.clear();
        navigator.stop();
    }

    /** Replaces whatever the bot is doing with {@code task}. */
    public void doNow(BotTask task) {
        tasks.clear();
        navigator.stop();
        tasks.push(task);
        say("-> " + task.describe());
    }

    /** Drops everything the bot is doing (the brain decides anew on the next tick). */
    public void clearTasks() {
        tasks.clear();
        navigator.stop();
        actions.reset();
        body.stopInputs();
    }

    /** Runs {@code task} first, then returns to what was going on. */
    public void interject(BotTask task) {
        // (The walk of what was on top is kept, and taken up again once this is done.)
        navigator.suspend(tasks.peek());
        tasks.push(task);
    }

    /** The task stack, top first, with details (for testing). */
    public String status() {
        StringBuilder sb = new StringBuilder();
        for (BotTask t : tasks) sb.append(" > ").append(t.status());
        return sb.length() == 0 ? " idle" : sb.toString();
    }

    /** The task at the bottom of the stack: what it set out to do (the rest is on the way there). */
    public BotTask root() {
        return tasks.peekLast();
    }

    public BotTask current() {
        return tasks.peek();
    }

    void tick() {
        if (!body.isAlive()) return;
        senses.tick(body);
        memory.tick(body);
        if (body.tickCount % 10 == 0) layTrail();
        waterBucketLanding(); // every tick, busy or not: a fall does not wait
        reflexes();
        if (brain != null) brain.tick(this);
        BotTask task = tasks.peek();
        if (task == null) {
            if (brain != null) brain.think(this);
            task = tasks.peek();
            if (task == null) {
                idleTicks++;
                body.stopInputs();
                // (Even doing nothing: not under water.)
                // (Unless the way leads down through the water, along a lake bed, and there is air
            // to spare: then the path decides.)
            if (body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                    && (!navigator.headingDown() || body.getAirSupply() < body.getMaxAirSupply() / 2)) body.jump = true;
                return;
            }
        }
        BotTask.Result r;
        try {
            sinkToDig = false;
            r = task.tick(this);
            // Head under water: hold jump to swim up, whatever the task does (a player never forgets
            // that). Unless it is digging its way out from the bottom (afloat, it digs five times slower).
            if (body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) && !sinkToDig) body.jump = true;
        } catch (RuntimeException e) {
            BotManager.LOG.warn("[Bot] {} task {} crashed", name, task.describe(), e);
            r = BotTask.Result.FAILED;
        }
        if (r != BotTask.Result.RUNNING) {
            tasks.remove(task);
            navigator.stop();
            navigator.resumeFor(tasks.peek());
            if (r == BotTask.Result.FAILED) failures++;
            if (task == retreat) {
                body.hurry = false;
                if (r == BotTask.Result.FAILED) {
                    retreatFailedAt = body.tickCount;
                    retreatFails++;
                }
                retreat = null;
            }
            say((r == BotTask.Result.FAILED ? "x " : "done: ") + task.describe());
            if (brain != null) brain.finished(this, task, r == BotTask.Result.DONE);
        }
    }

    private static final int TRAIL_STEP = 6, TRAIL_CAP = 400;

    private void layTrail() {
        var feet = navigator.feet();
        if (trailDimension != body.level().dimension()) {
            trailDimension = body.level().dimension();
            trail.clear();
            lastSurface = null;
        }
        if (!body.onGround() && !body.isInWater()) return;
        if (!net.kasax.challengecraft.bot.task.SurfaceTask.underground(body)) {
            lastSurface = feet;
            trail.clear();
            return;
        }
        if (trail.isEmpty() && lastSurface != null) trail.add(lastSurface);
        if (trail.isEmpty() || trail.get(trail.size() - 1).distSqr(feet) >= TRAIL_STEP * TRAIL_STEP) trail.add(feet);
        // Long: every other crumb in the middle dropped (the ends matter most).
        if (trail.size() > TRAIL_CAP) for (int i = trail.size() - 2; i > 0; i -= 2) trail.remove(i);
    }

    private int reflexCooldown, armorCheck;
    /** The last way off from a monster, and when one found no way (then it is a fight). */
    private BotTask retreat;
    private int retreatFailedAt = -1000, retreatFails;

    /**
     * Things a player does without thinking about them, whatever the plan: hit back at a monster
     * that attacks, eat when hungry.
     */
    private void reflexes() {
        if (reflexCooldown-- > 0) return;
        reflexCooldown = 10;
        tidyInventory();
        // Better armour on as soon as it has some (and gold in the Nether); not while a task puts
        // on something in particular (a tile wants leather worn).
        if (++armorCheck % 5 == 0 && !(tasks.peek() instanceof net.kasax.challengecraft.bot.task.EquipTask)
                && !(tasks.peek() instanceof net.kasax.challengecraft.bot.task.PowderSnowTask)) BotArmor.wearBest(this);
        // Bundled things the plan wants again: out, while there is room.
        if (armorCheck % 10 == 0 && !keepItems.isEmpty() && BotBundles.freeSlots(body) >= 3
                && BotBundles.countInside(body, keepItems) > 0) BotBundles.unpack(this, keepItems);
        BotTask top = tasks.peek();
        // Running out of air with water over the head that swimming up does not get out of: to
        // the nearest air first, whatever else is going on.
        // (A lid over the head, no water above it to swim up through: at once, while there is air.)
        net.minecraft.core.BlockPos overHead = net.minecraft.core.BlockPos.containing(body.getX(), body.getEyeY(), body.getZ()).above();
        boolean lid = !body.level().getBlockState(overHead).getCollisionShape(body.level(), overHead).isEmpty();
        if (body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) && body.getAirSupply() < body.getMaxAirSupply() * (lid ? 4 : 2) / 5
                && !(top instanceof net.kasax.challengecraft.bot.task.AirTask)) {
            actions.reset();
            interject(new net.kasax.challengecraft.bot.task.AirTask());
            return;
        }
        if (top instanceof net.kasax.challengecraft.bot.task.EatTask || top instanceof net.kasax.challengecraft.bot.task.HideTask) return;
        boolean fighting = top instanceof net.kasax.challengecraft.bot.task.KillTask;
        // Low on health with a monster close: get away first (and eat on the way), as a player
        // backs off rather than trade the last hearts — in a fight too, once it goes badly. A
        // creeper about to blow: always away. (Cornered, with no way off: fight on, below.)
        boolean recent = body.tickCount - retreatFailedAt < 200;
        if (!recent) retreatFails = 0;
        boolean cornered = recent && retreatFails >= 2;
        if (!(top instanceof net.kasax.challengecraft.bot.task.GoToTask) && !cornered) {
            for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, body.getBoundingBox().inflate(6), net.minecraft.world.entity.LivingEntity::isAlive)) {
                boolean creeper = m instanceof net.minecraft.world.entity.monster.Creeper c && c.getSwellDir() > 0;
                // A tile wants an explosion survived: at full health, let it go off a few blocks away
                // (about five blocks off: on hard some four hearts, at full health only).
                if (creeper && welcomeExplosion && body.getHealth() >= 20) {
                    double d = m.distanceTo(body);
                    if (d < 4.4) {
                        net.minecraft.world.phys.Vec3 back = body.position().subtract(m.position()).normalize().scale(5.1 - d);
                        actions.reset();
                        interject(new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(back)), 0.8).sprinting());
                    } else {
                        navigator.stop();
                        body.stopInputs();
                    }
                    return;
                }
                if (creeper || body.getHealth() <= (fighting ? 6 : 7)) {
                    net.minecraft.world.phys.Vec3 away = body.position().subtract(m.position()).normalize().scale(16);
                    // (That way was blocked last time: off to the side instead.)
                    if (recent) away = new net.minecraft.world.phys.Vec3(-away.z, 0, away.x);
                    actions.reset();
                    retreat = new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(away)), 3).sprinting();
                    interject(retreat);
                    return;
                }
            }
        }
        // A creeper coming for it, not hissing yet: dealt with before it gets close, as a player
        // does. With a weapon and the hearts for it, killed (hit, a step back, hit); else away.
        if (!fighting && !welcomeExplosion && !(top instanceof net.kasax.challengecraft.bot.task.GoToTask)) {
            for (var c : body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, body.getBoundingBox().inflate(7),
                    c -> c.isAlive() && c.getTarget() == body && c.getSwellDir() <= 0 && body.hasLineOfSight(c))) {
                boolean armed = false;
                for (var st : body.getInventory().getNonEquipmentItems()) {
                    if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) armed = true;
                }
                actions.reset();
                if (armed && body.getHealth() >= 10) {
                    interject(new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(c.getType()), java.util.Set.of(), 0, 1));
                } else {
                    net.minecraft.world.phys.Vec3 away = body.position().subtract(c.position()).normalize().scale(14);
                    retreat = new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(away)), 3).sprinting();
                    interject(retreat);
                }
                return;
            }
        }
        // Hurt badly by something (an arrow from afar counts), and running did not or would not
        // help: into the ground until the hearts are back.
        var shooter = body.getLastHurtByMob();
        boolean shot = shooter != null && shooter.isAlive() && shooter.distanceTo(body) > 6 && body.tickCount - body.getLastHurtByMobTimestamp() < 60;
        // A bow drawn on it (skeleton, pillager) with few hearts left: before the arrow, not after.
        boolean aimedAt = body.getHealth() <= 10 && !body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(16),
                m -> m.isAlive() && m.getTarget() == body && m instanceof net.minecraft.world.entity.monster.RangedAttackMob).isEmpty();
        if ((body.getHealth() <= (shot ? 10 : 6) && (cornered || shot) && body.tickCount - body.getLastHurtByMobTimestamp() < 60 || aimedAt)
                && (net.kasax.challengecraft.bot.task.HideTask.possible(this) || net.kasax.challengecraft.bot.task.HideTask.pillarPossible(this))) {
            actions.reset();
            navigator.stop();
            // Arrows: into the ground. Zombies and the like close by: up a pillar, out of their reach
            // (they would hit on while it digs).
            boolean melee = !shot && !body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, body.getBoundingBox().inflate(3),
                    net.minecraft.world.entity.LivingEntity::isAlive).isEmpty();
            boolean upward = melee && net.kasax.challengecraft.bot.task.HideTask.pillarPossible(this) || !net.kasax.challengecraft.bot.task.HideTask.possible(this);
            interject(upward ? net.kasax.challengecraft.bot.task.HideTask.upward() : new net.kasax.challengecraft.bot.task.HideTask());
            return;
        }
        if (fighting) return;
        net.minecraft.world.entity.LivingEntity attacker = body.getLastHurtByMob();
        if (attacker instanceof net.minecraft.world.entity.monster.Enemy && attacker.isAlive() && (body.getHealth() > 7 || cornered)
                && body.tickCount - body.getLastHurtByMobTimestamp() < 60 && attacker.distanceTo(body) < 8) {
            actions.reset();
            interject(new net.kasax.challengecraft.bot.task.KillTask(java.util.Set.of(attacker.getType()), java.util.Set.of(), 0, 1));
            return;
        }
        int food = body.getFoodData().getFoodLevel();
        boolean hurt = body.getHealth() < body.getMaxHealth() * 0.6f && food < 20;
        if ((food <= 14 || hurt) && net.kasax.challengecraft.bot.task.EatTask.bestFood(body) >= 0) {
            actions.reset();
            interject(new net.kasax.challengecraft.bot.task.EatTask());
        }
    }

    private net.minecraft.core.BlockPos mlgWater;

    /**
     * The water bucket trick: falling deep with a water bucket, look down and pour it on the ground
     * just before landing (no fall damage), then scoop it up again.
     */
    private void waterBucketLanding() {
        var level = body.level();
        if (mlgWater != null) {
            int slot = BotInventory.slotOf(body, net.minecraft.world.item.Items.BUCKET);
            if (body.onGround() || body.isInWater()) {
                if (slot >= 0 && level.getFluidState(mlgWater).isSource()) {
                    tools.select(slot);
                    body.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(mlgWater));
                    body.gameMode.useItem(body, level, body.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                mlgWater = null;
            }
            return;
        }
        if (!waterLandingAllowed || body.onGround() || body.isInWater() || body.fallDistance < 4 || body.getDeltaMovement().y > -0.3) return;
        int slot = BotInventory.slotOf(body, net.minecraft.world.item.Items.WATER_BUCKET);
        if (slot < 0) return;
        net.minecraft.core.BlockPos p = body.blockPosition();
        for (int i = 0; i < 4; i++) {
            net.minecraft.core.BlockPos below = p.below(i + 1);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                double above = body.getY() - (below.getY() + 1);
                // Only a fall that hurts (a few blocks off a pillar costs a heart at most).
                if (above > 3.2 || body.fallDistance + above < 6 || !level.getFluidState(below).isEmpty()) return;
                tools.select(slot);
                body.setXRot(90f);
                body.gameMode.useItem(body, level, body.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND);
                mlgWater = below.above();
                return;
            }
        }
    }

    /** What a player throws away when the inventory fills up (keeping one stack of building blocks). */
    private static final java.util.Set<net.minecraft.world.item.Item> JUNK = java.util.Set.of(
            net.minecraft.world.item.Items.DIRT, net.minecraft.world.item.Items.GRAVEL, net.minecraft.world.item.Items.DIORITE,
            net.minecraft.world.item.Items.ANDESITE, net.minecraft.world.item.Items.GRANITE, net.minecraft.world.item.Items.TUFF,
            net.minecraft.world.item.Items.COBBLED_DEEPSLATE, net.minecraft.world.item.Items.COBBLESTONE,
            net.minecraft.world.item.Items.NETHERRACK, net.minecraft.world.item.Items.CALCITE);

    /** Frees slots when the inventory is nearly full: junk first, all but 64 building blocks. */
    private void tidyInventory() {
        var inv = body.getInventory().getNonEquipmentItems();
        int free = 0;
        for (var s : inv) if (s.isEmpty()) free++;
        if (free >= 3) return;
        throwawaySeen = 0;
        // A stack of building blocks stays, cobblestone first (furnaces, tools, walls), then the
        // rest (dirt, netherrack, the stone kinds); junk beyond that goes.
        int keep = 64;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < inv.size(); i++) {
                var s = inv.get(i);
                if (s.isEmpty() || !JUNK.contains(s.getItem())) continue; // (anything not junk stays)
                boolean cobble = s.is(net.minecraft.world.item.Items.COBBLESTONE) || s.is(net.minecraft.world.item.Items.COBBLED_DEEPSLATE);
                if (pass == 0 != cobble) continue;
                boolean building = BotActions.THROWAWAY.contains(s.getItem());
                if (building && keep > 0) {
                    keep -= s.getCount();
                    continue;
                }
                inv.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                body.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        body.getInventory().setChanged();
        free = 0;
        for (var st : inv) if (st.isEmpty()) free++;
        if (free >= 2) return;
        // Still full (a pack of odds and ends): into the bundle with them, if it has one.
        BotBundles.stash(this, keepItems, 3);
        free = BotBundles.freeSlots(body);
        if (free >= 2) return;
        // Still full: what is no use to anything on the board goes.
        for (int i = 0; i < inv.size() && free < 3; i++) {
            var st = inv.get(i);
            if (st.isEmpty() || !clutter(st)) continue;
            // (Gone for good: dropped at its feet it would be picked up again at once.)
            inv.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            say("pack full: threw away " + st.getCount() + " " + st.getItem().toString().replace("minecraft:", ""));
            free++;
        }
        body.getInventory().setChanged();
    }

    /** What the board or the plan still wants (kept up to date by the brain): never dropped. */
    public final java.util.Set<net.minecraft.world.item.Item> keepItems = new java.util.HashSet<>();

    private boolean clutter(net.minecraft.world.item.ItemStack st) {
        var item = st.getItem();
        if (keepItems.contains(item) || st.isDamageableItem() || st.get(net.minecraft.core.component.DataComponents.FOOD) != null) return false;
        // (The challenge's own things, the board map: handed out again anyway.)
        if (!item.toString().startsWith("minecraft:")) return false;
        String id = item.toString().replace("minecraft:", "");
        // The stuff of everything else: ores, metals, gems, wood, string, the buckets.
        if (id.contains("ingot") || id.startsWith("raw_") || id.contains("diamond") || id.contains("emerald") || id.contains("bucket")
                || id.equals("coal") || id.equals("redstone") || id.equals("lapis_lazuli") || id.equals("stick") || id.equals("string")
                || id.equals("flint") || id.equals("flint_and_steel") || id.equals("gunpowder") || id.equals("obsidian") || id.equals("ender_pearl")
                || id.equals("crafting_table") || id.equals("furnace") || id.equals("blaze_rod") || id.equals("quartz")
                || st.is(net.minecraft.tags.ItemTags.LOGS) || st.is(net.minecraft.tags.ItemTags.PLANKS)) return false;
        // Building blocks: a stack stays (the first one met), the rest is clutter.
        return !BotActions.THROWAWAY.contains(item) || ++throwawaySeen > 1;
    }

    private int throwawaySeen;

    public void say(String text) {
        if (verbose) BotManager.debug(body, text);
    }
}
