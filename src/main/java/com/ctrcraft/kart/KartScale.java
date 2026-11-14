package com.ctrcraft.kart;

import com.ctrcraft.physics.FixedMath;

/**
 * The one place that ties CTR's level units (lu) to Minecraft blocks, and CTR's angles to Minecraft's yaw.
 * Nothing in the code converts lu to meters (spec section 0). The MECHANICS (the kart's physics, the weapons' speeds and hit radii, the pickups' reach) are in lu and live at
 * {@link #LU_PER_BLOCK} = 108.8 / 1.5 = 72.53 lu to the block: the kart's model is drawn 3:2 to the crates' scale (a crate is 108.8 lu and one block), which is what fits a
 * Minecraft player in it. The Balanced top speed (78 lu a frame) is then 32 blocks a second and the 25 lu collision sphere 0.34 block in radius. The VISUALS of the pickups,
 * the beakers and the bomb stay at the crates' scale ({@link #CRATE_LU}, one block): they are 2/3 of the size the mechanics, which are the original's own numbers against the
 * kart, give them, so a crate is taken from a little before the kart touches it.
 */
public final class KartScale {
    private KartScale() {}

    /** A crate's width in lu: one block, the scale of every pickup, beaker and bomb picture. */
    public static final double CRATE_LU = 108.8;
    /** The mechanics' scale: the kart's model is 3:2 to the crates. */
    public static final double LU_PER_BLOCK = CRATE_LU / 1.5;

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
