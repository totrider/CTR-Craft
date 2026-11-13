# Craft Team Racing roadmap

Target: Minecraft 1.21.1, NeoForge 21.1.265, Java 21. Mod id `ctrcraft`, package `com.ctrcraft`.
Goal: drive Crash Team Racing-style karts (CTR's real physics) on ordinary Minecraft block terrain, built from
blocks and crafted in survival, compatible with Sable / Create / Create Aeronautics.
Background and sources: [RESEARCH.md](RESEARCH.md).

## Architecture decisions

- **Pure physics core** (`com.ctrcraft.physics`): no Minecraft imports, integer fixed-point exactly like CTR
  (positions/velocities 1/256 units, angles 4096/turn, 8.8 terrain multipliers, arithmetic shifts). Input in
  (steer, accel, brake, drift/hop, tap latches), world queries via an interface (`GroundProbe`), pose + events out
  (boost, spin-out, hop, hang time). Unit-testable headless; replays can be checked against numbers from the decomp.
- **Fixed 30 Hz sim with an accumulator** (CTR constants assume one step per frame; Minecraft ticks at 20). The
  driving client simulates, the render thread interpolates between the last two sim poses. Input taps are latched.
- **Client-authoritative driving, server validates** (like world-of-kartcraft): the driver's client runs the sim and
  sends the kart state ~10 Hz; the server sanity-checks speed/teleport and relays. Needed for responsive feel at 30 Hz.
- **World adapter** (`com.ctrcraft.world`): turns Minecraft blocks into the `GroundProbe` the core uses (M2).
- **Kart is an entity** driven by the player riding it (M1). Sable compatibility layer is separate (`compat/sable`),
  soft dependency, never required to load.
- **Scale:** 1 sim unit length is chosen so that Balanced top speed reads right at 1 block = 1 m (tune in M1; constant
  lives in one place).
- **Models/art:** Blockbench `.bbmodel` is the source of truth for every model, `.kra` for textures, generators read
  them (see [../CLAUDE.md](../CLAUDE.md)). No CTR assets shipped.

## Milestones

### M0 - Project setup and research  (this document)
- [x] Gradle/NeoForge workspace, mod loads, build-and-install script targeting `F:\ATLauncher\instances\CTRCraft`
- [x] Research ctr-native, world-of-kartcraft, Sable, Aeronautics (RESEARCH.md)
- [x] Build installs into the instance (launch not yet verified by a human)
- [ ] Add Sable (+ Create) to the test instance; pin versions

### M1 - Spawn a kart in creative and drive it (flat-ish ground, CTR physics)
Status: **done and played** (user: "feels good"). Dismount teleports were the server's vehicle validation; fixed by our own state packet.
1. [x] Read ctr-physics-spec.md + the Rust port; map in [PHYSICS_PORT.md](PHYSICS_PORT.md).
2. [x] `com.ctrcraft.physics`: `KartSim` (Normal, Powerslide, Spin-out, Wall slam, hop, hang time, turbo reserves, terrain
   table, Balanced/Accel/Speed/Turn classes) + 7 unit tests.
3. [x] `KartEntity`, `KartItem` (creative tab "Craft Team Racing"), placeholder model `blockbench/kart/kart.bbmodel`
   -> `tools/make_kart.py` -> item model, `KartRenderer` (draws the baked item model).
4. [x] Ride: right-click mounts; keys: W gas, S reverse, A/D steer, Space = R1 (hop/drift), C = L1 (boost press),
   X = brake; sneak dismounts. Camera swings behind the kart (third person on mount).
5. [x] Client sim at 30 Hz with interpolation (`client/KartDriver`), `BlockCollider` (sphere vs block boxes), vanilla
   move-vehicle packet for the position, `KartVisualPayload` for the drawn drift yaw.
6. [ ] First in-game test in a creative superflat world; tune `KartScale.LU_PER_BLOCK`, seat height, camera.
Known limits: steps and slopes are walls (M2), no sound/particles/HUD yet (turbo meter), no tilt, second player sees 20 Hz motion.

### M1.5 - Feel and feedback (from the first test)
First test findings: dismounting sometimes put the player at an earlier position (fixed in code, see KartDismountPayload; the
log line "Kart dismount: ... blocks" will show if the server's kart was behind); the drive felt janky (cause not yet pinned).
What CTR itself has, to copy (its single-player HUD has a dial speedometer with a needle, the powerslide meter and the hang-time meter, and no speed number):
- [x] HUD (`KartHud`): the original's: the dial (green, yellow and red band over a dark fan, gold needle; `Speedometer`), the powerslide meter box (green while there is room, red when a boost press is due) and the hang-time bar with its readout box, at the original's positions. No speed number, as in the original.
- [x] Boost wheelie ("rearing"): WHEELIE_START/HOLD/RECOVER pose tables are in the spec/Rust port (lift, forward shift, pitch, scale).
- [x] Exhaust: turbo flame sized 5 + (fireLevel >> 6), backfire puff on a failed (early) boost, drift tyre smoke, wall-rub sparks.
- [x] Sounds: engine pitch/volume from CTR's engine-sound logic (ported, drives our own samples), skid, boost, hop, land, wall hit.
  CTR's recordings are not shippable (Sony/Naughty Dog): original placeholder samples in the mod, and `tools/extract_ctr_sounds.py`
  (`extract-ctr-sounds.bat`) builds a LOCAL resource pack with the real ones from the player's own disc (engine, skid, turbo, hop,
  land, boost, meter, wall, crash). Done; no CTR sound exists for drift start, early-boost fail, red zone or spin-out (placeholders stay).
- [ ] Ground tilt (kart follows the surface normal), chase-camera tuning, interpolation for other players' karts (20 Hz now).

### M2 - Smooth terrain from blocky worlds
Status: **core done and played** (steps, slabs, stairs, mixed stairs smoothed; boost blocks, Fusion connecting textures and Copycats+ checked by the user; 12 terrain tests). See [TERRAIN.md](TERRAIN.md).
- [x] Smooth ground from block collision shapes: a height map at half-block resolution dilated by a 45 degree cone, rebuilt
  every physics tick around the kart (steps, slabs, stairs become ramps; walls, gaps and cliffs stay).
- [x] Wall collision: sphere vs the boxes the ground does not cover; CTR wall-rub / slam-wall behaviour unchanged.
- [x] Surface types from blocks via block tags (dirt, slow dirt, water, ice, icy road, snow, mud, sideslip, turbo pad), data-driven.
- [x] Boost blocks (CTR turbo pad: reserves, engine accel 8000, big flame, wheelie): a no-collision directional plate, a block, a
  slab and stairs (arrow up/right/down/left relative to the stair), all rotatable with an empty-hand click, and Create / Copycats+
  copycats take on the surface of the block they copy (a copycat of a boost block boosts). See TERRAIN.md.
- [x] Fusion connected textures for the boost blocks (optional dependency, plain textures without it).
- [x] The kart leans on the ground (smoothed normal synced and applied in the renderer); hang time and ramp jumps fall out of the physics.
- [ ] Kart-vs-kart bumping (the 64 lu spheres), out-of-bounds respawn. (Checkpoints, laps and the race manager moved to M2.6.)
- [ ] More racing blocks (start line, checkpoint, track edge), water, a hand-drawn pad texture (.kra).
- [ ] Later idea (user): a boost pad look where one large arrow design repeats across a group with a trim round the edge. Fusion cannot
  scale an arrow to the group (it only sees the 8 neighbours; continuous textures are world-grid aligned), so it would be either multi-block
  2x2 / 3x3 large pads (exact), or "long arrows" along the direction with Fusion's `simple` layout (head, shaft, tail), or own rendering.

### M2.5 - Racers, pad families, Wumpa (agreed 2026-11-12; faithful by default, extensions opt-in)
Principle: see the fidelity policy in [../CLAUDE.md](../CLAUDE.md). PS1 behaviour is the default; anything else is a separate block/item/data entry.
- **Fruit, crates, power-ups, rule sets:** design in [DESIGN_ITEMS.md](DESIGN_ITEMS.md); the pure core (item tables, `RuleSet`) is built.
- **Boost pad families.** The regular Boost Pad family stays exactly the PS1 turbo pad (cap 4096, reserves 960 frozen, accel 8000).
  Extensions as extra block families (same four shapes, Fusion-connected, copycat-able, each with its own colour): 
  - *Super Boost Pad* (blue): PS1's own super turbo pad (`COLL_STEP_TRIGGER_SUPER_TURBO_PAD`: reserves 120 frozen, fire level 0x800, cap 18432,
    target clamped to 0x6400). The only thing that burns the blue flame (`ultraFire()` is already wired to flame, sound and flags).
  - ideas, not decided: a *Mini Boost Pad* (a smaller boost, for a gentler tier), a *directional* pad (boosts only along its arrow).
  Needs a pad-kind in the surface code (today: one `PAD` bit) and a per-kind `KartSim.turboPad(kind)`.
