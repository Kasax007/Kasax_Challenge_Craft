package net.kasax.challengecraft.film;

import net.kasax.challengecraft.casino.BlackjackRevival;
import net.kasax.challengecraft.casino.BlackjackTable;
import net.kasax.challengecraft.casino.CasinoBooth;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CasinoRegistry;
import net.kasax.challengecraft.casino.CasinoTestHooks;
import net.kasax.challengecraft.casino.CounterDeposit;
import net.kasax.challengecraft.casino.CrashGame;
import net.kasax.challengecraft.casino.DeviceType;
import net.kasax.challengecraft.casino.PlinkoGame;
import net.kasax.challengecraft.casino.RouletteGame;
import net.kasax.challengecraft.casino.RouletteMath;
import net.kasax.challengecraft.casino.SlotGame;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * The casino short ("I built a casino in Minecraft"): The House Always Wins on a little casino
 * square west of the croupier's booth, at sunset over the sea (seed "lucky-sevens": the machines
 * stand with the setting sun behind them). Outcomes are forced with the casino's own admin hooks
 * ({@code /casino slotforce}, {@link CasinoTestHooks}), and a blackjack hand is dealt from a
 * searched shuffle, so every take shows what the script needs; everything else is the game
 * playing itself.
 */
final class CasinoScenes {
    static final String SEED = "lucky-sevens";

    private final FilmDirector d;
    /** The booth's anchor: the middle counter, at floor height. */
    private BlockPos a;
    private BlockPos slot1, slot2, slot3, plinko, crash, roulette;

    private CasinoScenes(FilmDirector d) {
        this.d = d;
    }

