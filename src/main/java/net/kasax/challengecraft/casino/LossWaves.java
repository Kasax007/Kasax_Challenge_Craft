package net.kasax.challengecraft.casino;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "The House sends its regards." Lost bets are not forgotten: every net loss goes on the player's
 * loss meter, and once they stop playing (ten quiet seconds, or a minute after the first loss at the
 * latest) the meter is paid out in monsters around them.
 *
 * <p>The size of a wave follows the loss <b>relative to the current fee</b>, so the punishment grows
 * with the run instead of being trivial late or brutal early. Wins in the same session pay the
 * meter down first. Never spawned: wither, warden, dragon, elder guardian. Casino mobs are tagged
 * and drop no loot (see {@code CasinoMobDropsMixin}) — otherwise losing to a vindicator wave would
 * pay out emeralds.
 */
public final class LossWaves {
    public static final String TAG = "challengecraft_casino";
    private static final int QUIET_TICKS = 200;
    private static final int MAX_WAIT_TICKS = 1200;

    private static final String[] TIER1 = {"zombie", "skeleton", "spider"};
    private static final String[] TIER2 = {"zombie", "skeleton", "spider", "creeper", "husk", "stray"};
    private static final String[] TIER3 = {"zombie", "skeleton", "creeper", "witch", "vindicator", "pillager"};
    private static final String[] TIER4 = {"vindicator", "pillager", "witch", "evoker", "creeper"};
    private static final String[] NETHER = {"piglin_brute", "blaze", "wither_skeleton", "magma_cube"};
    private static final String[] END = {"enderman", "endermite"};
    /** Undead that catch fire in daylight. */
    private static final Set<String> SUN_BURNERS = Set.of("zombie", "skeleton", "stray");

    private LossWaves() {
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        CasinoSavedData data = CasinoSavedData.get(server);
        if (data.isBankrupt()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CasinoAccount a = data.existing(player.getUUID());
            if (a == null || a.lossMeter <= 0) continue;
            boolean quiet = now - a.lastBetTick >= QUIET_TICKS;
            boolean overdue = now - a.lossStartTick >= MAX_WAIT_TICKS;
            if (!quiet && !overdue) continue;
            if (BlackjackRevival.inLimbo(player.getUUID()) || player.isSpectator() || player.isCreative()) {
                a.lossMeter = 0;
                continue;
            }
            long loss = a.lossMeter;
            a.lossMeter = 0;
            long reference = Math.max(256 * CasinoAccount.CENTI,
                    CasinoEconomy.fee(server, CasinoEconomy.nextFeeIndex(server)));
            spawnWave(player, (double) loss / reference, loss);
        }
    }

    private static void spawnWave(ServerPlayer player, double ratio, long loss) {
        if (ratio < 0.05) return;
        int tier = ratio < 0.25 ? 1 : ratio < 1.0 ? 2 : ratio < 4.0 ? 3 : 4;
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = level.getRandom();
        int count = switch (tier) {
            case 1 -> 2;
            case 2 -> 3 + random.nextInt(2);
            case 3 -> 5 + random.nextInt(2);
            default -> 6 + random.nextInt(3);
        };
        String[] pool = tier == 1 ? TIER1 : tier == 2 ? TIER2 : tier == 3 ? TIER3 : TIER4;
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] from = pool;
            if (level.dimension() == Level.NETHER && tier >= 2 && random.nextFloat() < 0.5f) from = NETHER;
            if (level.dimension() == Level.END && random.nextFloat() < 0.6f) from = END;
            picks.add(from[random.nextInt(from.length)]);
        }
        if (tier == 4) picks.add("ravager"); // exactly one — the House is angry, not insane

        int spawned = 0;
        for (String id : picks) {
            BlockPos at = findSpot(level, player.blockPosition(), random);
            if (at == null) continue;
            if (spawn(level, id, at, player)) spawned++;
        }
        if (spawned == 0) return;

        Component title = Component.translatable("challengecraft.casino.wave.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("challengecraft.casino.wave.subtitle", CasinoEconomy.formatFull(loss))
                .withStyle(ChatFormatting.GRAY);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), CasinoSounds.HOUSE_SENDS, SoundSource.HOSTILE, 1.2f, 1.0f);
        ServerPlayNetworking.send(player, new CasinoNet.Fx(CasinoNet.Fx.WAVE, loss, Integer.toString(tier), 0L));
    }

    private static boolean spawn(ServerLevel level, String id, BlockPos at, ServerPlayer target) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:" + id));
        if (type == null) return false;
        Entity e = type.create(level, EntitySpawnReason.EVENT);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return false;
        }
        mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
        if (SUN_BURNERS.contains(id) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            // A wave lost at noon must not simply burn away; the helmet never drops.
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0f);
        }
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        mob.setTarget(target);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 18, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(ParticleTypes.PORTAL, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.4);
        return true;
    }

    /** A free, floor-backed spot 5–10 blocks from the player, searched near their own height. */
    private static BlockPos findSpot(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 5 + random.nextDouble() * 5;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = 4; dy >= -5; dy--) {
                BlockPos p = new BlockPos(x, center.getY() + dy, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                    return p;
                }
            }
        }
        return null;
    }
}
