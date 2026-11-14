package com.ctrcraft.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ctrcraft.kart.KartScale;
import com.ctrcraft.physics.KartClass;
import com.ctrcraft.physics.KartInput;
import com.ctrcraft.physics.KartSim;
import com.ctrcraft.physics.V3;

/** The kart over a 45 degree sliced ramp (Copycats+ style) that ends in a hard edge: it goes over the apex, never scrapes a wall there, at the kart physics' own scale. */
class KartApexTest {
    /** A ramp along +z from z = 0, {@code blocks} blocks long, rising {@code rise} per block, {@code width} wide round x = 6.5, as {@code slicesPerBlock} boxes a block; no block after it. */
    static List<double[]> ramp(int blocks, int slicesPerBlock, double width, double rise) {
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < blocks * slicesPerBlock; i++) {
            double z0 = i / (double) slicesPerBlock;
            double z1 = (i + 1) / (double) slicesPerBlock;
            l.add(new double[] { 6.5 - width / 2, 0, z0, 6.5 + width / 2, z1 * rise, z1 });
        }
        return l;
    }

    /** Full gas from {@code runUp} blocks before the ramp: {furthest z in blocks, frames spent against a wall}. */
    static double[] drive(List<double[]> extra, double runUp, int frames) {
        double lu = KartScale.LU_PER_BLOCK;
        TerrainCollider collider = new TerrainCollider(new TerrainField(BombRollTest.world(extra)), lu);
        KartSim k = new KartSim(KartClass.BALANCED, new V3(6.5 * lu, 0, -runUp * lu), 0);
        KartInput gas = new KartInput(0, true, false, false, false, false);
        double maxZ = -99;
        int walls = 0;
        for (int f = 0; f < frames; f++) {
            collider.prepare(k.position(), k.velocity().y / 256.0);
            k.tick(collider, gas);
            maxZ = Math.max(maxZ, k.position().z / lu);
            if (k.againstWall()) walls++;
        }
        return new double[] { maxZ, walls };
    }

    @Test
    void goesOverTheApexOfAHardEndedRamp() {
        for (int slices : new int[] { 1, 2, 4, 8, 16 }) {
            for (double width : new double[] { 1, 3 }) {
                for (double rise : new double[] { 0.5, 1.0, 1.5 }) {
                    if (slices == 1 && rise > 1.0) continue;        // a 1.5 high step is a wall (more than the step the kart climbs)
                    for (double runUp : new double[] { 3, 12 }) {
                        double[] r = drive(ramp(3, slices, width, rise), runUp, 300);
                        String what = slices + " slices, width " + width + ", rise " + rise + ", run-up " + runUp;
                        assertTrue(r[0] > 20.0, "went over the apex: " + what + ", furthest z " + r[0]);
                        // (a ramp of one box a block, narrow, is a staircase: a one frame rub on a step is fine)
                        // (steeper than 45 degrees is a wall, and a one block wide ramp rubs its sides for a frame or two; a wide 45 degree one never touches a wall)
                        if (width >= 3 && rise <= 1.0) assertEquals(0, (int) r[1], "no wall scrape: " + what);
                    }
                }
            }
        }
    }
}
