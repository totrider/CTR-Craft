package com.ctrcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Headless checks of the CTR physics port against numbers derived from the decompilation (spec sections 3, 5, 6). */
class KartSimTest {
    /** An infinite flat floor at y = 0. */
    static final Collider FLOOR = new Collider() {
        @Override
        public Contact deepest(V3 centre, double radius) {
            double depth = radius - centre.y;
            return depth > 0 ? new Contact(depth, V3.UP, new V3(centre.x, 0, centre.z)) : null;
        }

        @Override
        public GroundHit ground(V3 from, double maxDist) {
            if (from.y < 0 || from.y > maxDist) return null;
            return new GroundHit(new V3(from.x, 0, from.z), V3.UP, from.y);
        }
    };

    static KartInput in(double steer, boolean gas, boolean r1, boolean l1) {
        return new KartInput(steer, gas, false, false, r1, l1);
    }

    static KartSim kart() {
        return new KartSim(KartClass.BALANCED, new V3(0, 0, 0), 0);
    }

    static void run(KartSim k, int frames, KartInput input) {
        for (int i = 0; i < frames; i++) k.tick(FLOOR, input);
    }

    @Test
    void restsOnTheGround() {
        KartSim k = kart();
        run(k, 30, KartInput.NONE);
        assertTrue(k.grounded());
        assertEquals(0.0, k.position().y, 1.0);
        assertEquals(0, Math.abs(k.forwardSpeed()), 5);
    }

    @Test
    void fullGasReachesTopSpeed() {
        KartSim k = kart();
        run(k, 37, in(0, true, false, false));
        // spec 3.5: about 37 frames from 0 to top speed (13140), net accel 360 a frame
        assertTrue(k.forwardSpeed() > 0.9 * 13140, "speed after 37 frames: " + k.forwardSpeed());
        run(k, 40, in(0, true, false, false));
        // `speed` settles on the class top speed; `forwardSpeed` (speedApprox) is read before the engine's
        // excess is removed, so it sits one frame of friction + accel above it (13500), as in CTR.
        assertEquals(13140, k.speed(), 200);
        assertTrue(k.position().z > 0, "drives forward along +Z at heading 0");
    }

    @Test
    void steeringLeftIncreasesHeading() {
        KartSim k = kart();
        run(k, 40, in(0, true, false, false));
        int before = k.heading();
        run(k, 20, in(-1, true, false, false)); // steer < 0 = left
        int turned = FixedMath.diff(k.heading(), before);
        assertTrue(turned > 0, "left steer turns the angle up, got " + turned);
        KartSim r = kart();
        run(r, 40, in(0, true, false, false));
        int b2 = r.heading();
        run(r, 20, in(1, true, false, false));
        assertTrue(FixedMath.diff(r.heading(), b2) < 0);
    }

    @Test
    void hopLeavesTheGroundForAboutTenFrames() {
        KartSim k = kart();
        run(k, 10, KartInput.NONE);
        k.tick(FLOOR, in(0, false, true, false)); // tap R1
        int air = 0;
        double apex = 0;
        for (int i = 0; i < 40; i++) {
            k.tick(FLOOR, in(0, false, false, false));
            if (!k.grounded()) air++;
            apex = Math.max(apex, k.position().y);
        }
        assertTrue(air >= 8 && air <= 14, "airborne frames " + air);
        // spec 6.4: apex about 45.8 lu
        assertEquals(46.0, apex, 8.0);
    }

    @Test
    void powerslideRedZoneBoostGivesReserves() {
        KartSim k = kart();
        run(k, 60, in(0, true, false, false));
        // hop while steering left and keep R1 held through the landing
        KartInput hold = in(-1, true, true, false);
        k.tick(FLOOR, hold);
        List<KartEvent> all = new ArrayList<>();
        boolean drifting = false;
        for (int i = 0; i < 30 && !drifting; i++) {
            k.tick(FLOOR, hold);
            all.addAll(k.drainEvents());
            drifting = k.state == KartSim.State.DRIFTING;
        }
        assertTrue(drifting, "landing with the button held and steer in starts a powerslide");
        // the meter fills over 30 frames; its last 15 are the red zone: press L1 once it is red
        int frames = 0;
        while (k.turboMeter() == null || k.turboMeter()[1] == 0) {
            k.tick(FLOOR, hold);
            assertTrue(++frames < 40, "meter reaches the red zone");
        }
        k.tick(FLOOR, in(-1, true, true, true));
        all.addAll(k.drainEvents());
        KartEvent boost = all.stream().filter(e -> e.type() == KartEvent.Type.BOOST).findFirst().orElse(null);
        assertNotNull(boost, "a red-zone press is a successful boost");
        assertTrue(k.reserves() > 0);
    }