- **Racer tuning and look (split).** Design in [DESIGN_KART_DATA.md](DESIGN_KART_DATA.md): stats (a `ctrcraft:tuning` component on the item and the entity, edited with a Tuning Wrench) are separate from visuals (parts, later). Summary: PS1 racers differ only by their *engine class*: Balanced (Crash, Cortex, Komodo Joe, Fake Crash, N. Oxide), Accel (Coco,
  N. Gin, Pinstripe), Speed (Tiny, Dingodile, Papu Papu, N. Tropy), Turn (Polar, Pura, Ripper Roo, Penta Penguin); within a class the
  physics are identical. We have the four classes (`KartClass`). Plan: make the stats data-driven (`KartStats`, one datapack JSON per class,
  defaults = the four PS1 classes), a class id on the kart (synced, kept in the item), choose it when spawning in creative and later by crafted
  parts in M3. Custom tunings are extra datapack entries, clearly not PS1. Racer names can be presets ("Crash" -> Balanced).
- **Wumpa fruit.** In PS1 each fruit (0-10) raises top speed a little (see below) and 10 = "juiced" upgrades items (turbo item fire level 128 -> 256,
  Super Engine) and sounds. A spin-out costs 1 fruit, a blast 3. Needs: fruit pickups in the world (an item/entity or crates, opt-in
  how they appear), the kart's fruit count (synced), the counter on the HUD, the losses on hits. `KartSim.wumpa` already feeds the speed.

