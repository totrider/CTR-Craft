package com.ctrcraft.client;

import com.ctrcraft.physics.KartSim;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * The driving HUD, as the original game's single-player HUD in speedometer mode draws it (UI_Speedometer.c, UI_DrawSlideMeter.c, UI_JumpMeter_Draw.c,
 * positions from hud_1P_P1 in zGlobal_DATA.c). All of it is plain coloured polygons, as in the game, on a 512 x 216 reference screen anchored at the
 * bottom right and kept at the PS1's proportions (a 4:3 television) whatever the window's shape:
 * <ul>
 * <li>the dial: a band of green, yellow and red segments with white outlines over a half-transparent black fan, and the gold needle;</li>
 * <li>the powerslide meter, the grey box under it that fills with green and turns red when a boost press is due;</li>
 * <li>the hang-time meter, a vertical bar (blue, green, yellow, red by the three boost tiers) with a white readout box above it (seconds, tenths,
 *     hundredths of the airtime), which stays up for a second and a half after landing.</li>
 * </ul>
 * The original has no number for the speed, and none is drawn.
 */
public final class KartHud {
    private KartHud() {}

    private static final int OUTLINE = 0xFF14101C;

    /** How wide a reference pixel is next to how tall it is: 2.5625 by 3.4815 GUI pixels on the screenshots of the original (512 x 216 shown at 1312 x 752). */
    public static final float PIXEL_ASPECT = 0.7361F;

    // the colour band of the dial (DrawSpeedBG_Colors), bottom to top of each segment
    private static final int GREEN = 0xFF00B500;
    private static final int YELLOW_BAND = 0xFFFFD100;
    private static final int RED_BAND = 0xFFDB0000;
    private static final int[] BAND = { GREEN, GREEN, GREEN, YELLOW_BAND, RED_BAND, RED_BAND, RED_BAND };

    // data.speedometerBG_vertData: pairs of (outer, inner) points of the band, relative to (480, 190), and the longer lines that cut across it
    private static final int[][] BAND_POINTS = {
        { -65, 16 }, { -54, 13 }, { -70, 0 }, { -58, 0 }, { -65, -16 }, { -54, -13 }, { -50, -29 }, { -42, -24 },
        { -25, -39 }, { -21, -32 }, { 0, -42 }, { 0, -35 }, { 25, -39 }, { 21, -32 },
    };
    private static final int[][] CUT_POINTS = {
        { -65, 16 }, { -48, 11 }, { -70, 0 }, { -51, 0 }, { -65, -16 }, { -48, -11 }, { -50, -29 }, { -37, -21 },
        { -25, -39 }, { -18, -29 }, { 0, -42 }, { 0, -31 }, { 25, -39 }, { 18, -29 },
    };

    /** The reference screen mapped to the window: anchored bottom right, both axes by the window's height with the original's pixel aspect, so the picture's
     *  proportions are the same in every window. */
    private record Screen(int w, int h, float k, float px) {
        static Screen of(GuiGraphics g) {
            return new Screen(g.guiWidth(), g.guiHeight(), g.guiHeight() / 216.0F, PIXEL_ASPECT * g.guiHeight() / 216.0F);
        }

        float x(float refX) {
            return w - (512.0F - refX) * px;
        }

        float y(float refY) {
            return h - (216.0F - refY) * k;
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        KartSim sim = KartDriver.activeSim();
        if (sim == null || mc.options.hideGui) {
            return;
        }
        Screen s = Screen.of(g);
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();

        // the dial's dark fan and band at the back, then the meters and the readout, and only the needle over them
        dialBackground(vc, m, s);
        slideMeter(vc, m, s, sim);
        jumpMeter(vc, m, s, sim.jumpMeterShown());
        g.flush();
        jumpReadout(g, mc, s, sim.jumpMeterShown());
        vc = g.bufferSource().getBuffer(RenderType.gui());
        needle(vc, m, s, sim.speedometerAngle());
        g.flush();
    }

    // ---- primitives ----

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        // both windings: the GUI's render type culls back faces, and these shapes point every way
        vc.addVertex(m, x0, y0, 0.0F).setColor(c0);
        vc.addVertex(m, x1, y1, 0.0F).setColor(c1);
        vc.addVertex(m, x2, y2, 0.0F).setColor(c2);
        vc.addVertex(m, x3, y3, 0.0F).setColor(c3);
        vc.addVertex(m, x3, y3, 0.0F).setColor(c3);
        vc.addVertex(m, x2, y2, 0.0F).setColor(c2);
        vc.addVertex(m, x1, y1, 0.0F).setColor(c1);
        vc.addVertex(m, x0, y0, 0.0F).setColor(c0);
    }

    private static void tri(VertexConsumer vc, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        quad(vc, m, x0, y0, c0, x1, y1, c1, x2, y2, c2, x2, y2, c2);
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int c) {
        quad(vc, m, x0, y0, c, x1, y0, c, x1, y1, c, x0, y1, c);
    }

