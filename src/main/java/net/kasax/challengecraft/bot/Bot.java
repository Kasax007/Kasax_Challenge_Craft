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
    /** Lava, fire and drops about it, and the guard over its own moves (see {@link DangerSense}). */
    public final DangerSense danger = new DangerSense(this);
    /** Arrows and fireballs seen coming, and side-stepped (see {@link ThreatSense}). */
    public final ThreatSense threats = new ThreatSense(this);
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
    /**
     * Where and when it died; whether the things can be had back (not burnt, not in the void),
     * whether a monster killed it (it may still be there) and whether that was under ground.
     */
    public record Death(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, net.minecraft.core.BlockPos pos, long time, boolean recoverable,
                        boolean byMob, boolean underground) {
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
        // (The timers count the new body's ticks, which start again at nought.)
        retreatFailedAt = -1000;
        retreatFails = 0;
        shooterCheckAt = 0;
        reflexCooldown = 0;
        retreat = null;
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

    /**
     * Ends {@code task} and whatever was put on top of it, without a word to the brain: what was
     * going on underneath goes on (its walk taken up again).
     */
    public void cancel(BotTask task) {
        if (task == null || !tasks.contains(task)) return;
        while (!tasks.isEmpty()) {
            BotTask t = tasks.pop();
            if (t == task) break;
        }
        navigator.stop();
        actions.reset();
        body.stopInputs();
        navigator.resumeFor(tasks.peek());
    }

    /** What the reflexes put on top (a fight, a retreat, the shield): the brain leaves the stack alone meanwhile. */
    private BotTask reflexTask;

    private void reflex(BotTask task) {
        reflexTask = task;
        interject(task);
    }

    /** A reflex (fight, flight, shield, air) is still being carried out. */
    public boolean inReflex() {
        return reflexTask != null && tasks.contains(reflexTask);
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
        danger.restore();
        act();
        // Something flying at it that would hit: a step aside first.
        threats.react();
        // Last, the keys pressed: where they lead worked out, and not pressed if that is into harm.
        danger.guard();
    }

    private void act() {
        senses.tick(body);
        memory.tick(body);
        if (body.tickCount % 10 == 0) layTrail();
        waterBucketLanding(); // every tick, busy or not: a fall does not wait
        // Head in a block (gravel or sand come down on it, dug from under a column of them):
        // dug free at once, before anything else - it suffocates a heart every half second.
        if (body.isInWall()) {
            net.minecraft.core.BlockPos eye = net.minecraft.core.BlockPos.containing(body.getX(), body.getEyeY(), body.getZ());
            net.minecraft.core.BlockPos q = !body.level().getBlockState(eye).getCollisionShape(body.level(), eye).isEmpty() ? eye : eye.below();
            navigator.stop();
            body.stopInputs();
            if (body.tickCount % 20 == 0) say("buried (head in " + body.level().getBlockState(q).getBlock().getName().getString() + "): digging free");
            actions.breakTick(q);
            return;
        }
        watchdog();
        // (A fall of more than four: what it was doing then, for the log - falls are the
        // commonest death after lava, and seldom a planned step.)
        if (body.fallDistance > 4 && !fallLogged) {
            fallLogged = true;
            BotManager.LOG.info("[Bot] {}: falling at {} ({} blocks so far): {} | nav {} | last step from {}", name, body.blockPosition().toShortString(),
                    Math.round(body.fallDistance), status(), navigator.debug(), lastGround == null ? "-" : lastGround.toShortString());
        }
        if (body.onGround()) {
            fallLogged = false;
            lastGround = body.blockPosition();
        }
        reflexes();
        // (Not in the middle of a fight or a flight: the plan waits till that is over.)
        if (brain != null && !inReflex()) brain.tick(this);
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
            // A walk left off without a word (the task waits for something now, or steers by
            // itself): ended, or the body runs on with the last keys of it until a wall stops it.
            // (Before the task's tick: what the task sets itself stands.)
            if (navigator.status() == BotNavigator.Status.MOVING && body.tickCount - navigator.tickedAt > 2) navigator.stop();
            r = task.tick(this);
            // Head under water: hold jump to swim up, whatever the task does (a player never forgets
            // that). Unless it is digging its way out from the bottom (afloat, it digs five times slower).
            if (body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) && !sinkToDig && !navigator.diving()) body.jump = true;
        } catch (RuntimeException e) {
            BotManager.LOG.warn("[Bot] {} task {} crashed", name, task.describe(), e);
            r = BotTask.Result.FAILED;
        }
        if (r != BotTask.Result.RUNNING) {
            tasks.remove(task);
            navigator.stop();
            navigator.resumeFor(tasks.peek());
            if (r == BotTask.Result.FAILED) {
                failures++;
                // The same thing failing over and over within half a minute (a step that cannot be
                // done, asked for again by the task under it): a loop. The whole errand ends as a
                // failure, so the brain - and its memory of failures - decides what next.
                long now = body.tickCount;
                String what = task.describe();
                var times = failedAt.computeIfAbsent(what, k -> new java.util.ArrayDeque<>());
                times.addLast(now);
                while (!times.isEmpty() && now - times.peekFirst() > 600) times.pollFirst();
                if (times.size() >= 10 && !tasks.isEmpty()) {
                    times.clear();
                    BotTask root = tasks.peekLast();
                    BotManager.LOG.info("[Bot] {}: loop - '{}' failed 10 times in half a minute: giving up on {}", name, what, root.describe());
                    say("x " + task.describe());
                    tasks.clear();
                    navigator.stop();
                    if (brain != null) brain.finished(this, root, false);
                    return;
                }
            }
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

    private boolean fallLogged;
    private net.minecraft.core.BlockPos lastGround;

    /** Where it was when it last got anywhere, and when (see {@link #watchdog()}). */
    private net.minecraft.core.BlockPos movedFrom;
    private int movedAt, unsticks;

    /**
     * The last line of defence against standing still for good: trying to walk somewhere (a way
     * set) for a minute without getting a block further, whatever the reason - a few steps off
     * to somewhere else, as a player shakes off whatever holds him, and the walk again from there.
     */
    private void watchdog() {
        var feet = body.blockPosition();
        if (movedFrom == null || feet.distSqr(movedFrom) > 2 || navigator.status() != BotNavigator.Status.MOVING) {
            movedFrom = feet;
            movedAt = body.tickCount;
            return;
        }
        if (body.tickCount - movedAt < 1200 || inReflex()) return;
        movedAt = body.tickCount;
        unsticks++;
        BotTask top = tasks.peek();
        say("stuck for a minute (" + (top == null ? "-" : top.describe()) + ", " + navigator.debug() + "): a few steps off first");
        var r = body.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        net.minecraft.core.BlockPos aside = feet.offset((int) (Math.cos(a) * 8), 0, (int) (Math.sin(a) * 8));
        actions.reset();
        reflex(new net.kasax.challengecraft.bot.task.GoToTask(aside, 3));
    }

    /** When each kind of task last failed (for the loop breaker). */
    private final java.util.Map<String, java.util.ArrayDeque<Long>> failedAt = new java.util.HashMap<>();

    private int reflexCooldown, armorCheck;
    /** The last way off from a monster, and when one found no way (then it is a fight). */
    private BotTask retreat;
    private int retreatFailedAt = -1000, retreatFails;
    /** The retreat being watched for progress: from where, since when. */
    private BotTask retreatSeen;
    private net.minecraft.world.phys.Vec3 retreatFrom;
    private int retreatStart;

    /**
     * Things a player does without thinking about them, whatever the plan: hit back at a monster
     * that attacks, eat when hungry.
     */
    private int shooterCheckAt;

    private void reflexes() {
        // Every tick, cheaply: a creeper hissing close by (its fuse is a second and a half) or a
        // monster about to strike - the full look round at once, not up to ten ticks late.
        if (reflexCooldown > 0 && body.tickCount % 2 == 0 && dangerClose()) reflexCooldown = 0;
        if (reflexCooldown-- > 0) return;
        reflexCooldown = 10;
        // (Not throwing things out with a monster at it: the hands are for the fight.)
        if (!targeted(8)) tidyInventory();
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
            reflex(new net.kasax.challengecraft.bot.task.AirTask());
            return;
        }
        // In lava, or on magma: out onto cool ground before anything else.
        if (net.kasax.challengecraft.bot.task.LavaEscapeTask.burning(this) && !(top instanceof net.kasax.challengecraft.bot.task.LavaEscapeTask)) {
            actions.reset();
            reflex(new net.kasax.challengecraft.bot.task.LavaEscapeTask());
            return;
        }
        // On fire (out of lava, through a fire): put out at once - water poured at its feet from
        // the bucket and taken back, or into water close by - before it burns away the hearts.
        if (body.isOnFire() && !body.isInLava() && !body.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)
                && !(top instanceof net.kasax.challengecraft.bot.task.ExtinguishTask) && net.kasax.challengecraft.bot.task.ExtinguishTask.possible(this)) {
            actions.reset();
            reflex(new net.kasax.challengecraft.bot.task.ExtinguishTask());
            return;
        }
        // Sunk into powder snow by mistake (not on purpose for a tile): out, before it freezes.
        if (body.isInPowderSnow && body.getTicksFrozen() > 20 && !(top instanceof net.kasax.challengecraft.bot.task.SnowEscapeTask)
                && !(top instanceof net.kasax.challengecraft.bot.task.PowderSnowTask)) {
            actions.reset();
            reflex(new net.kasax.challengecraft.bot.task.SnowEscapeTask());
            return;
        }
        if (top instanceof net.kasax.challengecraft.bot.task.EatTask || top instanceof net.kasax.challengecraft.bot.task.HideTask) return;
        // Getting out of lava, out of the water, out of the fire: nothing else meanwhile (a meal
        // in the lava is the last one).
        if (top instanceof net.kasax.challengecraft.bot.task.LavaEscapeTask || top instanceof net.kasax.challengecraft.bot.task.AirTask
                || top instanceof net.kasax.challengecraft.bot.task.ExtinguishTask || top instanceof net.kasax.challengecraft.bot.task.SnowEscapeTask) return;
        // (A fight inside a hunt or a sequence counts too.)
        boolean fighting = BotTask.innermost(top) instanceof net.kasax.challengecraft.bot.task.KillTask
                || top instanceof net.kasax.challengecraft.bot.task.ShootTask && tasks.stream().anyMatch(t -> t instanceof net.kasax.challengecraft.bot.task.KillTask);
        // Low on health with a monster close: get away first (and eat on the way), as a player
        // backs off rather than trade the last hearts — in a fight too, once it goes badly. A
        // creeper about to blow: always away. (Cornered, with no way off: fight on, below.)
        // Running away and getting nowhere (a corner, a dead end, no way found) while still being
        // hit: the run is over - a failed one, and two of those make it cornered: it fights.
        if (retreat != null && top == retreat) {
            if (retreatFrom == null || retreatSeen != retreat) {
                retreatSeen = retreat;
                retreatFrom = body.position();
                retreatStart = body.tickCount;
            }
            boolean stalled = body.tickCount - retreatStart > 40 && body.position().distanceToSqr(retreatFrom) < 9;
            if (body.hurtTime > 0 && (stalled || navigator.status() != BotNavigator.Status.MOVING && body.tickCount - retreatStart > 20)) {
                say("no way off from here: standing to fight");
                cancel(retreat);
                body.hurry = false;
                retreat = null;
                retreatFailedAt = body.tickCount;
                retreatFails = Math.max(2, retreatFails + 1);
                top = tasks.peek();
            } else if (body.position().distanceToSqr(retreatFrom) >= 9 && body.tickCount - retreatStart > 40) {
                retreatFrom = body.position();
                retreatStart = body.tickCount;
            }
        }
        boolean recent = body.tickCount - retreatFailedAt < 200;
        if (!recent) retreatFails = 0;
        boolean cornered = recent && retreatFails >= 2;
        if (!(top instanceof net.kasax.challengecraft.bot.task.GoToTask) && !cornered) {
            // (Any enemy, not only the Monster kind: magma cubes and slimes are not of it.)
            for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(6),
                    e -> e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy)) {
                boolean creeper = m instanceof net.minecraft.world.entity.monster.Creeper c && c.getSwellDir() > 0;
                // A tile wants an explosion survived: at full health, let it go off a few blocks away
                // (about five blocks off: on hard some four hearts, at full health only).
                if (creeper && welcomeExplosion && body.getHealth() >= 20) {
                    double d = m.distanceTo(body);
                    if (d < 4.4) {
                        net.minecraft.world.phys.Vec3 back = body.position().subtract(m.position()).normalize().scale(5.1 - d);
                        actions.reset();
                        reflex(new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(back)), 0.8).sprinting());
                    } else {
                        navigator.stop();
                        body.stopInputs();
                    }
                    return;
                }
                // Hissing too close to get clear (its blast reaches some seven blocks): behind
                // the shield, facing it, if there is one - that takes the blast.
                if (creeper && m.distanceTo(body) < 3 && body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)
                        && !(top instanceof net.kasax.challengecraft.bot.task.ShieldUpTask)) {
                    actions.reset();
                    navigator.stop();
                    reflex(net.kasax.challengecraft.bot.task.ShieldUpTask.against((net.minecraft.world.entity.monster.Creeper) m));
                    return;
                }
                boolean hard = body.level().getDifficulty() == net.minecraft.world.Difficulty.HARD;
                // Low with something at it that only strikes close (a zombie that keeps
                // coming, wherever it runs): three blocks up, out of reach, and eat up there.
                // (Not from a magma cube or a slime: they leap that high - away from those instead.)
                if (!creeper && body.getHealth() <= (fighting ? 6 : 7) + (hard ? 2 : 0) && !(m instanceof net.minecraft.world.entity.monster.RangedAttackMob)
                        && !(m instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob)
                        && net.kasax.challengecraft.bot.task.HideTask.pillarPossible(this)) {
                    actions.reset();
                    navigator.stop();
                    say("hurt, with a " + m.getType().toShortString() + " at me: up a pillar to eat");
                    reflex(net.kasax.challengecraft.bot.task.HideTask.upward());
                    return;
                }
                // (From a magma cube sooner: they come in families, each split another two blows.)
                if (creeper || body.getHealth() <= (fighting ? 6 : 7) + (hard ? 2 : 0)
                        + (m instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob ? 4 : 0)) {
                    net.minecraft.world.phys.Vec3 away = openWayFrom(m.position(), 16);
                    // (That way was blocked last time: off to the side instead.)
                    if (recent) away = new net.minecraft.world.phys.Vec3(-away.z, 0, away.x);
                    actions.reset();
                    retreat = new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(away)), 3).sprinting();
                    reflex(retreat);
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
                // (On hard, a blast close by takes a dozen hearts and more: only at near full health.)
                double needed = body.level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 16 : 10;
                if (armed && body.getHealth() >= needed) {
                    reflex(new net.kasax.challengecraft.bot.task.KillTask(c).nearby(12));
                } else {
                    net.minecraft.world.phys.Vec3 away = body.position().subtract(c.position()).normalize().scale(14);
                    retreat = new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(away)), 3).sprinting();
                    reflex(retreat);
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
            reflex(upward ? net.kasax.challengecraft.bot.task.HideTask.upward() : new net.kasax.challengecraft.bot.task.HideTask());
            return;
        }
        // A brute or a hoglin after it, and not geared to stand up to it: up on a pillar, and hit
        // from up there (nothing that only strikes close reaches two blocks up).
        boolean pillaring = tasks.stream().anyMatch(t -> BotTask.innermost(t) instanceof net.kasax.challengecraft.bot.task.PillarFightTask);
        if (!pillaring && net.kasax.challengecraft.bot.task.PillarFightTask.possible(this)) {
            // (Early: a brute's axe takes nearly all the hearts at one blow on hard, and it closes
            // ten blocks in two seconds - up before it gets there, not as it arrives.)
            for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(16, 6, 16),
                    m -> m.isAlive() && m.getTarget() == body && net.kasax.challengecraft.bot.task.PillarFightTask.wanted(this, m)
                            && (m.distanceTo(body) < 8 || body.hasLineOfSight(m)))) {
                // (A fight with it on foot, begun by the reflexes: ended for this.)
                if (top instanceof net.kasax.challengecraft.bot.task.KillTask k && k.target() == m) cancel(top);
                actions.reset();
                say("a " + m.getType().toShortString() + " after me: up on a pillar");
                reflex(new net.kasax.challengecraft.bot.task.PillarFightTask(m));
                return;
            }
        }
        // (Backing off from something: not straight back into a fight with it - the retreat
        // ends first; then, if it still comes, a fight.)
        boolean retreating = retreat != null && tasks.contains(retreat);
        if (fighting || pillaring || retreating) return;
        // A shield in the pack goes into the off hand (nothing else lives there).
        if (body.getOffhandItem().isEmpty()) {
            int slot = BotInventory.slotOf(body, net.minecraft.world.item.Items.SHIELD);
            if (slot >= 0) {
                var inv = body.getInventory();
                body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, inv.getItem(slot).copy());
                inv.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        // A bow drawn on it (skeleton, pillager): the shield up towards it until the arrow is in
        // it; then, armed and well and the shooter not far, after it (it shoots again otherwise).
        boolean shieldOn = body.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
        if (!(top instanceof net.kasax.challengecraft.bot.task.ShieldUpTask) && body.tickCount >= shooterCheckAt) {
            for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(20),
                    m -> m.isAlive() && m.getTarget() == body && m instanceof net.minecraft.world.entity.monster.RangedAttackMob
                            && m.isUsingItem() && body.hasLineOfSight(m))) {
                actions.reset();
                boolean armed = false;
                for (var st : body.getInventory().getNonEquipmentItems()) {
                    if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) armed = true;
                }
                // (Armed and well: straight at it, dodging the arrows - standing behind the shield
                // only lets it shoot again and again.)
                if (armed && body.getHealth() >= 12 && m.distanceTo(body) < 14 && m instanceof net.minecraft.world.entity.monster.Enemy) {
                    reflex(new net.kasax.challengecraft.bot.task.KillTask(m).nearby(16));
                    return;
                }
                if (shieldOn) {
                    reflex(new net.kasax.challengecraft.bot.task.ShieldUpTask(m));
                    return;
                }
                shooterCheckAt = body.tickCount + 40; // (nothing to do about it: not every check)
                break;
            }
        }
        net.minecraft.world.entity.LivingEntity attacker = body.getLastHurtByMob();
        // Hit while swimming (a drowned, a guardian): ashore first - in the water it is slow, cannot
        // crit, and every breath is short; a fight on land is won.
        if (attacker instanceof net.minecraft.world.entity.monster.Enemy && attacker.isAlive() && body.isInWater()
                && body.tickCount - body.getLastHurtByMobTimestamp() < 60 && !(top instanceof net.kasax.challengecraft.bot.task.NavGoalTask)) {
            actions.reset();
            say("attacked in the water: ashore first");
            reflex(new net.kasax.challengecraft.bot.task.NavGoalTask("ashore, away from the " + attacker.getType().toShortString(),
                    (lv, p) -> lv.getFluidState(p).isEmpty() && lv.getFluidState(p.below()).isEmpty()
                            && !lv.getBlockState(p.below()).getCollisionShape(lv, p.below()).isEmpty(),
                    b -> b.body().blockPosition(), 600));
            return;
        }
        // Magma cubes and slimes not wanted for anything: not fought - a big one splits in two to
        // four, each of those again, and the swarm wears the hearts down (the Nether's commonest
        // killer after lava). Hit with a swarm about: away from them instead.
        // (Unless a hunt is after them: then that is the job.)
        if (attacker instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube && attacker.isAlive() && !cornered
                && body.tickCount - body.getLastHurtByMobTimestamp() < 60 && !hunting(attacker.getType())) {
            int cubes = body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.AbstractCubeMob.class,
                    body.getBoundingBox().inflate(8), net.minecraft.world.entity.LivingEntity::isAlive).size();
            // (One or two that are at it already: fought - they leap faster than it runs.)
            if (cubes >= 4 || cubes >= 3 && body.getHealth() < 14) {
                net.minecraft.world.phys.Vec3 away = openWayFrom(cube.position(), 14);
                actions.reset();
                say(cubes + " " + cube.getType().toShortString() + "s about me: away from the swarm, not a fight");
                retreat = new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(away)), 3).sprinting();
                reflex(retreat);
                return;
            }
        }
        boolean fromPillar = attacker != null && net.kasax.challengecraft.bot.task.PillarFightTask.wanted(this, attacker)
                && net.kasax.challengecraft.bot.task.PillarFightTask.possible(this);
        // Bare-handed (just come back to life, say) against something that strikes close, more
        // than one about or at night: away, far, instead of a fist fight it cannot win (on hard a
        // zombie takes twenty blows of the fist; on seed 77 the bot died forty times so, at a bed
        // with zombies round it). Cornered: it fights all the same.
        if (attacker instanceof net.minecraft.world.entity.monster.Enemy && attacker.isAlive() && !armed() && !cornered
                && !(attacker instanceof net.minecraft.world.entity.monster.RangedAttackMob)
                && body.tickCount - body.getLastHurtByMobTimestamp() < 60 && attacker.distanceTo(body) < 8
                && !(top instanceof net.kasax.challengecraft.bot.task.GoToTask)) {
            net.minecraft.world.phys.Vec3 away = openWayFrom(attacker.position(), 32);
            actions.reset();
            say("no weapon, a " + attacker.getType().toShortString() + " at me: away, not a fist fight");
            retreat = new net.kasax.challengecraft.bot.task.GoToTask(net.minecraft.core.BlockPos.containing(body.position().add(away)), 3).sprinting();
            reflex(retreat);
            return;
        }
        if (attacker instanceof net.minecraft.world.entity.monster.Enemy && attacker.isAlive() && !fromPillar && (body.getHealth() > 7 || cornered)
                && body.tickCount - body.getLastHurtByMobTimestamp() < 60 && attacker.distanceTo(body) < 8) {
            actions.reset();
            reflex(new net.kasax.challengecraft.bot.task.KillTask(attacker).nearby(12));
            return;
        }
        // A monster coming for it close by, armed and well: it strikes first (the first blow at a
        // run throws it back), instead of waiting to be hit. (Creepers: above.)
        if (body.getHealth() >= 12 && armed()) {
            for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(6),
                    m -> m.isAlive() && m.getTarget() == body && m instanceof net.minecraft.world.entity.monster.Enemy
                            && !(m instanceof net.minecraft.world.entity.monster.Creeper) && Math.abs(m.getY() - body.getY()) < 2.5 && body.hasLineOfSight(m)
                            // (Not a magma cube or a slime nobody wants, left alone: they split, and the swarm is worse.)
                            // (Once in a fight with them, though - hit in the last ten seconds - the splits are
                            // hit first like anything else: waiting for their blows only costs hearts.)
                            && !(m instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob && !hunting(m.getType())
                                    && body.tickCount - body.getLastHurtByMobTimestamp() > 200)
                            && !(net.kasax.challengecraft.bot.task.PillarFightTask.wanted(this, m) && net.kasax.challengecraft.bot.task.PillarFightTask.possible(this)))) {
                actions.reset();
                reflex(new net.kasax.challengecraft.bot.task.KillTask(m).nearby(12));
                return;
            }
        }
        int food = body.getFoodData().getFoodLevel();
        // (On hard, topped up sooner: the hearts only come back with a full stomach, and two blows
        // of a magma cube or a brute take twelve.)
        boolean hurt = body.getHealth() < body.getMaxHealth() * (body.level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 0.8f : 0.6f) && food < 20;
        // (Not with a monster at it: eating stands still for a second and a half. Unless starving.)
        if ((food <= 14 || hurt) && (food <= 4 || !targeted(8)) && net.kasax.challengecraft.bot.task.EatTask.bestFood(body) >= 0) {
            actions.reset();
            reflex(new net.kasax.challengecraft.bot.task.EatTask());
        }
    }

    /** Whether a task on the stack hunts this kind (then meeting one is the job). */
    private boolean hunting(net.minecraft.world.entity.EntityType<?> type) {
        for (BotTask t : tasks) {
            BotTask in = BotTask.innermost(t);
            if (t instanceof net.kasax.challengecraft.bot.task.KillTask k && k.after(type)
                    || in instanceof net.kasax.challengecraft.bot.task.KillTask k2 && k2.after(type)) return true;
            if (t instanceof net.kasax.challengecraft.bot.task.HuntRoundTask) return true;
        }
        return false;
    }

    /** A weapon (sword or axe) in the pack. */
    private boolean armed() {
        for (var st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(net.minecraft.tags.ItemTags.SWORDS) || st.is(net.minecraft.tags.ItemTags.AXES)) return true;
        }
        return false;
    }

    /** A monster within {@code r} blocks out for it. */
    public boolean targeted(double r) {
        return !body.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, body.getBoundingBox().inflate(r),
                m -> m.isAlive() && m.getTarget() == body && m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty();
    }

    /** Cheap, for every tick: a creeper hissing within five blocks, or a monster out for it within three. */
    private boolean dangerClose() {
        if (body.isOnFire() && !body.isInLava()) return true;
        for (var m : body.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, body.getBoundingBox().inflate(5), net.minecraft.world.entity.LivingEntity::isAlive)) {
            if (m instanceof net.minecraft.world.entity.monster.Creeper c && c.getSwellDir() > 0) return true;
            if (m.getTarget() == body && m.distanceTo(body) < 3) return true;
        }
        return false;
    }

    /**
     * Which way to run from a threat at {@code from}: straight away from it, or up to ninety
     * degrees off that, whichever has the most open ground ahead (not into a wall or a corner).
     */
    net.minecraft.world.phys.Vec3 openWayFrom(net.minecraft.world.phys.Vec3 from, double length) {
        net.minecraft.world.phys.Vec3 base = body.position().subtract(from).multiply(1, 0, 1);
        if (base.lengthSqr() < 1e-4) base = new net.minecraft.world.phys.Vec3(1, 0, 0);
        base = base.normalize();
        net.minecraft.world.phys.Vec3 best = base.scale(length);
        double bestFree = -1;
        net.minecraft.world.phys.Vec3 eye = body.getEyePosition();
        for (double deg : new double[]{0, 45, -45, 90, -90}) {
            double r = Math.toRadians(deg);
            net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(base.x * Math.cos(r) - base.z * Math.sin(r), 0, base.x * Math.sin(r) + base.z * Math.cos(r));
            var hit = body.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.scale(length)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, body));
            double free = hit.getLocation().distanceTo(eye);
            // Ground all the way: a run off a cliff edge (or into a ravine) ends where the ground
            // drops more than three blocks.
            for (int k = 1; k <= (int) free; k++) {
                net.minecraft.core.BlockPos col = net.minecraft.core.BlockPos.containing(body.getX() + dir.x * k, body.getY() - 0.5, body.getZ() + dir.z * k);
                boolean ground = false;
                for (int dy = 0; dy <= 3 && !ground; dy++) {
                    net.minecraft.core.BlockPos q = col.below(dy);
                    var st = body.level().getBlockState(q);
                    ground = !st.getCollisionShape(body.level(), q).isEmpty() || !st.getFluidState().isEmpty()
                            && st.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                    if (st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) break;
                }
                // (Nor into lava or fire at the feet, or beside the way: a run along a lava sea's
                // shore is one stumble from the end.)
                if (ground) {
                    net.minecraft.core.BlockPos feetAt = col.above();
                    net.minecraft.core.BlockPos side = net.minecraft.core.BlockPos.containing(-dir.z, 0, dir.x);
                    if (DangerSense.hotCell(body.level(), feetAt) || DangerSense.hotCell(body.level(), feetAt.offset(side))
                            || DangerSense.hotCell(body.level(), feetAt.subtract(side))) ground = false;
                }
                if (!ground) {
                    free = k - 1;
                    break;
                }
            }
            // (Straight away is worth a little more: the others lead past the threat's side.)
            free -= Math.abs(deg) / 45.0;
            if (free > bestFree + 0.5) {
                bestFree = free;
                best = dir.scale(length);
            }
        }
        return best;
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
        // Still full: what is no use to anything on the board goes, the cheapest first (rotten
        // flesh, seeds, saplings before an odd block), never what is worth something.
        for (int cheap = 0; cheap < 2 && free < 3; cheap++) {
            throwawaySeen = 0;
            for (int i = 0; i < inv.size() && free < 3; i++) {
                var st = inv.get(i);
                if (st.isEmpty() || !clutter(st) || (cheap == 0 && !cheap(st))) continue;
                // (Gone for good: dropped at its feet it would be picked up again at once.)
                inv.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                say("pack full: threw away " + st.getCount() + " " + st.getItem().toString().replace("minecraft:", ""));
                free++;
            }
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
        // Worth something whatever the board says (trading, bartering, armour, the Nether's riches).
        if (VALUABLE.stream().anyMatch(id::contains)) return false;
        // Building blocks: a stack stays (the first one met), the rest is clutter.
        // (Counted over all kinds: 27 blackstone thrown away beside a stack of netherrack left too
        // few for the pillar up to the portal.)
        return !BotActions.THROWAWAY.contains(item) || BotActions.buildingBlocks(body) - st.getCount() >= 64 && ++throwawaySeen > 1;
    }

    private int throwawaySeen;

    /** Parts of item names never thrown away to make room. */
    private static final java.util.List<String> VALUABLE = java.util.List.of(
            "gold", "netherite", "ancient_debris", "bundle", "leather", "book", "map", "eye", "shulker", "totem",
            "pearl", "rod", "tear", "star", "shard", "wart", "slime", "honey", "amethyst", "crying_obsidian", "bed",
            "shield", "saddle", "bow", "arrow", "copper", "iron", "lapis", "blaze", "powder", "magma_cream", "bone",
            "feather", "paper", "sugar_cane", "wool", "spyglass", "compass", "clock", "potion", "scute", "trident",
            "nautilus", "heart", "echo", "trim", "sherd", "egg", "head", "skull", "_ore", "clay", "glass", "brick");

    /** What a player throws first: worthless scraps. */
    private static boolean cheap(net.minecraft.world.item.ItemStack st) {
        String id = st.getItem().toString().replace("minecraft:", "");
        return id.equals("rotten_flesh") || id.endsWith("_seeds") || id.endsWith("sapling") || id.equals("poisonous_potato")
                || id.equals("spider_eye") || id.equals("dead_bush") || id.equals("short_grass") || id.equals("fern")
                || id.equals("kelp") || id.equals("seagrass") || id.equals("pointed_dripstone") || id.equals("moss_carpet")
                || BotActions.THROWAWAY.contains(st.getItem());
    }

    public void say(String text) {
        if (verbose) BotManager.debug(body, text);
    }
}
