package com.ctrcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ctrcraft.physics.RevEngine.StartBoost;

/** The grid rev: the fill, the over-rev lockout and which start boost a release earns (VehStuckProc.c). */
class RevEngineTest {
    static final int ACCEL = KartClass.BALANCED.speed + 1500;
    static final int MS = KartSim.MS;

    static boolean run(RevEngine r, boolean gas, int frames) {
        boolean over = false;
        for (int i = 0; i < frames; i++) over |= r.tick(gas, MS);
        return over;
    }

    @Test
    void theGasFillsTheRevsAtHalfTheDistanceAFrame() {
        RevEngine r = new RevEngine(ACCEL);
        int target = ACCEL + 4096 / 3;
        r.tick(true, MS);
        assertEquals(5000, r.level(), "the first step is capped at 5000");
        r.tick(true, MS);
        assertEquals(5000 + Math.min(5000, (target - 5000) / 2), r.level(), "half the way left, never more than 5000");
        run(r, true, 4);
        assertEquals(RevEngine.State.READY, r.state(), "past the acceleration speed");
        int before = r.level();
        r.tick(false, MS);
        assertEquals(before - 1000, r.level(), "letting go loses half, at most 1000, a frame");
    }

    @Test
    void heldAtTheTargetTheEngineOverRevsAndLocksOut() {
        RevEngine r = new RevEngine(ACCEL);
        int frames = 0;
        while (!r.tick(true, MS)) {
            frames++;
            assertTrue(frames < 40);
        }
        assertNull(r.release(), "an over-revved engine gets no boost");
        assertEquals(RevEngine.State.BUSY, r.state());
    }

    @Test
    void releasedWhileRevvedAGoodBoostAndPumpedFirstABigOne() {
        assertNull(new RevEngine(ACCEL).release(), "no revs, no boost");
        RevEngine small = new RevEngine(ACCEL);
        run(small, true, 6);
        assertEquals(StartBoost.SMALL, small.release());

        RevEngine big = new RevEngine(ACCEL);
        for (int i = 0; i < 2; i++) {
            run(big, true, 6);
            run(big, false, 8);
        }
        assertEquals(ACCEL + 4096, big.target(), "two pumps raise the target to its top");
        run(big, true, 8);
        assertEquals(StartBoost.BIG, big.release());
    }

    @Test
    void theStartBoostGivesReservesAndAFlame() {
        KartSim sim = new KartSim(KartClass.BALANCED, V3.ZERO, 0);
        assertEquals(0, sim.reserves());
        sim.startBoost(StartBoost.SMALL);
        assertEquals(RevEngine.START_BOOST_RESERVES, sim.reserves());
        assertEquals(ACCEL, sim.accelSpeed());
    }
}
