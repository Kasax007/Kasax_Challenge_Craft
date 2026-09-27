package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.entity.DiceEntity;
import net.kasax.challengecraft.entity.ModEntities;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.network.DiceSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Würfel / Dice (challenge 46) — you may only walk as many blocks as your last die roll showed.
 *
 * <p>Loop: right-click the die item → a physical {@link DiceEntity} is thrown and tumbles → when it
 * settles, the face pointing up <b>replaces</b> your horizontal movement budget. Replacing (rather
 * than adding) is what makes banking impossible: re-rolling early forfeits the remainder, so you
 * can never stockpile ten rolls into thirty blocks.
 *
 * <p>Budget is measured as <b>path length actually walked</b> in the XZ plane, so walking in a
 * circle still costs budget. Vertical movement is free.
 *
 * <p>Enforcement is two-layered: {@code DiceMovementMixin} kills the movement input client-side so
 * it <i>feels</i> immediate, and this class is the server-side authority that accounts the distance
 * and snaps a drifting player back.
 */
public class Chal_46_Dice {
    private static boolean active = false;
    private static int tickCounter = 0;

    /** Server-authoritative state. The client keeps its own copy in {@code DiceClientState} — a
     *  shared static would be a data race on an integrated server, where both sides share a JVM. */
    private static final Map<UUID, Double> SERVER_BUDGET = new HashMap<>();
    private static final Map<UUID, Integer> LAST_ROLL = new HashMap<>();
    private static final Map<UUID, Integer> IN_FLIGHT = new HashMap<>();
    /** Server tick at which a throw stops counting as in flight. See {@link #pruneStaleThrow}. */
    private static final Map<UUID, Long> IN_FLIGHT_DEADLINE = new HashMap<>();

    /**
     * How long a die may be airborne before the throw is written off.
     *
     * <p>A settling die takes a couple of seconds; ten is far past any legitimate roll and well
     * short of being noticeable as a wait. The cap exists because {@code IN_FLIGHT} is only cleared
     * by the die itself — if the entity disappears another way (chunk unloaded, {@code /kill @e},
     * dropped into the void, removed by another mod) the entry would survive forever and the player
     * could never roll again. That is a soft-lock with no way out, and it happened.
     */
    private static final long THROW_TIMEOUT_TICKS = 200L;
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    /** Where an out-of-budget player is held. */
    private static final Map<UUID, Vec3> ANCHOR = new HashMap<>();
    private static final Map<UUID, Integer> SNAP_COOLDOWN = new HashMap<>();

    /** Mirrors the local player's budget so the mixin can read it client-side. */
    private static volatile double clientRemaining = 0.0;

    /** Beyond this, treat the move as a teleport/portal rather than walking. */
    private static final double TELEPORT_THRESHOLD_SQ = 64.0;
    /** How far an exhausted player may drift before being snapped back. */
    private static final double DRIFT_TOLERANCE = 0.6;
    /** requestTeleport makes the server discard client movement until confirmed, so it must never
     *  be spammed — see the comment in {@link #enforce}. */
    private static final int SNAP_COOLDOWN_TICKS = 8;

