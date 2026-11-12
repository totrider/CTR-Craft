package com.ctrcraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.ctrcraft.physics.KartClass;
import com.ctrcraft.physics.KartInput;
import com.ctrcraft.physics.KartSim;
import com.ctrcraft.physics.V3;

/** The smooth-ground model: ramps from steps, walls kept, and the CTR physics driving over it. */
class TerrainFieldTest {
    static final double LU = 50.0;

    /** A world of columns: solid from y = -20 up to height(bx, bz). */
    static BlockSource world(BiFunction<Integer, Integer, Double> height) {
        return new BlockSource() {
            @Override
            public void boxes(double x0, double y0, double z0, double x1, double y1, double z1, BoxVisitor v) {
                for (int bx = (int) Math.floor(x0); bx < Math.ceil(x1); bx++) {
                    for (int bz = (int) Math.floor(z0); bz < Math.ceil(z1); bz++) {
                        double h = height.apply(bx, bz);
                        if (h > y0) {
                            v.box(bx, -20, bz, bx + 1, h, bz + 1);
                        }
                    }
                }
            }

            @Override
            public int surface(int bx, int by, int bz) {
                return Surface.ASPHALT;
            }
        };
    }

    static TerrainField field(BiFunction<Integer, Integer, Double> height, double cx, double cy, double cz) {
        TerrainField f = new TerrainField(world(height));
        f.prepare(cx, cy, cz, 0);
        return f;
    }

    @Test
    void flatGroundIsFlat() {
        TerrainField f = field((x, z) -> 0.0, 8.5, 0, 5.5);
        assertEquals(0.0, f.surface(8.3, 5.2), 1e-9);
        double[] n = f.normal(8.3, 5.2);
        assertEquals(1.0, n[1], 1e-9);
    }

