# Research notes

Findings from the first investigation (2026-11-12). Some numbers were summarised by a small model while reading the
sources, so **re-verify any constant against the raw file before porting it**. Items marked (unverified) were not read.

## 1. ctr-native (the physics source)

<https://github.com/CTR-tools/ctr-native> - native PC port of Crash Team Racing (PS1, NTSC-U build 926), C17 + CMake + SDL3.
**License: GPL-3.0** (see "Licensing" below).

- `game/Vehicle/` - driving physics:
  - `VehPhysProc.c` state machine (Driving, PowerSlide, SpinFirst/Last/Stop, SlamWall...) and per-state updates.
  - `VehPhysForce.c` velocity vector, gravity, speed caps, friction, driver collisions, counter-steer.
  - `VehPhysGeneral.c` steering/angular physics, jump, acceleration + friction, base speed.
  - `VehCalc.c` helpers (InterpBySpeed, MapToRange, SteerAccel). `VehFire.c` turbo reserves. `VehStuckProc.c` tumble/crash.
  - `VehPhysCrash.c` kart-vs-kart. `VehTurbo.c` is visuals only.
- `game/COLL/` - collision: BSP search over quadblocks, point-vs-triangle sweeps with 12-bit barycentrics, per-quad
  terrain type and trigger flags (turbo pads etc.).
- Per-class stats: `game/zGlobal_RDATA_vehicle.inc` (not read yet - fetch first). Terrain table (`MetaDataTerrain`, in
  `zGlobal_DATA.c`): friction / turn response / speed multiplier per terrain type.
- The code is matching decompilation with MIPS/GTE emulation, so it is noisy. The upstream **CTR-ModSDK** repo has
  cleaner names; use it as a readability aid.
- No physics write-up exists in ctr-native/docs. **Use the world-of-kartcraft spec instead** (next section).

### Conventions
- 30 fps; one frame is 32 CTR-ms. `x * ms >> 5` means "per frame". Timers count down in ms.
- Angles: 4096 per turn (12-bit, mask `0xFFF`). Normals/matrices: 12 fractional bits (4096 = 1.0).
- Positions/velocities mostly FP8 (>>8). Terrain multipliers 8.8 (256 = 1.0). Arithmetic shifts (negatives floor).
- Yaw: `angle += turnState * ms >> 13`.

### Per-frame flow
state transitions -> `PhysLinear` (timers, throttle/brake/steer) -> `PhysAngular` (yaw, velocity-heading rotation) ->
`ApplyForces` (gravity, friction) -> collision (move-and-slide) -> driver collisions -> jump/friction/engine accel -> matrix.

### Constants spotted (Balanced class, via world-of-kartcraft - verify)
top speed 13140, speed clamp 25600, engine accel 480, reverse target -5120, gravity 900, terminal velocity 20480,
turn rate 28, hop force 4596, hop cooldown 384 ms, coyote time 160 ms. Turbo meter 960 ms (last 480 ms is the red
zone), up to 3 drift boosts (fire caps 2048/2560/3072), spin-out after 60 drift frames with no boost press,
hang time >= 640 ms airborne gives 960 reserves.

## 2. world-of-kartcraft (the proof it can be done)

<https://github.com/duxanlol/world-of-kartcraft> - correction to our assumption: **it is not a WoW addon**. It is a
custom WoW 1.12.1 *client* written in Rust/Bevy (fork of World of Skatecraft / benilla) with a patched vmangos server.
Nothing there is runnable inside Minecraft, but the architecture transfers.

- `crates/ctr-kart` = the physics port of ctr-native (`kart.rs`, `collision.rs`, `terrain.rs`, `stats.rs`).
  **`docs/ctr-physics-spec.md` (72 KB) is the best porting guide - read it in full before milestone 1 work.**
- **The physics core is pure and host-agnostic**: the host feeds it ground type and triangle collision, the core
  returns a pose. We copy this split: `com.ctrcraft.physics` has no Minecraft imports.
- Fixed 30 Hz tick with an accumulator; render interpolates previous/current tick (position lerp, rotation slerp);
  button taps between ticks are latched so short presses are not lost.
