package net.kasax.challengecraft.film;

import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.challenges.Chal_22_AllItems;
import net.kasax.challengecraft.challenges.Chal_44_ProgressiveBlockDrops;
import net.kasax.challengecraft.client.screen.AllBiomesHUD;
import net.kasax.challengecraft.client.screen.AllBiomesScreen;
import net.kasax.challengecraft.client.screen.AllItemsScreen;
import net.kasax.challengecraft.client.screen.TriviaScreen;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.data.XpManager;
import net.kasax.challengecraft.network.AllBiomesSyncPacket;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The gameplay and HUD shots of the long film: the chunk worlds, the End, Chunk Hunt, the
 * collection HUDs and their screens, trivia, the name tags. Each scene builds its own little stage
 * in a fresh world (the same seed as the challenge shorts) and films it with the free camera.
 */
final class WorldScenes {
    static final String SEED = "lucky-sevens";
    /** The film player's profile: level 20 and ten Infinity Stars (a gold name). */
    static final long PLAYER_XP = 29_000;
    /** Bob's profile: level 20 and fifteen Infinity Stars (a rainbow name). */
    static final long BOB_XP = 34_000;

    private final FilmDirector d;

    private WorldScenes(FilmDirector d) {
        this.d = d;
    }

    static boolean film(FilmDirector d, String scene) {
        WorldScenes w = new WorldScenes(d);
        switch (scene) {
            case "chunk_walk_v2" -> w.chunkWalk();
            case "chunk_air_v2" -> w.chunkAir();
            case "hud_run" -> w.hudRun();
            case "dragon" -> w.dragon();
            case "chunk_hunt_v2" -> w.chunkHunt();
            case "progressive_drops" -> w.progressiveDrops();
            case "all_biomes" -> w.allBiomes();
            case "trivia" -> w.trivia();
            case "all_items" -> w.allItems();
            case "bob_work" -> w.bobWork();
            case "bob_board" -> w.bobBoard();
            case "nametags" -> w.nametags();
            case "upside_down_v2" -> w.upsideDown();
            case "size_matters_v2" -> w.sizeMatters();
            case "skyblock_v2" -> w.skyblock();
            case "random_block_drops" -> w.randomBlockDrops();
            case "dice_walk" -> w.diceWalk();
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** The profile every gameplay shot starts from: level 20, five Infinity Stars. */
    static final long XP = 24_000;

    private void open(Integer... challenges) {
        d.profileXp(XP);
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
        d.cmd("fill " + String.format(box, y, y + 14) + " minecraft:air");
        d.cmd("fill " + String.format(box, y - 1, y - 1) + " " + top);
        d.cmd("fill " + String.format(box, y - 4, y - 2) + " minecraft:dirt");
        d.run(3);
        clearLoose();
    }

    private void clearLoose() {
        d.run(3);
        d.cmd("kill @e[type=minecraft:item]");
        d.cmd("kill @e[type=!minecraft:player,type=!challengecraft:croupier,distance=..80]");
    }

    private Vec3 playerPos() {
        return d.ctx.computeOnClient(mc -> mc.player.position());
    }

    private static Vec3 feet(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    /** Puts the free camera in place a few ticks before the first frame, so the renderer has settled on it. */
    private void preroll(Vec3 eye, Vec3 at) {
        d.cam.cutTo(eye, at);
        d.run(5);
    }

    private void track(int t, Vec3 eye, Vec3 at) {
        if (t <= 1) d.cam.cutTo(eye, at);
        else d.cam.moveTo(eye, at);
    }

    private void keys(boolean forward, boolean sprint, boolean free) {
        if (free && forward) Cam.freeWalk = true;
        if (forward) d.ctx.getInput().holdKey(o -> o.keyUp);
        else d.ctx.getInput().releaseKey(o -> o.keyUp);
        if (sprint) d.ctx.getInput().holdKey(o -> o.keySprint);
        else d.ctx.getInput().releaseKey(o -> o.keySprint);
    }

    private void attackKey(boolean on) {
        if (on) d.ctx.getInput().holdKey(o -> o.keyAttack);
        else d.ctx.getInput().releaseKey(o -> o.keyAttack);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double f) {
        return a.lerp(b, f);
    }

    /** A blocky tree: {@code logs} oak logs on a trunk at {@code base}, two layers of leaves. */
    private void tree(BlockPos base, int logs) {
        int x = base.getX(), y = base.getY(), z = base.getZ();
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves", x - 2, y + logs - 2, z - 2, x + 2, y + logs - 1, z + 2));
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves", x - 1, y + logs, z - 1, x + 1, y + logs + 1, z + 1));
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:oak_log", x, y, z, x, y + logs - 1, z));
    }

    // ------------------------------------------------------------------ chunk worlds

    /** Sprinting along a road that crosses chunk after chunk, each chunk one block of the challenge's pool. */
    private void chunkWalk() {
        open(16);
        d.settle(600);
        BlockPos s = spawn();
        String[] floors = {"gold_block", "redstone_block", "diamond_block", "emerald_block", "lapis_block", "amethyst_block", "copper_block", "quartz_block"};
        int cx = s.getX() >> 4;
        int x0 = cx * 16, x1 = (cx + floors.length) * 16 - 1;
        int z0 = s.getZ() - 10, z1 = s.getZ() + 10, y = s.getY();
        d.cmd(String.format("forceload add %d %d %d %d", x0, z0, x1, z1));
        d.run(100);
        for (int k = 0; k < floors.length; k++) {
            int a = x0 + k * 16, b = a + 15;
            d.cmd(String.format("fill %d %d %d %d %d %d minecraft:air", a, y, z0, b, y + 14, z1));
            d.cmd(String.format("fill %d %d %d %d %d %d minecraft:%s", a, y - 4, z0, b, y - 1, z1, floors[k]));
        }
        d.run(10);
        clearLoose();
        Vec3 start = new Vec3(x0 + 11.5, y, s.getZ() + 0.5);
        d.cam.playerPose(start, -90f, 0f, true);
        d.cmd("effect give @p minecraft:speed infinite 2 true");
        d.run(20);
        d.hud(false);
        double a0 = Math.toRadians(95);
        preroll(start.add(Math.cos(a0) * 6.0, 1.9, Math.sin(a0) * 6.0), start.add(1.5, 0.7, 0));
        keys(true, true, true);
        d.shoot("chunk_walk_v2", 10.0, t -> {
            Vec3 p = playerPos();
            double f = FilmDirector.ease(t, 0, 200);
            // From the side to behind and above him, looking a little ahead of him.
            double ang = Math.toRadians(FilmDirector.lerp(95, 140, f));
            double r = FilmDirector.lerp(6.0, 7.0, f);
            Vec3 eye = p.add(Math.cos(ang) * r, FilmDirector.lerp(1.9, 3.0, f), Math.sin(ang) * r);
            track(t, eye, p.add(FilmDirector.lerp(1.5, 3.5, f), 0.7, 0));
        });
        keys(false, false, true);
        d.cmd("effect clear @p minecraft:speed");
    }

    /** The world from the air: every chunk one random block. */
    private void chunkAir() {
        d.client(mc -> {
            mc.options.renderDistance().set(9);
            mc.options.simulationDistance().set(5);
        });
        open(16);
        d.settle(1200);
        BlockPos s = spawn();
        Vec3 c = feet(s);
        d.cmd(String.format(Locale.ROOT, "tp @p %.1f %d %.1f", c.x, s.getY() + 40, c.z));
        d.cmd("gamemode spectator @p");
        d.settle(800);
        Vec3 e0 = c.add(-34, 58, -26);
        Vec3 dir = new Vec3(26, -36, 20);
        d.cam.cutTo(e0, e0.add(dir));
        d.run(15);
        d.shoot("chunk_air_v2", 8.0, t -> {
            double f = t / 160.0;
            Vec3 eye = e0.add(64 * f, -6 * f, 44 * f);
            double yawTurn = Math.toRadians(-14 * f);
            Vec3 look = new Vec3(dir.x * Math.cos(yawTurn) - dir.z * Math.sin(yawTurn), dir.y, dir.x * Math.sin(yawTurn) + dir.z * Math.cos(yawTurn));
            track(t, eye, eye.add(look));
        });
        d.client(mc -> {
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(8);
        });
    }

    // ------------------------------------------------------------------ the HUD at work

    /** Progressive Block Drops: first target, the order the film wants (the real one is shuffled). */
    private void setBlockOrder(List<String> order) {
        d.server(server -> {
            try {
                ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
                data.setProgressiveBlocksOrder(new ArrayList<>(order));
                data.setProgressiveBlocksIndex(0);
                Field cached = Chal_44_ProgressiveBlockDrops.class.getDeclaredField("cachedIndex");
                cached.setAccessible(true);
                cached.setInt(null, -1);
                Chal_44_ProgressiveBlockDrops.syncProgressToAll(server, data);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** First person, HUD on: walking up to a pile of logs and chopping it, the run timer and the target card on screen. */
    private void hudRun() {
        open(44);
        BlockPos s = spawn();
        flat(s, -6, -8, 24, 8, "minecraft:grass_block");
        BlockPos pile = s.offset(9, 0, 0);
        List<BlockPos> logs = new ArrayList<>();
        int[][] order = {{1, 0}, {0, 0}, {1, -1}, {0, -1}, {1, 1}, {0, 1}};   // {height, sideways}: top middle first
        for (int[] o : order) {
            BlockPos b = pile.offset(0, o[0], o[1]);
            logs.add(b);
            d.cmd(String.format("setblock %d %d %d minecraft:oak_log", b.getX(), b.getY(), b.getZ()));
        }
        tree(s.offset(14, 0, -6), 5);
        tree(s.offset(15, 0, 5), 4);
        setBlockOrder(List.of("minecraft:oak_log", "minecraft:dirt", "minecraft:oak_planks", "minecraft:sand"));
        d.cmd("item replace entity @p weapon.mainhand with minecraft:iron_axe");
        Vec3 start = feet(s.offset(1, 0, 0));
        d.cam.playerPose(start, -90f, 6f, true);
        d.cam.player();
        d.run(30);
        d.hud(true);
        keys(true, false, false);
        boolean[] chopping = {false};
        d.shoot("hud_run", 8.0, t -> {
            Vec3 p = playerPos();
            if (!chopping[0] && p.x >= pile.getX() - 2.3) {
                keys(false, false, false);
                chopping[0] = true;
                attackKey(true);
            }
            if (chopping[0]) {
                // On the next log of the pile that is still standing.
                BlockPos next = d.ctx.computeOnClient(mc -> {
                    for (BlockPos b : logs) {
                        if (mc.level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) return b;
                    }
                    return null;
                });
                if (next != null) d.cam.playerAim(Vec3.atCenterOf(next));
            }
        });
        attackKey(false);
        keys(false, false, false);
        d.hud(false);
    }

    /** Progressive Block Drops: a dirt block gives nothing, the target log unlocks and the card moves on. */
    private void progressiveDrops() {
        open(44);
        BlockPos s = spawn();
        flat(s, -8, -10, 12, 8, "minecraft:grass_block");
        setBlockOrder(List.of("minecraft:oak_log", "minecraft:dirt", "minecraft:sand", "minecraft:gravel"));
        d.cmd("item replace entity @p weapon.mainhand with minecraft:iron_axe");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 10f, true);
        BlockPos dirt1 = s.offset(2, 0, 0), log = s.offset(3, 0, 0), dirt2 = s.offset(4, 0, 0);
        d.cmd(String.format("setblock %d %d %d minecraft:dirt", dirt1.getX(), dirt1.getY(), dirt1.getZ()));
        d.cmd(String.format("setblock %d %d %d minecraft:oak_log", log.getX(), log.getY(), log.getZ()));
        d.cmd(String.format("setblock %d %d %d minecraft:dirt", dirt2.getX(), dirt2.getY(), dirt2.getZ()));
        d.run(20);
        d.hud(true);
        // From behind and a little above, from the south-west side: the player at the left, his three blocks running away ahead of him.
        preroll(at.add(-3.8, 3.0, 3.4), at.add(2.0, 0.4, 0));
        d.shoot("progressive_drops", 8.0, t -> {
            if (t == 30) breakBlock(dirt1);
            if (t == 74) breakBlock(log);
            if (t == 118) breakBlock(dirt2);
            double f = FilmDirector.ease(t, 0, 160);
            Vec3 eye = at.add(FilmDirector.lerp(-3.8, -3.0, f), FilmDirector.lerp(3.0, 2.5, f), FilmDirector.lerp(3.4, 2.8, f));
            track(t, eye, at.add(2.0, 0.4, 0));
            // The player looks at the block he is about to break.
            BlockPos next = t < 50 ? dirt1 : t < 95 ? log : dirt2;
            d.cam.playerAim(Vec3.atCenterOf(next));
        });
        d.hud(false);
    }

    private void breakBlock(BlockPos b) {
        d.server(server -> {
            ServerPlayer p = d.player(server);
            p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            p.gameMode.destroyBlock(b);
        });
    }

    // ------------------------------------------------------------------ the End

    private void dragon() {
        open();
        d.cmd("gamemode creative @p");
        d.cmd("execute in minecraft:the_end run forceload add -48 -48 48 48");
        d.run(60);
        int groundY = d.fromServer(server -> {
            ServerLevel end = server.getLevel(Level.END);
            end.getChunk(1, 1);
            return end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 18, 18);
        });
        FilmDirector.LOG.info("[Film] the island's ground at (18, 18) is y={}", groundY);
        d.cmd(String.format("execute in minecraft:the_end run tp @p 18.5 %d 18.5", groundY));
        d.settle(900);
        d.hud(false);
        Vec3 ground = new Vec3(18.5, groundY, 18.5);
        // The player looks towards the island's middle; the dragon circles there, 34 blocks ahead and 11.5 up. The film flies
        // it round that circle itself (a lap in 9.5 s) so it is in the picture the whole shot.
        Vec3 fwd = new Vec3(-Math.sqrt(0.5), 0, -Math.sqrt(0.5));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 centre = ground.add(fwd.scale(34)).add(0, 11.5, 0);
        double radius = 13, lap = 9.5, phi0 = 215;
        java.util.function.DoubleFunction<Vec3[]> flight = frame -> {
            double phi = Math.toRadians(phi0 + 360.0 * (frame / 20.0) / lap);
            Vec3 pos = centre.add(right.scale(radius * Math.sin(phi))).add(fwd.scale(-radius * Math.cos(phi))).add(0, 1.4 * Math.sin(phi * 2), 0);
            Vec3 tan = right.scale(Math.cos(phi)).add(fwd.scale(Math.sin(phi)));
            return new Vec3[]{pos, tan};
        };
        java.util.function.BiConsumer<Vec3, Vec3> place = (pos, tan) -> d.server(server -> {
            ServerLevel end = server.getLevel(Level.END);
            List<EnderDragon> found = end.getEntitiesOfClass(EnderDragon.class, new net.minecraft.world.phys.AABB(-300, 0, -300, 300, 300, 300));
            if (found.isEmpty()) {
                FilmDirector.LOG.warn("[Film] there is no dragon in the End");
                return;
            }
            // His head points along (sin yaw, -cos yaw): the other way round from every other mob.
            float yaw = (float) Math.toDegrees(Math.atan2(tan.x, -tan.z));
            for (EnderDragon e : found) {
                e.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
                e.setDeltaMovement(tan.scale(2 * Math.PI * radius / (lap * 20)));
            }
        });
        Vec3[] f0 = flight.apply(0);
        place.accept(f0[0], f0[1]);
        d.cam.playerLook(ground, f0[0], true);
        Vec3 eye0 = ground.add(fwd.scale(-5.0)).add(right.scale(1.3)).add(0, 2.0, 0);
        preroll(eye0, ground.add(fwd.scale(30)).add(0, 10.0, 0));
        d.cam.nightVision();
        d.run(3);
        d.shoot("dragon", 10.0, t -> {
            Vec3[] fl = flight.apply(t);
            place.accept(fl[0], fl[1]);
            d.cam.playerLook(ground, fl[0], false);
            // Behind the player and a little to his right, the dragon over his shoulder; the camera drifts round him slowly.
            double ang = Math.toRadians(FilmDirector.lerp(-7, 7, FilmDirector.ease(t, 0, 200)));
            Vec3 back = fwd.scale(-5.0);
            Vec3 off = new Vec3(back.x * Math.cos(ang) - back.z * Math.sin(ang), 0, back.x * Math.sin(ang) + back.z * Math.cos(ang));
            Vec3 eye = ground.add(off).add(right.scale(1.3)).add(0, FilmDirector.lerp(2.0, 2.4, FilmDirector.ease(t, 0, 200)), 0);
            track(t, eye, ground.add(fwd.scale(30)).add(0, 10.0, 0));
        });
    }

    // ------------------------------------------------------------------ Chunk Hunt

    private static final String HUNT_TAG = "challengecraft_chunk_hunt_target";

    /** Into a new chunk: the border slams shut round it, the chunk changes, a glowing target appears - and gets hunted. */
    private void chunkHunt() {
        open(38);
        d.cmd("effect give @p minecraft:resistance infinite 4 true");
        d.cmd("item replace entity @p weapon.mainhand with minecraft:iron_sword");
        d.run(40);
        // The spawn chunk's own hunt (the challenge starts one as the world opens) is over before the shot.
        d.cmd("kill @e[tag=" + HUNT_TAG + "]");
        d.run(30);
        BlockPos s = spawn();
        int[] chunk = huntChunk(s);
        int edge = chunk[0] << 4;                              // the west edge of the chunk the player runs into
        double zc = chunk[1] * 16 + 8.0;                       // the middle of its row
        double zl = zc - 0.5;                                  // the line the player and the target run along
        double y = s.getY();
        Vec3 start = new Vec3(edge - 13.0, y, zl);
        Vec3 husk = new Vec3(edge + 7.5, y, zl);
        d.cam.playerPose(start, -90f, 0f, true);
        d.run(20);
        // Coming to the start chunk began a hunt of its own (and changed its blocks): it is over, and the chunk levelled
        // again, before the shot - from then on the next chunk is the new one.
        for (int i = 0; i < 3; i++) {
            d.cmd("kill @e[tag=" + HUNT_TAG + "]");
            d.run(12);
        }
        flat(new BlockPos(edge, s.getY(), (int) zc), -24, -20, 24, 20, "minecraft:grass_block");
        d.cam.playerPose(start, -90f, 0f, true);
        d.run(15);
        d.hud(false);
        Vec3[] look = {new Vec3(edge - 10.5, y + 0.8, zl)};
        preroll(new Vec3(edge - 11.0, y + 3.8, zc + 6.2), look[0]);
        keys(true, true, true);
        boolean[] replaced = {false};
        int[] lastHit = {-99};
        d.shoot("chunk_hunt", 10.5, t -> {
            Vec3 p = playerPos();
            Vec3 mob = hunted();
            if (mob != null && !replaced[0]) {
                // A husk (it does not burn in the sun) takes the place of the random target: same tags, same glow.
                replaceTarget(husk);
                replaced[0] = true;
                mob = husk;
            }
            if (replaced[0] && mob != null) {
                double dist = Math.hypot(mob.x - p.x, mob.z - p.z);
                d.cam.playerAim(mob.add(0, 1.0, 0));
                if (p.x < edge + 1.5 && dist > 2.4) keys(true, true, true);
                else keys(false, false, true);
                if (dist <= 2.4 && t - lastHit[0] >= 13) {
                    lastHit[0] = t;
                    d.server(server -> {
                        ServerPlayer sp = d.player(server);
                        List<Entity> targets = tagged(server.overworld().getAllEntities());
                        if (!targets.isEmpty()) {
                            sp.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                            sp.attack(targets.get(0));
                        }
                    });
                }
            } else if (!replaced[0]) {
                d.cam.playerAim(new Vec3(edge + 12, y + 1.6, zl));
            } else {
                keys(false, false, true);   // the target is down
            }
            // From the south side of the chunk, inside the border: the player and the target, the wall closing behind.
            Vec3 focus = replaced[0] && mob != null ? p.lerp(mob, 0.5) : p.add(2.4, 0, 0);
            Vec3 want = new Vec3(Math.max(edge - 13.0, Math.min(edge + 8.0, focus.x)), y + 0.8, zl);
            look[0] = t < 2 ? want : look[0].lerp(want, 0.12);
            double f = FilmDirector.ease(t, 0, 210);
            Vec3 eye = new Vec3(look[0].x - 0.5, y + FilmDirector.lerp(3.8, 3.2, f), zc + FilmDirector.lerp(6.2, 5.6, f));
            track(t, eye, look[0]);
        });
        keys(false, false, true);
    }

    /**
     * The chunk the hunt shot runs into: close to the spawn, and one the challenge turns into a plain, bright cube. The hunt
     * changes the whole chunk into one random block; some of those have holes (a shelf) or are see-through.
     */
    private int[] huntChunk(BlockPos s) {
        return d.fromServer(server -> {
            ServerLevel w = server.overworld();
            int sx = s.getX() >> 4, sz = s.getZ() >> 4;
            java.util.regex.Pattern nice = java.util.regex.Pattern.compile(
                    "(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red)_(concrete|wool|terracotta)"
                            + "|(gold|diamond|emerald|lapis|iron|redstone|copper)_block|(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|bamboo)_planks"
                            + "|purpur_block|quartz_block|bricks|prismarine");
            for (int r = 1; r <= 5; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        net.minecraft.world.level.block.Block b = net.kasax.challengecraft.challenges.Chal_16_RandomChunkBlocks
                                .getRandomBlockForChunk(w.getSeed(), new net.minecraft.world.level.ChunkPos(sx + dx, sz + dz));
                        String id = b.builtInRegistryHolder().key().identifier().getPath();
                        if (nice.matcher(id).matches()) {
                            FilmDirector.LOG.info("[Film] the hunt runs into chunk ({}, {}): {}", sx + dx, sz + dz, id);
                            return new int[]{sx + dx, sz + dz};
                        }
                    }
                }
            }
            return new int[]{sx + 1, sz};
        });
    }

    private static List<Entity> tagged(Iterable<Entity> all) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : all) {
            if (e.entityTags().contains(HUNT_TAG)) out.add(e);
        }
        return out;
    }

    /** The hunt's target mob, if one exists. */
    private Vec3 hunted() {
        return d.fromServer(server -> {
            for (Entity e : server.overworld().getAllEntities()) {
                if (e.entityTags().contains(HUNT_TAG) && e.isAlive()) return e.position();
            }
            return null;
        });
    }

    /** Swaps the random target for a husk at {@code at}, with the same tags and glow. */
    private void replaceTarget(Vec3 at) {
        d.server(server -> {
            ServerLevel w = server.overworld();
            for (Entity e : w.getAllEntities()) {
                if (e.entityTags().contains(HUNT_TAG) && e.isAlive()) {
                    List<String> tags = new ArrayList<>(e.entityTags());
                    e.discard();
                    Entity husk = net.minecraft.world.entity.EntityTypes.HUSK.create(w, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                    if (husk == null) return;
                    husk.snapTo(at.x, at.y, at.z, 90f, 0f);
                    for (String tag : tags) husk.addTag(tag);
                    husk.setGlowingTag(true);
                    if (husk instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();
                    w.addFreshEntity(husk);
                    return;
                }
            }
        });
    }

    // ------------------------------------------------------------------ the collections

    /** The widgets of the open screen, top to bottom. */
    private List<AbstractWidget> widgets() {
        return d.ctx.computeOnClient(mc -> {
            List<AbstractWidget> out = new ArrayList<>();
            for (GuiEventListener g : mc.gui.screen().children()) {
                if (g instanceof AbstractWidget w) out.add(w);
            }
            out.sort(Comparator.comparingInt(AbstractWidget::getY));
            return out;
        });
    }

    /** A collection screen scrolled slowly down from the top. */
    private void slowScroll(FilmMouse m, int from, int ticks, double pixels) {
        AbstractScrollArea list = null;
        for (AbstractWidget w : widgets()) {
            if (w instanceof AbstractScrollArea a) list = a;
        }
        if (list == null) return;
        AbstractScrollArea l = list;
        m.alongside(from, ticks, f -> l.setScrollAmount(pixels * f));
    }

    /** All Biomes: the card names the biome he stands in; then the checklist. */
    private void allBiomes() {
        open(49);
        BlockPos s = spawn();
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.run(40);
        for (int i = 0; i < 14; i++) d.cmd("challengecraft_skip_biome");
        d.run(10);
        d.hud(true);
        Vec3 eye0 = at.add(3.2, 1.5, 2.6);
        preroll(eye0, at.add(0, 1.1, 0));
        FilmMouse[] m = {null};
        d.shoot("all_biomes", 6.0, t -> {
            double f = FilmDirector.ease(t, 0, 120);
            Vec3 eye = at.add(FilmDirector.lerp(3.2, 2.6, f), FilmDirector.lerp(1.5, 1.9, f), FilmDirector.lerp(2.6, 2.2, f));
            track(t, eye, at.add(0, 1.1, 0));
            if (t == 50) {
                d.client(mc -> mc.setScreenAndShow(new AllBiomesScreen()));
                m[0] = new FilmMouse(d, 330, 120);
                m[0].pause(0);
                slowScroll(m[0], 10, 60, 420);
                // the pointer rests beside the list
                m[0].glide(300, 150, 1);
            }
            if (m[0] != null) m[0].tick(t - 50);
        });
        FilmCursor.shown = false;
        d.ctx.setScreen(() -> null);
        d.hud(false);
    }

    /** A trivia question: the screen over the world, the answer picked. */
    private void trivia() {
        open(36);
        BlockPos s = spawn();
        flat(s, -8, -8, 8, 8, "minecraft:grass_block");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.run(20);
        Vec3 eye0 = at.add(3.4, 1.4, 2.6);
        preroll(eye0, at.add(0, 1.1, 0));
        FilmMouse[] m = {null};
        d.shoot("trivia", 6.0, t -> {
            double f = FilmDirector.ease(t, 0, 120);
            Vec3 eye = at.add(FilmDirector.lerp(3.4, 2.8, f), FilmDirector.lerp(1.4, 1.7, f), FilmDirector.lerp(2.6, 2.2, f));
            track(t, eye, at.add(0, 1.1, 0));
            if (t == 6) {
                d.client(mc -> mc.setScreenAndShow(new TriviaScreen("Which mob was created by a coding mistake?",
                        List.of("Zombie", "Creeper", "Spider", "Skeleton"), 1)));
                List<AbstractWidget> answers = new ArrayList<>();
                for (AbstractWidget w : widgets()) if (w.getHeight() == 24) answers.add(w);
                AbstractWidget pick = answers.get(Math.min(1, answers.size() - 1));
                m[0] = new FilmMouse(d, pick.getX() + pick.getWidth() * 0.8, pick.getY() - 70);
                m[0].pause(14);
                m[0].glide(() -> new double[]{pick.getX() + pick.getWidth() * 0.5, pick.getY() + pick.getHeight() * 0.5}, 24).pause(16).click();
            }
            if (m[0] != null) m[0].tick(t - 6);
        });
        FilmCursor.shown = false;
        d.ctx.setScreen(() -> null);
    }

    /** All Items: the card with the item to find next, then the list of everything. */
    private void allItems() {
        open(22);
        BlockPos s = spawn();
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.run(40);
        d.cmd("challengecraft_skip_item 14");
        d.run(10);
        List<ItemStack> items = d.fromServer(server -> new ArrayList<>(ChallengeSavedData.get(server.overworld()).getAllItemsOrder()));
        int index = d.fromServer(server -> ChallengeSavedData.get(server.overworld()).getAllItemsIndex());
        FilmDirector.LOG.info("[Film] all items: {} on the list, at {}", items.size(), index);
        d.hud(true);
        Vec3 eye0 = at.add(3.2, 1.5, 2.6);
        preroll(eye0, at.add(0, 1.1, 0));
        FilmMouse[] m = {null};
        d.shoot("all_items", 6.0, t -> {
            double f = FilmDirector.ease(t, 0, 120);
            Vec3 eye = at.add(FilmDirector.lerp(3.2, 2.6, f), FilmDirector.lerp(1.5, 1.9, f), FilmDirector.lerp(2.6, 2.2, f));
            track(t, eye, at.add(0, 1.1, 0));
            if (t == 50) {
                d.client(mc -> mc.setScreenAndShow(new AllItemsScreen(items, index)));
                m[0] = new FilmMouse(d, 330, 120);
                slowScroll(m[0], 6, 64, 150);
                m[0].glide(300, 150, 1);
            }
            if (m[0] != null) m[0].tick(t - 50);
        });
        FilmCursor.shown = false;
        d.ctx.setScreen(() -> null);
        d.hud(false);
    }

    // ------------------------------------------------------------------ Bob

    private Vec3 bobPos() {
        return d.fromServer(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getName().getString().equals("Bob")) return p.position();
            }
            return null;
        });
    }

