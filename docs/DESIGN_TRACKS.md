# Tracks and races: a node graph you build, then test-drive

Status: **steps 1-3 built (2026-11-12): nodes, tool, linking, test drive with validity; race runtime (gather, grid, lights, start boost, laps, positions, HUD).** Step 4 (polish) is design only.
Opt-in by nature (nothing here changes how a kart drives).

## Step 1 as built (how to use it)
- Creative tab: **Track Tool**, **Start Node**, **Checkpoint Node**. Place nodes like blocks (they face the way you look, in 22.5 degree steps); they are invisible
  and have no collision. Only creative players / operators may edit (server setting `trackEditing`, `EVERYONE` opens it; `TrackPermissions` is where a claims mod hooks in).
- Hold the Track Tool (or a node item): every node near you is drawn as a ghost: green box = start, orange = checkpoint, an arrow for its facing, a ring the size of
  its radius (6 blocks), cyan lines for links, white pulsing for the selected node.
- With the Track Tool in the main hand, right-click a node to select it, then another to link them (again to unlink). The clicked node becomes the selection, so you can
  walk a chain: start, c1, c2, ... back to the start. Several links on one node make branches. Sneak + right-click clears the selection. The action bar says what happened
  and whether the loop is closed. A track may only have one start node; links are limited to 192 blocks.
- Holding a node item also shows a **placement preview**: the node exactly where it will go, its ring, a compass of the 16 facings (the one you get is long and
  white) and an action-bar line naming it ("facing NNE (22.5 degrees, step 1 of 16)"). Rotation 0 = south, turning clockwise (west, north, east), like a standing sign;
  all 16 are reachable by turning your view. Red box = cannot place there.
- **Remove / replace:** while holding the Track Tool or a node item the nodes can be pointed at, so you can break them (their links go with them) and place a node onto
  one to replace it, like a block onto tall grass. Replacing keeps links and ring size, so it turns a node or changes checkpoint <-> start; a checkpoint cannot
  become a second start in its track.
- Data: `TrackData` (saved per dimension, `data/ctrcraft_tracks.dat` in the world); the logic is `TrackGraph` (pure Java, 11 tests).

## Step 2 as built: the test drive
- **Menu:** Track Tool, sneak + right-click the **Start node** opens a menu (needs track-editing permission): track summary, **Start / Stop test drive**, **Re-record main lap...**
  (asks for confirmation first, then clears the main lap and branches) and Done. The server re-checks node, distance (16 blocks) and permission on every button
  (`TrackMenuPayload` / `TrackMenuActionPayload`, `client/TrackMenuScreen`).
- **Drive:** the menu's start button begins the drive. Drive a kart; the server follows its reported positions
  (`TestDriveManager.onKartMoved`, called from `KartStatePayload`). Rings are crossed by segment-vs-disc tests (`NodeGeometry.crossing`): the move from one tick
  to the next must pass through the node's ring (radius 6). **Either direction counts**: which way round the course goes comes from the drive, not from the ring.
  (Design said "forwards only"; dropped because nodes are placed facing wherever the builder looked. A wrong-facing node is only counted, see below.)
- **Main lap:** the rings shown (cyan, three nested circles, pulsing) are the unvisited nodes linked to the last one you reached. Passing the start again with a loop
  behind you (not straight back along the first link) stores the lap as the **main path** `[start, c1, ..., start]`. Branching in the lap is allowed: whichever way you
  drive becomes the main path, the rest become branches.
- **Branches:** once a main path exists, a ring shows at each node off the main path that is linked to it and not yet driven. Passing it starts a branch; follow it node by node,
  and it ends when you pass a main-path node it is linked to. Driving the same branch the other way is not stored twice. `from` is the main neighbour of the first
  branch node (a different one than `to` when possible).
- **Valid** = a main path exists and every node of the track is on it or on a driven branch. The action bar says "N nodes not driven yet" or "Track valid".
- **Warnings:** nodes whose facing points more than a quarter turn against the course are counted after the lap (`TrackRoutes.facingAgainstCourse`); they still work.
- **Saved** in `TrackData` (per start node) and checked against the graph whenever it changes (`TrackRoutes.verify`): break a main-path node or unlink two of them and the
  recording is dropped; a broken branch is dropped alone. A lap or branch in progress is not saved (stopping restarts it).
