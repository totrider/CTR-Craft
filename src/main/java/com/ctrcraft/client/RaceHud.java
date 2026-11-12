package com.ctrcraft.client;

import com.ctrcraft.physics.RevEngine;
import com.ctrcraft.track.RaceClient;
import com.ctrcraft.track.RaceManager;
import com.ctrcraft.track.RacePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The race HUD: the gathering countdown, the three signal lights with the rev bar, then lap, position and time. */
public final class RaceHud {
    private RaceHud() {}

    private static final int OUTLINE = 0xFF14101C;
    private static final int PANEL = 0xA0141018;
    private static final int OFF = 0xFF3A3642;
    private static final int YELLOW = 0xFFFFD21F;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        RacePayload s = RaceClient.state();
        if (s.phase() == RacePayload.OFF || mc.options.hideGui) return;
        int w = g.guiWidth();
        switch (s.phase()) {
            case RacePayload.GATHER -> {
                String text = Component.translatable("hud.ctrcraft.race.gather", Math.max(0, (s.ticksLeft() + 19) / 20), s.karts()).getString();
                KartHud.outlined(g, mc, text, w / 2 - (int) (mc.font.width(text) * 1.5F / 2), 22, 1.5F, YELLOW);
            }
            case RacePayload.LIGHTS -> {
                lights(g, w, s.lights());
                rev(g, mc, w);
            }
            case RacePayload.RACING, RacePayload.DONE -> racing(g, mc, w, s);
            default -> { }
        }
    }

    private static void lights(GuiGraphics g, int w, int lit) {
        int size = 22;
        int gap = 8;
        int x0 = w / 2 - (3 * size + 2 * gap) / 2;
        for (int i = 0; i < 3; i++) {
            int x = x0 + i * (size + gap);
            g.fill(x - 2, 18, x + size + 2, 20 + size + 2, OUTLINE);
            g.fill(x, 20, x + size, 20 + size, i < lit ? 0xFFFF3030 : OFF);
        }
    }

    /** The grid rev: a bar to the top of the engine's range, red from the acceleration speed up (where a start boost is earned). */
    private static void rev(GuiGraphics g, Minecraft mc, int w) {
        RevEngine rev = KartDriver.rev();
        int y = 56;
        String hint = Component.translatable("hud.ctrcraft.race.hold").getString();
        g.drawString(mc.font, hint, w / 2 - mc.font.width(hint) / 2, y - 12, 0xFFCFC8E0, true);
        if (rev == null) return;
        int barW = 160;
        int x = w / 2 - barW / 2;
        double top = rev.accelSpeed() + 4096.0;
        int redFrom = (int) (barW * rev.accelSpeed() / top);
        g.fill(x - 2, y - 2, x + barW + 2, y + 10, OUTLINE);
        g.fill(x, y, x + barW, y + 8, 0xFF2A2634);
        g.fill(x + redFrom, y, x + barW, y + 8, 0xFF5A1C24);
        int len = (int) (barW * Math.min(1.0, rev.level() / top));
        int colour = rev.state() == RevEngine.State.BUSY ? 0xFF777777 : rev.state() == RevEngine.State.READY ? 0xFFFF4040 : 0xFFF2F2F2;
        g.fill(x, y + 1, x + len, y + 7, colour);
    }

    private static void racing(GuiGraphics g, Minecraft mc, int w, RacePayload s) {
        int x = 10;
        int y = 10;
        boolean done = s.phase() == RacePayload.DONE;
        int ms = s.timeMs();
        if (!done) ms += (int) Math.min(RaceClient.sinceUpdateMs(), 400);
        g.fill(x - 4, y - 4, x + 124, y + 52, OUTLINE);
        g.fill(x - 2, y - 2, x + 122, y + 50, PANEL);
        if (done) {
            KartHud.outlined(g, mc, Component.translatable("hud.ctrcraft.race.finished", s.finishPlace()).getString(), x, y, 1.5F, YELLOW);
        } else {
            KartHud.outlined(g, mc, Component.translatable("hud.ctrcraft.race.lap", s.lap(), s.laps()).getString(), x, y, 1.5F, YELLOW);
        }
        KartHud.outlined(g, mc, Component.translatable("hud.ctrcraft.race.place", s.place(), s.field()).getString(), x, y + 16, 1.5F, YELLOW);
        g.drawString(mc.font, RaceManager.fmt(ms), x, y + 34, 0xFFFFFFFF, true);
        if (s.bestLapMs() > 0) {
            g.drawString(mc.font, Component.translatable("hud.ctrcraft.race.best", RaceManager.fmt(s.bestLapMs())).getString(), x + 66, y + 34, 0xFFCFC8E0, true);
        }
        if (!done && s.timeMs() < 1500) {
            String go = Component.translatable("hud.ctrcraft.race.go").getString();
            float scale = 4.0F;
            KartHud.outlined(g, mc, go, w / 2 - (int) (mc.font.width(go) * scale / 2), 30, scale, 0xFF55E05A);
        }
    }
}
