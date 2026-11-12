package com.ctrcraft.track;

/** The client's copy of the race state (see {@link RacePayload}). Plain data, no client classes. */
public final class RaceClient {
    private RaceClient() {}

    private static volatile RacePayload state = RacePayload.off();
    private static int placedSeq;
    private static volatile long updatedAt;

    static void update(RacePayload payload) {
        state = payload;
        updatedAt = System.currentTimeMillis();
    }

    public static RacePayload state() {
        return state;
    }

    public static boolean active() {
        return state.phase() != RacePayload.OFF;
    }

    /** The kart stands on the grid, revving, until GO. */
    public static boolean hold() {
        return state.hold();
    }

    /** Milliseconds since the last update: the HUD counts the race clock on from the last known time. */
    public static long sinceUpdateMs() {
        return System.currentTimeMillis() - updatedAt;
    }

    /** The slot the server put this racer on, once per placement (null if there is nothing new). */
    public static RacePayload takePlacement() {
        RacePayload s = state;
        if (s.placeSeq() != 0 && s.placeSeq() != placedSeq) {
            placedSeq = s.placeSeq();
            return s;
        }
        return null;
    }

    public static void clear() {
        state = RacePayload.off();
        placedSeq = 0;
    }
}
