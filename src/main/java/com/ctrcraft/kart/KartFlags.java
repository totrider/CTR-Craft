package com.ctrcraft.kart;

import com.ctrcraft.physics.KartSim;

/**
 * What other players (and the particle and sound code) need to know about a kart, packed in one int that the driver's client sends
 * and the entity syncs: effect bits, the wheelie frame (5 bits) and the turbo flame size (4 bits).
 */
public final class KartFlags {
    private KartFlags() {}

    public static final int DRIFTING = 1;
    public static final int BOOSTING = 2;
    public static final int AIRBORNE = 4;
    public static final int BACK_SKID = 8;
    public static final int FRONT_SKID = 16;
    public static final int WALL_RUB = 32;
    public static final int BACKFIRE = 64;
    /** Burning past the sacred fire cap: only a super turbo pad does that in CTR, and we have none yet. */
    public static final int ULTRA = 128;
    /** The bits a client may set (anything else is dropped by the server). */
    public static final int MASK = 0x7FFFFFF;

    public static int encode(KartSim sim) {
        int f = 0;
        if (sim.state == KartSim.State.DRIFTING) f |= DRIFTING;
        if (sim.reserves() > 0) f |= BOOSTING;
        if (!sim.grounded()) f |= AIRBORNE;
        int skids = sim.skids();
        if ((skids & 1) != 0) f |= BACK_SKID;
        if ((skids & 2) != 0) f |= FRONT_SKID;
        if (sim.againstWall()) f |= WALL_RUB;
        if (sim.failedBoost() > 0) f |= BACKFIRE;
        f |= (sim.wheelieCode() & 31) << 8;
        f |= (sim.fireSize() & 15) << 13;
        f |= (sim.flameFade() & 7) << 17;
        if (sim.ultraFire()) f |= ULTRA;
        f |= (sim.tumbleCode() & 127) << 20;
        return f;
    }

    public static int wheelie(int flags) {
        return (flags >> 8) & 31;
    }

    public static int fireSize(int flags) {
        return (flags >> 13) & 15;
    }

    /** How far the flame has faded, 0 (full) to 7. */
    public static int fade(int flags) {
        return (flags >> 17) & 7;
    }

    /** 0 when the kart is not tumbling (after an explosion), else 64 | the turn of its flip in 64ths of a revolution. */
    public static int tumble(int flags) {
        return (flags >> 20) & 127;
    }

    public static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }
}
