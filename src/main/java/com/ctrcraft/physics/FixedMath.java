package com.ctrcraft.physics;

/** CTR's integer helpers (VehCalc.c) and angle conventions: 4096 units = one turn. */
public final class FixedMath {
    private FixedMath() {}

    public static final int ANGLE_MASK = 0xFFF;

    /** VehCalc_MapToRange: the clamps test the old bounds literally, so a reversed range saturates. */
    public static int map(int v, int omin, int omax, int nmin, int nmax) {
        if (v <= omin) return nmin;
        if (v >= omax) return nmax;
        return nmin + (int) ((long) (v - omin) * (long) (nmax - nmin) / (long) (omax - omin));
    }

    /** VehCalc_InterpBySpeed: v toward target by step, no overshoot. */
    public static int approach(int v, int step, int target) {
        return v < target ? Math.min(v + step, target) : Math.max(v - step, target);
    }

    public static double approach(double v, double step, double target) {
        return v < target ? Math.min(v + step, target) : Math.max(v - step, target);
    }

    public static double rad(int angle) {
        return angle * (Math.PI * 2.0) / 4096.0;
    }

    public static int wrap(int angle) {
        return angle & ANGLE_MASK;
    }

    /** The signed shortest difference a - b in [-2048, 2048). */
    public static int diff(int a, int b) {
        return ((a - b + 2048) & ANGLE_MASK) - 2048;
    }

    public static int wrapSigned(int angle) {
        return ((angle + 2048) & ANGLE_MASK) - 2048;
    }

    public static int atan2Units(double y, double x) {
        return wrap((int) Math.round(Math.atan2(y, x) * 4096.0 / (Math.PI * 2.0)));
    }

    /**
     * VehPhysForce_RotAxisAngle: the frame with Y on n and Z the heading carried onto the plane by the
     * minimal rotation from up to n; X = n x Z (the kart's left).
     */
    public static Mat3 rotAxisAngle(V3 n, int angle) {
        double a = rad(angle);
        V3 f = new V3(Math.sin(a), 0, Math.cos(a));
        V3 k = V3.UP.cross(n);
        double s = k.length();
        V3 z;
        if (s < 1e-6) {
            z = f;
        } else {
            k = k.div(s);
            double c = n.y;
            z = f.mul(c).add(k.cross(f).mul(s)).add(k.mul(k.dot(f) * (1.0 - c)));
        }
        V3 x = n.cross(z);
        return new Mat3(x, n, z);
    }
}
