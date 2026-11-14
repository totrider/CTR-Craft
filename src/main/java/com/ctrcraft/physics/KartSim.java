package com.ctrcraft.physics;

import static com.ctrcraft.physics.FixedMath.approach;
import static com.ctrcraft.physics.FixedMath.atan2Units;
import static com.ctrcraft.physics.FixedMath.diff;
import static com.ctrcraft.physics.FixedMath.map;
import static com.ctrcraft.physics.FixedMath.rad;
import static com.ctrcraft.physics.FixedMath.rotAxisAngle;
import static com.ctrcraft.physics.FixedMath.wrap;
import static com.ctrcraft.physics.FixedMath.wrapSigned;

import java.util.ArrayList;
import java.util.List;

/**
 * One kart, ticked at CTR's 30 Hz in CTR's own units: positions in level units (lu), speeds in 1/256 lu per
 * frame, angles in 4096ths of a turn, timers in CTR-milliseconds (32 per frame). Stages run in the game's
 * order (docs/PHYSICS_PORT.md): UPDATE, PHYS_LINEAR, PHYS_ANGULAR, APPLY_FORCES, collision sweep, ground
 * probe, JUMP_FRICTION, visual frame, hang-time meter.
 *
 * <p>No Minecraft imports: the world is a {@link Collider}. This is a port of the CTR-tools/ctr-native
 * decompilation (GPL-3.0) following the port spec of world-of-kartcraft; each method names its C source.
 * Not ported yet (M1 scope): items, masks, flight, water, kart-vs-kart bumping, hit reactions other than spin-out.
 */
public final class KartSim {
    /** One frame's CTR-milliseconds at 30 fps. */
    public static final int MS = 32;
    public static final double TICK_SECONDS = 1.0 / 30.0;

    public enum State { NORMAL, DRIFTING, CRASHING, SPINNING }

    private enum SpinPhase { FIRST, LAST, STOP }

    private enum FireKind { FREEZE, STACK }

    // ---- shared MetaPhys constants (spec section 10) ----
    private static final int GRAVITY = 900;
    private static final int JUMP_FORCE = 4596;
    private static final int PEDAL_FRICTION_PERP = 350;
    private static final int PEDAL_FRICTION_FWD = 120;
    private static final int NO_PEDAL_FRICTION_PERP = 425;
    private static final int NO_PEDAL_FRICTION_FWD = 120;
    private static final int BRAKE_FRICTION = 260;
    private static final int DRIFT_CURVE = 120;
    private static final int DRIFT_FRICTION = 120;
    private static final int ACCEL_RESERVES = 1152;
    private static final int SINGLE_TURBO_SPEED = 2048;
    private static final int SACRED_FIRE_SPEED = 4096;
    private static final int BACKWARD_SPEED = 5120;
    private static final int MASK_SPEED = 3900;
    private static final int BACKWARD_TURN_RATE = 48;
    private static final int TERMINAL_VELOCITY = 20480;
    private static final int STEER_ACCEL_STAGE4_FIRST = 64;
    private static final int STEER_ACCEL_STAGE2_FIRST = 4;
    private static final int STEER_ACCEL_STAGE2_LEN = 8;
    private static final int STEER_ACCEL_MAX = 3072;
    private static final int STEER_ACCEL_TURN_VEL_SCALE = 128;
    private static final int MODEL_ROT_VEL_MAX = 350;
    private static final int MODEL_ROT_VEL_MIN = 450;
    private static final int MODEL_TURN_COUNTER_STEER = 30;
    private static final int TURN_RESIST_MIN = 32;
    private static final int TURN_RESIST_MAX = 224;
    private static final int DRIFT_SWITCH_WAY = -10;
    private static final int DRIFT_STANDARD = 22;
    private static final int DRIFT_TURN_STARTUP_SCALE = 100;
    private static final int DRIFT_TURN_RAMP_MS = 60 << 5;
    private static final int DRIFT_FRAMES_TILL_SPINOUT = 60;
    private static final int DRIFT_SPIN_RATE_ACCEL = 3000;
    private static final int DRIFT_SPIN_RATE_DECEL = 500;
    private static final int DRIFT_RELEASE_ASSIST_MS = 15 << 5;
    private static final int DRIFT_SAME_DIR_ANGLE = 300;
    private static final int DRIFT_OPPOSITE_DIR_ANGLE = -128;
    private static final int DRIFT_TURN_ANGLE_SCALE = 768;
    private static final int TURBO_MAX_ROOM_MS = 30 << 5;
    private static final int TURBO_LOW_ROOM_MS = 15 << 5;
    private static final int TURBO_FULL_BAR_GAIN_MS = 60 << 5;
    private static final int DRIFT_BOOST_MS = 15 << 5;
    private static final int DRIFT_BOOST_AXIS_KICK = 36;
    private static final int SLOPE_FORWARD_SPEED_BONUS = 5120;
    private static final int SIDE_SPEED_CLAMP = 2560;
    private static final int MAX_SPEED = 25600;
    private static final int MAX_BASE_SPEED = 0x6400;
    private static final long WUMPA_NET_STAT = ((1500 << 12) / 5) - 1;
    private static final int WALL_RUB_MS = 240;
    private static final int WALL_RUB_SPEED_LIMIT = 3328;
    private static final int COYOTE_MS = 160;
    private static final int JUMP_COOLDOWN_MS = 384;
    private static final int JUMP_FORCED_MS = 160;
    private static final int JUMP_BUFFER_FRAMES = 10;
    private static final int JUMP_VERTICAL_CAP = 0x3700;
    private static final int LANDING_GRIP_MS = 320;
    private static final int HANG_TIME_MIN_MS = 640;
    private static final int HANG_TIME_MID_MS = 960;
    private static final int HANG_TIME_HIGH_MS = 1440;
    private static final int HANG_TIME_METER_MAX = 2400;
    private static final int HANG_TIME_RESERVES = 960;
    private static final int TURBO_PAD_RESERVES = 0x3C0;
    private static final int TURBO_PAD_ACCEL = 8000;
    private static final int ICE_EXIT_MS = -320;
    /** The kart's collision sphere: radius and centre height above the origin (lu). */
    public static final double SPHERE_RADIUS = 25.0;
    private static final int SPIN_MS = 0x3C0;
    private static final int REVERSE_SPIN_MS = 0x780;
    private static final int SLAM_MS = 640;
    private static final int SPIN_RATE = 300;
    private static final int SPIN_SLOW_SPEED = 0x2FF;
    private static final int SPIN_RECENTER_ANGLE = 400;
    private static final int SPIN_MIN_RECENTER_RATE = 0x20;
    private static final int SPIN_STOP_ANGLE = 16;
    private static final int SPIN_STOP_ANIM_FRAMES = 8;
    private static final int ACCEL_TAP_WINDOW_MS = 0x100;
    private static final int ACCEL_TAP_STEER_COUNT = 7;
    private static final int ACCEL_TAP_SPEED_MAX = 0x2600;
    private static final int ACCEL_TAP_STRENGTH = 0x5A;

    // ---- actionsFlagSet bits ----
    private static final int TOUCH_GROUND = 0x1;
    private static final int STARTED_TOUCH_GROUND = 0x2;
    private static final int JUMP_BUTTON_HELD = 0x4;
    private static final int ACCEL_PREVENTION = 0x8;
    private static final int STEER_LEFT = 0x10;
    private static final int BRAKE_WITH_ACCEL = 0x20;
    private static final int DRIVING_AGAINST_WALL = 0x2000;
    private static final int REVERSING = 0x20000;
    private static final int AIRBORNE = 0x80000;
    private static final int TURBO_LATCH = 0x400;
    private static final int BACK_SKID = 0x800;
    private static final int FRONT_SKID = 0x1000;
    /** What survives from one frame to the next; the rest is rebuilt each frame. */
    private static final int CARRY = TOUCH_GROUND | STEER_LEFT | REVERSING | AIRBORNE | TURBO_LATCH;

    // the turbo flame's life (VehFire.c / VehTurbo.c): a powerslide or hang-time flame shows for 2 frames then fades fast; a pad's burns
    // while the reserves last and fades slowly after; boosts other than a pad's hide the flame for 3 frames (0x60 ms) when they re-light it
    private static final int FLAME_POWERSLIDE_FRAMES = 2;
    private static final int FLAME_NO_DISAPPEAR = -1;
    private static final int FLAME_HIDE_FRAMES = 3;
    private static final int FLAME_FADE_FAST = 0x100;
    private static final int FLAME_FADE_SLOW = 0x40;
    private static final int FLAME_GONE = 0xFFF;
    private static final int FLAME_RESERVES_MIN = 0x10;

    private static final int WHEELIE_NONE = 0;
    private static final int WHEELIE_START = 1;
    private static final int WHEELIE_HOLD = 2;
    private static final int WHEELIE_RECOVER = 3;

    private KartClass kartClass;

    // ---- state ----
    public State state = State.NORMAL;
    /** The ground under the kart: the host's to set before a tick (CTR reads the touched quad's terrain). */
    public Terrain groundType = Terrain.ASPHALT;
    /** Wumpa fruit held, 0 to 10 (top-speed bonus). */
    public int wumpa = 0;

