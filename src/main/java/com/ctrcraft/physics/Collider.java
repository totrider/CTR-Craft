package com.ctrcraft.physics;

/**
 * What the kart drives on. The host (Minecraft) implements this over its blocks; the physics never learns
 * where the geometry comes from. Everything is in level units (lu), +Y up.
 */
public interface Collider {
    /** A face whose normal is this upright or more is ground; steeper is wall (about 50 degrees). */
    double GROUND_NORMAL_Y = 0.643;

    /** The deepest overlap of a sphere with the world, or null. */
    Contact deepest(V3 centre, double radius);

    /** The highest ground under {@code from} within {@code maxDist} straight down, or null. */
    GroundHit ground(V3 from, double maxDist);

    record Contact(double depth, V3 normal, V3 point) {}

    record GroundHit(V3 point, V3 normal, double distance) {}
}