### M2.6 - Tracks and races (design: [DESIGN_TRACKS.md](DESIGN_TRACKS.md)) - step 1 built, needs an in-game test
A node graph you build (start and checkpoint node blocks, linked with a Track Tool), then test-drive to record the main path and branches; a race starts when karts
gather at the Start node, with the signal-light countdown and start boost. In order: (1) nodes, tool, linking, saved data; (2) test drive and validation;
(3) race runtime (lobby, countdown, grid, lights, start boost, laps, positions, HUD); (4) polish (respawn, wrong-way, time trials, ready UI).
Replaces the old "checkpoints / laps / timer / race manager" line in M2.

### M3 - Survival integration
- Crafting: kart chassis, engine, wheels, steering part (recipes + advancements); kart classes (stat sets from the
  CTR class table) from different engines.
- Kart as an item; placing/pick-up, health/durability, repairs, fuel-less (no fuel) or optional fuel.
- Loot/upgrades: turbo upgrades, tires (terrain grip), paint/dye.
- Config + gamerules (speed scale, allow in survival, server validation strictness).
- Multiplayer polish: remote kart interpolation, kart-vs-kart collisions, spectate.

### M4 - Sable / Create / Aeronautics compatibility
- Entity-on-sub-level: kart drives on moving/rotating sub-levels (Create contraptions, Aeronautics airships): ground
  probe works in sub-level space via Sable's transforms; tag `sable:retain_in_sub_level` where needed.
- Decide on "kart as sub-level" adapter (CTR physics expressed as forces through Sable's physics API) so karts can be
  built from blocks, attached to contraptions, towed or carried. Prototype before committing.
- Compat with Create (kinetic/stress for optional engines?) only if it falls out naturally.

### M5+ (ideas, unscheduled)
- **Map integration (decided 2026-11-12):** no track display of our own. Show the track and the racers through Xaero's Minimap / World Map
  (an integration or extension if the mods allow it; how is still to be researched, including their licences and whether they offer an API).
- Weapons/items (crates, wumpa), AI karts, race manager block, track editor, custom character skins, sounds/music
  (original), more kart classes, replay/ghosts.

## Open decisions
1. Licensing: GPL-3.0-or-later, decided 2026-11-12. `LICENSE` is in the repo root.
2. Sable integration style for M4 (entity vs block assembly); revisit after M1/M2.
3. Controls: default keys for drift/hop/brake/reverse; use vanilla movement keys for steer/accel.
