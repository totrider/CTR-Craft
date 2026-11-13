package com.ctrcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The airborne poses are the game's: Crash's last pose, zGlobal_DATA.c matArr07[3], and the codes that go on the wire. */
class AirTiltTest {
    @Test
    void crashsLastPoseRollsAboutTwentyThreeDegrees() {
        AirTilt.Pose p = AirTilt.pose(4, 0, 50.0);
        assertNotNull(p);
        assertEquals(-265 * 360.0 / 4096.0, p.rollDegrees(), 1e-9);        // -23.3 degrees
        assertEquals(-11 * 360.0 / 4096.0, p.pitchDegrees(), 1e-9);
        assertEquals(18 * 360.0 / 4096.0, p.yawDegrees(), 1e-9);
        assertEquals(-1343 / 256.0 / 50.0, p.x(), 1e-9);                   // 5.2 level units to the side
        assertEquals(4255 / 256.0 / 50.0, p.lift(), 1e-9);                 // and 16.6 up: a third of a block
        assertEquals(1149 / 256.0 / 50.0, p.forward(), 1e-9);
    }

    @Test
    void theRollGrowsFromPoseToPose() {
        double last = 0;
        for (int code = 1; code <= AirTilt.FRAMES; code++) {
            double roll = AirTilt.pose(code, 0, 50.0).rollDegrees();
            assert roll < last : "pose " + code;
            last = roll;
        }
    }

    @Test
    void noPoseOutsideTheCodes() {
        assertNull(AirTilt.pose(0, 0, 50.0));
        assertNull(AirTilt.pose(AirTilt.FRAMES + 1, 0, 50.0));
        assertNull(AirTilt.pose(1, 13, 50.0));
    }
}
