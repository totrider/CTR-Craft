package com.ctrcraft.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The white flash of a beaker hit: GenericMine's driver collision sets the victim's screen to bright white (fadeFromBlack_currentValue 0x1fff) and lets it fade back
 * to normal (desired 0x1000, step 0xff78 a frame). Here the screen is covered in white at once and the white fades out over about 0.8 s.
 */
public final class ScreenFlash {
    private ScreenFlash() {}

    private static final long DURATION_MS = 800;
    private static long startedMs = -1;

    public static void start() {
        startedMs = System.currentTimeMillis();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (startedMs < 0) return;
        long t = System.currentTimeMillis() - startedMs;
        if (t >= DURATION_MS || Minecraft.getInstance().player == null) {
            startedMs = -1;
            return;
        }
        float k = 1.0F - t / (float) DURATION_MS;
        int alpha = Math.round(255.0F * k * k);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0xFFFFFF);
    }
}
