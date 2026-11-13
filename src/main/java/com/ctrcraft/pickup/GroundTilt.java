package com.ctrcraft.pickup;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The slope a placed weapon lies on, found once when it comes to rest (the original turns a dropped mine and a landed beaker onto the surface normal: VehPickupItem_ShootNow,
 * RB_Potion_ThTick_InAir). Four downward probes round the weapon give the slope of the surface; the result is packed like the kart's tilt: x and z of the unit normal as two
 * bytes (value = n * 127 + 127), 0x7F7F flat.
 */
public final class GroundTilt {
    private GroundTilt() {}

    public static final int FLAT = 0x7F7F;
    /** The probes' distance from the weapon, blocks. */
    private static final double REACH = 0.5;
    /** Slopes steeper than this (rise over run) are not followed: a wall or a step edge, not a surface to lie on. */
    private static final double MAX_SLOPE = 1.7;

    private static double height(Level level, Entity e, double x, double y, double z) {
        var hit = level.clip(new ClipContext(new Vec3(x, y + 1.0, z), new Vec3(x, y - 2.0, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        return hit.getType() == HitResult.Type.MISS ? y : hit.getLocation().y;
    }

    /** The packed slope and the height of the surface under the weapon's middle (the mean of the four probes, which on a plane is the plane's height there). */
    public record Rest(int tilt, double y) {}

    public static Rest rest(Level level, Entity e, double x, double y, double z) {
        double h1 = height(level, e, x - REACH, y, z);
        double h2 = height(level, e, x + REACH, y, z);
        double h3 = height(level, e, x, y, z - REACH);
        double h4 = height(level, e, x, y, z + REACH);
        double nx = (h1 - h2) / (2.0 * REACH);
        double nz = (h3 - h4) / (2.0 * REACH);
        if (Math.hypot(nx, nz) > MAX_SLOPE) return new Rest(FLAT, y);
        double len = Math.sqrt(nx * nx + 1.0 + nz * nz);
        int bx = (int) Math.round(nx / len * 127.0) + 127;
        int bz = (int) Math.round(nz / len * 127.0) + 127;
        // never raised: on the plane of the slope it lies lower than the highest point of the blocks under it, which is where it came to rest
        return new Rest((bx & 0xFF) | ((bz & 0xFF) << 8), Math.min(y, (h1 + h2 + h3 + h4) / 4.0));
    }

    /**
     * A cheap fingerprint of the blocks a resting weapon (or kart) lies on: the states of the 3 x 3 columns under and at it. A resting thing sleeps, and looks at this a
     * second or so apart; when it changes (a block was broken, placed, pushed by a piston, blown up) the thing wakes, settles again and works its slope out afresh.
     */
    public static int signature(Level level, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        int sig = 1;
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    sig = sig * 31 + level.getBlockState(p.set(bx + dx, by + dy, bz + dz)).hashCode();
                }
            }
        }
        return sig;
    }

    public static int sample(Level level, Entity e, double x, double y, double z) {
        return rest(level, e, x, y, z).tilt();
    }
}