    public static void register() {
        // Right-click throws the die. Never consumes it.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!active) return InteractionResult.PASS;
            if (!player.getItemInHand(hand).is(ModItems.DICE)) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.SUCCESS;

            if (player instanceof ServerPlayer serverPlayer) {
                throwDie(serverPlayer);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter++;
            // Scanning every inventory every tick would be wasteful; once a second is plenty.
            boolean handOutDice = tickCounter % 20 == 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (handOutDice && !player.isSpectator()) {
                    ensureDice(player);
                }
                tickPlayer(player);
            }
        });
    }

    /**
     * Guarantees every player is holding a die while the challenge runs. The die is the <b>only</b>
     * way to earn movement, so losing it (death, dropping it, a full inventory at join, a late
     * joiner) would leave that player permanently frozen. Also strips duplicates, so it can't be
     * farmed into a stack.
     */
    private static void ensureDice(ServerPlayer player) {
        var inventory = player.getInventory();
        int firstSlot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!inventory.getItem(i).is(ModItems.DICE)) continue;
            if (firstSlot == -1) {
                firstSlot = i;
            } else {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (firstSlot == -1) {
            inventory.add(new ItemStack(ModItems.DICE));
        }
    }

    // ---- activation ----------------------------------------------------------------------------

    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            SERVER_BUDGET.clear();
            LAST_ROLL.clear();
            IN_FLIGHT.clear();
            IN_FLIGHT_DEADLINE.clear();
            LAST_POS.clear();
            ANCHOR.clear();
            SNAP_COOLDOWN.clear();
            clientRemaining = 0.0;
            tickCounter = 0;
        }
        ChallengeCraft.LOGGER.info("[Chal46] {}", value ? "activated" : "deactivated");
    }

    public static boolean isActive() {
        return active;
    }

    // ---- budget --------------------------------------------------------------------------------

    public static double getBudget(UUID player) {
        return SERVER_BUDGET.getOrDefault(player, 0.0);
    }

    public static void setBudget(UUID player, double blocks) {
        SERVER_BUDGET.put(player, Math.max(0.0, blocks));
    }

    /** Side-aware read used by {@code DiceMovementMixin}, which runs on both logical sides. */
    public static double getBudgetFor(Player player) {
        if (player.level().isClientSide()) {
            // travel() only runs for the main player on a client, but be explicit: never clamp
            // another player's puppet from local state.
            return player.isLocalPlayer() ? clientRemaining : Double.MAX_VALUE;
        }
        return getBudget(player.getUUID());
    }

    public static void setClientRemaining(double value) {
        clientRemaining = value;
    }

    public static boolean hasDieInFlight(UUID player) {
        return IN_FLIGHT.containsKey(player);
    }

    public static void clearInFlight(UUID player) {
        IN_FLIGHT.remove(player);
        IN_FLIGHT_DEADLINE.remove(player);
    }

    /**
     * Drops a throw that can no longer finish, silently.
     *
     * <p>Two ways out: the deadline passes, or the die entity is simply gone from the world. Either
     * way the player just gets to roll again — deliberately with no message, because from their side
     * nothing happened that they did or could have avoided, and "your roll was voided" reads as a
     * punishment for a bug.
     */
    private static void pruneStaleThrow(ServerPlayer player, UUID uuid) {
        Integer dieId = IN_FLIGHT.get(uuid);
        if (dieId == null) return;

        long deadline = IN_FLIGHT_DEADLINE.getOrDefault(uuid, 0L);
        boolean expired = player.level().getGameTime() > deadline;
        boolean gone = !(player.level() instanceof ServerLevel world)
                || world.getEntity(dieId) == null;

        if (expired || gone) {
            clearInFlight(uuid);
            ChallengeCraft.LOGGER.info("[Dice] Wurf von {} verworfen ({}), erneutes Würfeln möglich",
                    player.getName().getString(), expired ? "Zeitüberschreitung" : "Würfel verschwunden");
            sync(player);
        }
    }

    // ---- throwing ------------------------------------------------------------------------------

    private static void throwDie(ServerPlayer player) {
        if (hasDieInFlight(player.getUUID())) {
            // The only restriction on throw rate: the previous die must have finished.
            player.sendOverlayMessage(Component.translatable("challengecraft.dice.already_rolling")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!(player.level() instanceof ServerLevel world)) return;

        DiceEntity die = new DiceEntity(ModEntities.DICE, world);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        die.setPos(eye.x + look.x * 0.4, eye.y - 0.15, eye.z + look.z * 0.4);
        die.throwFrom(player);
        world.addFreshEntity(die);

        IN_FLIGHT.put(player.getUUID(), die.getId());
        IN_FLIGHT_DEADLINE.put(player.getUUID(), world.getGameTime() + THROW_TIMEOUT_TICKS);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS,
                0.4f, 1.6f);
        sync(player);
    }

    /**
     * Called by {@link DiceEntity} once the die has physically come to rest and its top face has
     * been read. REPLACES the budget — see the class javadoc for why.
     */
    public static void onDiceSettled(ServerPlayer player, int pips) {
        if (!active || player == null) return;
        UUID uuid = player.getUUID();
        setBudget(uuid, pips);
        LAST_ROLL.put(uuid, pips);
        clearInFlight(uuid);
        ANCHOR.put(uuid, player.position());
        SNAP_COOLDOWN.remove(uuid);
        // The freeze no longer uses a status effect, but a world saved by an older build can still
        // carry the old amplifier-6 Slowness, and it would keep ticking forever because nothing
        // refreshes or clears it any more. Amplifier 6 was only ever ours — Heavy Pockets tops out
        // at 5 — so clearing it here migrates those worlds and touches nothing else.
        MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
        if (slowness != null && slowness.getAmplifier() >= 6) {
            player.removeEffect(MobEffects.SLOWNESS);
        }

        player.sendOverlayMessage(Component.translatable("challengecraft.dice.rolled", pips)
                .withStyle(ChatFormatting.GOLD));
        player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 1.4f);
        sync(player);
    }

    // ---- per-tick accounting --------------------------------------------------------------------

    private static void tickPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (player.isCreative() || player.isSpectator()) {
            LAST_POS.put(uuid, player.position());
            return;
        }

        pruneStaleThrow(player, uuid);

        Vec3 now = player.position();
        Vec3 last = LAST_POS.get(uuid);
        LAST_POS.put(uuid, now);

        if (last == null || last.distanceToSqr(now) > TELEPORT_THRESHOLD_SQ) {
            // Portal, /tp, respawn or dimension change — never bill the player for it.
            ANCHOR.put(uuid, now);
            return;
        }

        double budget = getBudget(uuid);
        double dx = now.x - last.x;
        double dz = now.z - last.z;
        double walked = Math.sqrt(dx * dx + dz * dz);

        boolean changed = false;
        if (walked > 1.0e-4 && budget > 0.0) {
            budget = Math.max(0.0, budget - walked);
            setBudget(uuid, budget);
            changed = true;
        }

        if (budget > 0.0) {
            ANCHOR.put(uuid, now);
            SNAP_COOLDOWN.remove(uuid);
        } else {
            enforce(player, uuid, now);
        }

        if (changed || player.tickCount % 10 == 0) {
            sync(player);
        }
    }

    /** Server-side backstop for a player with no budget left. */
    private static void enforce(ServerPlayer player, UUID uuid, Vec3 now) {
        // Elytra: travelGliding derives velocity from the look vector, so zeroing the movement
        // input does nothing at all while gliding — the flight has to be ended outright.
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
        // Kills residual momentum and ice sliding directly instead of through a status effect.
        //
        // This used to be Slowness at amplifier 6. It worked, but Minecraft derives the field of
        // view from movement speed — `modifier *= (speed / walkingSpeed + 1) / 2` — so a speed of
        // zero halved the FOV and the view snapped inward every time the budget ran out, which is
        // most of this challenge. Zeroing the velocity has exactly the same effect on movement and
        // none at all on the camera. It also leaves the potion HUD alone, so nothing looks like a
        // debuff the player could cure.
        //
        // Vertical velocity is deliberately preserved: falling still has to work, and the challenge
        // only ever counted horizontal distance anyway.
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.x != 0.0 || velocity.z != 0.0) {
            player.setDeltaMovement(0.0, velocity.y, 0.0);
            player.syncVelocity = true;   // 26.3: Entity.hurtMarked renamed to syncVelocity; ServerEntity still reads this to force a ClientboundSetEntityMotionPacket down to the client, which owns its own position
        }

        Vec3 anchor = ANCHOR.get(uuid);
        if (anchor == null) {
            ANCHOR.put(uuid, now);
            return;
        }

        double dx = now.x - anchor.x;
        double dz = now.z - anchor.z;
        if (dx * dx + dz * dz <= DRIFT_TOLERANCE * DRIFT_TOLERANCE) {
            return;
        }

        // requestTeleport stores a pending position and makes onPlayerMove DISCARD all client
        // movement until the client confirms the matching teleport id. Calling it every tick
        // churns that id so confirmation never matches, leaving the player in a permanently
        // movement-ignored state — hence the cooldown.
        int cooldown = SNAP_COOLDOWN.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            SNAP_COOLDOWN.put(uuid, cooldown - 1);
            return;
        }
        SNAP_COOLDOWN.put(uuid, SNAP_COOLDOWN_TICKS);
        player.teleportTo(anchor.x, now.y, anchor.z);
    }

    // ---- sync ------------------------------------------------------------------------------------

    public static void sync(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ServerPlayNetworking.send(player, new DiceSyncPacket(
                (float) getBudget(uuid),
                LAST_ROLL.getOrDefault(uuid, 0),
                hasDieInFlight(uuid)));
    }
}
