package com.ctrcraft.physics;

/**
 * The speedometer needle: how it follows the kart's speed and where it points. Ports of the needle update in {@code VehPhysGeneral_PhysLinear}
 * (VehPhysGeneral.c:1584-1646, the needle's value is a smoothed speed) and of {@code UI_DrawSpeedNeedle} (UI_Speedometer.c:3-33, the dial's angle).
 * No Minecraft imports.
 */
public final class Speedometer {
    private Speedometer() {}

    private static final int REVERSE_THRESHOLD = 0x100;   // VEH_PHYS_JUMP_SPEEDOMETER_REVERSE_THRESHOLD
    private static final int DECAY_SHIFT = 3;            // _DECAY_SHIFT
    private static final int BLEND_OLD = 0xD;            // _BLEND_OLD
    private static final int BLEND_NEW = 3;              // _BLEND_NEW
    private static final int BLEND_SHIFT = 4;            // _BLEND_SHIFT
    private static final int TIMER_MASK = 7;             // _TIMER_MASK
    private static final int TIMER_SCALE = 0x300;        // the timer term of the reverse blend: (t << 8) + (t << 9)

    /** One frame of the needle's value (an s16 in the game): {@code speedApprox} is the kart's forward speed, {@code timer} the game's frame counter. */
    public static int step(int needle, int speedApprox, int timer) {
        int next;
        if (speedApprox < 0) {
            if (-speedApprox < REVERSE_THRESHOLD) {
                next = needle - (needle >> DECAY_SHIFT);
            } else {
                next = (needle * BLEND_OLD + (timer & TIMER_MASK) * TIMER_SCALE) >>> BLEND_SHIFT;
            }
        } else {
            next = (needle * BLEND_OLD + speedApprox * BLEND_NEW) >> BLEND_SHIFT;
        }
        return (short) next;
    }

    /** ANG(degrees): the game's 4096ths of a turn. */
    private static int ang(float degrees) {
        return ((short) ((degrees * 4096.0F) / 360.0F)) & 0xFFF;
    }

    /** VehCalc_MapToRange. */
    private static int map(int val, int oldMin, int oldMax, int newMin, int newMax) {
        if (val <= oldMin) return newMin;
        if (val >= oldMax) return newMax;
        return newMin + (val - oldMin) * (newMax - newMin) / (oldMax - oldMin);
    }

    /**
     * The needle's angle in 4096ths of a turn (0 is straight down, growing clockwise as the game draws it: the tip is at {@code (sin a, cos a)} in screen
     * space with y down): about 305 degrees at rest, 213.75 at the class's acceleration speed, 157.5 at the top of the sacred-fire boost.
     */
    public static int angle(int needle, int accelSpeed, int sacredFireSpeed) {
        int minScale = 0;
        int maxScale = (accelSpeed + sacredFireSpeed) >> 8;
        int accelInt = accelSpeed >> 8;
        int minAngle;
        int maxAngle;
        if (accelSpeed < needle) {
            maxAngle = ang(157.5F);
            minAngle = ang(213.75F);
            minScale = accelInt * 108000 / 64000;
        } else {
            maxAngle = ang(213.75F);
            minAngle = ang(305.20F);
            maxScale = accelInt;
        }
        int speedScale = (needle >> 8) * 108000 / 64000;
        maxScale = maxScale * 108000 / 64000;
        return map(speedScale, minScale, maxScale, minAngle, maxAngle);
    }
}