    /** The surface along x at z = 5.5 from x0 to x1 in steps of 0.05. */
    static double[] along(TerrainField f, double x0, double x1) {
        int n = (int) Math.round((x1 - x0) / 0.05) + 1;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = f.surface(x0 + i * 0.05, 5.5);
        }
        return out;
    }

    static void assertMonotoneAndGentle(double[] s, double maxSlope) {
        for (int i = 1; i < s.length; i++) {
            assertTrue(s[i] >= s[i - 1] - 1e-9, "never dips going up: " + s[i - 1] + " -> " + s[i] + " at sample " + i);
            assertTrue((s[i] - s[i - 1]) / 0.05 <= maxSlope, "no steeper than " + maxSlope + " at sample " + i);
        }
    }

    @Test
    void aOneBlockStepBecomesAGentleRampOnItsLowerSide() {
        TerrainField f = field((x, z) -> x >= 10 ? 1.0 : 0.0, 8.5, 0, 5.5);
        double[] s = along(f, 7.0, 12.0);
        assertMonotoneAndGentle(s, 1.02);
        assertEquals(0.0, f.surface(7.5, 5.5), 1e-9, "flat well before it");
        assertTrue(f.surface(9.0, 5.5) < 0.3, "the ramp starts about a block before the face");
        double mid = f.surface(9.5, 5.5);
        assertTrue(mid > 0.25 && mid < 0.75, "half way up half way: " + mid);
        assertTrue(f.surface(10.0, 5.5) >= 1.0 - 0.18 - 1e-9, "at the face within a couple of pixels of the real top");
        assertEquals(1.0, f.surface(11.5, 5.5), 1e-9, "flat on top");
        double[] n = f.normal(9.5, 5.5);
        assertTrue(n[1] >= 0.643, "always ground for the physics: ny = " + n[1]);
    }

    @Test
    void aStaircaseOfFullBlocksIsOneContinuousRamp() {
        // the window follows the kart's height (steps are looked for from where it stands), so stand mid-staircase
        TerrainField f = field((x, z) -> x >= 11 ? 2.0 : x >= 10 ? 1.0 : 0.0, 9.5, 1.0, 5.5);
        double[] s = along(f, 8.0, 12.0);
        assertMonotoneAndGentle(s, 1.02);
        assertEquals(1.5, f.surface(10.5, 5.5), 0.2, "the first step is itself a ramp up to the second");
        assertEquals(2.0, f.surface(12.5, 5.5), 0.2);
    }

    @Test
    void aCurbOfHalfABlockIsAShortRamp() {
        TerrainField f = field((x, z) -> x >= 10 ? 0.5 : 0.0, 8.5, 0, 5.5);
        assertMonotoneAndGentle(along(f, 8.0, 11.5), 1.02);
        assertTrue(f.surface(9.5, 5.5) < 0.2);
        assertTrue(f.surface(10.0, 5.5) >= 0.5 - 0.18 - 1e-9);
        assertEquals(0.5, f.surface(11.5, 5.5), 1e-9);
    }

    @Test
    void stairsOfFullBlocksAndSlabsMakeOneSmoothIncline() {
        // 0 | full block +1.0 | slab +0.5 | full block +1.0 | slab +0.5 ...: the mix that used to be ramp, flat, ramp, flat
        TerrainField f = field((x, z) -> x >= 14 ? 4.0 : x >= 13 ? 3.0 : x >= 12 ? 2.5 : x >= 11 ? 1.5 : x >= 10 ? 1.0 : 0.0,
                11.5, 1.5, 5.5);
        double[] s = along(f, 9.2, 12.0);
        assertMonotoneAndGentle(s, 1.02);
        double minSlope = Double.MAX_VALUE;
        double maxChange = 0;
        double prev = Double.NaN;
        for (int i = 5; i < s.length; i += 5) {                     // slopes over quarter-block spans
            double slope = (s[i] - s[i - 5]) / 0.25;
            minSlope = Math.min(minSlope, slope);
            if (!Double.isNaN(prev)) maxChange = Math.max(maxChange, Math.abs(slope - prev));
            prev = slope;
        }
        assertTrue(minSlope >= 0.3, "no flat tread in the middle of the climb: min slope " + minSlope);
        assertTrue(maxChange <= 0.5, "the slope changes gradually: " + maxChange);
    }

    @Test
    void stairsOfSlabsAreAnEvenSlope() {
        TerrainField f = field((x, z) -> x >= 14 ? 2.0 : x >= 13 ? 1.5 : x >= 12 ? 1.0 : x >= 11 ? 0.5 : 0.0, 12.5, 1.0, 5.5);
        double[] s = along(f, 10.5, 13.5);
        assertMonotoneAndGentle(s, 1.02);
        double lo = Double.MAX_VALUE;
        double hi = 0;
        for (int i = 5; i < s.length; i += 5) {
            double slope = (s[i] - s[i - 5]) / 0.25;
            lo = Math.min(lo, slope);
            hi = Math.max(hi, slope);
        }
        assertTrue(lo >= 0.3 && hi <= 0.75, "about 0.5 all the way: " + lo + " to " + hi);
    }

    @Test
    void aTallWallGetsNoRampAndStaysAWall() {
        TerrainField f = field((x, z) -> x >= 10 ? 2.0 : 0.0, 8.5, 0, 5.5);
        assertEquals(0.0, f.surface(9.9, 5.5), 1e-9, "a two-block step is not climbable");
        assertTrue(Double.isNaN(f.surface(10.5, 5.5)), "its top is out of reach, so there is no ground on it");
        double[] c = f.deepest(9.6, 0.5, 5.5, 0.5);
        assertNotNull(c);
        assertTrue(c[1] < -0.9, "the wall pushes straight back: nx = " + c[1]);
    }

    @Test
    void aCliffEdgeEndsTheGround() {
        TerrainField f = field((x, z) -> x >= 10 ? -10.0 : 0.0, 8.5, 0, 5.5);
        assertEquals(0.0, f.surface(9.9, 5.5), 1e-9);
        assertTrue(Double.isNaN(f.surface(10.2, 5.5)), "nothing to stand on past the edge");
        assertEquals(-1, f.kindAt(10.2, 5.5));
    }

    // ---- the CTR physics driving over it ----

    static void drive(KartSim k, TerrainCollider c, int frames, KartInput in) {
        for (int i = 0; i < frames; i++) {
            c.prepare(k.position(), k.velocity().y / 256.0);
            k.tick(c, in);
        }
    }

    static KartSim kartFacingPlusX() {
        return new KartSim(KartClass.BALANCED, new V3(5 * LU, 0, 5.5 * LU), 1024); // forward is (sin a, 0, cos a) = +X
    }

    @Test
    void theKartDrivesUpAOneBlockStepWithoutStopping() {
        TerrainField f = new TerrainField(world((x, z) -> x >= 20 ? 1.0 : 0.0));
        TerrainCollider c = new TerrainCollider(f, LU);
        KartSim k = kartFacingPlusX();
        drive(k, c, 140, new KartInput(0, true, false, false, false, false));
        assertTrue(k.position().x > 24 * LU, "it got up and over: x = " + k.position().x / LU);
        assertEquals(1.0 * LU, k.position().y, 6.0, "and is standing on the top");
        assertTrue(k.speed() > 8000, "without losing its speed: " + k.speed());
        assertTrue(k.state == KartSim.State.NORMAL);
    }

    @Test
    void theKartDrivesUpAStaircase() {
        TerrainField f = new TerrainField(world((x, z) -> x >= 24 ? 3.0 : x >= 22 ? 2.0 : x >= 20 ? 1.0 : 0.0));
        TerrainCollider c = new TerrainCollider(f, LU);
        KartSim k = kartFacingPlusX();
        drive(k, c, 160, new KartInput(0, true, false, false, false, false));
        assertTrue(k.position().x > 25 * LU && k.position().y > 2.5 * LU, "x " + k.position().x / LU + " y " + k.position().y / LU);
    }

    @Test
    void theKartCrashesIntoATwoBlockWall() {
        TerrainField f = new TerrainField(world((x, z) -> x >= 20 ? 2.0 : 0.0));
        TerrainCollider c = new TerrainCollider(f, LU);
        KartSim k = kartFacingPlusX();
        drive(k, c, 140, new KartInput(0, true, false, false, false, false));
        assertTrue(k.position().x < 20.2 * LU, "it did not get through: x = " + k.position().x / LU);
        assertTrue(k.position().y < 0.5 * LU);
    }

    @Test
    void aSlabCurbBarelyBumpsTheKart() {
        TerrainField f = new TerrainField(world((x, z) -> x >= 20 ? 0.5 : 0.0));
        TerrainCollider c = new TerrainCollider(f, LU);
        KartSim k = kartFacingPlusX();
        drive(k, c, 140, new KartInput(0, true, false, false, false, false));
        assertTrue(k.position().x > 25 * LU);
        assertTrue(k.speed() > 11000, "speed " + k.speed());
    }
}
