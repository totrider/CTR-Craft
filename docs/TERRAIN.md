# Terrain model (milestone 2)

`com.ctrcraft.terrain` turns blocky worlds into the smooth ground CTR's physics expects. No Minecraft imports; the world comes in
through `BlockSource` (collision boxes + a surface code per block), the physics sees it as a `Collider` (`TerrainCollider`).
Tests: `TerrainFieldTest` (geometry, and the real `KartSim` driving over steps, a staircase, a curb, into a wall).

## The idea: a height map dilated by a 45 degree cone
Once per physics tick (`prepare`) the window of +-4 blocks around the kart is sampled at half-block resolution: for each cell the
top of the highest collision box that the kart could be on (at most 1.25 blocks above its feet, at most 6 below, with 0.9 of free
headroom, so bridges, overhangs and tunnels pick the right layer). The ground surface is
`S(p) = max over cells c of (h_c - distance(p, c))`, counting only cells at most a step (1.25 blocks) above the cell under `p`.

| Terrain | What the kart gets |
|---|---|
| flat floor | flat; the surface is never below a block's real top (nothing to sink into) |
| 1-block step | a 45 degree ramp over the block before it (1 block long), ending at the block's face |
| slab / stair / half-block curb | a gentle ramp over the lower side; a stair flight is one smooth slope |
| staircase of full blocks | one continuous 45 degree ramp |
| 2-block step, fence, wall | no ramp: the surface ends, the box is a wall (`WALL_RISE`) and the sphere bumps / scrapes / slams |
| cliff edge, gap | the surface ends at the edge; the kart falls |
| ceilings | boxes above the surface collide as walls (contact normal down) |

**Smoothing.** The cone surface alone turns a staircase of mixed steps (full blocks and slabs) into ramp, flat, ramp, flat. So the
cone surface is blurred (two passes of [1 2 1] over the half-block cells, about a block wide, which cancels a one-block rhythm
exactly) and the result may sit at most 0.18 block (3 px) below the cone surface, so the kart rides a hair into a tread's corner
instead of in bumps. The blur only mixes cells within a step of each other (a cliff's foot is not lifted) and only valid cells
(a wall does not pull the ground up). Tests: `stairsOfFullBlocksAndSlabsMakeOneSmoothIncline`, `stairsOfSlabsAreAnEvenSlope`.

The steepest the surface gets is 45 degrees (normal.y 0.707, above the physics' 0.643 ground threshold), so every ramp is "ground".
The sphere (radius 25 lu = 1 block wide) touches the surface through tangent planes at five points of its footprint, plus any
wall box. The window follows the kart's height (plus its climb per tick), so steps are always looked for from where it stands.

## Surfaces (data-driven)
A block is asphalt (full speed) unless it is in a tag; a data pack can add to or replace any of them:
`ctrcraft:surface/{dirt, slow_dirt, water, ice, icy_road, snow, mud, sideslip}` and `ctrcraft:turbo_pad` (data/ctrcraft/tags/block/).
Defaults: dirt, grass, sand, gravel, farmland... = dirt (CTR's off-road: 55% top speed, slower accel); ice = ice; snow; mud; soul sand = slow dirt.

## Boost blocks (`tools/make_boost_pad.py`, models in `blockbench/boost_pad/`)
Four blocks, all in `ctrcraft:turbo_pad`, each with a direction shown as a chevron arrow on top (CTR's pad boosts whichever way
you drive over it, so the direction is a marker, not a rule):
| Block | Direction | Notes |
|---|---|---|
| Boost Pad | 4 (the way you looked placing it) | 1 px plate laid on the floor, **no collision**: a kart drives over it with no bump (the physics finds it as the block lying on the ground) |
| Boost Block | 4 | solid cube, arrow on top |
| Boost Slab | 4 | bottom / top / double; completing a slab keeps the first one's arrow |
| Boost Stairs | up / right / down / left relative to the stair | up (default) leads up the stairs; the arrow is on the treads |
A click with an **empty hand** turns the arrow (clockwise; stairs cycle up, right, down, left).

## Connected textures (Fusion, optional)
With [Fusion](https://github.com/SuperMartijn642/Fusion) installed, boost blocks of the same state placed next to each other join up:
the yellow border is only drawn round the outside of the group (inner corners get a notch), each block keeps its arrow. Without Fusion the
plain texture is used, nothing else changes.
- **How:** `boost_top.png.mcmeta` in `src/main/resources/fusion-overrides/assets/ctrcraft/textures/block/` makes the texture a Fusion
  `connecting` texture, layout `pieced` (5 tiles in a row, 80x16), connecting when the neighbour `is_same_state` (same block, direction,
  type: a change of direction shows as a border). The mod's `pack.mcmeta` (`fusion.overrides_folder`) tells Fusion to use that folder
  only when it is installed (read from the Fusion source: it reads the pack.mcmeta of every mod).
- **The sheet is generated** from `boost_top.png` by `tools/make_boost_pad.py` (`--preview` draws a sample field to
  `build/boost_connect_preview.png`): the outer 2 px ring of the base texture is the border; repaint the base keeping that ring and the
  sheet follows. Do not edit the sheet by hand.
- **Rotation:** Fusion works out each face's texture orientation from the baked quad (block state rotation, face rotation), so arrows
  pointing any way connect on the right sides.
- **Stairs:** a stair tread is a slice of the arrow texture (half a block), so stairs show the arrows but not the border seams.
- **Copycats:** Fusion and Copycats+ are said to work together (not checked here); our copycat support is about the surface/boost.

## Copycats (Create, Copycats+)
A copycat (any Create or Copycats+ shape: stairs, slabs, slopes, beams, panels, bytes ...) is the block it copies, for surfaces: a
copycat of a boost block boosts (so boost slopes and boost beams exist), a copycat of ice is ice. The shape already works (the terrain
reads collision boxes). `compat/CopycatCompat` reads the copied block state by reflection, so neither mod is a dependency;
`create:copycat_allow` lets copycats take the boost blocks. For Copycats+ blocks with several parts, any part that is a boost block
makes the block a pad and the first non-asphalt part gives the surface type.

How applying and turning works (read from Create 6.0.10 and Copycats+ 3.0.11, not guessed):
- **Applying:** right-click a copycat with a boost block. Create accepts a block as material if it is a plain full cube or in
  `create:copycat_allow`, and refuses stairs unless allowed: the Boost Block, Slab and Stairs are in that tag (the plate is not: it is a
  1 px non-solid marker, not a material). Create sets the material's `HORIZONTAL_FACING` to the side of the copycat you clicked (north
  on a top click); Copycats+ sets a stair material's facing and half from where you look and click.
- **Turning:** right-click again with the same block and Create calls `cycleMaterial()`, which turns `HORIZONTAL_FACING` clockwise. Our
  plate, block, slab and stairs all use exactly that property, so the arrow turns with no hook of ours. A stair's up/right/down/left stays
  as it is (Create does not know that property), but its facing turns and the arrow is relative to it, so four clicks reach all four
  directions.
- **Direction and boosting:** the arrow is a marker: CTR's pads boost whichever way you cross them, so nothing reads it. If a pad
  should only boost along its arrow, `CopycatCompat` already hands back the copied block state, so the direction would be read
  from `HORIZONTAL_FACING` (and the stair's `direction`) the same way for a plain block and for a copycat.

## Not done yet
Kart-vs-kart bumping, checkpoints/laps/race manager, out-of-bounds/respawn, a real texture for the pad, water surfaces,
the ground edge is the cell edge (the sphere does not rest on an edge it overhangs), steep slope jumps with the 25 lu sphere only.