- **Re-record:** the menu button; `/ctrcraft track reset` (no confirmation) still clears the routes of the start node nearest to you.
- Client: `TestDrivePayload` (candidates, trail, routes) -> `TestDriveClient`; `TrackGhostRenderer` draws main path gold, branches magenta, trail white. The node snapshot is
  also sent while a drive is active, so you do not need to hold a track item.
- Code: `NodeGeometry`, `TrackRoutes`, `TestDrive` (pure Java, `TestDriveTest`: 13 tests), `TestDriveManager`, `TrackCommands`.

## Step 3 as built: the race runtime
- **Start:** Start node menu (sneak + right-click with the Track Tool): **Start race** (only on a *valid* track: main lap recorded and every node driven), **Laps: N** (click to cycle
  1, 2, 3, 5, 7, 10, 20; saved with the track), **Cancel race**. Needs track-editing permission for now (a player-facing way to start races comes with the ready UI).
- **Gather (10 s):** drive your kart into the area round the Start node (24 blocks sideways, 12 up or down). Everyone within 64 blocks sees "Race starts in N" and the kart count.
  At zero the karts in the area are the entrants; none -> called off.
- **Grid and lights:** entrants are teleported to **randomly ordered** slots behind the node (the original's two rows of four abreast, `RaceGrid.slot`: every one of CTR's 16 race tracks has the same eight `DriverSpawn` entries, read from the disc: two rows of four abreast (3.8 blocks apart), the rows 5 blocks apart; racers past eight repeat the pattern behind), facing the node's facing.
  The client places its sim there (`RacePayload.placeSeq`). Karts are **held revving** (`RevEngine`, ctr-native `VehStuckProc_RevEngine`: hold gas to rev, pump it for the big start
  boost, hold it too long and the engine over-revs and locks out) while four lamps count down (`RaceGrid.lightsLit`). At GO the revs earn the start boost (small or big, 960 reserves).
  From the decomp: the 0xF00 = 3840 ms timer, release at 0, and the lamps (`DotLights_AudioAndVideo`): red at 2880, 1920 and 960 ms left, green at 0 (= GO), then the lamps stay up 960 ms. **Ours:** a second to settle on the grid before the countdown.
- **Racing:** the server follows each kart's reported positions (`RaceManager.onKartMoved`) and counts rings with `RaceProgress`: the main path rings in order, or a recorded branch
  instead of the stretch it replaces (then the skipped main nodes count). Passing the start ring again closes a lap. Position = finishers first, then progress round the course,
  ties by distance to the next ring. Clock = server ticks (50 ms resolution).
- **End:** everybody finished, nobody left in a kart, or 60 s after the first finisher. Results go to chat. Leaving the kart, logging out or changing dimension is a DNF.
  Changing the track (breaking a node, unlinking) during a race calls it off.
- **HUD** (`RaceHud`): the gathering count, the four lamps and the rev bar (red from the acceleration speed up, where a start boost is earned), then the race screen laid out as the original's 512 x 216 HUD (clock and lap times top left, the top four as skin heads below, the place in big digits bottom left, lamps top centre, item and Wumpa top centre-left); only the lap counter is moved, to the right edge above the driving meters. The start grid and the gathering box stay drawn until the race has really started (GO), not just until the countdown begins.
- Code: `RaceProgress`, `RaceGrid` (pure, `RaceProgressTest`), `RevEngine` (physics, `RevEngineTest`), `RaceManager`, `RacePayload`/`RaceClient`, `RaceHud`, `KartDriver` (hold, placement, boost).
- **Not yet:** kart-vs-kart bumping, wrong-way detection, respawn when out of bounds, a ready check, time trials, a player-facing start (all step 4 / roadmap).

