package com.ctrcraft.physics;

/**
 * The poses of the kart in a big jump: bakedGteMath[JUMP_BASE + character] (matArr07..matArr13, zGlobal_DATA.c:6078-6545), four per character, played by
 * {@code VehFrameProc_Driving}'s airborne animation (VehFrame.c:100-186). Each entry is the 0x20 byte "authored" form the wheelie's are ({@link Wheelie}): a shift (sideways x, lift y,
 * forward z, in 1/256 level units, in the kart's own frame), a rotation (pitch x, yaw y, roll z, in 4096ths of a turn, the matrix convention: a negative pitch is nose up) and a
 * scale, which is 4096 (one) in all of them. Crash's last pose rolls him 265 4096ths, about 23 degrees, and lifts him 16.6 units: the sideways tilt of a long jump; Coco's rolls
 * 64, about 5.6 degrees. Oxide uses Crash's. No Minecraft imports.
 *
 * <p>When it plays: the kart is more than 0x8000 position units (128 level units) above the ground and either is rising faster than 0x600 or already plays it; the animation
 * frame then climbs one a frame to the last pose and stays there; when the conditions end it falls back two a frame to the first and is gone (VehFrame.c's
 * TransitionAnimation). {@link KartSim} keeps that frame; one number goes on the wire: 0 none, 1 to 4 the pose.
 */
public final class AirTilt {
    private AirTilt() {}

    public static final int FRAMES = 4;
    /** The character whose poses the mod's kart uses until the game has a choice of characters (the game's id 0: Crash, and Oxide). */
    public static final int DEFAULT_CHARACTER = 0;
    /** VEH_FRAME_JUMP_AIR_THRESHOLD, 1/256 level units a frame (the sim's velocity units). */
    public static final int JUMP_AIR_THRESHOLD = 0x600;
    /** VEH_FRAME_AIR_GAP_THRESHOLD 0x8000 position units = 128 level units above the ground. */
    public static final double AIR_GAP = 128.0;

    // {x, lift, forward, pitch, yaw, roll} per pose, per character: Crash, Cortex, Tiny, Coco, N. Gin, Dingo, Polar, Pura, Roo, Papu, Joe, Pinstripe, N. Tropy
    static final int[][][] TABLE = {
        {{-209, 664, 179, -3, 2, -41}, {-672, 2127, 574, -6, 9, -132}, {-1133, 3591, 969, -10, 15, -224}, {-1343, 4255, 1149, -11, 18, -265}},
        {{-157, 43, 121, -49, 0, -27}, {-503, 137, 389, -153, 0, -88}, {-849, 231, 656, -256, 0, -148}, {-1006, 275, 778, -303, 0, -176}},
        {{78, 328, -103, 35, 0, -17}, {252, 1051, -330, 113, 0, -56}, {425, 1774, -557, 192, 0, -96}, {505, 2103, -659, 227, 0, -113}},
        {{-83, 252, 0, -20, 0, -10}, {-267, 808, 0, -62, 0, -32}, {-452, 1364, 0, -103, 0, -54}, {-536, 1617, 0, -121, 0, -64}},
        {{11, 176, -96, 19, 0, -13}, {38, 566, -310, 60, 0, -44}, {62, 954, -524, 102, 0, -74}, {74, 1131, -621, 121, 0, -88}},
        {{-107, 313, -298, 31, 0, -14}, {-342, 1004, -954, 100, 0, -44}, {-576, 1693, -1610, 169, 0, -75}, {-684, 2008, -1909, 201, 0, -89}},
        {{-61, 81, 115, -20, 0, -11}, {-196, 259, 369, -64, 0, -37}, {-331, 437, 625, -108, 0, -63}, {-393, 519, 740, -128, 0, -75}},
        {{-62, 180, -81, 20, 0, -9}, {-199, 578, -260, 65, 0, -30}, {-336, 976, -440, 111, 0, -51}, {-399, 1157, -521, 131, 0, -61}},
        {{-150, 114, 0, -4, 0, -28}, {-481, 363, 0, -13, 0, -92}, {-812, 613, 0, -22, 0, -155}, {-963, 727, 0, -26, 0, -184}},
        {{0, 57, 38, -7, 0, -2}, {0, 199, 132, -25, 0, -7}, {0, 374, 247, -47, 0, -14}, {0, 533, 352, -67, 0, -20}},
        {{0, 238, -83, 18, 0, -6}, {0, 761, -267, 58, 0, -20}, {0, 1284, -450, 98, 0, -34}, {0, 1523, -534, 117, 0, -40}},
        {{-145, 0, 148, -19, 0, -14}, {-463, 0, 475, -62, 0, -45}, {-782, 0, 803, -105, 0, -76}, {-927, 0, 951, -125, 0, -91}},
        {{-237, 373, 55, -10, 0, -19}, {-760, 1196, 174, -34, 0, -62}, {-1281, 2018, 294, -58, 0, -105}, {-1520, 2392, 349, -69, 0, -125}},
    };

    /** One pose: shifts in blocks in the kart's frame (x to its right, +z ahead), rotations in degrees as the game gives them (pitch negative = nose up, roll negative = leaning towards +x). */
    public record Pose(double x, double lift, double forward, double pitchDegrees, double yawDegrees, double rollDegrees) {}

    /** The pose for a wire code (1 to {@link #FRAMES}), or null for 0 / out of range. */
    public static Pose pose(int code, int character, double luPerBlock) {
        if (code < 1 || code > FRAMES || character < 0 || character >= TABLE.length) return null;
        int[] f = TABLE[character][code - 1];
        return new Pose(f[0] / 256.0 / luPerBlock, f[1] / 256.0 / luPerBlock, f[2] / 256.0 / luPerBlock, f[3] * 360.0 / 4096.0, f[4] * 360.0 / 4096.0, f[5] * 360.0 / 4096.0);
    }
}