    @Test
    void earlyPressIsABackfire() {
        KartSim k = kart();
        run(k, 60, in(0, true, false, false));
        KartInput hold = in(-1, true, true, false);
        k.tick(FLOOR, hold);
        for (int i = 0; i < 30 && k.state != KartSim.State.DRIFTING; i++) k.tick(FLOOR, hold);
        assertEquals(KartSim.State.DRIFTING, k.state);
        k.tick(FLOOR, hold);
        k.drainEvents();
        k.tick(FLOOR, in(-1, true, true, true)); // meter is still near-empty here
        assertTrue(k.drainEvents().stream().anyMatch(e -> e.type() == KartEvent.Type.BOOST_EARLY));
        assertEquals(0, k.reserves());
    }

    @Test
    void turboPadLightsTheFlameAndRearsTheKart() {
        KartSim k = kart();
        run(k, 40, in(0, true, false, false));
        int maxWheelie = 0;
        for (int i = 0; i < 20; i++) {
            k.turboPad();
            k.tick(FLOOR, in(0, true, false, false));
            maxWheelie = Math.max(maxWheelie, k.wheelieCode());
        }
        assertEquals(8, k.fireSize(), "a pad burns the biggest flame");
        assertFalse(k.ultraFire(), "and it is not the blue one: only a super turbo pad passes the sacred fire cap");
        assertEquals(12, maxWheelie, "the rise ends in the hold frame");
        // off the pad the flame burns on for as long as the reserves last (about a second left)
        run(k, 20, in(0, true, false, false));
        assertTrue(k.reserves() > 0 && k.fireSize() == 8, "still burning: reserves " + k.reserves());
        // reserves run out, the flame fades slowly, the nose comes down and the wheelie ends
        int guard = 0;
        while ((k.reserves() > 0 || k.wheelieCode() != 0 || k.fireSize() != 0) && guard++ < 400) {
            k.tick(FLOOR, in(0, true, false, false));
        }
        assertEquals(0, k.wheelieCode());
        assertEquals(0, k.fireSize());
    }

    @Test
    void aPowerslideBoostFlameIsAShortPuff() {
        // VehFire.c: a powerslide (or hang-time) flame shows for 2 frames then fades fast, even with boost left
        KartSim k = kart();
        run(k, 60, in(0, true, false, false));
        KartInput hold = in(-1, true, true, false);
        k.tick(FLOOR, hold);
        for (int i = 0; i < 30 && k.state != KartSim.State.DRIFTING; i++) k.tick(FLOOR, hold);
        assertEquals(KartSim.State.DRIFTING, k.state);
        int guard = 0;
        while ((k.turboMeter() == null || k.turboMeter()[0] < 0.85) && guard++ < 40) k.tick(FLOOR, hold);
        k.tick(FLOOR, in(-1, true, true, true));          // a late press: a long boost
        assertTrue(k.reserves() > 1000, "reserves " + k.reserves());
        assertTrue(k.fireSize() > 0, "the flame lights");
        assertFalse(k.ultraFire());
        for (int i = 0; i < 30; i++) k.tick(FLOOR, hold);
        assertTrue(k.reserves() > 0, "the boost is still on: " + k.reserves());
        assertEquals(0, k.fireSize(), "but the puff is over");
    }

    @Test
    void engineNoteRisesWithSpeed() {
        KartSim k = kart();
        run(k, 20, KartInput.NONE);
        int idle = k.engineSound()[0];
        run(k, 80, in(0, true, false, false));
        int fast = k.engineSound()[0];
        assertTrue(fast > idle + 40, "idle " + idle + ", fast " + fast);
    }

    @Test
    void brakingAtSpeedSkidsTheBackTyres() {
        KartSim k = kart();
        run(k, 60, in(0, true, false, false));
        k.tick(FLOOR, new KartInput(0, false, true, false, false, false));
        assertTrue((k.skids() & 1) != 0);
    }

    @Test
    void coastingStopsInAboutThreePointSixSeconds() {
        KartSim k = kart();
        run(k, 80, in(0, true, false, false));
        assertFalse(k.forwardSpeed() == 0);
        int frames = 0;
        while (Math.abs(k.forwardSpeed()) > 50 && frames < 300) {
            k.tick(FLOOR, KartInput.NONE);
            frames++;
        }
        // spec 3.6: -120 a frame from 13140 is about 110 frames
        assertTrue(frames > 90 && frames < 130, "coast frames " + frames);
    }
}
