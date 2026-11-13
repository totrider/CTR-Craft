package com.ctrcraft.terrain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The bomb's roll over the terrain field: a sliced 45 degree wedge (Copycats+ style), stairs and a block are ramps; a two-block wall is a wall. */
class BombRollTest {
    /** Flat ground at height 0 with extra boxes (x0, y0, z0, x1, y1, z1) standing on it. */
    static BlockSource world(List<double[]> extra) {
        return new BlockSource() {
            @Override
            public void boxes(double x0, double y0, double z0, double x1, double y1, double z1, BoxVisitor v) {
                for (int bx = (int) Math.floor(x0); bx < Math.ceil(x1); bx++) {
                    for (int bz = (int) Math.floor(z0); bz < Math.ceil(z1); bz++) {
                        if (0.0 > y0) v.box(bx, -20, bz, bx + 1, 0.0, bz + 1);
                    }
                }
                for (double[] b : extra) {
                    if (b[3] > x0 && b[0] < x1 && b[5] > z0 && b[2] < z1 && b[4] > y0 && b[1] < y1) v.box(b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }

            @Override
            public int surface(int bx, int by, int bz) {
                return Surface.ASPHALT;
            }
        };
    }

    /** A wedge block at x 6..7 and z 4..8 rising 45 degrees towards +x, as {@code slices} boxes. */
    static List<double[]> wedge(int slices) {
        List<double[]> l = new ArrayList<>();
        for (int j = 0; j < slices; j++) {
            l.add(new double[] { 6.0 + j / (double) slices, 0, 4, 6.0 + (j + 1) / (double) slices, (j + 1) / (double) slices, 8 });
        }
        return l;
    }

    /** Rolls along +x from x = 2.5 at z = 6.3 the way BombEntity does (steps of 0.288, the window rebuilt every 1.5 blocks or 0.4 of height); returns the x it was blocked at, or NaN. */
    static double roll(List<double[]> extra, double zStart) {
        return roll(extra, zStart, LAG);
    }

    static final double LAG = TerrainField.RIDE_LAG;

    static double roll(List<double[]> extra, double zStart, double lag) {
        TerrainField f = new TerrainField(world(extra));
        double x = 2.5;
        double y = 0.0;
        double z = zStart;
        double px = Double.NaN;
        double py = 0;
        double pz = 0;
        for (int i = 0; i < 40; i++) {
            double nx = x + 0.288;
            if (Double.isNaN(px) || Math.abs(y - py) > 0.4 || (nx - px) * (nx - px) + (z - pz) * (z - pz) > 2.25) {
                f.prepare(x, y + lag, z, 0);
                px = x;
                py = y;
                pz = z;
            }
            double ground = f.surface(nx, z);
            if (!Double.isNaN(ground) && ground >= y - 1.0) {
                y = ground;
            }
            if (f.blocked(nx, z, y + lag + TerrainField.STEP_UP, y + lag + TerrainField.STEP_UP + 0.4, 0.3)) {
                return nx;
            }
            x = nx;
        }
        return Double.NaN;
    }

    @Test
    void wedgeIsRolledUpDeadOn() {
        for (int slices : new int[] { 2, 4, 8, 16, 32 }) {
            assertTrue(Double.isNaN(roll(wedge(slices), 6.3)), "a wedge of " + slices + " slices, dead on");
            assertTrue(Double.isNaN(roll(wedge(slices), 4.1)), "a wedge of " + slices + " slices, at its edge");
        }
    }

    /** Mirrored: the bomb goes along -x towards a wedge that rises towards -x (the same wedge turned round), from x = 12.5. */
    static double rollBack(List<double[]> extra, double z) {
        List<double[]> m = new ArrayList<>();
        for (double[] b : extra) m.add(new double[] { 14 - b[3], b[1], b[2], 14 - b[0], b[4], b[5] });
        TerrainField f = new TerrainField(world(m));
        double x = 11.5;
        double y = 0.0;
        double px = Double.NaN;
        double py = 0;
        for (int i = 0; i < 40; i++) {
            double nx = x - 0.288;
            if (Double.isNaN(px) || Math.abs(y - py) > 0.4 || (nx - px) * (nx - px) > 2.25) {
                f.prepare(x, y, z, 0);
                px = x;
                py = y;
            }
            double ground = f.surface(nx, z);
            if (!Double.isNaN(ground) && ground >= y - 1.0) y = ground;
            if (f.blocked(nx, z, y + TerrainField.STEP_UP, y + TerrainField.STEP_UP + 0.4, 0.3)) return nx;
            x = nx;
        }
        return Double.NaN;
    }

    @Test
    void wedgeAnywhereOnTheGridIsRolledUp() {
        int fails = 0;
        for (int slices : new int[] { 1, 2, 3, 4, 6, 8, 16, 32 }) {
            for (double z = 4.05; z < 7.96; z += 0.1) {
                for (double dx = 0; dx < 0.3; dx += 0.07) {
                    if (!Double.isNaN(roll(shift(wedge(slices), dx), z))) fails++;
                }
                if (!Double.isNaN(rollBack(wedge(slices), z))) fails++;
            }
        }
        assertTrue(fails == 0, fails + " rolls were stopped by a wedge that is a ramp");
    }

    static List<double[]> shift(List<double[]> w, double dx) {
        List<double[]> m = new ArrayList<>();
        for (double[] b : w) m.add(new double[] { b[0] + dx, b[1], b[2], b[3] + dx, b[4], b[5] });
        return m;
    }

    /** Slope blocks one after another: each rises 1 over its block, the next starts where it ended. */
    static List<double[]> slopeStaircase(int blocks) {
        List<double[]> l = new ArrayList<>();
        for (int k = 0; k < blocks; k++) {
            l.add(new double[] { 6 + k, 0, 4, 7 + k, k, 8 });             // the base under the next slope block (a full block column)
            for (double[] b : wedge(8)) {
                l.add(new double[] { b[0] + k, k, b[2], b[3] + k, k + b[4], b[5] });
            }
        }
        return l;
    }

    @Test
    void apexIntoTheNextSlopeBlock() {
        for (double z = 4.3; z < 7.8; z += 0.25) {
            assertTrue(Double.isNaN(roll(slopeStaircase(3), z)), "three slope blocks in a row, z " + z + ": " + roll(slopeStaircase(3), z));
        }
    }

    @Test
    void crestThenAFullBlockStep() {
        List<double[]> l = wedge(8);
        l.add(new double[] { 7, 0, 4, 9, 2, 8 });                    // beyond the crest (1.0) a platform a block higher
        for (double z = 4.3; z < 7.8; z += 0.25) {
            assertTrue(Double.isNaN(roll(l, z)), "slope, then a block step up, z " + z + ": " + roll(l, z));
        }
    }

    @Test
    void withoutTheLagMarginTheApexIsAWall() {
        int failed = 0;
        for (double z = 4.3; z < 7.8; z += 0.25) {
            if (!Double.isNaN(roll(slopeStaircase(3), z, 0.0)) || !Double.isNaN(roll(wedgeThenBlock(), z, 0.0))) failed++;
        }
        System.out.println("APEX without the lag margin: " + failed + " of 14 lines blocked");
    }

    static List<double[]> wedgeThenBlock() {
        List<double[]> l = wedge(8);
        l.add(new double[] { 7, 0, 4, 9, 2, 8 });
        return l;
    }

    @Test
    void oneBlockStepAndStairsAreRamps() {
        List<double[]> step = List.of(new double[] { 6, 0, 4, 9, 1, 8 });
        assertTrue(Double.isNaN(roll(step, 6.3)), "a full block");
        List<double[]> stairs = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            stairs.add(new double[] { 6 + k, 0, 4, 7 + k, 0.5 * (k + 1), 8 });
        }
        assertTrue(Double.isNaN(roll(stairs, 6.3)), "slab stairs");
    }

    @Test
    void aTwoBlockWallIsAWall() {
        double hit = roll(List.of(new double[] { 6, 0, 4, 7, 2, 8 }), 6.3);
        assertFalse(Double.isNaN(hit), "a wall explodes the bomb");
        assertTrue(hit > 5.0 && hit < 6.2, "at its face, not far before it: " + hit);
    }
}