    private Terrain terrain = Terrain.ASPHALT;
    private Terrain speedTerrain = Terrain.ASPHALT;
    private int terrainBase;
    private boolean onPad;

    private V3 pos;
    private V3 vel = V3.ZERO;
    private int speed;
    private int speedApprox;
    private int velYaw;
    private int velPitch;

    private int angle;
    private int turnAngle;
    private int turnAnglePrev;
    private int turnAngleVel;
    private int simpTurn;
    private int spinRate;
    private int ampTurn;
    private int framesSteering = 10000;

    private int baseSpeed;
    private int fireSpeed;
    private int fwdAccelImpulse;
    private int forwardDir = 1;

    private int reserves;
    private int fireSpeedCap;
    private int turboOutside;
    private int turboMeter;

    private int multDrift;
    private int prevMultDrift;
    private int driftAssistMs;
    private int driftFrames;
    private int driftBoostMs;
    private int driftTotalMs;
    private int boostsAttempted;
    private int boostsSuccess;
    private boolean driftButtonR1 = true;

    private int jumpBuffer;
    private int jumpCooldown;
    private int coyote;
    private int jumpForced;
    private int airtime;
    private int jumpMeter;
    /** The speedometer needle's smoothed value ({@link Speedometer}) and the game's frame counter that feeds it. */
    private int needle;
    private int frameCounter;
    /** UI_JumpMeter_Update: the hang-time meter as the HUD shows it, kept for UI_JUMP_METER_DISPLAY_TIMER (0x5A0 ms) after landing. */
    private int jumpShown;
    private int jumpShownTimer;
    private int jumpVel = JUMP_FORCE;
    private int fall;

    private int wallRub;
    private int gripTimer;
    private int accelTapWindow;
    private int accelTapCount;

    private V3 n1 = V3.UP;
    private V3 n2 = V3.UP;
    private V3 n4 = V3.UP;
    private Mat3 moving;
    private Mat3 facing;
    private V3 nudge = V3.ZERO;

    private int flags;
    private int flagsPrev;
    private boolean prevR1;
    private boolean prevL1;

    private int stateTimer;
    private int noInput;
    private SpinPhase spinPhase = SpinPhase.FIRST;
    private int spinTurn;
    private int failedBoost;
    private double groundHeight;

    private boolean flameOn;
    private int flameCountdown = FLAME_NO_DISAPPEAR;
    private int flameAlpha;
    private int flameHide;
    private int flameSize = 4;

    private int wheelieKind = WHEELIE_NONE;
    private int wheelieIndex;
    /** The airborne pose animation (VehFrameProc_Driving): whether it plays, and its frame (0 to 3). */
    private boolean airTiltOn;
    private int airTiltFrame;
    /**
     * The drive animation's steering frame as a number from -1 (full left) to +1 (full right): the game maps the stick (simpTurnState, over the class's turn rate) onto the animation's
     * frames and moves the shown frame one step a frame towards it (VehCalc_InterpBySpeed, speed 1). The animation has 21 frames (counted in the racers' models,
     * tools/inspect_ctr_kart.py), so a step is 1/10 of the way from straight to a lock.
     */
    private double steerAnim;
    private static final double STEER_ANIM_STEP = 2.0 / 20.0;
    private int enginePitch;
    private int engineVolume;
    private int engineWhine;
    private int engineDistortion = 0x3C;
    private int engineVolumeOut = 0x82;

    private final List<KartEvent> events = new ArrayList<>();
    /** The other karts near this one, for the next tick's bump (the host sets them; see {@link KartBump}). */
    private List<KartBump.Other> others = List.of();
    private int bumpSoundCooldown;
    private final List<Bump> bumps = new ArrayList<>();

    /** A contact this tick: with the other body {@code id} (see {@link KartBump.Other}), how hard, and the normal (other to this kart). */
    public record Bump(int id, int impact, V3 normal) {}

    /** A kart standing at {@code position} (lu), facing {@code heading} (4096ths; forward is (sin h, 0, cos h)). */
    public KartSim(KartClass kartClass, V3 position, int heading) {
        this.kartClass = kartClass;
        this.pos = position;
        heading = wrap(heading);
        this.velYaw = heading;
        this.angle = heading;
        this.moving = rotAxisAngle(V3.UP, heading);
        this.facing = moving;
        this.groundHeight = position.y;
    }

    // ---- read access ----

    public KartClass kartClass() { return kartClass; }
    public void setKartClass(KartClass c) { this.kartClass = c; }
    public V3 position() { return pos; }

    /** The other karts (position lu, velocity lu/256 per frame) the next tick may bump into. */
    public void setOthers(List<KartBump.Other> others) { this.others = others; }
    public V3 velocity() { return vel; }
    /** Physics heading, 4096ths of a turn. */
    public int heading() { return angle; }
    /** The drawn yaw offset of the model relative to the heading (drift angle, spin). */
    public int turnAngle() { return turnAngle; }
    public int drawnYaw() { return wrap(angle + turnAngle); }
    public Mat3 facing() { return facing; }
    public int speed() { return speed; }
    public int forwardSpeed() { return speedApprox; }
    public int reserves() { return reserves; }
    public boolean grounded() { return (flags & TOUCH_GROUND) != 0; }
    public boolean againstWall() { return wallRub == WALL_RUB_MS; }
    public int simpTurn() { return simpTurn; }
    public int failedBoost() { return failedBoost; }
    public int boostsSuccess() { return boostsSuccess; }
    public int jumpMeter() { return jumpMeter; }

    /**
     * The turbo flame's size while it shows, else 0: clamp(5 + (fireLevel >> 6), 4, 8), kept from the boost that last raised the cap
     * (VehFire.c:322-330). Only the size varies: CTR draws the same 8-frame flame model for every boost, there is no colour choice.
     */
    public int fireSize() {
        if (!flameOn || flameHide > 0 || flameAlpha > FLAME_GONE) return 0;
        return Math.max(4, Math.min(8, flameSize));
    }

    /** How far the flame has faded, 0 (full) to 7 (almost gone). */
    public int flameFade() {
        return Math.min(7, flameAlpha >> 9);
    }

    /** Burning past the sacred fire cap (4096): only a super turbo pad gives that in CTR (a cheat), and we have none yet. */
    public boolean ultraFire() {
        return flameOn && fireSpeedCap > SACRED_FIRE_SPEED;
    }

    /** Tyre marks / smoke this tick: bit 0 the back tyres, bit 1 the front, on the ground only (VehEmitter.c:1413-1456). */
    public int skids() {
        if (!grounded()) return 0;
        boolean all = state == State.DRIFTING;
        int out = 0;
        if (all || (flags & BACK_SKID) != 0) out |= 1;
        if (all || (flags & FRONT_SKID) != 0) out |= 2;
        return out;
    }

    /** The steering frame as a number for the wire: 0 when unset, else 1 (full left) to 29 (full right), 15 straight. */
    public int steerCode() {
        return 15 + (int) Math.round(steerAnim * 14.0);
    }

    /** The shown steering of the driver, -1 left to +1 right. */
    public double steerAnim() {
        return steerAnim;
    }

    private void steerTick() {
        double target = 0.0;
        if (state == State.NORMAL || state == State.DRIFTING) {
            target = Math.max(-1.0, Math.min(1.0, -(double) simpTurn / Math.max(1, kartClass.turnRate)));
        }
        if (steerAnim < target) steerAnim = Math.min(steerAnim + STEER_ANIM_STEP, target);
        else if (steerAnim > target) steerAnim = Math.max(steerAnim - STEER_ANIM_STEP, target);
    }

    /** The airborne pose's frame as one number for the wire (see {@link AirTilt}): 0 none, 1 to 4 the pose. */
    public int airTiltCode() {
        return airTiltOn ? 1 + airTiltFrame : 0;
    }

    /**
     * VehFrameProc_Driving, the airborne branch (VehFrame.c:100-186): a kart that is more than 128 level units above the ground and is rising faster than 0x600 (or already plays
     * it) strikes the jump pose, one frame a frame up to the last; when that ends the frame falls back two a frame to the first. Only while driving: a spinning, crashing or
     * blasted kart has its own animation.
     */
    private void airTiltTick() {
        boolean driving = (state == State.NORMAL || state == State.DRIFTING) && tumbleMs <= 0;
        boolean want = driving && (vel.y > AirTilt.JUMP_AIR_THRESHOLD || airTiltOn) && pos.y - groundHeight > AirTilt.AIR_GAP;
        if (want) {
            if (!airTiltOn) {
                airTiltOn = true;
                airTiltFrame = 0;
            } else {
                airTiltFrame = Math.min(airTiltFrame + 1, AirTilt.FRAMES - 1);
            }
        } else if (airTiltOn) {
            airTiltFrame = Math.max(airTiltFrame - 2, 0);
            if (airTiltFrame == 0) airTiltOn = false;
        }
    }

