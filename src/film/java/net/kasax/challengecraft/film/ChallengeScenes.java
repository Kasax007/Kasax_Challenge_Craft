package net.kasax.challengecraft.film;

import net.kasax.challengecraft.challenges.Chal_45_ForceItemBattle;
import net.kasax.challengecraft.challenges.Chal_48_RedLight;
import net.kasax.challengecraft.entity.DiceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

/**
 * The challenge short ("I coded viral challenges into Minecraft"): one scene per challenge, each
 * in a fresh world created with just that challenge on. The camera is mostly a free one, so the
 * player's character is seen doing the thing; the HUD stays on where the challenge shows itself
 * there (with a free camera the game leaves out the hotbar and hearts by itself).
 */
final class ChallengeScenes {
    static final String SEED = "lucky-sevens";

    private final FilmDirector d;

    private ChallengeScenes(FilmDirector d) {
        this.d = d;
    }

    static boolean film(FilmDirector d, String scene) {
        ChallengeScenes c = new ChallengeScenes(d);
        switch (scene) {
            case "clock_test" -> c.clockTest();
            case "red_light" -> c.redLight();
            case "dice" -> c.dice();
            case "cushion" -> c.cushion();
            case "chunk_blocks" -> c.chunkBlocks();
            case "floor_lava" -> c.floorLava();
            case "size_matters" -> c.sizeMatters();
            case "upside_down" -> c.upsideDown();
            case "level_border" -> c.levelBorder();
            case "chunk_hunt" -> c.chunkHunt();
            case "double_trouble" -> c.doubleTrouble();
            case "skyblock" -> c.skyblock();
            case "force_item" -> c.forceItem();
            case "lockout_bob" -> c.lockoutBob();
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private void open(Integer... challenges) {
        d.openWorld(SEED, false, challenges);
        d.cmd("time set 6000");
        d.cmd("gamerule advance_time false");
        d.cmd("gamerule spawn_mobs false");
        d.cmd("effect give @p minecraft:saturation infinite 0 true");
        d.run(10);
    }

    private BlockPos spawn() {
        return d.fromServer(server -> {
            BlockPos s = server.overworld().getRespawnData().pos();
            int y = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.getX(), s.getZ());
            return new BlockPos(s.getX(), y, s.getZ());
        });
    }

    /** Levels a patch round {@code c} (at its height): air above, {@code top} as floor, dirt below. */
    private void flat(BlockPos c, int x0, int z0, int x1, int z1, String top) {
        int y = c.getY();
        String box = String.format("%d %%d %d %d %%d %d", c.getX() + x0, c.getZ() + z0, c.getX() + x1, c.getZ() + z1);
        d.cmd("fill " + String.format(box, y, y + 12) + " minecraft:air");
        d.cmd("fill " + String.format(box, y - 1, y - 1) + " " + top);
        d.cmd("fill " + String.format(box, y - 4, y - 2) + " minecraft:dirt");
        d.run(3);
        clearLoose();
    }

    /** Items knocked loose by the levelling, and animals that wandered in. */
    private void clearLoose() {
        d.run(3);
        d.cmd("kill @e[type=minecraft:item]");
        d.cmd("kill @e[type=!minecraft:player,type=!challengecraft:croupier,distance=..80]");
    }

    private Vec3 playerPos() {
        return d.ctx.computeOnClient(mc -> mc.player.position());
    }

    private void keys(boolean forward, boolean sprint) {
        if (forward) Cam.freeWalk = true;
        if (forward) d.ctx.getInput().holdKey(o -> o.keyUp);
        else d.ctx.getInput().releaseKey(o -> o.keyUp);
        if (sprint) d.ctx.getInput().holdKey(o -> o.keySprint);
        else d.ctx.getInput().releaseKey(o -> o.keySprint);
    }

    private static Vec3 feet(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    private void track(int t, Vec3 eye, Vec3 at) {
        if (t <= 1) d.cam.cutTo(eye, at);
        else d.cam.moveTo(eye, at);
    }

    // ------------------------------------------------------------------ scenes

    /** How many server ticks run per client tick, free and gated. */
    private void clockTest() {
        open();
        FilmClock.gate(false);
        long s0 = d.fromServer(server -> (long) server.getTickCount());
        d.run(100);
        long s1 = d.fromServer(server -> (long) server.getTickCount());
        FilmDirector.LOG.info("[Film] free: {} server ticks in 100 client ticks", s1 - s0);
        FilmClock.gate(true);
        d.run(100);
        long s2 = d.fromServer(server -> (long) server.getTickCount());
        FilmDirector.LOG.info("[Film] gated: {} server ticks in 100 client ticks (ran {}, held {})", s2 - s1, FilmClock.RAN.get(), FilmClock.HELD.get());
        d.shoot("clock_test", 2.0, null);
        long s3 = d.fromServer(server -> (long) server.getTickCount());
        FilmDirector.LOG.info("[Film] gated while filming: {} server ticks in 40 client ticks", s3 - s2);
    }

    /** Sprinting down a road while the light goes green, yellow, red - and he keeps running. */
    private void redLight() {
        open(48);
        BlockPos s = spawn();
        flat(s, -4, -3, 70, 3, "minecraft:dirt_path");
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:grass_block", s.getX() - 4, s.getY() - 1, s.getZ() - 3, s.getX() + 70, s.getY() - 1, s.getZ() - 2));
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:grass_block", s.getX() - 4, s.getY() - 1, s.getZ() + 2, s.getX() + 70, s.getY() - 1, s.getZ() + 3));
        Vec3 start = feet(s);
        d.cam.playerPose(start, -90f, 0f, true);
        d.run(20);
        light(Chal_48_RedLight.Phase.GREEN, 45);
        d.hud(true);
        keys(true, true);
        d.shoot("red_light", 9.0, t -> {
            Vec3 p = playerPos();
            track(t, p.add(4.6, 1.45, 2.1), p.add(0, 1.15, 0));
        });
        keys(false, false);
        d.hud(false);
    }

    /** Sets the traffic light (server statics, as its own timer would) with {@code ticks} of it left. */
    private void light(Chal_48_RedLight.Phase phase, int ticks) {
        d.server(server -> {
            try {
                Method enter = Chal_48_RedLight.class.getDeclaredMethod("enter", MinecraftServer.class, Chal_48_RedLight.Phase.class);
                enter.setAccessible(true);
                enter.invoke(null, server, phase);
                Field left = Chal_48_RedLight.class.getDeclaredField("ticksLeft");
                left.setAccessible(true);
                left.setInt(null, ticks);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private Vec3 diePos() {
        return d.ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(DiceEntity.class, mc.player.getBoundingBox().inflate(16))
                .stream().map(e -> e.position()).findFirst().orElse(null));
    }

    private void throwDie() {
        d.server(server -> {
            ServerPlayer p = d.player(server);
            p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
        });
    }

    /** The die tumbles; however many pips land up is how far he may walk - and the ring shows it. */
    private void dice() {
        open(46);
        BlockPos s = spawn();
        flat(s, -8, -8, 12, 8, "minecraft:grass_block");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 20f, true);
        d.run(20);
        d.cmd("item replace entity @p weapon.mainhand with challengecraft:dice");
        d.run(5);
        // A first throw off camera, to learn where it lands.
        throwDie();
        d.run(70);
        Vec3 land = diePos();
        if (land == null) land = at.add(2.5, 0, 0);
        Vec3 l = land;
        FilmDirector.LOG.info("[Film] the die lands near {}", l);
        d.cam.playerPose(at, -90f, 20f, true);
        d.run(10);
        d.shoot("dice_throw", 5.0, t -> {
            if (t == 4) throwDie();
            Vec3 die = diePos();
            Vec3 focus = die != null && t > 6 ? die : l;
            double up = FilmDirector.ease(t, 60, 95);
            Vec3 eye = l.add(FilmDirector.lerp(0.9, 2.2, up), FilmDirector.lerp(0.55, 3.2, up), FilmDirector.lerp(1.2, 3.0, up));
            Vec3 look = focus.add(0, 0.1, 0).lerp(at.add(0, 0.5, 0), up * 0.6);
            track(t, eye, look);
        });
        keys(true, false);
        d.shoot("dice_walk", 5.0, t -> {
            Vec3 p = playerPos();
            track(t, at.add(3.0, 3.6, 4.5), p.add(0, 0.8, 0));
        });
        keys(false, false);
    }

    /** No walking: a cushion down three blocks ahead, sit on it, get up there, again. */
    private void cushion() {
        open(47);
        BlockPos s = spawn();
        flat(s, -6, -6, 26, 6, "minecraft:grass_block");
        Vec3 start = feet(s);
        d.cam.playerPose(start, -90f, 30f, true);
        d.run(20);
        d.cmd("item replace entity @p weapon.mainhand with minecraft:white_cushion");
        d.run(5);
        d.shoot("cushion", 10.0, t -> {
            int k = t % 32;
            if (k == 4) d.server(server -> {
                ServerPlayer p = d.player(server);
                BlockPos ground = p.blockPosition().offset(3, -1, 0);
                ItemStack held = p.getMainHandItem();
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
                p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                p.gameMode.useItemOn(p, p.level(), held, InteractionHand.MAIN_HAND, hit);
            });
            if (k == 12) d.server(server -> {
                ServerPlayer p = d.player(server);
                List<Cushion> near = p.level().getEntitiesOfClass(Cushion.class, p.getBoundingBox().inflate(5),
                        c -> c.getX() > p.getX() + 1.5);
                near.stream().min(Comparator.comparingDouble(c -> c.distanceToSqr(p)))
                        .ifPresent(c -> p.interactOn(c, InteractionHand.MAIN_HAND, c.position()));
            });
            if (k == 26) d.server(server -> d.player(server).stopRiding());
            Vec3 p = playerPos();
            track(t, p.add(1.5, 2.2, 5.5), p.add(1.5, 0.6, 0));
        });
    }

    /** The world from the air: every chunk one random block. */
    private void chunkBlocks() {
        d.client(mc -> mc.options.renderDistance().set(10));
        open(16);
        d.settle(1200);
        BlockPos s = spawn();
        Vec3 c = feet(s);
        d.cmd(String.format("tp @p %.1f %d %.1f", c.x, s.getY() + 40, c.z));
        d.cmd("gamemode spectator @p");
        d.settle(800);
        d.shoot("chunk_blocks", 8.0, t -> {
            double f = t / 160.0;
            Vec3 eye = c.add(-20 + 40 * f, 46 - 6 * f, -24 + 10 * f);
            track(t, eye, eye.add(18, -30, 14));
        });
        d.client(mc -> mc.options.renderDistance().set(6));
    }

    /** Standing still on his own platform: three seconds, and he burns. */
    private void floorLava() {
        open(29);
        BlockPos s = spawn();
        flat(s, -6, -6, 6, 6, "minecraft:grass_block");
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", s.getX() - 1, s.getY() - 1, s.getZ() - 1, s.getX() + 1, s.getY() - 1, s.getZ() + 1));
        Vec3 at = feet(s);
        d.cam.playerPose(at, 135f, 0f, true);
        d.run(5);
        d.shoot("floor_lava", 7.0, t -> {
            double ang = Math.toRadians(30 + t * 0.35);
            track(t, at.add(Math.cos(ang) * 3.6, 1.8, Math.sin(ang) * 3.6), at.add(0, 1.0, 0));
        });
    }

    /** One giant creeper, a giant spider, tiny ones round them - all coming on. */
    private void sizeMatters() {
        open(33);
        BlockPos s = spawn();
        flat(s, -14, -10, 14, 10, "minecraft:grass_block");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.cmd("effect give @p minecraft:resistance infinite 4 true");
        BlockPos m = s.offset(11, 0, 0);
        summonSized("creeper", m.offset(0, 0, -1), 2.0);
        summonSized("spider", m.offset(2, 0, 4), 1.6);
        summonSized("creeper", m.offset(-2, 0, 3), -0.5);
        summonSized("creeper", m.offset(-1, 0, -4), -0.5);
        summonSized("zombie", m.offset(1, 0, -6), -0.5);
        d.run(10);
        d.shoot("size_matters", 7.0, t -> {
            double f = FilmDirector.ease(t, 0, 140);
            Vec3 eye = at.add(FilmDirector.lerp(2.5, 3.8, f), FilmDirector.lerp(0.6, 0.9, f), FilmDirector.lerp(1.8, 1.0, f));
            Vec3 look = feet(m).add(FilmDirector.lerp(0, -4, f), 2.6, -0.5);
            track(t, eye, look);
        });
    }

    /** A mob with the challenge's own size modifier set to {@code add} (scale = 1 + add, as the challenge rolls it). */
    private void summonSized(String type, BlockPos at, double add) {
        String tag = "film_" + type + "_" + Math.abs(at.getX()) + "_" + Math.abs(at.getZ());
        d.cmd(String.format("summon minecraft:%s %d %d %d {Tags:[\"%s\"],PersistenceRequired:1b}", type, at.getX(), at.getY(), at.getZ(), tag));
        d.cmd(String.format(java.util.Locale.ROOT, "attribute @e[tag=%s,limit=1] minecraft:scale modifier add challengecraft:size_matters_scale %.2f add_value", tag, add));
    }

    /** Ores broken one after another - and every drop flies up into the sky. */
    private void upsideDown() {
        open(34);
        BlockPos s = spawn();
        flat(s, -6, -6, 8, 6, "minecraft:grass_block");
        String[] ores = {"diamond_ore", "gold_ore", "emerald_ore", "iron_ore", "redstone_ore", "lapis_ore", "diamond_ore", "gold_ore"};
        BlockPos w = s.offset(3, 0, -2);
        for (int i = 0; i < ores.length; i++) {
            d.cmd(String.format("setblock %d %d %d minecraft:%s", w.getX(), w.getY() + (i % 2), w.getZ() + i / 2, ores[i]));
        }
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 10f, true);
        d.cmd("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        d.run(10);
        d.shoot("upside_down", 7.0, t -> {
            if (t >= 6 && (t - 6) % 7 == 0 && (t - 6) / 7 < ores.length) {
                int i = (t - 6) / 7;
                BlockPos b = new BlockPos(w.getX(), w.getY() + (i % 2), w.getZ() + i / 2);
                d.server(server -> {
                    ServerPlayer p = d.player(server);
                    p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                    p.gameMode.destroyBlock(b);
                });
            }
            double up = FilmDirector.ease(t, 30, 140);
            Vec3 eye = at.add(1.2, 0.6, 3.2);
            Vec3 look = feet(w).add(0, FilmDirector.lerp(1.0, 6.0, up), 1.5);
            track(t, eye, look);
        });
    }

    /** XP level 0: a world one block wide. Levels come in, the border grows. */
    private void levelBorder() {
        open(9);
        // The challenge keeps the border on 0/0, often far from spawn: load that ground first.
        d.cmd("forceload add -48 -48 48 48");
        d.run(60);
        BlockPos c = d.fromServer(server -> {
            int y = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            return new BlockPos(0, y, 0);
        });
        Vec3 at = new Vec3(0.5, c.getY(), 0.5);
        FilmDirector.LOG.info("[Film] border centre ground at {}", c);
        flat(c, -24, -24, 24, 24, "minecraft:grass_block");
        d.cam.playerPose(at, 200f, 10f, true);
        d.settle(400);
        d.shoot("level_border", 9.0, t -> {
            if (t == 30) d.cmd("xp add @p 3 levels");
            if (t == 70) d.cmd("xp add @p 7 levels");
            if (t == 110) d.cmd("xp add @p 20 levels");
            double f = FilmDirector.ease(t, 0, 180);
            Vec3 eye = at.add(FilmDirector.lerp(2.2, 9.0, f), FilmDirector.lerp(3.0, 13.0, f), FilmDirector.lerp(2.6, 10.0, f));
            track(t, eye, at.add(0, 0.8, 0));
        });
    }

    /** Into a new chunk: the border slams shut round it, the chunk changes, a glowing target appears. */
    private void chunkHunt() {
        open(38);
        BlockPos s = spawn();
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.cmd("effect give @p minecraft:resistance infinite 4 true");
        d.run(60);
        int nextChunkX = ((s.getX() >> 4) + 1) << 4;
        Vec3 chunkMid = new Vec3(nextChunkX + 8, s.getY(), (s.getZ() & ~15) + 8);
        keys(true, false);
        d.shoot("chunk_hunt", 9.0, t -> {
            Vec3 p = playerPos();
            if (p.x > nextChunkX + 3) keys(false, false);
            double f = FilmDirector.ease(t, 0, 180);
            Vec3 eye = chunkMid.add(FilmDirector.lerp(-14, -18, f), FilmDirector.lerp(14, 22, f), FilmDirector.lerp(12, 16, f));
            track(t, eye, chunkMid.add(0, 1, 0));
        });
        keys(false, false);
    }

    /** Ten times the spawns, at night (the player sees in the dark, so the horde does too). */
    private void doubleTrouble() {
        FilmDirector.doubleTrouble = 10;
        open(35);
        d.cmd("time set 18000");
        d.cmd("effect give @p minecraft:night_vision infinite 0 true");
        d.cmd("effect give @p minecraft:resistance infinite 4 true");
        d.cmd("effect give @p minecraft:invisibility infinite 0 true");
        BlockPos s = spawn();
        flat(s, -10, -10, 10, 10, "minecraft:grass_block");
        Vec3 at = feet(s);
        Vec3 spot = at.add(5, 0, 0);
        d.run(40);
        // One mob of each kind appears in front of the camera - and comes ten times over.
        d.shoot("double_trouble", 8.0, t -> {
            if (t == 12) d.cmd(String.format(java.util.Locale.ROOT, "summon minecraft:zombie %.1f %.1f %.1f", spot.x, spot.y, spot.z - 1.5));
            if (t == 52) d.cmd(String.format(java.util.Locale.ROOT, "summon minecraft:skeleton %.1f %.1f %.1f", spot.x + 1, spot.y, spot.z + 1));
            if (t == 92) d.cmd(String.format(java.util.Locale.ROOT, "summon minecraft:creeper %.1f %.1f %.1f", spot.x - 1, spot.y, spot.z + 2.5));
            double f = FilmDirector.ease(t, 0, 160);
            Vec3 eye = spot.add(FilmDirector.lerp(-6.5, -9.5, f), FilmDirector.lerp(2.2, 4.5, f), FilmDirector.lerp(-2.5, -4.0, f));
            track(t, eye, spot.add(0, 0.8, 0.5));
        });
    }

    /** A Skyblock island in the void, circled from the air. */
    private void skyblock() {
        open(11);
        d.settle(400);
        // The island is where the player stands; the spawn column below it is void.
        Vec3 at = d.fromServer(server -> d.player(server).position());
        FilmDirector.LOG.info("[Film] skyblock island at {}", at);
        d.cmd("effect give @p minecraft:invisibility infinite 0 true");
        d.shoot("skyblock", 8.0, t -> {
            double ang = Math.toRadians(-40 + t * 0.55);
            Vec3 eye = at.add(Math.cos(ang) * 11, 5.5, Math.sin(ang) * 11);
            track(t, eye, at.add(0, -2, 0));
        });
    }

    /** Force Item Battle against Bob: each with his own target, Bob's floating over his head. */
    private void forceItem() {
        open(45);
        BlockPos s = spawn();
        flat(s, -8, -8, 8, 8, "minecraft:grass_block");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.cmd("challengecraft_bot spawn Bob");
        d.run(20);
        d.cmd(String.format(java.util.Locale.ROOT, "tp Bob %.1f %d %.1f 90 0", at.x + 3, s.getY(), at.z));
        d.run(10);
        d.server(server -> Chal_45_ForceItemBattle.startBattle(server, d.player(server), false));
        d.run(40);
        d.hud(true);
        Vec3 bob = at.add(3, 0, 0);
        d.shoot("force_item", 6.0, t -> {
            double f = FilmDirector.ease(t, 0, 120);
            Vec3 eye = bob.add(FilmDirector.lerp(-2.6, -2.0, f), FilmDirector.lerp(1.9, 2.2, f), FilmDirector.lerp(1.6, 1.0, f));
            track(t, eye, bob.add(0, 2.0, 0));
        });
        d.hud(false);
    }

    /** Lockout against Bob: Bob off to work, and the board. */
    private void lockoutBob() {
        open(40);
        BlockPos s = spawn();
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.cmd("challengecraft_bot spawn Bob");
        d.run(20);
        d.cmd("execute as @p run challengecraft_bot lockout Bob");
        d.run(60);
        // The board opens by itself when the game starts; the run is filmed without it.
        d.ctx.setScreen(() -> null);
        d.run(5);
        d.shoot("lockout_bob_run", 7.0, t -> {
            Vec3 bob = d.ctx.computeOnClient(mc -> mc.level.players().stream()
                    .filter(p -> p.getName().getString().equals("Bob")).findFirst().map(p -> p.position()).orElse(at));
            track(t, bob.add(-3.5, 2.0, 3.0), bob.add(0, 1.2, 0));
        });
        // The board over a calm sky, not over whatever the player stands in.
        d.cam.cutTo(at.add(0, 6, 0), at.add(20, 9, 0));
        d.ctx.setScreen(net.kasax.challengecraft.client.screen.LockoutBingoBoardScreen::new);
        d.run(10);
        d.shoot("lockout_board", 4.0, null);
        d.ctx.setScreen(() -> null);
    }
}
