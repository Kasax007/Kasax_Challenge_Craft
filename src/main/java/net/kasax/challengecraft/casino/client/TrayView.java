package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.kasax.challengecraft.casino.DeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The chip tray every game device carries: five chips around the viewer's current stake. Clicking a
 * chip makes it the stake; the selected one stands raised on a gold ring, the one aimed at lifts a
 * little. Each viewer sees the tray around their own stake — it is per player.
 */
@Environment(EnvType.CLIENT)
final class TrayView {
    private TrayView() {
    }

    static int currentLevel() {
        CasinoNet.State s = CasinoClientState.state;
        return s == null ? 0 : s.betLevel() < 0 ? DeviceLayouts.ALL_IN_LEVEL : s.betLevel();
    }

    static void draw(DevicePainter p, DeviceType type, BlockPos pos) {
        DeviceLayouts.Tray t = DeviceLayouts.tray(type);
        if (t == null) return;
        int current = currentLevel();
        CasinoClientState.Aim aim = CasinoClientState.aim;
        for (int slot = 0; slot < DeviceLayouts.TRAY_SLOTS; slot++) {
            int level = DeviceLayouts.trayLevel(current, slot);
            if (level > DeviceLayouts.ALL_IN_LEVEL) continue;
            boolean selected = level == current;
            boolean hover = aim != null && aim.master().equals(pos) && aim.zone().kind() == DeviceLayouts.CHIP
                    && aim.zone().a() == slot;
            double u = t.u(slot), v = t.v(), r = t.radius();
            double lift = selected ? 0.8 : hover ? 0.45 : 0.0;
            double y = t.y() + 0.34 + lift;
            if (selected) {
                p.discTop(u, v, t.y() + 0.15, r * 1.28, 0xFFE3B35A, 16);
            } else if (hover) {
                p.discTop(u, v, t.y() + 0.15, r * 1.18, 0xC0FFFFFF, 16);
            }
            // A second chip under the selected one, so it reads as a small stack.
            if (selected) p.chip(u, v, y - 0.34, r, DevicePainter.chipColour(level), 0.8f);
            p.chip(u, v, y, r, DevicePainter.chipColour(level), 1f);
            p.textTop(Component.literal(DevicePainter.chipLabel(level)), u, v, y, r * 0.62, 0xFF101010, 0, p.light);
        }
    }
}
