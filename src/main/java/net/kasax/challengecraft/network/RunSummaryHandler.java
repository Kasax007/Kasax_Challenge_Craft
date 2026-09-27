package net.kasax.challengecraft.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.client.screen.ChallengeRewardOverlay;
import net.kasax.challengecraft.client.screen.RunSummaryScreen;
import net.minecraft.client.Minecraft;

/**
 * Receives the run summary and puts the card on screen at a moment that does not fight anything else.
 *
 * <p>It does not open immediately. Killing the dragon is followed by the XP reward animation, and
 * often by the End poem — throwing a full screen over either would hide the celebration and get
 * dismissed unread. So the packet is parked and the card opens on the first client tick where the
 * reward animation has finished and nothing else owns the screen. If the player is reading the
 * credits, it simply waits until they are done.
 *
 * <p>The last summary is kept after it is shown so the card can be reopened later rather than being
 * a one-shot the player might miss entirely.
 */
@Environment(EnvType.CLIENT)
public final class RunSummaryHandler {
    private static RunSummaryPacket pending;
    private static RunSummaryPacket last;

    private RunSummaryHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RunSummaryPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    pending = payload;
                    last = payload;
                    net.kasax.challengecraft.client.RunHistory.onRunFinished(payload);
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pending == null) {
                return;
            }
            if (client.player == null || client.level == null) {
                return;         // still connecting, or already left the world
            }
            // 26.2 has no public getter for the current screen, but it has exactly the question
            // worth asking: canInterruptScreen() is
            //   (screen == null || screen.canInterruptWithAnotherScreen()) && !levelTeardownInProgress
            // — which also keeps the card from appearing while a world is being torn down.
            if (!client.canInterruptScreen() || ChallengeRewardOverlay.isPlaying()) {
                return;         // credits, inventory, or the XP animation still running
            }
            RunSummaryPacket show = pending;
            pending = null;
            client.setScreenAndShow(new RunSummaryScreen(show));
        });
    }

    /** The most recent summary, for reopening the card on demand. Null before the first run ends. */
    public static RunSummaryPacket last() {
        return last;
    }

    /** Reopens the last card, if there is one. Returns whether anything was shown. */
    public static boolean reopen() {
        if (last == null) {
            return false;
        }
        Minecraft.getInstance().setScreenAndShow(new RunSummaryScreen(last));
        return true;
    }

    /** Client statics survive a world change; a stale card from the previous world must not pop up. */
    public static void reset() {
        pending = null;
        last = null;
    }
}