    /** A follow camera that eases after Bob instead of nailing him to the frame. */
    private final Vec3[] follow = {null};

    private void followBob(int t, Vec3 offset, double height) {
        Vec3 bob = bobPos();
        if (bob == null) return;
        follow[0] = follow[0] == null ? bob : follow[0].lerp(bob, 0.18);
        Vec3 eye = follow[0].add(offset);
        track(t, eye, follow[0].add(0, height, 0));
    }

    /** Bob chops a tree, mines a cobblestone wall, fights zombies - the commands of the bot, one task each. */
    private void bobWork() {
        open();
        BlockPos s = spawn();
        flat(s, -14, -14, 30, 14, "minecraft:grass_block");
        d.cmd("effect give @p minecraft:invisibility infinite 0 true");
        d.run(10);
        d.cmd(String.format(Locale.ROOT, "execute positioned %d %d %d run challengecraft_bot spawn Bob", s.getX(), s.getY(), s.getZ()));
        d.run(30);
        d.cmd("give Bob minecraft:iron_axe");
        d.cmd("give Bob minecraft:iron_pickaxe");
        d.cmd("give Bob minecraft:diamond_sword");
        d.cmd("give Bob minecraft:cooked_beef 16");
        BlockPos tree = s.offset(7, 0, -3);
        tree(tree, 5);
        BlockPos wall = s.offset(-7, 0, 5);
        d.cmd(String.format("fill %d %d %d %d %d %d minecraft:cobblestone", wall.getX(), wall.getY(), wall.getZ(), wall.getX() + 3, wall.getY() + 1, wall.getZ()));
        d.run(10);
        // 1: chopping
        Vec3 off1 = new Vec3(-3.0, 1.8, 3.6);
        follow[0] = null;
        d.cmd("challengecraft_bot mine Bob oak_log 5");
        d.run(2);
        Vec3 b0 = bobPos();
        if (b0 != null) preroll(b0.add(off1), b0.add(0, 1.0, 0));
        d.shoot("bob_1", 8.0, t -> followBob(t, off1, 1.0));
        // 2: mining a cobblestone wall
        Vec3 off2 = new Vec3(3.4, 1.8, 3.4);
        follow[0] = null;
        d.cmd("challengecraft_bot mine Bob cobblestone 6");
        d.run(2);
        Vec3 b1 = bobPos();
        if (b1 != null) preroll(b1.add(off2), b1.add(0, 1.0, 0));
        d.shoot("bob_2", 8.0, t -> followBob(t, off2, 1.0));
        // 3: a fight
        d.cmd("challengecraft_bot stop Bob");
        // Husks: a zombie that does not burn in the sun.
        d.cmd("execute at Bob run summon minecraft:husk ~6 ~ ~2 {PersistenceRequired:1b,Tags:[\"film_zombie\"]}");
        d.cmd("execute at Bob run summon minecraft:husk ~7 ~ ~-3 {PersistenceRequired:1b,Tags:[\"film_zombie\"]}");
        d.cmd("execute at Bob run summon minecraft:husk ~9 ~ ~0 {PersistenceRequired:1b,Tags:[\"film_zombie\"]}");
        d.cmd("challengecraft_bot kill Bob husk 3");
        Vec3 off3 = new Vec3(-2.2, 2.4, 4.4);
        follow[0] = null;
        d.run(2);
        Vec3 b2 = bobPos();
        if (b2 != null) preroll(b2.add(off3), b2.add(3, 1.0, 0));
        d.shoot("bob_3", 8.0, t -> {
            Vec3 bob = bobPos();
            if (bob == null) return;
            Vec3 zom = d.fromServer(server -> {
                for (Entity e : server.overworld().getAllEntities()) {
                    if (e.entityTags().contains("film_zombie") && e.isAlive()) return e.position();
                }
                return null;
            });
            Vec3 focus = zom != null ? bob.lerp(zom, 0.4) : bob;
            follow[0] = follow[0] == null ? focus : follow[0].lerp(focus, 0.15);
            track(t, follow[0].add(off3), follow[0].add(0, 1.0, 0));
        });
    }

