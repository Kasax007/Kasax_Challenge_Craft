package net.kasax.challengecraft.casino.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoDeviceBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The show a slot machine puts on for a big win, in four tiers by the win's multiple of the stake
 * ({@link SlotAnimation#tierFor}). Each tier keeps everything of the one below and adds to it:
 * <ol>
 *   <li><b>Big</b> (5×) — gold nuggets spring from the top of the cabinet, sparks at the display;</li>
 *   <li><b>Mega</b> (20×) — the fountain gets emeralds and diamonds, and totem sparkles rise;</li>
 *   <li><b>Epic</b> (50×) — fireworks burst above the machine;</li>
 *   <li><b>Legendary</b> (200×) — a spiral of light climbs around the cabinet, notes fly, and the
 *       fireworks turn into a golden barrage.</li>
 * </ol>
 * Purely client side: every viewer sees the show of every machine near them, from the same timeline.
 */
@Environment(EnvType.CLIENT)
final class SlotEffects {
    private static final ItemParticleOption NUGGET = new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET);
    private static final ItemParticleOption[] GEMS = {
            new ItemParticleOption(ParticleTypes.ITEM, Items.EMERALD),
            new ItemParticleOption(ParticleTypes.ITEM, Items.DIAMOND),
            new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_INGOT)};

    private SlotEffects() {
    }

    /** Called once per client tick for a machine in view. */
    static void tick(ClientLevel level, SlotAnimation anim, float t) {
        SlotAnimation.Celebration party = anim.celebrationAt(t);
        if (party == null) return;
        BlockPos pos = BlockPos.of(anim.pos);
        BlockState state = level.getBlockState(pos);
        Direction facing = state.hasProperty(CasinoDeviceBlock.FACING) ? state.getValue(CasinoDeviceBlock.FACING) : Direction.NORTH;
        RandomSource r = level.getRandom();
        int tick = (int) party.local();
        int tier = party.tier();
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        // The top of the cabinet: its gold cap ends 26.5 model pixels up (models/block/slot_machine).
        double top = pos.getY() + 26.5 / 16.0;
        // The display, just in front of the cabinet face.
        double fx = cx + facing.getStepX() * 0.62, fz = cz + facing.getStepZ() * 0.62, fy = pos.getY() + 0.45;

        // 1: a fountain of gold nuggets from the top, sparks around the display.
        for (int i = 0; i < 1 + tier; i++) {
            ParticleOptions coin = tier >= 2 && r.nextInt(4) == 0 ? GEMS[r.nextInt(GEMS.length)] : NUGGET;
            level.addParticle(coin, cx + (r.nextDouble() - 0.5) * 0.4, top, cz + (r.nextDouble() - 0.5) * 0.4,
                    (r.nextDouble() - 0.5) * 0.22, 0.28 + r.nextDouble() * 0.18, (r.nextDouble() - 0.5) * 0.22);
        }
        if (tick % 2 == 0) {
            level.addParticle(ParticleTypes.WAX_ON, fx + (r.nextDouble() - 0.5) * 0.8, fy + r.nextDouble() * 0.3,
                    fz + (r.nextDouble() - 0.5) * 0.8, 0, 0.05, 0);
        }
        if (tier < 2) return;

        // 2: totem sparkles rising around the cabinet.
        if (tick % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, cx + Math.cos(a) * 0.7, pos.getY() + 0.3 + r.nextDouble() * 1.6,
                        cz + Math.sin(a) * 0.7, Math.cos(a) * 0.08, 0.18, Math.sin(a) * 0.08);
            }
        }
        if (tier < 3) return;

        // 3: fireworks above the machine.
        int every = tier >= 4 ? 8 : 14;
        if (tick % every == 3) {
            // Small shapes, well above the cabinet: a large ball this close fills the whole view.
            FireworkExplosion.Shape shape = switch (r.nextInt(3)) {
                case 0 -> FireworkExplosion.Shape.SMALL_BALL;
                case 1 -> FireworkExplosion.Shape.STAR;
                default -> FireworkExplosion.Shape.BURST;
            };
            IntList colours = tier >= 4 ? IntList.of(0xFFD24A, 0xFFF3A0, 0xE8B83A)
                    : IntList.of(PALETTE[r.nextInt(PALETTE.length)], PALETTE[r.nextInt(PALETTE.length)]);
            level.createFireworks(cx + (r.nextDouble() - 0.5) * 1.5, top + 3.0 + r.nextDouble() * 1.5, cz + (r.nextDouble() - 0.5) * 1.5,
                    0, 0, 0, List.of(new FireworkExplosion(shape, colours, IntList.of(0xFFFFFF), true, tier >= 4)), false);
        }
        if (tier < 4) return;

        // 4: a spiral of light climbing around the cabinet, and notes flying off it.
        for (int i = 0; i < 2; i++) {
            double a = tick * 0.45 + i * Math.PI;
            double y = pos.getY() + (tick % 30) / 30.0 * 1.8;
            level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * 0.95, y, cz + Math.sin(a) * 0.95, 0, 0.02, 0);
        }
        if (tick % 3 == 0) {
            level.addParticle(ParticleTypes.NOTE, cx + (r.nextDouble() - 0.5), top + 0.3, cz + (r.nextDouble() - 0.5),
                    r.nextDouble(), 0, 0);
        }
    }

    private static final int[] PALETTE = {0xFF5A5A, 0x5AD2FF, 0x7BE07B, 0xFFD24A, 0xD08CFF, 0xFF9A2E};
}
