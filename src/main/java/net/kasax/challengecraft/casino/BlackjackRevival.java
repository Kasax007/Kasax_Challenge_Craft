package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.challenges.Chal_21_Hardcore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "The House offers you a game." Instead of dying, a player plays one hand of blackjack against
 * the croupier:
 * <ul>
 *   <li><b>win</b> — they are back exactly where they fell (or the nearest safe spot, should that
 *       be lava, a wall or the void), healed, with their complete inventory and experience;</li>
 *   <li><b>lose</b> — they die exactly where they fell, items drop there, as if nothing happened;</li>
 *   <li><b>push</b> — a new hand is dealt.</li>
 * </ul>
 * The House keeps 25 % of the chips on every death either way. Under Hardcore there is no hand.
 *
 * <h2>At the counter</h2>
 * The hand is played for real at the croupier's booth: the player is taken to a free seat in front
 * of counter 0 or 2 (the middle one holds the deposit tray), the croupier deals the cards onto the
 * counter and the player right-clicks the plaques HIT / STAND / DOUBLE / SPLIT on it. Other players
 * nearby watch the same cards. While the hand runs the player cannot leave the seat, cannot be hurt
 * and has nothing in their hands: the inventory is held by the House and comes back before the
 * outcome — on a loss the player is first put back where they fell, so everything drops there.
 * Leaving the game mid-hand loses it. Should both seats be taken (or the booth be missing), the hand
 * is played where the player fell, in the blackjack screen (kept for exactly that).
 *
 * <p>Mechanics: the fatal hit is intercepted with Fabric's {@code ALLOW_DEATH} (which fires before
 * totems, so a held totem is simply allowed to do its job). A win revives the player in place, like
 * a totem would. Only when there is no safe spot near the place of death (fell into the void, say)
 * does it stash the inventory, let the death happen with an empty inventory and restore everything
 * in {@code AFTER_RESPAWN}, so vanilla's respawn picks the spawn point, beds and anchors included.
 */
public final class BlackjackRevival {
    public static final int PLAYING = 0, RESULT = 1;
    public static final int LOSE = 0, WIN = 1, PUSH = 2;
    /** Plaque order on the counter. */
    public static final int[] ACTIONS = {BlackjackTable.HIT, BlackjackTable.STAND, BlackjackTable.DOUBLE, BlackjackTable.SPLIT};
    /** Counters a hand can be played at; the middle one is the deposit tray. */
    private static final int[] SEATS = {0, 2};
    private static final int DECISION_TICKS = 20 * 20;
    private static final int RESULT_TICKS_SCREEN = 70;
    /** At the counter the dealer's cards are dealt one by one before the outcome shows. */
    private static final int RESULT_TICKS_COUNTER = 120;
    private static final double VIEW_RADIUS = 32.0;

    private static final class Limbo {
        BlackjackTable table;
        final String name;
        final DamageSource source;
        final ResourceKey<Level> deathLevel;
        final Vec3 deathPos;
        final float yRot;
        final float xRot;
        final long lostChips;
        final int seat;
        final Vec3 spot;
        final List<ItemStack> held;
        int ticksLeft = DECISION_TICKS;
        int phase = PLAYING;
        int outcome = -1;
        int serial;

        Limbo(BlackjackTable table, ServerPlayer player, DamageSource source, long lostChips, int seat, Vec3 spot,
              List<ItemStack> held) {
            this.table = table;
            this.name = player.getName().getString();
            this.source = source;
            this.deathLevel = player.level().dimension();
            this.deathPos = player.position();
            this.yRot = player.getYRot();
            this.xRot = player.getXRot();
            this.lostChips = lostChips;
            this.seat = seat;
            this.spot = spot;
            this.held = held;
        }

        boolean atCounter() {
            return seat >= 0;
        }
    }

    private record Stash(List<ItemStack> items, int xpLevel, float xpProgress, int totalXp) {
    }

    private static final Map<UUID, Limbo> LIMBO = new HashMap<>();
    private static final Map<UUID, Stash> STASH = new HashMap<>();
    private static final Set<UUID> LET_DIE = new HashSet<>();

    private BlackjackRevival() {
    }