    /** The Lockout board with tiles claimed by both sides; the pointer rests on a few of them. */
    private void bobBoard() {
        open(40);
        BlockPos s = spawn();
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.cmd("challengecraft_bot spawn Bob");
        d.run(20);
        d.cmd("execute as @p run challengecraft_bot lockout Bob");
        d.run(60);
        d.ctx.setScreen(() -> null);
        d.run(5);
        d.server(server -> {
            ServerPlayer human = d.player(server);
            net.kasax.challengecraft.challenges.Chal_40_LockoutBingo.debugClaimTiles(human, 4);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getName().getString().equals("Bob")) net.kasax.challengecraft.challenges.Chal_40_LockoutBingo.debugClaimTiles(p, 5);
            }
        });
        d.run(20);
        d.cam.cutTo(at.add(0, 6, 0), at.add(20, 9, 0));
        d.ctx.setScreen(net.kasax.challengecraft.client.screen.LockoutBingoBoardScreen::new);
        d.run(10);
        // Tile centres in GUI coordinates (the board is centred, 5 x 5): the first row is the human's, the second Bob's.
        FilmMouse m = new FilmMouse(d, 180, 470);
        m.pause(8);
        m.glide(115, 252, 26).pause(26);
        m.glide(147, 283, 16).pause(26);
        m.glide(243, 283, 20).pause(26);
        m.glide(243, 252, 14).pause(14);
        FilmDirector.LOG.info("[Film] bob_board script is {} ticks long", m.at());
        d.shoot("bob_board", 8.0, m::tick);
        FilmCursor.shown = false;
        d.ctx.setScreen(() -> null);
    }

    // ------------------------------------------------------------------ name tags

    /** Two players side by side: the film player and Bob, their name tags with level and colour. */
    private void nametags() {
        d.profileXp(PLAYER_XP);
        d.openWorld(SEED, false);
        d.cmd("time set 6000");
        d.cmd("gamerule advance_time false");
        d.cmd("gamerule spawn_mobs false");
        d.run(10);
        BlockPos s = spawn();
        flat(s, -8, -8, 8, 8, "minecraft:grass_block");
        Vec3 at = feet(s);
        d.cam.playerPose(at, 165f, 0f, true);
        d.cmd(String.format(Locale.ROOT, "execute positioned %.1f %d %.1f run challengecraft_bot spawn Bob", at.x + 2.5, s.getY(), at.z));
        d.run(30);
        d.cmd(String.format(Locale.ROOT, "tp Bob %.1f %d %.1f 195 0", at.x + 2.5, s.getY(), at.z));
        d.server(server -> {
            ServerPlayer human = d.player(server);
            XpManager.setXp(human.getUUID(), PLAYER_XP);
            LevelManager.sync(human);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getName().getString().equals("Bob")) {
                    XpManager.setXp(p.getUUID(), BOB_XP);
                    LevelManager.sync(p);
                }
            }
        });
        d.run(20);
        Cam.ownNameTag = true;
        d.hud(true);          // the game hides every name tag with the HUD
        d.cam.playerPose(at, 165f, 0f, true);
        d.run(10);
        Vec3 mid = at.add(1.25, 1.7, 0);
        Vec3 eye0 = mid.add(-0.4, 0.0, -6.8);
        preroll(eye0, mid.add(0, 0.1, 0));
        d.shoot("nametags", 6.0, t -> {
            double f = FilmDirector.ease(t, 0, 120);
            Vec3 eye = mid.add(FilmDirector.lerp(-0.4, 0.4, f), FilmDirector.lerp(0.0, 0.25, f), FilmDirector.lerp(-6.8, -6.2, f));
            track(t, eye, mid.add(0, 0.1, 0));
        });
        // Closer: a slow pan along the two of them, one tag at a time, big.
        Vec3 bob = at.add(2.5, 0, 0);
        preroll(new Vec3(bob.x, at.y + 1.7, at.z - 3.3), new Vec3(bob.x, at.y + 1.9, at.z));
        d.shoot("nametags_close", 6.0, t -> {
            double f = FilmDirector.ease(t, 14, 106);
            double x = FilmDirector.lerp(bob.x, at.x, f);
            track(t, new Vec3(x, at.y + 1.7, at.z - FilmDirector.lerp(3.3, 3.0, f)), new Vec3(x, at.y + 1.9, at.z));
        });
        d.hud(false);
    }

    // ------------------------------------------------------------------ more challenges, the player in view

    /** A row of blocks along x in front of the player, {@code high} tall: {@code ids} cycle over them. */
    private List<BlockPos> wall(BlockPos from, int wide, int high, String... blocks) {
        List<BlockPos> out = new ArrayList<>();
        int i = 0;
        for (int x = 0; x < wide; x++) {
            for (int y = 0; y < high; y++) {
                BlockPos b = from.offset(x, y, 0);
                d.cmd(String.format("setblock %d %d %d minecraft:%s", b.getX(), b.getY(), b.getZ(), blocks[i++ % blocks.length]));
                out.add(b);
            }
        }
        return out;
    }

    /** Ores broken one after another - and every drop flies up into the sky; the player does it. */
    private void upsideDown() {
        open(34);
        BlockPos s = spawn();
        flat(s, -6, -8, 12, 8, "minecraft:grass_block");
        List<BlockPos> ores = wall(s.offset(2, 0, 0), 3, 2, "diamond_ore", "gold_ore", "emerald_ore", "iron_ore", "redstone_ore", "lapis_ore");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 8f, true);
        d.cmd("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        d.run(20);
        d.hud(false);
        preroll(at.add(2.4, 1.8, -8.2), at.add(2.2, 1.0, 0));
        d.shoot("upside_down_v2", 8.0, t -> {
            int i = (t - 20) / 14;
            if (t >= 20 && (t - 20) % 14 == 0 && i < ores.size()) breakBlock(ores.get(i));
            double up = FilmDirector.ease(t, 40, 150);
            track(t, at.add(2.4, FilmDirector.lerp(1.8, 2.4, up), -8.2), at.add(2.2, FilmDirector.lerp(1.0, 4.4, up), 0));
            d.cam.playerAim(Vec3.atCenterOf(ores.get(Math.max(0, Math.min(ores.size() - 1, i)))));
        });
    }

    /** One giant creeper, a giant spider, tiny ones round them - and the player in the middle of it. */
    private void sizeMatters() {
        open(33);
        BlockPos s = spawn();
        flat(s, -14, -12, 22, 12, "minecraft:grass_block");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 0f, true);
        d.cmd("effect give @p minecraft:resistance infinite 4 true");
        BlockPos m = s.offset(11, 0, 0);
        sized("creeper", m.offset(0, 0, -1), 2.0);
        sized("spider", m.offset(3, 0, 5), 1.6);
        sized("creeper", s.offset(4, 0, 2), -0.5);
        sized("creeper", s.offset(3, 0, -2), -0.5);
        sized("zombie", s.offset(5, 0, 0), -0.5);
        d.run(15);
        d.hud(false);
        preroll(at.add(-2.8, 1.8, 2.4), Vec3.atCenterOf(m).add(0, 2.4, 0));
        d.shoot("size_matters_v2", 8.0, t -> {
            double f = FilmDirector.ease(t, 0, 160);
            Vec3 eye = at.add(FilmDirector.lerp(-2.8, -2.4, f), FilmDirector.lerp(1.8, 1.6, f), FilmDirector.lerp(2.4, 3.4, f));
            track(t, eye, feet(m).add(FilmDirector.lerp(0, -3, f), FilmDirector.lerp(2.4, 1.6, f), 0));
        });
    }

    /** A mob with the challenge's own size modifier ({@code add}: scale = 1 + add) and no mind of its own. */
    private void sized(String type, BlockPos at, double add) {
        String tag = "film_" + type + "_" + Math.abs(at.getX()) + "_" + Math.abs(at.getZ());
        d.cmd(String.format("summon minecraft:%s %d %d %d {Tags:[\"%s\"],PersistenceRequired:1b,NoAI:1b,Silent:1b}", type, at.getX(), at.getY(), at.getZ(), tag));
        d.cmd(String.format(Locale.ROOT, "attribute @e[tag=%s,limit=1] minecraft:scale modifier add challengecraft:size_matters_scale %.2f add_value", tag, add));
    }

    /** A Skyblock island in the void, circled from the air - with the player on it. */
    private void skyblock() {
        open(11);
        d.settle(400);
        Vec3 at = d.fromServer(server -> d.player(server).position());
        FilmDirector.LOG.info("[Film] skyblock island at {}", at);
        d.cam.playerPose(at, 200f, 5f, true);
        d.run(10);
        d.hud(false);
        preroll(at.add(Math.cos(Math.toRadians(-40)) * 6.5, 3.0, Math.sin(Math.toRadians(-40)) * 6.5), at.add(0, 1.0, 0));
        d.shoot("skyblock_v2", 8.0, t -> {
            double ang = Math.toRadians(-40 + t * 0.55);
            Vec3 eye = at.add(Math.cos(ang) * 6.5, 3.0, Math.sin(ang) * 6.5);
            track(t, eye, at.add(0, 0.2, 0));
        });
    }

    /** Whatever a block is broken for, something else drops: a row of different blocks, the player breaking them. */
    private void randomBlockDrops() {
        open(14);
        BlockPos s = spawn();
        flat(s, -6, -8, 12, 8, "minecraft:grass_block");
        List<BlockPos> blocks = wall(s.offset(2, 0, 0), 6, 1, "dirt", "stone", "oak_log", "sand", "gravel", "coal_ore");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 12f, true);
        d.cmd("item replace entity @p weapon.mainhand with minecraft:iron_pickaxe");
        d.run(20);
        d.hud(false);
        preroll(at.add(2.8, 1.8, -7.4), at.add(2.6, 0.9, 0));
        d.shoot("random_block_drops", 8.0, t -> {
            int i = (t - 16) / 18;
            if (t >= 16 && (t - 16) % 18 == 0 && i < blocks.size()) breakBlock(blocks.get(i));
            double f = FilmDirector.ease(t, 0, 160);
            track(t, at.add(FilmDirector.lerp(2.8, 3.8, f), FilmDirector.lerp(1.8, 2.2, f), -7.4), at.add(FilmDirector.lerp(2.6, 3.4, f), 0.9, 0));
            d.cam.playerAim(Vec3.atCenterOf(blocks.get(Math.max(0, Math.min(blocks.size() - 1, i)))));
        });
    }

    private void throwDie() {
        d.server(server -> {
            ServerPlayer p = d.player(server);
            p.gameMode.useItem(p, p.level(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
        });
    }

    /** The die is thrown and tumbles, the ring shows how far he may go, and he walks exactly that far. */
    private void diceWalk() {
        open(46);
        BlockPos s = spawn();
        flat(s, -8, -8, 24, 8, "minecraft:grass_block");
        Vec3 at = feet(s);
        d.cam.playerPose(at, -90f, 20f, true);
        d.cmd("item replace entity @p weapon.mainhand with challengecraft:dice");
        d.run(25);
        d.hud(true);
        preroll(at.add(-1.5, 3.4, 5.6), at.add(2.5, 0.4, 0));
        keys(true, false, true);
        Vec3[] cam = {at};
        d.shoot("dice_walk", 9.0, t -> {
            if (t == 6) throwDie();
            Vec3 p = playerPos();
            cam[0] = cam[0].lerp(p, 0.12);
            track(t, cam[0].add(-1.5, 3.4, 5.6), cam[0].add(2.5, 0.4, 0));
        });
        keys(false, false, true);
        d.hud(false);
    }
}
