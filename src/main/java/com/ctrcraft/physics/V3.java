package com.ctrcraft.physics;

/** Immutable 3-vector (right-handed, +Y up), in whatever unit the caller works in. No Minecraft imports. */
public final class V3 {
    public static final V3 ZERO = new V3(0, 0, 0);
    public static final V3 UP = new V3(0, 1, 0);

    public final double x, y, z;

    public V3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public V3 add(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }
    public V3 sub(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }
    public V3 mul(double s) { return new V3(x * s, y * s, z * s); }
    public V3 div(double s) { return new V3(x / s, y / s, z / s); }
    public double dot(V3 o) { return x * o.x + y * o.y + z * o.z; }
    public V3 cross(V3 o) { return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }

    /** Unit vector, or {@code fallback} for a zero-length one. */
    public V3 normalizeOr(V3 fallback) {
        double l = length();
        return l < 1e-9 ? fallback : div(l);
    }

    public boolean isUp() { return x == 0 && y == 1 && z == 0; }

    @Override
    public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
