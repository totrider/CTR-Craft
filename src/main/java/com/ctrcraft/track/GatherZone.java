package com.ctrcraft.track;

/**
 * Where karts have to be to be counted into a race: a box behind the Start node, a fixed size measured from the start line and the way it faces,
 * whatever size the node's own checkpoint box has. It is {@link #WIDTH} wide across the line (centred on the node) and {@link #DEPTH} deep behind
 * it, so a full grid of eight (see {@link RaceGrid}) fits inside; it reaches {@link #UP} above the node and {@link #DOWN} below it, to take in
 * a slope. Pure, no Minecraft imports.
 */
public final class GatherZone {
    private GatherZone() {}

    public static final double WIDTH = 16.0;
    public static final double DEPTH = 16.0;
    public static final double UP = 5.0;
    public static final double DOWN = 3.0;

    /** Whether the point is inside the gathering box of the Start node {@code start} facing {@code rotation}. */
    public static boolean contains(long start, int rotation, double x, double y, double z) {
        double dx = x - (NodeGeometry.x(start) + 0.5);
        double dy = y - (NodeGeometry.y(start) + 0.5);
        double dz = z - (NodeGeometry.z(start) + 0.5);
        if (dy < -DOWN || dy > UP) return false;
        double along = -(dx * NodeGeometry.facingX(rotation) + dz * NodeGeometry.facingZ(rotation));   // behind the line is positive
        double across = dx * NodeGeometry.leftX(rotation) + dz * NodeGeometry.leftZ(rotation);
        return along >= 0.0 && along <= DEPTH && Math.abs(across) <= WIDTH / 2.0;
    }

    /** The point {@code along} blocks behind the line and {@code across} to its left, on the node's level (for drawing). */
    public static double[] point(long start, int rotation, double along, double across) {
        return new double[] {
                NodeGeometry.x(start) + 0.5 - NodeGeometry.facingX(rotation) * along + NodeGeometry.leftX(rotation) * across,
                NodeGeometry.z(start) + 0.5 - NodeGeometry.facingZ(rotation) * along + NodeGeometry.leftZ(rotation) * across };
    }
}