    static boolean film(FilmDirector d, String scene) {
        CasinoScenes c = new CasinoScenes(d);
        switch (scene) {
            case "casino_survey" -> c.survey();
            case "casino_slot" -> c.slots();
            case "casino_plinko" -> c.plinko();
            case "casino_crash" -> c.crash();
            case "casino_roulette" -> c.roulette();
            case "casino_counter" -> c.counter();
            case "casino_wave" -> c.wave();
            case "casino_blackjack" -> c.blackjack();
            case "casino_fee" -> c.fee();
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the set

    private void openCasino(String seed) {
        a = null;
        d.openWorld(seed, false, 50);
        d.cmd("time set 12550");
        d.cmd("gamerule advance_time false");
        d.cmd("gamerule spawn_mobs false");
        for (int i = 0; i < 600 && a == null; i += 20) {
            d.run(20);
            a = d.fromServer(CasinoBooth::anchorOf);
        }
        if (a == null) throw new IllegalStateException("no casino booth after 30 s");
        FilmDirector.LOG.info("[Film] booth at {}", a);
        slot1 = a.offset(-14, 0, 4);
        slot2 = a.offset(-14, 0, 5);
        slot3 = a.offset(-14, 0, 6);
        plinko = a.offset(-14, 0, -3);
        crash = a.offset(-14, 0, -8);
        roulette = a.offset(-8, 0, 10);
        square();
        // What the rebuild knocked loose (plants pop off as items) and whatever wandered in.
        d.cmd("kill @e[type=!minecraft:player,type=!challengecraft:croupier]");
        d.run(5);
        d.cmd("kill @e[type=minecraft:item]");
        d.cmd("execute as @p run casino chips 2000000");
        d.cmd("effect give @p minecraft:invisibility infinite 0 true");
        d.cmd("effect give @p minecraft:resistance infinite 4 true");
        d.cmd("effect give @p minecraft:saturation infinite 0 true");
        d.run(10);
    }

    /** A dark stone square west of the booth with a red carpet up to the counters, lanterns round it. */
    private void square() {
        int y = a.getY();
        int x0 = a.getX() - 18, x1 = a.getX() + 8, z0 = a.getZ() - 14, z1 = a.getZ() + 14;
        // Everything but the booth's own 5x5 (which nothing may build into).
        int bx0 = a.getX() - 2, bx1 = a.getX() + 2, bz0 = a.getZ() - 2, bz1 = a.getZ() + 2;
        int[][] parts = {
                {x0, z0, bx0 - 1, z1}, {bx1 + 1, z0, x1, z1}, {bx0, z0, bx1, bz0 - 1}, {bx0, bz1 + 1, bx1, z1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int[] p : parts) {
                d.cmd(String.format("fill %d %d %d %d %d %d minecraft:air", p[0], y, p[1], p[2], y + 14, p[3]));
                d.cmd(String.format("fill %d %d %d %d %d %d minecraft:polished_blackstone_bricks", p[0], y - 1, p[1], p[2], y - 1, p[3]));
                d.cmd(String.format("fill %d %d %d %d %d %d minecraft:stone", p[0], y - 4, p[1], p[2], y - 2, p[3]));
            }
            d.run(3);
        }
        // The carpet from the square's west edge to the counters.
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:red_carpet", x0 + 2, y, a.getZ() - 1, bx0 - 1, y, a.getZ() + 1));
        // Gold trim and lantern posts along the square's edges.
        for (int x = x0 + 2; x <= x1 - 2; x += 6) {
            for (int z : new int[]{z0 + 1, z1 - 1}) {
                post(x, y, z);
            }
        }
        for (int z = z0 + 4; z <= z1 - 4; z += 6) {
            post(x0 + 1, y, z);
        }
        d.run(5);
        place(DeviceType.SLOT, slot1, Direction.EAST);
        place(DeviceType.SLOT, slot2, Direction.EAST);
        place(DeviceType.SLOT, slot3, Direction.EAST);
        place(DeviceType.PLINKO, plinko, Direction.EAST);
        place(DeviceType.CRASH, crash, Direction.EAST);
        place(DeviceType.ROULETTE, roulette, Direction.EAST);
        d.run(10);
    }

    private void post(int x, int y, int z) {
        d.cmd(String.format("setblock %d %d %d minecraft:gold_block", x, y - 1, z));
        d.cmd(String.format("setblock %d %d %d minecraft:dark_oak_fence", x, y, z));
        d.cmd(String.format("setblock %d %d %d minecraft:dark_oak_fence", x, y + 1, z));
        d.cmd(String.format("setblock %d %d %d minecraft:lantern", x, y + 2, z));
    }

    /** Places a casino device as a player would: standing in front of it, so it faces {@code facing}. */
    private void place(DeviceType type, BlockPos master, Direction facing) {
        d.server(server -> {
            ServerPlayer p = d.player(server);
            ItemStack stack = new ItemStack(CasinoRegistry.item(type));
            ItemStack before = p.getMainHandItem().copy();
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            p.setYRot(facing.getOpposite().toYRot());
            BlockPos ground = master.below();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
            p.gameMode.useItemOn(p, p.level(), stack, InteractionHand.MAIN_HAND, hit);
            p.setItemInHand(InteractionHand.MAIN_HAND, before);
        });
        d.run(2);
    }

    private void stake(int level) {
        d.server(server -> CasinoEconomy.account(d.player(server)).betLevel = level);
    }

    private ServerPlayer me() {
        return d.fromServer(d::player);
    }

    /** The middle of a device's front (devices here face east, so the front is their east face). */
    private static Vec3 front(BlockPos m, double up, double alongNorth) {
        return new Vec3(m.getX() + 1.0, m.getY() + up, m.getZ() + 0.5 - alongNorth);
    }

    /** A point east of a device front, {@code dist} out, {@code up} above its floor. */
    private static Vec3 out(BlockPos m, double dist, double up, double alongNorth) {
        return new Vec3(m.getX() + 1.0 + dist, m.getY() + up, m.getZ() + 0.5 - alongNorth);
    }

    // ------------------------------------------------------------------ scenes

    /** Stills round the square on a few seeds, to pick the set with the best backdrop. */
    private void survey() {
        for (String seed : new String[]{SEED}) {
            openCasino(seed);
            Vec3 mid = new Vec3(a.getX() - 6.5, a.getY(), a.getZ() + 0.5);
            d.server(server -> {
                var level = server.overworld();
                java.util.Map<String, Integer> found = new java.util.TreeMap<>();
                for (int x = a.getX() - 18; x <= a.getX() + 8; x++) {
                    for (int z = a.getZ() - 14; z <= a.getZ() + 14; z++) {
                        for (int y = a.getY(); y <= a.getY() + 1; y++) {
                            var st = level.getBlockState(new BlockPos(x, y, z));
                            if (!st.isAir()) found.merge(st.getBlock().getDescriptionId(), 1, Integer::sum);
                        }
                    }
                }
                FilmDirector.LOG.info("[Film] on the square: {}", found);
                java.util.Map<String, Integer> ents = new java.util.TreeMap<>();
                for (var e : level.getEntities((net.minecraft.world.entity.Entity) null,
                        new net.minecraft.world.phys.AABB(a).inflate(24, 8, 24), e -> true)) {
                    ents.merge(e.getType().getDescriptionId(), 1, Integer::sum);
                }
                FilmDirector.LOG.info("[Film] entities round the square: {}", ents);
            });
            String[] names = {"south", "west", "north", "east"};
            for (int i = 0; i < 4; i++) {
                d.cam.playerPose(mid, i * 90f, 5f, true);
                d.run(3);
                d.still(seed + "_" + names[i]);
            }
            d.cam.cutTo(mid.add(12, 9, 0), mid.add(-8, 0, 0));
            d.run(3);
            d.still(seed + "_air");
            // The planned camera spots.
            d.cam.cutTo(out(slot2, 1.35, 1.3, 0), front(slot2, 1.25, 0));
            d.run(3);
            d.still("cam_slot_close");
            d.cam.cutTo(out(slot2, 3.4, 2.6, 0.8), front(slot2, 2.45, 0));
            d.run(3);
            d.still("cam_slot_back");
            d.cam.cutTo(out(plinko, 2.5, 1.2, 0.8), front(plinko, 1.05, 0.5));
            d.run(3);
            d.still("cam_plinko");
            Vec3 ramp = front(crash, 1.0, 0);
            Vec3 screen = front(crash, 1.4, 1.0);
            Vec3 cmid = ramp.add(0, 0.2, 0.5).add(screen).scale(0.5);
            d.cam.cutTo(out(crash, 3.2, 1.6, 0.5), cmid.add(0, 0.2, 0));
            d.run(3);
            d.still("cam_crash");
            Vec3 felt = new Vec3(roulette.getX(), roulette.getY() + 0.9, roulette.getZ() - 1.0);
            d.cam.cutTo(felt.add(1.5, 1.6, -0.6), felt);
            d.run(3);
            d.still("cam_roulette_felt");
            Vec3 wheel = new Vec3(roulette.getX(), roulette.getY() + 0.95, roulette.getZ() + 0.5);
            d.cam.cutTo(wheel.add(1.6, 1.5, -0.3), wheel);
            d.run(3);
            d.still("cam_roulette_wheel");
            Vec3 spot = CasinoBooth.customerSpot(a, 1);
            Vec3 tray = new Vec3(a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5);
            d.cam.cutTo(spot.add(0.15, 1.62, 0.35), tray.add(0.25, 0.2, 0));
            d.run(3);
            d.still("cam_counter");
            d.cam.player();
            d.closeWorld();
        }
    }

    /** A legendary win in one take (free spins, the lucky item spreading, the show), plain spins, the square at sunset. */
    private void slots() {
        openCasino(SEED);
        stake(6);
        Vec3 face = front(slot2, 1.25, 0);
        d.cam.playerLook(out(slot2, 2.2, 0, 0), face, true);
        d.run(20);

        // 1. Legendary: push in on the reels through the free spins, back off for the show.
        d.cmd("execute as @p run casino slotforce legendary");
        d.shoot("casino_slot_legendary", 17.0, t -> {
            if (t == 3) d.server(server -> SlotGame.use(d.player(server), slot2, ItemStack.EMPTY));
            double push = FilmDirector.ease(t, 0, 200);
            double back = FilmDirector.ease(t, 205, 245);
            double dist = FilmDirector.lerp(FilmDirector.lerp(1.6, 1.1, push), 3.4, back);
            double up = FilmDirector.lerp(0, 1.3, back);
            double side = FilmDirector.lerp(0, 0.8, back);
            Vec3 eye = out(slot2, dist, 1.3 + up, side);
            Vec3 at = face.add(0, FilmDirector.lerp(0, 1.2, back), 0);
            if (t <= 1) d.cam.cutTo(eye, at);
            else d.cam.moveTo(eye, at);
        });
        d.run(240);

        // 2. Plain spins on the next machine; most win nothing.
        stake(4);
        Vec3 face1 = front(slot1, 1.25, 0);
        d.shoot("casino_slot_spins", 9.0, t -> {
            if (t == 3 || t == 63 || t == 123) d.server(server -> SlotGame.use(d.player(server), slot1, ItemStack.EMPTY));
            Vec3 eye = out(slot1, FilmDirector.lerp(1.45, 1.25, FilmDirector.ease(t, 0, 180)), 1.3, -0.15);
            if (t <= 1) d.cam.cutTo(eye, face1);
            else d.cam.moveTo(eye, face1);
        });

        // 3. The square, low, gliding along the machines with the sun going down behind them.
        d.shoot("casino_square", 7.0, t -> {
            double f = FilmDirector.ease(t, 0, 140);
            double z = FilmDirector.lerp(a.getZ() + 9, a.getZ() - 6, f);
            Vec3 eye = new Vec3(a.getX() - 7.5, a.getY() + 2.1, z);
            Vec3 at = new Vec3(a.getX() - 16, a.getY() + 1.4, z - 2.5);
            if (t <= 1) d.cam.cutTo(eye, at);
            else d.cam.moveTo(eye, at);
        });
    }

    /** Four balls, the last into the 30x pocket. */
    private void plinko() {
        openCasino(SEED);
        stake(6);
        Vec3 face = front(plinko, 1.05, 0.5);
        d.cam.playerLook(out(plinko, 2.6, 0, 0.5), face, true);
        d.run(20);
        d.shoot("casino_plinko", 8.0, t -> {
            if (t == 3 || t == 9 || t == 15) d.server(server -> PlinkoGame.use(d.player(server), (ServerLevel) d.player(server).level(), plinko));
            if (t == 21) d.server(server -> {
                ServerPlayer p = d.player(server);
                CasinoTestHooks.forcePlinko(p.getUUID(), 0);
                PlinkoGame.use(p, (ServerLevel) p.level(), plinko);
            });
            double f = FilmDirector.ease(t, 0, 150);
            Vec3 eye = out(plinko, FilmDirector.lerp(2.5, 2.0, f), 1.2, 0.5 + FilmDirector.lerp(0.3, -0.2, f));
            if (t <= 1) d.cam.cutTo(eye, face);
            else d.cam.moveTo(eye, face);
        });
    }

    /** Aboard, the lift-off, the climb (time-lapse), cashing out at 21x, the rocket bursting at 25x. */
    private void crash() {
        openCasino(SEED);
        stake(7);
        Vec3 ramp = front(crash, 1.0, 0);
        Vec3 screen = front(crash, 1.4, 1.0);
        Vec3 mid = ramp.add(0, 0.2, 0.5).add(screen).scale(0.5);
        d.cam.playerLook(out(crash, 2.5, 0, 0.5), mid, true);
        d.run(10);
        CasinoTestHooks.forceCrash(25.0);
        int[] start = {0};
        d.server(server -> CrashGame.use(d.player(server), (ServerLevel) d.player(server).level(), crash));
        start[0] = d.tick;
        // Most of the countdown off camera.
        d.run(165);
        Vec3 eye0 = out(crash, 3.2, 1.6, 0.5);
        d.shoot("casino_crash_launch", 4.0, t -> {
            Vec3 eye = eye0.add(FilmDirector.lerp(0, 0.5, FilmDirector.ease(t, 0, 80)), 0, 0);
            Vec3 at = mid.add(0, FilmDirector.lerp(0.2, 1.8, FilmDirector.ease(t, 30, 80)), 0);
            if (t <= 1) d.cam.cutTo(eye, at);
            else d.cam.moveTo(eye, at);
        });
        int launch = start[0] + 200;
        int cashAt = launch + 870;
        d.shoot("casino_crash_climb", (cashAt - 30 - d.tick) / 20.0, 10, t -> {
            Vec3 eye = out(crash, 3.7, 1.6, 0.5);
            Vec3 at = mid.add(0, 1.6, 0);
            if (t <= 1) d.cam.cutTo(eye, at);
            else d.cam.moveTo(eye, at);
        });
        d.shoot("casino_crash_cashout", 7.0, t -> {
            if (d.tick == cashAt) d.server(server -> CrashGame.cashOutAnywhere(d.player(server)));
            double f = FilmDirector.ease(t, 0, 60);
            Vec3 eye = out(crash, FilmDirector.lerp(3.7, 2.4, f), 1.6, 0.6);
            Vec3 at = mid.add(0, FilmDirector.lerp(1.6, 0.4, f), 0.3);
            if (t <= 1) d.cam.cutTo(eye, at);
            else d.cam.moveTo(eye, at);
        });
    }

    /** Chips going down on the felt, the wheel, the ball dropping into 17, the win. */
    private void roulette() {
        openCasino(SEED);
        Vec3 felt = new Vec3(roulette.getX(), roulette.getY() + 0.9, roulette.getZ() - 1.0);
        Vec3 stand = new Vec3(roulette.getX() + 2.2, roulette.getY(), roulette.getZ() - 1.4);
        d.cam.playerLook(stand, felt, true);
        d.run(10);
        int[][] bets = {
                {RouletteMath.STRAIGHT, 17, 1000}, {RouletteMath.RED, 0, 5000}, {RouletteMath.SPLIT, 1720, 500},
                {RouletteMath.CORNER, 13, 500}, {RouletteMath.DOZEN, 1, 2000}, {RouletteMath.STRAIGHT, 32, 1000},
                {RouletteMath.ODD, 0, 2000}, {RouletteMath.COLUMN, 1, 1000}};
        d.shoot("casino_roulette_bets", 5.0, t -> {
            int i = (t - 4) / 7;
            if (t >= 4 && (t - 4) % 7 == 0 && i < bets.length) {
                int[] b = bets[i];
                d.server(server -> RouletteGame.placeBet(d.player(server), roulette, b[0], b[1], b[2]));
            }
            double f = FilmDirector.ease(t, 0, 100);
            Vec3 eye = felt.add(FilmDirector.lerp(1.5, 1.1, f), FilmDirector.lerp(1.6, 1.2, f), FilmDirector.lerp(-0.6, 0.2, f));
            if (t <= 1) d.cam.cutTo(eye, felt);
            else d.cam.moveTo(eye, felt);
        });
        d.server(server -> CasinoTestHooks.forceRoulette(17));
        // The rest of the betting time off camera.
        d.run(500 - 100 - 50);
        Vec3 wheel = new Vec3(roulette.getX(), roulette.getY() + 0.95, roulette.getZ() + 0.5);
        d.shoot("casino_roulette_spin", 15.0, t -> {
            double close = FilmDirector.ease(t, 0, 150);
            double back = FilmDirector.ease(t, 215, 260);
            Vec3 eye = wheel.add(FilmDirector.lerp(FilmDirector.lerp(1.6, 0.9, close), 2.4, back),
                    FilmDirector.lerp(FilmDirector.lerp(1.5, 1.0, close), 2.0, back), FilmDirector.lerp(-0.3, -1.0, back));
            Vec3 at = wheel.add(0, 0, FilmDirector.lerp(0, -1.2, back));
            if (t <= 1) d.cam.cutTo(eye, at);
            else d.cam.moveTo(eye, at);
        });
    }

    /** Diamonds, gold and emeralds onto the counter, the bell, the croupier pays out in chips. */
    private void counter() {
        openCasino(SEED);
        Vec3 spot = CasinoBooth.customerSpot(a, 1);
        Vec3 tray = new Vec3(a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5);
        d.cam.playerLook(spot, tray.add(0.5, 0.35, 0), true);
        d.run(20);
        d.server(server -> {
            ServerPlayer p = d.player(server);
            p.teleportTo(spot.x, spot.y, spot.z);
            FilmDirector.LOG.info("[Film] at the counter: {} (player {}, croupier {})", net.kasax.challengecraft.casino.CasinoGames.atCroupier(p),
                    p.position(), CasinoBooth.croupier(server) == null ? null : CasinoBooth.croupier(server).position());
        });
        d.run(5);
        d.server(server -> {
            CounterDeposit.place(d.player(server), new ItemStack(Items.DIAMOND, 1));
            FilmDirector.LOG.info("[Film] test deposit lies on the counter: {}", CounterDeposit.hasPile(d.player(server)));
            CounterDeposit.takeBack(d.player(server), 0);
        });
        d.run(5);
        d.shoot("casino_counter", 9.0, t -> {
            if (t == 6) d.server(server -> CounterDeposit.place(d.player(server), new ItemStack(Items.DIAMOND, 64)));
            if (t == 16) d.server(server -> CounterDeposit.place(d.player(server), new ItemStack(Items.GOLD_INGOT, 64)));
            if (t == 26) d.server(server -> CounterDeposit.place(d.player(server), new ItemStack(Items.EMERALD, 48)));
            if (t == 36) d.server(server -> CounterDeposit.place(d.player(server), new ItemStack(Items.NETHERITE_INGOT, 9)));
            if (t == 70) d.server(server -> CounterDeposit.deal(d.player(server)));
            double f = FilmDirector.ease(t, 0, 160);
            double up = FilmDirector.ease(t, 70, 120);
            Vec3 eye = spot.add(FilmDirector.lerp(0.1, 0.25, f), FilmDirector.lerp(1.75, 1.65, f), FilmDirector.lerp(0.3, 0.15, f));
            Vec3 at = tray.add(FilmDirector.lerp(0.05, 0.6, up), FilmDirector.lerp(-0.05, 0.6, up), 0);
            if (t <= 1) d.cam.cutTo(eye, at);
            else d.cam.moveTo(eye, at);
        });
    }

    /** A big loss on plinko, and ten seconds later the House sends its regards. */
    private void wave() {
        openCasino(SEED);
        d.cmd("effect clear @p minecraft:invisibility");
        d.cmd("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
        Vec3 standAt = new Vec3(a.getX() - 7.5, a.getY(), a.getZ() + 0.5);
        d.cam.playerPose(standAt, 90f, 0f, true);
        stake(12);
        // Lose at the plinko board: the 0.3x pocket.
        d.server(server -> {
            ServerPlayer p = d.player(server);
            CasinoTestHooks.forcePlinko(p.getUUID(), 6);
            PlinkoGame.use(p, (ServerLevel) p.level(), plinko);
        });
        d.run(60);
        d.cam.playerPose(standAt, 90f, 0f, true);
        // The wave comes after ten quiet seconds.
        d.run(130);
        Vec3 hero = standAt.add(0, 1.0, 0);
        d.shoot("casino_wave", 9.0, t -> {
            double f = FilmDirector.ease(t, 0, 180);
            double ang = Math.toRadians(FilmDirector.lerp(150, 200, f));
            double r = FilmDirector.lerp(4.2, 6.0, f);
            Vec3 eye = hero.add(Math.cos(ang) * r, FilmDirector.lerp(1.6, 3.2, f), Math.sin(ang) * r);
            if (t <= 1) d.cam.cutTo(eye, hero);
            else d.cam.moveTo(eye, hero);
        });
    }

    /**
     * Dies in the square, plays for his life at the counter (a hand searched for: hit on thirteen to
     * twenty-one, the croupier busts), and stands up again where he fell.
     */
    private void blackjack() {
        openCasino(SEED);
        d.cmd("effect clear @p minecraft:invisibility");
        d.cmd("effect clear @p minecraft:resistance");
        Vec3 fall = new Vec3(a.getX() - 9.5, a.getY(), a.getZ() + 3.5);
        d.cam.playerPose(fall, -60f, 10f, true);
        d.run(20);
        long seed = dramaticHand();
        FilmDirector.LOG.info("[Film] blackjack shuffle seed {}", seed);
        d.hud(true);
        d.cam.player();
        // Death: the House catches it and seats him at the counter.
        d.cmd("kill @p");
        d.server(server -> rig(d.player(server), seed));
        d.shoot("casino_blackjack", 16.0, t -> {
            if (t == 60) d.server(server -> BlackjackRevival.act(d.player(server), BlackjackTable.HIT));
            if (t == 95) d.server(server -> {
                if (BlackjackRevival.inLimbo(d.player(server).getUUID())) BlackjackRevival.act(d.player(server), BlackjackTable.STAND);
            });
        });
        d.shoot("casino_revived", 5.0, null);
        d.hud(false);
    }

    /** A shuffle where thirteen against a ten turns to twenty-one with one card and the croupier busts. */
    private static long dramaticHand() {
        for (long s = 1; s < 5_000_000; s++) {
            BlackjackTable t = new BlackjackTable(RandomSource.create(s));
            if (t.finished) continue;
            var hand = t.hands.get(0).cards;
            if (BlackjackTable.total(hand) != 13 || BlackjackTable.isSoft(hand)) continue;
            if (BlackjackTable.cardValue(t.dealer.get(0)) != 10) continue;
            t.act(BlackjackTable.HIT);
            if (BlackjackTable.total(t.hands.get(0).cards) != 21) continue;
            if (!t.finished) t.act(BlackjackTable.STAND);
            if (!t.finished || t.net <= 0) continue;
            if (BlackjackTable.total(t.dealer) <= 21 || t.dealer.size() < 3) continue;
            return s;
        }
        throw new IllegalStateException("no dramatic hand found");
    }

    /** Swaps the freshly dealt hand of a player in limbo for the searched one, and tells his client. */
    private static void rig(ServerPlayer player, long seed) {
        try {
            Field limboField = BlackjackRevival.class.getDeclaredField("LIMBO");
            limboField.setAccessible(true);
            Object limbo = ((Map<?, ?>) limboField.get(null)).get(player.getUUID());
            if (limbo == null) throw new IllegalStateException("not in limbo");
            Field table = limbo.getClass().getDeclaredField("table");
            table.setAccessible(true);
            table.set(limbo, new BlackjackTable(RandomSource.create(seed)));
            // The hand dealt first may already have been over (a natural): play the new one from the start.
            for (String[] f : new String[][]{{"phase", "0"}, {"outcome", "-1"}, {"ticksLeft", "400"}}) {
                Field fld = limbo.getClass().getDeclaredField(f[0]);
                fld.setAccessible(true);
                fld.setInt(limbo, Integer.parseInt(f[1]));
            }
            Method send = BlackjackRevival.class.getDeclaredMethod("send", ServerPlayer.class, limbo.getClass());
            send.setAccessible(true);
            send.invoke(null, player, limbo);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The fee is due (HUD shown), the House collects; then a fee the team cannot pay: the House wins. */
    private void fee() {
        openCasino(SEED);
        // A step back from the counter, the croupier in the middle of the frame.
        Vec3 spot = CasinoBooth.customerSpot(a, 1);
        Vec3 tray = new Vec3(a.getX() + 0.5, spot.y, a.getZ() + 0.5);
        Vec3 back = spot.subtract(tray).normalize();
        Vec3 standAt = spot.add(back.scale(1.6));
        Vec3 croupier = d.fromServer(server -> CasinoBooth.croupier(server) == null ? null : CasinoBooth.croupier(server).position());
        Vec3 face = croupier == null ? tray.add(0, 1.6, 0) : croupier.add(0, 1.3, 0);
        d.cam.player();
        d.cam.playerLook(standAt, face, true);
        d.hud(true);
        d.run(40);
        d.shoot("casino_fee", 6.5, t -> {
            if (t == 2) d.server(server -> {
                long fee = CasinoEconomy.fee(server, CasinoEconomy.nextFeeIndex(server));
                ServerPlayNetworking.send(d.player(server), new CasinoNet.Fx(CasinoNet.Fx.FEE_WARNING, fee, "", 0L));
            });
            if (t == 60) {
                long fee = d.fromServer(server -> CasinoEconomy.fee(server, CasinoEconomy.nextFeeIndex(server)));
                d.cmd("casino fee");
                d.server(server -> ServerPlayNetworking.send(d.player(server), new CasinoNet.Fx(CasinoNet.Fx.FEE_PAID, fee, Long.toString(fee), 0L)));
            }
        });
        d.cmd("execute as @p run casino chips -1990000");
        // A smaller GUI, so the title "THE HOUSE WINS" fits the phone-wide screen.
        d.client(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });
        d.run(30);
        d.shoot("casino_bankrupt", 7.0, t -> {
            if (t == 12) d.server(server -> {
                try {
                    Method bankrupt = CasinoEconomy.class.getDeclaredMethod("bankrupt", MinecraftServer.class, long.class, long.class);
                    bankrupt.setAccessible(true);
                    long total = CasinoEconomy.total(server);
                    bankrupt.invoke(null, server, Math.max(total * 3, 1_000_000L), total);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
        });
        d.hud(false);
        d.client(mc -> {
            mc.options.guiScale().set(4);
            mc.resizeGui();
        });
    }
}
