package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * The slot machine's win show ({@link WinShows}), played from the machine's own timeline: the
 * fountain springs from the gold cap on top of the cabinet, the sparks gather at the display
 * under the reels. The fanfare is part of the timeline's sounds ({@link SlotAnimation}).
 */
@Environment(EnvType.CLIENT)
final class SlotEffects {
    private SlotEffects() {
    }

    /** Called once per client tick for a machine in view. */
    static void tick(ClientLevel level, SlotAnimation anim, float t) {
        SlotAnimation.Celebration party = anim.celebrationAt(t);
        if (party == null) return;
        BlockPos pos = BlockPos.of(anim.pos);
        // The cabinet's gold cap ends 26.5 model pixels up (models/block/slot_machine); the display
        // sits in front of the cabinet face, below the reels.
        WinShows.Stage stage = WinShows.stage(pos, new double[]{8, 26.5, 8}, new double[]{8, 7, -2},
                new double[]{8, 0, 8}, 0.95, 1.8);
        WinShows.particles(level, level.getRandom(), party.tier(), (int) party.local(), stage);
    }
}
