package com.ctrcraft.track;

/**
 * Where a node is, which way it faces, and its BOX: the volume a kart has to touch to pass the node. Pure Java (a node key is a packed
 * block position, laid out like Minecraft's BlockPos.asLong).
 *
 * <p>The box is centred on the node block, turned to face the way the node does, and has six extents in blocks ({@link #LEFT} ... {@link #DOWN},
 * "left" as seen looking the way the node faces). Its shape is the track builder's: wide for a wide road, deep enough that a fast kart
 * cannot jump over it between two ticks (and the test is a segment against the box anyway).
 */
public final class NodeGeometry {
    private NodeGeometry() {}

    // the sides of a node's box: indices into the extents array
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int FRONT = 2;
    public static final int BACK = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;

    /** The extents a new node starts with, blocks: 24 wide, 6 deep, from 3 below to 8 above the node. Generous on purpose. */
    public static final float[] DEFAULT_EXTENTS = { 12.0F, 12.0F, 3.0F, 3.0F, 8.0F, 3.0F };
    public static final float MIN_EXTENT = 1.0F;
    public static final float MAX_EXTENT = 64.0F;

    public static int x(long key) {
        return (int) (key >> 38);
    }

    public static int y(long key) {
        return (int) (key << 52 >> 52);
    }

    public static int z(long key) {
        return (int) (key << 26 >> 38);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    /** The unit vector a node faces along (x, z): rotation 0 is south (+z), turning clockwise, like a standing sign. */
    public static double facingX(int rotation) {
        return -Math.sin(Math.toRadians((rotation & 15) * 22.5));
    }

    public static double facingZ(int rotation) {
        return Math.cos(Math.toRadians((rotation & 15) * 22.5));
    }

    /** The unit vector across the node, to its left as it faces (x, z). */
    public static double leftX(int rotation) {
        return Math.cos(Math.toRadians((rotation & 15) * 22.5));
    }

    public static double leftZ(int rotation) {
        return Math.sin(Math.toRadians((rotation & 15) * 22.5));
    }

    public static String sideName(int side) {
        return switch (side) {
            case LEFT -> "left";
            case RIGHT -> "right";
            case FRONT -> "front";
            case BACK -> "back";
            case UP -> "top";
            default -> "bottom";
        };
    }

    /**
     * Whether the move (x0,y0,z0) to (x1,y1,z1) touches the box of a node: it starts inside, ends inside or passes through. Returns how far
     * along the move that first happens (0..1), or -1. Either direction counts: which way round the course goes is decided by the drive.
     */
    public static double touch(long node, int rotation, float[] ext, double x0, double y0, double z0, double x1, double y1, double z1) {
        double cx = x(node) + 0.5;
        double cy = y(node) + 0.5;
        double cz = z(node) + 0.5;
        double lx = leftX(rotation);
        double lz = leftZ(rotation);
        double fx = facingX(rotation);
        double fz = facingZ(rotation);
        double[] p0 = { (x0 - cx) * lx + (z0 - cz) * lz, y0 - cy, (x0 - cx) * fx + (z0 - cz) * fz };
        double[] p1 = { (x1 - cx) * lx + (z1 - cz) * lz, y1 - cy, (z1 - cz) * fz + (x1 - cx) * fx };
        double[] lo = { -ext[RIGHT], -ext[DOWN], -ext[BACK] };
        double[] hi = { ext[LEFT], ext[UP], ext[FRONT] };
        double tmin = 0.0;
        double tmax = 1.0;
        for (int a = 0; a < 3; a++) {
            double d = p1[a] - p0[a];
            if (Math.abs(d) < 1e-9) {
                if (p0[a] < lo[a] || p0[a] > hi[a]) return -1;
                continue;
            }
            double t1 = (lo[a] - p0[a]) / d;
            double t2 = (hi[a] - p0[a]) / d;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
            if (tmin > tmax) return -1;
        }
        return tmin;
    }

    /** A ray's first hit on a box face: how far along the ray, and which side. */
    public record FaceHit(double distance, int side) {}

    /**
     * The nearest face of a node's box a ray meets within {@code maxDist}, from outside or from inside the box (the player may stand in the
     * middle of the road and point at the wall). Direction need not be normalised. Null if none.
     */
    public static FaceHit rayFace(long node, int rotation, float[] ext, double ox, double oy, double oz, double dx, double dy, double dz, double maxDist) {
        double cx = x(node) + 0.5;
        double cy = y(node) + 0.5;
        double cz = z(node) + 0.5;
        double lx = leftX(rotation);
        double lz = leftZ(rotation);
        double fx = facingX(rotation);
        double fz = facingZ(rotation);
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return null;
        dx /= len;
        dy /= len;
        dz /= len;
        double[] o = { (ox - cx) * lx + (oz - cz) * lz, oy - cy, (ox - cx) * fx + (oz - cz) * fz };
        double[] d = { dx * lx + dz * lz, dy, dx * fx + dz * fz };
        // axis 0 = lateral (left positive), 1 = vertical, 2 = forward
        double[] lo = { -ext[RIGHT], -ext[DOWN], -ext[BACK] };
        double[] hi = { ext[LEFT], ext[UP], ext[FRONT] };
        int[] sideLo = { RIGHT, DOWN, BACK };
        int[] sideHi = { LEFT, UP, FRONT };
        double best = Double.MAX_VALUE;
        int bestSide = -1;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-9) continue;
            for (int end = 0; end < 2; end++) {
                double plane = end == 0 ? lo[a] : hi[a];
                double t = (plane - o[a]) / d[a];
                if (t <= 1e-6 || t > maxDist || t >= best) continue;
                boolean inside = true;
                for (int b = 0; b < 3 && inside; b++) {
                    if (b == a) continue;
                    double v = o[b] + d[b] * t;
                    inside = v >= lo[b] - 1e-6 && v <= hi[b] + 1e-6;
                }
                if (inside) {
                    best = t;
                    bestSide = end == 0 ? sideLo[a] : sideHi[a];
                }
            }
        }
        return bestSide < 0 ? null : new FaceHit(best, bestSide);
    }

    /** The world position of a point of a node's box: {@code lat} to its left, {@code up} above its centre, {@code fwd} ahead. */
    public static double[] world(long node, int rotation, double lat, double up, double fwd) {
        return new double[] {
                x(node) + 0.5 + leftX(rotation) * lat + facingX(rotation) * fwd,
                y(node) + 0.5 + up,
                z(node) + 0.5 + leftZ(rotation) * lat + facingZ(rotation) * fwd };
    }
}
