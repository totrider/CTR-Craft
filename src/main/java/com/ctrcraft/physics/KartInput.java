package com.ctrcraft.physics;

/**
 * One frame of controls, in CTR's terms.
 *
 * @param steer -1 full left to 1 full right
 * @param gas   Cross
 * @param brake Square (with gas a power-brake; with down, reverse)
 * @param down  Down: reverse
 * @param r1    R1: hop / powerslide / boost
 * @param l1    L1: hop / powerslide / boost
 */
public record KartInput(double steer, boolean gas, boolean brake, boolean down, boolean r1, boolean l1) {
    public static final KartInput NONE = new KartInput(0, false, false, false, false, false);
}
