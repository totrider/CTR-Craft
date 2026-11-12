package com.ctrcraft.physics;

/**
 * Revving on the grid before GO: CTR's {@code KS_ENGINE_REVVING} state (ctr-native {@code VehStuckProc.c}:
 * {@code VehStuckProc_RevEngine_Init} / {@code _Update} / {@code _Animate}). The kart stands; the gas fills a rev level
 * ({@code fireLevel}) toward a target ({@code boostMeter}) at half the distance a frame (step 0x100..5000); held at the target for more
 * than 192 ms the engine over-revs and is locked out until the gas is let go and the revs die. Let go above the class's acceleration
 * speed and fallen back under it, the target steps up by a third of the sacred fire speed (+3) toward its top: pumping the gas raises it.
 * At GO an engine revved above the acceleration speed and not locked out earns the start boost: big if the target reached its top,
 * else small (both 960 reserves, fire level 0x80 or 0x20).
 *
 * <p>Constants are the decomp's {@code VEH_STUCK_REV_*} enum. The structure follows the same function as ported in world-of-kartcraft's
 * {@code rev.rs} (GPL-3.0), which was read to cross-check the decomp. Pure Java, 30 Hz frames of {@link KartSim#MS}.
 */
public final class RevEngine {
    public enum StartBoost { SMALL, BIG }

    /** What the exhaust and the meter show: below the acceleration speed, at or above it ("boost now"), or cooling down. */
    public enum State { LOW, READY, BUSY }

    private enum Charge { IDLE, RELEASED_ABOVE_ACCEL, ACTIVE }

    private static final int SACRED_FIRE_SPEED = 4096;
    public static final int SMALL_BOOST_FIRE = 0x20;
    public static final int BIG_BOOST_FIRE = 0x80;
    public static final int START_BOOST_RESERVES = 960;

    private static final int FILL_STEP_MAX = 5000;
    private static final int STEP_MIN = 0x100;
    private static final int OVERREV_TIMEOUT_MS = 192;
    private static final int RELEASE_COOLDOWN_MS = 0x100;
    private static final int DECAY_STEP_MAX_NORMAL = 1000;
    private static final int DECAY_STEP_MAX_LOCKED = 3000;
    private static final int EMPTY_COOLDOWN_MS = 0xC0;

    private static final int LOCKOUT_PEDAL_HELD = 0x1;
    private static final int LOCKOUT_REV_DECAY = 0x2;
    private static final int LOCKOUT_ALL = LOCKOUT_PEDAL_HELD | LOCKOUT_REV_DECAY;

    private final int accel;
    private int fireLevel;
    private int boostMeter;
    private int overRevMs;
    private int releaseCooldownMs;
    private int emptyCooldownMs;
    private Charge charge = Charge.IDLE;
    private int lockout;

    /** {@code VehStuckProc_RevEngine_Init} for a class whose AccelSpeed_ClassStat is {@code accelSpeed}: no revs, nothing locked out. */
    public RevEngine(int accelSpeed) {
        this.accel = accelSpeed;
        this.boostMeter = accelSpeed + SACRED_FIRE_SPEED / 3;
    }

    private static int interp(int val, int speed, int desired) {
        return val > desired ? Math.max(val - speed, desired) : Math.min(val + speed, desired);
    }

    /** One frame of {@code ms} with the gas held or not. Returns true on the frame the engine over-revs (OtherFX 0xF). */
    public boolean tick(boolean gas, int ms) {
        releaseCooldownMs = Math.max(0, releaseCooldownMs - ms);
        emptyCooldownMs = Math.max(0, emptyCooldownMs - ms);
        if (gas && releaseCooldownMs == 0 && (lockout & LOCKOUT_ALL) == 0) {
            int step = Math.max(STEP_MIN, Math.min(FILL_STEP_MAX, Math.abs(fireLevel - boostMeter) >> 1));
            fireLevel = interp(fireLevel, step, boostMeter);
            charge = Charge.ACTIVE;
            if (boostMeter <= fireLevel) {
                overRevMs += ms;
                if (overRevMs > OVERREV_TIMEOUT_MS) {
                    charge = Charge.IDLE;
                    lockout |= LOCKOUT_ALL;
                    return true;
                }
            } else {
                overRevMs = 0;
            }
            return false;
        }
        overRevMs = 0;
        if (charge == Charge.ACTIVE) {
            releaseCooldownMs = RELEASE_COOLDOWN_MS;
            charge = accel < fireLevel ? Charge.RELEASED_ABOVE_ACCEL : Charge.IDLE;
        }
        if (charge != Charge.IDLE && fireLevel < accel) {
            charge = Charge.IDLE;
            boostMeter = interp(boostMeter, SACRED_FIRE_SPEED / 3 + 3, accel + SACRED_FIRE_SPEED);
        }
        if (fireLevel > 0) {
            int max = (lockout & LOCKOUT_REV_DECAY) != 0 ? DECAY_STEP_MAX_LOCKED : DECAY_STEP_MAX_NORMAL;
            int decay = Math.max(STEP_MIN, Math.min(max, fireLevel >> 1));
            fireLevel -= decay;
            if (fireLevel < 1) {
                emptyCooldownMs = EMPTY_COOLDOWN_MS;
                fireLevel = 0;
            }
        } else {
            lockout &= ~LOCKOUT_REV_DECAY;
            boostMeter = accel + SACRED_FIRE_SPEED / 3;
        }
        if (!gas) {
            lockout &= ~LOCKOUT_PEDAL_HELD;
        }
        return false;
    }

    /** The revs: what the speedometer's needle shows while revving. */
    public int level() {
        return fireLevel;
    }

    public int target() {
        return boostMeter;
    }

    public int accelSpeed() {
        return accel;
    }

    public State state() {
        if (emptyCooldownMs != 0 || (lockout & LOCKOUT_REV_DECAY) != 0) return State.BUSY;
        return fireLevel >= accel ? State.READY : State.LOW;
    }

    /** GO ({@code VehStuckProc_RevEngine_Update}): the boost the revs earn, or null for none (under the acceleration speed, or locked out). */
    public StartBoost release() {
        if (accel >= fireLevel || (lockout & LOCKOUT_ALL) != 0) return null;
        return accel + SACRED_FIRE_SPEED <= boostMeter ? StartBoost.BIG : StartBoost.SMALL;
    }
}
