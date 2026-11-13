# Physics port map

`com.ctrcraft.physics.KartSim` is a port of CTR's kart driving, following `docs/ctr-physics-spec.md` of
world-of-kartcraft (kept locally in the gitignored `reference/` folder, with the raw ctr-native sources it cites).
Source is GPL-3.0, so this mod is GPL-3.0-or-later. No Minecraft imports in the package.

Conventions: positions in lu (1 block = 50 lu, `KartScale`), speeds in 1/256 lu per frame, angles 4096 per turn,
timers in CTR-ms (32 per 30 Hz frame). Vectors are doubles; the scalar state is `int` with the same shifts as CTR
(the Rust port is float too; numerics drift slightly from the decomp, the feel is the same).

## Function map (Java method -> CTR function -> spec section)

| `KartSim` | CTR | Spec |
|---|---|---|
| `tick` | stage order of `MainFrame.c:308-330` | 2.1, 11 |
| `updateStage` | `VehPhysProc_Driving_Update`, `_PowerSlide_Update`, spin phase updates | 2.3 |
| `powerslideInit` | `VehPhysProc_PowerSlide_Init` | 5.1 |
| `powerslideUpdate` | `VehPhysProc_PowerSlide_Update` (turbo meter, boosts, spin-out, exit) | 5.3-5.4 |
| `spinInit`, `spinAngular` | `VehPhysProc_SpinFirst_Init`, spin PhysAngular | 13.2 |
| `physLinear` | `VehPhysProc_Driving_PhysLinear` (timers, throttle table, steer command) | 3.1-3.2, 4.1 |
| `targetSpeed` | `VehPhysGeneral_GetBaseSpeed` | 3.2 |
| `physAngular`, `visualTurn`, `lerpToForwards` | `VehPhysGeneral_PhysAngular` | 4.2-4.6 |
| `counterSteer` | `VehPhysForce_CounterSteer` | 4.6 |
| `powerslideAngular` | `VehPhysProc_PowerSlide_PhysAngular` | 5.2 |
| `applyForces`, `friction` | `VehPhysForce_OnApplyForces`, `_OnGravity`, friction block | 3.6, 6.4 |
| `sweep` | `COLL_MOVED_PlayerSearch`, `COLL_MOVED_ScrubImpact` | 9.1-9.2 |
| `groundProbe` | `COLL_FIXED_PlayerSearch` | 9.3, 6.1, 6.5 |
| `jumpAndFriction`, `engineAccel`, `processJump` | `VehPhysGeneral_JumpAndFriction` | 3.3-3.5, 6.2-6.3 |
| `fireIncrement` | `VehFire_Increment` | 7.1 |
| `hangTime` | `UI_JumpMeter_Update` | 6.6 |
| `fireIncrement` (flame part), `flameTick`, `fireSize`, `flameFade` | `VehFire_Increment` (VehFire.c:180-330), `VehTurbo_ThTick` (VehTurbo.c:143-420) | not in the spec: read from the decomp |
| `rotAxisAngle` (FixedMath) | `VehPhysForce_RotAxisAngle` | 8.1 |

## The turbo flame (from VehFire.c / VehTurbo.c, checked 2026-11-12)
- There is **no colour choice**: one 8-frame flame model for every boost, scaled by size = clamp(5 + (fireLevel >> 6), 4, 8). A pad,
  a 1.5 s hang time and a juiced turbo item all reach size 8. (The "blue flame" of CTR lore is not in the turbo code; only a super
  turbo pad passes the sacred fire cap of 4096, and we have none, so `ultraFire()` is never true yet and is the one place blue could go.)
- **Lifetime:** a flame made by a powerslide or hang-time boost shows 2 frames, then fades fast (alpha +0x100 a frame, about 16 frames)
  even with boost left. A pad's or item's burns while reserves >= 0x10, then fades slowly (+0x40 a frame, about 64 frames). Lighting it again
  while the thread still exists (even fading) resets it to burn on; non-pad boosts hide it 3 frames (0x60 ms) when they do. A wall slam ends it.
- The turbo sound follows the same alpha (volume 0x100 - (alpha >> 4)).

## The grid rev and start boost (ported 2026-11-12)
`RevEngine` = `VehStuckProc_RevEngine_Init/_Update/_Animate` (ctr-native `game/Vehicle/VehStuckProc.c`), constants from its `VEH_STUCK_REV_*` enum:
fill step half the distance clamped 0x100..5000, over-rev after 192 ms at the target (lockout until the gas is let go and the revs die), release cooldown 0x100 ms,
decay half clamped 0x100..1000 (3000 when locked), empty cooldown 0xC0 ms; the target (`boostMeter`) starts at AccelSpeed + SacredFire/3 and steps by SacredFire/3+3 each
time you let go above and fall back under AccelSpeed (pumping), up to AccelSpeed + SacredFire. At GO: boost only if the revs are above AccelSpeed and not locked out;
big (fire level 0x80) if the target reached its top, else small (0x20); both call `VehFire_Increment(960 reserves, STACK, fire)` (`KartSim.startBoost`).
AccelSpeed_ClassStat = Speed_ClassStat + 1500 (`KartSim.accelSpeed`). Read from the decomp by a fetch and cross-checked against world-of-kartcraft `rev.rs`
(the structure follows it; GPL-3.0). While revving the kart stands braked and the gas revs instead of driving (`KartDriver`).
The lamps are `DotLights.c`: red at 0xB40, 0x780 and 0x3C0 ms left, green at 0 (DOT_LIGHT_TIMER_*), 960 ms apart, matching `RaceGrid.lightsLit`.

