package com.ctrcraft.client;

import com.ctrcraft.kart.KartScale;
import com.ctrcraft.physics.KartSim;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The driving HUD, in CTR's style (chunky yellow numbers with a dark outline, a segmented bar): a speedometer (World of Kartcraft
 * has one, CTR itself does not), the powerslide turbo meter with its red zone and boost pips, the turbo reserves burning down, and the
 * hang-time meter with its three tiers. Drawn only while you drive a kart.
 */
public final class KartHud {
    private KartHud() {}

    private static final int OUTLINE = 0xFF14101C;
    private static final int YELLOW = 0xFFFFD21F;
    private static final int PANEL = 0xA0141018;
    private static final int OFF = 0xFF3A3642;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        KartSim sim = KartDriver.activeSim();
        if (sim == null || mc.options.hideGui) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int px = w - 132;
        int py = h - 70;

        // ---- speedometer panel ----
        g.fill(px - 4, py - 4, px + 120, py + 58, OUTLINE);
        g.fill(px - 2, py - 2, px + 118, py + 56, PANEL);
        double blocksPerSecond = Math.abs(sim.forwardSpeed()) / 256.0 * 30.0 / KartScale.LU_PER_BLOCK;
        int kmh = (int) Math.round(blocksPerSecond * 3.6);
        String number = Integer.toString(kmh);
        float scale = 3.0F;
        int textW = (int) (mc.font.width(number) * scale);
        int tx = px + 86 - textW;
        int ty = py + 2;
        outlined(g, mc, number, tx, ty, scale, boostingColour(sim));
        g.drawString(mc.font, "KM/H", px + 90, py + 16, 0xFFCFC8E0, true);

        // segmented speed bar: 24 segments, green to yellow to red, filling to top speed + a sacred-fire boost
        int segments = 24;
        double fraction = Math.min(1.0, Math.abs(sim.forwardSpeed()) / (double) sim.speedScale());
        int lit = (int) Math.round(fraction * segments);
        for (int i = 0; i < segments; i++) {
            int x = px + i * 5;
            int colour = i < lit ? segmentColour(i, segments) : OFF;
            g.fill(x, py + 38, x + 4, py + 52, colour);
        }

        // ---- reserves (the turbo burning) ----
        int reserves = sim.reserves();
        if (reserves > 0) {
            int len = (int) (118 * Math.min(1.0, reserves / 1920.0));
            g.fill(px - 2, py - 12, px + 118, py - 6, OUTLINE);
            g.fill(px - 1, py - 11, px - 1 + len, py - 7, 0xFFFF8A1F);
        }

        // ---- powerslide turbo meter ----
        double[] meter = sim.turboMeter();
        if (sim.state == KartSim.State.DRIFTING) {
            int my = py - (reserves > 0 ? 30 : 22);
            g.fill(px - 4, my - 2, px + 120, my + 12, OUTLINE);
            g.fill(px - 2, my, px + 118, my + 10, 0xFF2A2634);
            // the red zone is the last half of the bar
            g.fill(px + 58, my, px + 118, my + 10, 0xFF5A1C24);
            if (meter != null) {
                int len = (int) (116 * meter[0]);
                boolean red = meter[1] > 0;
                boolean blink = red && (System.currentTimeMillis() / 90 % 2 == 0);
                g.fill(px - 1, my + 1, px - 1 + len, my + 9, red ? (blink ? 0xFFFFFFFF : 0xFFFF4040) : 0xFFF2F2F2);
            }
            // boost pips: one per successful boost of this slide
            for (int i = 0; i < 3; i++) {
                int cx = px + 4 + i * 12;
                g.fill(cx, my - 10, cx + 9, my - 3, OUTLINE);
                g.fill(cx + 1, my - 9, cx + 8, my - 4, i < sim.boostsSuccess() ? 0xFFFFA01F : OFF);
            }
        }

        // ---- hang-time meter ----
        int hang = sim.hangTimeMeter();
        if (hang >= 0) {
            int hx = px - 22;
            int hh = 54;
            g.fill(hx - 2, py - 4, hx + 14, py + hh + 4, OUTLINE);
            g.fill(hx, py - 2, hx + 12, py + hh + 2, 0xFF2A2634);
            int fillH = (int) (hh * Math.min(1.0, hang / 2400.0));
            int colour = hang >= 1440 ? 0xFFFFD21F : hang >= 960 ? 0xFFFFA01F : hang >= 640 ? 0xFF7CE04A : 0xFF9A96A8;
            g.fill(hx + 1, py + hh - fillH, hx + 11, py + hh, colour);
            for (int tier : new int[] { 640, 960, 1440 }) {
                int ty2 = py + hh - (int) (hh * tier / 2400.0);
                g.fill(hx, ty2, hx + 12, ty2 + 1, 0xFFFFFFFF);
            }
        }
    }

    private static int boostingColour(KartSim sim) {
        return sim.reserves() > 0 && (System.currentTimeMillis() / 120 % 2 == 0) ? 0xFFFF9A2A : YELLOW;
    }

    private static int segmentColour(int i, int n) {
        double t = (double) i / (n - 1);
        if (t < 0.5) return 0xFF55E05A;
        if (t < 0.8) return 0xFFFFD21F;
        return 0xFFFF4A3A;
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