## Step 3b (2026-11-12): box checkpoints, driving in to start, respawn, wrench
Changes after the first race test, from the user's feedback. Everything below replaces what the older sections say about rings / radius / starting from the tool.
- **Checkpoints are boxes, not rings.** A node's box is centred on the node block, turned to its facing, with six extents (left, right, front, back, up, down), default
  12 + 12 wide, 3 + 3 deep, 8 up and 3 down (`NodeGeometry.DEFAULT_EXTENTS`; saved per node). A node counts when a kart's move from one tick to the next **touches** the box
  (starts in it, ends in it or passes through; `NodeGeometry.touch`), from any direction, so a wide or organic track has no blind spots and a fast kart cannot jump a box.
  The order along the recorded path is still required (test drive and race), which is what stops shortcuts across the infield.
- **Resizing:** Track Tool, point at a side of a box (the side under the crosshair is outlined yellow): **right-click extends it one block, left-click retracts it** (1..64 blocks). The click is made on the client (`TrackBoxInput`, `BoxPicker`: the box sides are not blocks) and checked on the server
  (`TrackBoxPayload`: node, distance, permission). The node itself, and any block nearer than the side, win the click (so linking still works).
- **Menu:** unchanged: **sneak + right-click on the Start node** (the node itself, not a box side) opens the menu. Right-clicking the air does nothing special, so it cannot clash with box sides.
- **Races start by driving in.** Server config `raceMinRacers` (default 1). The area is `GatherZone`: a 16 x 16 box behind the Start node's line (and 5 above, 3 below it), a fixed size whatever the node's own box is. Each kart position report is
  compared with the Start nodes (`RaceManager.trackAreas`, nothing else polls): entering the box adds the kart to the node's set, and when the set reaches the minimum on a valid
  track the 10 s countdown starts; leaving it (or stepping out of the kart, logging out, changing dimension) removes the kart and cancels the countdown if too few remain. Whoever
  is in the box at zero is entered. The set is kept apart from races, so karts still inside after a race do not start the next one: leave and drive in again.
  The menu's **Start race** still starts one by hand (anyone in the area, any number, for testing alone).
