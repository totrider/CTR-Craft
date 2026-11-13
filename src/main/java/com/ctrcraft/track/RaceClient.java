package com.ctrcraft.track;

/** The client's copy of the race state (see {@link RacePayload}). Plain data, no client classes. */
public final class RaceClient {
    private RaceClient() {}

    private static volatile RacePayload state = RacePayload.off();
    private static int placedSeq;
    private static volatile long updatedAt;

    private static volatile long introEndMs;
    private static volatile int introTotalMs;

    private static volatile int finishSeq;
    private static volatile int finishPlace;

    /** Counts the times this player finished a race (the DONE state may be replaced by OFF within the same client tick, so the music cannot rely on seeing it). */
    public static int finishSeq() {
        return finishSeq;
    }

    public static int finishPlace() {
        return finishPlace;
    }

    static void update(RacePayload payload) {
        if (payload.phase() == RacePayload.DONE && state.phase() != RacePayload.DONE) {
            finishPlace = payload.finishPlace();
            finishSeq++;
        }
        boolean lightsBegin = payload.phase() == RacePayload.LIGHTS && state.phase() != RacePayload.LIGHTS;
        state = payload;
        updatedAt = System.currentTimeMillis();
        if (lightsBegin) {
            // the phase starts with the intro (jingle and camera pan): the lamps count from where the ticks left reach the lamps' own stretch
            introTotalMs = Math.max(0, (payload.ticksLeft() - (RaceGrid.LIGHTS_TICKS + 20)) * 50);
            introEndMs = updatedAt + introTotalMs;
        }
    }

    /** Milliseconds of the race intro left (0 when there is none, or it is over): until then no lamps, no revving. */
    public static long introMsLeft() {
        return state.phase() == RacePayload.LIGHTS ? Math.max(0, introEndMs - System.currentTimeMillis()) : 0;
    }

    public static int introTotalMs() {
        return introTotalMs;
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