    /** The boost wheelie's frame as one number for the wire (see {@link Wheelie}): 0 none, 1-11 rise, 12 hold, 13-21 down. */
    public int wheelieCode() {
        return switch (wheelieKind) {
            case WHEELIE_START -> 1 + Math.min(wheelieIndex, Wheelie.START_FRAMES - 1);
            case WHEELIE_HOLD -> Wheelie.START_FRAMES + 1;
            case WHEELIE_RECOVER -> Wheelie.START_FRAMES + 2 + Math.min(wheelieIndex, Wheelie.RECOVER_FRAMES - 1);
            default -> 0;
        };
    }

    /** The engine sound this frame as CTR mixes it: {distortion 0-255 (rate 2^((d-128)/64)), volume 0-255}. */
    public int[] engineSound() {
        return new int[] { engineDistortion, engineVolumeOut };
    }

    /** The hang-time meter (CTR-ms of airtime, up to 2400) while a hop's airtime counts for it, else -1. */
    /** The speedometer needle's value (a smoothed forward speed, as the game's HUD uses it). */
    public int speedometerNeedle() { return needle; }

    /** The dial's needle angle in 4096ths of a turn, see {@link Speedometer#angle}. */
    public int speedometerAngle() { return Speedometer.angle(needle, accelSpeed(), SACRED_FIRE_SPEED); }

    /** The hang-time meter as the HUD draws it: the airtime so far, held for 0x5A0 ms after landing, then 0 (up to 0x960). */
    public int jumpMeterShown() { return jumpShown; }

    /** The powerslide meter's room left in CTR-ms (turbo_MeterRoomLeft, 0 when no slide), its maximum and the point under which it is in the red zone. */
    public int slideMeterRoomLeft() { return state == State.DRIFTING ? turboMeter : 0; }
    /**
     * What the exhaust pipes do (VehEmitter_Main, VehEmitter_Exhaust), while driving: 0 no smoke, 1 the BRIGHT, additive puff (the emitter's default draw mode), 2 the DARK, subtractive
     * one. The meter's room left (turbo_MeterRoomLeft) is out of its window when it is under 129 (and it is 0 when not sliding) or over (low-room warning + 2) * 32; out of the window the
     * puff is bright and, if the turbo flame is on and no failed boost's smoke is pending, there is no smoke at all; inside it (a powerslide whose meter is in the last part, where a press
     * boosts) the puff is dark.
     */
    public int exhaustMode() {
        int room = slideMeterRoomLeft();
        boolean out = room < 129 || TURBO_LOW_ROOM_MS + 2 * 32 < room;
        if (failedBoost == 0 && out && fireSize() > 0) return 0;
        return out ? 1 : 2;
    }

    public static int slideMeterMaxRoom() { return TURBO_MAX_ROOM_MS; }
    public static int slideMeterLowRoom() { return TURBO_LOW_ROOM_MS; }

    public int hangTimeMeter() {
        return (flags & AIRBORNE) != 0 && !grounded() ? jumpMeter : -1;
    }

    /** Top speed of this class plus a sacred-fire boost: the scale a speed readout fills against. */
    public int speedScale() {
        return kartClass.speed + SACRED_FIRE_SPEED;
    }

    /** Powerslide meter: 0..1 fill, and whether it is in the red zone; null when idle. */
    public double[] turboMeter() {
        if (state != State.DRIFTING || turboMeter == 0) return null;
        double fill = 1.0 - (double) turboMeter / TURBO_MAX_ROOM_MS;
        return new double[] { fill, turboMeter < TURBO_LOW_ROOM_MS ? 1 : 0 };
    }

    /** The bodies bumped since the last call (for the host's own consequences, such as damage); clears them. */
    public List<Bump> drainBumps() {
        List<Bump> out = new ArrayList<>(bumps);
        bumps.clear();
        return out;
    }

