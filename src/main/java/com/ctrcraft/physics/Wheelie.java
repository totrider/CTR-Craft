package com.ctrcraft.physics;

/**
 * The boost wheelie ("rearing") poses: bakedGteMath[WHEELIE_START / HOLD / RECOVER] (zGlobal_DATA.c:5361-5560), as the
 * spec / world-of-kartcraft read them. Per frame: lift and forward shift (1/256 lu, in the kart's frame), pitch (4096ths of a
 * turn, negative = nose up) and the X, Y, Z scale (4096ths). A frame is one number on the wire: 0 none, 1-11 the rise,
 * 12 the hold, 13-21 the way down.
 */
public final class Wheelie {
    private Wheelie() {}

    public static final int START_FRAMES = 11;
    public static final int RECOVER_FRAMES = 9;

    // {lift, forward, pitch, x, y, z}
    static final int[][] START = {
        {-19, -537, -26, 4096, 4262, 3686},
        {45, -789, -53, 4096, 4429, 3276},
        {359, -162, -82, 4096, 4096, 3596},
        {779, 720, -112, 4096, 4420, 4300},
        {1234, 1471, -142, 4096, 4217, 5004},
        {1685, 1994, -171, 4096, 3974, 5324},
        {2093, 1958, -198, 4096, 3771, 5233},
        {2385, 1690, -222, 4096, 3686, 5006},
        {2431, 1398, -243, 4096, 3750, 4710},
        {2361, 1115, -259, 4096, 3891, 4414},
        {2232, 946, -269, 4096, 4032, 4186},
    };
    static final int[][] HOLD = {{2132, 879, -273, 4096, 4096, 4096}};
    static final int[][] RECOVER = {
        {1896, 865, -243, 4096, 4057, 4171},
        {1502, 844, -171, 4096, 3974, 4338},
        {716, 792, -82, 4096, 3891, 4505},
        {481, 1014, -2, 4915, 3276, 4915},
        {297, 541, -2, 4596, 3595, 4596},
        {-22, -203, -2, 4005, 4186, 4005},
        {-224, -389, -2, 3686, 4505, 3686},
        {-287, -439, -2, 3792, 4399, 3792},
        {-142, -220, -2, 3989, 4202, 3989},
    };

    /** One pose: lift/forward in blocks (kart frame), pitch in degrees (negative = nose up), scale factors. */
    public record Pose(double lift, double forward, double pitchDegrees, double sx, double sy, double sz) {}

    /** The pose for a wire code, or null for 0 / out of range. {@code luPerBlock} converts the lu offsets. */
    public static Pose pose(int code, double luPerBlock) {
        int[] f;
        if (code >= 1 && code <= START_FRAMES) f = START[code - 1];
        else if (code == START_FRAMES + 1) f = HOLD[0];
        else if (code > START_FRAMES + 1 && code <= START_FRAMES + 1 + RECOVER_FRAMES) f = RECOVER[code - START_FRAMES - 2];
        else return null;
        return new Pose(f[0] / 256.0 / luPerBlock, f[1] / 256.0 / luPerBlock, f[2] * 360.0 / 4096.0,
                f[3] / 4096.0, f[4] / 4096.0, f[5] / 4096.0);
    }

    /** VehPhysForce_TranslateMatrix_RemapIndex: a cut-short rise's frame as the matching frame of the way down, or back. */
    static int remap(int index, int from, int to) {
        int q = (index << 8) / Math.max(from - 1, 1);
        int blend = Math.max(0, Math.min(256, 256 - q));
        return (blend * (to - 1)) >> 8;
    }
}
