package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.challenges.Chal_21_Hardcore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 *   <li><b>win</b> — they respawn at their own spawn point (bed, anchor or world spawn) with their
 *       complete inventory and experience;</li>
 *   <li><b>lose</b> — they die exactly where they fell, items drop there, as if nothing happened;</li>
 *   <li><b>push</b> — a new hand is dealt.</li>
 * </ul>
 * The House keeps 25 % of the chips on every death either way. Under Hardcore there is no hand.
 *
 * <p>Mechanics: the fatal hit is intercepted with Fabric's {@code ALLOW_DEATH} (which fires before
 * totems, so a held totem is simply allowed to do its job). While the hand is played the player is
 * frozen in place and immune to damage. A win stashes the inventory, lets the death happen with an
 * empty inventory and restores everything in {@code AFTER_RESPAWN} — that way vanilla's own respawn
 * logic picks the spawn point, including beds and anchors, instead of this class guessing it.
 */
public final class BlackjackRevival {
    public static final int PLAYING = 0, RESULT = 1;
    public static final int LOSE = 0, WIN = 1, PUSH = 2;
    private static final int DECISION_TICKS = 20 * 20;
    private static final int RESULT_TICKS = 70;

    private static final class Limbo {
        BlackjackTable table;
        final DamageSource source;
        final Vec3 pos;
        final float yRot;
        final float xRot;
        final long lostChips;
        int ticksLeft = DECISION_TICKS;
        int phase = PLAYING;
        int outcome = -1;
        int serial;

        Limbo(BlackjackTable table, DamageSource source, Vec3 pos, float yRot, float xRot, long lostChips) {
            this.table = table;
            this.source = source;
            this.pos = pos;
            this.yRot = yRot;
            this.xRot = xRot;
            this.lostChips = lostChips;
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
            Limbo limbo = new Limbo(new BlackjackTable(player.level().getRandom()), source, player.position(),
                    player.getYRot(), player.getXRot(), tax);
            LIMBO.put(id, limbo);
            if (limbo.table.finished) finish(player, limbo);
            send(player, limbo);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), CasinoSounds.CARD_SLIDE,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            ChallengeCraft.LOGGER.info("[Casino] {} plays blackjack for their life", player.getName().getString());
            return false;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer p) || !LIMBO.containsKey(p.getUUID()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Stash stash = STASH.remove(newPlayer.getUUID());
            if (stash == null) return;
            Inventory inv = newPlayer.getInventory();
            for (int i = 0; i < stash.items.size() && i < inv.getContainerSize(); i++) {
                ItemStack s = stash.items.get(i);
                if (s.isEmpty()) continue;
                if (inv.getItem(i).isEmpty()) inv.setItem(i, s);
                else if (!inv.add(s) && !s.isEmpty()) {
                    ServerLevel at = (ServerLevel) newPlayer.level();
                    at.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(at, newPlayer.getX(), newPlayer.getY() + 0.5,
                            newPlayer.getZ(), s));
                }
            }
            inv.setChanged();
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

    public static void act(ServerPlayer player, int action) {
        Limbo limbo = LIMBO.get(player.getUUID());
        if (limbo == null || limbo.phase != PLAYING) return;
        if (!limbo.table.act(action)) return;
        limbo.ticksLeft = DECISION_TICKS;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                action == BlackjackTable.STAND ? CasinoSounds.CHIP : CasinoSounds.CARD_PLACE, SoundSource.PLAYERS, 0.8f, 1.0f);
        if (limbo.table.finished) finish(player, limbo);
        send(player, limbo);
    }

    private static void finish(ServerPlayer player, Limbo limbo) {
        limbo.phase = RESULT;
        limbo.outcome = limbo.table.net > 0 ? WIN : limbo.table.net < 0 ? LOSE : PUSH;
        limbo.ticksLeft = RESULT_TICKS;
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
            // Frozen where they fell, and nothing may touch them.
            player.teleportTo(limbo.pos.x, limbo.pos.y, limbo.pos.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.clearFire();
            player.setAirSupply(player.getMaxAirSupply());
            if (player.getHealth() < 1.0f) player.setHealth(1.0f);

            limbo.ticksLeft--;
            if (limbo.phase == PLAYING) {
                if (limbo.ticksLeft <= 0) act(player, BlackjackTable.STAND);
                else if (limbo.ticksLeft % 20 == 0) send(player, limbo);
                continue;
            }
            if (limbo.ticksLeft > 0) continue;
            if (limbo.outcome == PUSH) {
                limbo.table = new BlackjackTable(player.level().getRandom());
                limbo.phase = PLAYING;
                limbo.outcome = -1;
                limbo.ticksLeft = DECISION_TICKS;
                if (limbo.table.finished) finish(player, limbo);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), CasinoSounds.CARD_SLIDE,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                send(player, limbo);
                continue;
            }
            resolve(player, limbo);
        }
    }

    private static void resolve(ServerPlayer player, Limbo limbo) {
        UUID id = player.getUUID();
        LIMBO.remove(id);
        CasinoAccount account = CasinoEconomy.account(player);
        ServerLevel level = (ServerLevel) player.level();
        if (limbo.outcome == WIN) {
            account.revivalsWon++;
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
            level.playSound(null, player.getX(), player.getY(), player.getZ(), CasinoSounds.BUST, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        CasinoSavedData.get(level.getServer()).touch();
        sendClosed(player, limbo);
        kill(player, limbo.source);
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
        ServerPlayNetworking.send(player, new CasinoNet.Blackjack(limbo.phase, dealer, hands, t.active, t.allowed(),
                limbo.outcome, Math.max(0, limbo.ticksLeft), limbo.lostChips, ++limbo.serial));
    }

    /** Phase 2 tells the client to close the table. */
    private static void sendClosed(ServerPlayer player, Limbo limbo) {
        ServerPlayNetworking.send(player, new CasinoNet.Blackjack(2, new int[0], new int[0], 0, 0, limbo.outcome, 0,
                limbo.lostChips, ++limbo.serial));
    }

    public static void reset() {
        LIMBO.clear();
        STASH.clear();
        LET_DIE.clear();
    }
}
