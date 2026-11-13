# Craft Team Racing (CTR) - an unofficial Crash Team Racing tribute for Minecraft

A NeoForge mod for Minecraft 1.21.1 (NeoForge 21.1.x, Java 21) that brings **Crash Team Racing's kart driving** into Minecraft: powerslides,
hops, turbo boosts, boost pads, start boosts, hang time, on ordinary block terrain, with race tracks you build yourself.

> *Unofficial fan project, not affiliated with or endorsed by Activision, Sony or Mojang. It ships no assets from the original game.*

> **Please play the original.** This is a fan tribute, not a replacement. *Crash Team Racing* is a wonderful game from Naughty Dog and Sony, and it deserves to be played.
> If you enjoy this mod, please get a legitimate copy of the game through official channels. The optional local-asset tools in this repository only work with a disc
> you own, and they never put any of the original's content in the mod or the repository.

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
| **Works today** | Drivable karts with CTR's physics (powerslide, hop, boosts, spin-outs, wall slams, hang time) and its engine, skid and boost sounds (original synthesised placeholders). Smooth driving over steps, slabs and stairs. Boost blocks. Track building with nodes, a Track Tool, test drive and validation, branching paths. Races: starting by driving in, an intro with a camera pan over the grid, random grid (the original's two rows of four), lights, start rev and start boost, laps, positions, HUD, respawn, a deliberate way out of a race. A Wrench to switch whether a kart can start races. The original's driving meters and HUD layout. Wumpa fruit, fruit crates and power-up crates with CTR's place-based roulette, and three power-ups: the **Turbo**, **TNT / Nitro crates** and **N. Brio's Beaker** (green and red). Race music and sounds from optional local resource packs. |
| **Planned** | Survival crafting for karts, parts and boost pads. The other power-ups (bowling bombs, missiles, Power Shield, masks, warp orb, clock) and a battle mode. Time trials and ghosts. Kart tuning (CTR's four engine classes, data-driven) with a tuning profile on the Wrench. A Super Boost Pad (CTR's blue-flame pad) as an opt-in block. Wrong-way detection, a ready check. Sable / Aeronautics compatibility and claim support for building tracks on a server (see [Mod compatibility](#mod-compatibility)). A rulebook item for custom rule sets. The original's 3D weapon models (the flask, bomb, missile and others) as directional sprites in the local pack. |

The full plan is in [docs/ROADMAP.md](docs/ROADMAP.md).

## Quick start
1. Install NeoForge 21.1.x for Minecraft 1.21.1 and put `ctrcraft-<version>.jar` in `mods/`. No other mod is needed;
   see [Mod compatibility](#mod-compatibility) for the ones that enhance it.
2. In creative, open the Craft Team Racing tab, take a **Kart**, right-click the ground to place it and right-click the kart to get in.
3. Drive (see [Controls](#controls)). Sneak to get out.
4. Build and race a track: see [Building a race track](#building-a-race-track).

## Blocks and items
Everything is in the Craft Team Racing creative tab for now (survival recipes are planned).

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
| **Fruit Spot** | Block | Where a Wumpa fruit appears. Touch it with a kart for +1 (0 to 10 wumpa; ten make the kart "juiced": its power-ups come in their stronger forms). |
| **Fruit Crate Spot** | Block | A crate with 5 to 8 wumpa; it grows back a second after it is broken. |
| **Power-up Crate Spot** | Block | A "?" crate. Break it while holding nothing and a roulette picks a power-up by your place in the race (CTR's tables). Right-click with the Wrench to make it always give one particular power-up (good for testing). |
| **Wrench** | Item | Right-click a kart to switch whether it can start races (on by default). Kart tuning is planned for it. |

### Fruit, crates and power-ups
Spots are small marker blocks, **invisible unless you hold something to edit them with** (a spot block, the Track Tool, a node block or the Wrench).
**A pickup appears at a spot while a race is on a track near it** (within 96 blocks of the track's line, the links between its nodes; configurable), and is gone when the race ends.
A **redstone signal arriving at a spot arms it**: its pickup appears and stays, even after the signal is gone, and a new pulse brings back a fruit that was taken. A spot waits
to respawn a crate while a kart (or a placed weapon) sits on it, as the original does. The **wumpa count and the held power-up belong to the kart**: they stay when you get out, and
every race starts with nothing. The design, with what the original does, is in [docs/DESIGN_ITEMS.md](docs/DESIGN_ITEMS.md).

#### Power-ups built so far
| Power-up | What it does here |
|---|---|
| **Turbo** | A speed burst (CTR's turbo item; bigger with ten wumpa). |
| **TNT / Nitro crate** | Dropped behind you. The first other kart to touch it gets it on its head; three seconds later it explodes and **blasts** that kart (a 2.4 s tumble with the bounces of the original's animation, the speed taken away, three wumpa lost). **Jump repeatedly** (8 to 10 times) to throw it off, and it explodes harmlessly where it lands. With ten wumpa it is a Nitro crate, which explodes at the first touch. |
| **N. Brio's Beaker** | Dropped behind, or thrown in an arc when the forward key is held. A kart that touches it spins out and the beaker bursts. A red one (ten wumpa) also puts a **rain cloud** over the kart for 7.68 s (item roll, icy ground or jump lockout; the slow effect is not built yet). |

Placed weapons follow the original's mine pool: ten at a time (the oldest bursts when an eleventh is placed), cleared when a race ends; in free roam each player has their own
ten and untouched ones go away after five minutes. They lie on the slope they come to rest on, and a kart left on a slope stays on it. The rest of the original's list (bombs, missiles,
shield, masks, warp orb, clock) is summarised in [docs/POWERUPS.md](docs/POWERUPS.md) with what each needs.

### Boost blocks
- **They are placed relative to your orientation.** Each shows a chevron arrow on top, pointing the way you were looking when you placed it.
  Stairs have four arrow directions (up, right, down, left) relative to the stair. Click a boost block with an **empty hand** to turn its arrow.
- **The arrow is a marker, not a rule:** as in CTR, a pad boosts whichever way you drive over it.
- **Connected textures:** boost blocks of the same kind and direction placed next to each other join up, so the yellow border runs only round
  the outside of the group and each block keeps its arrow. This needs Fusion (see below); without it the plain texture is used.
- Boost blocks work as **surfaces for copycat blocks** too, see [Mod compatibility](#mod-compatibility).

## The race screen
A race begins with the original's intro: the intro jingle plays (from your local music pack) while the camera pans over the grid from the finish line and comes back to your own
kart, and only then do the four start lamps appear. The HUD is laid out where the original game puts things, at the original's proportions whatever the window's shape: the clock and lap times top left, the first four racers (as skin heads) below them, your place in
big digits bottom left, the four start lamps top centre, the held power-up top centre-left and the Wumpa count beside it. The driving meters (the dial with its needle, the grey powerslide box under it and the hang-time bar beside it) are the original's, drawn as it draws them,
in the bottom right corner. The one difference: the lap
counter sits at the right edge above the driving meters, where the original shows its map (the track and racers on a map are planned for Xaero's mods).

## Controls
All of these can be rebound under Options > Controls > Craft Team Racing (the movement keys are Minecraft's own).

| Action | Default | Notes |
|---|---|---|
| Accelerate | `W` | Hold. |
| Steer | `A` / `D` | |
| Reverse | `S` | |
| Brake | `X` | The PS1 "square" button. With `W` held, a power-brake. |
| Hop / powerslide / boost press (R1) | `Space` | Hop; land with it held and steering to start a powerslide; tap it in the red zone of the meter to boost. |
| Second shoulder button (L1) | `C` | The same as R1; either one hops and boosts. |
| Get out of the kart | `Shift` (sneak) | Not during a race, so it cannot happen by accident. |
| Leave a race | hold `Backspace` | Two seconds, in a race only. |
| Use the held power-up | `V` | Turbo, TNT / Nitro and the beaker work (hold forward to throw a beaker); any other power-up is used up with a message, so the roulette can be tested. |
| Respawn at last checkpoint | `R` | During a race only, with a short cooldown. |
| Rev the engine on the grid | hold `W` | Held on the grid until the lights go out, see [Race](#6-race). |

## Mod compatibility
Craft Team Racing is a **standalone mod**: it needs only NeoForge and Minecraft, and everything described above works without any other mod. The mods
below are all optional; when they are installed, the mod is enhanced.

| Mod | State | What you get when it is installed |
|---|---|---|
| [**Fusion**](https://github.com/SuperMartijn642/Fusion) | Works | **Connected textures** for boost blocks: neighbouring boost blocks of the same kind and direction merge, with the border drawn round the group only. |
| [**Create**](https://github.com/Creators-of-Create/Create) | Works | **Copycat blocks** take on the block they copy: right-click a copycat with a boost block and it boosts (boost slopes, boost beams...); a copycat of ice is slippery ice. Right-click again with the same block to turn the arrow. |
| **Copycats+** | Works | The same for its many shapes (slopes, beams, panels, bytes ...), including blocks made of several parts. |
| [**Sable**](https://github.com/ryanhcode/sable) | Planned | Karts driving on sub-levels (moving and rotating structures). Declared as an optional dependency only; never required and never bundled. |
| **Create Aeronautics** | Planned | Karts on and around airships and contraptions, through Sable. |
| **Xaero's Minimap** and **Xaero's World Map** | Planned | The track and the racers on the map, instead of a track display of the mod's own (the original game's circuit minimap with the racers' dots). |
| [**Open Parties and Claims**](https://github.com/thexaero/open-parties-and-claims) | Planned | Track building that respects claims, so players can build and edit tracks on a server in survival. |

## Karts and other entities
- **Kart against kart** is CTR's own bump: each kart is a sphere, overlapping spheres push apart, the heavier kart is bounced less, and nothing is damaged.
- **Karts and everything else** (mobs, boats, minecarts, players on foot) is the mod's own system and is **on by default**, because it is part of
  playing in a survival world. The kart bounces off, the thing it hit is thrown (a heavy one hardly moves), and at speed it is hurt: about a heart
  for every 2 blocks/s over a walking pace, so a full-speed hit takes 6 hearts. The driver gets the credit; their own pets are spared, and players
  on foot are only hurt where the server's PvP rules allow it.
- Everything about it is in the server config (`ctrcraft-server.toml`, section `entities`): `contact`, `damage`, `damagePlayers`, `damageScale`.
  Using CTR power-ups on entities through the kart will come with the items.

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
Drive a kart into the **gathering area**: a box 16 blocks wide and 16 deep **behind the Start Node's line** (it always has this size, whatever size
the node's own checkpoint box has). With the default setting one kart is enough to begin; the
countdown (10 seconds) lets others drive in, and anyone who stays inside is entered. Karts are put on the grid in random order and held; **hold the
gas to rev while the four lamps count down and let go at the right time for the start boost** (rev into the red zone for a small boost, pump the gas to
raise the target for a big one, hold it too long and the engine over-revs and you get nothing). Then it is laps, positions and times; **R** takes you
back to your last checkpoint. A kart that you do not want to start races can be switched off with the Wrench.

### Fine-tuning
- Resize each box side separately for odd shapes. To move a node, break it and place a new one (then link it again); placing a node onto another
  turns or swaps it in place and keeps its links.
- Change the number of laps in the Start Node menu (1, 2, 3, 5, 7, 10, 20).
- Server settings (`config/ctrcraft-server.toml`): `trackEditing` (who may edit tracks: creative or operators by default), `raceMinRacers`
  (karts needed to start a race by driving in, default 1).

**What you see on the ground.** Each Start Node shows a flat sprite for every kart spot of the start grid (the first eight, as many as the original
game's race; more racers are placed further back in the same pattern) and, a little above them, the gathering area. They are drawn while you hold
a track item, and for any complete track that nobody is racing on.

### Pitfalls to avoid
- **Gaps in the boxes.** A kart has to *touch* the box of the next node in order. If the road is wider than the box, or a jump takes the kart
  more than 8 blocks above (or 3 below) the node, it can miss the box and the race will not count the checkpoint. Widen the box, or add a node
  before and after the jump.
- **A box that covers more than its part of the track.** Boxes count as soon as they are touched, so a big box that reaches another stretch of road
  (a hairpin, a parallel lane, a bridge over the track) lets players skip ahead through the wrong section. Keep boxes to the road they belong to.
- **Forgotten nodes.** The track is only valid when every node has been test driven, including every branch. Adding, removing or relinking nodes later
  invalidates what was recorded, so test drive again. A race cannot start on an invalid track.
- **Unclosed loops.** Link the last node back to the Start. Branches must rejoin the main path at both ends.
- **A start line with no room.** Karts are placed on a grid behind the Start Node (the original game's layout: two rows of four karts side by side, a kart every 3.8 blocks along a row and the rows 5 blocks apart, starting 2 blocks behind the line; more racers repeat it further back),
  so there must be flat road there for every kart, about 12 blocks wide.
- **Two Start Nodes close together.** Their areas overlap and one would start the other's race. Keep their gathering areas well apart (the boxes are 16 blocks deep and wide).
- **Unloaded chunks.** The server only follows a kart in loaded chunks, so a track that runs through chunks nobody has loaded will not register
  properly. Players' own view distance normally covers it; keep this in mind for very large tracks.
- **Walls you did not mean.** A step of two blocks, a fence or anything taller than a block and a quarter is a wall, not a ramp. Test drive and see.

## Original game assets (optional, local only)
The mod ships **only original, synthesised placeholder sounds** and original models and textures. It never ships anything from the game.
If you own *Crash Team Racing*, a supplied tool can build a **local resource pack** from your own disc that replaces the placeholder sounds with the real
ones for a more authentic feel: put your disc image (`.bin` + `.cue`, or `.iso`) in the project folder and run `extract-ctr-sounds.bat`. It reads
the game's sound bank from your disc, decodes the samples and writes a resource pack into your instance's `resourcepacks` folder, which you enable
in Options > Resource Packs. **Nothing from the disc is ever committed or put into the mod jar, and the resulting pack must not be shared.**

**Textures:** `extract-ctr-textures.bat` does the same for the artwork and writes **two** packs: `CTRCraft-OriginalTextures` (the art scaled down to
Minecraft sizes: 32 x 32 crate faces and half-size item icons; a crate is one block) and `CTRCraft-OriginalTextures-OriginalScale` (the art at its original size: 64 x 64 crate
faces, 32 x 48 power-up icons). Enable one of them. They replace the crates (the weapon, fruit, TNT and Nitro crates are built from the real models' own side, top and bottom pictures), the power-up icons (turned upright, with the juiced
pictures for ten wumpa), the Wumpa fruit sprite in the HUD and the start lamps. The Wumpa fruit in the world is replaced by a sprite: the original mesh, with its own vertex colours, rendered turning in 32 steps
(`tools/render_ctr_sprite.py`) as an animated texture on a flat quad that always faces the camera (the mod draws any single-quad pickup model that way). Spring,
Invisibility and Super Engine have no icon in the game's shared art, so they keep the mod's.

**Effects:** every kart casts a dark kart-shaped **shadow on the ground** under it, at the same size however high it flies, to judge a landing by (the
original does the same). Tyre smoke and backfires are **dust puffs** that step through five pictures, bright to dark; wheels on dirt throw **clods**; a kart rubbing a wall throws starburst **sparks**. Both have the mod's own art and, in the
texture packs, the original's.

**Blender:** `python tools/export_ctr_mesh.py` exports the crates and the Wumpa fruit (or any model of Crash Cove's level; with `--pack` the weapon models of the shared pack) from your disc as OBJ files with
UVs, materials, textures and the game's shading colours in `local-assets/ctr-mesh/`, to compare with the mod's models in Blender. The crate faces in the
texture packs are baked from those meshes, so the frame, rivets and panel are exactly the original's.

**Browsing the disc:** `browse-ctr-assets.bat` writes a local web page (`local-assets/ctr-browse/index.html`, git-ignored) with every icon of the shared pack,
every texture of Crash Cove's models (crates, fruit, ...) with its index and raw layout, the models' shading tables and the video memory, so you can look
through what is on your disc and say which picture the extractor should use.

**Music:** the mod plays race music (the intro jingle with the camera pan, music from GO to the finish that speeds up on the last lap while the last-lap jingle plays over it, and a win or
lose jingle that fades out after 30 seconds) from whatever resource packs put into its music sound events; it ships none, so vanilla stays silent. Any pack, or any player's own tracks, can fill them.
`extract-ctr-music.bat` builds a local pack from your raw disc image with the original's jingles **and the level music**, which is sequenced, not recorded: `tools/ctr_cseq.py` runs the game's
sequencer and sound chip offline and renders each level's song to a looping Ogg. The Music slider of the sound options sets the volume. See [docs/MUSIC.md](docs/MUSIC.md).

**Sounds:** the sound tool extracts: the kart's (engine, skid, turbo, hop, landing, boost, wall hits), the three kinds of kart-against-kart crash, the start
lamps, the Wumpa fruit pickup, the crate break, the power-up roulette (looped while it runs) and the TNT, Nitro and beaker sounds. The same approach is planned for textures, models and UI elements as the
mod grows, for those who want to replace the original artwork with the real look. The mod's own stand-in art for those (the pickup models, the
power-up icons) is drawn from memory as look-alikes and is easy to replace in a resource pack of your own.

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
[tracks and races](docs/DESIGN_TRACKS.md), [fruit, crates and rule sets](docs/DESIGN_ITEMS.md), [power-ups](docs/POWERUPS.md), [race music](docs/MUSIC.md),
[terrain](docs/TERRAIN.md), [the physics port](docs/PHYSICS_PORT.md), [research](docs/RESEARCH.md).

## License and credits
**GPL-3.0-or-later**, see [LICENSE](LICENSE). The physics is a port of GPL-3.0 code, which is why the whole mod is GPL.
- [ctr-native](https://github.com/CTR-tools/ctr-native) (GPL-3.0): the decompiled game the physics, boosts and start rev are ported from.
- [world-of-kartcraft](https://github.com/duxanlol/world-of-kartcraft) (GPL-3.0): the inspiration for this project, and a Rust port of the same physics used as a cross-check.
- Files that come from the NeoForge MDK template (build scripts, Gradle wrapper) keep their own license, see [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