- Integer fidelity (positions in 1/256 units, 4096-angle, arithmetic shift) keeps behaviour identical to the decomp.
- Collision: triangle soup in a uniform XZ hash grid; `ground` = downward ray accepting faces with normal y >= 0.643
  (about 50 degrees; steeper = wall); swept sphere (radius 25 lu) with up to 15 move-and-slide iterations; grounded
  within 16 lu. It **replaces CTR's per-quad flags with a slope test** and a per-terrain-type table set by the host.
- Client-authoritative physics, server validates range/timing/inventory. Kart state sync ~10 Hz, < 200 bytes.
- Scale is a free parameter (they chose 1 lu = 0.025 m). We choose ours so top speed feels right at 1 block = 1 m.
- Tracks: checkpoints + laps in a JSON; in-game track editor. Models: extracted from the user's CTR disc (we will
  **not** ship CTR assets; see roadmap).

## 3. Sable and Create Aeronautics

- **Sable** <https://github.com/ryanhcode/sable>: mixin-heavy mod adding **sub-levels** (chunks/entities/block entities
  at a dynamic position + orientation inside a level), physics by Rapier (Rust natives). MC 1.21.1, NeoForge + Fabric.
  Latest 2.0.6 (2026-10-03). **License: Polyform Shield 1.0.0** (source-available; never bundle it, depend on it).
  Maven: `https://maven.ryanhcode.dev/releases`, artifact `dev.ryanhcode.sable:sable-common-1.21.1:<version>`.
  Companion (MIT, jar-in-jar ok): `dev.ryanhcode.sable-companion:sable-companion-common-1.21.1`.
  Wiki: Home, Block Physics Properties, Dimension Physics Data, **Working with Entities** (read this first).
- Public API (`dev.ryanhcode.sable.api`): `SubLevelContainer`, `SubLevelHelper`, `physics/*` (RigidBodyHandle, forces,
  constraints, `BoxPhysicsObject`), block hooks (`BlockEntitySubLevelActor`, `BlockSubLevelCollisionShape`, ...),
  `SablePrePhysicsTickEvent` / `SablePostPhysicsTickEvent`, `entity/EntitySubLevelUtil` (`getTrackingSubLevel`,
  `kickEntity`, `setOldPosNoMovement`).
- Entities standing on a sub-level are **tracked** by it and move/rotate with it. By default Sable kicks entities out
  of the plot into world space; the entity-type tag `sable:retain_in_sub_level` keeps them inside.
- **Simulated Project** <https://github.com/Creators-of-Aeronautics/Simulated-Project> (modules Simulated, Aeronautics,
  Offroad) pins Sable 2.0.4-37, Create 6.0.10-280, NeoForge 21.1.247, Veil 4.3.2, Flywheel 1.0.6. Vehicles there are
  **block-based sub-levels** (assembled blocks + wheels/propellers applying forces), not entities. No entity-vehicle
  API found.
- **Consequence for us (open design question, see roadmap M5):** two ways to be "Sable compatible":
  1. *Kart is an entity* that can drive on and ride along sub-levels (Sable tracks it). Simple, keeps CTR feel. Does not
     make the kart itself a Create contraption.
  2. *Kart is a block assembly / sub-level* (Create-style), driven by CTR-like forces applied through Sable's physics
     API. Truly Create/Aeronautics compatible (can be built on, attached to, flown), but the CTR integer physics
     cannot run as-is; it must be re-expressed as forces on a rigid body.
  Plan: ship (1) first, design the physics core so a (2) "force output" adapter can be added.

## 4. Licensing (decided: GPL-3.0-or-later)

ctr-native is **GPL-3.0**; world-of-kartcraft's kart crates are GPL-3.0-or-later. A mod whose physics is a port of
either must itself be GPL-3.0-compatible. `gradle.properties` is set to `GPL-3.0-or-later` for that reason (a mod
cannot be all-rights-reserved if it ports that code). Sable (Polyform Shield) is only a compile-time/runtime
dependency, never bundled. Do not ship CTR game assets (models, textures, music): custom models only.

## 5. Open items
- Read raw: `ctr-physics-spec.md`, `zGlobal_RDATA_vehicle.inc`, `VehPhysProc/Force/General.c`, `COLL_*`.
- Read Sable wiki "Working with Entities" and the entity-collision mixins; check whether Sable's jar needs a Rust
  native for the dev run configuration.
- Confirm Create version for our instance (Create is not installed yet: instance currently has no Create/Sable).
