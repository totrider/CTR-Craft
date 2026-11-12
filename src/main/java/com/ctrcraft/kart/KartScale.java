package com.ctrcraft.kart;

import com.ctrcraft.physics.FixedMath;

/**
 * The one place that ties CTR's level units (lu) to Minecraft blocks, and CTR's angles to Minecraft's yaw.
 * Nothing in the code converts lu to meters (spec section 0); 50 lu = 1 block makes the 25 lu collision sphere exactly
 * one block wide, a hop about 0.9 of a block high and the Balanced top speed about 31 blocks/s.
 */
public final class KartScale {
    private KartScale() {}

    public static final double LU_PER_BLOCK = 50.0;

    public static double toLu(double blocks) {
        return blocks * LU_PER_BLOCK;
    }

    public static double toBlocks(double lu) {
        return lu / LU_PER_BLOCK;
    }

    /**
     * Minecraft yaw (degrees, 0 = +Z, increasing clockwise seen from above) to CTR's heading (4096ths, forward is
     * (sin a, 0, cos a), steering left increases it). Both worlds are right-handed with +Y up, so only the angle flips.
     */
    public static int yawToAngle(float yawDegrees) {
        return FixedMath.wrap(Math.round(-yawDegrees * 4096.0f / 360.0f));
    }

    public static float angleToYaw(int angle) {
        return -angle * 360.0f / 4096.0f;
    }
}
