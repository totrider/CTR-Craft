package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The start grid and the traffic lights, pure Java.
 *
 * <p>From the decomp (ctr-native): {@code MainGameStart_Initialize} sets {@code trafficLightsTimer = 0xF00} (3840) and karts stay held
 * (KS_ENGINE_REVVING) until it reaches 0 ({@code VehStuckProc_RevEngine_Update}). {@code DotLights_AudioAndVideo} (DotLights.c) lights the
 * first red at 0xB40 (2880) ms left, the second at 0x780 (1920), the third at 0x3C0 (960) and the green at 0, which is GO: four lamps,
 * one beat apart, and the first 960 ms of the countdown is the lamps sliding in. The grid layout is ours: CTR's start positions are per-track data.
 */
public final class RaceGrid {
    private RaceGrid() {}

    /** The whole countdown from the first light to GO, in game ticks (3840 ms at 50 ms a tick, rounded up). */
    public static final int LIGHTS_TICKS = 77;
    /**
     * The race intro before the lamps: the intro jingle plays (about 7.8 s) while the camera pans over the grid from the finish line and comes back to the driver's own
     * kart; the lamps start when it is over. In game ticks.
     */
    public static final int INTRO_TICKS = 156;
    /** DOT_LIGHT_TIMER_RED1 / _RED2 / _RED3 (DotLights.c): the milliseconds left when each red lamp comes on. */
    public static final int RED1_MS = 0xB40;
    public static final int RED2_MS = 0x780;
    public static final int RED3_MS = 0x3C0;
    /** How long the green lamp stays up after GO (DOT_LIGHT_TIMER_SHOW_MIN is -0x3BF). */
    public static final int GREEN_SHOWN_MS = 0x3C0;

    /** How many red lamps are lit with {@code ticksLeft} game ticks to go (0 to 3); the green one is GO itself. */
    public static int lightsLit(int ticksLeft) {
        int ms = ticksLeft * 50;
        if (ms <= RED3_MS) return 3;
        if (ms <= RED2_MS) return 2;
        if (ms <= RED1_MS) return 1;
        return 0;
    }

    /** A kart's place on the grid: position in blocks and facing as the standing-sign rotation yaw in degrees. */
    public record Slot(double x, double y, double z, float yaw) {}

    /**
     * The original game's eight start positions, in blocks. Every one of its 16 race tracks has the same ones (the {@code DriverSpawn[8]} of the level
     * header, read from the disc and divided by 50 level units to the block, relative to the first): two rows of FOUR karts side by side across the track,
     * a kart every 3.8 blocks along a row, the second row about 5 blocks behind the first (the original's start camera shows it). The disc's values wobble by up to half a block
     * between karts (the rows are not quite straight); here both rows are straight, at the averages. {@code ALONG_ROW[i]} is how far
     * slot i is from slot 0 along its row (to the right), {@code BEHIND[i]} how far behind the first row it is. Slots 0 to 3 are the front row, 4 to 7 the back row.
     */
    private static final double[] ALONG_ROW = { 0.0, 3.77, 7.54, 11.31, 0.0, 3.77, 7.54, 11.31 };
    private static final double[] BEHIND = { 0.0, 0.0, 0.0, 0.0, 4.9, 4.9, 4.9, 4.9 };
    /** How many blocks the whole pattern is from front to back (two rows and the gap to the next two): further racers repeat it behind. */
    public static final double PATTERN_DEPTH = 10.0;
    /** The first row is this far behind the start node, so a kart on the line itself is not on the node. */
    private static final double FIRST_ROW_BEHIND = 2.0;
    /** The rows are centred on the node: slot 0, the leftmost, is this far to its left. */
    private static final double HALF_ROW = 5.65;

    /**
     * Slot {@code i} (0 is pole position, front row far left) of a grid behind the start node at {@code start}, which faces {@code rotation}: the original game's
     * two rows of four, centred on the node. More than eight racers repeat the pattern further back.
     */
    public static Slot slot(long start, int rotation, int i) {
        double fx = NodeGeometry.facingX(rotation);
        double fz = NodeGeometry.facingZ(rotation);
        double lx = NodeGeometry.leftX(rotation);
        double lz = NodeGeometry.leftZ(rotation);
        int k = i % 8;
        double back = FIRST_ROW_BEHIND + BEHIND[k] + (i / 8) * PATTERN_DEPTH;
        double left = HALF_ROW - ALONG_ROW[k];
        double x = NodeGeometry.x(start) + 0.5 - fx * back + lx * left;
        double z = NodeGeometry.z(start) + 0.5 - fz * back + lz * left;
        return new Slot(x, NodeGeometry.y(start), z, (rotation & 15) * 22.5F);
    }

    /** The order the entrants take the slots in: shuffled. */
    public static <T> List<T> shuffled(List<T> entrants, Random random) {
        List<T> out = new ArrayList<>(entrants);
        Collections.shuffle(out, random);
        return out;
    }
}