    public static boolean inLimbo(UUID uuid) {
        return LIMBO.containsKey(uuid);
    }

    /** The counter a player's hand is played at, or -1. */
    public static int seatOf(UUID uuid) {
        Limbo l = LIMBO.get(uuid);
        return l == null ? -1 : l.seat;
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || !CasinoGames.active()) return true;
            UUID id = player.getUUID();
            if (LET_DIE.remove(id)) return true;
            if (LIMBO.containsKey(id)) {
                player.setHealth(1.0f);
                return false;
            }
            MinecraftServer server = player.level().getServer();
            if (CasinoSavedData.get(server).isBankrupt()) return true;
            // A totem saves the player on its own, so this is not a death the House can tax.
            if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
            long tax = CasinoEconomy.deathTax(player);
            if (Chal_21_Hardcore.isActive()) return true;

            player.setHealth(1.0f);
            player.clearFire();
            player.setDeltaMovement(Vec3.ZERO);
            start(player, source, tax);
            return false;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer p) || !LIMBO.containsKey(p.getUUID()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Stash stash = STASH.remove(newPlayer.getUUID());
            if (stash == null) return;
            restore(newPlayer, stash.items);
            newPlayer.setExperienceLevels(stash.xpLevel);
            newPlayer.experienceProgress = stash.xpProgress;
            newPlayer.totalExperience = stash.totalXp;
            ServerLevel level = (ServerLevel) newPlayer.level();
            level.broadcastEntityEvent(newPlayer, (byte) 35); // the totem-of-undying flourish
            level.playSound(null, newPlayer.getX(), newPlayer.getY(), newPlayer.getZ(), CasinoSounds.REVIVE,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            newPlayer.sendSystemMessage(Component.translatable("challengecraft.casino.blackjack.revived").withStyle(ChatFormatting.GOLD));
        });
    }

    // ---- the start of a hand ------------------------------------------------------------------

    private static int freeSeat(MinecraftServer server) {
        BlockPos a = CasinoBooth.anchorOf(server);
        if (a == null || CasinoBooth.croupier(server) == null) return -1;
        ServerLevel overworld = server.overworld();
        for (int seat : SEATS) {
            boolean taken = false;
            for (Limbo l : LIMBO.values()) taken |= l.seat == seat;
            if (!taken && overworld.getBlockState(CasinoBooth.counter(a, seat)).getBlock() instanceof CasinoDeviceBlock) return seat;
        }
        return -1;
    }

    private static void start(ServerPlayer player, DamageSource source, long tax) {
        MinecraftServer server = player.level().getServer();
        int seat = freeSeat(server);
        Vec3 spot = seat >= 0 ? CasinoBooth.customerSpot(CasinoBooth.anchorOf(server), seat) : player.position();
        List<ItemStack> held = null;
        if (seat >= 0) {
            // The House holds the inventory while the hand runs: nothing to drop, throw or use.
            held = new ArrayList<>();
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                held.add(inv.getItem(i).copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
            inv.setChanged();
        }
        Limbo limbo = new Limbo(new BlackjackTable(player.level().getRandom()), player, source, tax, seat, spot, held);
        LIMBO.put(player.getUUID(), limbo);
        if (seat >= 0) {
            ServerLevel from = (ServerLevel) player.level();
            from.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            player.teleportTo(server.overworld(), spot.x, spot.y, spot.z, Set.of(), -90f, 38f, false);
            server.overworld().sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1, spot.z, 40, 0.3, 0.8, 0.3, 0.3);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 40, 15));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("challengecraft.casino.blackjack.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("challengecraft.casino.blackjack.subtitle")
                    .withStyle(ChatFormatting.GRAY)));
            CasinoBooth.gesture(server, CroupierEntity.GESTURE_WAVE, seat);
            CasinoBooth.gestureLater(server, CroupierEntity.GESTURE_DEAL, seat, 16);
        }
        if (limbo.table.finished) finish(player, limbo);
        send(player, limbo);
        playCard(player, limbo, CasinoSounds.CARD_SLIDE);
        ChallengeCraft.LOGGER.info("[Casino] {} plays blackjack for their life ({})", player.getName().getString(),
                seat >= 0 ? "counter " + seat : "screen");
    }

    // ---- playing ------------------------------------------------------------------------------

    public static void act(ServerPlayer player, int action) {
        Limbo limbo = LIMBO.get(player.getUUID());
        if (limbo == null || limbo.phase != PLAYING) return;
        int dealerBefore = limbo.table.dealer.size();
        if (!limbo.table.act(action)) {
            if (limbo.atCounter()) {
                player.sendOverlayMessage(Component.translatable("challengecraft.casino.blackjack.not_allowed").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        limbo.ticksLeft = DECISION_TICKS;
        MinecraftServer server = player.level().getServer();
        if (limbo.atCounter()) {
            if (action != BlackjackTable.STAND) CasinoBooth.gesture(server, CroupierEntity.GESTURE_DEAL, limbo.seat);
        } else {
            playCard(player, limbo, action == BlackjackTable.STAND ? CasinoSounds.CHIP : CasinoSounds.CARD_PLACE);
        }
        if (limbo.table.finished) {
            finish(player, limbo);
            if (limbo.atCounter()) {
                // The croupier turns his card, draws what he must, then settles.
                int drawn = Math.max(0, limbo.table.dealer.size() - dealerBefore);
                CasinoBooth.gestureLater(server, CroupierEntity.GESTURE_FLIP, limbo.seat, 6);
                for (int i = 0; i < drawn; i++) {
                    CasinoBooth.gestureLater(server, CroupierEntity.GESTURE_DEAL, limbo.seat, 24 + i * 12);
                }
                int settle = 30 + drawn * 12;
                CasinoBooth.gestureLater(server, limbo.outcome == WIN ? CroupierEntity.GESTURE_PAY
                        : limbo.outcome == LOSE ? CroupierEntity.GESTURE_COLLECT : CroupierEntity.GESTURE_TIP_HAT, limbo.seat, settle);
            }
        }
        send(player, limbo);
    }

    private static void finish(ServerPlayer player, Limbo limbo) {
        limbo.phase = RESULT;
        limbo.outcome = limbo.table.net > 0 ? WIN : limbo.table.net < 0 ? LOSE : PUSH;
        limbo.ticksLeft = limbo.atCounter() ? RESULT_TICKS_COUNTER : RESULT_TICKS_SCREEN;
    }

    public static void tick(MinecraftServer server) {
        if (LIMBO.isEmpty()) return;
        for (Map.Entry<UUID, Limbo> e : new ArrayList<>(LIMBO.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Limbo limbo = e.getValue();
            if (player == null) {
                LIMBO.remove(e.getKey());
                continue;
            }
            // Held in place (they may look around), and nothing may touch them.
            if (player.position().distanceToSqr(limbo.spot) > 0.01) {
                player.teleportTo(limbo.spot.x, limbo.spot.y, limbo.spot.z);
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.clearFire();
            player.setAirSupply(player.getMaxAirSupply());
            if (player.getHealth() < 1.0f) player.setHealth(1.0f);

            limbo.ticksLeft--;
            if (limbo.phase == PLAYING) {
                if (limbo.ticksLeft <= 0) {
                    player.sendOverlayMessage(Component.translatable("challengecraft.casino.blackjack.auto_stand").withStyle(ChatFormatting.GRAY));
                    act(player, BlackjackTable.STAND);
                } else if (limbo.ticksLeft % 20 == 0) {
                    send(player, limbo);
                }
                continue;
            }
            if (limbo.ticksLeft > 0) continue;
            if (limbo.outcome == PUSH) {
                limbo.table = new BlackjackTable(player.level().getRandom());
                limbo.phase = PLAYING;
                limbo.outcome = -1;
                limbo.ticksLeft = DECISION_TICKS;
                if (limbo.table.finished) finish(player, limbo);
                if (limbo.atCounter()) CasinoBooth.gesture(server, CroupierEntity.GESTURE_DEAL, limbo.seat);
                playCard(player, limbo, CasinoSounds.CARD_SLIDE);
                send(player, limbo);
                continue;
            }
            resolve(player, limbo);
        }
    }

    // ---- the outcome --------------------------------------------------------------------------

    private static void resolve(ServerPlayer player, Limbo limbo) {
        UUID id = player.getUUID();
        LIMBO.remove(id);
        CasinoAccount account = CasinoEconomy.account(player);
        MinecraftServer server = player.level().getServer();
        sendClosed(player, limbo);
        if (limbo.held != null) restore(player, limbo.held);
        if (limbo.outcome == WIN) {
            account.revivalsWon++;
            ServerLevel level = deathLevel(server, limbo);
            Vec3 back = safeSpot(level, limbo.deathPos);
            if (back != null) {
                CasinoSavedData.get(server).touch();
                reviveAt(player, level, back, limbo);
                return;
            }
            Inventory inv = player.getInventory();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                items.add(inv.getItem(i).copy());
                inv.setItem(i, ItemStack.EMPTY);
            }
            STASH.put(id, new Stash(items, player.experienceLevel, player.experienceProgress, player.totalExperience));
            player.setExperienceLevels(0);
            player.experienceProgress = 0f;
            player.totalExperience = 0;
            ServerPlayNetworking.send(player, new CasinoNet.Fx(CasinoNet.Fx.REVIVED, 0, "", 0L));
        } else {
            account.revivalsLost++;
            // Back to where they fell: the items drop there, not at the booth.
            if (limbo.atCounter()) backToDeath(player, limbo);
            ServerLevel level = (ServerLevel) player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), CasinoSounds.BUST, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        CasinoSavedData.get(server).touch();
        kill(player, limbo.source);
    }

    private static ServerLevel deathLevel(MinecraftServer server, Limbo limbo) {
        ServerLevel level = server.getLevel(limbo.deathLevel);
        return level == null ? server.overworld() : level;
    }

    private static void backToDeath(ServerPlayer player, Limbo limbo) {
        ServerLevel level = deathLevel(player.level().getServer(), limbo);
        player.teleportTo(level, limbo.deathPos.x, limbo.deathPos.y, limbo.deathPos.z, Set.of(), limbo.yRot, limbo.xRot, false);
    }

    /**
     * Where a winner comes back: the exact place of death if it is survivable, otherwise the
     * nearest spot around it (a few blocks) with ground underfoot and nothing harmful, or null.
     */
    private static Vec3 safeSpot(ServerLevel level, Vec3 death) {
        if (death.y < level.getMinY()) return null; // the void: nothing to stand on
        BlockPos at = BlockPos.containing(death);
        if (survivable(level, at)) return death;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -3; dy <= 6; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos p = at.offset(dx, dy, dz);
                    double d = p.distToCenterSqr(death);
                    if (d >= bestDist || !survivable(level, p)) continue;
                    if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
                    best = p;
                    bestDist = d;
                }
            }
        }
        return best == null ? null : Vec3.atBottomCenterOf(best);
    }

    /** Room for a player at feet position {@code p}, and nothing there that burns (water is fine: the air is refilled). */
    private static boolean survivable(ServerLevel level, BlockPos p) {
        if (p.getY() < level.getMinY() || p.getY() + 1 > level.getMaxY()) return false;
        for (BlockPos q : new BlockPos[]{p, p.above()}) {
            BlockState state = level.getBlockState(q);
            if (!state.getCollisionShape(level, q).isEmpty() || state.getFluidState().is(FluidTags.LAVA)) return false;
            if (state.is(BlockTags.FIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB)
                    || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)) return false;
        }
        BlockState below = level.getBlockState(p.below());
        return !below.is(Blocks.MAGMA_BLOCK) && !below.is(Blocks.LAVA) && !below.is(BlockTags.CAMPFIRES)
                && !below.is(Blocks.CACTUS);
    }

    /** A won hand: back on their feet where they fell, like a totem would do it. */
    private static void reviveAt(ServerPlayer player, ServerLevel level, Vec3 at, Limbo limbo) {
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), limbo.yRot, limbo.xRot, false);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.removeAllEffects(); // as a respawn would: no poison or wither to finish the job
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        // A short grace so whatever killed them (a mob, a fall of sparks) cannot do it at once again.
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        level.broadcastEntityEvent(player, (byte) 35); // the totem-of-undying flourish
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 40, 0.3, 0.8, 0.3, 0.3);
        level.playSound(null, at.x, at.y, at.z, CasinoSounds.REVIVE, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.sendSystemMessage(Component.translatable("challengecraft.casino.blackjack.revived").withStyle(ChatFormatting.GOLD));
    }

    private static void restore(ServerPlayer player, List<ItemStack> items) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < items.size() && i < inv.getContainerSize(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, s);
            } else if (!inv.add(s) && !s.isEmpty()) {
                ServerLevel at = (ServerLevel) player.level();
                at.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(at, player.getX(), player.getY() + 0.5,
                        player.getZ(), s));
            }
        }
        inv.setChanged();
    }

    private static void kill(ServerPlayer player, DamageSource source) {
        LET_DIE.add(player.getUUID());
        ServerLevel level = (ServerLevel) player.level();
        player.hurtServer(level, source, Float.MAX_VALUE);
        if (player.isAlive() && player.getHealth() > 0) {
            // The original cause may no longer apply (fire resistance, left the lava…): end it plainly.
            LET_DIE.add(player.getUUID());
            player.kill(level);
        }
        LET_DIE.remove(player.getUUID());
    }

    /** Leaving mid-hand counts as a lost hand. */
    public static void onLeave(ServerPlayer player) {
        Limbo limbo = LIMBO.get(player.getUUID());
        if (limbo == null) return;
        limbo.outcome = LOSE;
        resolve(player, limbo);
    }

    /**
     * The server is stopping mid-hand: nobody loses anything to that. The inventory comes back and
     * the player is put back where they fell, alive.
     */
    public static void abortAll(MinecraftServer server) {
        for (Map.Entry<UUID, Limbo> e : new ArrayList<>(LIMBO.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Limbo limbo = e.getValue();
            if (player == null) continue;
            if (limbo.held != null) restore(player, limbo.held);
            if (limbo.atCounter()) backToDeath(player, limbo);
        }
        LIMBO.clear();
    }

    // ---- packets and sounds -------------------------------------------------------------------

    private static void playCard(ServerPlayer player, Limbo limbo, net.minecraft.sounds.SoundEvent sound) {
        // At the counter every card sounds where it lands, on the watchers' clients.
        if (limbo.atCounter()) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static List<ServerPlayer> audience(ServerPlayer player, Limbo limbo) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(player);
        if (!limbo.atCounter()) return out;
        for (ServerPlayer p : CasinoGames.near(player.level().getServer().overworld(),
                BlockPos.containing(limbo.spot), VIEW_RADIUS)) {
            if (p != player) out.add(p);
        }
        return out;
    }

    private static void send(ServerPlayer player, Limbo limbo) {
        BlackjackTable t = limbo.table;
        int[] dealer = new int[t.dealer.size()];
        for (int i = 0; i < dealer.length; i++) {
            dealer[i] = (i == 1 && !t.dealerRevealed) ? -1 : t.dealer.get(i);
        }
        List<Integer> flat = new ArrayList<>();
        for (BlackjackTable.Hand h : t.hands) {
            flat.add(h.cards.size());
            flat.add(h.stake);
            flat.add(h.done ? 1 : 0);
            flat.addAll(h.cards);
        }
        int[] hands = flat.stream().mapToInt(Integer::intValue).toArray();
        CasinoNet.Blackjack packet = new CasinoNet.Blackjack(limbo.name, limbo.seat, limbo.phase, dealer, hands, t.active,
                t.allowed(), limbo.outcome, Math.max(0, limbo.ticksLeft), limbo.lostChips, ++limbo.serial);
        for (ServerPlayer p : audience(player, limbo)) ServerPlayNetworking.send(p, packet);
    }

    /** Phase 2 tells the clients the hand is over. */
    private static void sendClosed(ServerPlayer player, Limbo limbo) {
        CasinoNet.Blackjack packet = new CasinoNet.Blackjack(limbo.name, limbo.seat, 2, new int[0], new int[0], 0, 0,
                limbo.outcome, 0, limbo.lostChips, ++limbo.serial);
        for (ServerPlayer p : audience(player, limbo)) ServerPlayNetworking.send(p, packet);
    }

    public static void reset() {
        LIMBO.clear();
        STASH.clear();
        LET_DIE.clear();
    }
}
