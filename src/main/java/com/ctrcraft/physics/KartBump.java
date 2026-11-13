package com.ctrcraft.physics;

import java.util.List;

/**
 * Kart against kart: the CTR sphere bump. A port of {@code VehPhysForce_CollideDrivers} (VehPhysForce.c:1045-1072, the search) and
 * {@code VehPhysCrash_AnyTwoCars} / {@code VehPhysCrash_BounceSelf} (ctr-native game/Vehicle/VehPhysCrash.c). No Minecraft imports.
 *
 * <p>Every kart is a sphere of {@link #HIT_RADIUS} lu (THREAD_DRIVER_HIT_RADIUS = 0x40, namespace_Proc.h). The nearest other kart
 * whose centre is closer than the radii's sum bumps this one. CTR changes both karts' velocities in one call; here each side
 * works out its own half from the other's position and velocity, which is the same arithmetic (the weighted velocity and the
 * contact normal are shared, mirrored for the other side).
 *
 * <p>Units: positions in lu, velocities in lu/256 per frame (CTR's {@code posCurr >> 8} and {@code velocity}).
 */
public final class KartBump {
    private KartBump() {}

    /** THREAD_DRIVER_HIT_RADIUS (namespace_Proc.h:79). */
    public static final int HIT_RADIUS = 0x40;
    /** VEH_PHYS_CRASH_BOUNCE_DOT_DIVISOR: the bounce takes dot * 0x1000 / 0xC00 of the closing speed off, a third of it back as bounce. */
    private static final double BOUNCE = 4096.0 / 0xC00;
    /** VEH_PHYS_CRASH_BOUNCE_Y_CLAMP: a bounce may not throw a kart upwards past this. */
    private static final double BOUNCE_Y_CLAMP = 0x3200;
    /** hitDir (4096 = 1.0) * hitStrength >> VEH_PHYS_CRASH_VECTOR_SPEED_SHIFT (8): the separating push, per lu of overlap. */
    private static final double PUSH_PER_LU = 4096.0 / 256.0;
    /** VEH_PHYS_CRASH_FEEDBACK_MIN_IMPACT: a bump quieter than this makes no sound. */
    public static final int FEEDBACK_MIN_IMPACT = 0x200;
    /** VEH_PHYS_CRASH_VOLUME_IMPACT_MAX / _VOLUME_MIN / _VOLUME_MAX: impact to a 0..255 sound volume. */
    public static final int VOLUME_IMPACT_MAX = 0x1900;
    public static final int VOLUME_MIN = 0x3F;
    public static final int VOLUME_MAX = 0xFF;
    /** VEH_PHYS_CRASH_FEEDBACK_COOLDOWN_FRAMES: frames between two crash sounds. */
    public static final int FEEDBACK_COOLDOWN_FRAMES = 3;

    /** MetaPhys COLLISION_WEIGHT (zGlobal_DATA.c:7240): BALANCED 256, ACCEL 256, SPEED 300, TURN 200. */
    public static int weight(KartClass c) {
        return switch (c) {
            case BALANCED, ACCEL -> 256;
            case SPEED -> 300;
            case TURN -> 200;
        };
    }

    /**
     * Another body, as this one sees it: a kart (the default {@code reach} is the two hit radii, CTR's rule) or, for the mod's own entity
     * contact, anything else with the centre distance {@code reach} (lu) below which it touches. {@code id} is the host's handle for it.
     */
    public record Other(V3 pos, V3 vel, int weight, double reach, int id) {
        public Other(V3 pos, V3 vel, int weight) {
            this(pos, vel, weight, 2.0 * HIT_RADIUS, -1);
        }
    }

    /**
     * The bumped velocity, how hard it was (BounceSelf's largest |dot|, in velocity units; 0 when nothing hit), the contact normal (from the
     * other body to this one) and the other's {@code id}.
     */
    public record Result(V3 vel, int impact, V3 normal, int id) {
        public static final Result NONE = new Result(null, 0, V3.UP, -1);

        public boolean hit() { return vel != null; }
    }

    /** VehCalc_MapToRange(impact, 0, VOLUME_IMPACT_MAX, VOLUME_MIN, VOLUME_MAX). */
    public static int volume(int impact) {
        if (impact <= 0) return VOLUME_MIN;
        if (impact >= VOLUME_IMPACT_MAX) return VOLUME_MAX;
        return VOLUME_MIN + impact * (VOLUME_MAX - VOLUME_MIN) / VOLUME_IMPACT_MAX;
    }

    /**
     * The bump for a kart at {@code pos} moving {@code vel} (CTR's {@code driver->velocity} with the engine's accel already
     * taken off) against the nearest of {@code others}.
     */
    public static Result resolve(V3 pos, V3 vel, int weight, List<Other> others) {
        // PROC_CollidePointWithBucket: the nearest by squared distance; dist is search position minus the other's (other to self).
        // With every reach the same (karts only) that is CTR's pick; with entities mixed in, the deepest overlap wins.
        Other best = null;
        double bestSq = Double.MAX_VALUE;
        double bestOverlap = 0;
        for (Other o : others) {
            V3 d = pos.sub(o.pos());
            double sq = d.dot(d);
            double overlap = o.reach() - Math.sqrt(sq);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestSq = sq;
                best = o;
            }
        }
        if (best == null) return Result.NONE;
        double radiusSum = best.reach();

        // VehPhysCrash_AnyTwoCars: hitDir, hitStrength
        double distance = Math.sqrt(bestSq);
        V3 n = distance != 0 ? pos.sub(best.pos()).div(distance) : new V3(0, 0, 1); // dist 0: (0, 0, 4096)
        double hitStrength = radiusSum - distance;
        if (hitStrength <= 0) return Result.NONE;

        // the two weights blend the velocities; both sides bounce off that common velocity
        double ws = weight;
        double wo = best.weight();
        V3 blend = vel.mul(ws).add(best.vel().mul(wo)).div(ws + wo);

        // BounceSelf for the other kart: it only counts if it is closing in (dot > 0), and only gives the impact
        double impact = 0;
        double dotOther = best.vel().sub(blend).dot(n);
        if (dotOther > 0) impact = Math.max(impact, dotOther);

        // BounceSelf for this kart: closing in is dot < 0
        V3 out = vel;
        V3 diff = vel.sub(blend);
        double dot = diff.dot(n);
        if (dot < 0) {
            impact = Math.max(impact, -dot);
            diff = diff.sub(n.mul(dot * BOUNCE));
            double y = diff.y + blend.y;
            if (vel.y < y && y > BOUNCE_Y_CLAMP) y = BOUNCE_Y_CLAMP;
            out = new V3(diff.x + blend.x, y, diff.z + blend.z);
        }

        // and the push apart, along the line between the centres
        out = out.add(n.mul(hitStrength * PUSH_PER_LU));
        return new Result(out, (int) impact, n, best.id());
    }
}
