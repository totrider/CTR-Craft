package com.ctrcraft.physics;

/** A frame as three column vectors: X (the kart's left), Y (up / ground normal), Z (forward). */
public final class Mat3 {
    public final V3 x, y, z;

    public Mat3(V3 x, V3 y, V3 z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** M * v: a vector in this frame's coordinates to world coordinates. */
    public V3 mul(V3 v) {
        return new V3(
                x.x * v.x + y.x * v.y + z.x * v.z,
                x.y * v.x + y.y * v.y + z.y * v.z,
                x.z * v.x + y.z * v.y + z.z * v.z);
    }

    /** Mt * v: a world vector in this frame's coordinates. */
    public V3 mulTransposed(V3 v) {
        return new V3(x.dot(v), y.dot(v), z.dot(v));
    }
}
