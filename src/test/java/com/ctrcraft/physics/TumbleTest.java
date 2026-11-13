package com.ctrcraft.physics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The blast tumble (VehStuckProc_Tumble): no speed, and a bounce again and again. */
class TumbleTest {
    @Test
    void theKartBouncesWhileItTumbles() {
        KartSim k = new KartSim(KartClass.BALANCED, new V3(0, 0, 0), 0);
        for (int i = 0; i < 40; i++) k.tick(KartSimTest.FLOOR, KartInput.NONE);
        k.itemBlast();
        double maxY = 0;
        int airborne = 0;
        for (int i = 0; i < 90; i++) {
            k.tick(KartSimTest.FLOOR, KartInput.NONE);
            maxY = Math.max(maxY, k.position().y);
            if (!k.grounded()) airborne++;
        }
        System.out.println("tumble max height lu " + maxY + " airborne frames " + airborne);
        assertTrue(maxY > 100.0, "the kart should leave the ground, max height " + maxY);
        for (int i = 0; i < 120; i++) k.tick(KartSimTest.FLOOR, KartInput.NONE);
        assertTrue(k.reserves() == 0, "the tumble's bounces give no hang-time boost, reserves " + k.reserves());
    }

    @Test
    void anExplosionTakesTheSpeedAway() {
        KartSim k = new KartSim(KartClass.BALANCED, new V3(0, 0, 0), 0);
        for (int i = 0; i < 80; i++) k.tick(KartSimTest.FLOOR, KartInput.NONE.equals(null) ? null : new KartInput(0, true, false, false, false, false));
        int before = (int) Math.abs(k.forwardSpeed());
        k.itemBlast();
        for (int i = 0; i < 75; i++) k.tick(KartSimTest.FLOOR, KartInput.NONE);
        System.out.println("speed before " + before + " after the tumble " + k.forwardSpeed());
        assertTrue(Math.abs(k.forwardSpeed()) < before / 3, "the blast should stop most of the speed: " + before + " -> " + k.forwardSpeed());
    }
}
