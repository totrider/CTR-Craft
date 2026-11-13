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


## The airborne pose (2026-11-13)
`VehFrameProc_Driving`, the airborne branch (`Vehicle/VehFrame.c:100-186`), with the poses `bakedGteMath[BAKED_GTE_MATRIX_JUMP_BASE + character]` (`zGlobal_DATA.c:6078-6545`): `physics/AirTilt.java` (the 13 characters' four poses each, parsed from the data file; Oxide uses Crash's) and `KartSim.airTiltTick`. In a big jump the kart's model tilts sideways: Crash's last pose rolls him 265/4096 of a turn (23 degrees), lifts him 16.6 level units and shifts him 5.2 sideways and 4.5 forward; Coco's rolls 5.6 degrees.
- **When:** `kartState` driving (not spinning, crashing or blasted), the kart more than 0x8000 position units (128 level units) above the ground (`posCurr.y - quadBlockHeight`), and rising faster than `VEH_FRAME_JUMP_AIR_THRESHOLD` 0x600 (1/256 units a frame) or already in the animation. A small hop never gets there; a ramp launch does.
- **How:** the animation frame climbs by 1 a frame from 0 to the last (3), and the matrix index is that frame; when the conditions end it falls back by 2 a frame to 0 and the pose is gone (`TransitionAnimation`, default speed 2). The drive animation's own transition into it (a few frames of settling the steering frame to its middle first) is not ported.
- **Entries** are the same 0x20 byte authored form as the wheelie's (checked against `matArr01[0]`: shift `(0, -19, -537)` is the wheelie's lift and forward, rotation x `-26` its pitch, scale `(4096, 4262, 3686)`): bytes 0-7 the shift (x, y, z), 8-15 the rotation (x pitch, y yaw, z roll), 16-23 the scale; all the jump poses' scales are 1.
- **Renderer** (`KartRenderer.applyAirTilt`, also for the rider): shift, then roll about the length axis, yaw, pitch, as the wheelie's pitch is applied. The game's frame to the model's is `(x, y, z) -> (x, y, -z)` (the forward shift and the pitch flip, as the wheelie's do); a rotation about the length axis keeps its sense, so a negative roll leans the kart towards its right. **The side is by that derivation, not seen in the footage: flip `rollDegrees` in `applyAirTilt` if the original leans the other way.** One baked pose at a time: the jump pose replaces the wheelie's while it plays (the game's `matrixArray` holds one).
- **Wire:** 3 bits of the visual flags (`KartFlags.airTilt`, bits 27-29), 0 none, 1 to 4 the pose. The mod's kart uses Crash's poses until characters are chosen.

## Footage corrections and the steering pose (2026-11-13)
- **Jump pose direction:** from the original's rear and front footage the kart leans forward and to its left in a long jump, and the driver stays upright (the matrix moves the kart's model, not the character's). The renderer now takes the table's roll, pitch and yaw the other way round from before (`KartRenderer.applyAirTilt`: roll `ZP(-roll)`, yaw `YP(+yaw)`, pitch `XP(+pitch)`); the rider is only lifted and shifted with the kart (`applyBodyTurn`). The signs are by the footage, not by the table's convention.
- **Steering pose** (`VehFrameProc_Driving`, `DriveAnimation`): the shown steering frame is the stick (`simpTurnState` over the class's turn rate) mapped onto the animation's frames and moved one frame a game frame towards the target (`VehCalc_InterpBySpeed`, speed 1). `KartSim.steerAnim` keeps it as -1 (left) to +1 (right); the animation's frame count is in the character models, not the decomp's data, so a step of 2/15 a frame (sixteen frames lock to lock) is assumed. It goes over the wire in bits 16-20 of the engine word (`KartEntity.steer`). The Minecraft rider is turned by it: body 14 degrees, head 30 degrees into the turn (`PlayerKartPose`) and leaning 6 degrees about the kart's origin (`applyBodyTurn`). Those three angles are the mod's, tuned by eye, not the game's.
- **Held TNT:** the crate on the driver's head now takes the same body turn as the rider and the head's yaw (`TntRenderer`).
- **Bomb roll:** the rolling pictures run the other way (`BombRenderer`).

## The rider's steering pose, measured; the kart model (2026-11-13)
- **What the original does** (`tools/inspect_ctr_kart.py` measures the drive animation's 21 frames, 0 = full left, 10 = straight, 20 = full right, in the racers' models): only the driver's upper body moves; the legs, the seat and the kart do not. At full lock Crash's torso turns 19 degrees, his chest 28 and his head 36 about the vertical, and the head moves 23 units (0.21 of a crate) sideways and drops 6 and goes back 9: a lean of about 30 degrees about the waist. His hands move only 6 units (the wheel). The shown frame follows the stick one frame a game frame (`KartSim.steerAnim`, step 1/10 now that the 21 frames are counted).
- **In the mod** (`RiderPlayerModel`, which replaces the player renderers' model; no mixin): the legs stay as the seat pose has them; the torso turns 19 and leans 20 degrees (the original's 30 swings a Minecraft rider's much taller head too far; `KartRenderer.LEAN`) about the hips; the head turns 36 and follows the lean; each arm is a straight limb aimed from its moved shoulder at a fixed point in front of the lap (a Minecraft arm has no elbow), so the hands stay at the wheel. The held TNT crate takes the head's lean and turn.
- **Kart model:** the pack's racer models (`crash`, `cortex`, ... `oxide`) are kart body and driver in one mesh of about 100 triangles, with no wheels. Every racer is 111 units long (1.02 crates) and 51 to 109 wide, 48 to 138 tall (driver included); the mod's stand-in kart is 1.5 blocks long. The wheels are sprites: `tire00`-`tire16` and `trtire00`-`trtire16` (17 pictures of 32 x 32 texels each, one wheel seen from edge-on to face-on: view angles, not spin frames). `local-assets/ctr-kart/` has the OBJs, the sprites and `report.txt`.
- **Weapon speeds** (`CtrConfig.weaponSpeedScale`, default 1): the bomb's 96 units a frame (`TRACKER_LAUNCH_VELOCITY`: 4096 * 3 >> 7) and the beaker's 120 (`POTION_THROW_SPEED`, flight about 24 frames, 2880 units) are the decomp's values, converted with the kart's own 50 units to the block, so against the kart they are right (the bomb is 1.23 times a kart's top speed). Against the crates and beakers, drawn at 108.8 units to the block, the whole world runs 2.2 times faster than the original's proportions. The option scales the bomb and the throw only.