    /** A line of the given thickness (screen pixels). */
    private static void line(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float thickness, int c) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4F) return;
        float nx = -dy / len * thickness / 2.0F;
        float ny = dx / len * thickness / 2.0F;
        quad(vc, m, x0 + nx, y0 + ny, c, x1 + nx, y1 + ny, c, x1 - nx, y1 - ny, c, x0 - nx, y0 - ny, c);
    }

    /** CTR_Box_DrawWireBox in black: a one reference pixel outline. */
    private static void wireBox(VertexConsumer vc, Matrix4f m, Screen s, float x0, float y0, float x1, float y1) {
        float t = Math.max(1.0F, s.k * 0.7F);
        rect(vc, m, x0, y0, x1, y0 + t, 0xFF000000);
        rect(vc, m, x0, y1 - t, x1, y1, 0xFF000000);
        rect(vc, m, x0, y0, x0 + t, y1, 0xFF000000);
        rect(vc, m, x1 - t, y0, x1, y1, 0xFF000000);
    }

    // ---- the dial ----

    private static float bx(Screen s, int[][] pts, int i) {
        return s.x(480 + pts[i][0]);
    }

    private static float by(Screen s, int[][] pts, int i) {
        return s.y(190 + pts[i][1]);
    }

    /** UI_DrawSpeedBG: the cut lines, the coloured band with its outlines (each line has a black shadow one pixel down and right), the dark fan. */
    private static void dialBackground(VertexConsumer vc, Matrix4f m, Screen s) {
        float t = Math.max(1.0F, s.k * 0.7F);
        int n = BAND_POINTS.length;
        // the dark half-transparent fan under the band (drawn first: the game draws it last into a back-to-front list)
        float p2x = s.x(480 + BAND_POINTS[n - 1][0]);
        float p2y = s.y(190 + BAND_POINTS[1][1]);
        for (int i = 0; i < n - 2; i += 2) {
            tri(vc, m, bx(s, BAND_POINTS, i + 1), by(s, BAND_POINTS, i + 1), 0x80000000, bx(s, BAND_POINTS, i + 3), by(s, BAND_POINTS, i + 3), 0x80000000, p2x, p2y, 0x80000000);
        }
        // the segments, each shaded from one band colour to the next
        for (int i = 0, seg = 0; i < n - 2; i += 2, seg++) {
            quad(vc, m, bx(s, BAND_POINTS, i), by(s, BAND_POINTS, i), BAND[seg], bx(s, BAND_POINTS, i + 1), by(s, BAND_POINTS, i + 1), BAND[seg],
                    bx(s, BAND_POINTS, i + 3), by(s, BAND_POINTS, i + 3), BAND[seg + 1], bx(s, BAND_POINTS, i + 2), by(s, BAND_POINTS, i + 2), BAND[seg + 1]);
        }
        for (int i = 0; i < n - 2; i += 2) {
            for (int j = 0; j < 2; j++) {
                line(vc, m, bx(s, BAND_POINTS, i + j) + t, by(s, BAND_POINTS, i + j) + t, bx(s, BAND_POINTS, i + j + 2) + t, by(s, BAND_POINTS, i + j + 2) + t, t, 0xFF000000);
                line(vc, m, bx(s, BAND_POINTS, i + j), by(s, BAND_POINTS, i + j), bx(s, BAND_POINTS, i + j + 2), by(s, BAND_POINTS, i + j + 2), t, 0xFFFFFFFF);
            }
        }
        for (int i = 0; i < n; i += 2) {
            line(vc, m, bx(s, CUT_POINTS, i) + t, by(s, CUT_POINTS, i) + t, bx(s, CUT_POINTS, i + 1) + t, by(s, CUT_POINTS, i + 1) + t, t, 0xFF000000);
            line(vc, m, bx(s, CUT_POINTS, i), by(s, CUT_POINTS, i), bx(s, CUT_POINTS, i + 1), by(s, CUT_POINTS, i + 1), t, 0xFFFFFFFF);
        }
    }

    /**
     * UI_DrawSpeedNeedle: two Gouraud triangles from the pivot at (479, 186): tip 60 reference pixels out, the base 6 (8 on one side) wide, the tail 6
     * behind. The game squashes y by 0.625 for the PS1's pixel shape; the screen mapping here already has that shape, so the squash is applied in
     * reference pixels as the game does.
     */
    private static void needle(VertexConsumer vc, Matrix4f m, Screen s, int angle) {
        double a = angle * 2.0 * Math.PI / 4096.0;
        double a2 = a + Math.PI / 2.0;
        double sin0 = Math.sin(a);
        double cos0 = Math.cos(a);
        double sin1 = Math.sin(a2);
        double cos1 = Math.cos(a2);
        float cx = 479.0F;
        float cy = 186.0F;
        float tipX = (float) (cx + sin0 * 60);
        float tipY = (float) (cy + cos0 * 60 * 0.625);
        float tailX = (float) (cx - sin0 * 6);
        float tailY = (float) (cy - cos0 * 6 * 0.625);
        float sideAX = (float) (cx + sin1 * 6);
        float sideAY = (float) (cy + cos1 * 8 * 0.625);
        float sideBX = (float) (cx - sin1 * 6);
        float sideBY = (float) (cy - cos1 * 8 * 0.625);
        // the dark half
        tri(vc, m, s.x(tipX), s.y(tipY), 0xFF5B5B00, s.x(tailX), s.y(tailY), 0xFF322B01, s.x(sideAX), s.y(sideAY), 0xFFFFBB00);
        // the bright half
        tri(vc, m, s.x(tipX), s.y(tipY), 0xFFFFFFFF, s.x(tailX), s.y(tailY), 0xFF9C6900, s.x(sideBX), s.y(sideBY), 0xFFFFFF00);
    }

    // ---- the powerslide meter: box 49 x 7 ending at (490, 206) ----

    private static void slideMeter(VertexConsumer vc, Matrix4f m, Screen s, KartSim sim) {
        float x1 = s.x(490);
        float x0 = s.x(490 - 49);
        float y1 = s.y(206);
        float y0 = s.y(206 - 7);
        int room = sim.slideMeterRoomLeft();
        float length = room != 0 ? 49.0F - room * 49.0F / KartSim.slideMeterMaxRoom() : 0.0F;
        rect(vc, m, x0, y0, x1, y1, 0xFF808080);                                   // empty
        if (length > 0) {
            // UI_SLIDE_METER_WAIT_COLOR green while there is room, _READY_COLOR red once a press would boost
            rect(vc, m, x0, y0, s.x(490 - 49 + length), y1, room > KartSim.slideMeterLowRoom() ? 0xFF00FF00 : 0xFFFF0000);
        }
        wireBox(vc, m, s, x0, y0, x1, y1);                                         // the black outline over the edge of the fill
    }

    // ---- the hang-time meter: bar 12 x 38 from (491, 206) up, readout box above ----

    private static void jumpMeter(VertexConsumer vc, Matrix4f m, Screen s, int jump) {
        float x0 = s.x(491);
        float x1 = s.x(491 + 12);
        float y1 = s.y(206);
        // the readout box: x 471..505, y 161..171, white; the bar starts where it ends (in the original the two touch, they do not overlap)
        float bx0 = s.x(491 - 20);
        float by0 = s.y(206 - 45);
        float by1 = s.y(206 - 45 + 10);
        float yTop = by1;
        rect(vc, m, bx0, by0, s.x(491 + 14), by1, 0xFFFFFFFF);
        wireBox(vc, m, s, bx0, by0, s.x(491 - 20 + 34), by1);
        rect(vc, m, x0, yTop, x1, y1, 0xFF808080);
        if (jump > 0) {
            int colour = jump < 640 ? 0xFF0000FF : jump < 960 ? 0xFF00FF00 : jump < 1440 ? 0xFFFFFF00 : 0xFFFF0000;   // UI_JUMP_METER_HANG_TIME_*
            float full = y1 - yTop;
            rect(vc, m, x0, y1 - Math.min(full, full * jump / 0x960), x1, y1, colour);
        }
        wireBox(vc, m, s, x0, yTop, x1, y1);
    }

    /** DebugFont_DrawNumbers: the airtime as whole 960 ms units, tenths of them (96 ms) and hundredths, in black on the white box. */
    private static void jumpReadout(GuiGraphics g, Minecraft mc, Screen s, int jump) {
        int whole = jump / 0x3C0;
        int rem = jump - whole * 0x3C0;
        int tenths = rem / 6 >> 4;
        int hundredths = Math.min(9, (rem - tenths * 0x60) * 10 / 0x60);      // one digit each, "0 00": the game draws three numbers 8 pixels apart
        float size = s.k * 0.85F;
        String[] parts = { Integer.toString(whole), Integer.toString(tenths), Integer.toString(hundredths) };
        int[] offsets = { -16, -4, 4 };            // UI_JUMP_METER_SECONDS_X_OFFSET and the two after it
        for (int i = 0; i < 3; i++) {
            g.pose().pushPose();
            g.pose().translate(s.x(491 + offsets[i]), s.y(206 - 45 + 2), 0);
            g.pose().scale(size, size, 1.0F);
            g.drawString(mc.font, parts[i], 0, 0, 0xFF000000, false);
            g.pose().popPose();
        }
    }

    static void outlined(GuiGraphics g, Minecraft mc, String text, int x, int y, float scale, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g.drawString(mc.font, text, dx, dy, OUTLINE, false);
                }
            }
        }
        g.drawString(mc.font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
