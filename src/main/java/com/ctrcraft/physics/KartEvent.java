package com.ctrcraft.physics;

/** Something that happened this tick, for sounds and effects. */
public record KartEvent(Type type, int value) {
    public enum Type { HOP, LAND, DRIFT_START, DRIFT_END, BOOST, BOOST_EARLY, METER_FULL, METER_RED, HANG_TIME, WALL_RUB, SLAM, SPIN_OUT }

    public static KartEvent of(Type t) {
        return new KartEvent(t, 0);
    }
}