## Not ported yet (M1 scope)
Items (mask, shield, TNT, turbo item), hit reactions other than the drift spin-out, wall-slam recovery animation,
kart-vs-kart bumping, the boost wheelie, squash/stretch, anti-V-shift freeze, super turbo pad, start-line rev boost,
water/flight (those two are world-of-kartcraft additions, not CTR). Per-class tables exist (`KartClass`); the four classes
are Balanced, Accel, Speed, Turn.

## Deviations
- Doubles instead of s32/s16 truncation for vectors (`vel`, `pos`, matrices) and `Math.sin/cos` instead of the 1024-entry
  trig table. Scalars use the original integer shifts.
- Collision is the host's `Collider` (the smooth terrain model in [TERRAIN.md](TERRAIN.md): a ground surface plus wall boxes), sweep in
  <= 32 sub-steps of 0.4 radii, instead of CTR's BSP quadblocks. Ground vs wall is by slope (`normal.y >= 0.643`) instead of the
  per-quad flag.
- `speedApprox` (= `forwardSpeed()`) is read before the engine's excess is cancelled, as in CTR, so at top speed it reads one
  frame of accel above `speed` (13500 vs 13140 for Balanced). Tests assert `speed`.

## Kart vs kart (`KartBump`)
Ported from `VehPhysForce_CollideDrivers` (VehPhysForce.c:1045-1072, the search) and `VehPhysCrash_AnyTwoCars` / `_BounceSelf`
(ctr-native game/Vehicle/VehPhysCrash.c). Constants checked in the raw files: `THREAD_DRIVER_HIT_RADIUS = 0x40` (namespace_Proc.h:79),
COLLISION_WEIGHT 256 / 256 / 300 / 200 for Balanced / Accel / Speed / Turn (zGlobal_DATA.c:7240), bounce divisor `0xC00`, Y clamp
`0x3200`, feedback threshold `0x200`, volume map 0x3F..0xFF over 0..0x1900, sound cooldown 3 frames.
- Every kart is a sphere of 64 lu (1.28 blocks). The nearest other kart closer than the radii's sum (128 lu = 2.56 blocks, centre
  to centre) bumps it: both karts' velocities are blended by weight, each bounces off that velocity when closing in (1/3 comes
  back as bounce), and a push of `16 * overlap` velocity units separates them. No damage, whatever speed.
- CTR changes both karts in one call. Here every client works out its own kart's half from the other's position and velocity (the
  blend and the contact normal are shared, so it is the same arithmetic). Another kart's velocity is the distance its entity moved
  over the last game tick, as nothing relays it. All karts count as Balanced (256) until the class is synced.
- The bump runs where CTR runs it: after the collision sweep, once the engine's accel is taken off the velocity and before the
  speed is rebuilt from it. The crash sound (the placeholder slam) plays at the decomp's volume.
- Not ported: CTR's weapon / shield / turbo-item damage queued on contact (VehPhysCrash_Attack), the voice line, controller rumble.
  Interaction with Minecraft entities (mobs, boats, damage, power-ups) is a separate system, on by default.

## Wumpa fruit flying to the HUD (client, `WumpaFly`)
- `UI_RenderFrame.c` (the `PickupWumpaHUD` block), `RB_Fruit.c` / `RB_Crate.c` (what sets it), `UI_Lerp2D_HUD`. Taking a fruit sets `startX/startY` to its
  screen position minus 0x14, `cooldown = 5` and `numCollected++` (a crate sets `numCollected` to 5 to 8). Each 30 Hz frame with fruit queued: with `cooldown`
  at 0 one fruit arrives (`numCollected--`, `RB_Player_ModifyWumpa(+1)`, sound 0x42) and the timer restarts at 5 while more are queued; otherwise the fruit
  icon is drawn at `end + cooldown * (start - end) / 5` (integer maths, the HUD fruit at (316, 24) of the 512 x 216 screen) and `cooldown--`. So one fruit
  flies at a time and each takes 6 frames, and the HUD count only goes up on arrival.
- Here the HUD shows the real count minus the queued fruit (the server's count and the juiced state are not delayed), and the start is the kart's screen
  position for loose fruit too. The arrival sound (OtherFX 0x42, `fruit_arrive`) plays 2D at each arrival; the pickup itself plays 0x43 (`fruit_pickup`).

## Tests
`src/test/java/com/ctrcraft/physics/KartSimTest.java` checks the numbers the spec derives: top speed reached in about 37 frames,
coast to a stop in about 110 frames (-120/frame), hop apex about 46 lu and about 10 airborne frames, a landing with the hop
button held and steering in starts a powerslide, a red-zone press gives a boost and reserves, an early press backfires.
Run with `gradlew test`.