- **Wrench** (`ctrcraft:wrench`): right-click a kart with it to switch **"can start races"** on or off (on by default; kept in the kart's NBT). An off kart is never counted as
  inside an area. The tuning profile is meant to go on this item later.
- **Respawn:** key **R** (rebindable) during a race puts the kart on the last ring it passed, facing the next one (`RaceManager.respawn`, 3 s cooldown), speed 0.
  CTR does the same with a mask grab to the last valid checkpoint (`VehStuckProc_MaskGrab_FindDestPos`: the restart point of the last valid quad, facing the next one, then
  dropped onto the ground). Ours is instant and has no kill plane: there is no out-of-bounds block, on purpose (a build decides where you can go; falling into the void is only a
  problem in the End).
- **How CTR counts laps** (decomp `PlayLevel.c`/`VehLap.c`, read 2026-11-12): not by rings. Each track quad carries a checkpoint index into a chain of points; progress is the remaining
  distance along that chain; a lap is counted when that counter wraps from under 1200 to over 32000 (crossing the finish forwards), and a "behind the start line" flag swallows the first
  forward crossing after a backwards one. Boxes plus ordered passage are our stand-in for "the track surface decides where you are".

## The user's workflow (as described)
1. Place invisible, non-collision **node blocks**: a **Start node** and **Checkpoint nodes**. They are only seen/selectable while holding the Track Tool.
2. With the Track Tool, **link** nodes: select one, right-click another and they connect (both know each other). Keep linking along the course until
   the loop closes back at the Start node.
3. **Branches:** link several checkpoint nodes to the same checkpoint to make forks.
4. **Test drive** (a driven, validated lap): a transparent **ring** appears at each checkpoint; you drive through and "activate" them. The first complete lap along
   the main route is stored as the **main path**.
5. Then you keep driving the **branches**: a start ring appears at each branching node. Whichever branch node you activate first (connected to the main
   path) is the branch start, and the point where it joins the main path again is its end.
6. **Orientation matters:** a node's facing is used, e.g. the Start node's facing says which way the grid points and where the start positions are.
7. **Starting a race:** a group of players drive their karts into the area of the Start node. After a UI countdown the race starts: players are teleported to
   randomly ordered start positions, then the usual **signal light countdown** with the **start boost** if timed right. Being in a kart is the ready check for now.
8. Later: time trials on the same data; a nicer start (a ready UI).

## Proposed architecture
**Truth lives in world data, blocks are markers.** `TrackData` (a `SavedData` per level, server side) holds all tracks; the node blocks only mark where a
node is. If a node block is broken, its data and links are removed.
- `Track { id, name, nodes, main path, branches, valid, laps }`.
- `Node { pos, kind (start | checkpoint), facing, radius (ring size), links (a set of other node positions, undirected) }`.
- Links are **undirected**: you build them without choosing a direction; the direction of travel comes from the test drive (the main path is an ordered list).
- `MainPath = [start, c1, c2, ... , start]`. `Branch { fromMain, [nodes...], toMain }` where `fromMain` / `toMain` are main-path nodes.
- A track is **valid** when: it has one start, the main path is a closed loop through every checkpoint the first lap used, every node not on the main path belongs to a
  branch that was driven, and no node is isolated.

**Blocks.** `start_node`, `checkpoint_node` (one block with a `kind` property, or two): `RenderShape.INVISIBLE`, no collision, no occlusion, a `FACING` property.
They have an outline/selection shape and a ghost marker (a box plus a direction arrow, ring for checkpoints) drawn by the client only when the player holds the Track Tool
(`RenderLevelStageEvent`; the block's `getShape` can use the `CollisionContext` to return a small box only for a player holding the tool, so others cannot even click them).

**Track Tool** (`ctrcraft:track_tool`). Remembers the **selected node** (item component). Right-click a node: nothing selected → select it; something selected → toggle the
link between selected and clicked, and the clicked one becomes the selected one (so you can walk a chain: start, c1, c2, ... start). Sneak + right-click clears the
selection. The tool also starts and stops the test drive (sneak + use on the Start node) and shows the track's state in chat/an actionbar.

**Test drive** (server authoritative on progress, the client draws the rings): a ring is a disc of the node's radius facing along the course. The kart's per-tick positions
already reach the server (`KartStatePayload`), so crossing a ring is a segment-vs-disc test, forwards only. Phase 1 records the main path; phase 2 shows rings at nodes
adjacent to the main path that are not on it, and records branches as above. Progress is stored in the track (the user can stop and continue).

**Race runtime** (`RaceManager`, server): the Start node defines a **lobby area** (a box in front/around it). Karts with a driver inside it are the entrants; a countdown
timer (UI) runs while the set is stable; at zero the entrants are teleported to **start slots** (a staggered grid behind the node along its facing, order shuffled), then
the **signal lights** and the **start boost**. Laps count when the main path (or a valid branch) is completed in order and the start line crossed; positions are ranked by
progress (lap, node index, distance to the next node). Respawn and wrong-way handling come after (the out-of-bounds item already on the roadmap).
- Start boost: PS1 has it (`KS_ENGINE_REVVING`, `VehStuckProc_RevEngine`); the world-of-kartcraft port has it in `rev.rs`. The rev meter, the two boost sizes and the
  "over-rev" fail are in the decomp; port them from `reference/ctr-native` / `reference/wok/rev.rs` when we get there (not read yet).

## Build order (each step playable on its own)
1. **Nodes and the tool:** node blocks, ghost rendering, the tool, linking, `TrackData`, saving. (You can build and see a graph; nothing drives.)
2. **Test drive and validation:** rings, main path, branches, the valid flag, feedback.
3. **Race runtime:** lobby area, countdown, teleport to slots, signal lights, start boost, laps, positions, HUD (lap, position, time).
4. **Polish:** respawn / out of bounds, wrong-way, time trials, ready UI, track export as a datapack.

## Open questions
- **Who can edit tracks?** Proposed: creative / operators only by default (a config flag opens it to everyone with the tool).
- **Radius and ring size:** per node, adjustable with the tool (scroll or sneak-scroll)? Proposed yes, default 6 blocks.
- **Lap count and settings:** set on the Start node via the tool (a small screen), default 3.
- **Several tracks in one world:** yes (each Start node starts a track; nodes belong to the track they are linked into).
- **A branch ring that should also count for the lap:** the main path decides what counts; branches are alternative routes between two main nodes, so passing either counts.
