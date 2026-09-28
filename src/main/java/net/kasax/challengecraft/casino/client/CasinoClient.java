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
import net.kasax.challengecraft.casino.CasinoNet;
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

    private CasinoClient() {
    }

    public static void initialize() {
        ModelLayerRegistry.registerModelLayer(CroupierRenderer.LAYER, CroupierRenderer::createLayer);
        EntityRendererRegistry.register(CasinoRegistry.CROUPIER, CroupierRenderer::new);
        CasinoWorldRenderer.register();
        CasinoHud.register();

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
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.SlotResult.ID, (p, ctx) -> ctx.client().execute(() ->
                CasinoClientState.SLOTS.put(p.pos(), new SlotAnimation(p, CasinoClientState.clientTick))));
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
            CasinoClientState.blackjack = p;
            if (p.phase() == 2) {
                if (ctx.client().gui.screen() instanceof BlackjackScreen screen) screen.closeFromServer();
                return;
            }
            if (ctx.client().gui.screen() instanceof BlackjackScreen screen) {
                screen.update(p);
            } else {
                ctx.client().setScreenAndShow(new BlackjackScreen(p));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(CasinoNet.Fx.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (p.kind() == CasinoNet.Fx.OPEN_ROULETTE) {
                ctx.client().setScreenAndShow(new RouletteScreen(BlockPos.of(p.pos())));
                return;
            }
            if (p.kind() == CasinoNet.Fx.REVIVED) {
                autoRespawnTicks = 200;
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

        // Slot sound tracks: every machine plays its own timeline where it stands.
        float now = 0f;
        for (Iterator<Map.Entry<Long, SlotAnimation>> it = CasinoClientState.SLOTS.entrySet().iterator(); it.hasNext(); ) {
            SlotAnimation anim = it.next().getValue();
            float t = anim.elapsed(now);
            if (anim.finished(t)) {
                it.remove();
                continue;
            }
            BlockPos pos = BlockPos.of(anim.pos);
            if (client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 40 * 40) continue;
            for (SlotAnimation.SoundEvent e : anim.cuesBetween(t - 1f, t)) {
                SoundEvent sound = switch (e.cue()) {
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
                float volume = e.cue() == SlotAnimation.Cue.SPIN ? 0.55f : e.cue() == SlotAnimation.Cue.COINS ? 0.45f : 1.0f;
                client.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, sound,
                        SoundSource.BLOCKS, volume, e.pitch(), false);
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
