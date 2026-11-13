package com.ctrcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ctrcraft.physics.KartBump.Other;
import com.ctrcraft.physics.KartBump.Result;

/** The kart-vs-kart sphere bump (VehPhysCrash_AnyTwoCars / BounceSelf). */
class KartBumpTest {
    static final int W = 256;

    static Result bump(V3 pos, V3 vel, int weight, V3 otherPos, V3 otherVel, int otherWeight) {
        return KartBump.resolve(pos, vel, weight, List.of(new Other(otherPos, otherVel, otherWeight)));
    }

    @Test
    void karts_farther_apart_than_the_two_radii_do_not_touch() {
        assertFalse(bump(new V3(0, 0, 0), V3.ZERO, W, new V3(0, 0, 128), V3.ZERO, W).hit());
        assertFalse(bump(new V3(0, 0, 0), V3.ZERO, W, new V3(0, 0, 200), V3.ZERO, W).hit());
        assertTrue(bump(new V3(0, 0, 0), V3.ZERO, W, new V3(0, 0, 127), V3.ZERO, W).hit());
    }

    @Test
    void overlapping_karts_are_pushed_apart_along_the_line_between_them() {
        // 100 lu apart: 28 lu of overlap -> 28 * 16 velocity units, away from the other kart
        Result r = bump(new V3(0, 0, 0), V3.ZERO, W, new V3(0, 0, 100), V3.ZERO, W);
        assertEquals(0, r.vel().x, 1e-9);
        assertEquals(-28 * 16, r.vel().z, 1e-9);
        assertEquals(0, r.impact());
    }

    @Test
    void a_head_on_bump_bounces_back_and_reports_the_closing_speed() {
        // self drives +z at 3000 into an other standing still, equal weights: blend 1500, dot -1500
        Result r = bump(new V3(0, 0, 0), new V3(0, 0, 3000), W, new V3(0, 0, 100), V3.ZERO, W);
        // n = (0,0,-1); relative to the blend (1500) self closes at 1500; 4/3 of that comes off: 1500 - 2000 = -500 -> 1000 in all,
        // then the push of 28 lu of overlap (448) back along n
        assertEquals(1000 - 448, r.vel().z, 1e-6);
        assertEquals(1500, r.impact());
        assertTrue(r.vel().z < 3000);
    }

    @Test
    void a_kart_already_moving_apart_is_only_pushed() {
        Result r = bump(new V3(0, 0, 0), new V3(0, 0, -2000), W, new V3(0, 0, 100), V3.ZERO, W);
        // dot = (-2000 - -1000) . n(0,0,-1) = +1000 -> not closing: no bounce, just the push
        assertEquals(-2000 - 28 * 16, r.vel().z, 1e-9);
    }

    @Test
    void the_heavier_kart_is_bounced_less() {
        V3 other = new V3(0, 0, 100);
        V3 v = new V3(0, 0, 3000);
        double lightRebound = 3000 - bump(new V3(0, 0, 0), v, 200, other, V3.ZERO, 300).vel().z;
        double heavyRebound = 3000 - bump(new V3(0, 0, 0), v, 300, other, V3.ZERO, 200).vel().z;
        assertTrue(lightRebound > heavyRebound);
    }

    @Test
    void the_nearest_of_several_karts_is_the_one_that_bumps() {
        List<Other> two = List.of(new Other(new V3(0, 0, 120), V3.ZERO, W), new Other(new V3(100, 0, 0), V3.ZERO, W));
        Result r = KartBump.resolve(new V3(0, 0, 0), V3.ZERO, W, two);
        assertEquals(-28 * 16, r.vel().x, 1e-9);
        assertEquals(0, r.vel().z, 1e-9);
    }

    @Test
    void a_bounce_cannot_throw_a_kart_up_past_the_clamp() {
        Result r = bump(new V3(0, 0, 0), new V3(0, 12000, 0), W, new V3(0, -100, 0), new V3(0, -12000, 0), W);
        assertTrue(r.vel().y <= 0x3200 + 28 * 16 + 1e-9);
    }

    @Test
    void the_collision_weights_are_the_decomps() {
        assertEquals(256, KartBump.weight(KartClass.BALANCED));
        assertEquals(256, KartBump.weight(KartClass.ACCEL));
        assertEquals(300, KartBump.weight(KartClass.SPEED));
        assertEquals(200, KartBump.weight(KartClass.TURN));
    }

    @Test
    void the_crash_volume_maps_the_impact() {
        assertEquals(0x3F, KartBump.volume(0));
        assertEquals(0xFF, KartBump.volume(0x1900));
        assertEquals(0xFF, KartBump.volume(99999));
    }
}
