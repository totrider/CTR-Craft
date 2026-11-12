package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The start grid and the traffic lights, pure Java.
 *
 * <p>From the decomp (ctr-native): {@code MainGameStart_Initialize} sets {@code trafficLightsTimer = 0xF00} (3840) and karts stay held
 * (KS_ENGINE_REVVING) until it reaches 0 ({@code VehStuckProc_RevEngine_Update}). NOT in what was read: when each light comes on. The
 * three lights at 960 ms spacing here are this mod's choice. The grid layout is also ours: CTR's start positions are per-track data.
 */
public final class RaceGrid {
    private RaceGrid() {}

    /** The whole countdown from the first light to GO, in game ticks (3840 ms at 50 ms a tick, rounded up). */
    public static final int LIGHTS_TICKS = 77;
    /** Spacing of the three lights, ticks (about 960 ms). */
    public static final int LIGHT_SPACING_TICKS = 19;

    /** How many of the three lights are lit with {@code ticksLeft} to go (0 before the first, 3 from the third until GO). */
    public static int lightsLit(int ticksLeft) {
        if (ticksLeft > LIGHTS_TICKS) return 0;
        int sinceStart = LIGHTS_TICKS - ticksLeft;
        return Math.min(3, 1 + sinceStart / LIGHT_SPACING_TICKS);
    }

    /** A kart's place on the grid: position in blocks and facing as the standing-sign rotation yaw in degrees. */
    public record Slot(double x, double y, double z, float yaw) {}

    /**
     * Slot {@code i} (0 is pole position) of a grid behind the start node at {@code start}, which faces {@code rotation}: two karts abreast,
     * the second of each row a little further back, rows 3.5 blocks apart, the first row 2.5 behind the node.
     */
    public static Slot slot(long start, int rotation, int i) {
        double fx = NodeGeometry.facingX(rotation);
        double fz = NodeGeometry.facingZ(rotation);
        double lx = fz * -1.0;       // lateral axis (to the node's right), perpendicular to the facing
        double lz = fx;
        int row = i / 2;
        int col = i % 2;
        double back = 2.5 + row * 3.5 + (col == 1 ? 1.75 : 0.0);
        double side = col == 0 ? -1.4 : 1.4;
        double x = NodeGeometry.x(start) + 0.5 - fx * back + lx * side;
        double z = NodeGeometry.z(start) + 0.5 - fz * back + lz * side;
        return new Slot(x, NodeGeometry.y(start), z, (rotation & 15) * 22.5F);
    }

    /** The order the entrants take the slots in: shuffled. */
    public static <T> List<T> shuffled(List<T> entrants, Random random) {
        List<T> out = new ArrayList<>(entrants);
        Collections.shuffle(out, random);
        return out;
    }
}
