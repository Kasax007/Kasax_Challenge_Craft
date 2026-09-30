package net.kasax.challengecraft.casino.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.kasax.challengecraft.casino.CasinoDeviceBlock;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CasinoPartBlock;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.kasax.challengecraft.casino.DeviceSpace;
import net.kasax.challengecraft.casino.PlinkoGame;
import net.kasax.challengecraft.casino.CasinoRegistry;
import net.kasax.challengecraft.casino.CasinoSounds;
import net.kasax.challengecraft.casino.CrashGame;
import net.kasax.challengecraft.casino.CrashMath;
import net.kasax.challengecraft.challenges.Chal_50_HouseAlwaysWins;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Iterator;
import java.util.Map;

/**
 * Client entry point of the casino: packet receivers, key bindings, renderers and the per-tick
 * driver of every animation's sound track. Called from {@code ChallengeCraftClient}.
 */
@Environment(EnvType.CLIENT)
public final class CasinoClient {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("challengecraft", "casino"));
    public static final KeyMapping BET_UP = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.casino_bet_up", InputConstants.Type.KEYBOARD, InputConstants.KEY_EQUALS, CATEGORY));
    public static final KeyMapping BET_DOWN = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.casino_bet_down", InputConstants.Type.KEYBOARD, InputConstants.KEY_MINUS, CATEGORY));
    public static final KeyMapping ALL_IN = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.casino_all_in", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
    public static final KeyMapping CASH_OUT = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.challengecraft.casino_cash_out", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY));

    private static int autoRespawnTicks;
    /** Most chips one payout flight shows; enough for any amount up to a few million. */
    private static final int MAX_FLIGHT_CHIPS = 30;

    private CasinoClient() {
    }

    public static void initialize() {
        ModelLayerRegistry.registerModelLayer(CroupierRenderer.LAYER, CroupierRenderer::createLayer);
        EntityRendererRegistry.register(CasinoRegistry.CROUPIER, CroupierRenderer::new);
        CasinoWorldRenderer.register();
        CasinoHud.register();
        // Left-click takes one's own roulette chips back only where they lie; elsewhere it mines.
        net.kasax.challengecraft.casino.CasinoGames.CLIENT_OWN_BET = (pos, zone) -> {
            CasinoNet.RouletteState s = CasinoClientState.ROULETTE.get(pos.asLong());
            Minecraft mc = Minecraft.getInstance();
            if (s == null || s.phase() != net.kasax.challengecraft.casino.RouletteGame.BETTING || mc.player == null) return false;
            String me = mc.player.getName().getString();
            for (CasinoNet.RouletteBet b : s.bets()) {
                if (b.player().equals(me) && b.kind() == zone.a() && b.target() == zone.b()) return true;
            }
            return false;
        };

        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.State.ID, (p, ctx) -> ctx.client().execute(() -> {
            CasinoClientState.state = p;
            CasinoClientState.stateReceivedAt = System.currentTimeMillis();
        }));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.Devices.ID, (p, ctx) -> ctx.client().execute(() ->
                CasinoClientState.setDevices(p.data())));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.Cashier.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().gui.screen() instanceof CashierScreen screen) {
                screen.update(p);
            } else {
                ctx.client().setScreenAndShow(new CashierScreen(p));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.SlotResult.ID, (p, ctx) -> ctx.client().execute(() -> {
            CasinoClientState.SLOT_LAST.remove(p.pos());
            CasinoClientState.SLOTS.put(p.pos(), new SlotAnimation(p, CasinoClientState.clientTick));
        }));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.PlinkoBall.ID, (p, ctx) -> ctx.client().execute(() ->
                CasinoClientState.PLINKO_BALLS.add(new CasinoClientState.PlinkoDrop(p, CasinoClientState.clientTick, -1))));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.RouletteState.ID, (p, ctx) -> ctx.client().execute(() -> {
            CasinoClientState.ROULETTE.put(p.pos(), p);
            CasinoClientState.ROULETTE_RECEIVED.put(p.pos(), CasinoClientState.clientTick);
        }));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.CrashState.ID, (p, ctx) -> ctx.client().execute(() -> {
            CasinoNet.CrashState before = CasinoClientState.CRASH.get(p.pos());
            CasinoClientState.CRASH.put(p.pos(), p);
            CasinoClientState.CRASH_RECEIVED.put(p.pos(), CasinoClientState.clientTick);
            if (before != null && before.phase() == CrashGame.FLYING && p.phase() == CrashGame.CRASHED) {
                CasinoWorldRenderer.onCrash(p);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.Blackjack.ID, (p, ctx) -> ctx.client().execute(() -> {
            Minecraft mc = ctx.client();
            boolean mine = mc.player != null && p.player().equals(mc.player.getName().getString());
            if (p.seat() >= 0) {
                // Played at a counter: drawn in the world for everybody watching, no screen.
                if (p.phase() == 2) {
                    CasinoClientState.BLACKJACK.remove(p.player());
                } else {
                    CasinoClientState.BjView v = CasinoClientState.BLACKJACK.get(p.player());
                    if (v == null || (v.state.phase() == 1 && p.phase() == 0)) {
                        v = new CasinoClientState.BjView(p); // a new hand after a push starts from scratch
                        CasinoClientState.BLACKJACK.put(p.player(), v);
                    }
                    CounterView.schedule(v, p, CasinoClientState.clientTick);
                    v.stateTick = CasinoClientState.clientTick;
                }
                if (mine && mc.gui.screen() instanceof BlackjackScreen screen) screen.closeFromServer();
                return;
            }
            if (!mine) return;
            // The fallback: played where the player fell, in the blackjack screen.
            CasinoClientState.blackjack = p;
            if (p.phase() == 2) {
                if (mc.gui.screen() instanceof BlackjackScreen screen) screen.closeFromServer();
                return;
            }
            if (mc.gui.screen() instanceof BlackjackScreen screen) {
                screen.update(p);
            } else {
                mc.setScreenAndShow(new BlackjackScreen(p));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.Pending.ID, (p, ctx) -> ctx.client().execute(() ->
                CasinoClientState.pending = p));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.Fx.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (p.kind() == CasinoNet.Fx.OPEN_ROULETTE) {
                ctx.client().setScreenAndShow(new RouletteScreen(BlockPos.of(p.pos())));
                return;
            }
            if (p.kind() == CasinoNet.Fx.REVIVED) {
                autoRespawnTicks = 200;
            }
            if (p.kind() == CasinoNet.Fx.DEPOSIT && p.pos() != 0L) {
                BlockPos c = BlockPos.of(p.pos());
                // The croupier pays out in real chips: the credited amount as the chips that make
                // it up, largest first (a rest below the smallest chip is only on the account).
                int[] levels = DeviceLayouts.chipsFor(p.amount() / 100, MAX_FLIGHT_CHIPS);
                if (levels.length > 0) {
                    CasinoClientState.FLIGHTS.add(new CasinoClientState.ChipFlight(
                            new net.minecraft.world.phys.Vec3(c.getX() + 1.1, c.getY() + 1.35, c.getZ() + 0.5),
                            CasinoClientState.clientTick, levels));
                }
            }
            CasinoHud.fx(p);
        }));

        ClientTickEvents.END_CLIENT_TICK.register(CasinoClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CasinoClientState.clear());
    }

    private static void tick(Minecraft client) {
        CasinoClientState.clientTick++;
        if (client.player == null || client.level == null) return;

        // A won blackjack hand "dies" into the respawn; skip the death screen for the player.
        if (autoRespawnTicks > 0) {
            autoRespawnTicks--;
            if (client.player.isDeadOrDying()) {
                client.player.respawn();
                autoRespawnTicks = 0;
            }
        }

        if (!Chal_50_HouseAlwaysWins.isActive()) {
            while (BET_UP.consumeClick()) { }
            while (BET_DOWN.consumeClick()) { }
            while (ALL_IN.consumeClick()) { }
            while (CASH_OUT.consumeClick()) { }
            return;
        }
        boolean inWorld = client.gui.screen() == null;
        while (BET_UP.consumeClick()) if (inWorld) send(CasinoNet.Action.BET_UP);
        while (BET_DOWN.consumeClick()) if (inWorld) send(CasinoNet.Action.BET_DOWN);
        while (ALL_IN.consumeClick()) if (inWorld) send(CasinoNet.Action.BET_ALL_IN);
        while (CASH_OUT.consumeClick()) send(CasinoNet.Action.CRASH_CASH_OUT);

        CasinoClientState.aim = aimAt(client);

        // Counters: cards landing, chips arriving.
        for (CasinoClientState.BjView v : CasinoClientState.BLACKJACK.values()) {
            BlockPos counter = counterPos(client, v.state.seat());
            if (counter != null) CounterView.tickSounds(client, v, counter);
        }
        net.minecraft.world.phys.Vec3 chest = CasinoWorldRenderer.chipTarget(client, 0f);
        for (Iterator<CasinoClientState.ChipFlight> it = CasinoClientState.FLIGHTS.iterator(); it.hasNext(); ) {
            CasinoClientState.ChipFlight f = it.next();
            long t = CasinoClientState.clientTick - f.startTick() - CasinoWorldRenderer.FLIGHT_DELAY;
            for (int i = 0; i < f.count(); i++) {
                if (t == (long) i * CasinoWorldRenderer.FLIGHT_GAP + CasinoWorldRenderer.FLIGHT_TICKS) {
                    client.level.playLocalSound(chest.x, chest.y, chest.z, CasinoSounds.CHIP, SoundSource.PLAYERS, 0.35f,
                            0.9f + client.level.getRandom().nextFloat() * 0.4f, false);
                }
            }
            if (t > (long) f.count() * CasinoWorldRenderer.FLIGHT_GAP + CasinoWorldRenderer.FLIGHT_TICKS + 2) it.remove();
        }

        // Slot sound tracks: every machine plays its own timeline where it stands. A finished play
        // stays on the reels until the machine spins again.
        float now = 0f;
        for (Iterator<Map.Entry<Long, SlotAnimation>> it = CasinoClientState.SLOTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, SlotAnimation> entry = it.next();
            SlotAnimation anim = entry.getValue();
            float t = anim.elapsed(now);
            if (anim.finished(t)) {
                CasinoClientState.SLOT_LAST.put(entry.getKey(), anim);
                it.remove();
                continue;
            }
            BlockPos pos = BlockPos.of(anim.pos);
            if (client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 40 * 40) continue;
            for (SlotAnimation.SoundEvent e : anim.cuesBetween(t - 1f, t)) {
                SoundEvent sound = switch (e.cue()) {
                    case LEVER -> net.minecraft.sounds.SoundEvents.LEVER_CLICK;
                    case SPIN -> CasinoSounds.REEL_SPIN;
                    case STOP -> CasinoSounds.REEL_STOP;
                    case SCATTER -> CasinoSounds.SCATTER;
                    case TENSION -> CasinoSounds.REEL_TENSION;
                    case WIN_SMALL -> CasinoSounds.WIN_SMALL;
                    case WIN_BIG -> CasinoSounds.WIN_BIG;
                    case WIN_EPIC -> CasinoSounds.WIN_EPIC;
                    case FREE_SPINS -> CasinoSounds.FREE_SPINS;
                    case EXPAND -> CasinoSounds.EXPAND;
                    case COINS -> CasinoSounds.COINS;
                };
                float volume = e.cue() == SlotAnimation.Cue.SPIN ? 0.45f : e.cue() == SlotAnimation.Cue.COINS ? 0.4f : 0.8f;
                client.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, sound,
                        SoundSource.BLOCKS, volume, e.pitch(), false);
            }
            SlotEffects.tick(client.level, anim, t);
        }

        // Plinko balls: a soft tick on every peg, and bookkeeping once they land.
        String me = client.player.getName().getString();
        for (Iterator<CasinoClientState.PlinkoDrop> it = CasinoClientState.PLINKO_BALLS.iterator(); it.hasNext(); ) {
            CasinoClientState.PlinkoDrop d = it.next();
            long t = CasinoClientState.clientTick - d.startTick();
            BlockPos pos = BlockPos.of(d.ball().pos());
            if (t >= PlinkoGame.FALL_TICKS) {
                CasinoClientState.PlinkoDrop landed = new CasinoClientState.PlinkoDrop(d.ball(), d.startTick(), CasinoClientState.clientTick);
                CasinoClientState.PLINKO_LAST.put(d.ball().pos(), landed);
                if (d.ball().player().equals(me)) CasinoClientState.PLINKO_MINE.put(d.ball().pos(), landed);
                it.remove();
                continue;
            }
            if (t >= 8 && (t - 8) % 4 == 0 && client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) < 24 * 24) {
                client.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, CasinoSounds.TICK,
                        SoundSource.BLOCKS, 0.18f, 1.5f + client.level.getRandom().nextFloat() * 0.5f, false);
            }
        }

        // Crash rockets: smoke trail and sparks while flying.
        for (CasinoNet.CrashState s : CasinoClientState.CRASH.values()) {
            if (s.phase() != CrashGame.FLYING) continue;
            BlockPos pos = BlockPos.of(s.pos());
            float flight = CasinoWorldRenderer.flightTicks(s, 0f);
            double y = pos.getY() + 0.5 + CasinoWorldRenderer.rocketHeight(flight);
            double x = pos.getX() + 0.5;
            double z = pos.getZ() + 0.5;
            client.level.addParticle(ParticleTypes.FIREWORK, x, y - 0.3, z,
                    (client.level.getRandom().nextDouble() - 0.5) * 0.05, -0.15, (client.level.getRandom().nextDouble() - 0.5) * 0.05);
            client.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y - 0.6, z, 0, 0.01, 0);
            if (CrashMath.multiplierAt((int) flight) > 10) {
                client.level.addParticle(ParticleTypes.FLAME, x, y - 0.4, z, 0, -0.1, 0);
            }
        }
    }

    /** The device spot under the crosshair, if any. */
    private static CasinoClientState.Aim aimAt(Minecraft client) {
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos master = CasinoPartBlock.master(client.level, hit.getBlockPos());
        BlockState state = client.level.getBlockState(master);
        if (!(state.getBlock() instanceof CasinoDeviceBlock block)) return null;
        net.minecraft.core.Direction facing = state.getValue(CasinoDeviceBlock.FACING);
        net.minecraft.world.phys.Vec3 local = DeviceSpace.toDevice(master, facing, hit.getLocation());
        DeviceLayouts.Zone zone;
        if (block.getDeviceType() == net.kasax.challengecraft.casino.DeviceType.CASHIER) {
            int index = CasinoDeviceBlock.counterIndex(client.level, master, facing);
            CasinoClientState.BjView mine = CasinoClientState.BLACKJACK.get(client.player.getName().getString());
            zone = DeviceLayouts.counterZone(index, mine != null && mine.state.seat() == index, local.x, local.y, local.z);
        } else {
            zone = DeviceLayouts.zoneAt(block.getDeviceType(), local);
        }
        return new CasinoClientState.Aim(master, block.getDeviceType(), zone);
    }

    /** The counter a seat plays at, found among the known devices (the booth's counters). */
    static BlockPos counterPos(Minecraft client, int seat) {
        for (CasinoClientState.DevicePos d : CasinoClientState.DEVICES) {
            if (d.type() != net.kasax.challengecraft.casino.DeviceType.CASHIER) continue;
            BlockState st = client.level.getBlockState(d.pos());
            if (!(st.getBlock() instanceof CasinoDeviceBlock)) continue;
            if (CasinoDeviceBlock.counterIndex(client.level, d.pos(), st.getValue(CasinoDeviceBlock.FACING)) == seat) return d.pos();
        }
        return null;
    }

    public static void send(int action) {
        if (ClientPlayNetworking.canSend(CasinoNet.Action.ID)) {
            ClientPlayNetworking.send(CasinoNet.Action.of(action));
        }
    }

    public static void send(CasinoNet.Action action) {
        if (ClientPlayNetworking.canSend(CasinoNet.Action.ID)) {
            ClientPlayNetworking.send(action);
        }
    }
}