    /** Events since the last call; clears them. */
    public List<KartEvent> drainEvents() {
        List<KartEvent> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    /** Teleport to a place and stand still (new ride, out of bounds). */
    public void place(V3 position, int heading) {
        pos = position;
        heading = wrap(heading);
        angle = heading;
        velYaw = heading;
        velPitch = 0;
        vel = V3.ZERO;
        speed = 0;
        speedApprox = 0;
        turnAngle = 0;
        turnAnglePrev = 0;
        state = State.NORMAL;
        reserves = 0;
        n1 = V3.UP;
        n2 = V3.UP;
        moving = rotAxisAngle(V3.UP, heading);
        facing = moving;
    }

    /** Called by the host while the kart is on a turbo pad this tick (VehFire_Increment FREEZE type). */
    public void turboPad() {
        if ((flags & ACCEL_PREVENTION) != 0) return;
        onPad = true;
        if (fireIncrement(TURBO_PAD_RESERVES, FireKind.FREEZE, 256) && !padPrev) {
            events.add(KartEvent.of(KartEvent.Type.BOOST));
        }
    }

    private boolean padPrev;

    /** AccelSpeed_ClassStat: Speed_ClassStat + 1500 in every class (reference/wok/ctr-physics-spec.md, stats.rs). */
    public int accelSpeed() {
        return kartClass.speed + 1500;
    }

    /**
     * The start boost at GO (VehStuckProc_RevEngine_Update): VehFire_Increment(960 reserves, STACK type, fire level 0x80 for the big one,
     * 0x20 for the small one), see {@link RevEngine}.
     */
    public void startBoost(RevEngine.StartBoost boost) {
        int fire = boost == RevEngine.StartBoost.BIG ? RevEngine.BIG_BOOST_FIRE : RevEngine.SMALL_BOOST_FIRE;
        if (fireIncrement(RevEngine.START_BOOST_RESERVES, FireKind.STACK, fire)) {
            events.add(new KartEvent(KartEvent.Type.BOOST, 2));
        }
    }

    /**
     * The Turbo power-up (VehPickupItem_ShootNow): {@code VehFire_Increment(0x960 reserves, FREEZE type, fire level 0x80)}, 0x100 with ten
     * wumpa (TURBO_ITEM_RESERVES, TURBO_ITEM_BOOST_NORMAL / _JUICED).
     */
    public void itemTurbo(boolean juiced) {
        if (fireIncrement(0x960, FireKind.FREEZE, juiced ? 0x100 : 0x80)) {
            events.add(new KartEvent(KartEvent.Type.BOOST, 2));
        }
    }

    /** The red beaker's rain cloud over the kart (RAIN_CLOUD_EFFECT_*, 7.68 s): only what the sim has a place for is applied (jump lockout, ice). */
    public static final int CLOUD_SLOW = 0;
    public static final int CLOUD_ITEM_ROLL = 1;
    public static final int CLOUD_ICE = 2;
    public static final int CLOUD_JUMP_LOCKOUT = 3;
    private int cloudMs;
    private int cloudEffect = -1;

    public void setCloud(int effect, int ms) {
        cloudEffect = effect;
        cloudMs = ms;
    }

    public boolean cloudActive() {
        return cloudMs > 0;
    }

    public boolean cloudIce() {
        return cloudMs > 0 && cloudEffect == CLOUD_ICE;
    }

    /** A beaker hit the kart (damage type 1, VEH_PICK_DAMAGE_SPIN): it spins out. */
    public void itemSpin() {
        spinOut(SPIN_MS);
    }

    /** How long the kart still tumbles, ms: VEH_PICK_BLAST_NO_INPUT_TIMER is 0x960. */
    private int tumbleMs;
    /** The tumble's bounces are not hops: no hang time meter and no boost from them until the kart is back on the ground for good (VehFire_Increment refuses a blasted kart). */
    private boolean tumbleGuard;

    /** For the renderer: 0 when not tumbling, else 64 | the frame (0 to 26) of the blasted animation: VehStuckProc_Tumble_Animate, (time left / 32 ms) mod 27. */
    public int tumbleCode() {
        return tumbleMs > 0 ? 64 | ((tumbleMs >> 5) % 27) : 0;
    }

    /**
     * An explosion of a power-up (TNT, Nitro) hit the kart: VehStuckProc_Tumble. For 2.4 s it has no input, no speed and no boost, and jumps again and again, each bounce
     * lower than the one before (the jump velocity is twice the time left plus 6000), while it whirls about its own axis. (CTR plays a baked tumbling animation of the
     * kart's model; the whirl is the nearest the model here has.)
     */
    public void itemBlast() {
        tumbleGuard = true;
        tumbleMs = 0x960;
        spinOut(0x960);
    }

    // ---- tick ----

    /** One 30 Hz frame. */
    public void tick(Collider world, KartInput input) {
        boolean tappedR1 = input.r1() && !prevR1;
        boolean tappedL1 = input.l1() && !prevL1;
        if (cloudMs > 0) {
            cloudMs = Math.max(0, cloudMs - MS);
            if (cloudEffect == CLOUD_JUMP_LOCKOUT) {      // RAIN_CLOUD_EFFECT_JUMP_LOCKOUT: the jump button does nothing
                tappedR1 = false;
                tappedL1 = false;
            }
        }
        prevR1 = input.r1();
        prevL1 = input.l1();
        boolean tapped = tappedR1 || tappedL1;

        updateStage(input, tapped);
        physLinear(input, tappedR1, tappedL1);
        if (tumbleMs > 0) {
            tumbleMs = Math.max(0, tumbleMs - MS);
            flags |= ACCEL_PREVENTION;            // ... and the brake friction applies (ACTION_ACCEL_PREVENTION)
            baseSpeed = 0;                        // (driver->speed stays: it carries the bounce between frames; friction takes the rest)
            fireSpeed = 0;
            jumpForced = JUMP_FORCED_MS;          // VehStuckProc_Tumble_PhysLinear: jump_ForcedMS 0x60 every frame ...
            jumpVel = tumbleMs * 2 + 6000;         // ... with this initial velocity
        }
        switch (state) {
            case DRIFTING -> powerslideAngular();
            case SPINNING -> spinAngular();
            default -> physAngular();
        }
        applyForces();
        n4 = n1;
        n1 = V3.UP;
        sweep(world);
        vel = vel.sub(nudge);
        bump();
        groundProbe(world);
        jumpAndFriction();
        needle = Speedometer.step(needle, speedApprox, frameCounter++);
        // TRANSLATE_MATRIX: the visual frame.
        double w = grounded() ? 6.0 : 7.0;
        V3 target = grounded() ? n1 : V3.UP;
        n2 = n2.mul(w).add(target.mul(8.0 - w)).div(8.0).normalizeOr(V3.UP);
        facing = rotAxisAngle(n2, wrap(angle + turnAngle));
        hangTime();
        engineSoundTick();
        // ANIMATE: SpinStop hands back to driving once its animation is home.
        if (state == State.SPINNING && spinPhase == SpinPhase.STOP && turnAngle == 0) {
            stateTimer -= 1;
        }
        wheelieTick();
        airTiltTick();
        steerTick();
        flameTick();
        padPrev = onPad;
        onPad = false;
    }

    /**
     * The boost wheelie's frame (VehPhysForce_TranslateMatrix_UpdateMatrixAnimation, VehPhysForce.c:1234-1340): while reserves
     * burn at the class's top speed or over and no latch holds it, the nose rises over START and stays at HOLD; then it comes
     * down over RECOVER. A rise cut short comes down from as high as it got.
     */
    private void wheelieTick() {
        boolean on = reserves != 0 && fireSpeed >= kartClass.speed && (flags & TURBO_LATCH) == 0;
        switch (wheelieKind) {
            case WHEELIE_NONE -> {
                if (on) {
                    wheelieKind = WHEELIE_START;
                    wheelieIndex = 0;
                }
            }
            case WHEELIE_START -> {
                if (on) {
                    if (wheelieIndex + 1 >= Wheelie.START_FRAMES) {
                        wheelieKind = WHEELIE_HOLD;
                        wheelieIndex = 0;
                    } else {
                        wheelieIndex++;
                    }
                } else {
                    wheelieIndex = Wheelie.remap(wheelieIndex, Wheelie.START_FRAMES, Wheelie.RECOVER_FRAMES);
                    wheelieKind = WHEELIE_RECOVER;
                }
            }
            case WHEELIE_HOLD -> {
                if (!on) {
                    wheelieKind = WHEELIE_RECOVER;
                    wheelieIndex = 0;
                }
            }
            case WHEELIE_RECOVER -> {
                if (on) {
                    wheelieIndex = Wheelie.remap(wheelieIndex, Wheelie.RECOVER_FRAMES, Wheelie.START_FRAMES);
                    wheelieKind = WHEELIE_START;
                } else if (wheelieIndex + 1 >= Wheelie.RECOVER_FRAMES) {
                    wheelieKind = WHEELIE_NONE;
                    wheelieIndex = 0;
                } else {
                    wheelieIndex++;
                }
            }
            default -> { }
        }
    }

    /**
     * EngineSound_Player (HOWL_Engine.c), the player's steady-state branch: the pitch chases the mean of the throttle's target
     * and the speed (the target plus 0xF00 in the air and in a powerslide, so the engine revs), the volume swells while the revs
     * change and settles when they hold, and through a powerslide the pitch climbs with the turbo meter.
     */
    private void engineSoundTick() {
        int target = Math.abs(fireSpeed);
        boolean drifting = state == State.DRIFTING;
        if ((flagsPrev & TOUCH_GROUND) == 0 || drifting) {
            target += 0xF00;
        } else {
            target = (target + Math.abs(speedApprox)) >> 1;
        }
        if (Math.abs(target - enginePitch) < 0x601) {
            engineVolume -= 500;
            engineVolume = Math.max(engineVolume, drifting ? 2000 : 0);
        } else {
            engineVolume = Math.min(engineVolume + 2000, 14000);
        }
        enginePitch = (target * 0x89 + enginePitch * 0x177) >> 9;
        int accelSpeed = kartClass.speed + 1500;
        int volume = map(engineVolume, 0, accelSpeed, 0x82, 0xE6);
        int pitch = map(enginePitch, 0, accelSpeed + SACRED_FIRE_SPEED + 0xF00, 0x3C, 200);
        if (drifting) {
            if (turboMeter == 0) engineWhine = Math.max(engineWhine - 1, 0);
            else engineWhine = ((TURBO_MAX_ROOM_MS >> 5) >> 1) - (turboMeter >> 6);
        }
        engineDistortion = Math.max(0, Math.min(0xFF, pitch + engineWhine));
        engineVolumeOut = Math.max(0, Math.min(0xFF, volume));
    }

    // ---- UPDATE stage (VehPhysProc_*_Update) ----

    private void updateStage(KartInput input, boolean tapped) {
        switch (state) {
            case NORMAL -> {
                boolean held = driftButtonR1 ? input.r1() : input.l1();
                boolean start = (flags & STARTED_TOUCH_GROUND) != 0;
                if (start && Math.abs(simpTurn) > kartClass.turnRate / 2 && held
                        && (flags & ACCEL_PREVENTION) == 0 && speedApprox >= kartClass.speed / 2) {
                    powerslideInit();
                }
            }
            case DRIFTING -> powerslideUpdate(input, tapped);
            case CRASHING -> {
                if (stateTimer <= 0) {
                    state = State.NORMAL;
                    turboMeter = 0;
                }
            }
            case SPINNING -> {
                switch (spinPhase) {
                    case FIRST -> {
                        if (noInput == 0 || Math.abs(speedApprox) <= SPIN_SLOW_SPEED) spinPhase = SpinPhase.LAST;
                    }
                    case LAST -> {
                        if (Math.abs(turnAngle) < SPIN_STOP_ANGLE) {
                            spinPhase = SpinPhase.STOP;
                            stateTimer = SPIN_STOP_ANIM_FRAMES;
                        }
                    }
                    case STOP -> {
                        if (stateTimer <= 0) {
                            state = State.NORMAL;
                            turboMeter = 0;
                        }
                    }
                }
            }
        }
    }

    /** VehPhysProc_PowerSlide_Init (spec 5.1). */
    private void powerslideInit() {
        state = State.DRIFTING;
        int base = kartClass.driftTurnBase << 8;
        multDrift = simpTurn < 0 ? -base : base;
        driftFrames = 0;
        driftTotalMs = 0;
        driftBoostMs = 0;
        boostsAttempted = 0;
        boostsSuccess = 0;
        spinRate = 0;
        turnAngleVel = 0;
        driftAssistMs = 0;
        turboMeter = TURBO_MAX_ROOM_MS;
        events.add(new KartEvent(KartEvent.Type.DRIFT_START, multDrift > 0 ? 1 : 0));
    }

    /** VehPhysProc_PowerSlide_Update (spec 5.3, 5.4): the turbo meter, boosts, spin-out and exit. */
    private void powerslideUpdate(KartInput input, boolean tapped) {
        if (tapped) {
            driftFrames = 0;
            int m = turboMeter;
            if (m != 0) {
                if (m < TURBO_LOW_ROOM_MS) {
                    int gain = map(m, 0, TURBO_LOW_ROOM_MS, TURBO_FULL_BAR_GAIN_MS, 0);
                    int fire = boostsSuccess << 6;
                    fireIncrement(gain, FireKind.STACK, fire);
                    boostsSuccess++;
                    // The first two give no wheelie (VehPhysProc.c:2496-2499).
                    if (boostsSuccess < 3) flags |= TURBO_LATCH;
                    driftBoostMs = DRIFT_BOOST_MS;
                    events.add(new KartEvent(KartEvent.Type.BOOST, boostsSuccess));
                } else {
                    failedBoost = 8;
                    events.add(KartEvent.of(KartEvent.Type.BOOST_EARLY));
                }
                turboMeter = 0;
                boostsAttempted++;
            }
        } else if (turboMeter != 0) {
            int before = turboMeter;
            turboMeter = Math.max(turboMeter - MS, 0);
            if (before >= TURBO_LOW_ROOM_MS && turboMeter < TURBO_LOW_ROOM_MS) {
                events.add(KartEvent.of(KartEvent.Type.METER_RED));
            }
            if (turboMeter == 0) {
                boostsAttempted += 3;
                events.add(KartEvent.of(KartEvent.Type.METER_FULL));
            }
        } else if (boostsAttempted < 3) {
            turboMeter = TURBO_MAX_ROOM_MS;
        }

        boolean held = driftButtonR1 ? input.r1() : input.l1();
        int lim = DRIFT_FRAMES_TILL_SPINOUT;
        if (driftFrames > lim) {
            spinOut(SPIN_MS);
        } else if (speedApprox < 0 && !onPad) {
            spinOut(REVERSE_SPIN_MS);
        } else if (driftFrames < -lim || speed < kartClass.speed / 2
                || (flags & (DRIVING_AGAINST_WALL | ACCEL_PREVENTION | BRAKE_WITH_ACCEL)) != 0 || !held) {
            prevMultDrift = multDrift;
            driftAssistMs = DRIFT_RELEASE_ASSIST_MS;
            endDrift();
        }
    }

    private void endDrift() {
        state = State.NORMAL;
        turboMeter = 0;
        events.add(KartEvent.of(KartEvent.Type.DRIFT_END));
    }

    private void spinOut(int ms) {
        noInput = ms;
        spinInit();
    }

    /** VehPhysProc_SpinFirst_Init (VehPhysProc.c:2859-2909): whirl the way the kart was turning. */
    private void spinInit() {
        if (state == State.DRIFTING) events.add(KartEvent.of(KartEvent.Type.DRIFT_END));
        state = State.SPINNING;
        spinPhase = SpinPhase.FIRST;
        turnAngleVel = 0;
        turboMeter = 0;
        spinTurn = ampTurn < 0 ? -SPIN_RATE : SPIN_RATE;
        events.add(KartEvent.of(KartEvent.Type.SPIN_OUT));
    }

    // ---- PHYS_LINEAR (VehPhysProc_Driving_PhysLinear, spec 3.1, 3.2, 4.1) ----

    private static int tickDown(int t) {
        return Math.max(t - MS, 0);
    }

    private void physLinear(KartInput input, boolean tappedR1, boolean tappedL1) {
        flagsPrev = flags;
        turnAnglePrev = turnAngle;
        reserves = tickDown(reserves);
        turboOutside = tickDown(turboOutside);
        wallRub = tickDown(wallRub);
        accelTapWindow = tickDown(accelTapWindow);
        jumpForced = tickDown(jumpForced);
        jumpCooldown = tickDown(jumpCooldown);
        jumpBuffer = Math.max(jumpBuffer - 1, 0);
        failedBoost = Math.max(failedBoost - 1, 0);
        if (state == State.CRASHING) stateTimer -= MS;
        if (state == State.SPINNING && spinPhase == SpinPhase.FIRST) noInput = tickDown(noInput);
        flags &= CARRY;

        // The jump button (spec 5.1): a tap buffers a hop and names the drift's button.
        boolean tap = tappedR1 || tappedL1;
        if (tap && state != State.DRIFTING) {
            driftButtonR1 = tappedR1;
            jumpBuffer = JUMP_BUFFER_FRAMES;
        }
        if (input.r1() || input.l1()) flags |= JUMP_BUTTON_HELD;

        // Square clears the reserves only off a turbo pad (VehPhysProc.c:953-963).
        if (input.brake() && !onPad) reserves = 0;
        boolean brake = input.brake() && !(onPad && reserves != 0);
        boolean cross = input.gas() || reserves != 0;
        int b = targetSpeed();
        int gas;
        int f;
        if (!brake) {
            if (cross) { gas = b; f = 0; }
            else if (!input.down()) { gas = 0; f = 0; }
            else { gas = -BACKWARD_SPEED; f = REVERSING; }
        } else if (input.down()) {
            gas = -(3 * BACKWARD_SPEED) >> 2;
            f = REVERSING | BRAKE_WITH_ACCEL;
        } else if (cross) {
            gas = b >> 1;
            f = BRAKE_WITH_ACCEL;
        } else {
            gas = 0;
            f = ACCEL_PREVENTION;
        }
        flags = (flags & ~REVERSING) | f;
        // A power brake at speed leaves the back tyres' marks (VehPhysGeneral.c:435-450).
        if ((flags & BRAKE_WITH_ACCEL) != 0 && speedApprox > 0x300) flags |= BACK_SKID;
        // The gas mashed (VehPhysProc.c:1328-1357).
        if (accelTapWindow == 0 || state != State.NORMAL) accelTapCount = 0;
        if ((fireSpeed > 0) != (gas > 0)) {
            if (accelTapWindow != 0) accelTapCount++;
            accelTapWindow = ACCEL_TAP_WINDOW_MS;
        }
        fireSpeed = gas;
        // terrainMeta2's multiplier (VehPhysProc.c:1362-1388).
        int m = speedTerrain.speed();
        boolean scaled = (flags & BRAKE_WITH_ACCEL) == 0 && m != 256;
        if (scaled) {
            baseSpeed = (gas * m) >> 8;
            terrainBase = (b * m) >> 8;
        } else {
            baseSpeed = gas;
            terrainBase = b;
        }
        boolean lockout = state == State.CRASHING || state == State.SPINNING;
        if (lockout) {
            baseSpeed = 0;
            fireSpeed = 0;
            if (!(state == State.SPINNING && spinPhase == SpinPhase.STOP)) flags |= ACCEL_PREVENTION;
        }

        // The steer command (spec 4.1); + is left.
        int strength = kartClass.turnRate;
        if (accelTapCount >= ACCEL_TAP_STEER_COUNT && speedApprox < ACCEL_TAP_SPEED_MAX) {
            strength = ACCEL_TAP_STRENGTH;
        } else if (wallRub != 0) {
            strength = 48;
        } else if ((flags & (ACCEL_PREVENTION | BRAKE_WITH_ACCEL)) != 0 && !lockout) {
            strength = input.gas() ? map(Math.abs(speed), 0x300, kartClass.speed / 2, 64, strength) : 64;
        }
        int distance = (int) Math.round(Math.min(Math.abs(input.steer()), 1.0) * 127.0);
        int s = stickStrength(distance, strength);
        int simp = input.steer() < 0 ? s : -s;
        if (simp == 0) {
            framesSteering = 10000;
        } else if (simpTurn == 0 || Integer.signum(simp) != Integer.signum(simpTurn)) {
            framesSteering = 0;
            if (simp > 0) flags |= STEER_LEFT;
            else flags &= ~STEER_LEFT;
        }
        simpTurn = simp;
    }

    /** VehPhysJoystick_GetStrength over a stick's distance from centre (0..127): dead zone 48, two-segment curve. */
    private static int stickStrength(int distance, int max) {
        final int dead = 0x30;
        final int range = 0x7f;
        final int dist = 0x5e;
        if (distance < dead) return 0;
        if (distance >= range) return max;
        int v = distance - dead;
        int half = dist / 2;
        int fifth = max / 5;
        if (v < half) return v * fifth * 2 / dist;
        return (v - half) * (max - fifth) * 2 / dist + fifth;
    }

    /** VehPhysGeneral_GetBaseSpeed (VehPhysGeneral.c:1979-2116, spec 3.2): the top speed this frame. */
    private int targetSpeed() {
        long w = Math.min(wumpa, 9);
        int bonus = (int) ((w * WUMPA_NET_STAT / 10) >> 12);
        int b = kartClass.speed;
        if (reserves != 0) {
            b += fireSpeedCap;
            int cap = SINGLE_TURBO_SPEED + 2 * (SACRED_FIRE_SPEED - SINGLE_TURBO_SPEED) - fireSpeedCap;
            bonus = Math.min(bonus, Math.max(cap, 0));
        }
        return Math.min(b + bonus, MAX_BASE_SPEED);
    }

    // ---- PHYS_ANGULAR (VehPhysGeneral_PhysAngular, spec 4.2-4.6) ----

    private static int steerAccel(int frames) {
        int s2 = STEER_ACCEL_STAGE2_FIRST;
        int s3 = s2 + STEER_ACCEL_STAGE2_LEN;
        int s4 = STEER_ACCEL_STAGE4_FIRST;
        if (frames < s2) return map(frames, 0, s2, 0, STEER_ACCEL_MAX);
        if (frames < s3) return STEER_ACCEL_MAX;
        if (frames < s4) return map(frames, s3, s4, STEER_ACCEL_MAX, 0);
        return 0;
    }

    private void physAngular() {
        boolean grounded = grounded();
        int target = simpTurn << 8;
        if (forwardDir < 0) {
            target = -target;
            flags ^= STEER_LEFT;
        }
        if (grounded) target = map(Math.abs(speedApprox), 16, 0x300, 0, target);
        int step = (kartClass.turnInputDelay * terrain.turnResponse()) >> 8;
        spinRate = approach(spinRate, step, target);
        int rot = spinRate;
        // 4.3: the drift's residue.
        if (driftAssistMs != 0) {
            rot += map(driftAssistMs, 0, 320, 0, prevMultDrift);
            driftAssistMs = Math.max(driftAssistMs - MS, 0);
        }
        visualTurn(rot);
        // 4.4: a fresh steer turns at about half rate at first.
        if (Math.abs(speedApprox) >= 0x300 && grounded) {
            int sa = steerAccel(framesSteering);
            framesSteering = Math.min(framesSteering + 1, 10000);
            sa = Math.min(sa, (STEER_ACCEL_TURN_VEL_SCALE * Math.abs(rot)) >> 8);
            if ((flags & STEER_LEFT) != 0) sa = -sa;
            rot = rot > 0 ? Math.max(rot + sa, 0) : Math.min(rot + sa, 0);
        }
        // 4.5: yaw, and the velocity's heading turned with it (the grip).
        ampTurn = rot;
        angle = wrap(angle + ((rot * MS) >> 13));
        int lean = ((flags & ACCEL_PREVENTION) != 0 || accelTapCount >= ACCEL_TAP_STEER_COUNT)
                ? (rot * 10) >> 8
                : (rot * terrain.lean()) >> 8;
        velYaw = wrap(velYaw + ((lean * MS) >> 13));
        moving = rotAxisAngle(n1, angle);
        counterSteer();
    }

    /** 4.6: the model's yaw offset, which leans into turns and feeds the counter-steer nudge. */
    private void visualTurn(int rot) {
        int classSpeed = kartClass.speed;
        int resistMin = (TURN_RESIST_MIN * classSpeed) >> 8;
        int resistMax = (TURN_RESIST_MAX * classSpeed) >> 8;
        int modelRotVel = MODEL_ROT_VEL_MAX;
        if ((flags & BRAKE_WITH_ACCEL) != 0) {
            resistMin >>= 1;
            resistMax >>= 1;
            modelRotVel = map(Math.abs(speed), 0x300, classSpeed / 2, MODEL_ROT_VEL_MIN, MODEL_ROT_VEL_MAX);
        }
        int full = kartClass.turnRate << 8;
        int resistance = map(Math.abs(speed), resistMin, resistMax, full, 0);
        int lerpTarget = 0;
        if (Math.abs(speedApprox) >= resistMin && Math.abs(rot) > resistance) {
            int lt = map(Math.abs(fireSpeed), resistMin, resistMax, 0, modelRotVel);
            lerpTarget = Integer.signum(rot) * map(Math.abs(rot), resistance, full, 0, lt);
        }
        turnAngleVel = lerpToForwards(lerpTarget);
        turnAngleVel = (turnAngleVel * terrain.turnAngle()) >> 8;
        turnAngle += turnAngleVel;
        // The low-speed transfer: the heading takes what the model recentres by.
        int low = map(Math.abs(speedApprox), 0, 0x600, turnAngleVel, 0);
        if (Math.abs(low) > 1) angle = wrap(angle - low);
    }

    private int lerpToForwards(int target) {
        int s = target < 0 ? -1 : 1;
        int cur = turnAngle * s;
        int vel = turnAngleVel * s;
        int tgt = target * s;
        int desired;
        if (wallRub == WALL_RUB_MS) {
            desired = 0;
        } else if (tgt < cur) {
            int cap = cur > 350 ? 20 * 15 : 20;
            desired = -Math.min(cap, Math.max((cur - tgt) >> 2, 1));
        } else if (cur < tgt) {
            int cap = cur < 0 ? 60 : 30;
            desired = Math.min(cap, Math.max((tgt - cur) >> 2, 1));
        } else {
            desired = 0;
        }
        return approach(vel, 10, desired) * s;
    }

    /** VehPhysForce_CounterSteer (VehPhysForce.c:2054-2135): a one-frame sideways nudge from the model's yaw change. */
    private void counterSteer() {
        nudge = V3.ZERO;
        int ratio = terrain.counterSteer();
        if (Math.abs(speedApprox) > 768 && state != State.CRASHING && wallRub == 0 && grounded() && ratio != 0) {
            int v = (-8000 * ratio) >> 8;
            int da = Math.max(-MODEL_TURN_COUNTER_STEER,
                    Math.min(MODEL_TURN_COUNTER_STEER, turnAngle - turnAnglePrev));
            nudge = moving.mul(new V3(v * Math.sin(rad(da)), 0, 0));
        }
    }

    /** VehPhysProc_PowerSlide_PhysAngular (VehPhysProc.c:1873-2464, spec 5.2). */
    private void powerslideAngular() {
        // (a) the velocity's heading and the kart's converge.
        int delta = diff(velYaw, angle);
        if (delta != 0) {
            int step = delta >> 3;
            if (step == 0) step = 1;
            step = Math.max(-MS * 2, Math.min(MS * 2, step));
            angle = wrap(angle + step);
            velYaw = wrap(velYaw - step);
        }
        // (c) the steering's share of the spin.
        int range = kartClass.turnRate << 8;
        int s = simpTurn << 8;
        int m = multDrift >= 0 ? 1 : -1;
        int into = s * m;
        int desired = into >= 0
                ? m * map(into, 0, range, 0, DRIFT_STANDARD << 8)
                : m * map(-into, 0, range, 0, DRIFT_SWITCH_WAY << 8);
        int d = desired * m;
        int c = spinRate * m;
        c = d < c ? Math.max(c - DRIFT_SPIN_RATE_DECEL, d) : Math.min(c + DRIFT_SPIN_RATE_ACCEL, d);
        int current = c * m;
        // (d) the spin-out counter.
        if (desired == 0) driftFrames = approach(driftFrames, 1, 0);
        else if (desired * m > 0) driftFrames = Math.max(driftFrames + 1, 0);
        else driftFrames = Math.min(driftFrames - 1, 0);
        // (e) the drift's own turn ramps in; (f) the spin may not reverse it.
        int dti = map(driftTotalMs, 0, DRIFT_TURN_RAMP_MS, (DRIFT_TURN_STARTUP_SCALE * multDrift) >> 8, multDrift);
        current = dti >= 0 ? Math.max(current, -dti) : Math.min(current, -dti);
        spinRate = current;
        // (g) the drawn drift angle.
        int side = multDrift >= 0 ? 1 : -1;
        int extra = current * side >= 0
                ? side * map(Math.abs(current), 0, DRIFT_STANDARD << 8, 0, DRIFT_SAME_DIR_ANGLE)
                : side * DRIFT_OPPOSITE_DIR_ANGLE;
        int target = side * DRIFT_TURN_ANGLE_SCALE + extra;
        int step = (target - turnAngle) >> 3;
        if (step == 0 && target != turnAngle) step = Integer.signum(target - turnAngle);
        turnAngle += step;
        // (h) yaw.
        ampTurn = current + dti;
        angle = wrap(angle + ((ampTurn * MS) >> 13));
        // (i) a successful boost kicks the velocity into the drift.
        if (driftBoostMs > 0) {
            driftBoostMs -= MS;
            velYaw = wrap(velYaw + Integer.signum(turnAngle) * DRIFT_BOOST_AXIS_KICK);
        }
        driftTotalMs = Math.min(driftTotalMs + MS, DRIFT_TURN_RAMP_MS);
        moving = rotAxisAngle(n1, angle);
        counterSteer();
    }

    /** The spin-out's PHYS_ANGULAR (VehPhysProc.c:2805-2849, 2945-3012, 3031-3048). */
    private void spinAngular() {
        framesSteering = 10000;
        if (spinPhase != SpinPhase.STOP) {
            spinRate -= spinRate >> 3;
            ampTurn = spinRate;
        }
        switch (spinPhase) {
            case FIRST -> turnAngle = wrapSigned(turnAngle + spinTurn);
            case LAST -> {
                int a = turnAngle;
                if (a > 0) {
                    if (spinTurn < 0 && a < SPIN_RECENTER_ANGLE) {
                        spinTurn = Math.min(((-(a << 2)) >> 3), -SPIN_MIN_RECENTER_RATE);
                    }
                    turnAngle = wrapSigned(a + spinTurn);
                    if (spinTurn < 0 && turnAngle < 0) turnAngle = 0;
                } else if (a < 0) {
                    if (spinTurn > 0 && a > -SPIN_RECENTER_ANGLE) {
                        spinTurn = Math.max(((-(a << 2)) >> 3), SPIN_MIN_RECENTER_RATE);
                    }
                    turnAngle = wrapSigned(a + spinTurn);
                    if (spinTurn > 0 && turnAngle > 0) turnAngle = 0;
                }
            }
            case STOP -> turnAngle = approach(turnAngle, (MS << 7) >> 5, 0);
        }
        angle = wrap(angle + ((ampTurn * MS) >> 13));
        moving = rotAxisAngle(n1, angle);
        nudge = V3.ZERO;
    }

    // ---- APPLY_FORCES (VehPhysForce_OnApplyForces + OnGravity, spec 6.4, 3.6) ----

    private void applyForces() {
        speed = Math.min(speed, MAX_SPEED);
        double yaw = rad(velYaw);
        double pitch = rad(velPitch);
        double sp = speed;
        vel = new V3(sp * Math.cos(pitch) * Math.sin(yaw), sp * Math.sin(pitch), sp * Math.cos(pitch) * Math.cos(yaw));
        V3 l = moving.mulTransposed(vel);
        V3 l0 = l;
        V3 g = moving.mulTransposed(new V3(0, -GRAVITY, 0));
        double gx = g.x, gy = g.y, gz = g.z;
        if ((gz < 0 && fwdAccelImpulse > 0) || (gz > 0 && fwdAccelImpulse < 0)) gz = 0;
        boolean opposite = (baseSpeed > 0 && speedApprox < 0) || (baseSpeed < 0 && speedApprox > 0);
        if ((flags & ACCEL_PREVENTION) != 0 || opposite) {
            gx = 0;
            gz = 0;
        }
        double lx = l.x + gx, ly = l.y + gy, lz = l.z + gz;
        // Gravity may push only so far past these; it never takes back what was already there.
        double fire = fireSpeed;
        if (gz > 0) lz = Math.min(lz, Math.max(l0.z, fire + SLOPE_FORWARD_SPEED_BONUS));
        else if (gz < 0) lz = Math.max(lz, Math.min(l0.z, fire - 2560));
        double side = SIDE_SPEED_CLAMP;
        lx = Math.max(Math.min(l0.x, -side), Math.min(lx, Math.max(l0.x, side)));
        ly = Math.max(ly, Math.min(l0.y, -TERMINAL_VELOCITY));
        l = new V3(lx, ly, lz);
        boolean slowDrag = speedApprox > terrainBase && speedTerrain.speed() < 256;
        // VehPhysForce_OnApplyForces: a blasted kart is rubbed even in the air (the tumble's bounces would otherwise carry the kart's speed on)
        if ((flagsPrev & TOUCH_GROUND) != 0 || slowDrag || tumbleMs > 0) l = friction(l);
        if (Math.abs(l.z) > 64) forwardDir = l.z >= 0 ? 1 : -1;
        vel = moving.mul(l).add(nudge);
    }

    /** Ground friction (VehPhysForce.c:429-806, spec 3.6), in the kart's frame. */
    private V3 friction(V3 l) {
        boolean fast = Math.abs(speedApprox) > 0x300;
        boolean opposing = fast && ((baseSpeed > 0 && speedApprox < 0) || (baseSpeed < 0 && speedApprox > 0));
        // The back tyres skid under the brake, against their direction, or spinning at under half the target speed
        // (VehPhysForce.c:448-534).
        boolean wheelspin = baseSpeed != 0 && !opposing && state != State.DRIFTING && fast
                && Math.abs(speedApprox) < (Math.abs(baseSpeed) >> 1);
        if ((fast && ((flags & ACCEL_PREVENTION) != 0 || opposing)) || wheelspin) flags |= BACK_SKID;
        int perp;
        int fwd;
        if ((flags & ACCEL_PREVENTION) != 0) {
            // a blasted kart brakes with three quarters of the brake friction (KS_BLASTED), a spinning one with half
            int f = tumbleMs > 0 ? (BRAKE_FRICTION * 3) >> 2 : state == State.SPINNING ? BRAKE_FRICTION / 2 : BRAKE_FRICTION;
            perp = f;
            fwd = f;
        } else if (baseSpeed != 0) {
            if (opposing) {
                perp = PEDAL_FRICTION_PERP;
                fwd = BRAKE_FRICTION;
            } else if (state == State.DRIFTING) {
                perp = DRIFT_CURVE;
                fwd = DRIFT_FRICTION;
            } else {
                perp = PEDAL_FRICTION_PERP;
                fwd = PEDAL_FRICTION_FWD;
            }
        } else {
            perp = NO_PEDAL_FRICTION_PERP;
            fwd = NO_PEDAL_FRICTION_FWD;
        }
        int gfs = terrain.groundFriction();
        if (gfs != 256) {
            perp = (perp * gfs) >> 8;
            fwd = (fwd * gfs) >> 8;
        }
        if (gripTimer > 0) {
            perp += perp;
        } else if (gripTimer < 0) {
            // Just off ice: the first frame takes half the sideways speed, the rest double grip.
            perp = gripTimer == ICE_EXIT_MS ? (int) (Math.abs(l.x) / 2) : perp + perp;
            if (Math.abs(l.x) >= 1.0) flags |= BACK_SKID | FRONT_SKID;
            gripTimer = Math.min(gripTimer + MS, 0);
        }
        int tflags = terrain.flags();
        if ((tflags & Terrain.FLAG_MUD_PHYSICS) != 0) {
            perp = Math.max(perp, (int) Math.abs(l.x / 8));
            double z = l.z;
            double base = baseSpeed;
            double least;
            if (z != 0 && base != 0 && (z < 0) != (base < 0)) least = Math.abs(z / 2);
            else if ((z < base && z <= 0) || (base < z && z >= 0)) least = Math.abs(z - base) / 2;
            else least = 0;
            fwd = Math.max(fwd, (int) least);
        }
        if ((tflags & Terrain.FLAG_SIDESLIP_FRICTION) != 0) perp = Math.max((perp * 3) >> 2, fwd);
        return new V3(approach(l.x, perp, 0), l.y, approach(l.z, fwd, 0));
    }

    // ---- COLL_MOVED (COLL.c:2246-2662, spec 9.1, 9.2) ----

    private void sweep(Collider world) {
        flags &= ~TOUCH_GROUND;
        double r = SPHERE_RADIUS;
        V3 lift = facing.y.mul(r);
        V3 centre = pos.add(lift);
        V3 delta = vel.div(256.0);
        int steps = (int) Math.max(1, Math.min(32, Math.ceil(delta.length() / (r * 0.4))));
        V3 step = delta.div(steps);
        boolean slammed = false;
        outer:
        for (int i = 0; i < steps; i++) {
            centre = centre.add(step);
            for (int k = 0; k < 4; k++) {
                Collider.Contact contact = world.deepest(centre, r);
                if (contact == null) break;
                centre = centre.add(contact.normal().mul(contact.depth() + 0.01));
                boolean ground = contact.normal().y >= Collider.GROUND_NORMAL_Y;
                double dot = vel.dot(contact.normal());
                if (dot < -2560.0) flags |= TURBO_LATCH; // a hard impact drops the wheelie
                if (ground) {
                    n1 = contact.normal();
                    flags |= TOUCH_GROUND;
                    if (dot < 0) vel = vel.sub(contact.normal().mul(dot));
                    continue;
                }
                if (dot >= 0) continue;
                flags |= DRIVING_AGAINST_WALL;
                reserves = 0;
                turboOutside = 0;
                if (wallRub == 0) events.add(KartEvent.of(KartEvent.Type.WALL_RUB));
                wallRub = WALL_RUB_MS;
                double horizontal = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                double headOn = -dot / Math.max(vel.length(), 1.0);
                if (dot < -5119.0 && horizontal > 5120.0 && headOn > 0.879) {
                    slammed = true;
                    break outer;
                }
                vel = vel.sub(contact.normal().mul(dot));
            }
        }
        pos = centre.sub(lift);
        if (slammed) {
            if (state == State.DRIFTING) {
                angle = wrap(angle + turnAngle);
                turnAngle = 0;
                events.add(KartEvent.of(KartEvent.Type.DRIFT_END));
            }
            events.add(new KartEvent(KartEvent.Type.SLAM, Math.abs(speed)));
            state = State.CRASHING;
            stateTimer = SLAM_MS;
            vel = V3.ZERO;
            speed = 0;
            reserves = 0;
            turboMeter = 0;
        }
    }

    /** VehPhysForce_CollideDrivers' kart search, between COLL_MOVED and the speed being rebuilt from the velocity. */
    private void bump() {
        if (bumpSoundCooldown > 0) bumpSoundCooldown--;
        if (others.isEmpty()) return;
        KartBump.Result r = KartBump.resolve(pos, vel, KartBump.weight(kartClass), others);
        if (!r.hit()) return;
        vel = r.vel();
        if (r.impact() > 0) bumps.add(new Bump(r.id(), r.impact(), r.normal()));
        if (r.impact() > KartBump.FEEDBACK_MIN_IMPACT && bumpSoundCooldown == 0) {
            events.add(new KartEvent(KartEvent.Type.BUMP, KartBump.volume(r.impact())));
            bumpSoundCooldown = KartBump.FEEDBACK_COOLDOWN_FRAMES;
        }
    }

    // ---- COLL_FIXED (COLL.c:1341-1674, spec 9.3, 6.1, 6.5) ----

    private void groundProbe(Collider world) {
        boolean was = (flagsPrev & TOUCH_GROUND) != 0;
        Collider.GroundHit hit = world.ground(pos.add(new V3(0, 128, 0)), 384.0);
        groundHeight = hit != null ? hit.point().y : pos.y - 256.0;
        boolean grounded = (flags & TOUCH_GROUND) != 0;
        if (hit != null && pos.y <= hit.point().y + 16.0) {
            grounded = true;
            if (n1.isUp()) n1 = hit.normal();
        }
        if (grounded) {
            flags |= TOUCH_GROUND;
            Terrain under = groundType;
            if (terrain == Terrain.ICE && under != Terrain.ICE) gripTimer = ICE_EXIT_MS;
            terrain = under;
            speedTerrain = under;
            if (!was) {
                flags |= STARTED_TOUCH_GROUND | TURBO_LATCH;
                gripTimer = LANDING_GRIP_MS;
                events.add(new KartEvent(KartEvent.Type.LAND, airtime));
            }
            coyote = COYOTE_MS;
            airtime = 0;
            fall = 0;
        } else {
            flags &= ~TOUCH_GROUND;
            terrain = Terrain.NONE;
            if (pos.y > groundHeight + 128.0) speedTerrain = Terrain.NONE;
            coyote = Math.max(coyote - MS, 0);
            if (coyote == 0) jumpCooldown = 0;
            airtime = Math.min(airtime + MS, Short.MAX_VALUE);
            fall = Math.max(fall, (int) -vel.y);
            if (jumpCooldown != 0) flags |= AIRBORNE;
        }
        if (gripTimer > 0) gripTimer = Math.max(gripTimer - MS, 0);
    }

    // ---- JUMP_FRICTION (VehPhysGeneral_JumpAndFriction, VehPhysGeneral.c:1109-1590) ----

    private void jumpAndFriction() {
        if (state != State.DRIFTING && reserves == 0) {
            int t = Math.abs(ampTurn >> 8);
            int dec = Math.min(map(t, 0, BACKWARD_TURN_RATE, 0, kartClass.turnDecrease), Math.abs(baseSpeed));
            baseSpeed -= Integer.signum(baseSpeed) * dec;
        }
        if (wallRub != 0) baseSpeed = Math.max(-WALL_RUB_SPEED_LIMIT, Math.min(WALL_RUB_SPEED_LIMIT, baseSpeed));
        V3 mv = vel;
        double loss = 0;
        if (grounded()) {
            int acc = engineAccel();
            V3 f = moving.mul(new V3(0, 0, acc));
            if (baseSpeed >= 0) {
                mv = mv.add(f);
                fwdAccelImpulse = acc;
            } else {
                mv = mv.sub(f);
                fwdAccelImpulse = -acc;
            }
            loss = Math.max(0, Math.min(mv.length() - Math.abs(baseSpeed), acc));
        } else {
            fwdAccelImpulse = 0;
        }
        boolean lockout = state == State.CRASHING || state == State.SPINNING;
        if (grounded() && jumpForced != 0) {
            mv = processJump(mv);
        } else if (!lockout && coyote != 0 && jumpBuffer != 0 && jumpCooldown == 0) {
            jumpForced = JUMP_FORCED_MS;
            jumpVel = JUMP_FORCE;
            mv = processJump(mv);
            events.add(KartEvent.of(KartEvent.Type.HOP));
        }
        // VehPhysCrash_ConvertVecToSpeed.
        double sp = mv.length();
        double flat = Math.sqrt(mv.x * mv.x + mv.z * mv.z);
        if (sp > 1e-3) {
            velYaw = atan2Units(mv.x, mv.z);
            velPitch = atan2Units(mv.y, flat);
        }
        V3 up = moving.y;
        V3 planar = mv.sub(up.mul(mv.dot(up)));
        double along = planar.dot(moving.z);
        speedApprox = (int) (planar.length() * (along >= 0 ? 1 : -1));
        speed = (int) Math.max(sp - loss, 0.0);
        vel = mv;
    }

    /** The engine's push (VehPhysGeneral.c:1181-1253, spec 3.4). */
    private int engineAccel() {
        int base = baseSpeed;
        int sa = speedApprox;
        if (base == 0) return 0;
        if (onPad && base > 0) return TURBO_PAD_ACCEL;
        boolean wrongWay = Math.abs(sa) > 0x2FF && (base <= 0 || sa <= 0) && (base >= 0 || sa >= 0);
        boolean reverseSlide = (terrain.flags() & Terrain.FLAG_ACCEL_WHILE_REVERSE_SLIDING) != 0 && base > 0 && sa < 0;
        if (wrongWay && !reverseSlide) return 0;
        int acc = kartClass.accel;
        if (reserves != 0 && base > 0) acc = ACCEL_RESERVES;
        if (terrain.accel() != 256) acc = (acc * terrain.accel()) >> 8;
        return acc;
    }

    /** PROCESS_JUMP (VehPhysGeneral.c:1435-1530, spec 6.3): the hop's vertical speed, plus the slope's. */
    private V3 processJump(V3 mv) {
        jumpCooldown = JUMP_COOLDOWN_MS;
        jumpBuffer = 0;
        double a = slopeVelY(n4, mv);
        double b = slopeVelY(grounded() ? n1 : n2, mv);
        double best = Math.abs(a) >= Math.abs(b) ? a : b;
        double j = jumpVel;
        double vy = Math.sqrt(best * best + j * j) - best;
        double ny = Math.max(mv.y, Math.min(vy, JUMP_VERTICAL_CAP));
        return new V3(mv.x, ny, mv.z);
    }

    /** JumpGetVelY: the vertical speed that following the slope would give. */
    private static double slopeVelY(V3 n, V3 v) {
        if (Math.abs(n.y * 4096.0) < 21.0) return 0;
        return (v.x * n.x + v.z * n.z) / n.y;
    }

    /** VehFire_Increment (VehFire.c:100-385, spec 7.1); false when refused. */
    private boolean fireIncrement(int amount, FireKind kind, int fire) {
        if (state == State.SPINNING) return false;
        flags &= ~TURBO_LATCH;
        // the flame: a new one, or the thread that is still there (even fading) lit again (VehFire.c:180-294)
        if (flameOn) {
            if (kind != FireKind.FREEZE) flameHide = FLAME_HIDE_FRAMES;
            flameCountdown = FLAME_NO_DISAPPEAR;
            flameAlpha = 0;
        } else {
            flameOn = true;
            flameAlpha = 0;
            flameHide = 0;
            flameCountdown = kind == FireKind.STACK ? FLAME_POWERSLIDE_FRAMES : FLAME_NO_DISAPPEAR;
        }
        int cap = SINGLE_TURBO_SPEED + ((fire * (SACRED_FIRE_SPEED - SINGLE_TURBO_SPEED)) >> 8);
        if (reserves == 0 || cap > fireSpeedCap || fireSpeedCap > 4096) {
            fireSpeedCap = cap;
            flameSize = Math.min(8, (fire >> 6) + 5);
        }
        if (kind == FireKind.FREEZE) {
            if (turboOutside < amount) {
                reserves += amount - turboOutside;
                turboOutside = amount;
            }
        } else {
            reserves += amount;
        }
        reserves = Math.min(reserves, Short.MAX_VALUE);
        return true;
    }

    /** VehTurbo_ThTick (VehTurbo.c:143-420): the flame counts down, fades once the reserves are nearly out or its puff is over. */
    private void flameTick() {
        if (!flameOn) return;
        if (state == State.CRASHING) {
            flameOn = false;
            return;
        }
        if (flameHide > 0) flameHide--;
        if (flameCountdown > 0) flameCountdown--;
        if (reserves < FLAME_RESERVES_MIN || flameCountdown == 0) {
            if (flameAlpha > FLAME_GONE) {
                flameOn = false;
            } else {
                flameAlpha += flameCountdown == 0 ? FLAME_FADE_FAST : FLAME_FADE_SLOW;
            }
        } else if (flameAlpha > FLAME_GONE) {
            flameOn = false;
        }
    }

    /** UI_JumpMeter_Update (UI_Meter.c:59-121, spec 6.6): the hang-time boost. */
    private void hangTime() {
        if (tumbleGuard) {
            if (tumbleMs == 0 && grounded()) tumbleGuard = false;
            jumpMeter = 0;
            jumpShown = 0;
            flags &= ~AIRBORNE;
            return;
        }
        boolean airborne = (flags & AIRBORNE) != 0;
        if (airborne && !grounded()) {
            jumpMeter = Math.min(airtime, HANG_TIME_METER_MAX);
            jumpShown = jumpMeter;
            jumpShownTimer = 0x5A0;
            return;
        }
        if (jumpShownTimer > 0) {
            jumpShownTimer = Math.max(0, jumpShownTimer - MS);
        } else {
            jumpShown = 0;
        }
        if (airborne) {
            flags &= ~AIRBORNE;
            int m = jumpMeter;
            jumpMeter = 0;
            if (m >= HANG_TIME_MIN_MS) {
                int fire;
                int level;
                if (m >= HANG_TIME_HIGH_MS) { fire = 0x100; level = 3; }
                else if (m >= HANG_TIME_MID_MS) { fire = 0x80; level = 2; }
                else { fire = 0; level = 1; }
                if (fireIncrement(HANG_TIME_RESERVES, FireKind.STACK, fire)) {
                    events.add(new KartEvent(KartEvent.Type.HANG_TIME, level));
                }
            }
        }
    }
}
