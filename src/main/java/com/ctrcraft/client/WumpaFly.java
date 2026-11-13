package com.ctrcraft.client;

import com.ctrcraft.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The Wumpa fruits flying to the HUD, as the original does it (UI_RenderFrame.c, RB_Fruit.c, RB_Crate.c, UI_Lerp2D_HUD): taking fruit queues them. The fruit's picture
 * flies one at a time from where the pickup was on the screen (the kart's position, 20 reference pixels higher) to the fruit on the HUD in 5 steps of the game's
 * 30 Hz, and the count on the HUD only goes up when each one arrives (so 5 to 8 fruit from a crate count up one after the other, 6 frames each). Taking
 * more while one is flying restarts it from the new place. The real count (the juiced state) is the server's and is not delayed: this only delays what the HUD shows.
 * Each arrival plays the original's sound for it (OtherFX 0x42). Difference: the original starts a loose fruit's flight at the fruit's own screen position, here it
 * is the kart's (the fruit is within a block of it).
 */
public final class WumpaFly {
    private WumpaFly() {}

    /** The HUD's fruit, the flight's end, in the 512 x 216 reference screen the original's numbers are in. */
    private static final int END_X = 316;
    private static final int END_Y = 24;
    private static final long STEP_NS = 1_000_000_000L / 30L;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean haveCamera;

    private static int lastWumpa = -1;
    private static int queued;
    private static int cooldown;
    private static int startX;
    private static int startY;
    private static long lastNs;
    private static long accumulatedNs;
    private static boolean drawing;
    private static int posX = END_X;
    private static int posY = END_Y;

    /** Remembers the world camera of the last frame, to put a world position on the screen. */
    public static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        PROJECTION.set(event.getProjectionMatrix());
        VIEW.set(event.getModelViewMatrix());
        camera = event.getCamera().getPosition();
        haveCamera = true;
    }

    /** Fruit queued and not arrived yet: the HUD shows the real count minus these. */
    public static int pending() {
        return queued;
    }

    public static boolean drawing() {
        return drawing;
    }

    /** Where the flying fruit's centre is, in the 512 x 216 reference screen. */
    public static int x() {
        return posX;
    }

    public static int y() {
        return posY;
    }

    public static void reset() {
        lastWumpa = -1;
        queued = 0;
        cooldown = 0;
        drawing = false;
        lastNs = 0;
        accumulatedNs = 0;
    }

    /** Once per rendered frame: {@code wumpa} the real count, {@code at} the pickup's world position, the screen size in gui pixels. */
    public static void update(int wumpa, Vec3 at, float guiWidth, float guiHeight, boolean paused) {
        if (lastWumpa < 0) lastWumpa = wumpa;
        if (wumpa > lastWumpa) {
            queued += wumpa - lastWumpa;
            cooldown = 5;
            int[] p = project(at, guiWidth, guiHeight);
            startX = p[0];
            startY = p[1] - 20;                                   // 0x14 above the pickup, as the original
        }
        lastWumpa = wumpa;
        queued = Math.min(queued, wumpa);                            // the count dropped (a hit, a new race): nothing left to fly
        if (queued == 0) cooldown = 0;

        long now = System.nanoTime();
        if (lastNs == 0 || paused) lastNs = now;
        accumulatedNs += now - lastNs;
        lastNs = now;
        for (int n = 0; accumulatedNs >= STEP_NS && n < 5; n++) {
            accumulatedNs -= STEP_NS;
            step();
        }
        if (accumulatedNs > 5 * STEP_NS) accumulatedNs = 0;
    }

    /** One game frame of the original's HUD block: draw the fruit between its start and the HUD, count it in when the timer is over. */
    private static void step() {
        if (queued == 0) {
            drawing = false;
            return;
        }
        posX = END_X;
        posY = END_Y;
        if (cooldown == 0) {
            queued--;                                                  // arrived: the HUD count goes up (queued is subtracted from what the HUD shows)
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.FRUIT_ARRIVE.get(), 1.0F, 0.8F));      // OtherFX_Play(0x42)
            if (queued != 0) cooldown = 5;
        } else {
            posX = END_X + cooldown * (startX - END_X) / 5;            // UI_Lerp2D_HUD: the timer counts down, 0 is the destination
            posY = END_Y + cooldown * (startY - END_Y) / 5;
            cooldown--;
        }
        drawing = true;
    }

    /** A world position on the 512 x 216 reference screen (the lower middle of it when it is behind the camera or there is no frame yet). */
    private static int[] project(Vec3 p, float guiWidth, float guiHeight) {
        if (haveCamera) {
            Vector4f v = new Vector4f((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z), 1.0F);
            new Matrix4f(PROJECTION).mul(VIEW).transform(v);
            if (v.w > 0.0F) {
                float gx = (v.x / v.w * 0.5F + 0.5F) * guiWidth;
                float gy = (1.0F - (v.y / v.w * 0.5F + 0.5F)) * guiHeight;
                return new int[] { Math.round(256.0F + (gx - guiWidth / 2.0F) / (0.7361F * guiHeight / 216.0F)), Math.round(gy * 216.0F / guiHeight) };
            }
        }
        return new int[] { 256, 150 };
    }
}
