package net.kasax.challengecraft.casino.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoDeviceBlock;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoSounds;
import net.kasax.challengecraft.casino.DeviceSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The show every game puts on for a big win, in four tiers by the win's multiple of the stake:
 * <ol>
 *   <li><b>Big</b> (5×) — gold nuggets spring from the device, sparks at its display;</li>
 *   <li><b>Mega</b> (20×) — the fountain gets emeralds, diamonds and gold, totem sparkles rise;</li>
 *   <li><b>Epic</b> (50×) — fireworks burst above the device;</li>
 *   <li><b>Legendary</b> (200×) — a spiral of light climbs around it, notes fly, and the fireworks
 *       turn into a golden barrage.</li>
 * </ol>
 * Each tier keeps everything of the one below. Purely client side and started from what every
 * viewer receives anyway, so everyone near a device sees the same show. The slot machine plays it
 * from its own timeline (its fanfare is part of that timeline); Plinko, Crash and Roulette start one
 * here with {@link #start}, which also plays the fanfare.
 */
@Environment(EnvType.CLIENT)
public final class WinShows {
    /** Show length per tier: none, big, mega, epic, legendary. */
    public static final int[] TICKS = {0, 30, 50, 70, 110};

    /**
     * Where a show plays, in world coordinates: the mouth of the coin fountain, the display the
     * sparks gather at, the foot of the device (centre, floor level), its radius and its height.
     */
    public record Stage(Vec3 fountain, Vec3 display, Vec3 foot, double radius, double height) {
    }

    /** A show running at a device. */
    public record Show(long pos, int tier, long startTick, long win, String player, Stage stage) {
        public float local(float partial) {
            return CasinoClientState.clientTick - startTick + partial;
        }
    }

    private static final Map<Long, Show> SHOWS = new HashMap<>();

    private WinShows() {
    }

    /** The tier of a win by its multiple of the stake: 5×, 20×, 50× and 200× and up; 0 below. */
    public static int tierFor(long win, long bet) {
        long b = Math.max(1, bet);
        if (win >= b * 200) return 4;
        if (win >= b * 50) return 3;
        if (win >= b * 20) return 2;
        if (win >= b * 5) return 1;
        return 0;
    }

    /** The tier's colours: gold, then flashing gold, then pink and cyan, then all the colours. */
    public static int tierColour(int tier, float local) {
        int blink = (int) (local / 4) % 2;
        return switch (tier) {
            case 1 -> 0xFFFFC53D;
            case 2 -> blink == 0 ? 0xFFFFE27A : 0xFFFF9A2E;
            case 3 -> blink == 0 ? 0xFFFF6FD8 : 0xFF6FE8FF;
            default -> 0xFF000000 | java.awt.Color.HSBtoRGB((local % 30) / 30f, 0.65f, 1f);
        };
    }

    /** "BIG WIN 1 200", "MEGA WIN …" and so on. */
    public static Component label(int tier, long win) {
        return Component.translatable("challengecraft.casino.slot.tier." + tier, CasinoEconomy.format(win));
    }

    /** A stage from device-space points (pixels) of a device standing at {@code master}. */
    public static Stage stage(BlockPos master, double[] fountain, double[] display, double[] foot, double radius, double height) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockState state = level == null ? null : level.getBlockState(master);
        Direction facing = state != null && state.hasProperty(CasinoDeviceBlock.FACING) ? state.getValue(CasinoDeviceBlock.FACING) : Direction.NORTH;
        return new Stage(DeviceSpace.toWorld(master, facing, fountain[0], fountain[1], fountain[2]),
                DeviceSpace.toWorld(master, facing, display[0], display[1], display[2]),
                DeviceSpace.toWorld(master, facing, foot[0], foot[1], foot[2]), radius, height);
    }

    /**
     * Starts a show at a device, unless an equal or bigger one is still running there (two players
     * cashing out at once make one show, the bigger one).
     */
    public static void start(long pos, int tier, long win, String player, Stage stage) {
        if (tier <= 0) return;
        Show running = SHOWS.get(pos);
        if (running != null && running.tier() >= tier && running.local(0) < TICKS[running.tier()]) return;
        SHOWS.put(pos, new Show(pos, tier, CasinoClientState.clientTick, win, player, stage));
    }

    /** The show running at a device, or null. */
    public static Show at(long pos) {
        Show s = SHOWS.get(pos);
        return s != null && s.local(0) < TICKS[s.tier()] ? s : null;
    }

    public static void clear() {
        SHOWS.clear();
    }

    /** Once per client tick: the fanfares and particles of every running show. */
    static void tick(Minecraft client) {
        if (client.level == null || client.player == null) return;
        for (Iterator<Show> it = SHOWS.values().iterator(); it.hasNext(); ) {
            Show s = it.next();
            int local = (int) s.local(0);
            if (local >= TICKS[s.tier()] + 5) {
                it.remove();
                continue;
            }
            if (client.player.distanceToSqr(s.stage().foot()) > 48 * 48) continue;
            sounds(client.level, s, local);
            particles(client.level, client.level.getRandom(), s.tier(), local, s.stage());
        }
    }

    /** The fanfare of a show: grander with every tier, and a shower of coins as long as it lasts. */
    private static void sounds(ClientLevel level, Show s, int local) {
        int tier = s.tier();
        if (local == 0) play(level, s, tier >= 3 ? CasinoSounds.WIN_EPIC : CasinoSounds.WIN_BIG, 0.8f, 1.0f);
        if (tier == 2 && local == 14) play(level, s, CasinoSounds.WIN_BIG, 0.8f, 1.25f);
        if (tier == 4 && local == 40) play(level, s, CasinoSounds.WIN_EPIC, 0.8f, 1.25f);
        if (local >= 6 && local < TICKS[tier] - 4 && (local - 6) % 5 == 0) {
            play(level, s, CasinoSounds.COINS, 0.4f, 0.9f + local * 0.006f);
        }
    }

    private static void play(ClientLevel level, Show s, SoundEvent sound, float volume, float pitch) {
        Vec3 at = s.stage().fountain();
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    private static final ItemParticleOption NUGGET = new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET);
    private static final ItemParticleOption[] GEMS = {
            new ItemParticleOption(ParticleTypes.ITEM, Items.EMERALD),
            new ItemParticleOption(ParticleTypes.ITEM, Items.DIAMOND),
            new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_INGOT)};
    private static final int[] PALETTE = {0xFF5A5A, 0x5AD2FF, 0x7BE07B, 0xFFD24A, 0xD08CFF, 0xFF9A2E};

    /** One tick of a show's particles at tick {@code tick} since it began. */
    static void particles(ClientLevel level, RandomSource r, int tier, int tick, Stage stage) {
        Vec3 f = stage.fountain(), d = stage.display(), foot = stage.foot();

        // 1: a fountain of gold nuggets from the device, sparks around its display.
        for (int i = 0; i < 1 + tier; i++) {
            ParticleOptions coin = tier >= 2 && r.nextInt(4) == 0 ? GEMS[r.nextInt(GEMS.length)] : NUGGET;
            level.addParticle(coin, f.x + (r.nextDouble() - 0.5) * 0.4, f.y, f.z + (r.nextDouble() - 0.5) * 0.4,
                    (r.nextDouble() - 0.5) * 0.22, 0.28 + r.nextDouble() * 0.18, (r.nextDouble() - 0.5) * 0.22);
        }
        if (tick % 2 == 0) {
            level.addParticle(ParticleTypes.WAX_ON, d.x + (r.nextDouble() - 0.5) * 0.8, d.y + r.nextDouble() * 0.3,
                    d.z + (r.nextDouble() - 0.5) * 0.8, 0, 0.05, 0);
        }
        if (tier < 2) return;

        // 2: totem sparkles rising around the device.
        if (tick % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double rr = stage.radius() * 0.75;
                level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, foot.x + Math.cos(a) * rr, foot.y + 0.3 + r.nextDouble() * stage.height() * 0.9,
                        foot.z + Math.sin(a) * rr, Math.cos(a) * 0.08, 0.18, Math.sin(a) * 0.08);
            }
        }
        if (tier < 3) return;

        // 3: fireworks above the device: small shapes, well above it — a large ball this close
        // fills the whole view.
        int every = tier >= 4 ? 8 : 14;
        if (tick % every == 3) {
            FireworkExplosion.Shape shape = switch (r.nextInt(3)) {
                case 0 -> FireworkExplosion.Shape.SMALL_BALL;
                case 1 -> FireworkExplosion.Shape.STAR;
                default -> FireworkExplosion.Shape.BURST;
            };
            IntList colours = tier >= 4 ? IntList.of(0xFFD24A, 0xFFF3A0, 0xE8B83A)
                    : IntList.of(PALETTE[r.nextInt(PALETTE.length)], PALETTE[r.nextInt(PALETTE.length)]);
            level.createFireworks(f.x + (r.nextDouble() - 0.5) * 1.5, f.y + 3.0 + r.nextDouble() * 1.5, f.z + (r.nextDouble() - 0.5) * 1.5,
                    0, 0, 0, List.of(new FireworkExplosion(shape, colours, IntList.of(0xFFFFFF), true, tier >= 4)), false);
        }
        if (tier < 4) return;

        // 4: a spiral of light climbing around the device, and notes flying off it.
        for (int i = 0; i < 2; i++) {
            double a = tick * 0.45 + i * Math.PI;
            double y = foot.y + (tick % 30) / 30.0 * stage.height();
            level.addParticle(ParticleTypes.END_ROD, foot.x + Math.cos(a) * stage.radius(), y, foot.z + Math.sin(a) * stage.radius(), 0, 0.02, 0);
        }
        if (tick % 3 == 0) {
            level.addParticle(ParticleTypes.NOTE, f.x + (r.nextDouble() - 0.5), f.y + 0.3, f.z + (r.nextDouble() - 0.5),
                    r.nextDouble(), 0, 0);
        }
    }
}
