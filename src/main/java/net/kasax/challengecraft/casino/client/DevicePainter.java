package net.kasax.challengecraft.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.DeviceLayouts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws onto a device in {@link net.kasax.challengecraft.casino.DeviceSpace} pixels (u to the
 * player's right, y up, v to the back). The pose it is given is the master block's model space
 * facing north, where {@code x = 16 - u} and {@code z = v}; the painter does that conversion, so
 * the views can use the same numbers as {@link DeviceLayouts}.
 *
 * <p>Flat-coloured quads and chips are collected and submitted in one batch each by {@link #flush},
 * text is submitted directly like sign text.
 */
@Environment(EnvType.CLIENT)
final class DevicePainter {
    static final int FULLBRIGHT = 0xF000F0;
    private static final Identifier CHIPS = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/chips.png");
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("challengecraft", "textures/misc/white.png");
    private static final Identifier WHEEL = Identifier.fromNamespaceAndPath("challengecraft", "textures/gui/casino/roulette_wheel.png");

    final Minecraft mc;
    final PoseStack pose;
    final SubmitNodeCollector collector;
    final ItemStackRenderState itemState;
    /** Light at the device, for things that are not self-lit. */
    final int light;
    private final List<float[]> quads = new ArrayList<>();
    private final List<float[]> chips = new ArrayList<>();
    private final List<float[]> wheel = new ArrayList<>();

    DevicePainter(Minecraft mc, PoseStack pose, SubmitNodeCollector collector, ItemStackRenderState itemState, int light) {
        this.mc = mc;
        this.pose = pose;
        this.collector = collector;
        this.itemState = itemState;
        this.light = light;
    }

    // ---- flat colour --------------------------------------------------------------------------

    /** Any quad by its four corners, each {u, y, v}. */
    void quad(double u0, double y0, double v0, double u1, double y1, double v1,
              double u2, double y2, double v2, double u3, double y3, double v3, int argb) {
        quads.add(new float[]{(float) (16 - u0), (float) y0, (float) v0, (float) (16 - u1), (float) y1, (float) v1,
                (float) (16 - u2), (float) y2, (float) v2, (float) (16 - u3), (float) y3, (float) v3,
                Float.intBitsToFloat(argb)});
    }

    /** A quad given directly in the pose's model space (x, y, z), not in device space. */
    void quadModel(float x0, float y0, float z0, float x1, float y1, float z1,
                   float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
        quads.add(new float[]{x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, Float.intBitsToFloat(argb)});
    }

    static int rgb(float r, float g, float b) {
        return 0xFF000000 | (int) (Math.min(1f, r) * 255) << 16 | (int) (Math.min(1f, g) * 255) << 8 | (int) (Math.min(1f, b) * 255);
    }

    /** A horizontal rectangle at height y. */
    void rectTop(double u0, double v0, double u1, double v1, double y, int argb) {
        quad(u0, y, v0, u1, y, v0, u1, y, v1, u0, y, v1, argb);
    }

    /** A rectangle facing the player at depth v. */
    void rectFront(double u0, double y0, double u1, double y1, double v, int argb) {
        quad(u0, y0, v, u1, y0, v, u1, y1, v, u0, y1, v, argb);
    }

    /** A solid box (all six faces). */
    void box(double u0, double y0, double v0, double u1, double y1, double v1, int argb) {
        int side = shade(argb, 0.8f), dark = shade(argb, 0.62f);
        rectTop(u0, v0, u1, v1, y1, argb);
        rectTop(u0, v0, u1, v1, y0, dark);
        rectFront(u0, y0, u1, y1, v0, side);
        rectFront(u0, y0, u1, y1, v1, side);
        quad(u0, y0, v0, u0, y0, v1, u0, y1, v1, u0, y1, v0, dark);
        quad(u1, y0, v0, u1, y0, v1, u1, y1, v1, u1, y1, v0, dark);
    }

    /** A horizontal disc as a fan of thin wedges. */
    void discTop(double uc, double vc, double y, double r, int argb, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            quad(uc, y, vc, uc + Math.cos(a0) * r, y, vc + Math.sin(a0) * r,
                    uc + Math.cos(a1) * r, y, vc + Math.sin(a1) * r, uc, y, vc, argb);
        }
    }

    /** A disc facing the player. */
    void discFront(double uc, double yc, double v, double r, int argb, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            quad(uc, yc, v, uc + Math.cos(a0) * r, yc + Math.sin(a0) * r, v,
                    uc + Math.cos(a1) * r, yc + Math.sin(a1) * r, v, uc, yc, v, argb);
        }
    }

    static int shade(int argb, float f) {
        int a = argb >>> 24, r = (int) (((argb >> 16) & 0xFF) * f), g = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int alpha(int rgb, float a) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // ---- chips --------------------------------------------------------------------------------

    /** A casino chip lying flat, from the GUI chip atlas (colour 0..7), optionally lifted. */
    void chip(double uc, double vc, double y, double r, int colour, float bright) {
        int c = Math.floorMod(colour, 8);
        float t0 = c * 32f / 256f, t1 = (c + 1) * 32f / 256f;
        int shade = (int) (255 * Math.max(0.2f, Math.min(1f, bright)));
        chips.add(new float[]{(float) (16 - (uc - r)), (float) y, (float) (vc - r), t0, 0f,
                (float) (16 - (uc + r)), (float) y, (float) (vc - r), t1, 0f,
                (float) (16 - (uc + r)), (float) y, (float) (vc + r), t1, 1f,
                (float) (16 - (uc - r)), (float) y, (float) (vc + r), t0, 1f, shade});
        // The chip's edge, so a stack reads as a stack.
        int edge = DevicePainter.shade(CHIP_EDGE[c], 0.85f * bright);
        for (int i = 0; i < 12; i++) {
            double a0 = Math.PI * 2 * i / 12, a1 = Math.PI * 2 * (i + 1) / 12;
            double ua = uc + Math.cos(a0) * r * 0.97, va = vc + Math.sin(a0) * r * 0.97;
            double ub = uc + Math.cos(a1) * r * 0.97, vb = vc + Math.sin(a1) * r * 0.97;
            quad(ua, y - 0.32, va, ub, y - 0.32, vb, ub, y - 0.06, vb, ua, y - 0.06, va, edge);
        }
    }

    /** Edge colours of the eight chips in the atlas. */
    private static final int[] CHIP_EDGE = {0xFFE8E8E8, 0xFFC0282E, 0xFF2E9A4A, 0xFF2A2A2E, 0xFF3A5FD0, 0xFFE07A20,
            0xFFB03A70, 0xFF6FB6E8};

    static int chipColour(int level) {
        if (level < 0 || level >= DeviceLayouts.ALL_IN_LEVEL) return 5;
        return new int[]{0, 1, 2, 3, 4, 6, 7, 1, 2, 3, 4, 6, 7, 5, 5, 5}[Math.min(15, level)];
    }

    static String chipLabel(int level) {
        if (level < 0 || level >= DeviceLayouts.ALL_IN_LEVEL) return "ALL";
        long v = DeviceLayouts.STAKES[level];
        if (v >= 1_000_000) return (v / 1_000_000) + "M";
        if (v >= 1000) return (v / 1000) + "K";
        return Long.toString(v);
    }

    /** The chip colour for an amount lying on a table (the largest stake it reaches). */
    static int chipColourFor(long chips) {
        int best = 0;
        for (int i = 0; i < DeviceLayouts.STAKES.length; i++) if (chips >= DeviceLayouts.STAKES[i]) best = i;
        return chipColour(best);
    }

    // ---- the roulette wheel --------------------------------------------------------------------

    /** The printed wheel texture as a horizontal disc of radius r, turned by {@code angle}. */
    void wheel(double uc, double vc, double y, double r, double angle) {
        int n = 36;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float[] q = new float[15];
            double[][] pts = {{0, 0}, {Math.cos(a0), Math.sin(a0)}, {Math.cos(a1), Math.sin(a1)}, {0, 0}};
            int k = 0;
            for (double[] p : pts) {
                // The texture has pocket i at image angle i·2π/37 - π/2 (clockwise from the top);
                // on the disc that is device angle π/2 - i·2π/37, turned further by `angle`.
                double du = p[0] * r, dv = p[1] * r;
                double rot = angle;
                double wu = du * Math.cos(rot) - dv * Math.sin(rot), wv = du * Math.sin(rot) + dv * Math.cos(rot);
                q[k++] = (float) (16 - (uc + wu));
                q[k++] = (float) y;
                q[k++] = (float) (vc + wv);
            }
            // Image y runs towards the viewer (-v), or the wheel would read mirrored from above.
            float[] uv = new float[8];
            for (int j = 0; j < 4; j++) {
                double[] p = pts[j];
                uv[j * 2] = (float) (0.5 + p[0] * 0.5);
                uv[j * 2 + 1] = (float) (0.5 - p[1] * 0.5);
            }
            wheel.add(new float[]{q[0], q[1], q[2], uv[0], uv[1], q[3], q[4], q[5], uv[2], uv[3],
                    q[6], q[7], q[8], uv[4], uv[5], q[9], q[10], q[11], uv[6], uv[7], 255});
        }
    }

    // ---- text and items -----------------------------------------------------------------------

    /**
     * Text standing on a front face at depth v, {@code height} pixels tall, centred vertically on y.
     * {@code align}: -1 left edge at u, 0 centred, 1 right edge at u.
     */
    void textFront(Component text, double u, double y, double v, double height, int argb, int align) {
        Font font = mc.font;
        FormattedCharSequence seq = text.getVisualOrderText();
        float s = (float) (height / 7.0);
        float w = font.width(seq);
        pose.pushPose();
        pose.translate((float) (16 - u), (float) (y + height / 2), (float) (v - 0.04));
        pose.rotate(Axis.YP.rotationDegrees(180f));
        pose.scale(s, -s, s);
        float x = align < 0 ? 0 : align > 0 ? -w : -w / 2f;
        collector.submitText(pose, x, 0, seq, false, Font.DisplayMode.POLYGON_OFFSET, FULLBRIGHT, argb, 0, 0);
        pose.popPose();
    }

    /** Text lying flat at height y, readable from the front, centred on (u, v). */
    void textTop(Component text, double u, double v, double y, double height, int argb, int align, int lightCoords) {
        Font font = mc.font;
        FormattedCharSequence seq = text.getVisualOrderText();
        float s = (float) (height / 7.0);
        float w = font.width(seq);
        pose.pushPose();
        pose.translate((float) (16 - u), (float) (y + 0.08), (float) (v + height / 2));
        pose.rotate(Axis.YP.rotationDegrees(180f));
        pose.rotate(Axis.XP.rotationDegrees(-90f));
        pose.scale(s, -s, s);
        float x = align < 0 ? 0 : align > 0 ? -w : -w / 2f;
        collector.submitText(pose, x, 0, seq, false, Font.DisplayMode.POLYGON_OFFSET, lightCoords, argb, 0, 0);
        pose.popPose();
    }

    int width(Component text) {
        return mc.font.width(text);
    }

    /** An item standing upright, facing the player, {@code size} pixels tall. */
    void item(ItemStack stack, double u, double y, double v, double size) {
        pose.pushPose();
        pose.translate((float) (16 - u), (float) y, (float) v);
        pose.rotate(Axis.YP.rotationDegrees(180f));
        pose.scale((float) size, (float) size, (float) size);
        itemState.clear();
        mc.getItemModelResolver().updateForTopItem(itemState, stack, ItemDisplayContext.FIXED, mc.level, null, 0);
        itemState.submit(pose, collector, FULLBRIGHT, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    // ---- submission ---------------------------------------------------------------------------

    /**
     * Submits everything collected. Opaque colours go through a culling, depth-writing entity
     * render type with a plain white texture, so overlapping layers (a box on a board, a chip on
     * its ring) always stack by depth. {@code debugQuads} neither writes depth nor keeps the
     * submission order — it is sorted by distance, which let big quads cover small ones from some
     * angles and made them flicker while moving — so only translucent highlights still use it.
     */
    void flush() {
        if (!quads.isEmpty()) {
            List<float[]> solid = new ArrayList<>(), translucent = new ArrayList<>();
            for (float[] q : quads) ((Float.floatToRawIntBits(q[12]) >>> 24) == 0xFF ? solid : translucent).add(q);
            quads.clear();
            if (!solid.isEmpty()) {
                collector.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(WHITE), (p, buf) -> {
                    for (float[] q : solid) {
                        int c = Float.floatToRawIntBits(q[12]);
                        // Both windings: the culling type keeps whichever faces the camera.
                        for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
                            for (int k : order) {
                                buf.addVertex(p, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).setColor(c).setUv(0.5f, 0.5f)
                                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT).setNormal(p, 0f, 1f, 0f);
                            }
                        }
                    }
                });
            }
            if (!translucent.isEmpty()) {
                collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buf) -> {
                    for (float[] q : translucent) {
                        int c = Float.floatToRawIntBits(q[12]);
                        float a = (c >>> 24) / 255f, r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
                        buf.addVertex(p, q[0], q[1], q[2]).setColor(r, g, b, a);
                        buf.addVertex(p, q[3], q[4], q[5]).setColor(r, g, b, a);
                        buf.addVertex(p, q[6], q[7], q[8]).setColor(r, g, b, a);
                        buf.addVertex(p, q[9], q[10], q[11]).setColor(r, g, b, a);
                        buf.addVertex(p, q[9], q[10], q[11]).setColor(r, g, b, a);
                        buf.addVertex(p, q[6], q[7], q[8]).setColor(r, g, b, a);
                        buf.addVertex(p, q[3], q[4], q[5]).setColor(r, g, b, a);
                        buf.addVertex(p, q[0], q[1], q[2]).setColor(r, g, b, a);
                    }
                });
            }
        }
        submitTextured(chips, CHIPS);
        submitTextured(wheel, WHEEL);
    }

    private void submitTextured(List<float[]> list, Identifier texture) {
        if (list.isEmpty()) return;
        List<float[]> batch = new ArrayList<>(list);
        list.clear();
        int lightCoords = light;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (p, buf) -> {
            for (float[] q : batch) {
                int shade = (int) q[20];
                int color = 0xFF000000 | shade << 16 | shade << 8 | shade;
                // One face only: the render type does not cull, and a second copy with the opposite
                // winding at the same depth z-fights (and is lit as a back face, so it flickers dark).
                {
                    for (int k = 0; k < 4; k++) {
                        int o = k * 5;
                        buf.addVertex(p, q[o], q[o + 1], q[o + 2]).setColor(color).setUv(q[o + 3], q[o + 4])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords).setNormal(p, 0f, 1f, 0f);
                    }
                }
            }
        });
    }
}
