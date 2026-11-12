# CTR Craft (Crash Team Racing Craft)

A NeoForge mod for Minecraft 1.21.1 (NeoForge 21.1.x, Java 21) that brings **Crash Team Racing's kart driving** into Minecraft: powerslides,
hops, turbo boosts, boost pads, start boosts, hang time, on ordinary block terrain, with race tracks you build yourself.

> **Status: active development (version 0.1.0).** The mod is not finished and parts of it will change. What works today and what is
> still planned is listed under [Status](#status). Expect rough edges, and please report them.

*Crash Team Racing* is a trademark of its owners. This is an unofficial fan project, not affiliated with or endorsed by Activision,
Naughty Dog or Sony. **No game assets are included in this repository or in the mod**, see [Original game assets](#original-game-assets-optional-local-only).

## Why this exists
The goal is something fun for Minecraft that sits *inside* the normal gameplay loop instead of next to it: a survival-centric mod where you
gather materials, craft what you need (karts and parts, boost pads, power-ups), and then build your own courses out of ordinary blocks, with
boost pads, branching paths, power-ups and time trials, on a server with friends. It is meant to work together with Create and especially
Create Aeronautics / Sable (sub-levels), so that karts and tracks can live on and next to moving contraptions. See
[Mod compatibility](#mod-compatibility): the mod is a standalone mod and works without any of them.

## How it is being made (please read)
This project is built by one developer **together with Claude** (Anthropic's AI model, through Claude Code), as *curated vibe coding*:
the developer decides what the mod should be and how it should feel and plays every change in the game; Claude writes much of the code,
tools and documentation. In practice that means a lot of iteration: describe a behaviour, build it, drive it, find what is wrong, fix it,
repeat. Some honest consequences:
- The code is AI-assisted. It is tested (unit tests for the physics, terrain and track logic, run with `gradlew test`) and played a lot, but
  it has not had a traditional human code review, and bugs are to be expected.
- Where the original game's behaviour matters, the numbers are checked against the decompilation rather than remembered, and what is *not*
  verified is marked as such in [docs/PHYSICS_PORT.md](docs/PHYSICS_PORT.md) and the design documents.
- The design notes in `docs/` are working documents written during development, including decisions that were later changed.

## Inspiration and references
The project is inspired by [**World of Kartcraft**](https://github.com/duxanlol/world-of-kartcraft) (CTR physics as a standalone game in
Rust), and its documentation of the physics was a valuable guide. To make the mechanics as authentic as possible while still working in
Minecraft, the physics here was ported function by function from the [**ctr-native**](https://github.com/CTR-tools/ctr-native)
decompilation of the PS1 game, and cross-checked against World of Kartcraft's Rust port. Everything else, the Minecraft side (terrain,
blocks, entities, networking, tracks, races), is **built from the ground up as an original mod**.

The rule for behaviour is: **the default is what the PS1 game does**, as read from the decompilation. Anything beyond that is an opt-in
extension (a different block, an item, a data pack entry or a setting), never a silent change.

## Status
| | |
|---|---|
| **Works today** | Drivable karts with CTR's physics (powerslide, hop, boosts, spin-outs, wall slams, hang time) and its engine, skid and boost sounds (original synthesised placeholders). Smooth driving over steps, slabs and stairs. Boost blocks. Track building with nodes, a Track Tool, test drive and validation, branching paths. Races: starting by driving in, countdown, random grid, lights, start rev and start boost, laps, positions, HUD, respawn. A Wrench to switch whether a kart can start races. Speedometer, powerslide meter, hang-time meter. |
| **Planned** | Survival crafting for karts, parts and boost pads. Power-ups (Wumpa fruit, items). Time trials and ghosts. Kart-vs-kart bumping. Kart tuning (CTR's four engine classes, data-driven) with a tuning profile on the Wrench. A Super Boost Pad (CTR's blue-flame pad) as an opt-in block. Wrong-way detection, a ready check. Sable / Aeronautics compatibility and claim support for building tracks on a server (see [Mod compatibility](#mod-compatibility)). Original CTR textures and UI via the optional local resource pack. |

The full plan is in [docs/ROADMAP.md](docs/ROADMAP.md).

## Quick start
1. Install NeoForge 21.1.x for Minecraft 1.21.1 and put `ctrcraft-<version>.jar` in `mods/`. No other mod is needed;
   see [Mod compatibility](#mod-compatibility) for the ones that enhance it.
2. In creative, open the CTR Craft tab, take a **Kart**, right-click the ground to place it and right-click the kart to get in.
3. Drive (see [Controls](#controls)). Sneak to get out.
4. Build and race a track: see [Building a race track](#building-a-race-track).

## Blocks and items
Everything is in the CTR Craft creative tab for now (survival recipes are planned).

| Item | Kind | What it does |
|---|---|---|
| **Kart** | Item, places an entity | A rideable kart with CTR's physics. Right-click the ground to place it, right-click the kart to board, sneak to get out. |
| **Boost Pad** | Block | A 1-pixel plate laid on the floor with **no collision**: a kart drives over it without a bump and gets CTR's turbo-pad boost. |
| **Boost Block** | Block | A solid cube that boosts the same way. |
| **Boost Slab** | Block | Bottom, top or double slab; boosts. Completing a slab keeps the first one's arrow. |
| **Boost Stairs** | Block | Stairs; boosts. The arrow runs along the treads. |
| **Track Tool** | Item | Links track nodes, resizes their boxes, opens the Start Node menu. See [Building a race track](#building-a-race-track). |
| **Start Node** | Block (invisible) | Marks the start line and the way the grid faces; starts races when a kart drives into its area. |
| **Checkpoint Node** | Block (invisible) | A checkpoint box a kart has to pass, in order. |
| **Wrench** | Item | Right-click a kart to switch whether it can start races (on by default). Kart tuning is planned for it. |

### Boost blocks
- **They are placed relative to your orientation.** Each shows a chevron arrow on top, pointing the way you were looking when you placed it.
  Stairs have four arrow directions (up, right, down, left) relative to the stair. Click a boost block with an **empty hand** to turn its arrow.
- **The arrow is a marker, not a rule:** as in CTR, a pad boosts whichever way you drive over it.
- **Connected textures:** boost blocks of the same kind and direction placed next to each other join up, so the yellow border runs only round
  the outside of the group and each block keeps its arrow. This needs Fusion (see below); without it the plain texture is used.
- Boost blocks work as **surfaces for copycat blocks** too, see [Mod compatibility](#mod-compatibility).

## Controls
All of these can be rebound under Options > Controls > CTR Craft (the movement keys are Minecraft's own).

| Action | Default | Notes |
|---|---|---|
| Accelerate | `W` | Hold. |
| Steer | `A` / `D` | |
| Reverse | `S` | |
| Brake | `X` | The PS1 "square" button. With `W` held, a power-brake. |
| Hop / powerslide / boost press (R1) | `Space` | Hop; land with it held and steering to start a powerslide; tap it in the red zone of the meter to boost. |
| Second shoulder button (L1) | `C` | The same as R1; either one hops and boosts. |
| Get out of the kart | `Shift` (sneak) | |
| Respawn at last checkpoint | `R` | During a race only, with a short cooldown. |
| Rev the engine on the grid | hold `W` | Held on the grid until the lights go out, see [Race](#6-race). |

## Mod compatibility
CTR Craft is a **standalone mod**: it needs only NeoForge and Minecraft, and everything described above works without any other mod. The mods
below are all optional; when they are installed, the mod is enhanced.

| Mod | State | What you get when it is installed |
|---|---|---|
| [**Fusion**](https://github.com/SuperMartijn642/Fusion) | Works | **Connected textures** for boost blocks: neighbouring boost blocks of the same kind and direction merge, with the border drawn round the group only. |
| [**Create**](https://github.com/Creators-of-Create/Create) | Works | **Copycat blocks** take on the block they copy: right-click a copycat with a boost block and it boosts (boost slopes, boost beams...); a copycat of ice is slippery ice. Right-click again with the same block to turn the arrow. |
| **Copycats+** | Works | The same for its many shapes (slopes, beams, panels, bytes ...), including blocks made of several parts. |
| [**Sable**](https://github.com/ryanhcode/sable) | Planned | Karts driving on sub-levels (moving and rotating structures). Declared as an optional dependency only; never required and never bundled. |
| **Create Aeronautics** | Planned | Karts on and around airships and contraptions, through Sable. |
| [**Open Parties and Claims**](https://github.com/thexaero/open-parties-and-claims) | Planned | Track building that respects claims, so players can build and edit tracks on a server in survival. |

## How the kart drives over blocks
CTR's physics expects smooth ground, and Minecraft is made of boxes, so the mod turns the blocks around the kart into a smooth surface
many times a second, and the unchanged CTR physics drives on that.

- **Each physics tick** (CTR runs at 30 Hz) the mod looks at the blocks in a small window around the kart, sampled at half-block resolution,
  and for each spot finds the top of the highest block the kart could be standing on (up to 1.25 blocks above its wheels, so bridges,
  overhangs and tunnels pick the right layer).
- **A height map with a 45 degree skirt.** Every sampled height is spread outwards as a cone with 45 degree slopes, and the ground is
  the highest of those cones. The result: **a one-block step becomes a one-block-long 45 degree ramp**, slabs and stairs become gentle ramps,
  and a staircase of full blocks becomes one continuous slope. Flat floors stay flat.
- **Smoothing.** Mixed staircases (full blocks and slabs) would come out as "ramp, flat, ramp, flat", so the surface is blurred about a block
  wide, which turns them into one even slope. It may sit at most 3 pixels below the sharp surface, so the kart rides over a block corner
  instead of bouncing.
- **The steepest slope is 45 degrees**, which CTR's physics counts as ground, so every ramp is drivable and the kart tilts to follow it.
  Drive off the top of a ramp fast enough and you jump; the airtime builds CTR's **hang-time boost**.
- **What it does *not* climb:** a step of **two blocks or more**, fences, walls and ceilings are walls. The surface ends there and the
  kart's sphere (one block wide) bumps, scrapes or slams into it, exactly like CTR's walls. Cliff edges and gaps are gaps: the kart falls.
- **Surfaces come from block tags** (`ctrcraft:surface/...`): dirt, grass, sand and gravel are CTR's slow off-road, ice and snow are slippery,
  mud and soul sand are slow, and everything else is full-speed asphalt. A data pack can change any of this. Boost blocks use
  `ctrcraft:turbo_pad`.

The long version, with the tests that pin it down, is in [docs/TERRAIN.md](docs/TERRAIN.md).

## Building a race track
A track is a loop of **nodes** that you place and link. The nodes are invisible markers; the track is whatever you build around them
with ordinary blocks, so there are no "out of bounds" blocks: the track's walls and shape decide where players can go.

### 1. Build the road
Build the course out of normal blocks first. Keep to what the kart can drive: one-block steps, slabs and stairs are ramps; two-block
steps, fences and walls are walls. Put **boost blocks** where you want boosts. Remember there is no invisible barrier, so build
walls or barriers where players should not leave the track.

### 2. Place the nodes
- Put a **Start Node** at the start line, on the road surface, **facing the direction of travel**: the grid for the race is laid out behind it.
  Nodes face the way you look; when you hold a node item you see a preview with all 16 directions (22.5 degree steps).
- Put **Checkpoint Nodes** along the course, roughly one per corner or every 20-40 blocks.
- Placing a node onto an existing node replaces it and keeps its links; breaking one (hold a node item or the tool) removes it and its links.

### 3. Size the boxes
Every node is a **box** (drawn as a wireframe while you hold the tool): a kart passes a node by touching its box, from any direction. The
default is generous (24 wide, 6 deep, 8 above and 3 below the node). With the Track Tool, point at a side of a box (it turns yellow):
**right-click extends it by one block, left-click retracts it**. Make the box cover the whole width of the road, and as tall as any jump needs.

### 4. Link the nodes
With the Track Tool, right-click a node to select it, then right-click the next node to link them (right-click again to unlink). The clicked node
becomes the selection, so you can walk round the course in order: Start, checkpoint, checkpoint, ... and back to the Start. A node linked to
several others makes a **branch**: build a shortcut or an alternative route by linking its nodes between two main-path nodes.
Links can be up to 192 blocks long, and a track can only have one Start node. Sneak + right-click in the air clears the selection.

### 5. Test drive
Sneak + right-click the **Start Node** with the tool for its menu, and choose *Start test drive*. Drive a kart round the course through every box
in order: the boxes you can reach next glow cyan, a gold line shows the recorded **main path**. Passing the start again closes the lap. Then the
menu says "Test drive the branches": a box shows at the start of each undriven branch, drive them back to the main path. The track is **valid**
once every node is on the main path or on a driven branch. The menu also sets the number of laps and can **re-record** the main lap (it asks first).

### 6. Race
Drive a kart into the sphere round the Start Node (24 blocks, a server setting). With the default setting one kart is enough to begin; the
countdown (10 seconds) lets others drive in, and anyone who stays inside is entered. Karts are put on the grid in random order and held; **hold the
gas to rev while the three lights come on and let go at the right time for the start boost** (rev into the red zone for a small boost, pump the gas to
raise the target for a big one, hold it too long and the engine over-revs and you get nothing). Then it is laps, positions and times; **R** takes you
back to your last checkpoint. A kart that you do not want to start races can be switched off with the Wrench.

### Fine-tuning
- Resize each box side separately for odd shapes. To move a node, break it and place a new one (then link it again); placing a node onto another
  turns or swaps it in place and keeps its links.
- Change the number of laps in the Start Node menu (1, 2, 3, 5, 7, 10, 20).
- Server settings (`config/ctrcraft-server.toml`): `trackEditing` (who may edit tracks: creative or operators by default), `raceMinRacers`
  (karts needed to start a race by driving in, default 1) and `raceStartRadius` (default 24).

### Pitfalls to avoid
- **Gaps in the boxes.** A kart has to *touch* the box of the next node in order. If the road is wider than the box, or a jump takes the kart
  more than 8 blocks above (or 3 below) the node, it can miss the box and the race will not count the checkpoint. Widen the box, or add a node
  before and after the jump.
- **A box that covers more than its part of the track.** Boxes count as soon as they are touched, so a big box that reaches another stretch of road
  (a hairpin, a parallel lane, a bridge over the track) lets players skip ahead through the wrong section. Keep boxes to the road they belong to.
- **Forgotten nodes.** The track is only valid when every node has been test driven, including every branch. Adding, removing or relinking nodes later
  invalidates what was recorded, so test drive again. A race cannot start on an invalid track.
- **Unclosed loops.** Link the last node back to the Start. Branches must rejoin the main path at both ends.
- **A start line with no room.** Karts are placed on a grid behind the Start Node (two abreast, 3.5 blocks per row, starting 2.5 blocks behind it),
  so there must be flat road there for every kart, as wide as 3 blocks.
- **Two Start Nodes close together.** Their areas overlap and one would start the other's race. Keep them well apart (more than the start radius).
- **Unloaded chunks.** The server only follows a kart in loaded chunks, so a track that runs through chunks nobody has loaded will not register
  properly. Players' own view distance normally covers it; keep this in mind for very large tracks.
- **Walls you did not mean.** A step of two blocks, a fence or anything taller than a block and a quarter is a wall, not a ramp. Test drive and see.

## Original game assets (optional, local only)
The mod ships **only original, synthesised placeholder sounds** and original models and textures. It never ships anything from the game.
If you own *Crash Team Racing*, a supplied tool can build a **local resource pack** from your own disc that replaces the placeholder sounds with the real
ones for a more authentic feel: put your disc image (`.bin` + `.cue`, or `.iso`) in the project folder and run `extract-ctr-sounds.bat`. It reads
the game's sound bank from your disc, decodes the samples and writes a resource pack into your instance's `resourcepacks` folder, which you enable
in Options > Resource Packs. **Nothing from the disc is ever committed or put into the mod jar, and the resulting pack must not be shared.**

Today the tool extracts sounds. The same approach is planned for textures, models and UI elements as the mod grows, for those who want to replace the
original artwork with the real look.

## Building from source
Java 21 is needed.
```
gradlew build
```
makes `build/libs/ctrcraft-<version>.jar`. `gradlew test` runs the unit tests. `build-and-install.bat` also runs the generators in `tools/`
(Python 3 with Pillow and numpy) and copies the jar into a test instance; edit the path in `build-and-install.ps1`.

## Contributing and project rules
Models are made in Blockbench first (`blockbench/`), textures in layered Krita files (`art/`), and the generators in `tools/` build the game files
from them. The working rules, including the fidelity policy above, are in [CLAUDE.md](CLAUDE.md); design documents are in [docs/](docs/):
[tracks and races](docs/DESIGN_TRACKS.md), [terrain](docs/TERRAIN.md), [the physics port](docs/PHYSICS_PORT.md), [research](docs/RESEARCH.md).

## License and credits
**GPL-3.0-or-later**, see [LICENSE](LICENSE). The physics is a port of GPL-3.0 code, which is why the whole mod is GPL.
- [ctr-native](https://github.com/CTR-tools/ctr-native) (GPL-3.0): the decompiled game the physics, boosts and start rev are ported from.
- [world-of-kartcraft](https://github.com/duxanlol/world-of-kartcraft) (GPL-3.0): the inspiration for this project, and a Rust port of the same physics used as a cross-check.
- Files that come from the NeoForge MDK template (build scripts, Gradle wrapper) keep their own license, see [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
