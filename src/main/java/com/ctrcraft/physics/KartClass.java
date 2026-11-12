package com.ctrcraft.physics;

/** The four engine classes of NTSC-U CTR (MetaPhys, zGlobal_DATA.c:7122-7189). Values from the physics spec, section 10. */
public enum KartClass {
    //       accel speed  turnRate turnDecrease turnInputDelay driftTurnBase
    BALANCED(480, 13140, 28, 4080, 5000, 14),
    ACCEL(544, 13520, 26, 9080, 4500, 10),
    SPEED(448, 13900, 24, 5666, 4000, 5),
    TURN(512, 12950, 30, 7252, 5500, 18);

    /** Accel_ClassStat: engine push per frame. */
    public final int accel;
    /** Speed_ClassStat: top speed. */
    public final int speed;
    /** TurnRate: steer strength at full lock. */
    public final int turnRate;
    /** TurnDecreaseRate: top speed lost at full steer. */
    public final int turnDecrease;
    /** TurnInputDelay: steering response per frame. */
    public final int turnInputDelay;
    /** DriftTurnBase: the powerslide's base turn. */
    public final int driftTurnBase;

    KartClass(int accel, int speed, int turnRate, int turnDecrease, int turnInputDelay, int driftTurnBase) {
        this.accel = accel;
        this.speed = speed;
        this.turnRate = turnRate;
        this.turnDecrease = turnDecrease;
        this.turnInputDelay = turnInputDelay;
        this.driftTurnBase = driftTurnBase;
    }
}
