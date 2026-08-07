package net.kasax.challengecraft.challenges;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.entity.DiceEntity;
import net.kasax.challengecraft.entity.ModEntities;
import net.kasax.challengecraft.item.ModItems;
import net.kasax.challengecraft.network.DiceSyncPacket;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

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
    private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();
    /** Where an out-of-budget player is held. */
    private static final Map<UUID, Vec3d> ANCHOR = new HashMap<>();
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
            if (!active) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(ModItems.DICE)) return ActionResult.PASS;
            if (world.isClient()) return ActionResult.SUCCESS;

            if (player instanceof ServerPlayerEntity serverPlayer) {
                throwDie(serverPlayer);
                return ActionResult.SUCCESS_SERVER;
            }
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!active) return;
            tickCounter++;
            // Scanning every inventory every tick would be wasteful; once a second is plenty.
            boolean handOutDice = tickCounter % 20 == 0;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
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
    private static void ensureDice(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        int firstSlot = -1;
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.getStack(i).isOf(ModItems.DICE)) continue;
            if (firstSlot == -1) {
                firstSlot = i;
            } else {
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
        if (firstSlot == -1) {
            inventory.insertStack(new ItemStack(ModItems.DICE));
        }
    }

    // ---- activation ----------------------------------------------------------------------------

    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            SERVER_BUDGET.clear();
            LAST_ROLL.clear();
            IN_FLIGHT.clear();
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
    public static double getBudgetFor(PlayerEntity player) {
        if (player.getWorld().isClient) {
            // travel() only runs for the main player on a client, but be explicit: never clamp
            // another player's puppet from local state.
            return player.isMainPlayer() ? clientRemaining : Double.MAX_VALUE;
        }
        return getBudget(player.getUuid());
    }

    public static void setClientRemaining(double value) {
        clientRemaining = value;
    }

    public static boolean hasDieInFlight(UUID player) {
        return IN_FLIGHT.containsKey(player);
    }

    public static void clearInFlight(UUID player) {
        IN_FLIGHT.remove(player);
    }

    // ---- throwing ------------------------------------------------------------------------------

    private static void throwDie(ServerPlayerEntity player) {
        if (hasDieInFlight(player.getUuid())) {
            // The only restriction on throw rate: the previous die must have finished.
            player.sendMessage(Text.translatable("challengecraft.dice.already_rolling")
                    .formatted(Formatting.RED), true);
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        DiceEntity die = new DiceEntity(ModEntities.DICE, world);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        die.setPosition(eye.x + look.x * 0.4, eye.y - 0.15, eye.z + look.z * 0.4);
        die.throwFrom(player);
        world.spawnEntity(die);

        IN_FLIGHT.put(player.getUuid(), die.getId());
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS,
                0.4f, 1.6f);
        sync(player);
    }

    /**
     * Called by {@link DiceEntity} once the die has physically come to rest and its top face has
     * been read. REPLACES the budget — see the class javadoc for why.
     */
    public static void onDiceSettled(ServerPlayerEntity player, int pips) {
        if (!active || player == null) return;
        UUID uuid = player.getUuid();
        setBudget(uuid, pips);
        LAST_ROLL.put(uuid, pips);
        clearInFlight(uuid);
        ANCHOR.put(uuid, player.getPos());
        SNAP_COOLDOWN.remove(uuid);
        // Drop the freeze immediately rather than letting it tick out — the player should be able
        // to walk the instant the die stops. (Heavy Pockets re-applies its own Slowness next tick
        // if that challenge is also running, so clearing here is safe.)
        StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
        if (slowness != null && slowness.getAmplifier() >= 6) {
            player.removeStatusEffect(StatusEffects.SLOWNESS);
        }

        player.sendMessage(Text.translatable("challengecraft.dice.rolled", pips)
                .formatted(Formatting.GOLD), true);
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.5f, 1.4f);
        sync(player);
    }

    // ---- per-tick accounting --------------------------------------------------------------------

    private static void tickPlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (player.isCreative() || player.isSpectator()) {
            LAST_POS.put(uuid, player.getPos());
            return;
        }

        Vec3d now = player.getPos();
        Vec3d last = LAST_POS.get(uuid);
        LAST_POS.put(uuid, now);

        if (last == null || last.squaredDistanceTo(now) > TELEPORT_THRESHOLD_SQ) {
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

        if (changed || player.age % 10 == 0) {
            sync(player);
        }
    }

    /** Server-side backstop for a player with no budget left. */
    private static void enforce(ServerPlayerEntity player, UUID uuid, Vec3d now) {
        // Elytra: travelGliding derives velocity from the look vector, so zeroing the movement
        // input does nothing at all while gliding — the flight has to be ended outright.
        if (player.isGliding()) {
            player.stopGliding();
        }
        // Belt-and-braces on top of the mixin (kills residual momentum / ice sliding): amplifier 6
        // is -1.05 speed, i.e. clamped to 0. Duration is deliberately only 3 ticks and refreshed
        // every tick — a longer one keeps ticking after the budget is restored and the player
        // stays frozen for the remainder, which feels awful right after a roll lands.
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 3, 6, false, false, false));

        Vec3d anchor = ANCHOR.get(uuid);
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
        player.requestTeleport(anchor.x, now.y, anchor.z);
    }

    // ---- sync ------------------------------------------------------------------------------------

    public static void sync(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerPlayNetworking.send(player, new DiceSyncPacket(
                (float) getBudget(uuid),
                LAST_ROLL.getOrDefault(uuid, 0),
                hasDieInFlight(uuid)));
    }
}
