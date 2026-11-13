package com.ctrcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The speedometer needle (VehPhysGeneral needle blend, UI_DrawSpeedNeedle angles). */
class SpeedometerTest {
    private static final int ACCEL = KartClass.BALANCED.speed + 1500;
    private static final int SACRED = 4096;

    @Test
    void the_needle_follows_the_speed_three_sixteenths_a_frame() {
        assertEquals((0 * 13 + 8000 * 3) >> 4, Speedometer.step(0, 8000, 0));
        int needle = 0;
        for (int i = 0; i < 120; i++) needle = Speedometer.step(needle, 13000, i);
        assertEquals(13000, needle, 20);
    }

    @Test
    void reversing_slowly_lets_the_needle_fall_and_reversing_fast_wobbles_it_low() {
        assertEquals(8000 - (8000 >> 3), Speedometer.step(8000, -0x80, 0));
        int needle = 8000;
        for (int i = 0; i < 200; i++) needle = Speedometer.step(needle, -0x400, i);
        assertTrue(needle > 600 && needle < 1300, "needle wobbles about 3.5 * 0x100: " + needle);
    }

    @Test
    void the_dial_runs_from_305_degrees_at_rest_through_213_at_the_acceleration_speed_to_157_at_full_boost() {
        assertEquals(3472, Speedometer.angle(0, ACCEL, SACRED));
        assertEquals(2432, Speedometer.angle(ACCEL, ACCEL, SACRED), 1);
        assertEquals(1792, Speedometer.angle(ACCEL + SACRED, ACCEL, SACRED), 1);
        // in between the angle falls steadily
        assertTrue(Speedometer.angle(ACCEL / 2, ACCEL, SACRED) < 3472 && Speedometer.angle(ACCEL / 2, ACCEL, SACRED) > 2432);
        assertTrue(Speedometer.angle(ACCEL + SACRED / 2, ACCEL, SACRED) < 2432 && Speedometer.angle(ACCEL + SACRED / 2, ACCEL, SACRED) > 1792);
    }
}
